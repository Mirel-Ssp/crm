<template>
  <div class="page">
    <el-tabs v-model="activeTab" @tab-change="onTabChange">
      <!-- ==================== 我的待办（CRM-F4 任务中心） ==================== -->
      <el-tab-pane label="我的待办任务" name="tasks">
        <el-card shadow="never">
          <template #header>
            <div class="card-head">
              <span>待办任务（含候选组未认领任务，认领后进入本人名下）</span>
              <el-button size="small" @click="loadTasks">刷新</el-button>
            </div>
          </template>
          <el-table :data="tasks" v-loading="tasksLoading" stripe>
            <el-table-column prop="name" label="任务" width="130">
              <template #default="{ row }">
                <el-tag effect="plain">{{ row.name }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="processName" label="所属流程" width="150" show-overflow-tooltip />
            <el-table-column label="业务单据" min-width="200">
              <template #default="{ row }">
                <template v-if="row.processKey === 'quoteApproval' && row.vars?.quoteNo">
                  报价单 {{ row.vars.quoteNo }}（{{ row.vars.title ?? '-' }}）
                </template>
                <template v-else>{{ row.businessKey || '-' }}</template>
              </template>
            </el-table-column>
            <el-table-column label="认领状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.assignee" type="success" size="small" effect="plain">已认领</el-tag>
                <el-tag v-else type="warning" size="small" effect="plain">候选中</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="创建时间" width="160">
              <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.assignee" v-permission="'wf:manage'" link type="primary" @click="onClaim(row)">认领</el-button>
                <!-- 报价审批任务：走业务接口（回写报价单状态 + 完成 BPMN 任务） -->
                <template v-if="row.processKey === 'quoteApproval' && row.businessKey?.startsWith('QUOTE:')">
                  <el-button v-permission="'quote:approve'" link type="success" @click="onApprove(row)">通过</el-button>
                  <el-button v-permission="'quote:approve'" link type="danger" @click="onReject(row)">驳回</el-button>
                </template>
                <!-- 其余流程任务：通用完成（携带变量） -->
                <el-button
                  v-else v-permission="'wf:manage'" link type="success" @click="onComplete(row)"
                >完成任务</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="暂无待办任务" :image-size="60" /></template>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ==================== 流程定义（引擎部署情况） ==================== -->
      <el-tab-pane label="流程定义" name="defs">
        <el-card shadow="never">
          <el-table :data="definitions" v-loading="defsLoading" stripe>
            <el-table-column prop="key" label="流程 Key" width="180" />
            <el-table-column prop="name" label="流程名称" min-width="160" />
            <el-table-column prop="version" label="版本" width="80" align="center" />
            <el-table-column prop="deploymentId" label="部署 ID" min-width="220" show-overflow-tooltip />
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="onViewXml(row)">查看 BPMN</el-button>
              </template>
            </el-table-column>
            <template #empty><el-empty description="引擎尚未部署流程（应用启动后自动部署 classpath:/processes/）" :image-size="60" /></template>
          </el-table>
        </el-card>
      </el-tab-pane>
    </el-tabs>

    <!-- BPMN XML 查看器 -->
    <el-dialog v-model="xmlVisible" :title="`BPMN 定义：${xmlName}`" width="760px">
      <pre class="xml">{{ xmlText }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { fmtTime } from '@/api/trade'
import { approveQuote, rejectQuote } from '@/api/quote'
import {
  wfClaimTask, wfCompleteTask, wfDefinitions, wfDefinitionXml, wfMyTasks,
  type WfDefinitionRow, type WfTaskRow,
} from '@/api/workflow'

const activeTab = ref('tasks')

// ==================== 待办任务 ====================
const tasks = ref<WfTaskRow[]>([])
const tasksLoading = ref(false)

async function loadTasks() {
  tasksLoading.value = true
  try {
    tasks.value = await wfMyTasks()
  } finally {
    tasksLoading.value = false
  }
}

async function onClaim(row: WfTaskRow) {
  await ElMessageBox.confirm(`认领后任务进入本人名下，确认认领「${row.name}」？`, '认领任务', { type: 'info' })
  await wfClaimTask(row.id)
  ElMessage.success('已认领')
  await loadTasks()
}

/** 报价审批：businessKey = QUOTE:{id}，走业务接口同步回写报价单状态 */
function quoteIdOf(row: WfTaskRow): number {
  return Number(row.businessKey?.split(':')[1])
}

async function onApprove(row: WfTaskRow) {
  const { value } = await ElMessageBox.prompt('审批意见（选填）', `通过任务：${row.name}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '审批意见（可留空）',
  })
  await approveQuote(quoteIdOf(row), value?.trim() || undefined)
  ElMessage.success('已通过（报价单已回写）')
  await loadTasks()
}

async function onReject(row: WfTaskRow) {
  const { value } = await ElMessageBox.prompt('驳回原因为必填', `驳回任务：${row.name}`, {
    confirmButtonText: '确认', cancelButtonText: '取消', inputPlaceholder: '请输入驳回原因',
    inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因不能为空'),
  })
  await rejectQuote(quoteIdOf(row), value.trim())
  ElMessage.success('已驳回（报价单已回写）')
  await loadTasks()
}

/** 通用任务完成（非报价流程）：弹窗确认后完成 */
async function onComplete(row: WfTaskRow) {
  await ElMessageBox.confirm(`确认完成任务「${row.name}」？任务完成后流程推进至下一节点`, '完成任务', { type: 'success' })
  await wfCompleteTask(row.id)
  ElMessage.success('任务已完成')
  await loadTasks()
}

// ==================== 流程定义 ====================
const definitions = ref<WfDefinitionRow[]>([])
const defsLoading = ref(false)
const xmlVisible = ref(false)
const xmlText = ref('')
const xmlName = ref('')

async function loadDefs() {
  defsLoading.value = true
  try {
    definitions.value = await wfDefinitions()
  } finally {
    defsLoading.value = false
  }
}

async function onViewXml(row: WfDefinitionRow) {
  xmlText.value = await wfDefinitionXml(row.id)
  xmlName.value = `${row.name}（v${row.version}）`
  xmlVisible.value = true
}

function onTabChange(tab: string | number) {
  if (tab === 'defs' && definitions.value.length === 0) loadDefs()
}

onMounted(loadTasks)
</script>

<style scoped>
.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.xml {
  max-height: 60vh;
  overflow: auto;
  background: #0f172a;
  color: #e2e8f0;
  padding: 14px;
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.6;
}
</style>
