import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { authApi } from '@/api/auth'
import { clearToken, getRefreshToken, getToken, setTokens } from '@/utils/auth'
import router, { pageComponents } from '@/router'
import type { MenuNode, UserProfile } from '@/types/api'

/**
 * 登录会话状态：profile / 角色 / 权限编码 / 可见菜单树。
 * 登录后经 loadProfile 拉取一次并注册动态路由；刷新页面由路由守卫触发重建。
 */
export const useUserStore = defineStore('user', () => {
  const profile = ref<UserProfile | null>(null)
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const menuTree = ref<MenuNode[]>([])
  /** 是否已完成首次资料加载（含动态路由注册）。 */
  const profileLoaded = ref(false)

  /** token 的响应式镜像：localStorage 非响应式源，computed 直接读会永久缓存首次结果。 */
  const token = ref<string | null>(getToken())
  const nickname = computed(() => profile.value?.nickname ?? profile.value?.username ?? '')

  /** 页面菜单注册为 BasicLayout 下的动态路由（name 用于去重）。 */
  function registerDynamicRoutes(menus: MenuNode[]): void {
    for (const node of menus) {
      if (node.menuType === 2 && node.path && node.visible === 1) {
        const name = `page-${node.id}`
        const component = pageComponents[node.path]
        if (component && !router.hasRoute(name)) {
          router.addRoute('layout', { path: node.path, name, component, meta: { title: node.title } })
        }
      }
      if (node.children?.length) {
        registerDynamicRoutes(node.children)
      }
    }
  }

  /** 拉取当前用户资料 + 权限 + 菜单，并注册动态路由。失败抛出让守卫回登录页。 */
  async function loadProfile(): Promise<void> {
    const [me, perms, menus] = await Promise.all([authApi.me(), authApi.myPerms(), authApi.myMenus()])
    profile.value = me
    roles.value = [...perms.roles]
    permissions.value = [...perms.permissions]
    menuTree.value = menus
    registerDynamicRoutes(menus)
    profileLoaded.value = true
  }

  /** 清空本地会话状态（不调接口）。 */
  function reset(): void {
    profile.value = null
    roles.value = []
    permissions.value = []
    menuTree.value = []
    profileLoaded.value = false
    clearToken()
    token.value = null
  }

  /** 登录成功：写入双 token 并立即加载资料。 */
  async function loginAfterSuccess(accessToken: string, refreshToken: string): Promise<void> {
    setTokens(accessToken, refreshToken)
    token.value = accessToken
    await loadProfile()
  }

  /** 退出登录：尽力通知后端吊销 refresh token，无论成败都清本地状态。 */
  async function logout(): Promise<void> {
    const refreshToken = getRefreshToken()
    try {
      if (refreshToken) {
        await authApi.logout(refreshToken)
      }
    } finally {
      reset()
      // replace：登录页替换工作台的历史记录，后退不会回到已退出的工作台
      await router.replace('/login')
    }
  }

  /** 按钮级权限判断（配合 v-permission / hasPerm 使用）。 */
  function hasPerm(code: string): boolean {
    return permissions.value.includes(code)
  }

  return {
    profile,
    roles,
    permissions,
    menuTree,
    profileLoaded,
    isLoggedIn: computed(() => !!token.value),
    nickname,
    loadProfile,
    reset,
    loginAfterSuccess,
    logout,
    hasPerm,
  }
})
