<template>
  <div>
    <el-row :gutter="16">
      <el-col :span="6" v-for="k in cards" :key="k.label">
        <el-card shadow="never">
          <p class="kpi">{{ k.label }}</p>
          <h2 :style="{ color: k.color }">{{ k.value }}</h2>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待办看板（CRM-F2）：超期置顶标红，支持完成/改期 -->
    <el-card class="mt">
      <template #header>
        <div class="todo-head">
          <span>待办看板（超期置顶）</span>
          <el-button size="small" text type="primary" @click="loadTodos">刷新</el-button>
        </div>
      </template>
      <el-table :data="todos" v-loading="todoLoading" size="small" :row-class-name="rowClass">
        <el-table-column label="关联对象" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag size="small" effect="plain" class="rel-tag">{{ relTypeLabel(row.relType) }}</el-tag>
            {{ row.relName }}
          </template>
        </el-table-column>
        <el-table-column prop="content" label="跟进内容" min-width="200" show-overflow-tooltip />
        <el-table-column prop="method" label="方式" width="80">
          <template #default="{ row }">{{ labelOf(FOLLOWUP_METHODS, row.method) }}</template>
        </el-table-column>
        <el-table-column label="计划时间" width="160">
          <template #default="{ row }">
            <span :class="{ overdue: row.overdue }">
              {{ row.nextFollowupAt?.replace('T', ' ') || '-' }}
            </span>
            <el-tag v-if="row.overdue" size="small" type="danger" effect="plain" class="ml4">超期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="onDone(row)">完成</el-button>
            <el-button link type="primary" @click="openReschedule(row)">改期</el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="暂无待办" :image-size="60" /></template>
      </el-table>
    </el-card>

    <!-- 交易工作台（WSP-DV-01）：业绩卡片 + 状态分布 + 待审批 + 待确认 + 动态流 -->
    <el-card class="mt">
      <template #header>
        <div class="todo-head">
          <span>交易工作台（{{ trade?.scope === 'ALL' ? '管理员视图' : trade?.scope === 'TEAM' ? '经理视图' : '业务员视图' }}）</span>
          <el-button size="small" text type="primary" @click="loadTrade">刷新</el-button>
        </div>
      </template>
      <template v-if="trade">
        <el-row :gutter="12" class="trade-cards">
          <el-col :span="4"><div class="tcard"><p>我的订单</p><b>{{ trade.cards.myOrderCount }}</b></div></el-col>
          <el-col :span="4"><div class="tcard"><p>我的订单总额</p><b>¥ {{ fmtMoney(trade.cards.myOrderAmount) }}</b></div></el-col>
          <el-col :span="4"><div class="tcard"><p>本月我的成交额</p><b>¥ {{ fmtMoney(trade.cards.myMonthAmount) }}</b></div></el-col>
          <el-col :span="4"><div class="tcard"><p>范围内订单总额</p><b>¥ {{ fmtMoney(trade.cards.scopeOrderAmount) }}</b></div></el-col>
          <el-col :span="4"><div class="tcard warn"><p>待审批订单</p><b>{{ trade.cards.pendingApproveCount }}</b></div></el-col>
          <el-col :span="4"><div class="tcard warn"><p>待确认汇款</p><b>{{ trade.cards.pendingRemitCount }}</b></div></el-col>
        </el-row>

        <div class="status-row">
          <span class="status-label">订单状态分布：</span>
          <el-tag
            v-for="(count, status) in trade.statusStats" :key="status"
            :type="ORDER_STATUS[status]?.tag || 'info'" effect="plain" class="status-tag"
          >{{ ORDER_STATUS[status]?.label || status }} {{ count }}</el-tag>
          <span v-if="Object.keys(trade.statusStats).length === 0" class="dim">暂无订单</span>
        </div>

        <el-row :gutter="16" class="mt">
          <el-col :span="12">
            <h4 class="sec">待审批订单（Top 10）</h4>
            <el-table :data="trade.pendingApprovals" size="small" stripe>
              <el-table-column prop="orderNo" label="订单号" show-overflow-tooltip />
              <el-table-column prop="itemName" label="标的" show-overflow-tooltip />
              <el-table-column label="总额" width="110" align="right">
                <template #default="{ row }">¥ {{ fmtMoney(row.totalAmount) }}</template>
              </el-table-column>
              <el-table-column prop="ownerName" label="经手人" width="80" />
              <template #empty><el-empty description="暂无待审批" :image-size="50" /></template>
            </el-table>
          </el-col>
          <el-col :span="12">
            <h4 class="sec">待确认汇款（Top 10）</h4>
            <el-table :data="trade.pendingRemits" size="small" stripe>
              <el-table-column prop="remitNo" label="汇款单号" show-overflow-tooltip />
              <el-table-column prop="customerName" label="客户" show-overflow-tooltip />
              <el-table-column label="金额" width="110" align="right">
                <template #default="{ row }">¥ {{ fmtMoney(row.amount) }}</template>
              </el-table-column>
              <el-table-column prop="ownerName" label="登记人" width="80" />
              <template #empty><el-empty description="暂无待确认汇款" :image-size="50" /></template>
            </el-table>
          </el-col>
        </el-row>

        <h4 class="sec">最新动态流（5s 轮询）</h4>
        <el-timeline v-if="trade.recentFeed.length" class="feed">
          <el-timeline-item
            v-for="f in trade.recentFeed" :key="f.id"
            :timestamp="fmtTime(f.createdAt)" :type="f.read ? '' : 'primary'"
          >
            <b>{{ f.title }}</b> — {{ f.content }}
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无动态" :image-size="50" />
      </template>
      <el-skeleton v-else :rows="5" animated />
    </el-card>

    <!-- 改期弹窗 -->
    <el-dialog v-model="rescheduleVisible" title="待办改期" width="420px">
      <el-date-picker
        v-model="rescheduleAt"
        type="datetime"
        placeholder="选择新的跟进时间"
        style="width: 100%"
        value-format="YYYY-MM-DDTHH:mm:ss"
      />
      <template #footer>
        <el-button @click="rescheduleVisible = false">取消</el-button>
        <el-button type="primary" @click="onReschedule">确认改期</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { workbenchSummary, todoList, todoDone, todoReschedule, type TodoRow } from '@/api/crm'
