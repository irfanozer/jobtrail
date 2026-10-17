import { describe, expect, it } from 'vitest'
import { validatePreferences } from './preferences'

describe('saved search preferences', () => {
  it('preserves valid filters without storing any career profile data', () => {
    expect(
      validatePreferences(
        {
          search: 'Java',
          level: 'MID',
          workplace: 'REMOTE',
          status: 'SAVED',
          sourceId: '42',
          sort: 'newest',
          engineeringOnly: true,
          profile: { skills: ['private'] },
        },
        false,
      ),
    ).toEqual({
      search: 'Java',
      level: 'MID',
      workplace: 'REMOTE',
      status: 'SAVED',
      sourceId: '42',
      sort: 'newest',
      engineeringOnly: true,
    })
  })
  it('recovers from stale or invalid settings with safe defaults', () => {
    expect(
      validatePreferences(
        { level: 'EXPERT', status: '', sourceId: '../source', sort: 'unexpected' },
        true,
      ),
    ).toMatchObject({
      level: '',
      status: 'SAVED',
      sourceId: '',
      sort: 'priority',
      engineeringOnly: false,
    })
    expect(validatePreferences(null, false).engineeringOnly).toBe(true)
  })
})
