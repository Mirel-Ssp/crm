<template>
  <div>
    <el-form inline @submit.prevent class="panel-toolbar">
      <el-form-item>
        <el-input v-model="query.keyword" placeholder="用户名/姓名搜索" clearable style="width: 200px" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="query.orgId" placeholder="所属组织" clearable style="width: 170px">
          <el-option v-for="o in orgOptions" :key="o.id" :label="o.label" :value="o.id" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
          <el-option label="启用" value="ACTIVE" />
          <el-option label="停用" value="DISABLED" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="search">查询</el-button>
        <el-button @click="resetQuery">重置</el-button>
      </el-form-item>
      <el-form-item class="right">
        <el-button v-permission="'system:user'" type="primary" plain @click="openCreate">新增成员</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="username" label="用户名" width="120" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="orgName" label="组织" min-width="140" show-overflow-tooltip />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACTIVE' ? 'success' : 'info'">{{ row.status === 'ACTIVE' ? '启用' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="140">
        <template #default="{ row }">{{ row.roleNames.join('、') || '-' }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:user'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:user'" link type="primary" @click="openRoles(row)">分配角色</el-button>
          <el-button v-permission="'system:user'" link type="warning" @click="openResetPwd(row)">重置密码</el-button>
          <el-popconfirm v-if="row.id !== 1" title="确认删除该成员？" @confirm="onDelete(row.id)">
            <template #reference>
              <el-button v-permission="'system:user'" link type="danger">删除</el-button>
            </template>
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

    <!-- 新增/编辑 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑成员' : '新增成员'" width="520px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item v-if="!editForm.id" label="用户名" required>
          <el-input v-model="editForm.username" maxlength="32" />
        </el-form-item>
        <el-form-item v-if="!editForm.id" label="初始密码" required>
          <el-input v-model="editForm.password" type="password" show-password placeholder="至少 8 位" maxlength="64" />
        </el-form-item>
        <el-form-item label="姓名" required>
          <el-input v-model="editForm.realName" maxlength="32" />
        </el-form-item>
        <el-form-item label="所属组织" required>
          <el-select v-model="editForm.orgId" style="width: 100%">
            <el-option v-for="o in orgOptions" :key="o.id" :label="o.label" :value="o.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="editForm.phone" :placeholder="editForm.id ? '留空 = 不变更' : ''" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="editForm.email" :placeholder="editForm.id ? '留空 = 不变更' : ''" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="editForm.id" label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="!editForm.id" label="角色">
          <el-select v-model="editForm.roleIds" multiple style="width: 100%">
            <el-option v-for="r in roles" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色 -->
    <el-dialog v-model="rolesVisible" title="分配角色（全量覆盖）" width="420px">
      <el-select v-model="roleForm.roleIds" multiple style="width: 100%">
        <el-option v-for="r in roles" :key="r.id" :label="`${r.name}（${r.dataScope}）`" :value="r.id" />
      </el-select>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveRoles">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码 -->
    <el-dialog v-model="pwdVisible" title="重置密码" width="400px">
      <el-input v-model="pwdForm.password" type="password" show-password placeholder="新密码至少 8 位" maxlength="64" />
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePwd">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  assignUserRoles, createUser, deleteUser, orgTree, pageUsers, resetUserPassword, updateUser,
  listRoles, type OrgNode, type RoleRow, type UserRow,
} from '@/api/system'

const query = reactive({ keyword: '', orgId: undefined as number | undefined, status: '', pageNum: 1, pageSize: 20 })
const rows = ref<UserRow[]>([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const orgOptions = ref<{ id: number; label: string }[]>([])
const roles = ref<RoleRow[]>([])

async function loadOrgOptions() {
  const toOptions = (nodes: OrgNode[], prefix = ''): { id: number; label: string }[] =>
    nodes.flatMap((n) => [{ id: n.id, label: prefix + n.name }, ...toOptions(n.children, prefix + n.name + ' / ')])
  orgOptions.value = toOptions(await orgTree())
}

async function load() {
  loading.value = true
  try {
    const data = await pageUsers({ ...query, orgId: query.orgId || undefined })
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
  Object.assign(query, { keyword: '', orgId: undefined, status: '', pageNum: 1 })
  load()
}

// ---------- 新增/编辑 ----------
const editVisible = ref(false)
const editForm = reactive({
  id: 0, username: '', password: '', realName: '', orgId: 0,
  email: '', phone: '', status: 'ACTIVE', roleIds: [] as number[],
})

function openCreate() {
  Object.assign(editForm, { id: 0, username: '', password: '', realName: '', orgId: orgOptions.value[0]?.id ?? 0, email: '', phone: '', status: 'ACTIVE', roleIds: [] })
  editVisible.value = true
}

function openEdit(row: UserRow) {
  Object.assign(editForm, { id: row.id, username: '', password: '', realName: row.realName, orgId: row.orgId, email: '', phone: '', status: row.status, roleIds: [] })
  editVisible.value = true
}

async function onSave() {
  if (!editForm.id) {
    if (!editForm.username.trim() || !editForm.realName.trim()) {
      ElMessage.warning('请填写用户名与姓名')
      return
    }
    if (editForm.password.length < 8) {
      ElMessage.warning('初始密码至少 8 位')
      return
    }
  }
  saving.value = true
  try {
    if (editForm.id) {
      await updateUser(editForm.id, {
        orgId: editForm.orgId, realName: editForm.realName,
        email: editForm.email || undefined, phone: editForm.phone || undefined, status: editForm.status,
      })
      ElMessage.success('已保存')
    } else {
      await createUser({
        orgId: editForm.orgId, username: editForm.username.trim(), password: editForm.password,
        realName: editForm.realName.trim(), email: editForm.email || undefined, phone: editForm.phone || undefined,
        roleIds: editForm.roleIds,
      })
      ElMessage.success('已创建')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 分配角色 ----------
const rolesVisible = ref(false)
const roleForm = reactive({ userId: 0, roleIds: [] as number[] })

function openRoles(row: UserRow) {
  Object.assign(roleForm, { userId: row.id, roleIds: [...row.roleIds] })
  rolesVisible.value = true
}

async function onSaveRoles() {
  saving.value = true
  try {
    await assignUserRoles(roleForm.userId, roleForm.roleIds)
    ElMessage.success('已保存')
    rolesVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 重置密码 ----------
const pwdVisible = ref(false)
const pwdForm = reactive({ userId: 0, password: '' })

function openResetPwd(row: UserRow) {
  Object.assign(pwdForm, { userId: row.id, password: '' })
  pwdVisible.value = true
}

async function onSavePwd() {
  if (pwdForm.password.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  saving.value = true
  try {
    await resetUserPassword(pwdForm.userId, pwdForm.password)
    ElMessage.success('已重置')
    pwdVisible.value = false
  } finally {
    saving.value = false
  }
}

async function onDelete(id: number) {
  await deleteUser(id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([loadOrgOptions(), listRoles().then((r) => (roles.value = r))])
  await load()
})
</script>

<style scoped>
.panel-toolbar {
  margin-bottom: 6px;
}
.panel-toolbar .right {
  float: right;
}
.pager {
  margin-top: 14px;
  justify-content: flex-end;
}
</style>
