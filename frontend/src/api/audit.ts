import axios from 'axios'
import http from './http'
import type { AuditLog, AuditQuery, PageResult, Result } from '@/types/api'

function filenameFrom(disposition: string | undefined, fallback: string) {
  if (!disposition) return fallback
  const encodedName = /filename\*=(?:UTF-8'')?([^;]+)/i.exec(disposition)?.[1]
  if (encodedName) {
    return decodeURIComponent(encodedName.replace(/"/g, ''))
  }
  const plainName = /filename="?([^"]+)"?/i.exec(disposition)?.[1]
  return plainName ? decodeURIComponent(plainName) : fallback
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}

export function listAuditLogs(params: AuditQuery) {
  return http.get<Result<PageResult<AuditLog>>>('/audit-logs', { params })
}

export async function exportAuditLogs(params: AuditQuery) {
  try {
    const response = await http.get<Blob>('/audit-logs/export', {
      params,
      responseType: 'blob',
      timeout: 60000,
      skipErrorMessage: true,
    })
    const blob = response.data
    if (blob.type.includes('application/json')) {
      const body = JSON.parse(await blob.text()) as Result<null>
      throw new Error(body.message || '下载失败')
    }
    saveBlob(blob, filenameFrom(response.headers['content-disposition'], '审计日志.xlsx'))
  } catch (error) {
    if (error instanceof Error && !axios.isAxiosError(error)) {
      throw error
    }
    if (axios.isAxiosError(error) && error.response?.data instanceof Blob) {
      const body = JSON.parse(await error.response.data.text()) as Result<null>
      throw new Error(body.message || '下载失败')
    }
    throw error
  }
}
