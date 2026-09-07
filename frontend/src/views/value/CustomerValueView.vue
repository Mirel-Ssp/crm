<template>
  <div class="page">
    <!-- 顶部：分层分布图 + 权重/重算卡 -->
    <el-row :gutter="16" class="top-row">
      <el-col :span="10">
        <el-card shadow="never" v-loading="distLoading">
          <template #header>分层分布</template>
          <div ref="distChartEl" class="chart chart-sm" />
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>五维权重（VA-1 可配）</span>
              <span>
                <el-button v-permission="'va:score:manage'" size="small" plain @click="openWeights">调整权重</el-button>
                <el-button v-permission="'va:score:manage'" size="small" type="primary" plain :loading="recalcing" @click="onRecalc">全量重算</el-button>
              </span>
            </div>
          </template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item v-for="d in DIMS" :key="d.key" :label="d.label">
              {{ percent(weights?.[d.wkey]) }}%
            </el-descriptions-item>
            <el-descriptions-item label="沉默阈值">超 {{ weights?.SILENT_DAYS ?? '-' }} 天无交易/跟进</el-descriptions-item>
            <el-descriptions-item label="每日重算" :span="2">每日 02:00 自动执行（可手动触发）</el-descriptions-item>
          </el-descriptions>
          <p class="hint">分层：高价值 ≥80 · 潜力 60-79 · 待激活 40-59 · 流失风险 &lt;40</p>
        </el-card>
      </el-col>
    </el-row>

    <el-tabs v-model="activeTab" class="mt8">
      <!-- Tab1：评分列表 -->
      <el-tab-pane label="客户评分" name="scores">
        <el-card shadow="never">
          <el-form inline @submit.prevent>
            <el-form-item>
              <el-input v-model="query.keyword" placeholder="客户名称搜索" clearable style="width: 200px" @keyup.enter="loadScores" />
            </el-form-item>
            <el-form-item>
              <el-select v-model="query.tier" placeholder="分层" clearable style="width: 130px">
                <el-option v-for="(v, k) in SCORE_TIER" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadScores">查询</el-button>
              <el-button @click="resetScoreQuery">重置</el-button>
            </el-form-item>
          </el-form>
          <el-table :data="scoreRows" v-loading="scoreLoading" stripe size="small">
            <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
            <el-table-column label="交易频率" width="90" align="right">
              <template #default="{ row }">{{ fmtDim(row.dimFreq) }}</template>
            </el-table-column>
            <el-table-column label="交易额" width="90" align="right">
              <template #default="{ row }">{{ fmtDim(row.dimAmount) }}</template>
            </el-table-column>
            <el-table-column label="最近活跃" width="90" align="right">
              <template #default="{ row }">{{ fmtDim(row.dimActive) }}</template>
            </el-table-column>
            <el-table-column label="汇款及时" width="90" align="right">
              <template #default="{ row }">{{ fmtDim(row.dimRemittance) }}</template>
            </el-table-column>
            <el-table-column label="跟进响应" width="90" align="right">
              <template #default="{ row }">{{ fmtDim(row.dimFollowup) }}</template>
            </el-table-column>
            <el-table-column label="总分" width="90" align="right">
              <template #default="{ row }"><b class="score">{{ Number(row.score).toFixed(1) }}</b></template>
            </el-table-column>
            <el-table-column label="分层" width="100">
              <template #default="{ row }">
                <el-tag :type="SCORE_TIER[row.tier]?.tag || 'info'" effect="plain">
                  {{ SCORE_TIER[row.tier]?.label || row.tier }}
                </el-tag>
                <el-tag v-if="row.manual === 1" size="small" type="warning" effect="plain" class="ml4">手动</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="计算日期" width="110">
              <template #default="{ row }">{{ row.calcDate }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTrend(row)">趋势</el-button>
                <el-button v-permission="'va:score:manage'" link type="warning" @click="openManual(row)">调分</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager" layout="total, prev, pager, next, sizes"
            :total="scoreTotal" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
            :page-sizes="[10, 20, 50]" @current-change="loadScores" @size-change="loadScores"
          />
        </el-card>
      </el-tab-pane>

      <!-- Tab2：沉默客户唤醒（VA-5） -->
      <el-tab-pane :label="`沉默唤醒（${silentRows.length}）`" name="silent">
        <el-card shadow="never">
          <el-table :data="silentRows" v-loading="silentLoading" stripe size="small">
            <el-table-column prop="customerName" label="客户" min-width="140" show-overflow-tooltip />
            <el-table-column label="所属业务员" width="120">
              <template #default="{ row }">{{ row.ownerName || '未分配' }}</template>
            </el-table-column>
            <el-table-column label="最近交易" width="110">
              <template #default="{ row }">{{ row.lastOrderAt?.slice(0, 10) || '—' }}</template>
            </el-table-column>
            <el-table-column label="最近跟进" width="110">
              <template #default="{ row }">{{ row.lastFollowupAt?.slice(0, 10) || '—' }}</template>
            </el-table-column>
            <el-table-column label="沉默天数" width="100" align="right">
              <template #default="{ row }">
                <el-tag type="danger" effect="plain">{{ row.silentDays }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-permission="'va:score:manage'" link type="primary" @click="openWake(row)">唤醒分配</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!silentLoading && silentRows.length === 0" description="暂无沉默客户" :image-size="70" />
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- 评分趋势 -->
    <el-dialog v-model="trendVisible" :title="`评分趋势：${trendRow?.customerName || ''}`" width="640px">
      <el-radio-group v-model="trendDays" size="small" class="mb8" @change="loadTrend">
        <el-radio-button :value="30">近 30 天</el-radio-button>
        <el-radio-button :value="90">近 90 天</el-radio-button>
      </el-radio-group>
      <div ref="trendChartEl" class="chart chart-trend" v-loading="trendLoading" />
    </el-dialog>

    <!-- 权重配置 -->
    <el-dialog v-model="weightsVisible" title="五维权重配置（和必须为 1）" width="480px">
      <el-form label-width="110px">
        <el-form-item v-for="d in DIMS" :key="d.key" :label="d.label">
          <el-input-number v-model="weightForm[d.key]" :min="0" :max="1" :step="0.05" :precision="2" style="width: 160px" />
        </el-form-item>
        <el-form-item label="沉默阈值(天)">
          <el-input-number v-model="weightForm.SILENT_DAYS" :min="7" :max="365" :precision="0" style="width: 160px" />
        </el-form-item>
        <el-alert
          :type="Math.abs(weightSum - 1) < 1e-9 ? 'success' : 'warning'" :closable="false"
          :title="`当前权重和：${weightSum.toFixed(2)}${Math.abs(weightSum - 1) < 1e-9 ? '（合法）' : '（必须等于 1）'}`"
        />
      </el-form>
      <template #footer>
        <el-button @click="weightsVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveWeights">保存并重算</el-button>
      </template>
    </el-dialog>

    <!-- 手动调分 -->
    <el-dialog v-model="manualVisible" :title="`手动调整评分：${manualRow?.customerName || ''}`" width="440px">
      <el-form label-width="90px">
        <el-form-item label="调整分数" required>
          <el-input-number v-model="manualScore" :min="0" :max="100" :precision="1" style="width: 160px" />
        </el-form-item>
        <el-form-item label="调整原因" required>
          <el-input v-model="manualReason" type="textarea" :rows="2" maxlength="200" placeholder="留痕记录，必填" />
        </el-form-item>
        <el-alert type="info" :closable="false" title="手动调整后每日自动重算将跳过该客户，可随时再次调整覆盖" />
      </el-form>
      <template #footer>
        <el-button @click="manualVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onManual">确认调整</el-button>
      </template>
    </el-dialog>

    <!-- 沉默唤醒分配 -->
    <el-dialog v-model="wakeVisible" :title="`唤醒分配：${wakeRow?.customerName || ''}`" width="420px">
      <p class="hint mb8">沉默 {{ wakeRow?.silentDays ?? '-' }} 天 · 将生成一条唤醒跟进待办并指派业务员</p>
      <el-select v-model="wakeAssigneeId" filterable placeholder="选择业务员" style="width: 100%">
        <el-option v-for="u in users" :key="u.id" :label="`${u.realName || u.username}（${u.orgName || '-'}）`" :value="u.id" />
      </el-select>
      <template #footer>
        <el-button @click="wakeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onWake">确认分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getWeights, manualAdjust, pageScores, recalcAll, SCORE_TIER, scoreTrend,
  silentList, tierDistribution, updateWeights, wakeAssign,
  type ScoreRow, type SilentRow, type WeightConfig,
} from '@/api/value'
import { pageUsers, type UserRow } from '@/api/system'
import { useEcharts } from '@/composables/useEcharts'

