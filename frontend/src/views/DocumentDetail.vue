<template>
  <div class="page" v-loading="loading">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/kb' }">知识库</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: `/kb/${kbId}/docs` }">文档管理</el-breadcrumb-item>
      <el-breadcrumb-item>{{ detail.fileName }}</el-breadcrumb-item>
    </el-breadcrumb>

    <div v-if="detail.id" class="detail-grid">
      <!-- 左：文档信息 + 版本时间线 -->
      <div class="left">
        <el-card class="info-card" shadow="never">
          <div class="doc-title">
            <span class="doc-icon">{{ iconFor(detail.fileType) }}</span>
            <div>
              <h2>{{ detail.fileName }}</h2>
              <p class="meta">
                {{ detail.fileType }} · {{ formatSize(detail.fileSize) }} ·
                当前 v{{ currentNo }} · {{ detail.chunkCount }} 个片段
              </p>
            </div>
            <el-tag
              :type="detail.status === 'DONE' ? 'success' : detail.status === 'FAILED' ? 'danger' : 'warning'"
              effect="plain"
            >
              {{ statusText(detail.status) }}
            </el-tag>
          </div>
          <p v-if="detail.errorMsg" class="err">{{ detail.errorMsg }}</p>
        </el-card>

        <el-card class="ver-card" shadow="never">
          <template #header><span class="card-title">版本历史</span></template>
          <el-timeline>
            <el-timeline-item
              v-for="v in detail.versions"
              :key="v.id"
              :type="v.isCurrent ? 'success' : 'info'"
              :hollow="!v.isCurrent"
            >
              <div class="ver-row">
                <div>
                  <b>v{{ v.versionNo }}</b>
                  <el-tag v-if="v.isCurrent" size="small" type="success" effect="plain" class="ml8">
                    当前版本
                  </el-tag>
                  <el-tag v-if="v.status === 'FAILED'" size="small" type="danger" effect="plain" class="ml8">
                    解析失败
                  </el-tag>
                  <p class="ver-meta">
                    {{ v.createTime }} · {{ v.chunkCount }} 个片段 · {{ formatSize(v.fileSize) }}
                  </p>
                  <p v-if="v.errorMsg" class="err">{{ v.errorMsg }}</p>
                </div>
                <el-button
                  v-if="!v.isCurrent && v.status === 'DONE'"
                  size="small"
                  @click="rollback(v)"
                >设为当前版本</el-button>
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </div>

      <!-- 右：在线预览 -->
      <div class="right">
        <el-card class="preview-card" shadow="never">
          <template #header><span class="card-title">在线预览（当前版本）</span></template>
          <div v-if="detail.fileType === 'pdf'" class="pdf-wrap">
            <embed :src="fileUrl" type="application/pdf" class="pdf-embed" />
          </div>
          <div v-else-if="detail.fileType === 'txt' || detail.fileType === 'md'" class="text-preview">
            <div v-if="textLoading" class="preview-loading">加载中…</div>
            <pre v-else>{{ previewText || '暂无内容' }}</pre>
          </div>
          <div v-else class="empty-hero" style="padding: 40px 16px">
            <div class="big-icon">📦</div>
            <h3>该格式不支持在线预览</h3>
            <p>可下载原文件查看。</p>
            <el-button type="primary" @click="download">下载原文件</el-button>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, formatSize } from '../api/http'

const props = defineProps(['kbId', 'docId'])
const detail = ref({})
const loading = ref(false)
const previewText = ref('')
const textLoading = ref(false)
const fileUrl = ref('')

const iconFor = (t) => ({ pdf: '📕', docx: '📘', txt: '📄', md: '📝' }[t] || '📄')
const statusText = (s) => ({ DONE: '已入库', FAILED: '解析失败', PROCESSING: '解析中' }[s] || s)

async function load() {
  loading.value = true
  try {
    const res = await api.documentDetail(props.docId)
    detail.value = res.data || {}
    if (detail.value.fileType === 'pdf') {
      fileUrl.value = api.documentFileUrl(props.docId)
    } else if (detail.value.fileType === 'txt' || detail.value.fileType === 'md') {
      loadText()
    }
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function loadText() {
  textLoading.value = true
  try {
    // 后端返回纯文本（responseType=text），拦截器直接透传字符串
    const res = await api.documentText(props.docId)
    previewText.value = typeof res === 'string' ? res : res.data
  } catch (e) {
    previewText.value = ''
  } finally {
    textLoading.value = false
  }
}

async function rollback(v) {
  try {
    await ElMessageBox.confirm(
      `将 v${v.versionNo} 设为当前版本？检索将立即切换到该版本的内容。`,
      '版本回滚',
      { type: 'warning' }
    )
    await api.activateVersion(props.docId, v.id)
    ElMessage.success('已回滚到 v' + v.versionNo)
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '回滚失败')
  }
}

function download() {
  window.open(api.documentFileUrl(props.docId), '_blank')
}

const currentNo = ref(0)

onMounted(async () => {
  await load()
  const cur = (detail.value.versions || []).find((v) => v.isCurrent)
  currentNo.value = cur ? cur.versionNo : 0
})
</script>

<style lang="scss" scoped>
.detail-grid {
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 18px;
  margin-top: 18px;
  align-items: start;
}

.card-title {
  font-weight: 700;
  color: var(--brand-900);
}

.doc-title {
  display: flex;
  gap: 12px;
  align-items: flex-start;

  .doc-icon {
    font-size: 34px;
  }

  h2 {
    margin: 0 0 6px;
    font-size: 17px;
    color: var(--brand-900);
    word-break: break-all;
  }

  .meta {
    margin: 0;
    font-size: 12.5px;
    color: var(--brand-500);
  }

  .el-tag {
    margin-left: auto;
    flex-shrink: 0;
  }
}

.err {
  color: var(--el-color-danger);
  font-size: 13px;
  background: #fef2f2;
  border-radius: 8px;
  padding: 8px 12px;
}

.ver-row {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 10px;

  b {
    font-size: 14px;
    color: var(--brand-800);
  }

  .ver-meta {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--brand-400);
  }
}

.ml8 {
  margin-left: 8px;
}

.pdf-wrap {
  border: 1px solid var(--brand-100);
  border-radius: 10px;
  overflow: hidden;
}

.pdf-embed {
  width: 100%;
  height: 70vh;
  border: none;
  display: block;
}

.text-preview {
  pre {
    white-space: pre-wrap;
    word-break: break-word;
    font-size: 13.5px;
    line-height: 1.8;
    color: var(--brand-700);
    max-height: 70vh;
    overflow-y: auto;
    margin: 0;
    font-family: var(--el-font-family);
  }

  .preview-loading {
    color: var(--brand-400);
    padding: 30px;
    text-align: center;
  }
}

@media (max-width: 960px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
