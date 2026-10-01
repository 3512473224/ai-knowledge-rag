<template>
  <div class="history-page">
    <h3>历史会话</h3>
    <el-empty v-if="!sessions.length" description="暂无会话" />
    <el-card v-for="s in sessions" :key="s.id" class="session-card">
      <template #header>
        <div class="card-head">
          <span class="title">{{ s.title }}</span>
          <el-button link type="danger" @click="remove(s.id)">删除</el-button>
        </div>
      </template>
      <div v-if="expanded[s.id]">
        <div v-for="m in messages[s.id]" :key="m.id" :class="['hmsg', m.role]">
          <b>{{ m.role === 'user' ? '我' : '助手' }}：</b>
          <span class="htext">{{ m.content.slice(0, 300) }}{{ m.content.length > 300 ? '…' : '' }}</span>
        </div>
      </div>
      <el-button link type="primary" @click="toggle(s.id)">
        {{ expanded[s.id] ? '收起' : '展开查看' }}
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/http'

const sessions = ref([])
const messages = reactive({})
const expanded = reactive({})

async function load() {
  try {
    const res = await api.listSessions()
    sessions.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  }
}

async function toggle(id) {
  expanded[id] = !expanded[id]
  if (expanded[id] && !messages[id]) {
    const res = await api.listMessages(id)
    messages[id] = res.data || []
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('删除该会话及其全部消息？', '确认')
    await api.deleteSession(id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.history-page { padding: 24px; max-width: 900px; margin: 0 auto; }
.session-card { margin-bottom: 14px; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.title { font-weight: 600; }
.hmsg { margin-bottom: 10px; line-height: 1.6; }
.hmsg.assistant .htext { color: #606266; white-space: pre-wrap; }
</style>
