<template>
  <el-container class="layout">
    <!-- B6-08：移动端遮罩（抽屉打开时点击关闭） -->
    <div v-if="isMobile && menuOpen" class="mask" @click="menuOpen = false" />
    <el-aside width="220px" class="aside" :class="{ 'aside-mobile': isMobile, 'aside-open': isMobile && menuOpen }">
      <div class="brand">CRM 客户管理系统</div>
      <el-menu
        :default-active="$route.path" router
        background-color="#1E293B" text-color="#CBD5E1" active-text-color="#38BDF8"
        @select="isMobile && (menuOpen = false)"
      >
        <el-menu-item index="/workbench">工作台</el-menu-item>
        <el-menu-item index="/customer">客户管理</el-menu-item>
        <el-menu-item index="/lead">线索管理</el-menu-item>
        <el-menu-item index="/opportunity">商机管理</el-menu-item>
        <el-menu-item index="/trading">交易业务</el-menu-item>
        <el-menu-item index="/order-contract">订单与合同</el-menu-item>
        <!-- CRM-F4：审批中心（BPMN 任务中心，持 wf:task:list 者可见） -->
        <el-menu-item v-if="userStore.hasPerm('wf:task:list')" index="/workflow">审批中心</el-menu-item>
        <el-menu-item index="/service">客户服务</el-menu-item>
        <!-- B5：客户价值/多维统计（权限 904/906 持有者可见） -->
        <el-menu-item v-if="userStore.hasPerm('va:score:list')" index="/customer-value">客户价值</el-menu-item>
        <el-menu-item index="/report">统计分析</el-menu-item>
        <el-menu-item v-if="userStore.hasPerm('stat:report')" index="/multi-stat">多维统计</el-menu-item>
        <!-- SYS-DV-02：仅持系统管理权限点的用户可见 -->
        <el-menu-item v-if="userStore.canAccessSystem" index="/system">系统管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <div class="header-left">
          <el-button v-if="isMobile" :icon="Expand" circle text aria-label="打开菜单" @click="menuOpen = true" />
        </div>
        <div class="header-right">
          <!-- RTP-DV-01：动态流通知（5s 轮询兜底，仅 trade:feed 权限可见） -->
          <el-popover v-if="userStore.hasPerm('trade:feed')" placement="bottom-end" width="380" trigger="click" @show="loadFeed">
            <template #reference>
              <el-badge :value="unread" :hidden="unread === 0" :max="99" class="bell-wrap">
                <el-button :icon="Bell" circle text />
              </el-badge>
            </template>
            <div class="feed-head">
              <span>交易动态流</span>
              <el-button link type="primary" size="small" @click="onReadAll">全部已读</el-button>
            </div>
            <div class="feed-list">
              <div v-for="f in feed" :key="f.id" class="feed-item" :class="{ unread: !f.read }" @click="onReadOne(f)">
                <p class="t"><b>{{ f.title }}</b><span class="time">{{ fmtTime(f.createdAt) }}</span></p>
                <p class="c">{{ f.content }}</p>
              </div>
              <el-empty v-if="feed.length === 0" description="暂无消息" :image-size="60" />
            </div>
          </el-popover>
          <el-dropdown>
            <span class="user">{{ userStore.username || '未登录' }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="openPwdDialog">修改密码</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>

  <!-- 自助改密（V2 遗留问题4）：mustChangePassword=1 时强制弹出且不可关闭 -->
  <el-dialog
    v-model="pwdVisible"
    title="修改密码"
    width="440px"
    :show-close="!forced"
    :close-on-click-modal="false"
    :close-on-press-escape="!forced"
  >
    <el-alert
      v-if="forced"
      type="warning"
      :closable="false"
      show-icon
      title="首次登录或密码已被重置，请先修改密码后再使用系统"
      class="mb8"
    />
    <el-form :model="pwdForm" label-width="90px">
      <el-form-item label="原密码" required>
        <el-input v-model="pwdForm.oldPassword" type="password" show-password />
      </el-form-item>
      <el-form-item label="新密码" required>
        <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 8 位" maxlength="64" />
      </el-form-item>
      <el-form-item label="确认新密码" required>
        <el-input v-model="pwdForm.confirm" type="password" show-password maxlength="64" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button v-if="!forced" @click="pwdVisible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="onChangePassword">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import { Bell, Expand } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { changePassword, fetchMe } from '@/api/auth'
import {
  notifyFeed, notifyRead, notifyReadAll, notifyUnreadCount,
  fmtTime, type FeedRow,
} from '@/api/trade'
import { useNotifyWs } from '@/api/notifyWs'

const userStore = useUserStore()
const router = useRouter()

// ---------- B6-08：移动端响应式（<768px 抽屉式侧边栏） ----------
const isMobile = ref(window.innerWidth < 768)
const menuOpen = ref(false)
const MOBILE_QUERY = '(max-width: 768px)'
let mq: MediaQueryList | null = null
function onMqChange(e: MediaQueryListEvent) {
  isMobile.value = e.matches
  if (!e.matches) menuOpen.value = false
}

function logout() {
  userStore.clear()
  router.push({ name: 'login' })
}

// ---------- 动态流通知（RTP-WS：WS 实时推送优先，5s 轮询兜底） ----------
const unread = ref(0)
const feed = ref<FeedRow[]>([])
let timer: ReturnType<typeof setInterval> | undefined

async function pollUnread() {
  try {
    unread.value = await notifyUnreadCount()
  } catch {
    // 会话失效由 request 拦截器统一处理
  }
}

// WS 推送到达（RTP-DV-02）：徽标 +1 并弹 toast；feed 已加载则增量插入
function onWsNotify(n: { id: number; title: string; content: string; createdAt: string }) {
  unread.value += 1
  feed.value = [
    { id: n.id, type: 'ORDER_EVENT', title: n.title, content: n.content, relType: '', relId: 0, read: false, createdAt: n.createdAt },
    ...feed.value.slice(0, 19),
  ]
  ElNotification({ title: n.title, message: n.content, type: 'info', duration: 5000 })
}

// 轮询兜底（RTP-DV-01 保留）：仅 WS 未连接期间真实发起请求
const { connected: wsConnected, connect: wsConnect, close: wsClose } = useNotifyWs(onWsNotify)

async function loadFeed() {
  feed.value = (await notifyFeed(1, 20)).list
}

async function onReadOne(f: FeedRow) {
  if (f.read) return
  await notifyRead(f.id)
  f.read = true
  unread.value = Math.max(0, unread.value - 1)
}

async function onReadAll() {
  await notifyReadAll()
  feed.value = feed.value.map((f) => ({ ...f, read: true }))
  unread.value = 0
}

// ---------- 自助改密（V2 遗留问题4） ----------
const forced = ref(false)
const pwdVisible = ref(false)
const saving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })

