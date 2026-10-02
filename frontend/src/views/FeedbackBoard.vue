<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>待优化看板</h2>
        <p class="desc">
          用户点了"无用"的回答都会出现在这里。看板就是知识库的体检报告：
          顺着它去补文档，回答质量才会越用越好。
        </p>
      </div>
      <el-radio-group v-model="status" @change="load">
        <el-radio-button value="open">待处理（{{ openCount }}）</el-radio-button>
        <el-radio-button value="fixed">已处理</el-radio-button>
      </el-radio-group>
    </div>

    <div v-if="!items.length && !loading" class="empty-hero">
      <div class="big-icon">{{ status === 'open' ? '🎉' : '📭' }}</div>
      <h3>{{ status === 'open' ? '没有待处理项，回答质量很稳' : '还没有已处理的记录' }}</h3>
      <p>{{ status === 'open' ? '用户点"无用"后会第一时间出现在这里。' : '' }}</p>
    </div>

    <el-card v-for="fb in items" :key="fb.id" class="fb-card" shadow="hover" v-loading="loading">
      <div class="fb-q">
        <span class="q-label">问</span>
        <span class="q-text">{{ fb.question || '（问题已不可见）' }}</span>
        <span class="fb-time">{{ fb.createTime }}</span>
      </div>
      <div class="fb-a">
        <span class="a-label">答</span>
        <span class="a-text">{{ fb.answerPreview }}</span>
      </div>
      <div v-if="fb.note" class="fb-note">
        <b>用户备注：</b>{{ fb.note }}
      </div>
      <div v-if="fb.fileNames && fb.fileNames.length" class="fb-files">
        引用文档：
        <el-tag v-for="f in fb.fileNames" :key="f" size="small" effect="plain" class="ml8">
          {{ f }}
        </el-tag>
      </div>
      <div class="fb-actions">
        <el-button
          v-if="fb.status === 'open'"
          type="primary"
          size="small"
          @click="resolve(fb, 'fixed')"
        >标记已处理</el-button>
        <el-button
          v-else
          size="small"
          @click="resolve(fb, 'open')"
        >重新打开</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

const items = ref([])
const status = ref('open')
const openCount = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [res, openRes] = await Promise.all([
      api.listFeedback(status.value),
      status.value === 'open'
        ? Promise.resolve(null)
        : api.listFeedback('open')
    ])
    items.value = res.data || []
    if (openRes) openCount.value = (openRes.data || []).length
    else openCount.value = items.value.length
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function resolve(fb, to) {
  try {
    await api.resolveFeedback(fb.id, to)
    ElMessage.success(to === 'fixed' ? '已标记为处理完成' : '已重新打开')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.fb-card {
  margin-bottom: 14px;
  border-radius: 12px;
  border-left: 4px solid var(--el-color-warning);
}

.fb-q {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 10px;

  .q-label {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--brand-700);
    color: #fff;
    font-size: 12px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .q-text {
    flex: 1;
    font-weight: 700;
    color: var(--brand-900);
    font-size: 15px;
    line-height: 1.6;
  }

  .fb-time {
    flex-shrink: 0;
    font-size: 12px;
    color: var(--brand-400);
  }
}

.fb-a {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;

  .a-label {
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    border-radius: 50%;
    background: var(--el-color-primary-light-8);
    color: var(--el-color-primary-dark-2);
    font-size: 12px;
    font-weight: 700;
    display: flex;
    align-items: center;
    justify-content: center;
  }

  .a-text {
    color: var(--brand-600);
    font-size: 13.5px;
    line-height: 1.7;
  }
}

.fb-note {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: #92400e;
  margin: 8px 0;
}

.fb-files {
  font-size: 12.5px;
  color: var(--brand-500);
  margin-bottom: 10px;
}

.ml8 {
  margin-left: 6px;
}

.fb-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
