<template>
  <div class="page">
    <el-row :gutter="16">
      <!-- 客户报表 -->
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>客户报表（等级 / 生命周期）</span>
              <el-button size="small" plain @click="download('customer')">导出 CSV</el-button>
            </div>
          </template>
          <p class="sub">客户总数：{{ customerData?.total ?? 0 }}</p>
          <el-table :data="customerData?.byLevel ?? []" size="small">
            <el-table-column prop="name" label="等级" />
            <el-table-column prop="count" label="数量" width="80" align="right" />
          </el-table>
          <el-table :data="customerData?.byLifecycle ?? []" size="small" class="mt8">
            <el-table-column prop="name" label="生命周期" />
            <el-table-column prop="count" label="数量" width="80" align="right" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 线索报表 -->
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>线索报表（状态 / 转化率）</span>
              <el-button size="small" plain @click="download('lead')">导出 CSV</el-button>
            </div>
          </template>
          <p class="sub">
            线索总数：{{ leadData?.total ?? 0 }} · 转化率：<b class="rate">{{ leadData?.conversionRate ?? 0 }}%</b>
          </p>
          <el-table :data="leadData?.byStatus ?? []" size="small">
            <el-table-column prop="name" label="状态" />
            <el-table-column prop="count" label="数量" width="80" align="right" />
          </el-table>
        </el-card>
      </el-col>

      <!-- 商机报表 -->
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span>商机报表（漏斗 / 丢单原因）</span>
              <el-button size="small" plain @click="download('opportunity')">导出 CSV</el-button>
            </div>
          </template>
          <p class="sub">
            商机总数：{{ oppData?.funnel.reduce((s, f) => s + f.count, 0) ?? 0 }} · 加权预测合计：
            <b class="rate">¥ {{ fmt(weightedTotal) }}</b>
          </p>
          <el-table :data="oppData?.funnel ?? []" size="small">
            <el-table-column label="阶段">
              <template #default="{ row }">{{ stageLabel(String(row.stage)) }}</template>
            </el-table-column>
            <el-table-column prop="count" label="数量" width="64" align="right" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥ {{ fmt(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="加权" width="100" align="right">
              <template #default="{ row }">¥ {{ fmt(row.weightedAmount) }}</template>
            </el-table-column>
          </el-table>
          <el-table :data="oppData?.lossStats ?? []" size="small" class="mt8">
            <el-table-column prop="reason" label="丢单原因" />
            <el-table-column prop="count" label="数量" width="64" align="right" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">¥ {{ fmt(row.amount) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表化补齐（批次6 B6-06：等级/生命周期/线索状态/商机漏斗/丢单原因） -->
    <el-row :gutter="16" class="mt16">
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header><span class="chart-head">客户分布图</span></template>
          <div ref="levelChartEl" class="chart chart-half"></div>
          <div ref="lifecycleChartEl" class="chart chart-half"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header><span class="chart-head">线索状态分布</span></template>
          <div ref="leadChartEl" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="never" v-loading="loading">
          <template #header><span class="chart-head">商机漏斗与丢单原因</span></template>
          <div ref="funnelChartEl" class="chart chart-half"></div>
          <div ref="lossChartEl" class="chart chart-half"></div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 跟进及时率监控（CRM-F3） -->
    <el-card shadow="never" class="mt16" v-loading="ftLoading">
      <template #header>
        <div class="card-head">
          <span class="chart-head">跟进及时率监控（CRM-F3）</span>
          <div>
            <el-input-number v-model="thresholdDays" :min="1" :max="365" size="small"
              style="width: 110px" @change="loadTimeliness" />
            <span class="sub" style="margin: 0 8px 0 4px">天阈值</span>
            <el-button size="small" plain @click="download('followup-timeliness')">导出 CSV</el-button>
          </div>
        </div>
      </template>
      <el-row :gutter="12" class="ft-kpi">
        <el-col :span="6"><p class="sub">活跃客户</p><p class="ft-num">{{ ft?.totalActive ?? 0 }}</p></el-col>
        <el-col :span="6"><p class="sub">超时未跟进</p>
          <p class="ft-num" :class="{ danger: (ft?.overdueCount ?? 0) > 0 }">{{ ft?.overdueCount ?? 0 }}</p></el-col>
        <el-col :span="6"><p class="sub">跟进及时率</p>
          <p class="ft-num" :class="{ danger: (ft?.timelyRate ?? 100) < 80 }">{{ ft?.timelyRate ?? 0 }}%</p></el-col>
        <el-col :span="6"><p class="sub">超期待办任务</p>
          <p class="ft-num" :class="{ danger: (ft?.overdueTodoCount ?? 0) > 0 }">{{ ft?.overdueTodoCount ?? 0 }}</p></el-col>
      </el-row>
      <el-tabs>
        <el-tab-pane :label="`超时未跟进客户（${ft?.overdueList.length ?? 0}）`">
          <el-table :data="ft?.overdueList ?? []" size="small" stripe max-height="360">
            <el-table-column prop="name" label="客户" min-width="140" show-overflow-tooltip />
            <el-table-column prop="ownerName" label="负责人" width="100" />
            <el-table-column label="等级" width="80">
              <template #default="{ row }">
                <el-tag :type="row.level === 'VIP' ? 'warning' : row.level === 'IMPORTANT' ? 'primary' : 'info'" size="small">
                  {{ levelLabel(row.level) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="最近跟进" width="160">
              <template #default="{ row }">{{ row.lastFollowupAt ? fmtTime(row.lastFollowupAt) : '从未跟进' }}</template>
            </el-table-column>
            <el-table-column label="超时天数" width="90" align="right">
              <template #default="{ row }">
                <span :class="{ danger: row.overdueDays >= 30 }">{{ Math.floor(row.overdueDays) }} 天</span>
              </template>
            </el-table-column>
            <el-table-column prop="followupCount" label="累计跟进" width="90" align="right" />
          </el-table>
        </el-tab-pane>
        <el-tab-pane :label="`超期待办任务（${ft?.overdueTodos.length ?? 0}）`">
          <el-table :data="ft?.overdueTodos ?? []" size="small" stripe max-height="360">
            <el-table-column prop="ownerName" label="负责人" width="100" />
            <el-table-column label="对象" width="100">
              <template #default="{ row }">{{ relTypeLabel(row.relType) }}#{{ row.relId }}</template>
            </el-table-column>
            <el-table-column prop="content" label="待办内容" min-width="220" show-overflow-tooltip />
            <el-table-column label="计划跟进时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.nextFollowupAt) }}</template>
            </el-table-column>
            <el-table-column label="超时天数" width="90" align="right">
              <template #default="{ row }">
                <span :class="{ danger: row.overdueDays >= 3 }">{{ Math.floor(row.overdueDays) }} 天</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import {
  exportReport, reportCustomer, reportFollowupTimeliness, reportLead, reportOpportunity,
  type DistRow, type FollowupTimelinessData, type FunnelRow, type LossRow,
} from '@/api/crm'
import { useDict } from '@/composables/useDict'
import { useEcharts, type EchartsOption } from '@/composables/useEcharts'

const { labelOfDict: stageLabel } = useDict('opportunity_stage')
const { labelOfDict: levelLabel } = useDict('customer_level')
const fmt = (n: number | null | undefined) =>
  (n ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
const fmtTime = (t: string) => t.replace('T', ' ').slice(0, 16)
const relTypeLabel = (t: string) =>
  ({ CUSTOMER: '客户', LEAD: '线索', OPPORTUNITY: '商机', TICKET: '工单' }[t] ?? t)

const loading = ref(false)
const customerData = ref<{ total: number; byLevel: DistRow[]; byLifecycle: DistRow[] } | null>(null)
const leadData = ref<{ total: number; byStatus: DistRow[]; conversionRate: number } | null>(null)
const oppData = ref<{ funnel: FunnelRow[]; lossStats: LossRow[] } | null>(null)

const weightedTotal = computed(() =>
  (oppData.value?.funnel ?? []).reduce((s, f) => s + f.weightedAmount, 0),
)

// ---------- 图表（批次6 B6-06：表格之上补充可视化） ----------
const levelChartEl = ref<HTMLElement | null>(null)
const lifecycleChartEl = ref<HTMLElement | null>(null)
const leadChartEl = ref<HTMLElement | null>(null)
const funnelChartEl = ref<HTMLElement | null>(null)
const lossChartEl = ref<HTMLElement | null>(null)

const { render: renderLevel } = useEcharts(levelChartEl)
const { render: renderLifecycle } = useEcharts(lifecycleChartEl)
const { render: renderLead } = useEcharts(leadChartEl)
const { render: renderFunnel } = useEcharts(funnelChartEl)
const { render: renderLoss } = useEcharts(lossChartEl)

function pieOption(data: { name: string; count: number }[]): EchartsOption {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}（{d}%）' },
    legend: { bottom: 0, type: 'scroll', textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie', radius: ['32%', '56%'], center: ['50%', '42%'],
      data: data.map((d) => ({ name: d.name, value: d.count })),
      label: { show: false },
    }],
  }
}

function barOption(data: DistRow[]): EchartsOption {
  return {
    tooltip: { trigger: 'axis' },
    grid: { left: 8, right: 12, top: 14, bottom: 0, containLabel: true },
    xAxis: { type: 'category', data: data.map((d) => d.name), axisLabel: { fontSize: 10, interval: 0, rotate: 24 } },
    yAxis: { type: 'value', minInterval: 1 },
    series: [{
      type: 'bar', data: data.map((d) => d.count), barMaxWidth: 24,
      itemStyle: { color: '#409eff', borderRadius: [3, 3, 0, 0] },
    }],
  }
}

function funnelOption(rows: FunnelRow[]): EchartsOption {
  return {
    tooltip: { trigger: 'item', formatter: '{b}: {c}' },
    series: [{
      type: 'funnel', sort: 'none', left: '6%', width: '88%', top: 8, bottom: 8, minSize: '28%',
      label: { show: true, position: 'inside', fontSize: 11, formatter: '{b}  {c}' },
      data: rows.map((f) => ({ name: stageLabel(String(f.stage)), value: f.count })),
    }],
  }
}

async function load() {
  loading.value = true
  try {
    const [c, l, o] = await Promise.all([reportCustomer(), reportLead(), reportOpportunity()])
    customerData.value = c
    leadData.value = l
    oppData.value = o
    renderLevel(pieOption(c.byLevel))
    renderLifecycle(barOption(c.byLifecycle))
    renderLead(pieOption(l.byStatus))
    renderFunnel(funnelOption(o.funnel))
    renderLoss(pieOption(o.lossStats.map((s) => ({ name: s.reason || '未填写', count: s.count }))))
  } finally {
    loading.value = false
  }
}

/** CSV blob 下载（UTF-8 BOM 由后端写入） */
async function download(type: 'customer' | 'lead' | 'opportunity' | 'followup-timeliness') {
  const blob = await exportReport(type)
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${type}-report-${new Date().toISOString().slice(0, 10)}.csv`
  a.click()
  URL.revokeObjectURL(url)
}

// ---------- 跟进及时率监控（CRM-F3） ----------
const ftLoading = ref(false)
const ft = ref<FollowupTimelinessData | null>(null)
const thresholdDays = ref(7)

async function loadTimeliness() {
  ftLoading.value = true
  try {
    ft.value = await reportFollowupTimeliness(thresholdDays.value)
  } finally {
    ftLoading.value = false
  }
}

onMounted(() => {
  load()
  loadTimeliness()
})
</script>

<style scoped>
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ft-kpi {
  margin-bottom: 8px;
}
.ft-num {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 2px 0 8px;
}
.ft-num.danger,
.danger {
  color: #dc2626;
}
.chart-head {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}
.mt16 {
  margin-top: 16px;
}
.chart {
  width: 100%;
  height: 320px;
}
.chart-half {
  height: 158px;
}
.sub {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 8px;
}
.rate {
  color: #2563eb;
}
.mt8 {
  margin-top: 8px;
}
</style>
