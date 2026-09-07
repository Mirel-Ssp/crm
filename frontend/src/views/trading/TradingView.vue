<template>
  <div class="page">
    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="标的名称/代码搜索" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.category" placeholder="标的类型" clearable style="width: 140px">
            <el-option v-for="x in categoryOptions" :key="x.value" :label="x.label" :value="x.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
            <el-option v-for="(v, k) in ITEM_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="right">
          <el-button v-permission="'trade:item:manage'" type="primary" plain @click="openCreate">新建标的</el-button>
          <el-button v-permission="'trade:item:manage'" plain @click="openRules">规则配置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 标的列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="code" label="代码" width="110" />
        <el-table-column prop="name" label="标的名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">{{ categoryLabel(String(row.category)) }}</template>
        </el-table-column>
        <el-table-column prop="market" label="市场" width="100" />
        <el-table-column label="参考价格" width="120" align="right">
          <template #default="{ row }">¥ {{ fmtMoney(row.referencePrice) }}</template>
        </el-table-column>
        <el-table-column label="费率" width="90" align="right">
          <template #default="{ row }">{{ (row.feeRate * 100).toFixed(4) }}%</template>
        </el-table-column>
        <el-table-column prop="minQuantity" label="最小交易量" width="100" align="right" />
        <el-table-column label="风险等级" width="90">
          <template #default="{ row }">
            <el-tag :type="row.riskLevel === 'HIGH' ? 'danger' : row.riskLevel === 'LOW' ? 'success' : 'warning'" effect="plain">
              {{ row.riskLevel === 'HIGH' ? '高' : row.riskLevel === 'LOW' ? '低' : '中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="ITEM_STATUS[row.status]?.tag || 'info'" effect="plain">{{ ITEM_STATUS[row.status]?.label || row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'trade:item:manage'" link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'trade:item:manage'" link type="warning" @click="openPrice(row)">调价</el-button>
            <el-button link type="info" @click="openLogs(row)">留痕</el-button>
            <el-button
              v-if="row.status === 'DRAFT' || row.status === 'DELISTED'"
              v-permission="'trade:item:manage'" link type="success" @click="onList(row)"
            >上架</el-button>
            <el-button
              v-if="row.status === 'LISTED'"
              v-permission="'trade:item:manage'" link type="danger" @click="onDelist(row)"
            >下架</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新建/编辑标的 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑标的' : '新建标的'" width="560px">
      <el-form :model="editForm" label-width="110px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="标的代码" required><el-input v-model="editForm.code" maxlength="32" :disabled="false" /></el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="标的名称" required><el-input v-model="editForm.name" maxlength="64" /></el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="标的类型" required>
              <el-select v-model="editForm.category" style="width: 100%">
                <el-option v-for="x in categoryOptions" :key="x.value" :label="x.label" :value="x.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="市场"><el-input v-model="editForm.market" maxlength="32" /></el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="参考价格" required>
              <el-input-number v-model="editForm.referencePrice" :min="0.0001" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="风险等级">
              <el-select v-model="editForm.riskLevel" style="width: 100%">
                <el-option label="低" value="LOW" />
                <el-option label="中" value="MEDIUM" />
                <el-option label="高" value="HIGH" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="手续费率">
              <el-input-number v-model="feeRatePercent" :min="0" :max="100" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最小交易量">
              <el-input-number v-model="editForm.minQuantity" :min="0" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
        <el-alert v-if="!editForm.id" type="info" :closable="false" title="新建后为草稿状态，需上架后才能创建订单；调价请使用「调价」入口以留痕" />
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 调价 -->
    <el-dialog v-model="priceVisible" :title="`调价：${priceRow?.name || ''}`" width="420px">
      <el-form label-width="100px">
        <el-form-item label="当前价格">¥ {{ fmtMoney(priceRow?.referencePrice) }}</el-form-item>
        <el-form-item label="新价格" required>
          <el-input-number v-model="newPrice" :min="0.0001" :precision="4" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePrice">确认调价</el-button>
      </template>
    </el-dialog>

    <!-- 调价留痕 -->
    <el-dialog v-model="logsVisible" :title="`调价留痕：${logsRow?.name || ''}`" width="560px">
      <el-table :data="logs" v-loading="logsLoading" size="small" stripe>
        <el-table-column label="旧价格" width="130" align="right">
          <template #default="{ row }">{{ row.oldPrice == null ? '—（初始价）' : fmtMoney(row.oldPrice) }}</template>
        </el-table-column>
        <el-table-column label="新价格" width="130" align="right">
          <template #default="{ row }">{{ fmtMoney(row.newPrice) }}</template>
        </el-table-column>
        <el-table-column label="变更时间" width="170">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 规则配置 -->
    <el-dialog v-model="rulesVisible" title="交易规则配置（TB-3）" width="560px">
      <el-table :data="rules" v-loading="rulesLoading" size="small" stripe>
        <el-table-column prop="ruleKey" label="规则键" width="200" />
        <el-table-column label="规则值" width="160">
          <template #default="{ row }">
            <el-input-number v-model="row.ruleValue" :min="0" :precision="2" size="small" style="width: 130px" />
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="说明" show-overflow-tooltip />
      </el-table>
      <template #footer>
        <el-button @click="rulesVisible = false">关闭</el-button>
        <el-button v-permission="'trade:item:manage'" type="primary" :loading="saving" @click="onSaveRules">保存规则</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  changeItemPrice, createTradeItem, delistItem, itemPriceLogs, listItem, pageTradeItems,
  tradeRules, updateTradeItem, updateTradeRule, ITEM_STATUS, fmtMoney, fmtTime,
  type PriceLogRow, type TradeItemRow, type TradeRuleRow,
} from '@/api/trade'
import { useDict } from '@/composables/useDict'

// 标的类型字典（sys_dict：V8 种子 item_category）
const { options: categoryOptions, labelOfDict: categoryLabel } = useDict('item_category')

// ---------- 列表 ----------
const query = reactive({ keyword: '', category: '', status: '' })
const rows = ref<TradeItemRow[]>([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    rows.value = await pageTradeItems({ ...query })
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  Object.assign(query, { keyword: '', category: '', status: '' })
  load()
}

// ---------- 新建/编辑 ----------
const editVisible = ref(false)
const saving = ref(false)
const feeRatePercent = ref(0)
const editForm = reactive({
  id: 0, code: '', name: '', category: '', market: '',
  referencePrice: 1, riskLevel: 'MEDIUM', minQuantity: 1, remark: '',
})

function openCreate() {
  Object.assign(editForm, { id: 0, code: '', name: '', category: '', market: '', referencePrice: 1, riskLevel: 'MEDIUM', minQuantity: 1, remark: '' })
  feeRatePercent.value = 0
  editVisible.value = true
}

function openEdit(row: TradeItemRow) {
  Object.assign(editForm, {
    id: row.id, code: row.code, name: row.name, category: row.category, market: row.market,
    referencePrice: row.referencePrice, riskLevel: row.riskLevel || 'MEDIUM',
    minQuantity: row.minQuantity, remark: row.remark || '',
  })
  feeRatePercent.value = Number(((row.feeRate ?? 0) * 100).toFixed(4))
  editVisible.value = true
}

async function onSave() {
  if (!editForm.code.trim() || !editForm.name.trim() || !editForm.category) {
    ElMessage.warning('请填写标的代码、名称与类型')
    return
  }
  saving.value = true
  try {
    const payload = {
      code: editForm.code.trim(),
      name: editForm.name.trim(),
      category: editForm.category,
      market: editForm.market || undefined,
      referencePrice: editForm.referencePrice,
      riskLevel: editForm.riskLevel,
      feeRate: feeRatePercent.value / 100,
      minQuantity: editForm.minQuantity,
      remark: editForm.remark || undefined,
    }
    if (editForm.id) {
      await updateTradeItem(editForm.id, payload)
      ElMessage.success('已保存')
    } else {
      await createTradeItem(payload)
      ElMessage.success('已创建（草稿状态，请上架后使用）')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 调价 ----------
const priceVisible = ref(false)
const priceRow = ref<TradeItemRow | null>(null)
const newPrice = ref(1)

function openPrice(row: TradeItemRow) {
  priceRow.value = row
  newPrice.value = row.referencePrice
  priceVisible.value = true
}

async function onSavePrice() {
  if (!priceRow.value) return
  saving.value = true
  try {
    await changeItemPrice(priceRow.value.id, newPrice.value)
    ElMessage.success('调价成功（已留痕）')
    priceVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 调价留痕 ----------
const logsVisible = ref(false)
const logsLoading = ref(false)
const logsRow = ref<TradeItemRow | null>(null)
const logs = ref<PriceLogRow[]>([])

async function openLogs(row: TradeItemRow) {
  logsRow.value = row
  logsVisible.value = true
  logsLoading.value = true
  try {
    logs.value = await itemPriceLogs(row.id)
  } finally {
    logsLoading.value = false
  }
}

// ---------- 上下架 ----------
async function onList(row: TradeItemRow) {
  await ElMessageBox.confirm(`确认上架「${row.name}」？上架后可创建订单`, '标的上架', { type: 'success' })
  await listItem(row.id)
  ElMessage.success('已上架')
  await load()
}

async function onDelist(row: TradeItemRow) {
  await ElMessageBox.confirm(`确认下架「${row.name}」？下架后不可创建新订单（存量订单不受影响）`, '标的下架', { type: 'warning' })
  await delistItem(row.id)
  ElMessage.success('已下架')
  await load()
}

// ---------- 规则配置 ----------
const rulesVisible = ref(false)
const rulesLoading = ref(false)
const rules = ref<TradeRuleRow[]>([])

async function openRules() {
  rulesVisible.value = true
  rulesLoading.value = true
  try {
    rules.value = await tradeRules()
  } finally {
    rulesLoading.value = false
  }
}

async function onSaveRules() {
  saving.value = true
  try {
    for (const r of rules.value) {
      await updateTradeRule(r.id, r.ruleValue)
    }
    ElMessage.success('规则已保存')
    rulesVisible.value = false
  } finally {
    saving.value = false
  }
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
</style>
