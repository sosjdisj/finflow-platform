/** el-table 时间列格式化：ISO(2026-10-17T12:51:05) → 2026-10-17 12:51:05，空值显示 —。 */
export const dateTimeFormatter = (_row: unknown, _column: unknown, value: string | null): string =>
  value ? value.replace('T', ' ').slice(0, 19) : '—'

/** 分 → 元字符串（铁律 1：金额库存分，仅展示层转元）。 */
export const fenToYuan = (fen: number | null | undefined): string =>
  fen == null ? '—' : (fen / 100).toFixed(2)

/** 带符号金额（分→元）：入账 +、出账 -，用于流水金额列。 */
export const signedYuan = (fen: number | null | undefined): string =>
  fen == null ? '—' : (fen > 0 ? '+' : '') + (fen / 100).toFixed(2)
