import { useEffect, useState } from 'react'
import { Link, Navigate, useSearchParams } from 'react-router-dom'
import { getAppliedJobs, getCandidate, removeAppliedJob, updateAppliedJobStatus } from '../api/candidates'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { useAuth } from '../context/AuthContext'
import { formatTimestamp } from '../format'
import { getNavItems } from '../nav'
import type { AppliedJob, AppliedJobStatus } from '../types'
import { safeHttpUrl } from '../safeUrl'

const STATUSES: { value: AppliedJobStatus; label: string }[] = [
  { value: 'APPLIED', label: 'Applied' },
  { value: 'INTERVIEWING', label: 'Interviewing' },
  { value: 'WAITING', label: 'Waiting on decision' },
  { value: 'OFFER', label: 'Offer' },
  { value: 'REJECTED', label: 'Rejected' },
]

type StatusFilter = AppliedJobStatus | 'ALL'

// How long the "Saved" note stays next to a status after it's changed.
const SAVED_NOTE_MS = 2000

const statusLabel = (status: AppliedJobStatus) => STATUSES.find((s) => s.value === status)?.label ?? status

// Jobs the candidate marked with "✓ Applied" on the Job Matches page, most recent first, each
// with a status they set as the application moves along. Marking one applied takes it out of
// the matches; removing it here lets it come back.
//
// Admin/Staff get a view-only version for any candidate (?candidate_id=, e.g. from the
// Candidates list): no status dropdown or undo, since the candidate reports these themselves.
// The backend enforces the same (only the candidate can change them).
export default function AppliedJobsPage() {
  const { user, isAdminOrStaff } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const readOnly = isAdminOrStaff
  // Whose applied jobs to show: the signed-in candidate's own, or the one an Admin/Staff picked.
  const candidateId = readOnly ? searchParams.get('candidate_id')?.trim() || null : user?.candidateProfileId
  const [candidateIdInput, setCandidateIdInput] = useState(searchParams.get('candidate_id') ?? '')
  const [appliedJobs, setAppliedJobs] = useState<AppliedJob[]>([])
  // Which candidate appliedJobs belongs to (and their name, for Admin/Staff); loading until it
  // matches candidateId.
  const [loadedFor, setLoadedFor] = useState<{ candidateId: string; name: string | null } | null>(null)
  const loading = !!candidateId && loadedFor?.candidateId !== candidateId
  const [error, setError] = useState<string | null>(null)
  const [filter, setFilter] = useState<StatusFilter>('ALL')
  const [confirmingId, setConfirmingId] = useState<string | null>(null)
  const [removingIds, setRemovingIds] = useState<Set<string>>(new Set())
  const [savingIds, setSavingIds] = useState<Set<string>>(new Set())
  const [savedId, setSavedId] = useState<string | null>(null)

  useEffect(() => {
    if (!candidateId) return
    let ignore = false
    // For Admin/Staff, looking the candidate up also catches a mistyped ID (a 404), which the
    // applied-jobs list alone would show as an empty list.
    Promise.all([getAppliedJobs(candidateId), readOnly ? getCandidate(candidateId) : Promise.resolve(null)])
      .then(([res, candidate]) => {
        if (ignore) return
        setError(null)
        setAppliedJobs(res.applied_jobs)
        setLoadedFor({ candidateId, name: candidate?.candidate_name ?? null })
      })
      .catch((err) => {
        if (ignore) return
        const fallback = readOnly ? "Failed to load this candidate's applied jobs." : 'Failed to load your applied jobs.'
        setError(err instanceof ApiError ? err.message : fallback)
        setAppliedJobs([])
        setLoadedFor({ candidateId, name: null })
      })
    return () => {
      ignore = true
    }
  }, [candidateId, readOnly])

  if (!user) return <Navigate to="/login" replace />
  if (!readOnly && !user.candidateProfileId) return <Navigate to="/profile-setup" replace />

  const withoutId = (ids: Set<string>, id: string) => {
    const next = new Set(ids)
    next.delete(id)
    return next
  }

  const handleView = () => {
    const id = candidateIdInput.trim()
    if (!id) return
    setError(null)
    setFilter('ALL')
    setSearchParams({ candidate_id: id })
  }

  const handleRemove = async (appliedId: string) => {
    if (!candidateId) return
    setConfirmingId(null)
    setError(null)
    setRemovingIds((prev) => new Set(prev).add(appliedId))
    try {
      await removeAppliedJob(candidateId, appliedId)
      setAppliedJobs((current) => current.filter((a) => a.id !== appliedId))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to remove the job from your applied list.')
    } finally {
      setRemovingIds((prev) => withoutId(prev, appliedId))
    }
  }

  // Shows the new status straight away, and puts the old one back if saving fails.
  const handleStatusChange = async (applied: AppliedJob, status: AppliedJobStatus) => {
    if (!candidateId) return
    const previous = applied
    setError(null)
    setSavedId(null)
    setAppliedJobs((current) => current.map((a) => (a.id === applied.id ? { ...a, status } : a)))
    setSavingIds((prev) => new Set(prev).add(applied.id))
    try {
      const updated = await updateAppliedJobStatus(candidateId, applied.id, status)
      setAppliedJobs((current) => current.map((a) => (a.id === updated.id ? updated : a)))
      setSavedId(updated.id)
      setTimeout(() => setSavedId((id) => (id === updated.id ? null : id)), SAVED_NOTE_MS)
    } catch (err) {
      setAppliedJobs((current) => current.map((a) => (a.id === previous.id ? previous : a)))
      setError(err instanceof ApiError ? err.message : 'Failed to update the status. Please try again.')
    } finally {
      setSavingIds((prev) => withoutId(prev, applied.id))
    }
  }

  const countFor = (status: StatusFilter) =>
    status === 'ALL' ? appliedJobs.length : appliedJobs.filter((a) => a.status === status).length
  const shown = filter === 'ALL' ? appliedJobs : appliedJobs.filter((a) => a.status === filter)
  const filterLabel = STATUSES.find((s) => s.value === filter)?.label
  const loaded = !loading && !!candidateId && !error
  const candidateName = loadedFor?.name ?? 'This candidate'

  return (
    <div className="container">
      <AppHeader
        title="Applied Jobs"
        subtitle={
          readOnly
            ? "See the jobs a candidate has applied to, and where each application stands"
            : "Jobs you've applied to, and where each application stands"
        }
        navItems={getNavItems(user)}
      />
      <div className="content">
        {readOnly && (
          <form
            className="toolbar"
            onSubmit={(e) => {
              e.preventDefault()
              handleView()
            }}
          >
            <div className="toolbar-left" style={{ alignItems: 'flex-end' }}>
              <div className="form-group" style={{ marginBottom: 0, flex: 1, minWidth: 220 }}>
                <label htmlFor="applied-candidate-id">Candidate ID</label>
                <input
                  id="applied-candidate-id"
                  type="text"
                  value={candidateIdInput}
                  onChange={(e) => setCandidateIdInput(e.target.value)}
                />
              </div>
              <button type="submit" className="btn btn-primary">
                View Applied Jobs
              </button>
            </div>
          </form>
        )}

        {error && <Alert type="error">{error}</Alert>}

        {readOnly && loaded && (
          <Alert type="info">
            Viewing applied jobs for <strong>{candidateName}</strong>. View only: the candidate updates these
            statuses. <Link to={`/job-matches?candidate_id=${candidateId}`}>See their job matches →</Link>
          </Alert>
        )}

        {readOnly && !candidateId ? (
          <div className="empty-state">
            <p>
              Enter a candidate ID, or use <strong>Applied Jobs</strong> on the{' '}
              <Link to="/candidates">Candidates list</Link>.
            </p>
          </div>
        ) : loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : !loaded ? null : appliedJobs.length === 0 ? (
          readOnly ? (
            <div className="empty-state">
              <h3>No applied jobs yet</h3>
              <p>{candidateName} hasn't marked any jobs as applied.</p>
            </div>
          ) : (
            <div className="empty-state">
              <h3>No applied jobs yet</h3>
              <p>
                Use <strong>✓ Applied</strong> on any of your <Link to="/job-matches">job matches</Link> once you've
                applied. It moves here, and a new match takes its place.
              </p>
            </div>
          )
        ) : (
          <>
            <h2 className="section-title">
              {appliedJobs.length} Applied {appliedJobs.length === 1 ? 'Job' : 'Jobs'}
            </h2>
            <div className="sort-bar" role="group" aria-label="Filter by status">
              <span className="sort-bar-label">Show:</span>
              {[{ value: 'ALL' as const, label: 'All' }, ...STATUSES].map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`sort-btn ${filter === option.value ? 'active' : ''}`}
                  aria-pressed={filter === option.value}
                  onClick={() => setFilter(option.value)}
                >
                  {option.label} ({countFor(option.value)})
                </button>
              ))}
            </div>

            {shown.length === 0 ? (
              <div className="empty-state">
                <p>No applications are marked {filterLabel} right now.</p>
              </div>
            ) : (
              <div className="match-list">
                {shown.map((applied) => {
                  const removing = removingIds.has(applied.id)
                  const saving = savingIds.has(applied.id)
                  const statusId = `status-${applied.id}`
                  return (
                    <div key={applied.id} className={`match-card ${removing ? 'dismissing' : ''}`} aria-busy={removing}>
                      <div className="match-card-header">
                        <div>
                          <h2>{applied.title ?? 'Untitled job'}</h2>
                          {applied.company_name && <div className="match-company">{applied.company_name}</div>}
                        </div>
                        <div className="match-card-actions">
                          {readOnly ? (
                            <span className={`status-select status-readonly status-${applied.status.toLowerCase()}`}>
                              {statusLabel(applied.status)}
                            </span>
                          ) : (
                            <>
                              {savedId === applied.id && (
                                <span className="status-saved" role="status">
                                  Saved
                                </span>
                              )}
                              <label htmlFor={statusId} className="visually-hidden">
                                Status for {applied.title ?? 'this job'}
                              </label>
                              <select
                                id={statusId}
                                className={`status-select status-${applied.status.toLowerCase()}`}
                                value={applied.status}
                                disabled={saving || removing}
                                onChange={(e) => handleStatusChange(applied, e.target.value as AppliedJobStatus)}
                              >
                                {STATUSES.map((s) => (
                                  <option key={s.value} value={s.value}>
                                    {s.label}
                                  </option>
                                ))}
                              </select>
                            </>
                          )}
                        </div>
                      </div>

                      <div className="applied-meta">
                        {applied.location && <span>📍 {applied.location}</span>}
                        <span>Applied {formatTimestamp(applied.applied_at)}</span>
                        {applied.status_updated_at && <span>Updated {formatTimestamp(applied.status_updated_at)}</span>}
                      </div>

                      {!readOnly && confirmingId === applied.id && !removing && (
                        <div className="dismiss-confirm applied-confirm" role="dialog" aria-label="Confirm removal">
                          <p>Remove from applied jobs? It can show up in your matches again.</p>
                          <div className="dismiss-confirm-btns">
                            <button
                              type="button"
                              className="btn btn-danger btn-sm"
                              onClick={() => handleRemove(applied.id)}
                            >
                              Remove
                            </button>
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              onClick={() => setConfirmingId(null)}
                            >
                              Keep
                            </button>
                          </div>
                        </div>
                      )}

                      <div className="match-footer">
                        <div className="match-footer-links">
                          {safeHttpUrl(applied.job_url) && (
                            <a href={safeHttpUrl(applied.job_url)} target="_blank" rel="noreferrer">
                              View Job →
                            </a>
                          )}
                          {!readOnly && (
                            <button
                              type="button"
                              className="btn btn-secondary btn-sm"
                              aria-expanded={confirmingId === applied.id}
                              disabled={removing}
                              onClick={() => setConfirmingId((id) => (id === applied.id ? null : applied.id))}
                            >
                              Undo applied
                            </button>
                          )}
                        </div>
                        {!applied.job_id && <span>Listing no longer available</span>}
                      </div>
                    </div>
                  )
                })}
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
