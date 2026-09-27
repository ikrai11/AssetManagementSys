import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/types/role'
import { changePassword as changePasswordApi, getMe, login as loginApi, logout as logoutApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') ?? '')
  const userId = ref(Number(localStorage.getItem('userId') || 0))
  const role = ref<UserRole | ''>((localStorage.getItem('role') as UserRole) || '')
  const realName = ref(localStorage.getItem('realName') ?? '')
  const mustChangePassword = ref(localStorage.getItem('mustChangePassword') === '1')

  const loggedIn = computed(() => token.value.length > 0 && token.value !== 'preview')

  function persist() {
    localStorage.setItem('token', token.value)
    localStorage.setItem('userId', userId.value ? String(userId.value) : '')
    localStorage.setItem('role', role.value)
    localStorage.setItem('realName', realName.value)
    localStorage.setItem('mustChangePassword', mustChangePassword.value ? '1' : '0')
  }

  async function login(username: string, password: string) {
    const { data } = await loginApi(username, password)
    token.value = data.data.token
    userId.value = data.data.userId
    role.value = data.data.role
    realName.value = data.data.realName
    mustChangePassword.value = data.data.mustChangePassword
    persist()
  }

  async function fetchMe() {
    const { data } = await getMe()
    userId.value = data.data.id
    role.value = data.data.role
    realName.value = data.data.realName
    mustChangePassword.value = data.data.mustChangePassword
    persist()
  }

  async function updatePassword(oldPassword: string, newPassword: string) {
    await changePasswordApi(oldPassword, newPassword)
    mustChangePassword.value = false
    persist()
  }

  async function logout() {
    try {
      if (loggedIn.value) {
        await logoutApi()
      }
    } finally {
      clear()
    }
  }

  function clear() {
    token.value = ''
    userId.value = 0
    role.value = ''
    realName.value = ''
    mustChangePassword.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('role')
    localStorage.removeItem('realName')
    localStorage.removeItem('mustChangePassword')
  }

  return {
    token,
    userId,
    role,
    realName,
    mustChangePassword,
    loggedIn,
    login,
    fetchMe,
    updatePassword,
    logout,
    clear,
  }
})
