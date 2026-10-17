import { useCallback, useEffect, useRef, useState } from 'react'
import {
  ArrowDownWideNarrow,
  ArrowRight,
  ArrowUpRight,
  Bookmark,
  Check,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  Clock3,
  ExternalLink,
  EyeOff,
  FileSearch,
  Filter,
  Layers3,
  MapPin,
  RotateCcw,
  Search,
  SlidersHorizontal,
  Sparkles,
  X,
} from 'lucide-react'
import { api } from './api'
import { EmptyState, ErrorNotice, Loading, SkeletonCards } from './components'
import {
  formatDate,
  formatDateTime,
  initials,
  levelLabels,
  safeExternalUrl,
  statusLabels,
  workplaceLabels,
} from './domain'
import { readPreferences, savePreferences } from './preferences'
import type {
  Job,
  JobLevel,
  JobPage,
  JobStatus,
  Match,
  MatchFinding,
  Source,
  Workplace,
} from './types'

interface JobsProps {
  sources: Source[]
  revision: number
  tracking: boolean
  demo?: boolean
  onManageSources: () => void
  onEditProfile: () => void
}

export function Jobs({
  sources,
  revision,
  tracking,
  demo = false,
  onManageSources,
  onEditProfile,
}: JobsProps) {
  const [initial] = useState(() => readPreferences(tracking, !demo))
  const [search, setSearch] = useState(initial.search)
  const [query, setQuery] = useState(initial.search)
  const [level, setLevel] = useState(initial.level)
  const [workplace, setWorkplace] = useState(initial.workplace)
  const [status, setStatus] = useState(initial.status)
  const [sourceId, setSourceId] = useState(initial.sourceId)
  const [sort, setSort] = useState(initial.sort)
  const [engineeringOnly, setEngineeringOnly] = useState(initial.engineeringOnly)
  const [page, setPage] = useState(0)
  const [data, setData] = useState<JobPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<unknown>(null)
  const [actionError, setActionError] = useState<unknown>(null)
  const [reload, setReload] = useState(0)
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [savingIds, setSavingIds] = useState<number[]>([])
  const [announcement, setAnnouncement] = useState('')

  useEffect(() => {
    savePreferences(
      { search, level, workplace, status, sourceId, sort, engineeringOnly },
      tracking,
      !demo,
    )
  }, [search, level, workplace, status, sourceId, sort, engineeringOnly, tracking, demo])

  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(timer)
  }, [search])
  useEffect(() => {
    const controller = new AbortController()
    const params = new URLSearchParams({
      page: String(page),
      size: '20',
      sort,
      engineeringOnly: String(engineeringOnly),
      includeInactive: String(tracking),
    })
    Object.entries({ q: query, level, workplace, status, sourceId }).forEach(([key, value]) => {
      if (value) params.set(key, value)
    })
    setLoading(true)
    setError(null)
    api<JobPage>(`/api/jobs?${params}`, { signal: controller.signal })
      .then((result) => {
        if (!controller.signal.aborted) {
          if (page > 0 && result.items.length === 0) setPage(Math.max(0, result.totalPages - 1))
          else setData(result)
        }
      })
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause)
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false)
      })
    return () => controller.abort()
  }, [query, level, workplace, status, sourceId, page, sort, engineeringOnly, revision, reload])

  const changeStatus = useCallback(async (job: Job, next: JobStatus) => {
    setActionError(null)
    setSavingIds((previous) => [...previous, job.id])
    try {
      await api<Job>(`/api/jobs/${job.id}/status`, {
        method: 'PATCH',
        body: JSON.stringify({ status: next }),
      })
      setData((previous) =>
        previous
          ? {
              ...previous,
              items: previous.items.map((item) =>
                item.id === job.id ? { ...item, status: next } : item,
              ),
            }
          : null,
      )
      setAnnouncement(`${job.title} marked ${statusLabels[next].toLowerCase()}.`)
      setReload((value) => value + 1)
      return true
    } catch (cause) {
      setActionError(cause)
      return false
    } finally {
      setSavingIds((previous) => previous.filter((id) => id !== job.id))
    }
  }, [])

  const clearFilters = () => {
    setSearch('')
    setQuery('')
    setLevel('')
    setWorkplace('')
    setSourceId('')
    setStatus(tracking ? 'SAVED' : '')
    setEngineeringOnly(!tracking)
    setPage(0)
  }
  const hasFilters = !!(
    search ||
    level ||
    workplace ||
    sourceId ||
    (!tracking && status) ||
    engineeringOnly !== !tracking
  )
  const setFilter = (setter: (value: string) => void) => (value: string) => {
    setter(value)
    setPage(0)
  }

  return (
    <section className="job-workspace" aria-label={tracking ? 'Application tracker' : 'Job search'}>
      <div className="sr-only" role="status">
        {announcement}
      </div>
      {tracking && (
        <div className="tracker-tabs" aria-label="Application status">
          <button
            className={status === 'SAVED' ? 'selected' : ''}
            onClick={() => {
              setStatus('SAVED')
              setPage(0)
            }}
          >
            <Bookmark size={16} />
            Saved roles
          </button>
          <button
            className={status === 'APPLIED' ? 'selected' : ''}
            onClick={() => {
              setStatus('APPLIED')
              setPage(0)
            }}
          >
            <Check size={17} />
            Applied
          </button>
          <button
            className={status === 'HIDDEN' ? 'selected' : ''}
            onClick={() => {
              setStatus('HIDDEN')
              setPage(0)
            }}
          >
            <EyeOff size={16} />
            Hidden
          </button>
          <p>Statuses are updated by you.</p>
        </div>
      )}
      <div className="search-panel">
        <div className="search-input-wrap">
          <Search size={20} aria-hidden="true" />
          <label className="sr-only" htmlFor="job-query">
            Search jobs by title, company, or keyword
          </label>
          <input
            id="job-query"
            type="search"
            maxLength={200}
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Job title, company, or keyword…"
            autoComplete="off"
          />
          <span className="search-hint">Search your boards</span>
        </div>
        <div className="filter-row">
          <div className="filter-label">
            <SlidersHorizontal size={15} />
            <span>Filters</span>
          </div>
          <label className="filter-select">
            <span className="sr-only">Experience level</span>
            <select value={level} onChange={(event) => setFilter(setLevel)(event.target.value)}>
              <option value="">All experience levels</option>
              {Object.entries(levelLabels).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-select">
            <span className="sr-only">Workplace</span>
            <select
              value={workplace}
              onChange={(event) => setFilter(setWorkplace)(event.target.value)}
            >
              <option value="">All work arrangements</option>
              {Object.entries(workplaceLabels).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="filter-select">
            <span className="sr-only">Employer board</span>
            <select
              value={sourceId}
              onChange={(event) => setFilter(setSourceId)(event.target.value)}
            >
              <option value="">All employer boards</option>
              {sources.map((source) => (
                <option key={source.id} value={source.id}>
                  {source.name}
                </option>
              ))}
            </select>
          </label>
          {!tracking && (
            <label className="filter-select">
              <span className="sr-only">Job status</span>
              <select value={status} onChange={(event) => setFilter(setStatus)(event.target.value)}>
                <option value="">All visible statuses</option>
                {Object.entries(statusLabels).map(([key, label]) => (
                  <option key={key} value={key}>
                    {label}
                  </option>
                ))}
              </select>
            </label>
          )}
          {hasFilters && (
            <button className="clear-filters" onClick={clearFilters}>
              <X size={14} />
              Reset
            </button>
          )}
        </div>
        <div className="search-options">
          <label className="check-label">
            <input
              type="checkbox"
              checked={engineeringOnly}
              onChange={(event) => {
                setEngineeringOnly(event.target.checked)
                setPage(0)
              }}
            />
            Software engineering roles only
          </label>
          <span>
            <MapPin size={13} />
            All locations included
          </span>
        </div>
      </div>

      <div className="results-heading">
        <div>
          <h2>
            {tracking
              ? status === 'APPLIED'
                ? demo
                  ? 'Demo application tracker'
                  : 'Applications submitted'
                : status === 'HIDDEN'
                  ? 'Hidden roles'
                  : 'Your shortlist'
              : 'Opportunities'}
          </h2>
          <span className="result-count" aria-live="polite">
            {loading
              ? 'Searching…'
              : error
                ? 'Unavailable'
                : `${data?.totalElements.toLocaleString() ?? 0} ${(data?.totalElements ?? 0) === 1 ? 'role' : 'roles'}`}
          </span>
        </div>
        <label className="sort-control">
          <ArrowDownWideNarrow size={15} />
          <span className="sr-only">Sort jobs</span>
          <select
            value={sort}
            onChange={(event) => {
              setSort(event.target.value)
              setPage(0)
            }}
          >
            <option value="priority">Priority first</option>
            <option value="newest">Newest first</option>
          </select>
        </label>
      </div>
      {!tracking && (
        <p className="results-note">
          {demo
            ? 'Results come from the shared employer catalog. Filters and tracking changes stay in this temporary demo.'
            : 'Results come from your tracked boards. Add an employer board to broaden your search.'}
        </p>
      )}
      <ErrorNotice error={error} retry={() => setReload((value) => value + 1)} />
      <ErrorNotice error={actionError} />
      {loading ? (
        <SkeletonCards />
      ) : !error && data && data.items.length > 0 ? (
        <div className="job-list">
          {data.items.map((job) => (
            <JobCard
              key={job.id}
              job={job}
              onSelect={() => setSelectedId(job.id)}
              onSave={() => void changeStatus(job, job.status === 'SAVED' ? 'NEW' : 'SAVED')}
              busy={savingIds.includes(job.id)}
            />
          ))}
        </div>
      ) : (
        !error && (
          <EmptyState
            title={
              tracking
                ? status === 'APPLIED'
                  ? 'Your next application starts here.'
                  : status === 'HIDDEN'
                    ? 'No hidden roles.'
                    : 'A shortlist worth building.'
                : hasFilters
                  ? 'No roles match these filters.'
                  : 'Your search is ready to begin.'
            }
          >
            {tracking
              ? status === 'APPLIED'
                ? demo
                  ? 'Open a role and try Mark as applied to explore the tracker. This only changes its temporary status; no application is submitted.'
                  : 'After you submit an application on an employer’s site, mark the role as applied to keep track of it here.'
                : status === 'HIDDEN'
                  ? 'Roles you hide will appear here, so you can revisit them later.'
                  : 'Save roles from Discover jobs to collect the opportunities you want to explore.'
              : hasFilters
                ? 'Try a broader title, another experience level, or fewer filters.'
                : demo
                  ? 'The shared catalog has no matching listings right now. Try again after the owner refreshes the employer boards.'
                  : 'Add employer boards, then refresh them to bring real job listings into your workspace.'}
            {!tracking && (
              <span className="empty-coverage">
                This search covers tracked employer boards, not the entire web.
              </span>
            )}
          </EmptyState>
        )
      )}
      {!loading && !error && (!data || data.items.length === 0) && !tracking && (
        <div className="empty-actions">
          {hasFilters && (
            <button className="button button-secondary" onClick={clearFilters}>
              <RotateCcw size={16} />
              Clear filters
            </button>
          )}
          <button className="button button-primary" onClick={onManageSources}>
            <Layers3 size={16} />
            {demo ? 'View employer boards' : 'Manage employer boards'}
          </button>
        </div>
      )}
      {!loading && data && data.totalPages > 1 && (
        <nav className="pagination" aria-label="Job result pages">
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <div>
            <button
              className="button button-secondary button-small"
              disabled={page === 0}
              onClick={() => setPage((value) => value - 1)}
            >
              <ChevronLeft size={16} />
              Previous
            </button>
            <button
              className="button button-secondary button-small"
              disabled={page >= data.totalPages - 1}
              onClick={() => setPage((value) => value + 1)}
            >
              Next
              <ChevronRight size={16} />
            </button>
          </div>
        </nav>
      )}
      {selectedId !== null && (
        <JobDetails
          jobId={selectedId}
          onClose={() => setSelectedId(null)}
          onStatus={changeStatus}
          onEditProfile={onEditProfile}
          demo={demo}
        />
      )}
    </section>
  )
}

function JobCard({
  job,
  onSelect,
  onSave,
  busy,
}: {
  job: Job
  onSelect: () => void
  onSave: () => void
  busy: boolean
}) {
  return (
    <article className={`job-card ${!job.active ? 'inactive' : ''}`}>
      <div className="company-avatar" aria-hidden="true">
        {initials(job.company || job.sourceName)}
      </div>
      <div className="job-card-body">
        <div className="job-company">
          {job.company || job.sourceName}
          <span className="dot-divider">·</span>
          <span>{job.sourceName}</span>
          {!job.active && <span className="tag tag-muted">No longer listed</span>}
        </div>
        <h3>
          <button className="job-title" onClick={onSelect}>
            {job.title}
            <ArrowUpRight size={17} />
          </button>
        </h3>
        <div className="job-meta">
          <span>
            <MapPin size={14} />
            {job.location || 'Location unspecified'}
          </span>
          <span className={`tag ${job.workplace === 'REMOTE' ? 'tag-teal' : 'tag-muted'}`}>
            {workplaceLabels[job.workplace as Workplace] || job.workplace}
          </span>
          <span className="tag tag-outline">{levelLabels[job.level as JobLevel] || job.level}</span>
        </div>
        <div className="job-card-footer">
          <span>
            <Clock3 size={13} />
            {job.publishedAt
              ? `Posted ${formatDate(job.publishedAt)}`
              : `First seen ${formatDate(job.firstSeenAt)}`}
          </span>
          {job.status !== 'NEW' && (
            <span className={`inline-status status-${job.status.toLowerCase()}`}>
              {statusLabels[job.status]}
            </span>
          )}
        </div>
      </div>
      <div className="job-card-actions">
        {job.status === 'APPLIED' ? (
          <span className="applied-check" title="Marked as applied">
            <Check size={18} />
          </span>
        ) : (
          <button
            className={`icon-button bookmark-button ${job.status === 'SAVED' ? 'is-saved' : ''}`}
            aria-label={job.status === 'SAVED' ? `Unsave ${job.title}` : `Save ${job.title}`}
            aria-pressed={job.status === 'SAVED'}
            onClick={onSave}
            disabled={busy}
          >
            <Bookmark size={19} fill={job.status === 'SAVED' ? 'currentColor' : 'none'} />
          </button>
        )}
        <button className="job-open" onClick={onSelect} aria-label={`View ${job.title}`}>
          <ArrowRight size={19} />
        </button>
      </div>
    </article>
  )
}

function JobDetails({
  jobId,
  onClose,
  onStatus,
  onEditProfile,
  demo,
}: {
  jobId: number
  onClose: () => void
  onStatus: (job: Job, status: JobStatus) => Promise<boolean>
  onEditProfile: () => void
  demo: boolean
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const [job, setJob] = useState<Job | null>(null)
  const [error, setError] = useState<unknown>(null)
  const [busy, setBusy] = useState(false)
  const [match, setMatch] = useState<Match | null>(null)
  const [matchLoading, setMatchLoading] = useState(false)
  const [matchError, setMatchError] = useState<unknown>(null)
  const [tab, setTab] = useState<'description' | 'match'>('description')
  const [reload, setReload] = useState(0)
  useEffect(() => {
    dialog.current?.showModal()
    return () => dialog.current?.close()
  }, [])
  useEffect(() => {
    const controller = new AbortController()
    setError(null)
    api<Job>(`/api/jobs/${jobId}`, { signal: controller.signal })
      .then(setJob)
      .catch((cause) => {
        if (!controller.signal.aborted) setError(cause)
      })
    return () => controller.abort()
  }, [jobId, reload])
  const update = async (status: JobStatus) => {
    if (!job) return
    setBusy(true)
    const success = await onStatus(job, status)
    if (success) setJob({ ...job, status })
    else setError(new Error('Could not update this role. Your previous status has been preserved.'))
    setBusy(false)
  }
  const getMatch = async () => {
    setMatchLoading(true)
    setMatchError(null)
    try {
      setMatch(await api<Match>(`/api/jobs/${jobId}/match`))
    } catch (cause) {
      setMatchError(cause)
    } finally {
      setMatchLoading(false)
    }
  }
  const url = job ? safeExternalUrl(job.url) : undefined
  return (
    <dialog
      ref={dialog}
      className="detail-dialog"
      onCancel={onClose}
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
      aria-labelledby="detail-heading"
    >
      <div className="detail-inner">
        <div className="detail-top">
          <span>
            <FileSearch size={17} />
            ROLE DETAILS
          </span>
          <button
            className="icon-button"
            aria-label="Close job details"
            onClick={onClose}
            autoFocus
          >
            <X size={21} />
          </button>
        </div>
        {demo && (
          <div className="demo-banner" role="note">
            <p>
              <strong>Temporary demo</strong> · Matches use a fictional profile. Tracking changes
              reset when you sign out or after 30 minutes of inactivity.
            </p>
          </div>
        )}
        <ErrorNotice error={error} retry={() => setReload((value) => value + 1)} />
        {!job && !error && <Loading label="Loading role details…" />}
        {job && (
          <>
            <div className="detail-heading">
              <div className="company-avatar">{initials(job.company || job.sourceName)}</div>
              <p>{job.company || job.sourceName}</p>
              <h2 id="detail-heading">{job.title}</h2>
              <div className="job-meta">
                <span>
                  <MapPin size={15} />
                  {job.location || 'Location unspecified'}
                </span>
                <span className="tag tag-teal">{workplaceLabels[job.workplace]}</span>
                <span className="tag tag-outline">{levelLabels[job.level]}</span>
              </div>
              {!job.active && (
                <div className="notice notice-warning">
                  <CircleHelp size={17} />
                  <p>
                    This role is no longer listed by its source. Check the employer’s page before
                    applying.
                  </p>
                </div>
              )}
            </div>
            <div className="detail-actions">
              {url && (
                <a
                  className="button button-primary"
                  href={url}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Open employer page
                  <ExternalLink size={15} />
                </a>
              )}
              <button
                className="button button-secondary"
                onClick={() => void update(job.status === 'SAVED' ? 'NEW' : 'SAVED')}
                disabled={busy || job.status === 'APPLIED'}
              >
                <Bookmark size={16} fill={job.status === 'SAVED' ? 'currentColor' : 'none'} />
                {job.status === 'SAVED' ? 'Saved' : 'Save role'}
              </button>
            </div>
            <div className="detail-tabs">
              <button
                className={tab === 'description' ? 'selected' : ''}
                onClick={() => setTab('description')}
              >
                Job description
              </button>
              <button
                className={tab === 'match' ? 'selected' : ''}
                onClick={() => {
                  setTab('match')
                  if (!match && !matchLoading) void getMatch()
                }}
              >
                <Sparkles size={15} />
                Profile match
              </button>
            </div>
            {tab === 'description' ? (
              <div className="job-description">
                {job.description ||
                  'No description was provided. Open the employer page for the latest details.'}
              </div>
            ) : (
              <div className="match-section">
                <div className="match-intro">
                  <span className="tag tag-teal">Evidence-based review</span>
                  <p>
                    Matching uses the skills and confirmed facts in your profile. It does not
                    predict a hiring outcome.
                  </p>
                </div>
                {matchLoading && <Loading label="Comparing your profile with this role…" />}
                <ErrorNotice error={matchError} retry={() => void getMatch()} />
                {match && !matchLoading && (
                  <>
                    <p className="match-method">{match.label}</p>
                    <FindingGroup
                      title="Where you align"
                      icon="strength"
                      findings={match.strengths}
                      empty="No supported strengths found yet."
                    />
                    <FindingGroup
                      title="Gaps to review"
                      icon="gap"
                      findings={match.gaps}
                      empty="No explicit gaps were identified."
                    />
                    <FindingGroup
                      title="What is still unknown"
                      icon="unknown"
                      findings={match.unknowns}
                      empty="No additional unknowns were identified."
                    />
                    <div className="match-footer">
                      <span>Profile version {match.profileVersion}</span>
                      <button className="text-button" onClick={() => void getMatch()}>
                        Recheck match
                        <RotateCcw size={13} />
                      </button>
                    </div>
                  </>
                )}
                <button
                  className="button button-secondary"
                  onClick={() => {
                    onClose()
                    onEditProfile()
                  }}
                >
                  {demo ? 'Edit demo profile' : 'Edit my profile'}
                  <ArrowUpRight size={15} />
                </button>
              </div>
            )}
            <div className="application-status-panel">
              <h3>Keep your progress up to date</h3>
              <p>
                {demo
                  ? 'Try changing the status in this temporary tracker. No application is submitted.'
                  : 'Mark a role as applied after you submit on the employer’s site.'}
              </p>
              <div>
                <button
                  className={`button ${job.status === 'APPLIED' ? 'button-success' : 'button-secondary'}`}
                  onClick={() => void update(job.status === 'APPLIED' ? 'SAVED' : 'APPLIED')}
                  disabled={busy}
                >
                  <Check size={16} />
                  {job.status === 'APPLIED' ? 'Applied · undo' : 'Mark as applied'}
                </button>
                <button
                  className="button button-quiet"
                  onClick={() => void update(job.status === 'HIDDEN' ? 'NEW' : 'HIDDEN')}
                  disabled={busy}
                >
                  <EyeOff size={16} />
                  {job.status === 'HIDDEN' ? 'Restore role' : 'Hide role'}
                </button>
              </div>
            </div>
            <dl className="source-metadata">
              <div>
                <dt>Employer board</dt>
                <dd>{job.sourceName}</dd>
              </div>
              <div>
                <dt>Published</dt>
                <dd>{formatDate(job.publishedAt)}</dd>
              </div>
              <div>
                <dt>First seen</dt>
                <dd>{formatDate(job.firstSeenAt)}</dd>
              </div>
              <div>
                <dt>Last seen</dt>
                <dd>{formatDateTime(job.lastSeenAt)}</dd>
              </div>
            </dl>
          </>
        )}
      </div>
    </dialog>
  )
}

function FindingGroup({
  title,
  icon,
  findings,
  empty,
}: {
  title: string
  icon: 'strength' | 'gap' | 'unknown'
  findings: MatchFinding[]
  empty: string
}) {
  return (
    <section className={`finding-group finding-${icon}`}>
      <h3>
        {icon === 'strength' ? (
          <Check size={17} />
        ) : icon === 'gap' ? (
          <Filter size={16} />
        ) : (
          <CircleHelp size={17} />
        )}
        {title}
        <span>{findings.length}</span>
      </h3>
      {findings.length ? (
        <ul>
          {findings.map((finding, index) => (
            <li key={`${finding.type}-${index}`}>
              <p>{finding.message}</p>
              {finding.evidence.length > 0 && (
                <details>
                  <summary>View supporting evidence</summary>
                  {finding.evidence.map((evidence, evidenceIndex) => (
                    <blockquote key={evidenceIndex}>
                      <span>
                        {evidence.source === 'JOB_TEXT'
                          ? 'Job description'
                          : evidence.source === 'PROFILE_SKILL'
                            ? 'Your skills'
                            : evidence.source === 'CAREER_FACT'
                              ? `Career fact · ${evidence.reference}`
                              : 'Your preferences'}
                      </span>
                      {evidence.text}
                    </blockquote>
                  ))}
                </details>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <p className="finding-empty">{empty}</p>
      )}
    </section>
  )
}
