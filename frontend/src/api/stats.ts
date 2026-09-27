import http from './http'
import type { Result, StatsNameCount, StatsOverview, StatsTrend } from '@/types/api'

export function getOverview() {
  return http.get<Result<StatsOverview>>('/stats/overview')
}

export function getByCategory() {
  return http.get<Result<StatsNameCount[]>>('/stats/by-category')
}

export function getBorrowTrend() {
  return http.get<Result<StatsTrend[]>>('/stats/borrow-trend')
}
