import { AlertCircle, ArrowUpRight, LoaderCircle, SearchX } from 'lucide-react'
import type { ReactNode } from 'react'
import { ApiError, errorMessage } from './api'

export function ErrorNotice({ error, retry }: { error: unknown; retry?: () => void }) {
  if (!error) return null
  return (
    <div className="notice notice-error" role="alert">
      <AlertCircle size={18} aria-hidden="true" />
      <div>
        <p>{errorMessage(error)}</p>
        {error instanceof ApiError && error.status === 401 ? (
          <a href="/login">
            Sign in <ArrowUpRight size={14} />
          </a>
        ) : retry ? (
          <button className="text-button" onClick={retry}>
            Try again
          </button>
        ) : null}
      </div>
    </div>
  )
}

export function EmptyState({
  title,
  children,
  action,
}: {
  title: string
  children: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="empty-state">
      <div className="empty-icon">
        <SearchX size={28} />
      </div>
      <h3>{title}</h3>
      <p>{children}</p>
      {action}
    </div>
  )
}

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <LoaderCircle size={20} className="spin" aria-hidden="true" />
      {label}
    </div>
  )
}

export function SkeletonCards() {
  return (
    <div className="skeleton-list" role="status" aria-label="Loading jobs">
      {[1, 2, 3].map((i) => (
        <div key={i} className="skeleton-card">
          <span />
          <span />
          <span />
        </div>
      ))}
    </div>
  )
}
