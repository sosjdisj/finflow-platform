<script setup lang="ts">
import { computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import MenuItem from '@/layouts/MenuItem.vue'
import { useUserStore } from '@/stores/user'
import { useUnreadCount } from '@/composables/useUnreadCount'

/**
 * 主布局：侧边栏 = 静态仪表盘 + 登录用户可见菜单树（/auth/me/menus），
 * 顶栏右侧 = 站内信铃铛（未读角标，60s 轮询）+ 用户下拉（退出登录）。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { unreadCount, refresh: refreshUnread } = useUnreadCount()

const activeMenu = computed(() => route.path)
const displayName = computed(() => userStore.nickname || userStore.profile?.username || '')
/** 无通知查看权限的用户不拉未读数、不显示铃铛。 */
const showBell = computed(() => userStore.hasPerm('notification:list'))

let bellTimer: number | null = null

onMounted(() => {
  if (!showBell.value) return
  void refreshUnread()
  bellTimer = window.setInterval(() => void refreshUnread(), 60_000)
})
onUnmounted(() => {
  if (bellTimer != null) window.clearInterval(bellTimer)
})

function openNotifications(): void {
  void router.push('/messages/notifications')
}

async function onLogout(): Promise<void> {
  await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
  await userStore.logout()
}
</script>

<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="brand">FinFlow 管理平台</div>
      <el-menu :default-active="activeMenu" router class="menu">
        <el-menu-item index="/dashboard">仪表盘</el-menu-item>
        <MenuItem v-for="node in userStore.menuTree" :key="node.id" :node="node" />
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <span class="title">{{ route.meta.title || '' }}</span>
        <div class="header-right">
          <el-badge
            v-if="showBell"
            :value="unreadCount"
            :hidden="unreadCount === 0"
            :max="99"
            class="bell"
          >
            <span class="bell-entry" @click="openNotifications">消息</span>
          </el-badge>
          <el-dropdown @command="(cmd: string) => cmd === 'logout' && onLogout()">
            <span class="user">{{ displayName }}</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100vh;
}

.aside {
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
}

.brand {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  color: var(--el-color-primary);
}

.menu {
  border-right: none;
  flex: 1;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid var(--el-border-color-light);
}

.title {
  font-size: 16px;
  font-weight: 500;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.bell-entry {
  cursor: pointer;
  color: var(--el-text-color-primary);
  font-size: 14px;
}

.user {
  cursor: pointer;
  color: var(--el-text-color-primary);
}

.main {
  background: var(--el-fill-color-lighter);
}
</style>
