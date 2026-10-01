<template>
  <div class="docs-page">
    <div class="toolbar">
      <el-upload
        :http-request="doUpload"
        :show-file-list="false"
        accept=".pdf,.docx,.doc,.txt,.md"
      >
        <el-button type="primary">📤 上传文档</el-button>
      </el-upload>
      <span class="hint">支持 pdf / docx / txt / md，最大 50MB。上传后自动解析切分并向量化。</span>
    </div>

    <el-table :data="docs" v-loading="loading" style="width: 100%">
      <el-table-column prop="fileName" label="文件名" min-width="220" />
      <el-table-column prop="fileType" label="类型" width="80" />
      <el-table-column prop="fileSize" label="大小" width="110">
        <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="130">
        <template #default="{ row }">
          <el-tag v-if="row.status === 'DONE'" type="success">已入库（{{ row.chunkCount }} 块）</el-tag>
          <el-tag v-else-if="row.status === 'FAILED'" type="danger">失败</el-tag>
          <el-tag v-else type="warning"><el-icon class="spin"><Loading /></el-icon> 解析中…</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMsg" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="上传时间" width="170" />
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api } from '../api/http'

const docs = ref([])
const loading = ref(false)
let timer = null

const formatSize = (b) => {
  if (!b) return '-'
  if (b < 1024) return b + ' B'
  if (b < 1048576) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1048576).toFixed(1) + ' MB'
}

async function load() {
  loading.value = true
  try {
    const res = await api.listDocuments()
    docs.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

async function doUpload({ file }) {
  try {
    const res = await api.uploadDocument(file)
    if (res.data.deduped) ElMessage.success('该文档已存在，直接复用（秒传）')
    else ElMessage.success('上传成功，后台解析中…')
    load()
  } catch (e) {
    ElMessage.error('上传失败：' + e.message)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`删除《${row.fileName}》？其向量数据将一并清除。`, '确认')
    await api.deleteDocument(row.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(() => {
  load()
  // 有文档处于解析中时，每 3 秒轮询进度
  timer = setInterval(() => {
    if (docs.value.some((d) => d.status === 'PROCESSING' || d.status === 'PENDING')) load()
  }, 3000)
})
onUnmounted(() => clearInterval(timer))
</script>

<style scoped>
.docs-page { padding: 24px; max-width: 1100px; margin: 0 auto; }
.toolbar { display: flex; align-items: center; gap: 14px; margin-bottom: 18px; }
.hint { color: #909399; font-size: 13px; }
.spin { animation: spin 1.2s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
