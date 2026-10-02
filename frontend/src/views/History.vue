<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>问答历史</h2>
        <p class="desc">所有问答会话都在这里，可按知识库筛选、按标题搜索。</p>
      </div>
      <div class="filters">
        <el-select v-model="kbFilter" placeholder="全部知识库" clearable class="w160" @change="load">
          <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索会话标题"
          clearable
          class="w220"
          :prefix-icon="Search"
          @keyup.enter="load"
          @clear="load"
        />
        <el-button type="primary" @click="load">搜索</el-button>
      </div>
    </div>

    <div v-if="!sessions.length && !loading" class="empty-hero">
      <div class="big-icon">💬</div>
      <h3>暂无会话</h3>
      <p>去问答页提第一个问题吧。</p>
      <el-button type="primary" @click="$router.push('/chat')">去问答</el-button>
    </div>

    <el-card
      v-for="s in sessions"
      :key="s.id"
      class="session-card"
      shadow="hover"
      v-loading="loading"
    >
      <template #header>
        <div class="card-head">
          <div>
            <span class="title">{{ s.title }}</span>
            <el-tag v-if="kbName(s.kbId)" size="small" effect="plain" class="ml8">
              {{ kbName(s.kbId) }}
            </el-tag>
          </div>
          <div>
            <el-button link type="primary" @click="$router.push({ path: '/chat', query: { kbId: s.kbId } })">
              继续对话
            </el-button>
            <el-button link type="danger" @click="remove(s.id)">删除</el-button>
          </div>
        </div>
      </template>
      <div v-if="expanded[s.id]">
        <div v-for="m in messages[s.id]" :key="m.id" :class="['hmsg', m.role]">
          <b>{{ m.role === 'user' ? '我' : '助手' }}：</b>
          <span class="htext">{{ m.content.slice(0, 400) }}{{ m.content.length > 400 ? '…' : '' }}</span>
          <el-tag v-if="m.refused" size="small" type="warning" effect="plain" class="ml8">拒答</el-tag>
        </div>
      </div>
      <el-button link type="primary" @click="toggle(s.id)">
        {{ expanded[s.id] ? '收起' : `展开查看（${counts[s.id] ?? ''} 条）` }}
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { api } from '../api/http'

const kbs = ref([])
const sessions = ref([])
const messages = reactive({})
const expanded = reactive({})
const counts = reactive({})
const kbFilter = ref(null)
const keyword = ref('')
const loading = ref(false)

const kbName = (id) => (kbs.value.find((k) => k.id === id) || {}).name

async function load() {
  loading.value = true
  try {
    const [kbRes, sRes] = await Promise.all([
      api.listKb(),
      api.listSessions(kbFilter.value, keyword.value.trim())
    ])
    kbs.value = kbRes.data || []
    sessions.value = sRes.data || []
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function toggle(id) {
  expanded[id] = !expanded[id]
  if (expanded[id] && !messages[id]) {
    try {
      const res = await api.listMessages(id)
      messages[id] = res.data || []
      counts[id] = messages[id].length
    } catch (e) {
      ElMessage.error('加载失败：' + e.message)
    }
  }
}

async function remove(id) {
  try {
    await ElMessageBox.confirm('删除该会话及其全部消息？', '确认', { type: 'warning' })
    await api.deleteSession(id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.filters {
  display: flex;
  gap: 10px;
  align-items: center;

  .w160 { width: 160px; }
  .w220 { width: 220px; }
}

.session-card {
  margin-bottom: 14px;
  border-radius: 12px;
}

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;

  .title {
    font-weight: 700;
    color: var(--brand-900);
  }
}

.ml8 {
  margin-left: 8px;
}

.hmsg {
  margin-bottom: 10px;
  line-height: 1.7;
  font-size: 14px;

  &.assistant .htext {
    color: var(--brand-600);
    white-space: pre-wrap;
  }
}
</style>
