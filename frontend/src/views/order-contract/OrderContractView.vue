<template>
  <div class="page">
    <el-tabs v-model="activeTab">
      <!-- ==================== 报价单（CRM-R1，批次7） ==================== -->
      <el-tab-pane label="报价单" name="quotes" lazy>
        <QuotePanel />
      </el-tab-pane>

      <!-- ==================== 订单（OD-1~5） ==================== -->
      <el-tab-pane label="交易订单" name="orders">
        <el-card shadow="never" class="toolbar">
          <el-form inline @submit.prevent>
            <el-form-item>
              <el-input v-model="orderQuery.keyword" placeholder="订单号搜索" clearable style="width: 180px" @keyup.enter="loadOrders" />
            </el-form-item>
            <el-form-item>
              <el-select v-model="orderQuery.status" placeholder="状态" clearable style="width: 130px">
                <el-option v-for="(v, k) in ORDER_STATUS" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-select v-model="orderQuery.direction" placeholder="方向" clearable style="width: 110px">
                <el-option label="买入 BUY" value="BUY" />
                <el-option label="卖出 SELL" value="SELL" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadOrders">查询</el-button>
              <el-button @click="resetOrderQuery">重置</el-button>
            </el-form-item>
            <el-form-item class="right">
              <el-button v-permission="'trade:order:create'" type="primary" plain @click="openOrderCreate">新建订单</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <el-table :data="orderRows" v-loading="orderLoading" stripe>
            <el-table-column prop="orderNo" label="订单号" width="200" show-overflow-tooltip />
            <el-table-column prop="itemName" label="标的" min-width="130" show-overflow-tooltip />
            <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
            <el-table-column label="方向" width="80">
              <template #default="{ row }">
                <el-tag :type="row.direction === 'BUY' ? 'success' : 'danger'" effect="plain">{{ row.direction }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="90" align="right">
              <template #default="{ row }">{{ row.quantity }}</template>
            </el-table-column>
            <el-table-column label="单价" width="110" align="right">
              <template #default="{ row }">{{ fmtMoney(row.price) }}</template>
            </el-table-column>
            <el-table-column label="总额（含费）" width="130" align="right">
              <template #default="{ row }">{{ fmtMoney(row.totalAmount) }}</template>
            </el-table-column>
            <el-table-column label="已核销" width="110" align="right">
              <template #default="{ row }">{{ fmtMoney(row.paidAmount) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="ORDER_STATUS[row.status]?.tag || 'info'" effect="plain">{{ ORDER_STATUS[row.status]?.label || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="ownerName" label="经手人" width="90" />
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openOrderDetail(row.id)">详情</el-button>
                <template v-if="row.status === 'PENDING_CONFIRM'">
                  <el-button v-permission="'order:confirm'" link type="success" @click="onApprove(row)">通过</el-button>
                  <el-button v-permission="'order:confirm'" link type="danger" @click="onReject(row)">驳回</el-button>
                </template>
                <el-button
                  v-if="row.status !== 'FULL_DEALT' && row.status !== 'CANCELLED'"
                  v-permission="'order:cancel'" link type="warning" @click="onCancel(row)"
                >取消</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            layout="total, prev, pager, next, sizes"
            :total="orderTotal"
            v-model:current-page="orderQuery.pageNum"
            v-model:page-size="orderQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="loadOrders"
            @size-change="loadOrders"
          />
        </el-card>
      </el-tab-pane>

      <!-- ==================== 汇款与核销（RM-1~4） ==================== -->
      <el-tab-pane label="汇款与核销" name="remits">
        <el-card shadow="never" class="toolbar">
          <el-form inline @submit.prevent>
            <el-form-item>
              <el-input v-model="remitQuery.keyword" placeholder="汇款单号搜索" clearable style="width: 180px" @keyup.enter="loadRemits" />
            </el-form-item>
            <el-form-item>
              <el-select v-model="remitQuery.status" placeholder="状态" clearable style="width: 130px">
                <el-option v-for="(v, k) in REMIT_STATUS" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" @click="loadRemits">查询</el-button>
              <el-button @click="resetRemitQuery">重置</el-button>
            </el-form-item>
            <el-form-item class="right">
              <el-button v-permission="'trade:remit:create'" type="primary" plain @click="openRemitCreate">汇款登记</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never">
          <el-table :data="remitRows" v-loading="remitLoading" stripe>
            <el-table-column prop="remitNo" label="汇款单号" width="200" show-overflow-tooltip />
            <el-table-column prop="customerName" label="客户" min-width="130" show-overflow-tooltip />
            <el-table-column label="金额" width="120" align="right">
              <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="已核销" width="120" align="right">
              <template #default="{ row }">{{ fmtMoney(row.writtenOffAmount) }}</template>
            </el-table-column>
            <el-table-column label="余额" width="120" align="right">
              <template #default="{ row }">{{ fmtMoney(row.balance) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="REMIT_STATUS[row.status]?.tag || 'info'" effect="plain">{{ REMIT_STATUS[row.status]?.label || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="汇款时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.remittedAt) }}</template>
            </el-table-column>
            <el-table-column prop="ownerName" label="登记人" width="90" />
            <el-table-column label="操作" width="210" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openRemitDetail(row.id)">详情</el-button>
                <el-button
                  v-if="row.status === 'PENDING_CONFIRM'"
                  v-permission="'remit:confirm'" link type="success" @click="onRemitConfirm(row)"
                >到账确认</el-button>
                <el-button
                  v-if="row.status === 'PENDING_CONFIRM'"
                  v-permission="'remit:confirm'" link type="danger" @click="onRemitReject(row)"
                >驳回</el-button>
                <el-button
                  v-if="row.status === 'CONFIRMED' || row.status === 'PARTIALLY_WRITTEN_OFF'"
                  v-permission="'remit:writeoff'" link type="warning" @click="openWriteOff(row)"
                >核销</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-pagination
            class="pager"
            layout="total, prev, pager, next, sizes"
            :total="remitTotal"
            v-model:current-page="remitQuery.pageNum"
            v-model:page-size="remitQuery.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="loadRemits"
            @size-change="loadRemits"
          />
        </el-card>
      </el-tab-pane>

      <!-- ==================== 合同管理（CRM-R3，批次7） ==================== -->
      <el-tab-pane label="合同管理" name="contracts" lazy>
        <ContractPanel />
      </el-tab-pane>
    </el-tabs>

    <!-- ==================== 新建订单 ==================== -->
    <el-dialog v-model="orderCreateVisible" title="新建订单（OD-1 服务端计价）" width="560px">
      <el-form :model="orderForm" label-width="100px">
        <el-form-item label="客户" required>
          <el-select v-model="orderForm.customerId" filterable placeholder="选择客户" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标的" required>
          <el-select v-model="orderForm.itemId" filterable placeholder="仅已上架标的" style="width: 100%" @change="onItemPicked">
            <el-option v-for="i in listedItems" :key="i.id" :label="`${i.name}（${i.code}）`" :value="i.id" />
          </el-select>
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="方向" required>
              <el-select v-model="orderForm.direction" style="width: 100%">
                <el-option label="买入 BUY" value="BUY" />
                <el-option label="卖出 SELL" value="SELL" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="数量" required>
              <el-input-number v-model="orderForm.quantity" :min="pickedItem?.minQuantity ?? 0.0001" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="单价">
          <el-input-number v-model="orderForm.price" :min="0" :precision="4" style="width: 100%" :placeholder="String(pickedItem?.referencePrice ?? '')" />
        </el-form-item>
        <el-alert
          v-if="pickedItem"
          type="info" :closable="false"
          :title="`参考价 ¥${fmtMoney(pickedItem.referencePrice)}｜费率 ${(pickedItem.feeRate * 100).toFixed(4)}%｜最小交易量 ${pickedItem.minQuantity}｜单价留空按参考价计`"
        />
      </el-form>
      <template #footer>
        <el-button @click="orderCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveOrder">提交订单</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 订单详情 ==================== -->
    <el-drawer v-model="orderDetailVisible" title="订单详情" size="640px">
      <template v-if="orderDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="订单号">{{ orderDetail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="ORDER_STATUS[orderDetail.status]?.tag || 'info'" effect="plain">{{ ORDER_STATUS[orderDetail.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="标的">{{ orderDetail.itemName }}</el-descriptions-item>
          <el-descriptions-item label="客户">{{ orderDetail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="方向">{{ orderDetail.direction }}</el-descriptions-item>
          <el-descriptions-item label="数量">{{ orderDetail.quantity }}（已成交 {{ orderDetail.dealQuantity }}）</el-descriptions-item>
          <el-descriptions-item label="金额">¥ {{ fmtMoney(orderDetail.amount) }}</el-descriptions-item>
          <el-descriptions-item label="手续费">¥ {{ fmtMoney(orderDetail.feeAmount) }}（{{ (orderDetail.feeRate * 100).toFixed(4) }}%）</el-descriptions-item>
          <el-descriptions-item label="应付总额">¥ {{ fmtMoney(orderDetail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="累计已核销">¥ {{ fmtMoney(orderDetail.paidAmount) }}</el-descriptions-item>
          <el-descriptions-item label="经手人">{{ orderDetail.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ fmtTime(orderDetail.createdAt) }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">状态时间轴</h4>
        <el-timeline class="tl">
          <el-timeline-item v-for="l in orderDetail.logs" :key="l.id" :timestamp="fmtTime(l.createdAt)">
            {{ (l.fromStatus ? statusLabel(l.fromStatus) : '创建') + ' → ' + statusLabel(l.toStatus) }}
            <span v-if="l.reason" class="reason">（{{ l.reason }}）</span>
          </el-timeline-item>
        </el-timeline>

        <h4 class="sec">核销明细</h4>
        <el-table :data="orderDetail.settlements" size="small" stripe>
          <el-table-column prop="remitNo" label="汇款单号" show-overflow-tooltip />
          <el-table-column label="核销金额" width="110" align="right">
            <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="90" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <template #empty><el-empty description="暂无核销" :image-size="50" /></template>
        </el-table>
      </template>
    </el-drawer>

    <!-- ==================== 汇款登记 ==================== -->
    <el-dialog v-model="remitCreateVisible" title="汇款登记（RM-1）" width="560px">
      <el-form :model="remitForm" label-width="100px">
        <el-form-item label="汇款单号" required>
          <el-input v-model="remitForm.remitNo" maxlength="32" placeholder="银行流水/汇款单号" />
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="remitForm.customerId" filterable placeholder="选择客户" style="width: 100%">
            <el-option v-for="c in customers" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row>
          <el-col :span="12">
            <el-form-item label="汇款金额" required>
              <el-input-number v-model="remitForm.amount" :min="0.0001" :precision="4" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="币种">
              <el-select v-model="remitForm.currency" style="width: 100%">
                <el-option label="人民币 CNY" value="CNY" />
                <el-option label="美元 USD" value="USD" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="汇款时间" required>
          <el-date-picker v-model="remitForm.remittedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="凭证 Key"><el-input v-model="remitForm.voucherKey" maxlength="255" placeholder="凭证文件标识（选填）" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="remitForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="remitCreateVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveRemit">提交登记</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 核销 ==================== -->
    <el-dialog v-model="writeOffVisible" title="汇款核销（RM-4 多对多）" width="680px">
      <el-alert
        v-if="writeOffRemit"
        type="info" :closable="false" class="mb8"
        :title="`汇款 ${writeOffRemit.remitNo}｜金额 ¥${fmtMoney(writeOffRemit.amount)}｜剩余可核 ¥${fmtMoney(writeOffRemit.balance)}`"
      />
      <el-table :data="woItems" size="small" stripe>
        <el-table-column label="订单" min-width="220">
          <template #default="{ row }">
            <el-select v-model="row.orderId" filterable placeholder="选择可核销订单" style="width: 100%">
              <el-option
                v-for="o in woOrders" :key="o.id"
                :label="`${o.orderNo}｜${o.itemName}｜应付 ${fmtMoney(o.totalAmount)}｜剩 ${fmtMoney(o.totalAmount - (woPaid[o.id] ?? 0))}`"
                :value="o.id"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="核销金额" width="170">
          <template #default="{ row }">
            <el-input-number v-model="row.amount" :min="0.0001" :precision="4" size="small" style="width: 150px" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70">
          <template #default="{ $index }">
            <el-button link type="danger" @click="woItems.splice($index, 1)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="wo-foot">
        <el-button size="small" @click="addWoItem">+ 添加订单</el-button>
        <span class="wo-total">本次合计：¥ {{ fmtMoney(woTotal) }} / 剩余可核 ¥ {{ fmtMoney(writeOffRemit?.balance) }}</span>
      </div>
      <template #footer>
        <el-button @click="writeOffVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveWriteOff">确认核销</el-button>
      </template>
    </el-dialog>

    <!-- ==================== 汇款详情 ==================== -->
    <el-drawer v-model="remitDetailVisible" title="汇款详情" size="620px">
      <template v-if="remitDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="汇款单号">{{ remitDetail.remitNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="REMIT_STATUS[remitDetail.status]?.tag || 'info'" effect="plain">{{ REMIT_STATUS[remitDetail.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户">{{ remitDetail.customerName }}</el-descriptions-item>
          <el-descriptions-item label="登记人">{{ remitDetail.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥ {{ fmtMoney(remitDetail.amount) }}（{{ remitDetail.currency }}）</el-descriptions-item>
          <el-descriptions-item label="汇款时间">{{ fmtTime(remitDetail.remittedAt) }}</el-descriptions-item>
          <el-descriptions-item label="已核销">¥ {{ fmtMoney(remitDetail.writtenOffAmount) }}</el-descriptions-item>
          <el-descriptions-item label="余额">¥ {{ fmtMoney(remitDetail.balance) }}</el-descriptions-item>
          <el-descriptions-item label="确认人">{{ remitDetail.confirmerName }}</el-descriptions-item>
          <el-descriptions-item label="确认时间">{{ fmtTime(remitDetail.confirmedAt) }}</el-descriptions-item>
          <el-descriptions-item label="凭证 Key" :span="2">{{ remitDetail.voucherKey || '-' }}</el-descriptions-item>
          <el-descriptions-item v-if="remitDetail.rejectReason" label="驳回原因" :span="2">{{ remitDetail.rejectReason }}</el-descriptions-item>
        </el-descriptions>

        <h4 class="sec">核销明细</h4>
        <el-table :data="remitDetail.settlements" size="small" stripe>
          <el-table-column prop="orderNo" label="订单号" show-overflow-tooltip />
          <el-table-column label="核销金额" width="110" align="right">
            <template #default="{ row }">{{ fmtMoney(row.amount) }}</template>
          </el-table-column>
          <el-table-column prop="operatorName" label="操作人" width="90" />
          <el-table-column label="时间" width="160">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
          <template #empty><el-empty description="暂无核销" :image-size="50" /></template>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageCustomers } from '@/api/crm'
import QuotePanel from './QuotePanel.vue'
import ContractPanel from './ContractPanel.vue'
import {
  approveOrder, cancelOrder, confirmRemit, createOrder, fmtMoney, fmtTime, ORDER_STATUS,
  orderDetail as fetchOrderDetail, pageOrders, pageRemits, pageTradeItems, registerRemit, rejectOrder,
  rejectRemit, remitDetail as fetchRemitDetail, REMIT_STATUS, writeOffRemit as submitWriteOff,
  type OrderDetail, type OrderRow, type RemitDetail, type RemitRow, type TradeItemRow,
} from '@/api/trade'

const saving = ref(false)

const statusLabel = (s: string) => ORDER_STATUS[s]?.label || s

// ==================== 订单 ====================
const activeTab = ref('orders')
const orderQuery = reactive({ keyword: '', status: '', direction: '', pageNum: 1, pageSize: 20 })
const orderRows = ref<OrderRow[]>([])
const orderTotal = ref(0)
const orderLoading = ref(false)

async function loadOrders() {
  orderLoading.value = true
  try {
    const d = await pageOrders({ ...orderQuery })
    orderRows.value = d.list
    orderTotal.value = d.total
  } finally {
    orderLoading.value = false
  }
}

function resetOrderQuery() {
  Object.assign(orderQuery, { keyword: '', status: '', direction: '', pageNum: 1 })
  loadOrders()
}

// ---------- 新建订单 ----------
const orderCreateVisible = ref(false)
const customers = ref<{ id: number; name: string }[]>([])
const listedItems = ref<TradeItemRow[]>([])
const pickedItem = computed(() => listedItems.value.find((i) => i.id === orderForm.itemId))
const orderForm = reactive({
  customerId: undefined as number | undefined,
  itemId: undefined as number | undefined,
  direction: 'BUY',
  quantity: 1,
  price: undefined as number | undefined,
})

function onItemPicked() {
  orderForm.price = undefined
  if (pickedItem.value) {
    orderForm.quantity = pickedItem.value.minQuantity ?? 1
  }
}

async function openOrderCreate() {
  const [cs, items] = await Promise.all([
    pageCustomers({ pageNum: 1, pageSize: 200 }).then((d) => d.list.map((c) => ({ id: c.id, name: c.name }))),
    pageTradeItems({ status: 'LISTED' }),
  ])
  customers.value = cs
  listedItems.value = items
  Object.assign(orderForm, { customerId: undefined, itemId: undefined, direction: 'BUY', quantity: 1, price: undefined })
  orderCreateVisible.value = true
}

async function onSaveOrder() {
  if (!orderForm.customerId || !orderForm.itemId) {
    ElMessage.warning('请选择客户与标的')
    return
  }
  saving.value = true
  try {
    await createOrder({
      customerId: orderForm.customerId,
      itemId: orderForm.itemId,
      direction: orderForm.direction,
      quantity: orderForm.quantity,
      price: orderForm.price,
    })
    ElMessage.success('订单已提交（超阈值进入待审批）')
    orderCreateVisible.value = false
    await loadOrders()
  } finally {
    saving.value = false
  }
}

// ---------- 订单详情 ----------
const orderDetailVisible = ref(false)
const orderDetail = ref<OrderDetail | null>(null)

async function openOrderDetail(id: number) {
  orderDetail.value = await fetchOrderDetail(id)
  orderDetailVisible.value = true
}

// ---------- 审批/驳回/取消 ----------
async function onApprove(row: OrderRow) {
  await ElMessageBox.confirm(`确认通过订单「${row.orderNo}」？通过后可发起汇款核销`, '审批通过', { type: 'success' })
  await approveOrder(row.id)
  ElMessage.success('已通过')
  await loadOrders()
}

async function onReject(row: OrderRow) {
  const { value } = await ElMessageBox.prompt('驳回原因为必填（留痕）', `驳回订单：${row.orderNo}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '请输入驳回原因',
    inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
  })
  await rejectOrder(row.id, value.trim())
  ElMessage.success('已驳回')
  await loadOrders()
}

async function onCancel(row: OrderRow) {
  const { value } = await ElMessageBox.prompt(
    '取消规则：业务员仅可取消本人待审批订单；经理可取消数据范围内未完成订单（留痕）',
    `取消订单：${row.orderNo}`,
    { confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '取消原因（选填）' },
  )
  await cancelOrder(row.id, value?.trim() || undefined)
  ElMessage.success('已取消')
  await loadOrders()
}

// ==================== 汇款 ====================
const remitQuery = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 20 })
const remitRows = ref<RemitRow[]>([])
const remitTotal = ref(0)
const remitLoading = ref(false)

async function loadRemits() {
  remitLoading.value = true
  try {
    const d = await pageRemits({ ...remitQuery })
    remitRows.value = d.list
    remitTotal.value = d.total
  } finally {
    remitLoading.value = false
  }
}

function resetRemitQuery() {
  Object.assign(remitQuery, { keyword: '', status: '', pageNum: 1 })
  loadRemits()
}

// ---------- 登记 ----------
const remitCreateVisible = ref(false)
const remitForm = reactive({
  remitNo: '', customerId: undefined as number | undefined, amount: 1,
  currency: 'CNY', remittedAt: '', voucherKey: '', remark: '',
})

async function openRemitCreate() {
  if (customers.value.length === 0) {
    customers.value = await pageCustomers({ pageNum: 1, pageSize: 200 }).then((d) => d.list.map((c) => ({ id: c.id, name: c.name })))
  }
  Object.assign(remitForm, {
    remitNo: '', customerId: undefined, amount: 1,
    currency: 'CNY', remittedAt: '', voucherKey: '', remark: '',
  })
  remitCreateVisible.value = true
}

async function onSaveRemit() {
  if (!remitForm.remitNo.trim() || !remitForm.customerId || !remitForm.remittedAt) {
    ElMessage.warning('请填写汇款单号、客户与汇款时间')
    return
  }
  saving.value = true
  try {
    await registerRemit({
      remitNo: remitForm.remitNo.trim(),
      customerId: remitForm.customerId,
      amount: remitForm.amount,
      currency: remitForm.currency,
      remittedAt: remitForm.remittedAt,
      voucherKey: remitForm.voucherKey || undefined,
      remark: remitForm.remark || undefined,
    })
    ElMessage.success('已登记，等待经理到账确认')
    remitCreateVisible.value = false
    await loadRemits()
  } finally {
    saving.value = false
  }
}

// ---------- 确认/驳回 ----------
async function onRemitConfirm(row: RemitRow) {
  await ElMessageBox.confirm(`确认汇款「${row.remitNo}」已到账？确认后可发起核销`, '到账确认', { type: 'success' })
  await confirmRemit(row.id)
  ElMessage.success('已确认到账')
  await loadRemits()
}

async function onRemitReject(row: RemitRow) {
  const { value } = await ElMessageBox.prompt('驳回原因为必填', `驳回汇款：${row.remitNo}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '请输入驳回原因',
    inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
  })
  await rejectRemit(row.id, value.trim())
  ElMessage.success('已驳回')
  await loadRemits()
}

// ---------- 核销 ----------
const writeOffVisible = ref(false)
const writeOffRemit = ref<RemitRow | null>(null)
const woItems = ref<{ orderId: number | undefined; amount: number }[]>([])
const woOrders = ref<OrderRow[]>([])
const woPaid = ref<Record<number, number>>({})
const woTotal = computed(() => woItems.value.reduce((s, i) => s + (i.amount || 0), 0))

async function openWriteOff(row: RemitRow) {
  writeOffRemit.value = row
  woItems.value = [{ orderId: undefined, amount: row.balance }]
  // 可核销订单：已确认/部分成交（服务端再校验剩余应付）
  const [confirmed, partial] = await Promise.all([
    pageOrders({ status: 'CONFIRMED', pageNum: 1, pageSize: 100 }),
    pageOrders({ status: 'PARTIAL_DEALT', pageNum: 1, pageSize: 100 }),
  ])
  const all = [...confirmed.list, ...partial.list]
  woOrders.value = all
  woPaid.value = Object.fromEntries(all.map((o) => [o.id, 0]))
  // 拉取每单已核销金额（详情接口），展示剩余应付
  await Promise.all(all.map(async (o) => {
    try {
      const d = await fetchOrderDetail(o.id)
      woPaid.value[o.id] = d.paidAmount
    } catch { /* 单条失败不阻塞 */ }
  }))
  writeOffVisible.value = true
}

function addWoItem() {
  woItems.value.push({ orderId: undefined, amount: 1 })
}

async function onSaveWriteOff() {
  const remit = writeOffRemit.value
  if (!remit) return
  const items = woItems.value.filter((i) => i.orderId && i.amount > 0)
  if (items.length === 0) {
    ElMessage.warning('请至少填写一条核销明细')
    return
  }
  if (items.some((i) => woItems.value.filter((x) => x.orderId === i.orderId).length > 1)) {
    ElMessage.warning('同一订单请合并为一条明细')
    return
  }
  if (woTotal.value > remit.balance) {
    ElMessage.warning('本次核销合计超出汇款剩余可核金额')
    return
  }
  saving.value = true
  try {
    await submitWriteOff(remit.id, items.map((i) => ({ orderId: i.orderId!, amount: i.amount })))
    ElMessage.success('核销成功（订单状态已推进）')
    writeOffVisible.value = false
    await Promise.all([loadRemits(), loadOrders()])
  } finally {
    saving.value = false
  }
}

// ---------- 汇款详情 ----------
const remitDetailVisible = ref(false)
const remitDetail = ref<RemitDetail | null>(null)

async function openRemitDetail(id: number) {
  remitDetail.value = await fetchRemitDetail(id)
  remitDetailVisible.value = true
}

onMounted(() => {
  loadOrders()
  loadRemits()
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
.sec {
  margin: 16px 0 8px;
}
.tl {
  padding-left: 4px;
}
.reason {
  color: #6b7280;
  font-size: 12px;
}
.mb8 {
  margin-bottom: 8px;
}
.wo-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 10px;
}
.wo-total {
  font-size: 13px;
  color: #2563eb;
  font-weight: 600;
}
</style>
