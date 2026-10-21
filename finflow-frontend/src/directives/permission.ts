import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * 按钮级权限：
 * - hasPerm(code)：脚本里编程式判断；
 * - v-permission="'system:user:create'"：无该权限码的元素直接移除（不存在"禁用置灰"歧义）。
 */
export function hasPerm(code: string): boolean {
  return useUserStore().hasPerm(code)
}

export const vPermission: Directive<HTMLElement, string> = {
  mounted(el, binding) {
    if (!hasPerm(binding.value)) {
      el.remove()
    }
  },
}
