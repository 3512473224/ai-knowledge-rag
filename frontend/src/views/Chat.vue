<template>
  <div class="chat-page">
    <!-- 顶栏：知识库选择 + 新对话 -->
    <div class="chat-topbar">
      <el-select
        v-model="kbId"
        placeholder="选择知识库"
        class="kb-select"
        :disabled="loading"
        @change="onKbChange"
      >
        <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-button :icon="Plus" circle @click="newChat" title="新对话" :disabled="loading" />
    </div>

    <div class="messages" ref="msgBox">
      <!-- 产品介绍区（空态） -->
      <div v-if="messages.length === 0" class="hero">
        <div class="hero-badge">RAG · 混合检索 · 引用溯源</div>
        <h1>把你的文档，变成对答如流的助手</h1>
        <p class="hero-sub">
          上传 PDF / Word / Markdown 资料，AI 只基于你的文档回答，
          每句话都标注来源，答不上来就直说不知道——不编造。
        </p>
        <div class="hero-cards">
          <div class="hero-card" @click="$router.push('/kb')">
            <div class="hc-icon">📚</div>
            <div class="hc-title">建知识库</div>
            <div class="hc-desc">按主题隔离资料，公开或私有</div>
          </div>
          <div class="hero-card" @click="goUpload">
            <div class="hc-icon">📤</div>
            <div class="hc-title">传文档</div>
            <div class="hc-desc">自动解析切分，生成新版本</div>
          </div>
          <div class="hero-card" @click="$router.push('/dashboard')">
            <div class="hc-icon">📊</div>
            <div class="hc-title">看效果</div>
            <div class="hc-desc">无答案率、好评率一目了然</div>
          </div>
        </div>
        <p v-if="!kbs.length" class="hero-tip">
          还没有知识库，<a @click="$router.push('/kb')">去创建一个</a> 开始吧。
        </p>
      </div>

      <!-- 消息流 -->
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="avatar" v-if="m.role === 'assistant'">知</div>
        <div class="bubble">
          <div v-if="m.role === 'user'" class="text">{{ m.content }}</div>
          <div v-else class="markdown" v-html="renderMd(m.content)"></div>

          <!-- 引用来源卡片：点击跳转到文档详情 -->
          <div v-if="m.role === 'assistant' && m.sources && m.sources.length" class="sources">
            <div class="sources-title">引用来源（{{ m.sources.length }}）</div>
            <div
              v-for="s in m.sources"
              :key="s.index"
              class="source-card"
              @click="goDoc(s.documentId)"
            >
              <span class="s-index">[{{ s.index }}]</span>
              <span class="s-name">{{ s.fileName }}</span>
              <el-tag size="small" type="success" effect="plain">{{ s.matchType }}</el-tag>
              <p class="s-preview">{{ s.preview }}</p>
            </div>
          </div>

          <!-- 反馈按钮 -->
          <div v-if="m.role === 'assistant' && m.messageId && !streaming" class="feedback-row">
            <span class="fb-label">这个回答：</span>
            <el-button
              size="small"
              :type="m.feedback === true ? 'success' : 'default'"
              :plain="m.feedback !== true"
              @click="sendFeedback(m, true)"
            >👍 有用</el-button>
            <el-button
              size="small"
              :type="m.feedback === false ? 'danger' : 'default'"
              :plain="m.feedback !== false"
              @click="openUselessDialog(m)"
            >👎 无用</el-button>
          </div>
        </div>
      </div>

      <div v-if="loading && !streaming" class="thinking">
        <span class="pulse-dot"></span> 正在检索资料并组织回答…
      </div>
    </div>

    <!-- 输入栏 -->
    <div class="input-bar">
      <div class="input-wrap">
        <el-input
          v-model="question"
          type="textarea"
          :rows="2"
          :placeholder="kbId ? '输入问题，回车发送（Shift+回车换行）' : '请先在上方选择知识库'"
          :disabled="loading || !kbId"
          @keydown.enter.exact.prevent="send"
        />
        <el-button
          type="primary"
          :loading="loading"
          :disabled="!kbId"
          @click="send"
          class="send-btn"
        >发送</el-button>
      </div>
      <p class="input-tip">AI 回答仅基于知识库资料，并标注来源；无相关资料时会明确拒答。</p>
    </div>

    <!-- 无用原因备注弹窗 -->
    <el-dialog v-model="uselessDlg" title="这个回答哪里不好？" width="420px">
      <el-input
        v-model="uselessNote"
        type="textarea"
        :rows="3"
        placeholder="选填：比如答非所问、引用资料不对、太啰嗦… 你的备注会进入待优化看板"
      />
      <template #footer>
        <el-button @click="uselessDlg = false">取消</el-button>
        <el-button type="primary" @click="confirmUseless">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api, streamChat } from '../api/http'

