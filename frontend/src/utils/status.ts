export function assetTagType(status?: string, overdue?: boolean) {
  if (overdue) return 'danger'
  if (status === 'IN_STOCK') return 'success'
  if (status === 'PENDING') return 'primary'
  if (status === 'BORROWED') return 'warning'
  return 'info'
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
}
