<template>
  <div class="page">
    <!-- 漏斗面板（CRM-O3/O5） -->
    <el-card shadow="never" class="funnel-card">
      <template #header>
        <div class="funnel-head">
          <span>商机漏斗（数量 / 金额 / 赢率 / 加权预测）</span>
          <span class="funnel-total">
            加权预测合计：<b>¥ {{ fmt(funnelTotal) }}</b>
          </span>
        </div>
      </template>
      <div v-for="f in funnel" :key="f.stage" class="funnel-row">
        <span class="funnel-name">{{ stageLabel(String(f.stage)) }}</span>
        <div class="funnel-bar-wrap">
          <div
            class="funnel-bar"
            :class="{ open: f.stage <= 5, won: f.stage === 6, lost: f.stage === 7 }"
            :style="{ width: barWidth(f) + '%' }"
          />
        </div>
        <span class="funnel-metric">{{ f.count }} 个</span>
        <span class="funnel-metric">¥ {{ fmt(f.amount) }}</span>
        <span class="funnel-metric">赢率 {{ f.winRate }}%</span>
        <span class="funnel-metric strong">加权 ¥ {{ fmt(f.weightedAmount) }}</span>
      </div>
    </el-card>

    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="商机名称搜索" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.stage" placeholder="阶段" clearable style="width: 130px">
            <el-option v-for="x in stageOptions" :key="x.value" :label="x.label" :value="Number(x.value)" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
            <el-option label="进行中" value="OPEN" />
            <el-option label="已成交" value="WON" />
            <el-option label="已丢单" value="LOST" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="right">
          <el-button v-permission="'opp:create'" type="primary" plain @click="openCreate">新增商机</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="name" label="商机名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="customerName" label="关联客户" min-width="150" show-overflow-tooltip />
        <el-table-column label="阶段" width="110">
          <template #default="{ row }">
            <el-tag :type="stageTag(row.stage)" effect="plain">{{ stageLabel(String(row.stage)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="金额" width="130" align="right">
          <template #default="{ row }">¥ {{ fmt(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="预计成交日" width="120">
          <template #default="{ row }">{{ row.expectedDate || '-' }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'WON' ? 'success' : row.status === 'LOST' ? 'danger' : 'primary'" effect="plain">
              {{ row.status === 'WON' ? '已成交' : row.status === 'LOST' ? '已丢单' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="丢单原因" width="110">
          <template #default="{ row }">{{ row.loseReason ? loseLabel(row.loseReason) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'OPEN'">
              <el-dropdown v-permission="'opp:stage'" @command="(to: number) => onStage(row, to)">
                <el-button link type="primary">流转</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item v-for="s in 5" :key="s" :command="s" :disabled="s === row.stage">
                      {{ s === row.stage ? stageLabel(String(s)) + '（当前）' : stageLabel(String(s)) }}
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button v-permission="'opp:win'" link type="success" @click="onWin(row)">成交</el-button>
              <el-button v-permission="'opp:lose'" link type="warning" @click="onLose(row)">丢单</el-button>
              <el-button v-permission="'opp:create'" link type="primary" @click="openEdit(row)">编辑</el-button>
              <el-popconfirm title="确认删除该商机？" @confirm="onDelete(row.id)">
                <template #reference><el-button v-permission="'opp:delete'" link type="danger">删除</el-button></template>
              </el-popconfirm>
            </template>
            <span v-else class="terminal-tip">终态（留痕统计）</span>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        class="pager"
        layout="total, prev, pager, next, sizes"
        :total="total"
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :page-sizes="[10, 20, 50]"
        @current-change="load"
        @size-change="load"
      />
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑商机' : '新增商机'" width="560px">
      <el-form :model="editForm" label-width="100px">
        <el-form-item label="关联客户" required>
          <el-select v-model="editForm.customerId" filterable placeholder="选择客户" style="width: 100%" :disabled="!!editForm.id">
            <el-option v-for="c in customers" :key="c.id" :label="c.region || c.address ? `${c.name}（${[c.region, c.address].filter(Boolean).join(' · ')}）` : c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商机名称" required><el-input v-model="editForm.name" maxlength="128" /></el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="金额"><el-input-number v-model="editForm.amount" :min="0" :precision="2" style="width: 100%" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种">
              <el-select v-model="editForm.currency" style="width: 100%">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
                <el-option label="欧元 EUR" value="EUR" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="预计成交日">
          <el-date-picker v-model="editForm.expectedDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="!editForm.id" label="初始阶段">
          <el-select v-model="editForm.stage" style="width: 100%">
            <el-option v-for="s in 5" :key="s" :label="stageLabel(String(s))" :value="s" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createOpp, deleteOpp, loseOpp, oppFunnel, pageCustomers, pageOpps, stageOpp,
  updateOpp, winOpp, type FunnelRow, type OppRow,
} from '@/api/crm'
import { useDict } from '@/composables/useDict'

// 商机阶段/丢单原因字典（sys_dict：V4 种子 opportunity_stage / lose_reason）
const { options: stageOptions, labelOfDict: stageLabel } = useDict('opportunity_stage')
const { labelOfDict: loseLabel } = useDict('lose_reason')

const stageTag = (s: number) => (s === 6 ? 'success' : s === 7 ? 'danger' : 'primary')
const fmt = (n: number | null | undefined) =>
  (n ?? 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })

// ---------- 漏斗 ----------
const funnel = ref<FunnelRow[]>([])
const funnelTotal = computed(() => funnel.value.reduce((s, f) => s + f.weightedAmount, 0))
const maxCount = computed(() => Math.max(1, ...funnel.value.map((f) => f.count)))
const barWidth = (f: FunnelRow) => Math.max(2, (f.count / maxCount.value) * 100)

async function loadFunnel() {
  funnel.value = await oppFunnel()
}

// ---------- 列表 ----------
const query = reactive({ keyword: '', stage: undefined as number | undefined, status: '', pageNum: 1, pageSize: 20 })
const rows = ref<OppRow[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await pageOpps({ ...query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { keyword: '', stage: undefined, status: '', pageNum: 1 })
  load()
}

// ---------- 新增/编辑 ----------
const editVisible = ref(false)
const saving = ref(false)
const customers = ref<{ id: number; name: string }[]>([])
const editForm = reactive({
  id: 0, customerId: undefined as number | undefined, name: '', amount: 0,
  currency: 'CNY', expectedDate: '', stage: 1, remark: '',
})

async function openCreate() {
  customers.value = await pageCustomers({ pageNum: 1, pageSize: 200 }).then((d) => d.list.map((c) => ({ id: c.id, name: c.name, region: c.region, address: c.address })))
  Object.assign(editForm, { id: 0, customerId: undefined, name: '', amount: 0, currency: 'CNY', expectedDate: '', stage: 1, remark: '' })
  editVisible.value = true
}

async function openEdit(row: OppRow) {
  customers.value = [{ id: row.customerId, name: row.customerName, region: '', address: '' }]
  Object.assign(editForm, {
    id: row.id, customerId: row.customerId, name: row.name, amount: row.amount,
    currency: row.currency || 'CNY', expectedDate: row.expectedDate, stage: row.stage, remark: '',
  })
  editVisible.value = true
}

async function onSave() {
  if (!editForm.customerId || !editForm.name.trim()) {
    ElMessage.warning('请填写关联客户与商机名称')
    return
  }
  saving.value = true
  try {
    const payload = {
      customerId: editForm.customerId,
      name: editForm.name.trim(),
      amount: editForm.amount,
      currency: editForm.currency,
      expectedDate: editForm.expectedDate || undefined,
      remark: editForm.remark || undefined,
    }
    if (editForm.id) {
      await updateOpp(editForm.id, payload)
      ElMessage.success('已保存')
    } else {
      await createOpp({ ...payload, stage: editForm.stage })
      ElMessage.success('已创建')
    }
    editVisible.value = false
    await Promise.all([load(), loadFunnel()])
  } finally {
    saving.value = false
  }
}

// ---------- 状态机动作 ----------
async function onStage(row: OppRow, toStage: number) {
  const { value } = await ElMessageBox.prompt(
    `将「${row.name}」流转至【${stageLabel(String(toStage))}】，可填写流转说明（留痕）`,
    '阶段流转', { confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '选填' },
  )
  await stageOpp(row.id, toStage, value?.trim() || undefined)
  ElMessage.success('已流转')
  await Promise.all([load(), loadFunnel()])
}

async function onWin(row: OppRow) {
  await ElMessageBox.confirm(`确认将「${row.name}」标记成交？关联客户生命周期将联动为已成交`, '标记成交', { type: 'success' })
  await winOpp(row.id)
  ElMessage.success('已成交')
  await Promise.all([load(), loadFunnel()])
}

async function onLose(row: OppRow) {
  const { value } = await ElMessageBox.prompt('丢单原因为必填（统计数据口径）', `标记丢单：${row.name}`, {
    confirmButtonText: '确认',
    cancelButtonText: '取消',
    inputPlaceholder: '选择或输入原因',
    inputValidator: (v: string) => (v && v.trim() ? true : '丢单原因不能为空'),
  })
  await loseOpp(row.id, value.trim())
  ElMessage.success('已标记丢单')
  await Promise.all([load(), loadFunnel()])
}

async function onDelete(id: number) {
  await deleteOpp(id)
  ElMessage.success('已删除')
  await Promise.all([load(), loadFunnel()])
}

onMounted(() => {
  load()
  loadFunnel()
})
</script>

<style scoped>
.funnel-card :deep(.el-card__body) {
  padding: 12px 20px;
}
.funnel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.funnel-total {
  font-size: 13px;
  color: #6b7280;
}
.funnel-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 3px 0;
}
.funnel-name {
  width: 76px;
  font-size: 13px;
  color: #374151;
  text-align: right;
  flex: none;
}
.funnel-bar-wrap {
  flex: 1;
  background: #f3f4f6;
  border-radius: 4px;
  height: 16px;
  overflow: hidden;
}
.funnel-bar {
  height: 100%;
  border-radius: 4px;
}
.funnel-bar.open {
  background: linear-gradient(90deg, #60a5fa, #2563eb);
}
.funnel-bar.won {
  background: linear-gradient(90deg, #34d399, #059669);
}
.funnel-bar.lost {
  background: linear-gradient(90deg, #f87171, #dc2626);
}
.funnel-metric {
  width: 110px;
  font-size: 12px;
  color: #6b7280;
  flex: none;
}
.funnel-metric.strong {
  color: #2563eb;
  font-weight: 600;
}
.toolbar {
  margin-top: 16px;
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
.terminal-tip {
  font-size: 12px;
  color: #9ca3af;
}
</style>
