export type RoleName = 'Admin' | 'Staff' | 'User'

// Decoded JWT payload. Claims are written with literal camelCase keys in JwtService
// (Jwts.builder().claim("activeRole", ...)) so they bypass the backend's global
// snake_case Jackson naming strategy that every other response body uses.
export interface JwtPayload {
  sub: string
  email: string
  username: string
  activeRole: RoleName
  candidateProfileId?: string
  iat: number
  exp: number
}

export interface AuthUser {
  id: string
  email: string
  username: string
  activeRole: RoleName
  candidateProfileId: string | null
}

export interface Role {
  role_id: string
  role_name: string
}

export interface Candidate {
  id: string
  candidate_name: string
  email: string | null
  college_degree: string | null
  graduation_date: string | null
  preferred_job_title: string | null
  skills: string[] | null
  location_city: string | null
  location_state: string | null
  location_country: string | null
  requires_sponsorship: boolean | null
  sponsorship_type: string[] | null
  resume_filename: string | null
  date_created?: string
  date_updated?: string
  // The user account linked to this profile (all null when none is linked).
  linked_user_id: string | null
  account_first_name: string | null
  account_last_name: string | null
}

export interface UnclaimedCandidate {
  id: string
  candidate_name: string
  email: string | null
  preferred_job_title: string | null
  college_degree: string | null
}

export interface JobMatch {
  id: string
  title: string
  company_name: string | null
  location: string | null
  job_url: string | null
  job_url_direct?: string | null
  description: string | null
  job_type: string | null
  is_remote: boolean | null
  min_amount: number | null
  max_amount: number | null
  currency: string | null
  date_posted: string | null
  site: string | null
  experience_range?: string | null
  similarity_score: number
  match_breakdown?: MatchBreakdown
}

// Percentages (0-100) explaining a match score, from the backend's matcher (MatchScorer).
// overall is embedding similarity, not a sum of the others. A part flagged *_estimated had
// nothing to compare (e.g. no skills on the profile), so its number just repeats overall.
export interface MatchBreakdown {
  overall: number
  skills: number
  title_relevance: number
  location: number
  matched_skills: string[]
  skills_estimated?: boolean
  title_relevance_estimated?: boolean
  location_estimated?: boolean
}

// The candidate's own location, sent with their matches ("" for unset parts).
export interface CandidateLocation {
  city?: string
  state?: string
  country?: string
}

// Where an application stands, set from the Applied Jobs page's dropdown. WAITING is after
// the interviews, before hearing back with an offer or a rejection.
export type AppliedJobStatus = 'APPLIED' | 'INTERVIEWING' | 'WAITING' | 'OFFER' | 'REJECTED'

// A job the candidate marked as applied to on the Job Matches page. job_id is null once the
// listing has been cleaned up; the other job details were copied when it was marked.
export interface AppliedJob {
  id: string
  job_id: string | null
  title: string | null
  company_name: string | null
  location: string | null
  job_url: string | null
  applied_at: string
  status: AppliedJobStatus
  // When the status was last changed; null while it's still the initial APPLIED.
  status_updated_at: string | null
}

// An application email drafted by an AI client over MCP, for the candidate to review.
// job_title/company_name/job_url are null if the job has since been deleted.
export interface EmailDraft {
  id: string
  job_id: string
  job_title: string | null
  company_name: string | null
  job_url: string | null
  recipient: string | null
  subject: string
  body: string
  status: 'DRAFT' | 'SENT'
  created_at: string
  updated_at: string
  sent_at: string | null
}

// "Ask about your matches" chat (backend MatchChatService). The panel keeps the conversation
// and sends it with each message; nothing is stored server-side.
export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

// An email the chat proposed, shown as a card. Nothing is saved until the candidate clicks
// Save (POST /api/drafts). recipient comes from the job's contact emails, never the model.
export interface DraftProposal {
  job_id: string
  job_title: string | null
  company_name: string | null
  recipient: string | null
  subject: string
  body: string
}

export interface ChatReply {
  reply: string
  draft_proposals: DraftProposal[]
}

// Admin "AI Usage" page (backend AiUsageMonitor): counts since the server started, never
// message content.
export interface AiUsage {
  chat_messages: number
  drafts_generated: number
  what_if_scores: number
  model_tokens: number
  moderation_blocked: number
  moderation_unavailable: number
  limit_hits: number
  cut_offs: number
  failures: number
}

export interface CandidateAiUsage {
  candidate_id: string
  candidate_name: string | null
  usage: AiUsage
  last_activity: string | null
  // Blocked by moderation or hit an hourly limit at least once.
  needs_review: boolean
}

export interface AiUsageReport {
  since: string
  totals: AiUsage
  candidates: CandidateAiUsage[]
}

export interface AdminUser {
  id: string
  username: string
  email: string
  candidate_profile_id: string | null
  roles: string[]
}

export interface ScrapeSummary {
  message: string
  jobs_scraped: number
  jobs_saved: number
  embeddings_generated: number
  sites: string[]
}
