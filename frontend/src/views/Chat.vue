<template>
  <div class="chat-page">
    <div class="messages" ref="msgBox">
      <div v-if="messages.length === 0" class="empty">
        <h2>👋 你好，我是知识库助手</h2>
        <p>先在「文档管理」上传资料（pdf / docx / txt / md），然后向我提问，我会基于文档内容回答并标注来源。</p>
      </div>
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="bubble">
          <div v-if="m.role === 'user'" class="text">{{ m.content }}</div>
          <div v-else class="markdown" v-html="renderMd(m.content)"></div>
          <div v-if="m.role === 'assistant' && m.sources && m.sources.length" class="sources">
            <el-collapse>
              <el-collapse-item :title="`📎 引用来源（${m.sources.length}）`" name="1">
                <div v-for="s in m.sources" :key="s.index" class="source">
                  <el-tag size="small">[来源{{ s.index }}]</el-tag>
                  <span class="fname">{{ s.fileName }}</span>
                  <el-tag size="small" type="info">相似度 {{ s.score }}</el-tag>
                  <el-tag size="small" type="success">{{ s.matchType }}</el-tag>
                  <p class="preview">{{ s.preview }}</p>
                </div>
              </el-collapse-item>
            </el-collapse>
          </div>
        </div>
      </div>
      <div v-if="loading && !streaming" class="typing">思考中<span class="dots">…</span></div>
    </div>
    <div class="input-bar">
      <el-input
        v-model="question"
        type="textarea"
        :rows="2"
        placeholder="输入问题，回车发送（Shift+回车换行）"
        :disabled="loading"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" :loading="loading" @click="send" class="send-btn">发送</el-button>
      <el-button v-if="messages.length" @click="newChat">新对话</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import { streamChat } from '../api/http'

const messages = ref([])
const question = ref('')
const loading = ref(false)
const streaming = ref(false)
const sessionId = ref(null)
const msgBox = ref(null)

const renderMd = (text) => marked.parse(text || '')

const scrollBottom = () => nextTick(() => {
  const el = msgBox.value
  if (el) el.scrollTop = el.scrollHeight
})

async function send() {
  const q = question.value.trim()
  if (!q || loading.value) return
  question.value = ''
  messages.value.push({ role: 'user', content: q })
  loading.value = true
  streaming.value = false
  scrollBottom()

  const aiMsg = { role: 'assistant', content: '', sources: [] }
  messages.value.push(aiMsg)

  try {
    await streamChat(sessionId.value, q, (name, data) => {
      if (name === 'token') {
        streaming.value = true
        aiMsg.content += data === '[DONE]' ? '' : data
        scrollBottom()
      } else if (name === 'sources') {
        try { aiMsg.sources = JSON.parse(data) } catch (e) { /* ignore */ }
      } else if (name === 'done') {
        if (data) sessionId.value = Number(data)
      }
    })
  } catch (e) {
    if (aiMsg.content === '') aiMsg.content = '❌ 出错了：' + (e.message || '未知错误')
    ElMessage.error(e.message || '请求失败')
  } finally {
    loading.value = false
    streaming.value = false
    scrollBottom()
  }
}

function newChat() {
  messages.value = []
  sessionId.value = null
}
</script>

<style scoped>
.chat-page { display: flex; flex-direction: column; height: 100vh; }
.messages { flex: 1; overflow-y: auto; padding: 24px; max-width: 900px; width: 100%; margin: 0 auto; box-sizing: border-box; }
.empty { text-align: center; color: #909399; margin-top: 15vh; }
.msg { display: flex; margin-bottom: 16px; }
.msg.user { justify-content: flex-end; }
.bubble { max-width: 80%; padding: 12px 16px; border-radius: 12px; line-height: 1.7; }
.msg.user .bubble { background: #409eff; color: #fff; }
.msg.assistant .bubble { background: #f5f7fa; }
.markdown :deep(p) { margin: 0.4em 0; }
.markdown :deep(pre) { background: #282c34; color: #abb2bf; padding: 12px; border-radius: 8px; overflow-x: auto; }
.markdown :deep(code) { font-family: Consolas, monospace; }
.sources { margin-top: 10px; }
.source { margin-bottom: 8px; }
.fname { margin: 0 8px; font-weight: 600; }
.preview { color: #909399; font-size: 13px; margin: 4px 0 0; }
.typing { color: #909399; padding: 8px 24px; max-width: 900px; margin: 0 auto; width: 100%; box-sizing: border-box; }
.dots { animation: blink 1s infinite; }
@keyframes blink { 50% { opacity: 0.2; } }
.input-bar { display: flex; gap: 10px; padding: 16px 24px; border-top: 1px solid #e6e8eb; max-width: 900px; width: 100%; margin: 0 auto; box-sizing: border-box; align-items: flex-end; }
.send-btn { height: 52px; }
</style>
