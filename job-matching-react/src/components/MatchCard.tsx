import { useState } from 'react'
import { formatDate, formatSalaryRange } from '../format'
import type { JobMatch } from '../types'
import { MatchBreakdownPanel } from './MatchBreakdownPanel'
import { safeHttpUrl } from '../safeUrl'

const DESCRIPTION_LIMIT = 200

interface MatchCardProps {
  job: JobMatch
  // When provided, shows a ✕ "not interested" control that asks for confirmation first.
  onDismiss?: (jobId: string) => void
  // When provided, shows an "Applied" button that asks for confirmation, then removes the
  // job from the matches and replaces it, like a dismissal.
  onApplied?: (jobId: string) => void
  // True while the dismissal (or applied) request is in flight.
  dismissing?: boolean
  // When provided, shows a "Draft email" button. Only for a candidate viewing their own
  // matches, since the MCP drafting tools act on the signed-in candidate.
  onDraftEmail?: (job: JobMatch) => void
}

export function MatchCard({ job, onDismiss, onApplied, dismissing = false, onDraftEmail }: MatchCardProps) {
  const [expanded, setExpanded] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const [confirmingApplied, setConfirmingApplied] = useState(false)
  const [showBreakdown, setShowBreakdown] = useState(false)
  const breakdownId = `breakdown-${job.id}`

  const description = job.description ?? ''
  const isLong = description.length > DESCRIPTION_LIMIT
  const shown = expanded || !isLong ? description : `${description.slice(0, DESCRIPTION_LIMIT)}…`
  const salary = formatSalaryRange(job.min_amount, job.max_amount, job.currency)

  return (
    <div className={`match-card ${dismissing ? 'dismissing' : ''}`} aria-busy={dismissing}>
      <div className="match-card-header">
        <div>
          <h2>{job.title}</h2>
          <div className="match-company">{job.company_name}</div>
        </div>
        <div className="match-card-actions">
          {job.match_breakdown ? (
            <button
              type="button"
              className="match-score match-score-toggle"
              aria-expanded={showBreakdown}
              aria-controls={breakdownId}
              title="See why this job matches"
              onClick={() => setShowBreakdown((v) => !v)}
            >
              {(job.similarity_score * 100).toFixed(1)}% Match {showBreakdown ? '▴' : '▾'}
            </button>
          ) : (
            <span className="match-score">{(job.similarity_score * 100).toFixed(1)}% Match</span>
          )}
          {onDismiss && (
            <button
              type="button"
              className="btn-dismiss"
              aria-label={`Not interested in ${job.title}`}
              title="Not interested"
              aria-expanded={confirming}
              disabled={dismissing}
              onClick={() => {
                setConfirmingApplied(false)
                setConfirming((v) => !v)
              }}
            >
              ✕
            </button>
          )}
        </div>
      </div>

      {onDismiss && confirming && !dismissing && (
        <div className="dismiss-confirm" role="dialog" aria-label="Confirm dismissal">
          <p>Remove this job and replace it?</p>
          <div className="dismiss-confirm-btns">
            <button
              type="button"
              className="btn btn-danger btn-sm"
              onClick={() => {
                setConfirming(false)
                onDismiss(job.id)
              }}
            >
              Replace
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setConfirming(false)}>
              Keep
            </button>
          </div>
        </div>
      )}

      {onApplied && confirmingApplied && !dismissing && (
        <div className="dismiss-confirm applied-confirm" role="dialog" aria-label="Confirm applied">
          <p>Mark as applied and replace it?</p>
          <div className="dismiss-confirm-btns">
            <button
              type="button"
              className="btn btn-success btn-sm"
              onClick={() => {
                setConfirmingApplied(false)
                onApplied(job.id)
              }}
            >
              Yes, applied
            </button>
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => setConfirmingApplied(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}

      {showBreakdown && job.match_breakdown && <MatchBreakdownPanel breakdown={job.match_breakdown} id={breakdownId} />}

      <div className="match-details">
        {job.location && <div>📍 {job.location}</div>}
        {job.job_type && <div>💼 {job.job_type}</div>}
        {job.is_remote && <div>🏠 Remote: Yes</div>}
        {salary && <div>💰 {salary}</div>}
      </div>

      {description && (
        <div className={`match-description ${expanded ? 'expanded' : ''}`}>
          {shown}
          {isLong && (
            <>
              {' '}
              <button type="button" className="btn-link" onClick={() => setExpanded((v) => !v)}>
                {expanded ? 'Show less' : 'Show more'}
              </button>
            </>
          )}
        </div>
      )}

      <div className="match-footer">
        <div className="match-footer-links">
          {safeHttpUrl(job.job_url) && (
            <a href={safeHttpUrl(job.job_url)} target="_blank" rel="noreferrer">
              View Job →
            </a>
          )}
          {onApplied && (
            <button
              type="button"
              className="btn btn-applied btn-sm"
              aria-expanded={confirmingApplied}
              disabled={dismissing}
              onClick={() => {
                setConfirming(false)
                setConfirmingApplied((v) => !v)
              }}
            >
              ✓ Applied
            </button>
          )}
          {onDraftEmail && (
            <button type="button" className="btn btn-secondary btn-sm" onClick={() => onDraftEmail(job)}>
              ✉ Draft email
            </button>
          )}
        </div>
        {job.date_posted && <span>Posted: {formatDate(job.date_posted)}</span>}
      </div>
    </div>
  )
}
