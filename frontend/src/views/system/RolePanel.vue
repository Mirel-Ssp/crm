<template>
  <div>
    <div class="panel-toolbar">
      <el-button v-permission="'system:role'" type="primary" plain @click="openCreate">新增角色</el-button>
    </div>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="code" label="编码" width="140" />
      <el-table-column prop="name" label="名称" width="140" />
      <el-table-column label="数据范围" width="110">
        <template #default="{ row }">
          <el-tag :type="row.dataScope === 'ALL' ? 'danger' : row.dataScope === 'TEAM' ? 'warning' : 'info'">
            {{ scopeLabel(row.dataScope) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="权限点数" width="90">
        <template #default="{ row }">{{ row.permissionIds.length }}</template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:role'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-permission="'system:role'" link type="primary" @click="openPerms(row)">分配权限</el-button>
          <el-popconfirm title="确认删除该角色？（被引用不可删）" @confirm="onDelete(row.id)">
            <template #reference><el-button v-permission="'system:role'" link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑角色' : '新增角色'" width="480px">
      <el-form :model="editForm" label-width="90px">
        <el-form-item label="编码" required>
          <el-input v-model="editForm.code" :disabled="!!editForm.id" maxlength="32" placeholder="如 sales_manager" />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="editForm.name" maxlength="32" />
        </el-form-item>
        <el-form-item label="数据范围">
          <el-select v-model="editForm.dataScope" style="width: 100%">
            <el-option label="全部数据（ALL）" value="ALL" />
            <el-option label="本团队（TEAM）" value="TEAM" />
            <el-option label="仅本人（SELF）" value="SELF" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限点 -->
    <el-dialog v-model="permVisible" :title="`分配权限点（${permForm.roleName}）`" width="560px">
      <el-checkbox-group v-model="permForm.permissionIds">
        <el-checkbox v-for="p in permissions" :key="p.id" :value="p.id" class="perm-item">
          {{ p.name }}（{{ p.code }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="permVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSavePerms">保存（全量覆盖）</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  assignRolePermissions, createRole, deleteRole, listPermissions, listRoles, updateRole,
  type PermissionRow, type RoleRow,
} from '@/api/system'

const rows = ref<RoleRow[]>([])
const permissions = ref<PermissionRow[]>([])
const loading = ref(false)
const saving = ref(false)

const scopeLabel = (s: string) => (s === 'ALL' ? '全部数据' : s === 'TEAM' ? '本团队' : '仅本人')

async function load() {
  loading.value = true
  try {
    rows.value = await listRoles()
  } finally {
    loading.value = false
  }
}

// ---------- 新增/编辑 ----------
const editVisible = ref(false)
const editForm = reactive({ id: 0, code: '', name: '', dataScope: 'SELF', remark: '' })

function openCreate() {
  Object.assign(editForm, { id: 0, code: '', name: '', dataScope: 'SELF', remark: '' })
  editVisible.value = true
}

function openEdit(row: RoleRow) {
  Object.assign(editForm, { id: row.id, code: row.code, name: row.name, dataScope: row.dataScope, remark: row.remark })
  editVisible.value = true
}

async function onSave() {
  if (!editForm.code.trim() || !editForm.name.trim()) {
    ElMessage.warning('请填写角色编码与名称')
    return
  }
  saving.value = true
  try {
    const payload = { code: editForm.code.trim(), name: editForm.name.trim(), dataScope: editForm.dataScope, remark: editForm.remark }
    if (editForm.id) {
      await updateRole(editForm.id, payload)
      ElMessage.success('已保存（数据范围变更即时生效）')
    } else {
      await createRole(payload)
      ElMessage.success('已创建')
    }
    editVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

// ---------- 分配权限点 ----------
const permVisible = ref(false)
const permForm = reactive({ roleId: 0, roleName: '', permissionIds: [] as number[] })

function openPerms(row: RoleRow) {
  Object.assign(permForm, { roleId: row.id, roleName: row.name, permissionIds: [...row.permissionIds] })
  permVisible.value = true
}

async function onSavePerms() {
  saving.value = true
  try {
    await assignRolePermissions(permForm.roleId, permForm.permissionIds)
    ElMessage.success('已保存（持有该角色的用户即时生效）')
    permVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(id: number) {
  await deleteRole(id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  await Promise.all([load(), listPermissions().then((p) => (permissions.value = p))])
})
</script>

<style scoped>
.panel-toolbar {
  margin-bottom: 14px;
}
.perm-item {
  display: flex;
  width: 46%;
  margin-right: 0;
}
</style>
