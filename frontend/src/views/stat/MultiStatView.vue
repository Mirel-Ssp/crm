<template>
  <div class="page">
    <!-- 工具栏：维度/日期/重建 -->
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item label="维度">
          <el-radio-group v-model="dim" @change="loadAll">
            <el-radio-button v-for="d in STAT_DIMS" :key="d.value" :value="d.value">{{ d.label }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="范围">
          <el-date-picker
            v-model="range" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始" end-placeholder="结束" style="width: 240px" clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadAll">查询</el-button>
          <el-button plain :loading="rebuilding" @click="onRebuild">重建聚合</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 总量统计卡（ST-1 同环比） -->
    <el-row :gutter="16" class="card-row">
      <el-col :span="6">
        <el-card shadow="never" v-loading="loading">
          <p class="stat-label">订单笔数</p>
          <p class="stat-value">{{ fmt(summary?.totalCount) }}</p>
          <p class="chain" :class="chainClass(summary?.countChainRatio)">
            环比 {{ chainText(summary?.countChainRatio) }}
          </p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" v-loading="loading">
          <p class="stat-label">订单金额</p>
          <p class="stat-value">¥ {{ fmt(summary?.totalAmount) }}</p>
          <p class="chain" :class="chainClass(summary?.amountChainRatio)">
            环比 {{ chainText(summary?.amountChainRatio) }}
          </p>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" v-loading="loading">
          <p class="stat-label">成交客户数</p>
          <p class="stat-value">{{ fmt(totalCustomers) }}</p>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" v-loading="loading">
          <p class="stat-label">确认汇款额</p>
          <p class="stat-value">¥ {{ fmt(totalRemit) }}</p>
        </el-card>
      </el-col>
      <el-col :span="4">
        <el-card shadow="never" v-loading="loading">
          <p class="stat-label">到账率</p>
          <p class="stat-value">{{ arriveRateText }}</p>
        </el-card>
      </el-col>
    </el-row>

    <!-- 趋势图 + 业绩排名 -->
    <el-row :gutter="16" class="card-row">
      <el-col :span="14">
        <el-card shadow="never" v-loading="loading">
          <template #header>交易趋势（金额折线 / 笔数柱状）</template>
          <div ref="trendChartEl" class="chart chart-md" />
        </el-card>
      </el-col>
      <el-col :span="10">
        <el-card shadow="never" v-loading="rankLoading">
          <template #header>业务员业绩排名（ST-4）</template>
          <el-table :data="rankRows" size="small" stripe max-height="300">
            <el-table-column label="#" width="46">
              <template #default="{ $index }">
                <el-tag v-if="$index < 3" size="small" :type="(['danger', 'warning', 'success'] as const)[$index]" effect="dark">{{ $index + 1 }}</el-tag>
                <span v-else>{{ $index + 1 }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="ownerName" label="业务员" min-width="90" show-overflow-tooltip />
            <el-table-column prop="orderCount" label="笔数" width="70" align="right" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">¥ {{ fmt(row.orderAmount) }}</template>
            </el-table-column>
            <el-table-column label="到账率" width="80" align="right">
              <template #default="{ row }">{{ rateText(row.arriveRate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 标的分析 -->
    <el-row :gutter="16" class="card-row">
      <el-col :span="10">
        <el-card shadow="never" v-loading="itemLoading">
          <template #header>标的交易占比（ST-5）</template>
          <div ref="itemChartEl" class="chart chart-md" />
        </el-card>
      </el-col>
      <el-col :span="14">
        <el-card shadow="never" v-loading="itemLoading">
          <template #header>标的分析明细</template>
          <el-table :data="itemRows" size="small" stripe max-height="300">
            <el-table-column label="#" width="46">
              <template #default="{ row }">{{ row.rank }}</template>
            </el-table-column>
            <el-table-column prop="itemCode" label="代码" width="100" />
            <el-table-column prop="itemName" label="标的" min-width="120" show-overflow-tooltip />
            <el-table-column prop="orderCount" label="笔数" width="70" align="right" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">¥ {{ fmt(row.orderAmount) }}</template>
            </el-table-column>
            <el-table-column label="到账率" width="80" align="right">
              <template #default="{ row }">{{ rateText(row.arriveRate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 交易明细（ST-3） -->
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>汇款交易明细</span>
          <el-button size="small" plain @click="onExport">导出 CSV</el-button>
        </div>
      </template>
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="detailQuery.customerId" placeholder="客户 ID" clearable style="width: 110px" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="detailQuery.itemId" placeholder="标的 ID" clearable style="width: 110px" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="detailQuery.status" placeholder="订单状态" clearable style="width: 150px">
            <el-option v-for="(v, k) in ORDER_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="detailQuery.direction" placeholder="方向" clearable style="width: 110px">
            <el-option label="买入 BUY" value="BUY" />
            <el-option label="卖出 SELL" value="SELL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-date-picker
            v-model="detailRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始" end-placeholder="结束" style="width: 240px" clearable
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadDetail(1)">查询</el-button>
          <el-button @click="resetDetail">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="detailRows" v-loading="detailLoading" stripe size="small">
        <el-table-column prop="orderNo" label="订单号" width="160" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
        <el-table-column prop="itemName" label="标的" min-width="110" show-overflow-tooltip />
        <el-table-column label="方向" width="70">
          <template #default="{ row }">
            <el-tag :type="row.direction === 'BUY' ? 'primary' : 'success'" effect="plain" size="small">
              {{ row.direction === 'BUY' ? '买入' : '卖出' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="quantity" label="数量" width="90" align="right" />
        <el-table-column label="价格" width="100" align="right">
          <template #default="{ row }">{{ fmt(row.price) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="110" align="right">
          <template #default="{ row }">¥ {{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="费用" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.feeAmount) }}</template>
        </el-table-column>
        <el-table-column label="总额" width="110" align="right">
          <template #default="{ row }">¥ {{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="订单状态" width="100">
          <template #default="{ row }">{{ ORDER_STATUS[row.status]?.label || row.status }}</template>
        </el-table-column>
        <el-table-column prop="remitStatus" label="核销" width="120">
          <template #default="{ row }">{{ remitLabel(row.remitStatus) }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="业务员" width="90" />
        <el-table-column label="下单时间" width="150">
          <template #default="{ row }">{{ fmtLocal(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager" layout="total, prev, pager, next, sizes"
        :total="detailTotal" v-model:current-page="detailQuery.pageNum" v-model:page-size="detailQuery.pageSize"
        :page-sizes="[10, 20, 50]" @current-change="loadDetail()" @size-change="loadDetail()"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  exportDetailCsv, itemRank, ownerRank, rebuildStat, STAT_DIMS, statDetail, statSummary,
  type ItemRankRow, type RankRow, type StatDetailRow, type StatSummary,
} from '@/api/stat'
import { ORDER_STATUS, REMIT_STATUS } from '@/api/trade'
import { useEcharts } from '@/composables/useEcharts'

// ---------- 查询条件 ----------
const dim = ref('MONTH')
const range = ref<[string, string] | null>(null)
const loading = ref(false)
const summary = ref<StatSummary | null>(null)

function rangeParams(): { from?: string; to?: string } {
  return range.value?.[0] ? { from: range.value[0], to: range.value[1] } : {}
}

// ---------- 汇总派生 ----------
const totalCustomers = computed(() =>
  (summary.value?.rows ?? []).reduce((s, r) => s + (r.customerCount ?? 0), 0),
)
const totalRemit = computed(() =>
  (summary.value?.rows ?? []).reduce((s, r) => s + Number(r.remitAmount ?? 0), 0),
)
const arriveRateText = computed(() => {
  const amount = Number(summary.value?.totalAmount ?? 0)
  return amount > 0 ? `${((totalRemit.value / amount) * 100).toFixed(2)}%` : '-'
})

function fmt(n: number | null | undefined): string {
  return (n ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function rateText(v: number | null | undefined): string {
  return v == null ? '-' : `${(Number(v) * 100).toFixed(2)}%`
}

function chainText(v: number | null | undefined): string {
  return v == null ? '—' : `${(Number(v) * 100).toFixed(1)}%`
}

function chainClass(v: number | null | undefined): string {
  if (v == null) return ''
  return Number(v) >= 0 ? 'up' : 'down'
}

// ---------- 趋势图 ----------
const trendChartEl = ref<HTMLElement | null>(null)
const { render: renderTrend } = useEcharts(trendChartEl)

function renderTrendChart() {
  const rows = summary.value?.rows ?? []
  renderTrend({
    tooltip: { trigger: 'axis' },
    legend: { data: ['订单金额', '确认汇款', '订单笔数'] },
    grid: { left: 64, right: 56, top: 40, bottom: 28 },
    xAxis: { type: 'category', data: rows.map((r) => r.statDate) },
    yAxis: [
      { type: 'value', name: '金额', axisLabel: { formatter: (v: number) => (v >= 10000 ? `${(v / 10000).toFixed(0)}万` : String(v)) } },
      { type: 'value', name: '笔数', minInterval: 1 },
    ],
    series: [
      { name: '订单金额', type: 'line', smooth: true, data: rows.map((r) => Number(r.orderAmount)), lineStyle: { width: 2 } },
      { name: '确认汇款', type: 'line', smooth: true, data: rows.map((r) => Number(r.remitAmount)), lineStyle: { width: 2 } },
      { name: '订单笔数', type: 'bar', yAxisIndex: 1, data: rows.map((r) => r.orderCount), barMaxWidth: 22, itemStyle: { opacity: 0.55 } },
    ],
  })
}

// ---------- 业绩排名 / 标的分析 ----------
const rankLoading = ref(false)
const rankRows = ref<RankRow[]>([])
const itemLoading = ref(false)
const itemRows = ref<ItemRankRow[]>([])
const itemChartEl = ref<HTMLElement | null>(null)
const { render: renderItem } = useEcharts(itemChartEl)

const ITEM_COLORS = ['#3b82f6', '#10b981', '#f59e0b', '#ef4444', '#8b5cf6', '#06b6d4', '#84cc16', '#f97316']

function renderItemChart() {
  renderItem({
    tooltip: { trigger: 'item', formatter: '{b}：¥{c}（{d}%）' },
    legend: { type: 'scroll', bottom: 0 },
    series: [{
      type: 'pie',
      radius: ['38%', '64%'],
      center: ['50%', '44%'],
      label: { show: false },
      data: itemRows.value.slice(0, 8).map((r, i) => ({
        name: r.itemName || r.itemCode || `标的#${r.itemId}`,
        value: Number(r.orderAmount),
        itemStyle: { color: ITEM_COLORS[i % ITEM_COLORS.length] },
      })),
    }],
  })
}

// ---------- 重建 ----------
const rebuilding = ref(false)

async function onRebuild() {
  rebuilding.value = true
  try {
    const n = await rebuildStat()
    ElMessage.success(`聚合表重建完成，共 ${n} 条`)
    await loadAll()
  } finally {
    rebuilding.value = false
  }
}

// ---------- 加载 ----------
async function loadSummary() {
  loading.value = true
  try {
    summary.value = await statSummary(dim.value, rangeParams().from, rangeParams().to)
    renderTrendChart()
  } finally {
    loading.value = false
  }
}

async function loadRank() {
  rankLoading.value = true
  try {
    rankRows.value = await ownerRank(dim.value, rangeParams().from, rangeParams().to)
  } finally {
    rankLoading.value = false
  }
}

async function loadItems() {
  itemLoading.value = true
  try {
    itemRows.value = await itemRank(dim.value, rangeParams().from, rangeParams().to)
    renderItemChart()
  } finally {
    itemLoading.value = false
  }
}

function loadAll() {
  void loadSummary()
  void loadRank()
  void loadItems()
}

// ---------- 明细 ----------
const ORDER_STATUS_MAP = ORDER_STATUS
const detailQuery = reactive({
  customerId: '', itemId: '', status: '', direction: '',
  pageNum: 1, pageSize: 10,
})
const detailRange = ref<[string, string] | null>(null)
const detailRows = ref<StatDetailRow[]>([])
const detailTotal = ref(0)
const detailLoading = ref(false)

function detailParams() {
  const cid = Number(detailQuery.customerId)
  const iid = Number(detailQuery.itemId)
  return {
    customerId: Number.isFinite(cid) && cid > 0 ? cid : undefined,
    itemId: Number.isFinite(iid) && iid > 0 ? iid : undefined,
    status: detailQuery.status || undefined,
    direction: detailQuery.direction || undefined,
    from: detailRange.value?.[0],
    to: detailRange.value?.[1],
  }
}

async function loadDetail(page?: number) {
  if (page) detailQuery.pageNum = page
  detailLoading.value = true
  try {
    const p = await statDetail({ ...detailQuery, ...detailParams() })
    detailRows.value = p.list
    detailTotal.value = Number(p.total)
  } finally {
    detailLoading.value = false
  }
}

function resetDetail() {
  Object.assign(detailQuery, { customerId: '', itemId: '', status: '', direction: '', pageNum: 1 })
  detailRange.value = null
  loadDetail()
}

async function onExport() {
  await exportDetailCsv(detailParams())
  ElMessage.success('CSV 已下载')
}

function remitLabel(v: string | null | undefined): string {
  if (!v) return '—'
  return REMIT_STATUS[v as keyof typeof REMIT_STATUS]?.label ?? v
}

function fmtLocal(v: string): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

onMounted(() => {
  loadAll()
  loadDetail()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.toolbar :deep(.el-form-item) {
  margin-bottom: 0;
}
.card-row {
  margin-bottom: 16px;
}
.stat-label {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 4px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 4px;
}
.chain {
  font-size: 12px;
  margin: 0;
}
.chain.up {
  color: #16a34a;
}
.chain.down {
  color: #ef4444;
}
.chart {
  width: 100%;
}
.chart-md {
  height: 300px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
