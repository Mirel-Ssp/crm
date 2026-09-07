import { createRouter, createWebHashHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

/**
 * 路由表（对应需求一级导航九大模块，P0 仅注册骨架路由）
 * 守卫：未登录访问受限页 → 跳登录
 */
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue'), meta: { title: '登录', public: true } },
    {
      path: '/',
      component: () => import('@/layouts/BasicLayout.vue'),
      redirect: '/workbench',
      children: [
        { path: 'workbench', name: 'workbench', component: () => import('@/views/workbench/WorkbenchView.vue'), meta: { title: '工作台' } },
        { path: 'customer', name: 'customer', component: () => import('@/views/customer/CustomerView.vue'), meta: { title: '客户管理' } },
        { path: 'lead', name: 'lead', component: () => import('@/views/lead/LeadView.vue'), meta: { title: '线索管理' } },
        { path: 'opportunity', name: 'opportunity', component: () => import('@/views/opportunity/OpportunityView.vue'), meta: { title: '商机管理' } },
        { path: 'trading', name: 'trading', component: () => import('@/views/trading/TradingView.vue'), meta: { title: '交易业务' } },
        { path: 'order-contract', name: 'orderContract', component: () => import('@/views/order-contract/OrderContractView.vue'), meta: { title: '订单与合同' } },
        { path: 'workflow', name: 'workflow', component: () => import('@/views/workflow/WorkflowView.vue'), meta: { title: '审批中心' } },
        { path: 'service', name: 'service', component: () => import('@/views/service/ServiceView.vue'), meta: { title: '客户服务' } },
        { path: 'customer-value', name: 'customerValue', component: () => import('@/views/value/CustomerValueView.vue'), meta: { title: '客户价值' } },
        { path: 'report', name: 'report', component: () => import('@/views/report/ReportView.vue'), meta: { title: '统计分析' } },
        { path: 'multi-stat', name: 'multiStat', component: () => import('@/views/stat/MultiStatView.vue'), meta: { title: '多维统计' } },
        { path: 'system', name: 'system', component: () => import('@/views/system/SystemView.vue'), meta: { title: '系统管理' } },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/workbench' },
  ],
})

router.beforeEach((to) => {
  document.title = `${to.meta.title ?? ''} · CRM 客户管理系统`
  if (to.meta.public) return true
  const userStore = useUserStore()
  if (!userStore.token) return { name: 'login' }
  return true
})

export default router
