import type { DictItem } from '@/types/api'

export function withCurrentOption(options: DictItem[], id?: number, name?: string) {
  if (id == null || !name || options.some((item) => item.id === id)) {
    return options
  }
  return [...options, { id, name: `${name}（已停用）` }]
}
