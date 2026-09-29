import { ref } from 'vue'
import { defineStore } from 'pinia'
import { unreadCount } from '@/api/message'
import { useAuthStore } from '@/stores/auth'

const POLL_MS = 10_000

export const useMessageStore = defineStore('message', () => {
  const unread = ref(0)
  let timer: number | undefined
  let inflight = false
  let queued = false

  async function refresh() {
    const auth = useAuthStore()
    if (!auth.loggedIn) {
      unread.value = 0
      return
    }
    if (inflight) {
      queued = true
      return
    }
    inflight = true
    try {
      do {
        queued = false
        const { data } = await unreadCount(true)
        unread.value = data.data ?? 0
      } while (queued && useAuthStore().loggedIn)
    } catch {
      unread.value = useAuthStore().loggedIn ? unread.value : 0
    } finally {
      inflight = false
    }
  }

  function onVisible() {
    if (document.visibilityState === 'visible') {
      void refresh()
    }
  }

  function startPolling() {
    stopPolling()
    document.addEventListener('visibilitychange', onVisible)
    timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') {
        void refresh()
      }
    }, POLL_MS)
  }

  function stopPolling() {
    if (timer !== undefined) {
      window.clearInterval(timer)
      timer = undefined
    }
    document.removeEventListener('visibilitychange', onVisible)
  }

  return { unread, refresh, startPolling, stopPolling }
})
