export function isDueSoon(status?: string, expectedReturnDate?: string | null, overdue?: boolean) {
  if (overdue || status !== 'BORROWED' || !expectedReturnDate) return false
  const due = parseLocalDate(expectedReturnDate)
  if (!due) return false
  const today = startOfLocalDay(new Date())
  const until = new Date(today)
  until.setDate(until.getDate() + 7)
  return due.getTime() >= today.getTime() && due.getTime() <= until.getTime()
}

function parseLocalDate(value: string) {
  const match = /^(\d{4})-(\d{2})-(\d{2})/.exec(value)
  if (!match) return null
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return Number.isNaN(date.getTime()) ? null : date
}

function startOfLocalDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

export function assetTagType(status?: string, overdue?: boolean) {
  if (overdue) return 'danger'
  if (status === 'IN_STOCK') return 'success'
  if (status === 'PENDING') return 'primary'
  if (status === 'BORROWED') return 'warning'
  if (status === 'REPAIRING' || status === 'SCRAPPED') return 'info'
  return 'info'
}

export function assetTagClass(status?: string) {
  if (status === 'REPAIRING') return 'tag-repairing'
  if (status === 'SCRAPPED') return 'tag-scrapped'
  return ''
}

export function borrowTagType(status?: string, overdue?: boolean) {
  if (overdue) return 'danger'
  if (status === 'PENDING' || status === 'APPROVED' || status === 'RETURN_PENDING') return 'primary'
  if (status === 'BORROWING') return 'warning'
  if (status === 'RETURNED' || status === 'APPROVED') return 'success'
  if (status === 'REJECTED' || status === 'WITHDRAWN') return 'info'
  return 'info'
}

export const actionLabel: Record<string, string> = {
  SUBMIT: '提交申请',
  WITHDRAW: '撤回',
  APPROVE: '审批通过',
  REJECT: '驳回',
  ISSUE: '确认发放',
  REQUEST_RETURN: '申请归还',
  CONFIRM_RETURN: '归还确认',
  RENEW_APPLY: '申请续借',
  RENEW_APPROVE: '续借通过',
  RENEW_REJECT: '续借驳回',
  TRANSFER: '调拨',
  REPAIR_START: '送修',
  REPAIR_FINISH: '维修完成',
  SCRAP: '报废',
  STOCKTAKE: '盘点',
}
