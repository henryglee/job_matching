import { api } from './client'
import type { EmailDraft } from '../types'

// The signed-in candidate's own drafts, unsent and sent, most recently updated first.
export function listDrafts() {
  return api.get<{ drafts: EmailDraft[] }>('/api/drafts')
}

// An empty recipient clears it (a cover letter rather than an email).
export function updateDraft(draftId: string, input: { recipient: string; subject: string; body: string }) {
  return api.put<EmailDraft>(`/api/drafts/${draftId}`, input)
}

// Only records that the candidate sent it from their own mail app; the server sends nothing.
export function markDraftSent(draftId: string) {
  return api.post<EmailDraft>(`/api/drafts/${draftId}/mark-sent`)
}

export function deleteDraft(draftId: string) {
  return api.delete<{ message: string }>(`/api/drafts/${draftId}`)
}

// Writes a draft with the app's own AI model and saves it for review. Takes a few seconds.
// 429 when the candidate has generated too many in the last hour, 502 if the model call fails.
export function generateDraft(jobId: string, instructions: string) {
  return api.post<EmailDraft>('/api/drafts/generate', { job_id: jobId, instructions: instructions.trim() || null })
}

// Saves a draft the candidate already has, e.g. one the chat proposed. The recipient must be
// one of the job's contact emails (or null), the same rule as drafts saved over MCP.
export function createDraft(input: { job_id: string; recipient: string | null; subject: string; body: string }) {
  return api.post<EmailDraft>('/api/drafts', input)
}
