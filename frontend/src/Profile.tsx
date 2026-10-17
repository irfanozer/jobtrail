import { useCallback, useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import {
  BookOpen,
  Check,
  CheckCircle2,
  FileCheck2,
  Fingerprint,
  Lightbulb,
  LoaderCircle,
  Plus,
  Save,
  ShieldCheck,
  Trash2,
  UserRound,
} from 'lucide-react'
import { ApiError, api } from './api'
import { ErrorNotice, Loading } from './components'
import { formatDateTime, listFromText } from './domain'
import type { CareerFact, Profile } from './types'

export function ProfileEditor({ demo = false }: { demo?: boolean }) {
  const [profile, setProfile] = useState<Profile | null>(null)
  const [skills, setSkills] = useState('')
  const [roles, setRoles] = useState('')
  const [locations, setLocations] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<unknown>(null)
  const [success, setSuccess] = useState(false)
  const [dirty, setDirty] = useState(false)

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const result = await api<Profile>('/api/profile')
      setProfile(result)
      setSkills(result.skills.join(', '))
      setRoles(result.targetRoles.join(', '))
      setLocations(result.preferredLocations.join(', '))
      setDirty(false)
    } catch (cause) {
      setError(cause)
    } finally {
      setLoading(false)
    }
  }, [])
  useEffect(() => {
    void load()
  }, [load])
  useEffect(() => {
    if (!dirty || demo) return
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty, demo])

  const changed = () => {
    setSuccess(false)
    setDirty(true)
  }
  const changeFact = (id: string, change: Partial<CareerFact>) => {
    changed()
    setProfile((previous) =>
      previous
        ? {
            ...previous,
            careerFacts: previous.careerFacts.map((fact) =>
              fact.id === id ? { ...fact, ...change } : fact,
            ),
          }
        : null,
    )
  }
  const addFact = () => {
    changed()
    setProfile((previous) =>
      previous
        ? {
            ...previous,
            careerFacts: [
              ...previous.careerFacts,
              {
                id: `fact-${crypto.randomUUID().slice(0, 8)}`,
                statement: '',
                source: '',
                confirmed: false,
              },
            ],
          }
        : null,
    )
  }
  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!profile) return
    setSaving(true)
    setError(null)
    setSuccess(false)
    const body = {
      displayName: profile.displayName.trim(),
      version: profile.version,
      skills: listFromText(skills),
      targetRoles: listFromText(roles),
      preferredLocations: listFromText(locations),
      careerFacts: profile.careerFacts.map((fact) => ({
        ...fact,
        statement: fact.statement.trim(),
        source: fact.source.trim(),
      })),
    }
    try {
      const next = await api<Profile>('/api/profile', { method: 'PUT', body: JSON.stringify(body) })
      setProfile(next)
      setSkills(next.skills.join(', '))
      setRoles(next.targetRoles.join(', '))
      setLocations(next.preferredLocations.join(', '))
      setDirty(false)
      setSuccess(true)
    } catch (cause) {
      setError(cause)
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <Loading label="Loading your profile…" />
  if (!profile) return <ErrorNotice error={error} retry={() => void load()} />
  const confirmedCount = profile.careerFacts.filter((fact) => fact.confirmed).length

  return (
    <form className="profile-layout" onSubmit={(event) => void save(event)}>
      <div className="profile-main">
        <ErrorNotice error={error} />
        {error instanceof ApiError && error.status === 409 && (
          <div className="notice notice-warning">
            <p>Your profile changed in another session. Copy any unsaved edits before reloading.</p>
            <button type="button" className="text-button" onClick={() => void load()}>
              Reload saved profile
            </button>
          </div>
        )}
        {success && (
          <div className="notice notice-success" role="status">
            <CheckCircle2 size={18} />
            <p>
              {demo
                ? 'Example profile saved for this demo session. Open a role to see its updated match.'
                : 'Your profile is saved. Open a role to check its match against your experience.'}
            </p>
          </div>
        )}
        <section className="surface profile-section">
          <div className="section-header">
            <span className="section-icon">
              <UserRound size={20} />
            </span>
            <div>
              <h2>The basics</h2>
              <p>
                {demo
                  ? 'A fictional example you can edit for this session.'
                  : 'A little context for a more relevant search.'}
              </p>
            </div>
          </div>
          <label className="field">
            <span>
              Display name <em>Optional</em>
            </span>
            <input
              value={profile.displayName}
              onChange={(event) => {
                changed()
                setProfile({ ...profile, displayName: event.target.value })
              }}
              maxLength={120}
              placeholder={demo ? 'Fictional display name' : 'Your name'}
              autoComplete={demo ? 'off' : 'name'}
            />
          </label>
          <label className="field">
            <span>Target roles</span>
            <input
              value={roles}
              onChange={(event) => {
                changed()
                setRoles(event.target.value)
              }}
              placeholder="Enter the roles you are targeting"
            />
            <small>Separate role titles with commas.</small>
          </label>
          <label className="field">
            <span>Preferred locations</span>
            <input
              value={locations}
              onChange={(event) => {
                changed()
                setLocations(event.target.value)
              }}
              placeholder="Cities, regions, or remote preferences"
            />
            <small>
              These inform match explanations. Jobs in other locations remain searchable.
            </small>
          </label>
        </section>
        <section className="surface profile-section">
          <div className="section-header">
            <span className="section-icon">
              <BookOpen size={20} />
            </span>
            <div>
              <h2>Your skills</h2>
              <p>Add technologies and skills you can discuss with confidence.</p>
            </div>
          </div>
          <label className="field">
            <span>Skills and technologies</span>
            <textarea
              rows={3}
              value={skills}
              onChange={(event) => {
                changed()
                setSkills(event.target.value)
              }}
              placeholder="Add your real skills, separated by commas"
            />
            <small>
              Only include skills you actually have. Matching treats this list as your stated
              experience.
            </small>
          </label>
          {listFromText(skills).length > 0 && (
            <div className="skill-preview">
              {listFromText(skills).map((skill) => (
                <span className="tag tag-teal" key={skill}>
                  {skill}
                </span>
              ))}
            </div>
          )}
        </section>
        <section className="surface profile-section">
          <div className="section-header">
            <span className="section-icon">
              <FileCheck2 size={20} />
            </span>
            <div>
              <h2>Career facts</h2>
              <p>Keep a traceable record of what you have done.</p>
            </div>
            <span className="count-pill">{profile.careerFacts.length}</span>
          </div>
          <div className="facts-guidance">
            <ShieldCheck size={18} />
            <p>
              {demo
                ? 'Use fictional example statements only. Confirm an example fact to include it in this session’s matching results. Do not enter personal information.'
                : 'Use specific, truthful statements from your resume, projects, or work history. Only facts you confirm contribute to matching.'}
            </p>
          </div>
          {profile.careerFacts.length === 0 && (
            <div className="facts-empty">
              <Fingerprint size={30} />
              <h3>Your experience belongs here.</h3>
              <p>
                Add a project, responsibility, or achievement, then record where it came from.
                Nothing is filled in for you.
              </p>
            </div>
          )}
          <div className="fact-list">
            {profile.careerFacts.map((fact, index) => (
              <div className="fact-card" key={fact.id}>
                <div className="fact-card-heading">
                  <span>FACT {String(index + 1).padStart(2, '0')}</span>
                  <span className="fact-id">{fact.id}</span>
                  <button
                    type="button"
                    className="icon-button"
                    aria-label={`Remove career fact ${index + 1}`}
                    onClick={() => {
                      changed()
                      setProfile({
                        ...profile,
                        careerFacts: profile.careerFacts.filter((item) => item.id !== fact.id),
                      })
                    }}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
                <label className="field">
                  <span>What did you do?</span>
                  <textarea
                    required
                    rows={3}
                    value={fact.statement}
                    onChange={(event) =>
                      changeFact(fact.id, { statement: event.target.value, confirmed: false })
                    }
                    placeholder={
                      demo
                        ? 'Describe a fictional project or responsibility.'
                        : 'Describe a real responsibility, project, or achievement.'
                    }
                  />
                </label>
                <label className="field">
                  <span>
                    Source or reference {fact.confirmed && <em>Required for confirmed facts</em>}
                  </span>
                  <input
                    required={fact.confirmed}
                    value={fact.source}
                    onChange={(event) =>
                      changeFact(fact.id, { source: event.target.value, confirmed: false })
                    }
                    placeholder="Resume section, project link, or your own notes"
                  />
                </label>
                <label className="check-label fact-confirm">
                  <input
                    type="checkbox"
                    checked={fact.confirmed}
                    onChange={(event) => changeFact(fact.id, { confirmed: event.target.checked })}
                  />
                  <span>
                    {demo
                      ? 'Include this fictional example in matching.'
                      : 'I confirm this statement is accurate.'}
                  </span>
                  {fact.confirmed && <Check size={15} />}
                </label>
              </div>
            ))}
          </div>
          <button
            type="button"
            className="button button-secondary add-fact-button"
            onClick={addFact}
          >
            <Plus size={17} />
            Add a career fact
          </button>
        </section>
        <div className="profile-save-bar">
          <span>
            {dirty
              ? 'You have unsaved changes.'
              : profile.updatedAt
                ? `Saved ${formatDateTime(profile.updatedAt)}`
                : 'Your profile has not been saved yet.'}
          </span>
          <button className="button button-primary" disabled={saving}>
            {saving ? <LoaderCircle size={17} className="spin" /> : <Save size={17} />}
            {saving ? 'Saving…' : demo ? 'Save for this session' : 'Save profile'}
          </button>
        </div>
      </div>
      <aside className="profile-sidebar">
        <div className="profile-principle">
          <div className="principle-icon">
            <ShieldCheck size={28} />
          </div>
          <span className="eyebrow">
            {demo ? 'TRY PROFILE MATCHING' : 'GROUNDED IN YOUR EXPERIENCE'}
          </span>
          <h2>
            {demo
              ? 'See how the evidence changes a match.'
              : 'Good matches start with honest facts.'}
          </h2>
          <p>
            {demo
              ? 'This fictional profile lets you explore rules-based matching. Change its skills or example facts, save, and reopen a role to compare the explanation.'
              : 'Your profile connects job requirements to experience you can support. Missing information stays unknown.'}
          </p>
          <div className="profile-stat">
            <span>Skills entered</span>
            <strong>{listFromText(skills).length}</strong>
          </div>
          <div className="profile-stat">
            <span>Confirmed career facts</span>
            <strong>{confirmedCount}</strong>
          </div>
          <div className="profile-stat">
            <span>Facts awaiting confirmation</span>
            <strong>{profile.careerFacts.length - confirmedCount}</strong>
          </div>
        </div>
        <div className="profile-tip">
          <Lightbulb size={21} />
          <h3>Make each fact useful.</h3>
          <p>
            Describe what you did, the tools you used, and a result you can verify. Include a metric
            only when you know it is accurate.
          </p>
        </div>
        <div className="privacy-note">
          <ShieldCheck size={15} />
          <p>
            {demo
              ? 'Use fictional details only. Demo edits are temporary and are cleared when you sign out or your session expires.'
              : 'Your profile belongs in your private workspace. Avoid storing passwords, government identifiers, or other unrelated sensitive details.'}
          </p>
        </div>
      </aside>
    </form>
  )
}