function openPwdDialog() {
  forced.value = false
  Object.assign(pwdForm, { oldPassword: '', newPassword: '', confirm: '' })
  pwdVisible.value = true
}

async function onChangePassword() {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码与新密码')
    return
  }
  if (pwdForm.newPassword.length < 8) {
    ElMessage.warning('新密码至少 8 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirm) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await changePassword(pwdForm.oldPassword, pwdForm.newPassword)
    ElMessage.success('密码已修改')
    pwdVisible.value = false
    forced.value = false
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  // B6-08：监听视口变化（桌面/移动切换）
  mq = window.matchMedia(MOBILE_QUERY)
  if (mq.addEventListener) {
    mq.addEventListener('change', onMqChange)
  } else if (mq.addListener) {
    // Safari < 14 兜底
    mq.addListener((e: MediaQueryListEvent) => onMqChange(e))
  }
  try {
    const me = await fetchMe()
    // 首登/被重置（mustChangePassword=1）→ 强制改密弹窗
    if (me.mustChangePassword === 1) {
      forced.value = true
      pwdVisible.value = true
    }
  } catch {
    // 会话失效由 request 拦截器统一处理
  }
  // RTP-WS：WS 实时推送优先；WS 未连接期间 5s 轮询兜底（RTP-DV-01）
  if (userStore.hasPerm('trade:feed')) {
    await pollUnread()
    wsConnect()
    timer = setInterval(() => {
      if (!wsConnected.value) void pollUnread()
    }, 5000)
  }
})

onUnmounted(() => {
  if (timer) clearInterval(timer)
  if (mq?.removeEventListener) mq.removeEventListener('change', onMqChange)
  wsClose()
})
</script>

<style scoped>
.layout {
  height: 100%;
}
.aside {
  background: #1e293b;
}
/* B6-08：移动端抽屉式侧边栏（<768px 收起，汉堡按钮唤出） */
.aside-mobile {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 1001;
  transform: translateX(-100%);
  transition: transform 0.25s ease;
}
.aside-mobile.aside-open {
  transform: translateX(0);
}
.mask {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 1000;
}
.brand {
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  padding: 18px 20px;
  border-bottom: 1px solid #334155;
}
.header {
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}
.user {
  cursor: pointer;
  font-size: 14px;
}
.mb8 {
  margin-bottom: 8px;
}
</style>
