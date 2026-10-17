export type JobStatus = 'NEW' | 'SAVED' | 'APPLIED' | 'HIDDEN'
export type JobLevel = 'JUNIOR' | 'MID' | 'SENIOR' | 'UNKNOWN'
export type Workplace = 'REMOTE' | 'HYBRID' | 'ONSITE' | 'UNKNOWN'
export type Provider = 'GREENHOUSE' | 'LEVER'

export interface Job {
  id: number
  sourceId: number
  sourceName: string
  company: string
  title: string
  location: string
  workplace: Workplace
  level: JobLevel
  status: JobStatus
  url: string
  description: string
  publishedAt: string | null
  firstSeenAt: string
  lastSeenAt: string
  active: boolean
  priorityScore: number
}

export interface JobPage {
  items: Job[]
  totalElements: number
  totalPages: number
  page: number
  size: number
}

export interface Source {
  id: number
  name: string
  provider: Provider
  boardKey: string
  enabled: boolean
  lastSuccessAt: string | null
  lastAttemptAt: string | null
  lastError: string | null
  jobCount: number
}

export interface RefreshRun {
  id: string | number
  status: 'RUNNING' | 'COMPLETED' | 'PARTIAL' | 'FAILED'
  startedAt: string
  finishedAt: string | null
  sources: Array<{
    sourceId: number
    name: string
    status: 'SUCCESS' | 'FAILED'
    jobCount: number
    error: string | null
  }>
}

export interface CareerFact {
  id: string
  statement: string
  source: string
  confirmed: boolean
}
export interface Profile {
  displayName: string
  preferredLocations: string[]
  skills: string[]
  targetRoles: string[]
  careerFacts: CareerFact[]
  version: number
  updatedAt: string | null
}

export interface MatchEvidence {
  source: 'PROFILE_SKILL' | 'CAREER_FACT' | 'PROFILE_PREFERENCE' | 'JOB_TEXT'
  reference: string
  text: string
}
export interface MatchFinding {
  type: string
  message: string
  evidence: MatchEvidence[]
}
export interface Match {
  jobId: number
  profileVersion: number
  method: string
  label: string
  strengths: MatchFinding[]
  gaps: MatchFinding[]
  unknowns: MatchFinding[]
}