const router = useRouter()
const route = useRoute()
const kbs = ref([])
const kbId = ref(null)
const messages = ref([])
const question = ref('')
const loading = ref(false)
const streaming = ref(false)
const sessionId = ref(null)
const msgBox = ref(null)
const uselessDlg = ref(false)
const uselessNote = ref('')
const uselessTarget = ref(null)

const renderMd = (text) => marked.parse(text || '')
const scrollBottom = () => nextTick(() => {
  const el = msgBox.value
  if (el) el.scrollTop = el.scrollHeight
})

async function loadKbs() {
  try {
    const res = await api.listKb()
    kbs.value = res.data || []
    const qKb = Number(route.query.kbId)
    if (qKb && kbs.value.some((k) => k.id === qKb)) kbId.value = qKb
    else if (kbs.value.length && !kbId.value) kbId.value = kbs.value[0].id
  } catch (e) {
    ElMessage.error('知识库加载失败：' + e.message)
  }
}

function onKbChange() {
  newChat()
}

function goUpload() {
  if (!kbId.value) {
    ElMessage.warning('请先创建并选择知识库')
    router.push('/kb')
    return
  }
  router.push(`/kb/${kbId.value}/docs`)
}

function goDoc(documentId) {
  if (!documentId || !kbId.value) return
  router.push(`/kb/${kbId.value}/docs/${documentId}`)
}

async function send() {
  const q = question.value.trim()
  if (!q || loading.value || !kbId.value) return
  question.value = ''
  messages.value.push({ role: 'user', content: q })
  loading.value = true
  streaming.value = false
  scrollBottom()

  const aiMsg = { role: 'assistant', content: '', sources: [], messageId: null, feedback: null }
  messages.value.push(aiMsg)

  try {
    await streamChat(sessionId.value, kbId.value, q, (name, data) => {
      if (name === 'token') {
        streaming.value = true
        aiMsg.content += data
        scrollBottom()
      } else if (name === 'sources') {
        try { aiMsg.sources = JSON.parse(data) } catch (e) { /* ignore */ }
      } else if (name === 'messageId') {
        aiMsg.messageId = Number(data)
      } else if (name === 'done') {
        if (data) sessionId.value = Number(data)
      }
    })
  } catch (e) {
    if (aiMsg.content === '') aiMsg.content = '出错了：' + (e.message || '未知错误')
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

async function sendFeedback(m, useful) {
  try {
    await api.submitFeedback(m.messageId, useful, null)
    m.feedback = useful
    ElMessage.success('感谢反馈！')
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  }
}

function openUselessDialog(m) {
  uselessTarget.value = m
  uselessNote.value = ''
  uselessDlg.value = true
}

async function confirmUseless() {
  const m = uselessTarget.value
  if (!m) return
  try {
    await api.submitFeedback(m.messageId, false, uselessNote.value.trim() || null)
    m.feedback = false
    uselessDlg.value = false
    ElMessage.success('已记录到待优化看板')
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  }
}

onMounted(loadKbs)
</script>

<style lang="scss" scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--brand-50);
}

.chat-topbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 14px 24px;
  background: #fff;
  border-bottom: 1px solid var(--brand-100);

  .kb-select {
    width: 260px;
  }
}

.messages {
  flex: 1;
  overflow-y: auto;
  padding: 28px 24px;
}

.hero {
  max-width: 760px;
  margin: 6vh auto 0;
  text-align: center;

  .hero-badge {
    display: inline-block;
    font-size: 12px;
    font-weight: 600;
    color: var(--el-color-primary-dark-2);
    background: var(--el-color-primary-light-9);
    border: 1px solid var(--el-color-primary-light-7);
    padding: 5px 14px;
    border-radius: 20px;
    margin-bottom: 18px;
  }

  h1 {
    font-size: 32px;
    font-weight: 800;
    color: var(--brand-900);
    margin: 0 0 12px;
    letter-spacing: 1px;
  }

  .hero-sub {
    font-size: 15px;
    color: var(--brand-500);
    line-height: 1.8;
    margin: 0 0 28px;
  }

  .hero-cards {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 14px;
    text-align: left;
  }

  .hero-card {
    background: #fff;
    border: 1px solid var(--brand-100);
    border-radius: 14px;
    padding: 20px;
    cursor: pointer;
    transition: transform 0.15s ease, box-shadow 0.15s ease;

    &:hover {
      transform: translateY(-3px);
      box-shadow: 0 10px 28px rgb(15 23 42 / 0.09);
    }

    .hc-icon { font-size: 28px; margin-bottom: 10px; }
    .hc-title { font-weight: 700; color: var(--brand-800); margin-bottom: 4px; }
    .hc-desc { font-size: 13px; color: var(--brand-500); }
  }

  .hero-tip {
    margin-top: 22px;
    font-size: 14px;
    color: var(--brand-500);

    a {
      color: var(--el-color-primary-dark-2);
      cursor: pointer;
      font-weight: 600;
    }
  }
}

