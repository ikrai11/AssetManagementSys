import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const message = error.response?.data?.message ?? '请求失败'
    if (status === 401) {
      useAuthStore().clear()
      router.push({ name: 'login' })
    } else if (status === 400 || status === 409) {
      ElMessage.error(message)
    }
    return Promise.reject(error)
  },
)

export default http
