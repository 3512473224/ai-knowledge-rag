<template>
  <div class="page">
    <div class="page-head">
      <div>
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/kb' }">知识库</el-breadcrumb-item>
          <el-breadcrumb-item>{{ kbName }}</el-breadcrumb-item>
        </el-breadcrumb>
        <h2 style="margin-top: 8px">文档管理</h2>
        <p class="desc">同名文件重复上传会自动生成新版本；检索永远只查当前版本，可随时回滚。</p>
      </div>
      <el-upload
        :http-request="doUpload"
        :show-file-list="false"
        accept=".pdf,.docx,.doc,.txt,.md"
      >
        <el-button type="primary" :icon="Upload">上传文档</el-button>
      </el-upload>
    </div>

    <p class="hint">支持 pdf / docx / txt / md，最大 50MB。上传后自动解析切分并向量化，可点击行查看版本与预览。</p>

    <el-table
      :data="docs"
      v-loading="loading"
      style="width: 100%"
      row-key="id"
      @row-click="(row) => $router.push(`/kb/${kbId}/docs/${row.id}`)"
      class="doc-table"
    >
      <el-table-column prop="fileName" label="文件名" min-width="220">
        <template #default="{ row }">
          <span class="fname">{{ row.fileName }}</span>
          <el-tag v-if="row.currentVersionNo > 1" size="small" effect="plain" class="vtag">
            v{{ row.currentVersionNo }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="fileType" label="类型" width="80" />
      <el-table-column prop="fileSize" label="大小" width="100">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="150">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'DONE'" type="success" effect="plain">
            已入库（{{ row.chunkCount }} 块）
          </el-tag>
          <el-tag v-else-if="row.status === 'FAILED'" type="danger" effect="plain">失败</el-tag>
          <el-tag v-else type="warning" effect="plain">
            <el-icon class="spin"><Loading /></el-icon> 解析中…
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button link type="danger" @click.stop="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div v-if="!docs.length && !loading" class="empty-hero">
      <div class="big-icon">📄</div>
      <h3>这个知识库还是空的</h3>
      <p>上传第一份文档，AI 才能基于它回答问题。</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { api, formatSize } from '../api/http'

const props = defineProps(['kbId'])
const kbName = ref('')
const docs = ref([])
const loading = ref(false)
let timer = null

async function loadKb() {
  try {
    const res = await api.listKb()
    const kb = (res.data || []).find((k) => String(k.id) === String(props.kbId))
    kbName.value = kb ? kb.name : ''
  } catch (e) { /* ignore */ }
}

async function load() {
  loading.value = true
  try {
    const res = await api.listDocuments(props.kbId)
    docs.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function doUpload({ file }) {
  try {
    const res = await api.uploadDocument(file, props.kbId)
    if (res.data.deduped) ElMessage.success('内容完全一致，已秒传复用')
    else ElMessage.success('上传成功，后台解析中…（同名文件会生成新版本）')
    load()
  } catch (e) {
    ElMessage.error('上传失败：' + e.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `删除《${row.fileName}》？其所有版本、向量数据将一并清除。`,
      '确认',
      { type: 'warning' }
    )
    await api.deleteDocument(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(() => {
  loadKb()
  load()
  timer = setInterval(() => {
    if (docs.value.some((d) => d.status === 'PROCESSING')) load()
  }, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style lang="scss" scoped>
.hint {
  color: var(--brand-400);
  font-size: 13px;
  margin: 0 0 16px;
}

.doc-table {
  border-radius: 12px;
  overflow: hidden;

  :deep(.el-table__row) {
    cursor: pointer;
  }
}

.fname {
  font-weight: 600;
  color: var(--brand-800);
}

.vtag {
  margin-left: 8px;
}

.spin {
  animation: spin 1.2s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}
</style>
