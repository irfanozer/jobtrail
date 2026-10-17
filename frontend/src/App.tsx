import { useCallback, useEffect, useState } from 'react'
import {
  ArrowUpRight,
  BriefcaseBusiness,
  Check,
  ChevronRight,
  CircleHelp,
  Compass,
  Layers3,
  LoaderCircle,
  LogOut,
  MapPinned,
  RefreshCw,
  Route,
  UserRound,
} from 'lucide-react'
import { ApiError, api, readCookie } from './api'
import { ErrorNotice } from './components'
import { formatDateTime } from './domain'
import { Jobs } from './Jobs'
import { Sources } from './Sources'
import { ProfileEditor } from './Profile'
import type { RefreshRun, Source } from './types'

type View = 'jobs' | 'applications' | 'sources' | 'profile'
const navigation = [
  { id: 'jobs' as const, label: 'Discover jobs', icon: Compass },
  { id: 'applications' as const, label: 'My applications', icon: BriefcaseBusiness },
  { id: 'sources' as const, label: 'Employer boards', icon: Layers3 },
  { id: 'profile' as const, label: 'My profile', icon: UserRound },
]

export default function App() {
  const [view, setView] = useState<View>('jobs')
  const [sources, setSources] = useState<Source[]>([])
  const [sourcesLoaded, setSourcesLoaded] = useState(false)
  const [sourceError, setSourceError] = useState<unknown>(null)
  const [run, setRun] = useState<RefreshRun | null>(null)
  const [refreshPending, setRefreshPending] = useState(false)
  const [refreshError, setRefreshError] = useState<unknown>(null)
  const [revision, setRevision] = useState(0)
  const [hideRun, setHideRun] = useState(false)
  const [authenticationEnabled, setAuthenticationEnabled] = useState(false)
  const running = refreshPending || run?.status === 'RUNNING'

  const loadSources = useCallback(async () => {
    try {
      const result = await api<Source[]>('/api/sources')
      setSources(result)
      setSourcesLoaded(true)
      setSourceError(null)
    } catch (error) {
      setSourceError(error)
    }
  }, [])

  useEffect(() => {
    void loadSources()
  }, [loadSources])
  useEffect(() => {
    api<RefreshRun | undefined>('/api/refresh/latest')
      .then((previous) => {
        if (previous) setRun(previous)
      })
      .catch((error) => {
        if (!(error instanceof ApiError && error.status === 404)) setRefreshError(error)
      })
    api<{ authenticationEnabled: boolean }>('/api/session')
      .then((session) => setAuthenticationEnabled(session.authenticationEnabled))
      .catch(() => {})
  }, [])

  const refresh = async () => {
    setRefreshError(null)
    setRefreshPending(true)
    setHideRun(false)
    try {
      const next = await api<RefreshRun>('/api/refresh', { method: 'POST' })
      setRun(next)
      if (next.status !== 'RUNNING') {
        setRevision((value) => value + 1)
        void loadSources()
      }
    } catch (error) {
      setRefreshError(error)
    } finally {
      setRefreshPending(false)
    }
  }

  useEffect(() => {
    if (!run || run.status !== 'RUNNING') return
    let cancelled = false
    let timer: ReturnType<typeof setTimeout>
    const poll = async () => {
      try {
        const next = await api<RefreshRun>(`/api/refresh/${encodeURIComponent(run.id)}`)
        if (cancelled) return
        setRun(next)
        setRefreshError(null)
        if (next.status !== 'RUNNING') {
          setRevision((value) => value + 1)
          void loadSources()
        }
      } catch (error) {
        if (!cancelled) setRefreshError(error)
      }
      if (!cancelled) timer = setTimeout(poll, 4000)
    }
    timer = setTimeout(poll, 1200)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [run?.id, run?.status, loadSources])

  const activeSources = sources.filter((source) => source.enabled).length
  const latestRefresh = sources
    .map((source) => source.lastSuccessAt)
    .filter((date): date is string => !!date)
    .sort()
    .at(-1)
  const currentNav = navigation.find((item) => item.id === view)!

  return (
    <div className="app-shell">
      <a className="skip-link" href="#main-content">
        Skip to content
      </a>
      <aside className="sidebar">
        <a
          href="#"
          className="brand"
          aria-label="JobTrail home"
          onClick={(event) => {
            event.preventDefault()
            setView('jobs')
          }}
        >
          <span className="brand-mark">
            <Route size={25} strokeWidth={2.2} />
          </span>
          JobTrail<span className="brand-dot">.</span>
        </a>
        <div className="sidebar-section-label">YOUR WORKSPACE</div>
        <nav aria-label="Main navigation">
          {navigation.map((item) => (
            <button
              key={item.id}
              className={`nav-item ${view === item.id ? 'active' : ''}`}
              aria-current={view === item.id ? 'page' : undefined}
              onClick={() => setView(item.id)}
            >
              <item.icon size={20} />
              <span>{item.label}</span>
              {view === item.id && <ChevronRight size={16} className="nav-chevron" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="workspace-note">
            <span className="small-icon">
              <MapPinned size={18} />
            </span>
            <strong>A search with direction.</strong>
            <p>
              Your boards, your experience,
              <br />
              your next opportunity.
            </p>
          </div>
          <div className="workspace-label">
            <span className="status-dot" />
            Personal workspace
          </div>
        </div>
      </aside>

      <div className="main-shell">
        <header className="topbar">
          <div className="breadcrumb">
            Workspace <ChevronRight size={14} /> <span>{currentNav.label}</span>
          </div>
          {authenticationEnabled ? (
            <form action="/logout" method="post">
              <input type="hidden" name="_csrf" value={readCookie('XSRF-TOKEN') || ''} />
              <button className="text-button">
                <LogOut size={14} />
                Sign out
              </button>
            </form>
          ) : (
            <div className="topbar-note">
              <span className="status-dot" />
              Built around your next move
            </div>
          )}
        </header>
        <main id="main-content" tabIndex={-1}>
          <div className="page-heading">
            <div>
              <div className="eyebrow">
                {view === 'jobs'
                  ? 'MAKE YOUR NEXT MOVE'
                  : view === 'sources'
                    ? 'CHOOSE YOUR COVERAGE'
                    : view === 'profile'
                      ? 'START WITH YOUR EXPERIENCE'
                      : 'KEEP YOUR MOMENTUM'}
              </div>
              <h1>
                {view === 'jobs'
                  ? 'Find your next chapter.'
                  : view === 'sources'
                    ? 'Go straight to the source.'
                    : view === 'profile'
                      ? 'Your experience. In your words.'
                      : 'Every opportunity, in view.'}
              </h1>
              <p>
                {view === 'jobs'
                  ? 'Search tracked employer boards. Find roles worth your time.'
                  : view === 'sources'
                    ? 'Build a watchlist of employer boards to search and refresh.'
                    : view === 'profile'
                      ? 'Add real skills and confirmed facts for evidence-based job matching.'
                      : 'Save interesting roles and track the applications you submit.'}
              </p>
            </div>
            {view !== 'profile' && (
              <button
                className="button button-primary refresh-button"
                onClick={() => void refresh()}
                disabled={running || !activeSources}
              >
                <RefreshCw size={17} className={running ? 'spin' : ''} />
                {running ? 'Refreshing…' : 'Refresh boards'}
              </button>
            )}
          </div>
          <ErrorNotice error={sourceError} retry={() => void loadSources()} />
          <ErrorNotice error={refreshError} />
          {run && !hideRun && (
            <div
              className={`refresh-notice ${run.status === 'FAILED' || run.status === 'PARTIAL' ? 'refresh-warning' : ''}`}
              role="status"
            >
              <div className="refresh-summary">
                {run.status === 'RUNNING' ? (
                  <LoaderCircle size={18} className="spin" />
                ) : run.status === 'COMPLETED' ? (
                  <Check size={18} />
                ) : (
                  <CircleHelp size={18} />
                )}
                <div>
                  <strong>
                    {run.status === 'RUNNING'
                      ? 'Checking your employer boards'
                      : run.status === 'COMPLETED'
                        ? 'Your boards are up to date'
                        : run.status === 'PARTIAL'
                          ? 'Refresh completed with some source errors'
                          : 'The refresh could not complete'}
                  </strong>
                  <p>
                    {run.status === 'RUNNING'
                      ? 'You can keep browsing while new listings are collected.'
                      : `${run.sources.filter((source) => source.status === 'SUCCESS').length} of ${run.sources.length} boards refreshed successfully.`}
                  </p>
                </div>
                {run.status !== 'RUNNING' && (
                  <button className="text-button" onClick={() => setHideRun(true)}>
                    Dismiss
                  </button>
                )}
              </div>
              {run.sources.some((source) => source.status === 'FAILED') && (
                <ul className="refresh-errors">
                  {run.sources
                    .filter((source) => source.status === 'FAILED')
                    .map((source) => (
                      <li key={source.sourceId}>
                        <strong>{source.name}:</strong>{' '}
                        {source.error || 'Refresh failed. Try again later.'}
                      </li>
                    ))}
                </ul>
              )}
            </div>
          )}

          {(view === 'jobs' || view === 'applications') && (
            <>
              <div className="coverage-bar">
                <div>
                  <Layers3 size={16} />
                  <span>
                    {sourcesLoaded ? (
                      <>
                        <strong>{activeSources}</strong> active employer{' '}
                        {activeSources === 1 ? 'board' : 'boards'}
                      </>
                    ) : sourceError ? (
                      'Employer boards unavailable'
                    ) : (
                      'Loading your employer boards…'
                    )}
                  </span>
                  <span className="coverage-divider" />
                  <span>Last refreshed {latestRefresh ? formatDateTime(latestRefresh) : '—'}</span>
                </div>
                <button className="text-button" onClick={() => setView('sources')}>
                  Manage coverage <ArrowUpRight size={14} />
                </button>
              </div>
              <Jobs
                key={view}
                sources={sources}
                revision={revision}
                tracking={view === 'applications'}
                onManageSources={() => setView('sources')}
                onEditProfile={() => setView('profile')}
              />
            </>
          )}
          {view === 'sources' && (
            <Sources
              sources={sources}
              loaded={sourcesLoaded}
              loadFailed={!!sourceError}
              onChange={loadSources}
              onRefresh={() => void refresh()}
              refreshing={!!running}
            />
          )}
          <div hidden={view !== 'profile'}>
            <ProfileEditor />
          </div>
          <footer className="page-footer">
            <span>JobTrail</span>
            <span>Made for a more intentional job search.</span>
          </footer>
        </main>
      </div>
    </div>
  )
}
