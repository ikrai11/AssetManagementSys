import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/types/role'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') ?? '')
  const role = ref<UserRole | ''>((localStorage.getItem('role') as UserRole) || '')
  const realName = ref(localStorage.getItem('realName') ?? '')

  const loggedIn = computed(() => token.value.length > 0)

  function enterPreview(nextRole: UserRole) {
    token.value = 'preview'
    role.value = nextRole
    realName.value = nextRole === 'ADMIN' ? '管理员预览' : '普通用户预览'
    localStorage.setItem('token', token.value)
    localStorage.setItem('role', nextRole)
    localStorage.setItem('realName', realName.value)
  }

  function clear() {
    token.value = ''
    role.value = ''
    realName.value = ''
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    localStorage.removeItem('realName')
  }

  return { token, role, realName, loggedIn, enterPreview, clear }
})
