<template>
  <div>
    <div class="panel-toolbar">
      <el-button type="primary" plain @click="openCreate(0)">新增根组织</el-button>
      <el-button type="primary" plain :disabled="!current" @click="openCreate(current!.id)">新增子组织</el-button>
      <el-button plain :disabled="!current" @click="openEdit">编辑</el-button>
      <el-popconfirm title="确认删除该组织？（须无子节点与成员）" @confirm="onDelete">
        <template #reference><el-button type="danger" plain :disabled="!current">删除</el-button></template>
      </el-popconfirm>
      <span v-if="current" class="picked">当前选中：{{ current.name }}</span>
    </div>

    <el-tree
      v-loading="loading"
      :data="tree"
      node-key="id"
      :props="{ label: 'name', children: 'children' }"
      default-expand-all
      highlight-current
      @node-click="onSelect"
    />

    <el-dialog v-model="formVisible" :title="form.id ? '编辑组织' : '新增组织'" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="上级组织">
          <el-input :model-value="parentName" disabled />
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="负责人ID">
          <el-input-number v-model="form.leaderId" :min="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createOrg, deleteOrg, orgTree, updateOrg, type OrgNode } from '@/api/system'

const tree = ref<OrgNode[]>([])
const current = ref<OrgNode | null>(null)
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const form = reactive({ id: 0, parentId: 0, name: '', leaderId: 0, sort: 0 })

async function load() {
  loading.value = true
  try {
    tree.value = await orgTree()
  } finally {
    loading.value = false
  }
}

function onSelect(node: OrgNode) {
  current.value = node
}

function parentNameOf(pid: number): string {
  if (pid === 0) return '（根节点）'
  const find = (nodes: OrgNode[]): OrgNode | null => {
    for (const n of nodes) {
      if (n.id === pid) return n
      const hit = find(n.children)
      if (hit) return hit
    }
    return null
  }
  return find(tree.value)?.name ?? `ID ${pid}`
}

const parentName = ref('（根节点）')

function openCreate(parentId: number) {
  Object.assign(form, { id: 0, parentId, name: '', leaderId: 0, sort: 0 })
  parentName.value = parentNameOf(parentId)
  formVisible.value = true
}

function openEdit() {
  if (!current.value) return
  const n = current.value
  Object.assign(form, { id: n.id, parentId: n.parentId, name: n.name, leaderId: n.leaderId, sort: n.sort })
  parentName.value = parentNameOf(n.parentId)
  formVisible.value = true
}

async function onSave() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入组织名称')
    return
  }
  saving.value = true
  try {
    const payload = { parentId: form.parentId, name: form.name.trim(), leaderId: form.leaderId || null, sort: form.sort }
    if (form.id) {
      await updateOrg(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await createOrg(payload)
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete() {
  if (!current.value) return
  await deleteOrg(current.value.id)
  ElMessage.success('已删除')
  current.value = null
  await load()
}

onMounted(load)
</script>

<style scoped>
.panel-toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 14px;
  align-items: center;
}
.picked {
  margin-left: auto;
  font-size: 13px;
  color: #64748b;
}
</style>
