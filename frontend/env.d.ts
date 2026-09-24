/// <reference types="vite/client" />

import type { UserRole } from './src/types/role'

export {}

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    role?: UserRole
    title?: string
  }
}
