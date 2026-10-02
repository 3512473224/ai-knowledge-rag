import axios from 'axios'

const http = axios.create({ timeout: 30000 })

http.interceptors.response.use(
  (res) => {
    const body = res.data
    if (body && typeof body.code !== 'undefined' && body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (err) => Promise.reject(err)
)

/**
 * SSE 流式问答。原生 EventSource 不支持 POST body，
 * 这里用 fetch + ReadableStream 手工解析 data: 行。
 * onEvent(name, data)：name = token | sources | messageId | done
 */
export async function streamChat(sessionId, kbId, question, onEvent, signal) {
  const params = new URLSearchParams()
  if (sessionId) params.append('sessionId', sessionId)
  if (kbId) params.append('kbId', kbId)
  params.append('question', question)
  const resp = await fetch(`/api/chat/stream?${params.toString()}`, {
    method: 'POST',
    signal
  })
  if (!resp.ok || !resp.body) throw new Error('连接失败：' + resp.status)
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buf = ''
  let eventName = ''
  const dispatch = () => {
    const lines = buf.split('\n')
    buf = lines.pop()
    for (const line of lines) {
      if (line.startsWith('event:')) eventName = line.slice(6).trim()
      else if (line.startsWith('data:')) {
        onEvent(eventName || 'message', line.slice(5).trim())
        eventName = ''
      }
    }
  }
  for (;;) {
    const { done, value } = await reader.read()
    if (done) break
    buf += decoder.decode(value, { stream: true })
    dispatch()
  }
}

export const api = {
  // ---- 知识库 ----
  listKb: () => http.get('/api/kb'),
  createKb: (data) => http.post('/api/kb', data),
  updateKb: (id, data) => http.put(`/api/kb/${id}`, data),
  deleteKb: (id) => http.delete(`/api/kb/${id}`),

  // ---- 文档 ----
  uploadDocument: (file, kbId) => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('kbId', kbId)
    return http.post('/api/documents/upload', fd)
  },
  listDocuments: (kbId) => http.get('/api/documents', { params: kbId ? { kbId } : {} }),
  documentDetail: (id) => http.get(`/api/documents/${id}`),
  documentProgress: (id) => http.get(`/api/documents/${id}/progress`),
  activateVersion: (docId, versionId) =>
    http.post(`/api/documents/${docId}/versions/${versionId}/activate`),
  documentFileUrl: (id) => `/api/documents/${id}/file`,
  documentText: (id) => http.get(`/api/documents/${id}/text`, { responseType: 'text' }),
  deleteDocument: (id) => http.delete(`/api/documents/${id}`),

  // ---- 问答 ----
  listSessions: (kbId, keyword) =>
    http.get('/api/chat/sessions', { params: { kbId: kbId || '', keyword: keyword || '' } }),
  listMessages: (id) => http.get(`/api/chat/sessions/${id}/messages`),
  deleteSession: (id) => http.delete(`/api/chat/sessions/${id}`),

  // ---- 反馈 ----
  submitFeedback: (messageId, useful, note) =>
    http.post(`/api/feedback/messages/${messageId}`, { useful, note }),
  listFeedback: (status) => http.get('/api/feedback', { params: { status } }),
  resolveFeedback: (id, status) => http.post(`/api/feedback/${id}/resolve`, { status }),

  // ---- 看板 ----
  dashboardStats: (kbId) => http.get('/api/dashboard/stats', { params: kbId ? { kbId } : {} })
}

export function formatSize(b) {
  if (!b && b !== 0) return '-'
  if (b < 1024) return b + ' B'
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1048576).toFixed(1) + ' MB'
}

export default http
