<template>
  <div>
    <!-- 到期预警横幅（CRM-R3） -->
    <el-alert
      v-if="expiringRows.length > 0" type="warning" :closable="false" class="mb8"
      :title="`到期预警：${expiringRows.length} 份合同将于 30 天内到期，请及时安排续约`"
    />

    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="合同号/标题搜索" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.signStatus" placeholder="签署状态" clearable style="width: 130px">
            <el-option v-for="(v, k) in CONTRACT_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="query.expiring" label="仅看 30 天内到期" @change="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
        <el-form-item class="right">
          <el-button v-permission="'contract:manage'" type="primary" plain @click="openCreate">新建合同</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="contractNo" label="合同号" width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }">
            <b class="money">{{ fmtMoney(row.amount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="签署状态" width="90">
          <template #default="{ row }">
            <el-tag :type="CONTRACT_STATUS[row.signStatus]?.tag || 'info'" effect="plain">{{ CONTRACT_STATUS[row.signStatus]?.label || row.signStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="履行期" width="200">
          <template #default="{ row }">{{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}</template>
        </el-table-column>
        <el-table-column label="剩余天数" width="90" align="right">
          <template #default="{ row }">
            <span v-if="row.daysLeft !== undefined" :class="{ danger: row.daysLeft <= 7 }">{{ row.daysLeft }} 天</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.signStatus === 'UNSIGNED'" v-permission="'contract:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.signStatus === 'UNSIGNED'" v-permission="'contract:manage'" link type="success" @click="onSign(row)">签署</el-button>
            <el-button v-if="row.signStatus === 'SIGNED'" v-permission="'contract:manage'" link type="success" @click="onExecute(row)">开始履行</el-button>
            <el-button
              v-if="['UNSIGNED', 'SIGNED', 'EXECUTING'].includes(row.signStatus)" v-permission="'contract:manage'"
              link type="danger" @click="onTerminate(row)"
            >终止</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager" layout="total, prev, pager, next, sizes" :total="total"
        v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50]"
        @current-change="load" @size-change="load"
      />
    </el-card>

    <!-- ==================== 新建/编辑 ==================== -->
    <el-dialog v-model="editVisible" :title="editingId ? '编辑合同（仅未签署可编辑）' : '新建合同（未签署）'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="合同标题" required>
          <el-input v-model="form.title" maxlength="128" placeholder="合同名称" />
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="合同金额" required>
              <el-input-number v-model="form.amount" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联报价单">
              <el-select v-model="form.quoteId" filterable clearable placeholder="选填" style="width: 100%">
                <el-option v-for="q in quotes" :key="q.id" :label="`${q.quoteNo}｜${q.title}`" :value="q.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="到期预警">
              <el-input-number v-model="form.reminderDays" :min="1" :max="365" style="width: 100%" />
              <span class="sub">提前 N 天预警</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="附件 Key">
              <el-input v-model="form.attachmentUrl" maxlength="500" placeholder="附件标识（选填）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 详情 ==================== -->
    <el-drawer v-model="detailVisible" title="合同详情" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="合同号">{{ detail.contractNo }}</el-descriptions-item>
          <el-descriptions-item label="签署状态">
            <el-tag :type="CONTRACT_STATUS[detail.signStatus]?.tag || 'info'" effect="plain">{{ CONTRACT_STATUS[detail.signStatus]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="合同金额">
            <b class="money">¥ {{ fmtMoney(detail.amount) }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="关联报价">{{ detail.quoteId ? `#${detail.quoteId}` : '-' }}</el-descriptions-item>
          <el-descriptions-item label="开始日期">{{ detail.startDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结束日期">{{ detail.endDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="签署时间">{{ detail.signedAt ? fmtTime(detail.signedAt) : '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.terminatedAt" label="终止时间">{{ fmtTime(detail.terminatedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.terminateReason" label="终止原因" :span="2">{{ detail.terminateReason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageCustomers } from '@/api/crm'
import { fmtMoney, fmtTime } from '@/api/trade'
import { pageQuotes } from '@/api/quote'
import {
  CONTRACT_STATUS, contractDetail, createContract, executeContract, expiringContracts,
  pageContracts, signContract, terminateContract, updateContract, type ContractRow,
} from '@/api/contract'

// ==================== 列表 ====================
const query = reactive({ keyword: '', signStatus: '', expiring: false, pageNum: 1, pageSize: 10 })
const rows = ref<ContractRow[]>([])
const total = ref(0)
const loading = ref(false)
const expiringRows = ref<ContractRow[]>([])

async function load() {
  loading.value = true
  try {
    const d = await pageContracts({ ...query })
    rows.value = d.list
    total.value = d.total
  } finally {
    loading.value = false
  }
}

async function loadExpiring() {
  expiringRows.value = await expiringContracts()
}

function reset() {
  Object.assign(query, { keyword: '', signStatus: '', expiring: false, pageNum: 1 })
  load()
}

// ==================== 新建/编辑 ====================
const editVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const customers = ref<{ id: number; name: string }[]>([])
const quotes = ref<{ id: number; quoteNo: string; title: string }[]>([])

const form = reactive({
  title: '',
  customerId: undefined as number | undefined,
  quoteId: undefined as number | undefined,
  amount: 0,
  startDate: '',
  endDate: '',
  reminderDays: 30,
  attachmentUrl: '',
  remark: '',
})

async function prepareOptions() {
  const [cs, qs] = await Promise.all([
    pageCustomers({ pageNum: 1, pageSize: 200 }).then((d) => d.list.map((c) => ({ id: c.id, name: c.name }))),
    pageQuotes({ status: 'APPROVED', pageNum: 1, pageSize: 100 }).then((d) => d.list.map((q) => ({ id: q.id, quoteNo: q.quoteNo, title: q.title }))),
  ])
  customers.value = cs
  quotes.value = qs
}

async function openCreate() {
  await prepareOptions()
  editingId.value = null
  Object.assign(form, {
    title: '', customerId: undefined, quoteId: undefined, amount: 0,
    startDate: '', endDate: '', reminderDays: 30, attachmentUrl: '', remark: '',
  })
  editVisible.value = true
}

async function openEdit(row: ContractRow) {
  const d = await contractDetail(row.id)
  await prepareOptions()
  editingId.value = row.id
  Object.assign(form, {
    title: d.title,
    customerId: d.customerId,
    quoteId: d.quoteId ?? undefined,
    amount: Number(d.amount),
    startDate: d.startDate || '',
    endDate: d.endDate || '',
    reminderDays: d.reminderDays ?? 30,
    attachmentUrl: d.attachmentUrl ?? '',
    remark: d.remark ?? '',
  })
  editVisible.value = true
}

async function onSave() {
  if (!form.title.trim() || !form.customerId) {
    ElMessage.warning('请填写标题并选择客户')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      customerId: form.customerId!,
      quoteId: form.quoteId || undefined,
      amount: form.amount,
      startDate: form.startDate || undefined,
      endDate: form.endDate || undefined,
      reminderDays: form.reminderDays,
      attachmentUrl: form.attachmentUrl || undefined,
      remark: form.remark || undefined,
    }
    if (editingId.value) {
      await updateContract(editingId.value, payload)
      ElMessage.success('已保存')
    } else {
      await createContract(payload)
      ElMessage.success('合同已创建（未签署）')
    }
    editVisible.value = false
    await Promise.all([load(), loadExpiring()])
  } finally {
    saving.value = false
  }
}

// ==================== 签署流转 ====================
async function onSign(row: ContractRow) {
  await ElMessageBox.confirm(`确认合同「${row.contractNo}」已完成双方签署？`, '签署完成', { type: 'success' })
  await signContract(row.id)
  ElMessage.success('已签署')
  await Promise.all([load(), loadExpiring()])
}

async function onExecute(row: ContractRow) {
  await ElMessageBox.confirm(`确认合同「${row.contractNo}」开始履行？`, '开始履行', { type: 'info' })
  await executeContract(row.id)
  ElMessage.success('已开始履行')
  await load()
}

async function onTerminate(row: ContractRow) {
  const { value } = await ElMessageBox.prompt('终止后不可恢复', `终止合同：${row.contractNo}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '终止原因（选填）',
  })
  await terminateContract(row.id, value?.trim() || undefined)
  ElMessage.success('已终止')
  await Promise.all([load(), loadExpiring()])
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref<ContractRow | null>(null)

async function openDetail(id: number) {
  detail.value = await contractDetail(id)
  detailVisible.value = true
}

onMounted(() => {
  load()
  loadExpiring()
})
</script>

<style scoped>
.toolbar {
  margin-bottom: 16px;
}
.toolbar :deep(.el-form-item) {
  margin-bottom: 0;
}
.toolbar .right {
  float: right;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.money {
  color: #2563eb;
}
.danger {
  color: #dc2626;
  font-weight: 600;
}
.mb8 {
  margin-bottom: 8px;
}
.sub {
  font-size: 12px;
  color: #94a3b8;
  margin-left: 6px;
}
</style>
