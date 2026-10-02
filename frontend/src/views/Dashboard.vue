<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h2>数据看板</h2>
        <p class="desc">知识库用得怎么样，一屏看完：问了多少、答上多少、用户满不满意。</p>
      </div>
      <el-select v-model="kbId" placeholder="全部知识库" clearable class="w200" @change="load">
        <el-option v-for="kb in kbs" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
    </div>

    <div v-loading="loading">
      <!-- 数字卡片 -->
      <div class="stat-grid">
        <div class="stat-card">
          <div class="label">用户提问</div>
          <div class="value">{{ stats.questionCount }}</div>
          <div class="sub">累计提问次数</div>
        </div>
        <div class="stat-card">
          <div class="label">无答案率</div>
          <div class="value" :class="stats.refusalRate > 20 ? 'rose' : 'emerald'">
            {{ stats.refusalRate }}%
          </div>
          <div class="sub">拒答 / AI 回答总数</div>
        </div>
        <div class="stat-card">
          <div class="label">反馈好评率</div>
          <div class="value emerald">
            {{ stats.feedbackRate == null ? '—' : stats.feedbackRate + '%' }}
          </div>
          <div class="sub">共 {{ stats.feedbackTotal }} 条反馈</div>
        </div>
        <div class="stat-card">
          <div class="label">文档 / 片段</div>
          <div class="value">{{ stats.docCount }}<span class="unit">篇</span></div>
          <div class="sub">{{ stats.chunkCount }} 个文本片段</div>
        </div>
      </div>

      <div class="dash-grid">
        <!-- 热门问题 -->
        <el-card class="dash-card" shadow="never">
          <template #header><span class="card-title">热门问题 Top10</span></template>
          <div v-if="!stats.topQuestions || !stats.topQuestions.length" class="empty-mini">
            还没有问答数据
          </div>
          <div v-else class="bar-chart">
            <div v-for="t in stats.topQuestions" :key="t.question" class="bar-row">
              <span class="bar-label" :title="t.question">{{ t.question }}</span>
              <div class="bar-track">
                <div class="bar-fill" :style="{ width: barWidth(t.count) }"></div>
              </div>
              <span class="bar-value">{{ t.count }}</span>
            </div>
          </div>
          <p class="card-tip">同一个问题被反复问，说明知识库缺这块内容——去待优化看板看看。</p>
        </el-card>

        <!-- 评估结果 -->
        <el-card class="dash-card" shadow="never">
          <template #header><span class="card-title">RAG 评估（Golden Set）</span></template>
          <div v-if="!stats.evalResult" class="empty-mini">
            <p>还没有评估数据。</p>
            <p class="mono">cd eval && python3 eval.py --kb-id {{ kbId || 1 }}</p>
            <p>跑完后结果会自动显示在这里。</p>
          </div>
          <div v-else class="eval-box">
            <div class="eval-time">评估时间：{{ stats.evalResult.time }} · 共 {{ stats.evalResult.total }} 题</div>
            <div class="eval-row">
              <span>召回率 Recall@5</span>
              <b class="emerald">{{ pct(stats.evalResult.recallAt5) }}</b>
              <span class="eval-detail">{{ stats.evalResult.recallDetail }}</span>
            </div>
            <div class="eval-row">
              <span>拒答正确率</span>
              <b class="emerald">{{ pct(stats.evalResult.refusalAccuracy) }}</b>
              <span class="eval-detail">{{ stats.evalResult.refusalDetail }}</span>
            </div>
            <div class="eval-row">
              <span>平均首字延迟</span>
              <b>{{ stats.evalResult.avgFirstTokenLatencySec }}s</b>
            </div>
          </div>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api/http'

const kbs = ref([])
const kbId = ref(null)
const loading = ref(false)
const stats = ref({
  questionCount: 0,
  refusalRate: 0,
  topQuestions: [],
  feedbackRate: null,
  feedbackTotal: 0,
  docCount: 0,
  chunkCount: 0,
  evalResult: null
})

const pct = (v) => (v == null ? '—' : (v * 100).toFixed(1) + '%')
const barWidth = (c) => {
  const max = Math.max(...(stats.value.topQuestions || []).map((t) => t.count), 1)
  return Math.max(4, (c / max) * 100) + '%'
}

async function load() {
  loading.value = true
  try {
    const [kbRes, stRes] = await Promise.all([api.listKb(), api.dashboardStats(kbId.value)])
    kbs.value = kbRes.data || []
    if (stRes.data) stats.value = stRes.data
  } catch (e) {
    ElMessage.error('加载失败：' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style lang="scss" scoped>
.w200 {
  width: 200px;
}

.unit {
  font-size: 15px;
  font-weight: 600;
  color: var(--brand-400);
  margin-left: 4px;
}

.dash-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px;
}

.dash-card {
  border-radius: 14px;
}

.card-title {
  font-weight: 700;
  color: var(--brand-900);
}

.card-tip {
  margin: 14px 0 0;
  font-size: 12.5px;
  color: var(--brand-400);
  line-height: 1.6;
}

.empty-mini {
  color: var(--brand-400);
  font-size: 14px;
  text-align: center;
  padding: 30px 10px;

  .mono {
    font-family: Consolas, monospace;
    background: var(--brand-900);
    color: #a7f3d0;
    display: inline-block;
    padding: 8px 14px;
    border-radius: 8px;
    font-size: 13px;
  }
}

.eval-box {
  .eval-time {
    font-size: 12.5px;
    color: var(--brand-400);
    margin-bottom: 14px;
  }

  .eval-row {
    display: flex;
    align-items: baseline;
    gap: 10px;
    padding: 10px 0;
    border-bottom: 1px solid var(--brand-100);
    font-size: 14px;

    &:last-child {
      border-bottom: none;
    }

    b {
      font-size: 20px;
      font-variant-numeric: tabular-nums;

      &.emerald {
        color: var(--el-color-primary-dark-2);
      }
    }

    .eval-detail {
      margin-left: auto;
      font-size: 12.5px;
      color: var(--brand-400);
    }
  }
}

@media (max-width: 960px) {
  .dash-grid {
    grid-template-columns: 1fr;
  }
}
</style>
