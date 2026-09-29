import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { Result } from '@/types/api'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipErrorMessage?: boolean
  }
}

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token && auth.token !== 'preview') {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    if (response.config.responseType === 'blob' || response.data instanceof Blob) {
      return response
    }
    const body = response.data as Result<unknown>
    if (body && typeof body.code === 'number' && body.code !== 0) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message))
    }
    return response
  },
  async (error) => {
    const status = error.response?.status
    if (status === 401) {
      useAuthStore().clear()
      if (router.currentRoute.value.name !== 'login') {
        router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      }
      return Promise.reject(error)
    }
    let message = '请求失败'
    const payload = error.response?.data
    if (error.config?.skipErrorMessage) {
      return Promise.reject(error)
    }
    if (payload instanceof Blob) {
      try {
        const body = JSON.parse(await payload.text()) as Result<unknown>
        message = body.message || message
      } catch {
        message = '请求失败'
      }
    } else if (payload && typeof payload === 'object' && 'message' in payload) {
      message = String((payload as Result<unknown>).message || message)
    }
    if (status === 403) {
      router.push({ name: 'forbidden' })
    } else if (status === 400 || status === 409) {
      ElMessage.error(message)
    } else if (status === 404) {
      ElMessage.error(message || '资源不存在')
    } else {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export default http
