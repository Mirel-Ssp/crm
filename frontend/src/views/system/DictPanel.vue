<template>
  <div>
    <el-form inline @submit.prevent class="panel-toolbar">
      <el-form-item>
        <el-select v-model="queryType" placeholder="字典类型" clearable style="width: 200px" @change="search">
          <el-option v-for="t in typeOptions" :key="t" :label="t" :value="t" />
        </el-select>
      </el-form-item>
      <el-form-item class="right">
        <el-button v-permission="'system:dict'" type="primary" plain @click="openCreate">新增字典项</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="rows" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="dictType" label="类型" width="150" />
      <el-table-column prop="code" label="编码（存储值）" width="140" />
      <el-table-column prop="value" label="显示名" min-width="160" />
      <el-table-column prop="sort" label="排序" width="80" />
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button v-permission="'system:dict'" link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-popconfirm title="确认删除该字典项？" @confirm="onDelete(row.id)">
            <template #reference><el-button v-permission="'system:dict'" link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑字典项' : '新增字典项'" width="460px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="类型" required>
          <el-input v-model="form.dictType" maxlength="32" placeholder="如 customer_level" />
        </el-form-item>
        <el-form-item label="编码（存储值）" required>
          <el-input v-model="form.code" maxlength="32" placeholder="如 VIP" />
        </el-form-item>
        <el-form-item label="显示名" required>
          <el-input v-model="form.value" maxlength="64" placeholder="如 VIP 客户" />
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
import { createDict, deleteDict, listDicts, updateDict, type DictRow } from '@/api/system'

const queryType = ref('')
const rows = ref<DictRow[]>([])
const allRows = ref<DictRow[]>([])
const loading = ref(false)
const saving = ref(false)
const typeOptions = ref<string[]>([])

const formVisible = ref(false)
const form = reactive({ id: 0, dictType: '', code: '', value: '', sort: 0 })

async function load() {
  loading.value = true
  try {
    allRows.value = await listDicts()
    typeOptions.value = [...new Set(allRows.value.map((r) => r.dictType))].sort()
    applyFilter()
  } finally {
    loading.value = false
  }
}

function applyFilter() {
  rows.value = queryType.value ? allRows.value.filter((r) => r.dictType === queryType.value) : allRows.value
}

function search() {
  applyFilter()
}

function openCreate() {
  Object.assign(form, { id: 0, dictType: queryType.value || '', code: '', value: '', sort: 0 })
  formVisible.value = true
}

function openEdit(row: DictRow) {
  Object.assign(form, { id: row.id, dictType: row.dictType, code: row.code, value: row.value, sort: row.sort })
  formVisible.value = true
}

async function onSave() {
  if (!form.dictType.trim() || !form.code.trim() || !form.value.trim()) {
    ElMessage.warning('请填写类型、编码与显示名')
    return
  }
  saving.value = true
  try {
    const payload = { dictType: form.dictType.trim(), code: form.code.trim(), value: form.value.trim(), sort: form.sort }
    if (form.id) {
      await updateDict(form.id, payload)
      ElMessage.success('已保存')
    } else {
      await createDict(payload)
      ElMessage.success('已创建')
    }
    formVisible.value = false
    await load()
  } finally {
    saving.value = false
  }
}

async function onDelete(id: number) {
  await deleteDict(id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.panel-toolbar {
  margin-bottom: 6px;
}
.panel-toolbar .right {
  float: right;
}
</style>
