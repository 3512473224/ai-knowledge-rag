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
 * onEvent(name, data)：name = token | sources | done
 */
export async function streamChat(sessionId, question, onEvent, signal) {
  const params = new URLSearchParams()
  if (sessionId) params.append('sessionId', sessionId)
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
  uploadDocument: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/api/documents/upload', fd)
  },
  listDocuments: () => http.get('/api/documents'),
  documentProgress: (id) => http.get(`/api/documents/${id}/progress`),
  deleteDocument: (id) => http.delete(`/api/documents/${id}`),
  listSessions: () => http.get('/api/chat/sessions'),
  listMessages: (id) => http.get(`/api/chat/sessions/${id}/messages`),
  deleteSession: (id) => http.delete(`/api/chat/sessions/${id}`)
}

export default http