const DIMS = [
  { key: 'WEIGHT_FREQ', wkey: 'WEIGHT_FREQ', label: '交易频率' },
  { key: 'WEIGHT_AMOUNT', wkey: 'WEIGHT_AMOUNT', label: '累计交易额' },
  { key: 'WEIGHT_ACTIVE', wkey: 'WEIGHT_ACTIVE', label: '最近活跃' },
  { key: 'WEIGHT_REMIT', wkey: 'WEIGHT_REMIT', label: '汇款及时性' },
  { key: 'WEIGHT_FOLLOWUP', wkey: 'WEIGHT_FOLLOWUP', label: '跟进响应度' },
] as const

// ---------- 权重 ----------
const weights = ref<WeightConfig | null>(null)
const weightsVisible = ref(false)
const saving = ref(false)
const weightForm = reactive<Partial<Record<string, number>>>({
  WEIGHT_FREQ: 0.3, WEIGHT_AMOUNT: 0.3, WEIGHT_ACTIVE: 0.2, WEIGHT_REMIT: 0.1, WEIGHT_FOLLOWUP: 0.1, SILENT_DAYS: 30,
})
const weightSum = computed(() =>
  ['WEIGHT_FREQ', 'WEIGHT_AMOUNT', 'WEIGHT_ACTIVE', 'WEIGHT_REMIT', 'WEIGHT_FOLLOWUP']
    .reduce((s, k) => s + (weightForm[k] ?? 0), 0),
)

