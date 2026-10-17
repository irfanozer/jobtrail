import { describe, expect, it } from 'vitest'
import { listFromText, safeExternalUrl } from './domain'
import { readCookie } from './api'

describe('profile input', () => {
  it('splits pasted lists, trims values, and removes duplicate claims', () => {
    expect(listFromText('Java, PostgreSQL\n Java ,\n')).toEqual(['Java', 'PostgreSQL'])
  })
})

describe('external job links', () => {
  it('accepts web URLs and rejects active content or malformed links', () => {
    expect(safeExternalUrl('https://jobs.example.org/role/1')).toBe(
      'https://jobs.example.org/role/1',
    )
    expect(safeExternalUrl('javascript:alert(1)')).toBeUndefined()
    expect(safeExternalUrl('data:text/html,hello')).toBeUndefined()
    expect(safeExternalUrl('not a url')).toBeUndefined()
  })
})

describe('CSRF cookie handling', () => {
  it('reads only the intended cookie and decodes its value', () => {
    expect(
      readCookie('XSRF-TOKEN', 'OTHER-XSRF-TOKEN=wrong; XSRF-TOKEN=token%2Fvalue; theme=light'),
    ).toBe('token/value')
    expect(readCookie('XSRF-TOKEN', 'OTHER-XSRF-TOKEN=wrong')).toBeUndefined()
    expect(readCookie('XSRF-TOKEN', 'XSRF-TOKEN=%invalid')).toBeUndefined()
  })
})
