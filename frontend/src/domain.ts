import type { JobLevel, JobStatus, Workplace } from './types'

export const levelLabels: Record<JobLevel, string> = {
  JUNIOR: 'Junior',
  MID: 'Mid-level',
  SENIOR: 'Senior',
  UNKNOWN: 'Level unspecified',
}
export const workplaceLabels: Record<Workplace, string> = {
  REMOTE: 'Remote',
  HYBRID: 'Hybrid',
  ONSITE: 'On-site',
  UNKNOWN: 'Work mode unspecified',
}
export const statusLabels: Record<JobStatus, string> = {
  NEW: 'New',
  SAVED: 'Saved',
  APPLIED: 'Applied',
  HIDDEN: 'Hidden',
}

export function listFromText(value: string): string[] {
  return [
    ...new Set(
      value
        .split(/[,\n]/)
        .map((part) => part.trim())
        .filter(Boolean),
    ),
  ]
}

export function safeExternalUrl(value: string): string | undefined {
  try {
    const url = new URL(value)
    return ['https:', 'http:'].includes(url.protocol) ? url.href : undefined
  } catch {
    return undefined
  }
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return 'Not available'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? 'Not available'
    : new Intl.DateTimeFormat(undefined, {
        month: 'short',
        day: 'numeric',
        year: 'numeric',
      }).format(date)
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) return 'Never refreshed'
  const date = new Date(value)
  return Number.isNaN(date.getTime())
    ? 'Not available'
    : new Intl.DateTimeFormat(undefined, {
        month: 'short',
        day: 'numeric',
        hour: 'numeric',
        minute: '2-digit',
      }).format(date)
}

export function initials(name: string): string {
  return (
    name
      .trim()
      .split(/\s+/)
      .map((part) => part[0])
      .slice(0, 2)
      .join('')
      .toUpperCase() || 'JT'
  )
}