async function loadWeights() {
  weights.value = await getWeights()
}

function openWeights() {
  if (!weights.value) return
  Object.assign(weightForm, weights.value)
  weightsVisible.value = true
}

async function onSaveWeights() {
  if (Math.abs(weightSum.value - 1) >= 1e-9) {
    ElMessage.warning('五维权重之和必须等于 1')
    return
  }
  saving.value = true
  try {
    await updateWeights({
      WEIGHT_FREQ: weightForm.WEIGHT_FREQ,
      WEIGHT_AMOUNT: weightForm.WEIGHT_AMOUNT,
      WEIGHT_ACTIVE: weightForm.WEIGHT_ACTIVE,
      WEIGHT_REMIT: weightForm.WEIGHT_REMIT,
      WEIGHT_FOLLOWUP: weightForm.WEIGHT_FOLLOWUP,
      SILENT_DAYS: weightForm.SILENT_DAYS,
    })
    ElMessage.success('权重已保存并触发重算')
    weightsVisible.value = false
    await Promise.all([loadWeights(), loadDist(), loadScores()])
  } finally {
    saving.value = false
  }
}

// ---------- 全量重算 ----------
const recalcing = ref(false)

async function onRecalc() {
  recalcing.value = true
  try {
    const n = await recalcAll()
    ElMessage.success(`重算完成，共 ${n} 个客户`)
    await Promise.all([loadDist(), loadScores(), loadSilent()])
  } finally {
    recalcing.value = false
  }
}

// ---------- 分层分布（饼图） ----------
const distChartEl = ref<HTMLElement | null>(null)
const { render: renderDist } = useEcharts(distChartEl)
const distLoading = ref(false)

async function loadDist() {
  distLoading.value = true
  try {
    const rows = await tierDistribution()
    renderDist({
      tooltip: { trigger: 'item', formatter: '{b}：{c} 户（{d}%）' },
      legend: { bottom: 0 },
      series: [{
        type: 'pie',
        radius: ['38%', '64%'],
        center: ['50%', '44%'],
        label: { show: false },
        data: rows.map((r) => ({
          name: SCORE_TIER[r.tier]?.label ?? r.tier,
          value: r.count,
          itemStyle: {
            color: { HIGH_VALUE: '#ef4444', POTENTIAL: '#3b82f6', TO_ACTIVATE: '#f59e0b', AT_RISK: '#94a3b8' }[r.tier],
          },
        })),
      }],
    })
  } finally {
    distLoading.value = false
  }
}

// ---------- 评分列表 ----------
const activeTab = ref('scores')
const query = reactive({ keyword: '', tier: '', pageNum: 1, pageSize: 10 })
const scoreRows = ref<ScoreRow[]>([])
const scoreTotal = ref(0)
const scoreLoading = ref(false)