import { tradeWorkbench, fmtMoney, fmtTime, ORDER_STATUS, type WorkbenchData } from '@/api/trade'
import { FOLLOWUP_METHODS, labelOf } from '@/utils/dict'

// ---------- 交易工作台（WSP-DV-01：业绩卡片/状态分布/待审批/待确认/动态流） ----------
const trade = ref<WorkbenchData | null>(null)

async function loadTrade() {
  trade.value = await tradeWorkbench()
}

// WSP-1：KPI 卡（真实数据）+ 待办看板（批次3）+ 动态流容器（批次 4）
const summary = ref({ myCustomers: 0, todayTodo: 0, allTodo: 0, weekNewCustomers: 0 })

const cards = computed(() => [
  { label: '我的客户', value: summary.value.myCustomers, color: '#2563eb' },
  { label: '今日待跟进', value: summary.value.todayTodo, color: '#ea580c' },
  { label: '全部待办', value: summary.value.allTodo, color: '#dc2626' },
  { label: '近 7 天新增客户', value: summary.value.weekNewCustomers, color: '#0f766e' },
])

// ---------- 待办看板 ----------
const todos = ref<TodoRow[]>([])
const todoLoading = ref(false)
const REL_TYPES: Record<string, string> = { CUSTOMER: '客户', LEAD: '线索', OPPORTUNITY: '商机' }
const relTypeLabel = (t: string) => REL_TYPES[t] ?? t

async function loadTodos() {
  todoLoading.value = true
  try {
    todos.value = await todoList()
  } finally {
    todoLoading.value = false
  }
}

/** 超期行标红 */
function rowClass({ row }: { row: TodoRow }) {
  return row.overdue ? 'overdue-row' : ''
}

async function onDone(row: TodoRow) {
  await todoDone(row.id)
  ElMessage.success('已完成')
  await loadTodos()
  summary.value = await workbenchSummary()
}

// ---------- 改期 ----------
const rescheduleVisible = ref(false)
const rescheduleAt = ref('')
const rescheduleId = ref(0)

function openReschedule(row: TodoRow) {
  rescheduleId.value = row.id
  // 回填值转 ISO 'T' 格式以匹配 date-picker value-format
  rescheduleAt.value = row.nextFollowupAt ? row.nextFollowupAt.replace(' ', 'T') : ''
  rescheduleVisible.value = true
}

async function onReschedule() {
  if (!rescheduleAt.value) {
    ElMessage.warning('请选择新的跟进时间')
    return
  }
  await todoReschedule(rescheduleId.value, rescheduleAt.value)
  ElMessage.success('已改期')
  rescheduleVisible.value = false
  await loadTodos()
}

onMounted(async () => {
  summary.value = await workbenchSummary()
  await loadTodos()
  await loadTrade()
})
</script>

<style scoped>
.kpi {
  font-size: 13px;
  color: #6b7280;
}
.mt {
  margin-top: 16px;
}
.todo-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.overdue {
  color: #dc2626;
  font-weight: 600;
}
.ml4 {
  margin-left: 4px;
}
.rel-tag {
  margin-right: 6px;
}
.trade-cards .tcard {
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 8px 12px;
}
.trade-cards .tcard p {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
}
.trade-cards .tcard b {
  font-size: 16px;
  color: #2563eb;
}
.trade-cards .tcard.warn b {
  color: #ea580c;
}
.status-row {
  margin-top: 12px;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.status-label {
  font-size: 13px;
  color: #374151;
}
.status-tag {
  margin-right: 4px;
}
.dim {
  font-size: 12px;
  color: #9ca3af;
}
.sec {
  margin: 8px 0;
}
.feed {
  padding-left: 4px;
}
:deep(.overdue-row) {
  --el-table-tr-bg-color: #fef2f2;
}
</style>
