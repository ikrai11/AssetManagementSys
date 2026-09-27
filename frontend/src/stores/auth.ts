import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import type { UserRole } from '@/types/role'
import { changePassword as changePasswordApi, getMe, login as loginApi, logout as logoutApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') ?? '')
  const role = ref<UserRole | ''>((localStorage.getItem('role') as UserRole) || '')
  const realName = ref(localStorage.getItem('realName') ?? '')
  const mustChangePassword = ref(localStorage.getItem('mustChangePassword') === '1')

  const loggedIn = computed(() => token.value.length > 0 && token.value !== 'preview')

  function persist() {
    localStorage.setItem('token', token.value)
    localStorage.setItem('role', role.value)
    localStorage.setItem('realName', realName.value)
    localStorage.setItem('mustChangePassword', mustChangePassword.value ? '1' : '0')
  }

  async function login(username: string, password: string) {
    const { data } = await loginApi(username, password)
    token.value = data.data.token
    role.value = data.data.role
    realName.value = data.data.realName
    mustChangePassword.value = data.data.mustChangePassword
    persist()
  }

  async function fetchMe() {
    const { data } = await getMe()
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
    role.value = ''
    realName.value = ''
    mustChangePassword.value = false
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    localStorage.removeItem('realName')
    localStorage.removeItem('mustChangePassword')
  }

  return {
    token,
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
