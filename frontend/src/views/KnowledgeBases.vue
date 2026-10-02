<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>知识库</h2>
        <p class="desc">按主题隔离资料：比如"公司制度""产品手册""课程资料"，问答时先选库再检索，互不干扰。</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button>
    </div>

    <div v-if="!kbs.length && !loading" class="empty-hero">
      <div class="big-icon">📚</div>
      <h3>还没有知识库</h3>
      <p>创建一个知识库，上传文档，就可以开始问答了。</p>
      <el-button type="primary" @click="openCreate">新建知识库</el-button>
    </div>

    <div v-loading="loading" class="card-grid">
      <el-card
        v-for="kb in kbs"
        :key="kb.id"
        class="kb-card"
        shadow="hover"
        @click="$router.push(`/kb/${kb.id}/docs`)"
      >
        <div class="kb-top">
          <span class="kb-icon">📚</span>
          <el-tag :type="kb.visibility === 'public' ? 'success' : 'info'" size="small" effect="plain">
            {{ kb.visibility === 'public' ? '公开' : '私有' }}
          </el-tag>
        </div>
        <h3 class="kb-name">{{ kb.name }}</h3>
        <p class="kb-desc">{{ kb.description || '暂无描述' }}</p>
        <div class="kb-meta">
          <span>📄 {{ kb.docCount }} 篇文档</span>
          <span>💬 {{ kb.questionCount }} 次问答</span>
        </div>
        <div class="kb-actions" @click.stop>
          <el-button link type="primary" @click="$router.push({ path: '/chat', query: { kbId: kb.id } })">去问答</el-button>
          <el-button link @click="openEdit(kb)">编辑</el-button>
          <el-button link type="danger" @click="remove(kb)">删除</el-button>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="dlg" :title="editing ? '编辑知识库' : '新建知识库'" width="460px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="如：公司考勤制度" maxlength="60" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="这个库里放什么资料，方便以后分辨" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="form.visibility">
            <el-radio value="private">私有（仅自己可见）</el-radio>
            <el-radio value="public">公开（所有人可见）</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dlg = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { api } from '../api/http'

const kbs = ref([])
const loading = ref(false)
const dlg = ref(false)
const saving = ref(false)
const editing = ref(null)
const form = ref({ name: '', description: '', visibility: 'private' })

async function load() {
  loading.value = true
  try {
    const res = await api.listKb()
    kbs.value = res.data || []
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', description: '', visibility: 'private' }
  dlg.value = true
}

function openEdit(kb) {
  editing.value = kb
  form.value = { name: kb.name, description: kb.description, visibility: kb.visibility }
  dlg.value = true
}

async function save() {
  if (!form.value.name.trim()) {
    ElMessage.warning('名称不能为空')
    return
  }
  saving.value = true
  try {
    if (editing.value) await api.updateKb(editing.value.id, form.value)
    else await api.createKb(form.value)
    ElMessage.success('已保存')
    dlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function remove(kb) {
  try {
    await ElMessageBox.confirm(
      `删除知识库「${kb.name}」？其下 ${kb.docCount} 篇文档、全部问答记录将被一并清除，不可恢复。`,
      '确认删除',
      { type: 'warning' }
    )
    await api.deleteKb(kb.id)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.message || '删除失败')
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.kb-card {
  border-radius: 14px;

  :deep(.el-card__body) {
    padding: 20px;
  }
}

.kb-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;

  .kb-icon {
    font-size: 30px;
  }
}

.kb-name {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  color: var(--brand-900);
}

.kb-desc {
  margin: 0 0 14px;
  font-size: 13px;
  color: var(--brand-500);
  min-height: 36px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.kb-meta {
  display: flex;
  gap: 14px;
  font-size: 12.5px;
  color: var(--brand-500);
  padding-top: 12px;
  border-top: 1px solid var(--brand-100);
  margin-bottom: 8px;
}

.kb-actions {
  display: flex;
  justify-content: flex-end;
}
</style>
