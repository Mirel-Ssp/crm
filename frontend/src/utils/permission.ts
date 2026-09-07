import type { Directive } from 'vue'
import { useUserStore } from '@/stores/user'

/**
 * v-permission 按钮级权限指令（SYS-DV-02）
 * 用法：v-permission="'lead:claim'" 或 v-permission="['lead:claim','lead:assign']"（任一满足）
 * 无权限时移除元素
 */
export const permission: Directive<HTMLElement, string | string[]> = {
  mounted(el, binding) {
    const store = useUserStore()
    const need = Array.isArray(binding.value) ? binding.value : [binding.value]
    if (need.length > 0 && !store.hasAnyPerm(need)) {
      el.parentNode?.removeChild(el)
    }
  },
}
