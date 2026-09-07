import { ref } from 'vue'
import { listDicts, type DictRow } from '@/api/system'

/**
 * 数据字典组合式函数（SYS-DV-05/T14）
 * - 按 dictType 动态加载下拉项（{ value: code, label: value }）
 * - 模块级缓存 + 并发去重：同一字典类型全应用只请求一次
 * - 未命中返回原值，保证旧数据（字典被删）展示不中断
 */
export interface DictOption {
  value: string
  label: string
}

const cache = new Map<string, DictOption[]>()
const inflight = new Map<string, Promise<DictOption[]>>()

export function useDict(dictType: string) {
  const options = ref<DictOption[]>(cache.get(dictType) ?? [])

  async function load(): Promise<void> {
    const hit = cache.get(dictType)
    if (hit) {
      options.value = hit
      return
    }
    let p = inflight.get(dictType)
    if (!p) {
      p = listDicts(dictType).then((rows: DictRow[]) => {
        const opts: DictOption[] = rows.map((r) => ({ value: r.code, label: r.value }))
        cache.set(dictType, opts)
        return opts
      }).finally(() => inflight.delete(dictType))
      inflight.set(dictType, p)
    }
    options.value = await p
  }

  /** 字典项 label（未命中回退原值） */
  function labelOfDict(value: string | null | undefined): string {
    return options.value.find((x) => x.value === value)?.label ?? value ?? '-'
  }

  void load()
  return { options, reload: load, labelOfDict }
}
