import { createRouter, createWebHistory } from 'vue-router'
import { defineComponent, h } from 'vue'
import BasicLayout from '@/layouts/BasicLayout.vue'
import { useUserStore } from '@/stores/user'
import type { Component } from 'vue'

/**
 * 静态路由：login / 404 / layout（仪表盘）。
 * 「系统管理」等页面路由是动态的：登录后按 /auth/me/menus 注册（见 stores/user.ts），
 * pageComponents 维护「菜单 path → 页面组件」的显式映射——新增页面时在此登记一行。
 */
export const pageComponents: Record<string, () => Promise<Component>> = {
  '/system/users': () => import('@/views/system/UserView.vue'),
  '/system/roles': () => import('@/views/system/RoleView.vue'),
  '/system/permissions': () => import('@/views/system/PermissionView.vue'),
  '/system/menus': () => import('@/views/system/MenuView.vue'),
  '/system/configs': () => import('@/views/system/ConfigView.vue'),
  '/merchant/merchants': () => import('@/views/merchant/MerchantListView.vue'),
  '/merchant/reviews': () => import('@/views/merchant/MerchantReviewView.vue'),
  '/payments/orders': () => import('@/views/payment/PaymentOrderListView.vue'),
  '/payments/refunds': () => import('@/views/refund/RefundListView.vue'),
  '/finance/accounts': () => import('@/views/finance/AccountListView.vue'),
  '/finance/ledgers': () => import('@/views/finance/LedgerRecordListView.vue'),
  '/finance/clearings': () => import('@/views/clearing/ClearingListView.vue'),
  '/finance/settlements': () => import('@/views/settlement/SettlementListView.vue'),
  '/finance/reconciliations': () => import('@/views/reconciliation/ReconciliationTaskListView.vue'),
  '/finance/recon-differences': () => import('@/views/reconciliation/DifferenceListView.vue'),
  '/risk/rules': () => import('@/views/risk/RuleListView.vue'),
  '/risk/orders': () => import('@/views/risk/RiskOrderListView.vue'),
  '/risk/blacklists': () => import('@/views/risk/BlacklistListView.vue'),
  '/risk/events': () => import('@/views/risk/RiskEventView.vue'),
  '/messages/webhooks': () => import('@/views/message/WebhookEventListView.vue'),
  '/messages/notifications': () => import('@/views/message/NotificationListView.vue'),
}

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: () => import('@/views/login/LoginView.vue') },
    {
      path: '/',
      name: 'layout',
      component: BasicLayout,
      redirect: '/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '仪表盘' },
        },
        // 对账结果页：任务列表钻入（非菜单页，静态注册；:id 为对账任务 ID）
        {
          path: '/finance/reconciliations/:id',
          name: 'reconciliation-result',
          component: () => import('@/views/reconciliation/ReconciliationResultView.vue'),
          meta: { title: '对账结果' },
        },
      ],
    },
    // 不带 redirect：未知路径在守卫里兜底（带 redirect 会在解析期先跳走，导致刷新动态路由页面时丢失原始目标）。
    // 守卫保证该路由不会被真正渲染，component 仅为满足路由记录类型。
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: defineComponent({ name: 'EmptyView', render: () => h('div') }),
    },
  ],
})

/** 路由守卫：未登录去登录页；已登录但资料未加载（刷新页面）先拉资料并注册动态路由。 */
router.beforeEach(async (to) => {
  const userStore = useUserStore()

  if (to.path === '/login') {
    return userStore.isLoggedIn ? { path: '/' } : true
  }
  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (!userStore.profileLoaded) {
    try {
      await userStore.loadProfile()
      // 动态路由已注册，用原始目标重新解析（此时 to 可能仍是未匹配的 not-found）
      return { path: to.path, query: to.query, hash: to.hash, replace: true }
    } catch {
      // 资料加载失败（token 失效等）：request 层已处理跳转，这里兜底清状态
      userStore.reset()
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
  // 动态路由就绪后仍未匹配的真实未知路径，兜底回仪表盘
  if (to.name === 'not-found') {
    return { path: '/dashboard' }
  }
  return true
})

export default router