async function loadScores() {
  scoreLoading.value = true
  try {
    const page = await pageScores({ ...query })
    scoreRows.value = page.list
    scoreTotal.value = Number(page.total)
  } finally {
    scoreLoading.value = false
  }
}

function resetScoreQuery() {
  Object.assign(query, { keyword: '', tier: '', pageNum: 1 })
  loadScores()
}

function fmtDim(v: number): string {
  return v == null ? '-' : Number(v).toFixed(0)
}

function percent(v: number | undefined): string {
  return v == null ? '-' : String(Number(v) * 100)
}

// ---------- 评分趋势 ----------
const trendVisible = ref(false)
const trendRow = ref<ScoreRow | null>(null)
const trendDays = ref(30)
const trendLoading = ref(false)
const trendChartEl = ref<HTMLElement | null>(null)
const { render: renderTrend } = useEcharts(trendChartEl)

async function openTrend(row: ScoreRow) {
  trendRow.value = row
  trendDays.value = 30
  trendVisible.value = true
  await nextTick() // 等对话框内图表容器挂载
  void loadTrend()
}

async function loadTrend() {
  if (!trendRow.value) return
  trendLoading.value = true
  try {
    const rows = await scoreTrend(trendRow.value.customerId, trendDays.value)
    renderTrend({
      tooltip: { trigger: 'axis' },
      legend: { data: ['总分'] },
      grid: { left: 48, right: 24, top: 36, bottom: 28 },
      xAxis: { type: 'category', data: rows.map((r) => r.calcDate) },
      yAxis: { type: 'value', max: 100, name: '评分' },
      series: [{
        name: '总分', type: 'line', smooth: true, data: rows.map((r) => Number(r.score)),
        areaStyle: { opacity: 0.12 }, lineStyle: { width: 2 },
      }],
    })
  } finally {
    trendLoading.value = false
  }
}

// ---------- 手动调分 ----------
const manualVisible = ref(false)
const manualRow = ref<ScoreRow | null>(null)
const manualScore = ref(0)
const manualReason = ref('')

function openManual(row: ScoreRow) {
  manualRow.value = row
  manualScore.value = Number(row.score)
  manualReason.value = ''
  manualVisible.value = true
}

async function onManual() {
  if (!manualRow.value || !manualReason.value.trim()) {
    ElMessage.warning('请填写调整原因')
    return
  }
  saving.value = true
  try {
    await manualAdjust(manualRow.value.customerId, manualScore.value, manualReason.value.trim())
    ElMessage.success('评分已调整（留痕）')
    manualVisible.value = false
    await Promise.all([loadScores(), loadDist()])
  } finally {
    saving.value = false
  }
}

// ---------- 沉默唤醒 ----------
const silentRows = ref<SilentRow[]>([])
const silentLoading = ref(false)
const wakeVisible = ref(false)
const wakeRow = ref<SilentRow | null>(null)
const wakeAssigneeId = ref(0)
const users = ref<UserRow[]>([])

async function loadSilent() {
  silentLoading.value = true
  try {
    silentRows.value = await silentList()
  } finally {
    silentLoading.value = false
  }
}

async function openWake(row: SilentRow) {
  wakeRow.value = row
  wakeAssigneeId.value = 0
  if (users.value.length === 0) {
    const page = await pageUsers({ pageNum: 1, pageSize: 100 })
    users.value = page.list
  }
  wakeVisible.value = true
}

async function onWake() {
  if (!wakeRow.value || !wakeAssigneeId.value) {
    ElMessage.warning('请选择业务员')
    return
  }
  saving.value = true
  try {
    await wakeAssign(wakeRow.value.customerId, wakeAssigneeId.value)
    ElMessage.success('已生成唤醒跟进待办')
    wakeVisible.value = false
    await loadSilent()
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await loadWeights()
  await Promise.all([loadDist(), loadScores(), loadSilent()])
})
</script>

<style scoped>
.top-row {
  margin-bottom: 8px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.chart {
  width: 100%;
}
.chart-sm {
  height: 240px;
}
.chart-trend {
  height: 300px;
}
.hint {
  font-size: 12px;
  color: #94a3b8;
  margin: 8px 0 0;
}
.score {
  color: #2563eb;
}
.ml4 {
  margin-left: 4px;
}
.mt8 {
  margin-top: 8px;
}
.mb8 {
  margin-bottom: 8px;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
