import { afterEach, describe, expect, it, vi } from 'vitest'
import { readPreferences, savePreferences, validatePreferences } from './preferences'

afterEach(() => vi.unstubAllGlobals())

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
  it('keeps demo filters separate from the owner browser preferences', () => {
    const owner = validatePreferences({ search: 'Owner search', sourceId: '42' }, false)
    const getItem = vi.fn(() => JSON.stringify(owner))
    const setItem = vi.fn()
    vi.stubGlobal('localStorage', { getItem, setItem })

    expect(readPreferences(false, false)).toEqual(validatePreferences(null, false))
    savePreferences(validatePreferences({ search: 'Demo search' }, false), false, false)
    expect(getItem).not.toHaveBeenCalled()
    expect(setItem).not.toHaveBeenCalled()

    expect(readPreferences(false)).toEqual(owner)
    savePreferences(owner, false)
    expect(setItem).toHaveBeenCalledWith('jobtrail.search', JSON.stringify(owner))
  })
})
