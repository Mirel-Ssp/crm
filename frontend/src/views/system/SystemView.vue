<template>
  <el-card shadow="never" class="system-card">
    <el-tabs v-model="activeTab">
      <el-tab-pane v-for="t in tabs" :key="t.name" :label="t.label" :name="t.name" lazy>
        <component :is="t.comp" />
      </el-tab-pane>
    </el-tabs>
    <el-empty v-if="tabs.length === 0" description="无系统管理权限（system:*）" />
  </el-card>
</template>

<script setup lang="ts">
import { ref, type Component } from 'vue'
import { useUserStore } from '@/stores/user'
import OrgPanel from './OrgPanel.vue'
import UserPanel from './UserPanel.vue'
import RolePanel from './RolePanel.vue'
import DictPanel from './DictPanel.vue'

/**
 * 系统管理（SYS-DV-01/05）：按权限点过滤 Tab，
 * lazy 渲染保证首次切入才发请求；默认选中首个有权限的 Tab
 */
const userStore = useUserStore()

const tabs: { name: string; label: string; perm: string; comp: Component }[] = [
  { name: 'org', label: '组织管理', perm: 'system:org', comp: OrgPanel },
  { name: 'user', label: '成员管理', perm: 'system:user', comp: UserPanel },
  { name: 'role', label: '角色权限', perm: 'system:role', comp: RolePanel },
  { name: 'dict', label: '数据字典', perm: 'system:dict', comp: DictPanel },
].filter((t) => userStore.hasPerm(t.perm))

const activeTab = ref(tabs[0]?.name ?? 'org')
</script>

<style scoped>
.system-card :deep(.el-card__body) {
  padding-top: 8px;
}
</style>
