import http from './http'
import type { DictItem, Result } from '@/types/api'

export function listCategories() {
  return http.get<Result<DictItem[]>>('/categories')
}

export function listDepts() {
  return http.get<Result<DictItem[]>>('/depts')
}

export function listLocations() {
  return http.get<Result<DictItem[]>>('/locations')
}
