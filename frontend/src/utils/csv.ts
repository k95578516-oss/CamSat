export type CsvCell = string | number | boolean | null | undefined

/**
 * Escapes a cell for CSV. String cells that start with a formula trigger are prefixed with
 * an apostrophe so spreadsheet software does not execute them (CSV injection).
 * Numbers are written as-is; missing values become empty cells (not 0).
 */
export function csvCell(value: CsvCell): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'number') return Number.isFinite(value) ? String(value) : ''
  let s = String(value)
  if (typeof value === 'string' && /^[=+\-@\t\r]/.test(s)) s = `'${s}`
  return /[",\r\n]/.test(s) ? `"${s.replace(/"/g, '""')}"` : s
}

export function toCsv(headers: string[], rows: CsvCell[][]): string {
  return [headers, ...rows].map((r) => r.map(csvCell).join(',')).join('\r\n') + '\r\n'
}

export function downloadCsv(filename: string, csv: string): void {
  const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(url)
}
