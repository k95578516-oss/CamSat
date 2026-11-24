import { describe, expect, it } from 'vitest'
import { csvCell, toCsv } from './csv'

describe('csv', () => {
  it('quotes cells containing commas, quotes and newlines', () => {
    expect(csvCell('a,b')).toBe('"a,b"')
    expect(csvCell('say "hi"')).toBe('"say ""hi"""')
    expect(csvCell('line1\nline2')).toBe('"line1\nline2"')
  })
  it('neutralises spreadsheet formulas in text', () => {
    expect(csvCell('=SUM(A1:A2)')).toBe("'=SUM(A1:A2)")
    expect(csvCell('@cmd')).toBe("'@cmd")
  })
  it('writes missing and non-finite numbers as empty cells, not zero', () => {
    expect(csvCell(null)).toBe('')
    expect(csvCell(undefined)).toBe('')
    expect(csvCell(NaN)).toBe('')
    expect(csvCell(-12.5)).toBe('-12.5')
  })
  it('builds a CRLF separated document with a header', () => {
    expect(toCsv(['a', 'b'], [[1, null]])).toBe('a,b\r\n1,\r\n')
  })
})
