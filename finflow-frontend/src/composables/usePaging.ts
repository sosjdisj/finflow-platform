import { reactive, ref } from 'vue'
import type { PageResponse } from '@/types/api'

/**
 * 分页列表通用状态：后端翻页接口（page/size → PageResponse）的标准接入。
 * 返回 reactive 包装：模板与脚本直接读写属性，无需 .value。
 * 用法：const paging = usePaging((p, s) => userApi.page(p, s, keyword.value))；首次 onMounted(paging.load)。
 */
export function usePaging<T>(fetcher: (page: number, size: number) => Promise<PageResponse<T>>, defaultSize = 10) {
  const page = ref(1)
  const size = ref(defaultSize)
  const total = ref(0)
  const records = ref<T[]>([])
  const loading = ref(false)

  async function load(): Promise<void> {
    loading.value = true
    try {
      const resp = await fetcher(page.value, size.value)
      records.value = resp.records
      total.value = resp.total
    } finally {
      loading.value = false
    }
  }

  /** 回到第一页再查（搜索、增删改后用）。 */
  async function reload(): Promise<void> {
    page.value = 1
    await load()
  }

  return reactive({ page, size, total, records, loading, load, reload })
}