.msg {
  display: flex;
  gap: 10px;
  max-width: 860px;
  margin: 0 auto 20px;

  &.user {
    flex-direction: row-reverse;
  }

  .avatar {
    width: 34px;
    height: 34px;
    flex-shrink: 0;
    border-radius: 50%;
    background: linear-gradient(135deg, #10b981, #059669);
    color: #fff;
    font-weight: 800;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
  }

  .bubble {
    max-width: 82%;
    padding: 13px 17px;
    border-radius: 14px;
    line-height: 1.75;
    font-size: 14.5px;
  }

  &.user .bubble {
    background: var(--brand-700);
    color: #fff;
    border-bottom-right-radius: 4px;
  }

  &.assistant .bubble {
    background: #fff;
    border: 1px solid var(--brand-100);
    color: var(--brand-800);
    border-bottom-left-radius: 4px;
    box-shadow: 0 1px 3px rgb(15 23 42 / 0.05);
  }
}

.markdown {
  :deep(p) { margin: 0.45em 0; }
  :deep(pre) {
    background: var(--brand-900);
    color: #e2e8f0;
    padding: 12px 14px;
    border-radius: 10px;
    overflow-x: auto;
    font-size: 13px;
  }
  :deep(code) {
    font-family: Consolas, "Courier New", monospace;
    background: var(--brand-100);
    padding: 1px 6px;
    border-radius: 5px;
    font-size: 13px;
  }
  :deep(pre code) { background: transparent; padding: 0; }
  :deep(table) { border-collapse: collapse; width: 100%; font-size: 13px; }
  :deep(th), :deep(td) { border: 1px solid var(--brand-200); padding: 6px 10px; }
  :deep(th) { background: var(--brand-50); }
}

.sources {
  margin-top: 12px;
  border-top: 1px dashed var(--brand-200);
  padding-top: 10px;

  .sources-title {
    font-size: 12px;
    font-weight: 700;
    color: var(--brand-500);
    margin-bottom: 8px;
  }
}

.source-card {
  background: var(--brand-50);
  border: 1px solid var(--brand-100);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: border-color 0.15s ease;

  &:hover {
    border-color: var(--el-color-primary-light-5);
  }

  .s-index {
    font-weight: 800;
    color: var(--el-color-primary-dark-2);
    margin-right: 6px;
  }

  .s-name {
    font-weight: 600;
    font-size: 13px;
    margin-right: 8px;
  }

  .s-preview {
    margin: 6px 0 0;
    font-size: 12.5px;
    color: var(--brand-500);
    line-height: 1.6;
  }
}

.feedback-row {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 6px;

  .fb-label {
    font-size: 12px;
    color: var(--brand-400);
  }
}

.thinking {
  max-width: 860px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--brand-500);
  font-size: 14px;
  padding: 6px 0;

  .pulse-dot {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: var(--el-color-primary);
    animation: pulse 1.2s ease-in-out infinite;
  }
}

@keyframes pulse {
  50% { opacity: 0.25; transform: scale(0.8); }
}

.input-bar {
  background: #fff;
  border-top: 1px solid var(--brand-100);
  padding: 14px 24px 10px;

  .input-wrap {
    display: flex;
    gap: 10px;
    max-width: 860px;
    margin: 0 auto;
    align-items: flex-end;
  }

  .send-btn {
    height: 56px;
    padding: 0 28px;
    font-size: 15px;
    font-weight: 600;
  }

  .input-tip {
    max-width: 860px;
    margin: 8px auto 0;
    font-size: 12px;
    color: var(--brand-400);
    text-align: center;
  }
}

@media (max-width: 768px) {
  .hero .hero-cards { grid-template-columns: 1fr; }
  .hero h1 { font-size: 24px; }
  .msg .bubble { max-width: 92%; }
}
</style>
