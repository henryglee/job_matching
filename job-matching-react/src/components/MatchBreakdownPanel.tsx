import { useAuth } from '../context/AuthContext'
import type { MatchBreakdown } from '../types'

// Same thresholds and colours as the Flask page: green 70+, amber 40+, red below.
function barClass(pct: number): string {
  return pct >= 70 ? 'bar-high' : pct >= 40 ? 'bar-mid' : 'bar-low'
}

interface BreakdownRowProps {
  label: string
  pct: number
  // Nothing to compare, so the backend's number just repeats Overall Fit; shown as n/a with
  // the reason instead of as a real score.
  notApplicable?: string
}

function BreakdownRow({ label, pct, notApplicable }: BreakdownRowProps) {
  if (notApplicable) {
    return (
      <div className="breakdown-row breakdown-row-na">
        <div className="breakdown-label">{label}</div>
        <div className="breakdown-na-reason">{notApplicable}</div>
        <div className="breakdown-pct">n/a</div>
      </div>
    )
  }
  const clamped = Math.max(0, Math.min(100, pct))
  return (
    <div className="breakdown-row">
      <div className="breakdown-label">{label}</div>
      <div
        className="breakdown-bar-wrap"
        role="meter"
        aria-label={label}
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={clamped}
      >
        <div className={`breakdown-bar ${barClass(clamped)}`} style={{ width: `${clamped}%` }} />
      </div>
      <div className="breakdown-pct">{Math.round(clamped)}%</div>
    </div>
  )
}

export function MatchBreakdownPanel({ breakdown, id }: { breakdown: MatchBreakdown; id: string }) {
  const { isUser } = useAuth()
  // Candidates can fix a gap themselves; Admin/Staff are looking at someone else's profile.
  const missing = (field: string) =>
    isUser ? `Add ${field} to your profile to compare` : `No ${field} on this profile to compare`

  return (
    <div className="match-breakdown" id={id}>
      <div className="breakdown-title">Why this match?</div>
      <BreakdownRow label="Overall Fit" pct={breakdown.overall} />
      <p className="breakdown-note">
        Overall Fit compares the whole profile with the whole posting. It isn't a sum of the checks below.
      </p>
      <BreakdownRow
        label="Skills Match"
        pct={breakdown.skills}
        notApplicable={breakdown.skills_estimated ? missing('skills') : undefined}
      />
      <BreakdownRow
        label="Title Relevance"
        pct={breakdown.title_relevance}
        notApplicable={breakdown.title_relevance_estimated ? missing('a preferred job title') : undefined}
      />
      {breakdown.matched_skills.length > 0 && (
        <div className="matched-skills">
          <strong>Matched skills:</strong>
          {breakdown.matched_skills.map((skill) => (
            <span key={skill} className="pill">
              {skill}
            </span>
          ))}
        </div>
      )}
    </div>
  )
}
