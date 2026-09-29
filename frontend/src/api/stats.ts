import http from './http'
import type { Result, StatsNameCount, StatsOverview, StatsTrend } from '@/types/api'

function quiet(silent: boolean) {
  return silent ? { skipErrorMessage: true } : undefined
}

export function getOverview(silent = false) {
  return http.get<Result<StatsOverview>>('/stats/overview', quiet(silent))
}

export function getByCategory(silent = false) {
  return http.get<Result<StatsNameCount[]>>('/stats/by-category', quiet(silent))
}

export function getBorrowTrend(silent = false) {
  return http.get<Result<StatsTrend[]>>('/stats/borrow-trend', quiet(silent))
}
