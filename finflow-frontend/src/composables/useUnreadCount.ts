import { ref } from 'vue'
import { getUnreadCount } from '@/api/notification'

/**
 * 未读站内信数（模块级单例状态）：顶栏铃铛与站内信页共享同一份。
 * 标记已读后调用 refresh() 同步角标；轮询由 BasicLayout 挂载期驱动。
 */
const unreadCount = ref(0)

export function useUnreadCount() {
  async function refresh(): Promise<void> {
    unreadCount.value = await getUnreadCount()
  }
  return { unreadCount, refresh }
}
