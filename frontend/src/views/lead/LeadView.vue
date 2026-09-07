<template>
  <div class="page">
    <!-- 筛选工具栏 -->
    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent>
        <el-form-item>
          <el-radio-group v-model="query.pool" @change="search">
            <el-radio-button value="mine">我的线索</el-radio-button>
            <el-radio-button value="public">公共池</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="公司名称搜索" clearable style="width: 200px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
            <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
        <el-form-item class="right">
          <el-button v-permission="'lead:create'" type="primary" plain @click="openCreate">新增线索</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="rows" v-loading="loading" stripe>
        <el-table-column prop="companyName" label="公司名称" min-width="170" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="130" />
        <el-table-column label="来源" width="110">
          <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
        </el-table-column>
        <el-table-column prop="ownerName" label="负责人" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <!-- 领取：公共池 + PENDING -->
            <el-button
              v-if="row.status === 'PENDING' && row.ownerId === 0"
              v-permission="'lead:claim'" link type="primary" @click="onClaim(row)"
            >领取</el-button>
            <!-- 分配：PENDING -->
            <el-button
              v-if="row.status === 'PENDING'"
              v-permission="'lead:assign'" link type="primary" @click="openAssign(row)"
            >分配</el-button>
            <!-- 转客户：已领取/已分配 -->
            <el-button
              v-if="row.status === 'CLAIMED' || row.status === 'ASSIGNED'"
              v-permission="'lead:convert'" link type="success" @click="onConvert(row)"
            >转客户</el-button>
            <!-- 编辑/删除：未转客户 -->
            <el-button
              v-if="row.status !== 'CONVERTED'"
              v-permission="'lead:create'" link type="primary" @click="openEdit(row)"
            >编辑</el-button>
            <el-popconfirm v-if="row.status !== 'CONVERTED'" title="确认删除该线索？" @confirm="onDelete(row.id)">
              <template #reference><el-button v-permission="'lead:create'" link type="danger">删除</el-button></template>
            </el-popconfirm>
            <!-- 作废：非终态 -->
            <el-popconfirm
              v-if="row.status === 'PENDING' || row.status === 'CLAIMED' || row.status === 'ASSIGNED'"
              title="确认作废该线索？" @confirm="onInvalidate(row.id)"
            >
              <template #reference><el-button v-permission="'lead:invalidate'" link type="warning">作废</el-button></template>
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
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑线索' : '新增线索（入公共池）'" width="520px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="公司名称" required><el-input v-model="editForm.companyName" maxlength="128" /></el-form-item>
        <el-form-item label="联系人" required><el-input v-model="editForm.contactName" maxlength="32" /></el-form-item>
        <el-form-item label="联系电话" required><el-input v-model="editForm.contactPhone" maxlength="20" /></el-form-item>
        <el-form-item label="来源">
          <el-select v-model="editForm.source" clearable style="width: 100%">
            <el-option v-for="x in sourceOptions" :key="x.value" :label="x.label" :value="x.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="500" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配弹窗 -->
    <el-dialog v-model="assignVisible" title="分配线索" width="420px">
      <el-select v-model="assignForm.userId" placeholder="选择成员" style="width: 100%">
        <el-option v-for="u in assignees" :key="u.id" :label="u.realName" :value="u.id" />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onAssign">确认分配</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  assignLead, assignableUsers, claimLead, convertLead, createLead, deleteLead,
  invalidateLead, pageLeads, updateLead, type LeadRow,
} from '@/api/lead'
import { useDict } from '@/composables/useDict'

// 线索状态机（前端展示用）：PENDING→CLAIMED/ASSIGNED→CONVERTED/INVALID
const STATUS_OPTIONS = [
  { value: 'PENDING', label: '待处理' },
  { value: 'CLAIMED', label: '已领取' },
  { value: 'ASSIGNED', label: '已分配' },
  { value: 'CONVERTED', label: '已转客户' },
  { value: 'INVALID', label: '已作废' },
] as const
const statusLabel = (s: string) => STATUS_OPTIONS.find((x) => x.value === s)?.label ?? s
const statusTag = (s: string) =>
  s === 'CONVERTED' ? 'success' : s === 'INVALID' ? 'info' : s === 'PENDING' ? 'warning' : 'primary'

// 来源字典动态化（sys_dict lead_source）
const { options: sourceOptions, labelOfDict: sourceLabel } = useDict('lead_source')

// ---------- 列表 ----------
const query = reactive({ pool: 'mine', keyword: '', status: '', pageNum: 1, pageSize: 20 })
const rows = ref<LeadRow[]>([])
const total = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const data = await pageLeads({ ...query })
    rows.value = data.list
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  query.pageNum = 1
  load()
}

function resetQuery() {
  Object.assign(query, { pool: 'mine', keyword: '', status: '', pageNum: 1 })
  load()
}

// ---------- 新增/编辑 ----------
const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ id: 0, companyName: '', contactName: '', contactPhone: '', source: '', remark: '' })

function openCreate() {
  Object.assign(editForm, { id: 0, companyName: '', contactName: '', contactPhone: '', source: '', remark: '' })
  editVisible.value = true
}

function openEdit(row: LeadRow) {
  Object.assign(editForm, {
    id: row.id, companyName: row.companyName, contactName: row.contactName,
    contactPhone: row.contactPhone, source: row.source, remark: '',
  })
  editVisible.value = true
}

async function onSave() {
  if (!editForm.companyName.trim() || !editForm.contactName.trim() || !editForm.contactPhone.trim()) {
    ElMessage.warning('请填写公司名称、联系人与联系电话')
    return
  }
  saving.value = true
  try {
    const payload = {
      companyName: editForm.companyName.trim(), contactName: editForm.contactName.trim(),
      contactPhone: editForm.contactPhone.trim(), source: editForm.source || undefined, remark: editForm.remark || undefined,
    }
    if (editForm.id) {
      await updateLead(editForm.id, payload)
      ElMessage.success('已保存')
    } else {
      await createLead(payload)
      ElMessage.success('已创建（公共池）')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 动作 ----------
async function onClaim(row: LeadRow) {
  await claimLead(row.id)
  ElMessage.success('已领取')
  await load()
}

async function onConvert(row: LeadRow) {
  await ElMessageBox.confirm(`确认将线索「${row.companyName}」转为客户？`, '转客户', { type: 'warning' })
  const customerId = await convertLead(row.id)
  ElMessage.success(`已转为客户（ID ${customerId}）`)
  await load()
}

async function onInvalidate(id: number) {
  await invalidateLead(id)
  ElMessage.success('已作废')
  await load()
}

async function onDelete(id: number) {
  await deleteLead(id)
  ElMessage.success('已删除')
  await load()
}

// ---------- 分配 ----------
const assignVisible = ref(false)
const assignees = ref<{ id: number; realName: string }[]>([])
const assignForm = reactive({ leadId: 0, userId: 0 })

async function openAssign(row: LeadRow) {
  assignees.value = await assignableUsers()
  if (assignees.value.length === 0) {
    ElMessage.warning('数据范围内无可分配成员')
    return
  }
  Object.assign(assignForm, { leadId: row.id, userId: 0 })
  assignVisible.value = true
}

async function onAssign() {
  if (!assignForm.userId) {
    ElMessage.warning('请选择分配成员')
    return
  }
  saving.value = true
  try {
    await assignLead(assignForm.leadId, assignForm.userId)
    ElMessage.success('已分配')
    assignVisible.value = false
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
.toolbar .right {
  float: right;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
