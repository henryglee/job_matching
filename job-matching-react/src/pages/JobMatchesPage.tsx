import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { dismissJob, getMatches, markJobApplied } from '../api/candidates'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { MatchCard } from '../components/MatchCard'
import { DraftEmailModal } from '../components/DraftEmailModal'
import { MatchChatPanel } from '../components/MatchChatPanel'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import { MATCH_SORTS, sortMatches, type MatchSort } from '../matchSorting'
import type { CandidateLocation, JobMatch } from '../types'

export default function JobMatchesPage() {
  const { user, isAdminOrStaff } = useAuth()
  const [searchParams] = useSearchParams()

  const [candidateId, setCandidateId] = useState(searchParams.get('candidate_id') ?? '')
  const [limit, setLimit] = useState(10)
  const [matches, setMatches] = useState<JobMatch[] | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [searched, setSearched] = useState(false)
  // The candidate the displayed matches belong to — not the ID input, which an admin can
  // edit after searching without re-running it.
  const [matchesFor, setMatchesFor] = useState<string | null>(null)
  const [dismissingIds, setDismissingIds] = useState<Set<string>>(new Set())
  const [notice, setNotice] = useState<string | null>(null)
  const [sortBy, setSortBy] = useState<MatchSort>('match')
  const [candidateLocation, setCandidateLocation] = useState<CandidateLocation>({})
  const [draftingJob, setDraftingJob] = useState<JobMatch | null>(null)

  // Only the latest search may update the page: an admin who searches candidate B while A's
  // request is still in flight would otherwise see A's slower response replace B's matches.
  const latestSearch = useRef(0)

  const runSearch = useCallback((id: string, limitValue: number) => {
    if (!id.trim()) return
    const searchId = ++latestSearch.current
    setLoading(true)
    setError(null)
    setNotice(null)
    setSearched(true)
    getMatches(id.trim(), limitValue)
      .then((res) => {
        if (searchId !== latestSearch.current) return
        setMatches(res.matches)
        setMatchesFor(id.trim())
        setCandidateLocation(res.candidate_location ?? {})
        setSortBy('match')
      })
      .catch((err) => {
        if (searchId !== latestSearch.current) return
        setMatches(null)
        setError(err instanceof ApiError ? err.message : 'Failed to load job matches.')
      })
      .finally(() => {
        if (searchId === latestSearch.current) setLoading(false)
      })
  }, [])

  // Admin/Staff: auto-run when arriving with ?candidate_id= from the Candidates List page.
  useEffect(() => {
    if (!isAdminOrStaff) return
    const fromQuery = searchParams.get('candidate_id')
    if (fromQuery) {
      setCandidateId(fromQuery)
      runSearch(fromQuery, 10)
    }
  }, [isAdminOrStaff, searchParams, runSearch])

  // User: auto-load their own matches.
  useEffect(() => {
    if (isAdminOrStaff || !user?.candidateProfileId) return
    runSearch(user.candidateProfileId, 10)
  }, [isAdminOrStaff, user?.candidateProfileId, runSearch])

  // Replacements from dismissals are appended to `matches`; sorting here slots them into
  // the right place for whichever sort is active.
  const sortedMatches = useMemo(
    () => (matches ? sortMatches(matches, sortBy, candidateLocation) : null),
    [matches, sortBy, candidateLocation],
  )

  if (!user) return null

  const handleSubmit = () => {
    runSearch(candidateId, limit)
  }

  // Dismissing and marking applied both take the job out of the list and fill its slot
  // with the next-best match; they differ only in which endpoint records it.
  const replaceJob = async (jobId: string, reason: 'dismiss' | 'applied') => {
    if (!matches || !matchesFor) return
    setError(null)
    setNotice(null)
    setDismissingIds((prev) => new Set(prev).add(jobId))
    try {
      const record = reason === 'applied' ? markJobApplied : dismissJob
      const { replacement } = await record(matchesFor, jobId, matches.length)
      setMatches((prev) => {
        if (!prev) return prev
        const remaining = prev.filter((j) => j.id !== jobId)
        // Guard against a duplicate if a concurrent dismissal already surfaced this job.
        return replacement && !remaining.some((j) => j.id === replacement.id) ? [...remaining, replacement] : remaining
      })
      const noMore = replacement ? '' : ' There are no more matches to replace it with right now.'
      if (reason === 'applied') {
        setNotice(
          isAdminOrStaff
            ? `Marked as applied.${noMore}`
            : `Marked as applied. You'll find it on your Applied Jobs page.${noMore}`,
        )
      } else if (noMore) {
        setNotice(`Job removed.${noMore}`)
      }
    } catch (err) {
      const fallback = reason === 'applied' ? 'Failed to mark job as applied.' : 'Failed to dismiss job.'
      setError(err instanceof ApiError ? err.message : fallback)
    } finally {
      setDismissingIds((prev) => {
        const next = new Set(prev)
        next.delete(jobId)
        return next
      })
    }
  }

  const handleDismiss = (jobId: string) => replaceJob(jobId, 'dismiss')
  const handleApplied = (jobId: string) => replaceJob(jobId, 'applied')

  return (
    <div className="container">
      <AppHeader title="Job Matches" subtitle="Find the best-fit jobs for a candidate" navItems={getNavItems(user)} />
      <div className="content">
        {isAdminOrStaff && (
          <div className="toolbar">
            <div className="toolbar-left" style={{ alignItems: 'flex-end' }}>
              <div className="form-group" style={{ marginBottom: 0, flex: 1, minWidth: 220 }}>
                <label htmlFor="candidate-id">Candidate ID</label>
                <input
                  id="candidate-id"
                  type="text"
                  value={candidateId}
                  onChange={(e) => setCandidateId(e.target.value)}
                />
              </div>
              <div className="form-group" style={{ marginBottom: 0, width: 160 }}>
                <label htmlFor="result-limit">Number of Results</label>
                <input
                  id="result-limit"
                  type="number"
                  min={1}
                  max={50}
                  value={limit}
                  onChange={(e) => setLimit(Math.min(50, Math.max(1, Number(e.target.value) || 1)))}
                />
              </div>
              <button type="button" className="btn btn-primary" onClick={handleSubmit}>
                Find Matches
              </button>
            </div>
          </div>
        )}

        {!isAdminOrStaff && !user.candidateProfileId && (
          <div className="empty-state">
            <h3>No profile linked</h3>
            <p>
              <Link to="/profile-setup">Set up your profile</Link> to see job matches.
            </p>
          </div>
        )}

        {error && <Alert type="error">{error}</Alert>}
        {notice && <Alert type="info">{notice}</Alert>}

        {!isAdminOrStaff && user.candidateProfileId && (
          <>
            <h2 className="section-title">Your Top {limit} Job Matches</h2>
            {/* Candidates only: the chat answers from the logged-in candidate's own matches. */}
            <MatchChatPanel />
          </>
        )}

        {loading ? (
          <div className="loading-row">
            <Spinner />
          </div>
        ) : matches && matches.length === 0 ? (
          <div className="empty-state">
            <h3>No Job Matches Found</h3>
            <p>We couldn't find any matching jobs yet.</p>
          </div>
        ) : sortedMatches && sortedMatches.length > 0 ? (
          <>
            <div className="sort-bar" role="group" aria-label="Sort matches">
              <span className="sort-bar-label">Sort by:</span>
              {MATCH_SORTS.map((option) => (
                <button
                  key={option.value}
                  type="button"
                  className={`sort-btn ${sortBy === option.value ? 'active' : ''}`}
                  aria-pressed={sortBy === option.value}
                  onClick={() => setSortBy(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <div className="match-list">
              {sortedMatches.map((job) => (
                <MatchCard
                  key={job.id}
                  job={job}
                  onDismiss={handleDismiss}
                  onApplied={handleApplied}
                  dismissing={dismissingIds.has(job.id)}
                  onDraftEmail={isAdminOrStaff ? undefined : setDraftingJob}
                />
              ))}
            </div>
          </>
        ) : isAdminOrStaff && !searched ? (
          <div className="empty-state">
            <p>Enter a candidate ID to view their job matches.</p>
          </div>
        ) : null}
      </div>

      {draftingJob && <DraftEmailModal job={draftingJob} onClose={() => setDraftingJob(null)} />}
    </div>
  )
}
