export interface SearchPreferences {
  search: string
  level: string
  workplace: string
  status: string
  sourceId: string
  sort: string
  engineeringOnly: boolean
}

export function validatePreferences(input: unknown, tracking: boolean): SearchPreferences {
  const value = input && typeof input === 'object' ? (input as Record<string, unknown>) : {}
  const choose = (key: string, values: string[], fallback: string) =>
    typeof value[key] === 'string' && values.includes(value[key])
      ? (value[key] as string)
      : fallback
  return {
    search: typeof value.search === 'string' ? value.search.slice(0, 200) : '',
    level: choose('level', ['', 'JUNIOR', 'MID', 'SENIOR', 'UNKNOWN'], ''),
    workplace: choose('workplace', ['', 'REMOTE', 'HYBRID', 'ONSITE', 'UNKNOWN'], ''),
    status: choose(
      'status',
      tracking ? ['SAVED', 'APPLIED', 'HIDDEN'] : ['', 'NEW', 'SAVED', 'APPLIED', 'HIDDEN'],
      tracking ? 'SAVED' : '',
    ),
    sourceId:
      typeof value.sourceId === 'string' && /^\d*$/.test(value.sourceId) ? value.sourceId : '',
    sort: choose('sort', ['priority', 'newest'], 'priority'),
    engineeringOnly: typeof value.engineeringOnly === 'boolean' ? value.engineeringOnly : !tracking,
  }
}

export function readPreferences(tracking: boolean): SearchPreferences {
  try {
    return validatePreferences(
      JSON.parse(
        localStorage.getItem(tracking ? 'jobtrail.applications' : 'jobtrail.search') || 'null',
      ),
      tracking,
    )
  } catch {
    return validatePreferences(null, tracking)
  }
}

export function savePreferences(value: SearchPreferences, tracking: boolean): void {
  try {
    localStorage.setItem(
      tracking ? 'jobtrail.applications' : 'jobtrail.search',
      JSON.stringify(value),
    )
  } catch {
    /* The search remains usable when browser storage is unavailable. */
  }
}
