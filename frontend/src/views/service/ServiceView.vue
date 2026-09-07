<template>
  <div class="page">
    <!-- 满意度统计（CRM-S2：svc:satisfaction 权限可见） -->
    <el-row v-if="userStore.hasPerm('svc:satisfaction')" :gutter="16" class="sat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">已解决工单</p>
          <p class="stat-value">{{ sat?.resolvedTotal ?? 0 }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">已评分工单</p>
          <p class="stat-value">{{ sat?.ratedCount ?? 0 }}</p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">平均满意度</p>
          <p class="stat-value">{{ sat ? Number(sat.avgScore).toFixed(2) : '-' }} <span class="stat-unit">/ 5</span></p>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <p class="stat-label">评分分布（1~5 星）</p>
          <div class="star-bars">
            <div v-for="star in [5, 4, 3, 2, 1]" :key="star" class="star-bar">
              <span class="star-name">{{ star }}★</span>
              <el-progress
                :percentage="satPercent(star)" :stroke-width="8"
                :color="star >= 4 ? '#67c23a' : star === 3 ? '#e6a23c' : '#f56c6c'" :show-text="false"
              />
              <span class="star-count">{{ sat?.distribution?.[String(star)] ?? 0 }}</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- SLA 超期预警（批次6：未关闭且已超期的工单，最多 50 条，按超期最久排序） -->
    <el-card v-if="slaRows.length > 0" shadow="never" class="sla-panel">
      <template #header>
        <div class="card-head">
          <span class="sla-title">
            SLA 超期预警
            <el-tag type="danger" size="small" effect="dark">{{ slaRows.length }}</el-tag>
          </span>
          <el-button size="small" text type="primary" @click="loadSla">刷新</el-button>
        </div>
      </template>
      <el-table :data="slaRows" size="small" stripe>
        <el-table-column prop="no" label="工单号" width="150" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
        <el-table-column label="优先级" width="80">
          <template #default="{ row }">
            <el-tag :type="TICKET_PRIORITY[row.priority]?.tag || 'info'" effect="plain">
              {{ TICKET_PRIORITY[row.priority]?.label || row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="TICKET_STATUS[row.status]?.tag || 'info'" effect="plain">
              {{ TICKET_STATUS[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="100">
          <template #default="{ row }">{{ row.assigneeName || '未指派' }}</template>
        </el-table-column>
        <el-table-column label="超期时长" width="130">
          <template #default="{ row }">
            <span class="sla-over">{{ fmtOverdue(row.overdueMinutes) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="SLA 截止" width="150">
          <template #default="{ row }">{{ fmtLocal(row.slaDueAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="工单号/标题搜索" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
            <el-option v-for="(v, k) in TICKET_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 120px">
            <el-option v-for="(v, k) in TICKET_TYPE" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.priority" placeholder="优先级" clearable style="width: 110px">
            <el-option v-for="(v, k) in TICKET_PRIORITY" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="right">
          <el-button v-permission="'svc:ticket:manage'" type="primary" plain @click="openCreate">新建工单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 工单列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="no" label="工单号" width="150" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="150" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">{{ TICKET_TYPE[row.type] || row.type }}</template>
        </el-table-column>
        <el-table-column label="优先级" width="90">
          <template #default="{ row }">
            <el-tag :type="TICKET_PRIORITY[row.priority]?.tag || 'info'" effect="plain">
              {{ TICKET_PRIORITY[row.priority]?.label || row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="TICKET_STATUS[row.status]?.tag || 'info'" effect="plain">
              {{ TICKET_STATUS[row.status]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assigneeName" label="负责人" width="100">
          <template #default="{ row }">{{ row.assigneeName || '未指派' }}</template>
        </el-table-column>
        <el-table-column label="SLA 截止" width="160">
          <template #default="{ row }">
            <span :class="{ 'sla-over': isSlaOver(row) }">{{ row.slaDueAt ? fmtLocal(row.slaDueAt) : '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="满意度" width="90" align="center">
          <template #default="{ row }">
            <span v-if="row.satisfaction != null">{{ row.satisfaction }}★</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">{{ fmtLocal(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="openDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
              v-permission="'svc:ticket:manage'" link type="primary" @click="openAssign(row)"
            >{{ row.assigneeId ? '改派' : '指派' }}</el-button>
            <el-button v-if="row.status === 'OPEN'" v-permission="'svc:ticket:manage'" link type="primary" @click="onProcess(row)">处理</el-button>
            <el-button
              v-if="row.status === 'OPEN' || row.status === 'PROCESSING'"
              v-permission="'svc:ticket:manage'" link type="success" @click="openResolve(row)"
            >解决</el-button>
            <el-button v-if="row.status === 'RESOLVED'" v-permission="'svc:satisfaction'" link type="warning" @click="openVisit(row)">回访</el-button>
            <el-button v-if="row.status === 'RESOLVED'" v-permission="'svc:ticket:manage'" link type="danger" @click="onClose(row)">关闭</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager" layout="total, prev, pager, next, sizes"
        :total="total" v-model:current-page="query.pageNum" v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]" @current-change="load" @size-change="load"
      />
    </el-card>

    <!-- 新建工单 -->
    <el-dialog v-model="createVisible" title="新建工单" width="560px">
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="关联客户" required>
          <el-select
            v-model="createForm.customerId" filterable remote :remote-method="searchCustomers"
            :loading="customerSearching" placeholder="输入客户名称搜索" style="width: 100%" @change="onCustomerChange"
          >
            <el-option v-for="c in customerOptions" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联联系人">
          <el-select v-model="createForm.contactId" placeholder="可选" clearable style="width: 100%">
            <el-option v-for="c in contacts" :key="c.id" :label="`${c.name}${c.phone ? ' · ' + c.phone : ''}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="createForm.type" style="width: 100%">
                <el-option v-for="(v, k) in TICKET_TYPE" :key="k" :label="v" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-select v-model="createForm.priority" style="width: 100%">
                <el-option v-for="(v, k) in TICKET_PRIORITY" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="问题描述" required>
          <el-input v-model="createForm.content" type="textarea" :rows="3" maxlength="1000" show-word-limit />
        </el-form-item>
        <el-form-item label="SLA 截止">
          <el-date-picker v-model="createForm.slaDueAt" type="datetime" placeholder="可选" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 指派/改派 -->
    <el-dialog v-model="assignVisible" :title="`指派工单：${assignRow?.no || ''}`" width="420px">
      <el-select v-model="assigneeId" filterable placeholder="选择业务员" style="width: 100%">
        <el-option v-for="u in users" :key="u.id" :label="`${u.realName || u.username}（${u.orgName || '-'}）`" :value="u.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAssign">确认指派</el-button>
      </template>
    </el-dialog>

    <!-- 标记解决 -->
    <el-dialog v-model="resolveVisible" :title="`标记解决：${resolveRow?.no || ''}`" width="440px">
      <el-input v-model="resolveRemark" type="textarea" :rows="3" placeholder="解决方案备注（可选）" maxlength="500" />
      <template #footer>
        <el-button @click="resolveVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onResolve">确认解决</el-button>
      </template>
    </el-dialog>

    <!-- 回访登记 + 满意度评分 -->
    <el-dialog v-model="visitVisible" :title="`回访登记：${visitRow?.no || ''}`" width="480px">
      <el-form label-width="100px">
        <el-form-item label="回访内容" required>
          <el-input v-model="visitForm.content" type="textarea" :rows="3" maxlength="500" show-word-limit />
        </el-form-item>
        <el-form-item label="满意度" required>
          <el-rate v-model="visitForm.satisfaction" :max="5" show-text :texts="['非常不满', '不满', '一般', '满意', '非常满意']" />
        </el-form-item>
        <el-form-item label="下次跟进">
          <el-date-picker v-model="visitForm.nextFollowupAt" type="datetime" placeholder="可选" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visitVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onVisit">提交回访</el-button>
      </template>
    </el-dialog>

    <!-- 工单详情 -->
    <el-dialog v-model="detailVisible" title="工单详情" width="600px">
      <el-descriptions v-if="detail" :column="2" border size="small">
        <el-descriptions-item label="工单号" :span="2">{{ detail.no }}</el-descriptions-item>
        <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactName || '—' }}{{ detail.contactPhone ? ' · ' + detail.contactPhone : '' }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ TICKET_TYPE[detail.type] || detail.type }}</el-descriptions-item>
        <el-descriptions-item label="优先级">{{ TICKET_PRIORITY[detail.priority]?.label || detail.priority }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="TICKET_STATUS[detail.status]?.tag || 'info'" effect="plain">
            {{ TICKET_STATUS[detail.status]?.label || detail.status }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">{{ detail.assigneeName || '未指派' }}</el-descriptions-item>
        <el-descriptions-item label="SLA 截止">{{ detail.slaDueAt ? fmtLocal(detail.slaDueAt) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="解决时间">{{ detail.resolvedAt ? fmtLocal(detail.resolvedAt) : '—' }}</el-descriptions-item>
        <el-descriptions-item label="满意度">{{ detail.satisfaction != null ? detail.satisfaction + '★' : '—' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ fmtLocal(detail.createdAt) }}</el-descriptions-item>
        <el-descriptions-item label="问题描述" :span="2">{{ detail.content }}</el-descriptions-item>
        <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignTicket, closeTicket, createTicket, pageTickets, processTicket, resolveTicket,
  satisfactionSummary, slaWarning, TICKET_PRIORITY, TICKET_STATUS, TICKET_TYPE, ticketDetail, visitTicket,
  type SatisfactionSummary, type SlaWarningRow, type TicketDetail, type TicketRow,
} from '@/api/svc'
import { listContacts, pageCustomers, type Contact, type CustomerRow } from '@/api/crm'
import { pageUsers, type UserRow } from '@/api/system'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

// ---------- 列表 ----------
const query = reactive({ keyword: '', status: '', type: '', priority: '', pageNum: 1, pageSize: 10 })
const rows = ref<TicketRow[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const page = await pageTickets({ ...query })
    rows.value = page.list
    total.value = Number(page.total)
  } finally {
    loading.value = false
  }
  loadSla()
}

function resetQuery() {
  Object.assign(query, { keyword: '', status: '', type: '', priority: '', pageNum: 1 })
  load()
}

// ---------- 满意度统计 ----------
const sat = ref<SatisfactionSummary | null>(null)

async function loadSat() {
  if (!userStore.hasPerm('svc:satisfaction')) return
  try {
    sat.value = await satisfactionSummary()
  } catch {
    // 无权限/异常静默（拦截器已提示）
  }
}

function satPercent(star: number): number {
  const dist = sat.value?.distribution ?? {}
  const sum = Object.values(dist).reduce((s, n) => s + Number(n), 0)
  return sum === 0 ? 0 : Math.round(((dist[String(star)] ?? 0) / sum) * 100)
}

// ---------- SLA 超期预警（批次6） ----------
const slaRows = ref<SlaWarningRow[]>([])

async function loadSla() {
  try {
    slaRows.value = await slaWarning()
  } catch {
    slaRows.value = []
  }
}

/** 超期时长：<1h 显示分钟，否则显示 x 小时 y 分 */
function fmtOverdue(minutes: number): string {
  if (minutes < 60) return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分`
}

// ---------- 新建 ----------
const createVisible = ref(false)
const saving = ref(false)
const createForm = reactive({
  customerId: 0, contactId: 0, type: 'CONSULT', priority: 'MEDIUM',
  title: '', content: '', slaDueAt: '',
})
const customerOptions = ref<CustomerRow[]>([])
const customerSearching = ref(false)
const contacts = ref<Contact[]>([])

function openCreate() {
  Object.assign(createForm, { customerId: 0, contactId: 0, type: 'CONSULT', priority: 'MEDIUM', title: '', content: '', slaDueAt: '' })
  customerOptions.value = []
  contacts.value = []
  createVisible.value = true
}

async function searchCustomers(kw: string) {
  customerSearching.value = true
  try {
    const page = await pageCustomers({ pageNum: 1, pageSize: 20, keyword: kw || undefined })
    customerOptions.value = page.list
  } finally {
    customerSearching.value = false
  }
}

async function onCustomerChange() {
  createForm.contactId = 0
  contacts.value = createForm.customerId ? await listContacts(createForm.customerId) : []
}

async function onCreate() {
  if (!createForm.customerId || !createForm.title.trim() || !createForm.content.trim()) {
    ElMessage.warning('请选择客户并填写标题与问题描述')
    return
  }
  saving.value = true
  try {
    await createTicket({
      customerId: createForm.customerId,
      contactId: createForm.contactId || undefined,
      type: createForm.type,
      priority: createForm.priority,
      title: createForm.title.trim(),
      content: createForm.content.trim(),
      slaDueAt: createForm.slaDueAt || undefined,
    })
    ElMessage.success('工单已创建')
    createVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 指派/改派 ----------
const assignVisible = ref(false)
const assignRow = ref<TicketRow | null>(null)
const assigneeId = ref(0)
const users = ref<UserRow[]>([])

async function openAssign(row: TicketRow) {
  assignRow.value = row
  assigneeId.value = row.assigneeId ?? 0
  if (users.value.length === 0) {
    const page = await pageUsers({ pageNum: 1, pageSize: 100 })
    users.value = page.list
  }
  assignVisible.value = true
}

async function onAssign() {
  if (!assigneeId.value || !assignRow.value) {
    ElMessage.warning('请选择负责人')
    return
  }
  saving.value = true
  try {
    await assignTicket(assignRow.value.id, assigneeId.value)
    ElMessage.success('已指派')
    assignVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 流转：处理 / 解决 / 关闭 ----------
async function onProcess(row: TicketRow) {
  await ElMessageBox.confirm(`开始处理工单「${row.title}」？`, '开始处理', { type: 'info' })
  await processTicket(row.id)
  ElMessage.success('已进入处理中')
  await load()
}

const resolveVisible = ref(false)
const resolveRow = ref<TicketRow | null>(null)
const resolveRemark = ref('')

function openResolve(row: TicketRow) {
  resolveRow.value = row
  resolveRemark.value = ''
  resolveVisible.value = true
}

async function onResolve() {
  if (!resolveRow.value) return
  saving.value = true
  try {
    await resolveTicket(resolveRow.value.id, resolveRemark.value.trim() || undefined)
    ElMessage.success('已标记解决')
    resolveVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onClose(row: TicketRow) {
  await ElMessageBox.confirm('关闭后工单进入终态，不可再操作。确认关闭？', '关闭工单', { type: 'warning' })
  await closeTicket(row.id)
  ElMessage.success('工单已关闭')
  await load()
}

// ---------- 回访 + 满意度 ----------
const visitVisible = ref(false)
const visitRow = ref<TicketRow | null>(null)
const visitForm = reactive({ content: '', satisfaction: 5, nextFollowupAt: '' })

function openVisit(row: TicketRow) {
  visitRow.value = row
  Object.assign(visitForm, { content: '', satisfaction: 5, nextFollowupAt: '' })
  visitVisible.value = true
}

async function onVisit() {
  if (!visitForm.content.trim() || !visitForm.satisfaction) {
    ElMessage.warning('请填写回访内容并评分')
    return
  }
  if (!visitRow.value) return
  saving.value = true
  try {
    await visitTicket(visitRow.value.id, {
      content: visitForm.content.trim(),
      satisfaction: visitForm.satisfaction,
      nextFollowupAt: visitForm.nextFollowupAt || undefined,
    })
    ElMessage.success('回访已登记')
    visitVisible.value = false
    await load()
    await loadSat()
  } finally {
    saving.value = false
  }
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref<TicketDetail | null>(null)

async function openDetail(row: TicketRow) {
  detail.value = await ticketDetail(row.id)
  detailVisible.value = true
}

// ---------- 工具 ----------
function isSlaOver(row: TicketRow): boolean {
  return !!row.slaDueAt && row.status !== 'CLOSED' && new Date(row.slaDueAt).getTime() < Date.now()
}

function fmtLocal(v: string): string {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

onMounted(() => {
  load()
  loadSat()
  loadSla()
})
</script>

<style scoped>
.sla-panel {
  margin-bottom: 16px;
}
.sla-panel :deep(.el-card__header) {
  padding: 10px 16px;
}
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.sla-title {
  font-size: 14px;
  font-weight: 600;
  color: #b91c1c;
  display: flex;
  align-items: center;
  gap: 8px;
}
.toolbar {
  margin-bottom: 16px;
}
.toolbar :deep(.el-form-item) {
  margin-bottom: 0;
}
.toolbar .right {
  float: right;
}
.sat-row {
  margin-bottom: 16px;
}
.stat-card {
  height: 100%;
}
.stat-label {
  font-size: 13px;
  color: #6b7280;
  margin: 0 0 4px;
}
.stat-value {
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
  margin: 0;
}
.stat-unit {
  font-size: 13px;
  font-weight: 400;
  color: #94a3b8;
}
.star-bars {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 4px;
}
.star-bar {
  display: flex;
  align-items: center;
  gap: 8px;
}
.star-name {
  width: 24px;
  font-size: 12px;
  color: #6b7280;
}
.star-bar .el-progress {
  flex: 1;
}
.star-count {
  width: 28px;
  text-align: right;
  font-size: 12px;
  color: #6b7280;
}
.sla-over {
  color: #ef4444;
  font-weight: 600;
}
.pager {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
