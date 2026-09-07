<template>
  <div>
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="报价单号/标题搜索" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
            <el-option v-for="(v, k) in QUOTE_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
        <el-form-item class="right">
          <el-button v-permission="'quote:manage'" type="primary" plain @click="openCreate">新建报价单</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="quoteNo" label="报价单号" width="180" show-overflow-tooltip />
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="customerName" label="客户" min-width="120" show-overflow-tooltip />
        <el-table-column label="折扣/税率" width="110" align="center">
          <template #default="{ row }">{{ (row.discountRate * 100).toFixed(0) }}% / {{ (row.taxRate * 100).toFixed(0) }}%</template>
        </el-table-column>
        <el-table-column label="明细合计" width="110" align="right">
          <template #default="{ row }">{{ fmtMoney(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="最终报价" width="110" align="right">
          <template #default="{ row }">
            <b class="money">{{ fmtMoney(row.finalAmount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="QUOTE_STATUS[row.status]?.tag || 'info'" effect="plain">{{ QUOTE_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" width="110">
          <template #default="{ row }">{{ row.validUntil || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="90" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" v-permission="'quote:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" v-permission="'quote:manage'" link type="success" @click="onSubmit(row)">提交审批</el-button>
            <template v-if="row.status === 'SUBMITTED'">
              <el-button v-permission="'quote:approve'" link type="success" @click="onApprove(row)">通过</el-button>
              <el-button v-permission="'quote:approve'" link type="danger" @click="onReject(row)">驳回</el-button>
            </template>
            <el-button v-if="row.status === 'APPROVED'" v-permission="'quote:manage'" link type="warning" @click="onConvert(row)">转订单</el-button>
            <el-button
              v-if="['DRAFT', 'SUBMITTED', 'REJECTED'].includes(row.status)" v-permission="'quote:manage'"
              link type="info" @click="onVoid(row)"
            >作废</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager" layout="total, prev, pager, next, sizes" :total="total"
        v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :page-sizes="[10, 20, 50]"
        @current-change="load" @size-change="load"
      />
    </el-card>

    <!-- ==================== 新建/编辑（含明细行，金额实时预览） ==================== -->
    <el-dialog v-model="editVisible" :title="editingId ? '编辑报价单（驳回可修改后重提）' : '新建报价单（保存为草稿）'" width="860px">
      <el-form :model="form" label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="标题" required>
              <el-input v-model="form.title" maxlength="128" placeholder="报价主题" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" required>
              <el-select v-model="form.customerId" filterable placeholder="选择客户" style="width: 100%">
                <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="8">
            <el-form-item label="关联商机">
              <el-select v-model="form.opportunityId" filterable clearable placeholder="选填" style="width: 100%">
                <el-option v-for="o in opps" :key="o.id" :label="o.name" :value="o.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="折扣率">
              <el-input-number v-model="form.discountRate" :min="0.01" :max="1" :step="0.05" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税率">
              <el-input-number v-model="form.taxRate" :min="0" :max="1" :step="0.01" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="有效期至">
              <el-date-picker v-model="form.validUntil" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="500" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-divider content-position="left">报价明细（金额服务端终算，下方为实时预览）</el-divider>
        <el-table :data="form.items" size="small" stripe>
          <el-table-column label="交易标的" min-width="200">
            <template #default="{ row }">
              <el-select v-model="row.itemId" filterable clearable placeholder="选填（转订单必需）" style="width: 100%" @change="onItemPicked(row)">
                <el-option v-for="i in listedItems" :key="i.id" :label="`${i.name}（${i.code}）`" :value="i.id" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="名称" min-width="150">
            <template #default="{ row }">
              <el-input v-model="row.name" maxlength="100" placeholder="明细名称" />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="120">
            <template #default="{ row }">
              <el-input v-model="row.spec" maxlength="100" placeholder="选填" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0.0001" :precision="4" size="small" style="width: 120px" />
            </template>
          </el-table-column>
          <el-table-column label="单价" width="150">
            <template #default="{ row }">
              <el-input-number v-model="row.price" :min="0" :precision="4" size="small" style="width: 130px" />
            </template>
          </el-table-column>
          <el-table-column label="小计" width="110" align="right">
            <template #default="{ row }">{{ fmtMoney(row.quantity * row.price) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ $index }">
              <el-button link type="danger" :disabled="form.items.length <= 1" @click="form.items.splice($index, 1)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="items-foot">
          <el-button size="small" @click="addItem">+ 添加明细</el-button>
          <span class="calc">
            合计 ¥{{ fmtMoney(preview.total) }}｜折扣后 ¥{{ fmtMoney(preview.discount) }}｜税 ¥{{ fmtMoney(preview.tax) }}｜
            <b>最终 ¥{{ fmtMoney(preview.final) }}</b>
          </span>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">{{ editingId ? '保存修改' : '保存草稿' }}</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 详情 ==================== -->
    <el-drawer v-model="detailVisible" title="报价单详情" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报价单号">{{ detail.quoteNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="QUOTE_STATUS[detail.status]?.tag || 'info'" effect="plain">{{ QUOTE_STATUS[detail.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ detail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="折扣率">{{ (detail.discountRate * 100).toFixed(2) }}%</el-descriptions-item>
          <el-descriptions-item label="税率">{{ (detail.taxRate * 100).toFixed(2) }}%</el-descriptions-item>
          <el-descriptions-item label="明细合计">¥ {{ fmtMoney(detail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="折后金额">¥ {{ fmtMoney(detail.discountAmount) }}</el-descriptions-item>
          <el-descriptions-item label="税额">¥ {{ fmtMoney(detail.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="最终报价">
            <b class="money">¥ {{ fmtMoney(detail.finalAmount) }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="有效期至">{{ detail.validUntil || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(detail.createdAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.approvedAt" label="审批通过时间">{{ fmtTime(detail.approvedAt) }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.rejectedReason" label="驳回原因" :span="2">{{ detail.rejectedReason }}</el-descriptions-item>
          <el-descriptions-item v-if="detail.remark" label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">报价明细</h4>
        <el-table :data="detail.items ?? []" size="small" stripe>
          <el-table-column prop="name" label="名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="spec" label="规格" width="100" show-overflow-tooltip />
          <el-table-column prop="quantity" label="数量" width="90" align="right" />
          <el-table-column label="单价" width="100" align="right">
            <template #default="{ row }">{{ fmtMoney(row.price) }}</template>
          </el-table-column>
          <el-table-column label="小计" width="110" align="right">
            <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column label="标的" width="80" align="center">
            <template #default="{ row }">{{ row.itemId ? `#${row.itemId}` : '自由行' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageCustomers, pageOpps } from '@/api/crm'
import { pageTradeItems, fmtMoney, fmtTime } from '@/api/trade'
import {
  QUOTE_STATUS, approveQuote, convertQuote, createQuote, pageQuotes, quoteDetail,
  rejectQuote, submitQuote, updateQuote, voidQuote, type QuoteRow,
} from '@/api/quote'

// ==================== 列表 ====================
const query = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 10 })
const rows = ref<QuoteRow[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const d = await pageQuotes({ ...query })
    rows.value = d.list
    total.value = d.total
  } finally {
    loading.value = false
  }
}

function reset() {
  Object.assign(query, { keyword: '', status: '', pageNum: 1 })
  load()
}

// ==================== 新建/编辑 ====================
const editVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const customers = ref<{ id: number; name: string }[]>([])
const opps = ref<{ id: number; name: string }[]>([])
const listedItems = ref<{ id: number; code: string; name: string; referencePrice: number }[]>([])

const form = reactive({
  title: '',
  customerId: undefined as number | undefined,
  opportunityId: undefined as number | undefined,
  discountRate: 1,
  taxRate: 0,
  validUntil: '',
  remark: '',
  items: [{ itemId: undefined as number | undefined, name: '', spec: '', quantity: 1, price: 0 }],
})

const preview = computed(() => {
  const totalSum = form.items.reduce((s, i) => s + (i.quantity || 0) * (i.price || 0), 0)
  const discount = totalSum * (form.discountRate ?? 1)
  const tax = discount * (form.taxRate ?? 0)
  return { total: totalSum, discount, tax, final: discount + tax }
})

function addItem() {
  form.items.push({ itemId: undefined, name: '', spec: '', quantity: 1, price: 0 })
}

function onItemPicked(row: { itemId?: number; name: string; price: number }) {
  const item = listedItems.value.find((i) => i.id === row.itemId)
  if (item) {
    row.name = item.name
    if (!row.price) row.price = item.referencePrice
  }
}

async function openCreate() {
  await prepareOptions()
  editingId.value = null
  Object.assign(form, {
    title: '', customerId: undefined, opportunityId: undefined, discountRate: 1, taxRate: 0,
    validUntil: '', remark: '', items: [{ itemId: undefined, name: '', spec: '', quantity: 1, price: 0 }],
  })
  editVisible.value = true
}

async function openEdit(row: QuoteRow) {
  const d = await quoteDetail(row.id)
  await prepareOptions()
  editingId.value = row.id
  Object.assign(form, {
    title: d.title,
    customerId: d.customerId,
    opportunityId: d.opportunityId ?? undefined,
    discountRate: Number(d.discountRate),
    taxRate: Number(d.taxRate),
    validUntil: d.validUntil || '',
    remark: d.remark ?? '',
    items: (d.items ?? []).map((i) => ({
      itemId: i.itemId ?? undefined, name: i.name, spec: i.spec ?? '', quantity: Number(i.quantity), price: Number(i.price),
    })),
  })
  if (form.items.length === 0) addItem()
  editVisible.value = true
}

async function prepareOptions() {
  const [cs, os, items] = await Promise.all([
    pageCustomers({ pageNum: 1, pageSize: 200 }).then((d) => d.list.map((c) => ({ id: c.id, name: c.name }))),
    pageOpps({ status: 'OPEN', pageNum: 1, pageSize: 100 }).then((d) => d.list.map((o) => ({ id: o.id, name: o.name }))),
    pageTradeItems({ status: 'LISTED' }),
  ])
  customers.value = cs
  opps.value = os
  listedItems.value = items
}

async function onSave() {
  if (!form.title.trim() || !form.customerId) {
    ElMessage.warning('请填写标题并选择客户')
    return
  }
  const items = form.items.filter((i) => i.name.trim() && i.quantity > 0)
  if (items.length === 0) {
    ElMessage.warning('至少一条有效明细（名称+数量）')
    return
  }
  if (items.some((i) => !i.price && i.price !== 0)) {
    ElMessage.warning('明细单价不能为空')
    return
  }
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      customerId: form.customerId!,
      opportunityId: form.opportunityId || undefined,
      discountRate: form.discountRate,
      taxRate: form.taxRate,
      validUntil: form.validUntil || undefined,
      remark: form.remark || undefined,
      items: items.map((i) => ({ itemId: i.itemId || null, name: i.name.trim(), spec: i.spec || undefined, quantity: i.quantity, price: i.price })),
    }
    if (editingId.value) {
      await updateQuote(editingId.value, payload)
      ElMessage.success('已保存（金额已按明细重算）')
    } else {
      await createQuote(payload)
      ElMessage.success('报价单已保存为草稿')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ==================== 状态流转 ====================
async function onSubmit(row: QuoteRow) {
  await ElMessageBox.confirm(`提交后进入 BPMN 审批流（候选组 MANAGER/ADMIN），确认提交「${row.quoteNo}」？`, '提交审批', { type: 'info' })
  await submitQuote(row.id)
  ElMessage.success('已提交审批')
  await load()
}

async function onApprove(row: QuoteRow) {
  const { value } = await ElMessageBox.prompt('审批意见（选填）', `通过报价单：${row.quoteNo}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '审批意见（可留空）',
  })
  await approveQuote(row.id, value?.trim() || undefined)
  ElMessage.success('已审批通过')
  await load()
}

async function onReject(row: QuoteRow) {
  const { value } = await ElMessageBox.prompt('驳回原因为必填', `驳回报价单：${row.quoteNo}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '请输入驳回原因',
    inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
  })
  await rejectQuote(row.id, value.trim())
  ElMessage.success('已驳回')
  await load()
}

async function onVoid(row: QuoteRow) {
  const { value } = await ElMessageBox.prompt('作废后不可恢复（审批中单据将同步取消流程）', `作废报价单：${row.quoteNo}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '作废原因（选填）',
  })
  await voidQuote(row.id, value?.trim() || undefined)
  ElMessage.success('已作废')
  await load()
}

async function onConvert(row: QuoteRow) {
  await ElMessageBox.confirm(
    `按明细逐行生成交易订单（仅含关联标的的行），确认转换「${row.quoteNo}」？`,
    '转订单', { type: 'warning' },
  )
  const orderIds = await convertQuote(row.id)
  ElMessage.success(`已生成 ${orderIds.length} 笔交易订单`)
  await load()
}

// ==================== 详情 ====================
const detailVisible = ref(false)
const detail = ref<QuoteRow | null>(null)

async function openDetail(id: number) {
  detail.value = await quoteDetail(id)
  detailVisible.value = true
}

onMounted(load)
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
.items-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}
.calc {
  font-size: 13px;
  color: #475569;
}
.calc b {
  color: #2563eb;
}
.sec {
  margin: 16px 0 8px;
}
</style>
