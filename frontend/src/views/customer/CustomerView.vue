<template>
  <div class="page">
    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="客户名称搜索" clearable style="width: 220px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.level" placeholder="等级" clearable style="width: 130px">
            <el-option v-for="x in levelOptions" :key="x.value" :label="x.label" :value="x.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
            <el-option v-for="x in CUSTOMER_STATUSES" :key="x.value" :label="x.label" :value="x.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item style="float: right">
          <el-button v-permission="'customer:merge'" plain @click="openMerge">合并客户</el-button>
          <el-button type="primary" plain @click="openCreate">新增客户</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="name" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="等级" width="110">
          <template #default="{ row }"><el-tag :type="levelTag(row.level)">{{ levelLabel(row.level) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="industry" label="行业" width="120" />
        <el-table-column prop="region" label="地区" width="120" />
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ labelOf(CUSTOMER_STATUSES, row.status) }}</template>
        </el-table-column>
        <el-table-column label="生命周期" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="lifecycleTag(row.lifecycleStatus)" effect="plain">
              {{ lifecycleLabel(row.lifecycleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row.id)">编辑</el-button>
            <el-popconfirm title="确认删除该客户？" @confirm="onDelete(row.id)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
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
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑客户' : '新增客户'" width="560px">
      <el-form :model="editForm" :rules="rules" ref="editFormRef" label-width="90px">
        <el-form-item label="客户名称" prop="name"><el-input v-model="editForm.name" maxlength="128" /></el-form-item>
        <el-row>
          <el-col :span="12"><el-form-item label="等级"><el-select v-model="editForm.level" style="width: 100%"><el-option v-for="x in levelOptions" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="状态"><el-select v-model="editForm.status" style="width: 100%"><el-option v-for="x in CUSTOMER_STATUSES" :key="x.value" :label="x.label" :value="x.value" /></el-select></el-form-item></el-col>
        </el-row>
        <el-row>
          <el-col :span="12"><el-form-item label="行业"><el-input v-model="editForm.industry" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="地区"><el-input v-model="editForm.region" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="地址"><el-input v-model="editForm.address" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="客户详情" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="名称" :span="2">{{ detail.customer.name }}</el-descriptions-item>
          <el-descriptions-item label="等级">{{ levelLabel(detail.customer.level) }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ labelOf(CUSTOMER_STATUSES, detail.customer.status) }}</el-descriptions-item>
          <el-descriptions-item label="行业">{{ detail.customer.industry || '-' }}</el-descriptions-item>
          <el-descriptions-item label="地区">{{ detail.customer.region || '-' }}</el-descriptions-item>
          <el-descriptions-item label="负责人">{{ detail.ownerName }}</el-descriptions-item>
          <el-descriptions-item label="地址">{{ detail.customer.address || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detail.customer.remark || '-' }}</el-descriptions-item>
          <el-descriptions-item label="生命周期">
            <el-select
              v-model="lifecycleValue"
              size="small"
              style="width: 130px"
              @change="onLifecycleChange"
            >
              <el-option v-for="x in LIFECYCLES" :key="x.value" :label="x.label" :value="x.value" />
            </el-select>
          </el-descriptions-item>
          <el-descriptions-item label="自定义字段">
            {{ customFieldsText || '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <el-tabs>
          <el-tab-pane label="联系人">
            <el-button size="small" type="primary" plain @click="contactFormVisible = true">添加联系人</el-button>
            <el-table :data="detail.contacts" size="small" class="mt8">
              <el-table-column prop="name" label="姓名" width="90" />
              <el-table-column prop="position" label="职务" width="100" />
              <el-table-column prop="phone" label="电话" width="130" />
              <el-table-column prop="email" label="邮箱" show-overflow-tooltip />
              <el-table-column label="主" width="50">
                <template #default="{ row }"><el-tag v-if="row.isPrimary === 1" size="small" type="warning">主</el-tag></template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ row }">
                  <el-popconfirm title="确认删除？" @confirm="onDeleteContact(row.id)">
                    <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane :label="`跟进记录(${detail.followups.length})`">
            <el-button size="small" type="primary" plain @click="followupFormVisible = true">新增跟进</el-button>
            <el-timeline class="mt8">
              <el-timeline-item v-for="f in detail.followups" :key="f.id" :timestamp="f.createdAt" placement="top">
                <p><b>{{ labelOf(FOLLOWUP_METHODS, f.method) }}</b>　{{ f.content }}</p>
                <el-tag v-if="f.status === 'TODO'" size="small" type="danger" class="mt4">
                  待跟进：{{ f.nextFollowupAt?.replace('T', ' ') }}
                </el-tag>
              </el-timeline-item>
              <el-empty v-if="detail.followups.length === 0" description="暂无跟进记录" :image-size="60" />
            </el-timeline>
          </el-tab-pane>
          <el-tab-pane :label="`商机(${detail.opportunities?.length ?? 0})`">
            <el-table :data="detail.opportunities ?? []" size="small" class="mt8">
              <el-table-column prop="name" label="商机名称" min-width="140" show-overflow-tooltip />
              <el-table-column label="阶段" width="90">
                <template #default="{ row }">{{ stageLabel(String(row.stage)) }}</template>
              </el-table-column>
              <el-table-column label="金额" width="110" align="right">
                <template #default="{ row }">¥ {{ row.amount ?? 0 }}</template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.status === 'WON' ? 'success' : row.status === 'LOST' ? 'danger' : 'primary'" effect="plain">
                    {{ row.status === 'WON' ? '已成交' : row.status === 'LOST' ? '已丢单' : '进行中' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="(detail.opportunities?.length ?? 0) === 0" description="暂无商机" :image-size="60" />
          </el-tab-pane>
          <el-tab-pane label="时间轴（360°）">
            <el-timeline class="mt8" v-if="timeline.length > 0">
              <el-timeline-item
                v-for="(e, i) in timeline"
                :key="i"
                :type="e.type === 'FOLLOWUP' ? 'primary' : e.type === 'OPP_STAGE' ? 'success' : 'warning'"
                :timestamp="e.time.replace('T', ' ')"
                placement="top"
              >
                <p><b>{{ e.title }}</b></p>
                <p class="tl-content">{{ e.content }}</p>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else description="暂无时间轴数据" :image-size="60" />
          </el-tab-pane>
        </el-tabs>
      </template>
    </el-drawer>

    <!-- 添加联系人弹窗 -->
    <el-dialog v-model="contactFormVisible" title="添加联系人" width="480px">
      <el-form :model="contactForm" label-width="80px">
        <el-form-item label="姓名" required><el-input v-model="contactForm.name" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="contactForm.position" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="contactForm.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="contactForm.email" /></el-form-item>
        <el-form-item label="微信"><el-input v-model="contactForm.wechat" /></el-form-item>
        <el-form-item label="主联系人"><el-switch v-model="contactForm.isPrimary" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactFormVisible = false">取消</el-button>
        <el-button type="primary" @click="onSaveContact">保存</el-button>
      </template>
    </el-dialog>

    <!-- 新增跟进弹窗 -->
    <el-dialog v-model="followupFormVisible" title="新增跟进" width="480px">
      <el-form :model="followupForm" label-width="90px">
        <el-form-item label="方式">
          <el-select v-model="followupForm.method" style="width: 100%">
            <el-option v-for="x in FOLLOWUP_METHODS" :key="x.value" :label="x.label" :value="x.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容" required><el-input v-model="followupForm.content" type="textarea" :rows="3" maxlength="1000" /></el-form-item>
        <el-form-item label="是否待办">
          <el-switch v-model="followupForm.isTodo" />
        </el-form-item>
        <el-form-item v-if="followupForm.isTodo" label="下次跟进">
          <el-date-picker v-model="followupForm.nextFollowupAt" type="datetime" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="followupFormVisible = false">取消</el-button>
        <el-button type="primary" @click="onSaveFollowup">保存</el-button>
      </template>
    </el-dialog>

    <!-- 客户合并弹窗（CRM-C5）：source 并入 target，关联数据整体搬迁 -->
    <el-dialog v-model="mergeVisible" title="合并客户（源客户并入目标）" width="520px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="合并后源客户将被软删除，其联系人/跟进/商机全部迁入目标客户，操作不可撤销"
        class="mb8"
      />
      <el-form label-width="100px">
        <el-form-item label="目标客户" required>
          <el-select v-model="mergeForm.targetId" filterable placeholder="保留的客户" style="width: 100%">
            <el-option v-for="c in mergeCandidates" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="源客户" required>
          <el-select v-model="mergeForm.sourceId" filterable placeholder="被并入后删除" style="width: 100%">
            <el-option v-for="c in mergeCandidates" :key="c.id" :label="c.name" :value="c.id" :disabled="c.id === mergeForm.targetId" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="mergeVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="onMerge">确认合并</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import {
  changeLifecycle, checkCustomerDuplicate, createContact, createCustomer, createFollowup,
  customerDetail, customerTimeline, deleteContact, deleteCustomer, mergeCustomer,
  pageCustomers, updateCustomer,
  type CustomerDetail, type CustomerRow, type PageData, type TimelineEvent,
} from '@/api/crm'
import {
  CUSTOMER_STATUSES, FOLLOWUP_METHODS, labelOf as staticLabelOf,
} from '@/utils/dict'
import { useDict } from '@/composables/useDict'

// T14：等级下拉动态化（sys_dict customer_level）；状态/跟进方式暂无字典种子，保持静态
const { options: levelOptions, labelOfDict: levelLabel } = useDict('customer_level')
const { labelOfDict: stageLabel } = useDict('opportunity_stage')
const labelOf = staticLabelOf

// 生命周期（CRM-C4）：五态；与 V4 迁移 CHECK 约束一致
const LIFECYCLES = [
  { value: 'POTENTIAL', label: '潜在客户' },
  { value: 'FOLLOWING', label: '跟进中' },
  { value: 'WON', label: '已成交' },
  { value: 'LOST', label: '已流失' },
  { value: 'DORMANT', label: '休眠' },
] as const
const lifecycleLabel = (s: string | null | undefined) =>
  LIFECYCLES.find((x) => x.value === s)?.label ?? s ?? '-'
const lifecycleTag = (s: string | null | undefined) =>
  s === 'WON' ? 'success' : s === 'LOST' ? 'danger' : s === 'FOLLOWING' ? 'primary' : 'info'

// ---------- 列表 ----------
const query = reactive({ keyword: '', level: '', status: '', pageNum: 1, pageSize: 20 })
const rows = ref<CustomerRow[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data: PageData<CustomerRow> = await pageCustomers({ ...query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.keyword = ''
  query.level = ''
  query.status = ''
  query.pageNum = 1
  load()
}

function levelTag(level: string) {
  return level === 'VIP' ? 'danger' : level === 'IMPORTANT' ? 'warning' : 'info'
}

// ---------- 新增/编辑 ----------
const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref<FormInstance>()
const editForm = reactive({
  id: 0, name: '', level: 'NORMAL', status: 'ACTIVE',
  industry: '', region: '', address: '', remark: '',
})
const rules: FormRules = {
  name: [{ required: true, message: '请输入客户名称', trigger: 'blur' }],
}

function openCreate() {
  Object.assign(editForm, { id: 0, name: '', level: 'NORMAL', status: 'ACTIVE', industry: '', region: '', address: '', remark: '' })
  editVisible.value = true
}

async function openEdit(id: number) {
  const d = await customerDetail(id)
  Object.assign(editForm, {
    id, name: d.customer.name, level: d.customer.level ?? 'NORMAL',
    status: d.customer.status ?? 'ACTIVE', industry: d.customer.industry,
    region: d.customer.region, address: d.customer.address, remark: d.customer.remark,
  })
  editVisible.value = true
}

async function onSave() {
  await editFormRef.value?.validate()
  saving.value = true
  try {
    const { id, ...payload } = editForm
    if (id) {
      await updateCustomer(id, payload)
      ElMessage.success('已保存')
    } else {
      // CRM-C5：录入查重——名称精确命中时提示疑似重复，可选择继续或取消
      const hits = await checkCustomerDuplicate(editForm.name.trim())
      if (hits.length > 0) {
        const names = hits.map((h) => `「${h.name}」`).join('、')
        await ElMessageBox.confirm(
          `发现疑似重复客户：${names}，是否仍要继续创建？`,
          '查重提示', { confirmButtonText: '仍要创建', cancelButtonText: '取消', type: 'warning' },
        )
      }
      await createCustomer(payload)
      ElMessage.success('已创建')
    }
    editVisible.value = false
    load()
  } catch {
    // 查重取消：静默，不关闭弹窗
  } finally {
    saving.value = false
  }
}

async function onDelete(id: number) {
  await deleteCustomer(id)
  ElMessage.success('已删除')
  load()
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref<CustomerDetail | null>(null)
const detailId = ref(0)
const timeline = ref<TimelineEvent[]>([])

/** 生命周期内联编辑（CRM-C4：流转留痕）；ref 随详情加载同步 */
const lifecycleValue = ref('POTENTIAL')

const customFieldsText = computed(() => {
  const cf = detail.value?.customer.customFields
  if (!cf || Object.keys(cf).length === 0) return ''
  return Object.entries(cf).map(([k, v]) => `${k}: ${v}`).join('；')
})

async function onLifecycleChange(to: string) {
  if (!detail.value) return
  const from = lifecycleLabel(detail.value.customer.lifecycleStatus)
  await changeLifecycle(detailId.value, to)
  ElMessage.success(`生命周期已从【${from}】变更为【${lifecycleLabel(to)}】`)
  await Promise.all([reloadDetail(), loadTimeline()])
}

async function loadTimeline() {
  timeline.value = await customerTimeline(detailId.value)
}

async function openDetail(id: number) {
  detailId.value = id
  detail.value = await customerDetail(id)
  lifecycleValue.value = detail.value.customer.lifecycleStatus ?? 'POTENTIAL'
  timeline.value = []
  detailVisible.value = true
  loadTimeline()
}

async function reloadDetail() {
  detail.value = await customerDetail(detailId.value)
  lifecycleValue.value = detail.value.customer.lifecycleStatus ?? 'POTENTIAL'
}

// ---------- 联系人 ----------
const contactFormVisible = ref(false)
const contactForm = reactive({ name: '', position: '', phone: '', email: '', wechat: '', isPrimary: false })

async function onSaveContact() {
  if (!contactForm.name.trim()) {
    ElMessage.warning('请输入联系人姓名')
    return
  }
  await createContact(detailId.value, { ...contactForm })
  ElMessage.success('已添加')
  contactFormVisible.value = false
  Object.assign(contactForm, { name: '', position: '', phone: '', email: '', wechat: '', isPrimary: false })
  await reloadDetail()
}

async function onDeleteContact(contactId: number) {
  await deleteContact(detailId.value, contactId)
  ElMessage.success('已删除')
  await reloadDetail()
}

// ---------- 跟进 ----------
const followupFormVisible = ref(false)
const followupForm = reactive({ method: 'PHONE', content: '', isTodo: false, nextFollowupAt: '' })

async function onSaveFollowup() {
  if (!followupForm.content.trim()) {
    ElMessage.warning('请输入跟进内容')
    return
  }
  if (followupForm.isTodo && !followupForm.nextFollowupAt) {
    ElMessage.warning('请选择下次跟进时间')
    return
  }
  await createFollowup({
    relType: 'CUSTOMER',
    relId: detailId.value,
    content: followupForm.content,
    method: followupForm.method,
    status: followupForm.isTodo ? 'TODO' : 'DONE',
    nextFollowupAt: followupForm.isTodo ? followupForm.nextFollowupAt : undefined,
  })
  ElMessage.success('已记录')
  followupFormVisible.value = false
  Object.assign(followupForm, { method: 'PHONE', content: '', isTodo: false, nextFollowupAt: '' })
  await reloadDetail()
}

// ---------- 合并（CRM-C5） ----------
const mergeVisible = ref(false)
const mergeCandidates = ref<{ id: number; name: string }[]>([])
const mergeForm = reactive({ targetId: undefined as number | undefined, sourceId: undefined as number | undefined })

async function openMerge() {
  const data = await pageCustomers({ pageNum: 1, pageSize: 200 })
  mergeCandidates.value = data.list.map((c) => ({ id: c.id, name: c.name }))
  if (mergeCandidates.value.length < 2) {
    ElMessage.warning('数据范围内客户不足 2 个，无可合并对象')
    return
  }
  Object.assign(mergeForm, { targetId: undefined, sourceId: undefined })
  mergeVisible.value = true
}

async function onMerge() {
  if (!mergeForm.targetId || !mergeForm.sourceId) {
    ElMessage.warning('请选择目标客户与源客户')
    return
  }
  if (mergeForm.targetId === mergeForm.sourceId) {
    ElMessage.warning('目标与源不能是同一客户')
    return
  }
  const targetName = mergeCandidates.value.find((c) => c.id === mergeForm.targetId)?.name
  await ElMessageBox.confirm(
    `确认将「${mergeCandidates.value.find((c) => c.id === mergeForm.sourceId)?.name}」并入「${targetName}」？关联数据将整体搬迁且不可撤销`,
    '确认合并', { confirmButtonText: '确认合并', cancelButtonText: '取消', type: 'warning' },
  )
  saving.value = true
  try {
    await mergeCustomer(mergeForm.targetId, mergeForm.sourceId)
    ElMessage.success('已合并')
    mergeVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.toolbar :deep(.el-form-item) {
  margin-bottom: 0;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
.mt8 {
  margin-top: 8px;
}
.mt4 {
  margin-top: 4px;
}
.mb8 {
  margin-bottom: 8px;
}
.tl-content {
  color: #6b7280;
  font-size: 13px;
}
</style>
