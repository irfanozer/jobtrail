import { useState } from 'react'
import type { FormEvent } from 'react'
import {
  ArrowUpRight,
  CheckCircle2,
  CircleHelp,
  ExternalLink,
  Layers3,
  LoaderCircle,
  Plus,
  RefreshCw,
} from 'lucide-react'
import { api } from './api'
import { EmptyState, ErrorNotice, Loading } from './components'
import { formatDateTime, initials } from './domain'
import type { Provider, Source } from './types'

export function Sources({
  sources,
  loaded,
  loadFailed,
  onChange,
  onRefresh,
  refreshing,
  readOnly = false,
}: {
  sources: Source[]
  loaded: boolean
  loadFailed: boolean
  onChange: () => Promise<void>
  onRefresh: () => void
  refreshing: boolean
  readOnly?: boolean
}) {
  const [name, setName] = useState('')
  const [provider, setProvider] = useState<Provider>('GREENHOUSE')
  const [boardKey, setBoardKey] = useState('')
  const [saving, setSaving] = useState(false)
  const [pendingIds, setPendingIds] = useState<number[]>([])
  const [error, setError] = useState<unknown>(null)
  const [success, setSuccess] = useState('')

  const addSource = async (event: FormEvent) => {
    event.preventDefault()
    if (readOnly) return
    setSaving(true)
    setError(null)
    setSuccess('')
    try {
      await api<Source>('/api/sources', {
        method: 'POST',
        body: JSON.stringify({ name: name.trim(), provider, boardKey: boardKey.trim() }),
      })
      await onChange()
      setSuccess(`${name.trim()} added. Refresh your boards to collect its listings.`)
      setName('')
      setBoardKey('')
    } catch (cause) {
      setError(cause)
    } finally {
      setSaving(false)
    }
  }

  const toggle = async (source: Source) => {
    if (readOnly) return
    setError(null)
    setSuccess('')
    setPendingIds((previous) => [...previous, source.id])
    try {
      await api<Source>(`/api/sources/${source.id}`, {
        method: 'PATCH',
        body: JSON.stringify({ enabled: !source.enabled }),
      })
      await onChange()
      setSuccess(`${source.name} ${source.enabled ? 'paused' : 'enabled'}.`)
    } catch (cause) {
      setError(cause)
    } finally {
      setPendingIds((previous) => previous.filter((id) => id !== source.id))
    }
  }

  return (
    <div className="sources-layout">
      <section className="sources-main" aria-label="Tracked employer boards">
        <div className="section-title">
          <h2>{readOnly ? 'Shared employer boards' : 'Your employer boards'}</h2>
          <span className="count-pill">{loaded ? sources.length : '—'}</span>
        </div>
        <p className="section-description">
          {readOnly
            ? 'Each board belongs to one employer. The demo uses a shared catalog; adding, pausing, and refreshing boards is available in the owner workspace.'
            : 'Each board belongs to one employer. Paused boards stay in your workspace and are skipped during refresh.'}
        </p>
        <ErrorNotice error={error} />
        {success && (
          <div className="notice notice-success" role="status">
            <CheckCircle2 size={18} />
            <p>{success}</p>
          </div>
        )}
        {!loaded && loadFailed ? (
          <p className="section-description">
            Employer boards could not be loaded. Use Try again above to reconnect.
          </p>
        ) : !loaded ? (
          <Loading label="Loading employer boards…" />
        ) : sources.length === 0 ? (
          <div className="surface">
            <EmptyState title="Choose where to look first.">
              {readOnly
                ? 'There are no employer boards in the shared catalog yet. Please try the demo again later.'
                : 'Add an employer’s Greenhouse or Lever board using the form. Then refresh to collect its available roles.'}
            </EmptyState>
          </div>
        ) : (
          <div className="source-list">
            {sources.map((source) => (
              <article
                className={`source-card ${source.enabled ? '' : 'source-paused'}`}
                key={source.id}
              >
                <div className="source-card-top">
                  <div className="company-avatar">{initials(source.name)}</div>
                  <div className="source-card-title">
                    <h3>{source.name}</h3>
                    <span>
                      {source.provider === 'GREENHOUSE' ? 'Greenhouse' : 'Lever'}
                      <span className="dot-divider">·</span>
                      {source.boardKey}
                    </span>
                  </div>
                  <label className="toggle-control">
                    <input
                      type="checkbox"
                      role="switch"
                      aria-label={`Enable ${source.name}`}
                      checked={source.enabled}
                      onChange={() => void toggle(source)}
                      disabled={readOnly || pendingIds.includes(source.id)}
                    />
                    <span className="toggle-track" />
                    <span className="toggle-caption">{source.enabled ? 'Active' : 'Paused'}</span>
                  </label>
                </div>
                <div className="source-card-stats">
                  <span>
                    <Layers3 size={14} />
                    <strong>{source.jobCount.toLocaleString()}</strong>{' '}
                    {source.jobCount === 1 ? 'role' : 'roles'}
                  </span>
                  <span>
                    <RefreshCw size={13} />
                    {source.lastSuccessAt
                      ? formatDateTime(source.lastSuccessAt)
                      : 'Not refreshed yet'}
                  </span>
                  <a
                    href={
                      source.provider === 'GREENHOUSE'
                        ? `https://boards.greenhouse.io/${encodeURIComponent(source.boardKey)}`
                        : `https://jobs.lever.co/${encodeURIComponent(source.boardKey)}`
                    }
                    target="_blank"
                    rel="noopener noreferrer"
                  >
                    Visit board
                    <ArrowUpRight size={14} />
                  </a>
                </div>
                {source.lastError && (
                  <div className="source-error">
                    <CircleHelp size={16} />
                    <div>
                      <strong>Last refresh needs attention</strong>
                      <p>{source.lastError}</p>
                      {source.lastAttemptAt && (
                        <span>Attempted {formatDateTime(source.lastAttemptAt)}</span>
                      )}
                    </div>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </section>
      <aside className="source-add-panel">
        {readOnly ? (
          <section className="surface add-source-form">
            <span className="form-kicker">
              <Layers3 size={17} /> SHARED CATALOG
            </span>
            <h2>Explore the current coverage</h2>
            <p>
              Search these listings, try the filters, and save roles to your temporary tracker.
              Employer board settings and collection runs are managed by the owner.
            </p>
          </section>
        ) : (
          <form className="surface add-source-form" onSubmit={(event) => void addSource(event)}>
            <span className="form-kicker">
              <Plus size={17} />
              EXPAND YOUR SEARCH
            </span>
            <h2>Add an employer board</h2>
            <p>Track a company’s official listings from a supported hiring platform.</p>
            <label className="field">
              <span>Employer name</span>
              <input
                required
                maxLength={100}
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder="Company name"
              />
            </label>
            <label className="field">
              <span>Hiring platform</span>
              <select
                value={provider}
                onChange={(event) => setProvider(event.target.value as Provider)}
              >
                <option value="GREENHOUSE">Greenhouse</option>
                <option value="LEVER">Lever</option>
              </select>
            </label>
            <label className="field">
              <span>Board key</span>
              <input
                required
                value={boardKey}
                onChange={(event) => setBoardKey(event.target.value)}
                pattern="[A-Za-z0-9_-]+"
                title="Enter the employer’s board key using letters, numbers, hyphens, or underscores."
                maxLength={100}
                placeholder="employer-board-key"
                autoCapitalize="none"
                spellCheck={false}
              />
              <small>Use the employer’s board identifier, not the full URL.</small>
            </label>
            <div className="url-example">
              <span>Find it at the end of the board URL</span>
              <code>
                {provider === 'GREENHOUSE' ? 'boards.greenhouse.io/' : 'jobs.lever.co/'}
                <strong>board-key</strong>
              </code>
            </div>
            <button
              className="button button-primary full-width"
              disabled={saving || !name.trim() || !boardKey.trim()}
            >
              {saving ? <LoaderCircle size={16} className="spin" /> : <Plus size={17} />}
              {saving ? 'Adding board…' : 'Add employer board'}
            </button>
          </form>
        )}
        <div className="source-help">
          <CircleHelp size={18} />
          <div>
            <strong>Know what your search covers.</strong>
            <p>
              {readOnly
                ? 'JobTrail searches the employer boards listed here. It does not search every employer or job site.'
                : 'JobTrail searches the boards you add. It does not search every employer or job site. Add more boards as your target list grows.'}
            </p>
            {!readOnly && (
              <button
                className="text-button"
                onClick={onRefresh}
                disabled={refreshing || !sources.some((source) => source.enabled)}
              >
                Refresh active boards
                <ExternalLink size={13} />
              </button>
            )}
          </div>
        </div>
      </aside>
    </div>
  )
}
