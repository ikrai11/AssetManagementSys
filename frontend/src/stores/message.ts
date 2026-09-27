import { ref } from 'vue'
import { defineStore } from 'pinia'
import { unreadCount } from '@/api/message'
import { useAuthStore } from '@/stores/auth'

export const useMessageStore = defineStore('message', () => {
  const unread = ref(0)

  async function refresh() {
    const auth = useAuthStore()
    if (!auth.loggedIn) {
      unread.value = 0
      return
    }
    const { data } = await unreadCount()
    unread.value = data.data ?? 0
  }

  return { unread, refresh }
})
