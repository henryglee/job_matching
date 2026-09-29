import type { ReactNode } from 'react'
import { formatDate, formatTimestamp } from '../format'
import type { Candidate } from '../types'

// Read-only view of a candidate profile (the Flask pages' "View" / "View Profile" dialogs).
export function CandidateDetails({ candidate }: { candidate: Candidate }) {
  const location = [candidate.location_city, candidate.location_state, candidate.location_country]
    .filter((part) => part && part.trim())
    .join(', ')
  const sponsorshipTypes = candidate.requires_sponsorship ? (candidate.sponsorship_type ?? []) : []

  const rows: [string, ReactNode][] = [
    ['Name', candidate.candidate_name],
    ['Email', candidate.email || '—'],
    ['College Degree', candidate.college_degree || '—'],
    ['Graduation Date', formatDate(candidate.graduation_date)],
    ['Preferred Job Title', candidate.preferred_job_title || '—'],
    [
      'Skills',
      candidate.skills && candidate.skills.length > 0
        ? candidate.skills.map((skill) => (
            <span key={skill} className="pill">
              {skill}
            </span>
          ))
        : '—',
    ],
    ['Location', location || '—'],
    [
      'Requires Sponsorship',
      candidate.requires_sponsorship
        ? `Yes${sponsorshipTypes.length > 0 ? ` (${sponsorshipTypes.join(', ')})` : ''}`
        : 'No',
    ],
    ['Resume', candidate.resume_filename || '—'],
    ['Profile Created', formatTimestamp(candidate.date_created)],
    ['Candidate ID', <code key="id">{candidate.id}</code>],
  ]

  return (
    <dl className="details-list">
      {rows.map(([label, value]) => (
        <div key={label} className="details-row">
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  )
}
