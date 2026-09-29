import { useCallback, useEffect, useState } from 'react'
import * as adminApi from '../api/admin'
import { ApiError } from '../api/client'
import { AppHeader } from '../components/AppHeader'
import { Alert } from '../components/Alert'
import { Spinner } from '../components/Spinner'
import { useAuth } from '../context/AuthContext'
import { getNavItems } from '../nav'
import type { AiUsage, AiUsageReport } from '../types'

function formatTime(value: string | null): string {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString()
}

const TOTALS: { key: keyof AiUsage; label: string }[] = [
  { key: 'chat_messages', label: 'Chat messages' },
  { key: 'drafts_generated', label: 'Drafts generated' },
  { key: 'what_if_scores', label: 'What-if scores' },
  { key: 'model_tokens', label: 'Model tokens' },
  { key: 'moderation_blocked', label: 'Blocked by moderation' },
  { key: 'limit_hits', label: 'Hourly limit hits' },
  { key: 'failures', label: 'Failures' },
  { key: 'cut_offs', label: 'Answers cut off' },
]

// Admin view of how candidates use the AI features (chat, Generate draft, what-if), from the
// backend's AiUsageMonitor: counts and tokens only, never what anyone typed. Rows flagged
// "Review" were blocked by moderation or hit an hourly limit at least once.
export default function AdminAiUsagePage() {
  const { user } = useAuth()
  const [report, setReport] = useState<AiUsageReport | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetchReport = useCallback(() => {
    adminApi
      .getAiUsage()
      .then((res) => {
        setReport(res)
        setError(null)
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Failed to load AI usage.'))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    fetchReport()
  }, [fetchReport])

  const refresh = () => {
    setLoading(true)
    fetchReport()
  }

  return (
    <div className="container">
      <AppHeader title="AI Usage" subtitle="How candidates use the chat, draft generation and what-if scores" navItems={getNavItems(user)} />
      <div className="content">
        {error && <Alert type="error">{error}</Alert>}

        <div className="usage-toolbar">
          <p className="usage-note">
            {report ? `Since ${formatTime(report.since)}. ` : ''}Counts reset when the server restarts. No message
            content is stored.
          </p>
          <button type="button" className="btn btn-secondary btn-sm" disabled={loading} onClick={refresh}>
            {loading ? <Spinner small /> : 'Refresh'}
          </button>
        </div>

        {report && report.totals.moderation_unavailable > 0 && (
          <Alert type="info">
            The moderation check was unavailable {report.totals.moderation_unavailable} time(s); those messages went
            through unscreened.
          </Alert>
        )}

        {report && (
          <>
            <div className="usage-totals">
              {TOTALS.map(({ key, label }) => (
                <div key={key} className="usage-stat">
                  <div className="usage-stat-value">{report.totals[key].toLocaleString()}</div>
                  <div className="usage-stat-label">{label}</div>
                </div>
              ))}
            </div>

            {report.candidates.length === 0 ? (
              <div className="empty-state">
                <h3>No AI usage yet</h3>
                <p>Candidates' chat messages, generated drafts and what-if scores will show up here.</p>
              </div>
            ) : (
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Candidate</th>
                      <th>Chat</th>
                      <th>Drafts</th>
                      <th>What-if</th>
                      <th>Tokens</th>
                      <th>Blocked</th>
                      <th>Limit hits</th>
                      <th>Failures</th>
                      <th>Last active</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.candidates.map((c) => (
                      <tr key={c.candidate_id} className={c.needs_review ? 'usage-review' : ''}>
                        <td>
                          {c.candidate_name ?? <code>{c.candidate_id.slice(0, 8)}</code>}
                          {c.needs_review && <span className="usage-badge">Review</span>}
                        </td>
                        <td>{c.usage.chat_messages}</td>
                        <td>{c.usage.drafts_generated}</td>
                        <td>{c.usage.what_if_scores}</td>
                        <td>{c.usage.model_tokens.toLocaleString()}</td>
                        <td>{c.usage.moderation_blocked}</td>
                        <td>{c.usage.limit_hits}</td>
                        <td>{c.usage.failures}</td>
                        <td>{formatTime(c.last_activity)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
