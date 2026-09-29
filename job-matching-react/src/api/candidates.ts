import { api } from './client'
import type { AppliedJob, AppliedJobStatus, Candidate, CandidateLocation, JobMatch, UnclaimedCandidate } from '../types'

export interface CreateCandidateInput {
  candidate_name: string
  email?: string
  college_degree?: string
  graduation_date?: string
  preferred_job_title?: string
  skills?: string[]
  location_city?: string | null
  location_state?: string | null
  location_country?: string | null
  requires_sponsorship?: boolean
  sponsorship_type?: string[]
  resume?: File | null
}

export interface CreateCandidateResponse {
  message: string
  candidate_id: string
  email_sent: boolean
  // A User's new token, which includes the profile just created (see CandidateController).
  token?: string
}

export function createCandidate(input: CreateCandidateInput) {
  const formData = new FormData()
  formData.append('candidate_name', input.candidate_name)
  if (input.email) formData.append('email', input.email)
  if (input.college_degree) formData.append('college_degree', input.college_degree)
  if (input.graduation_date) formData.append('graduation_date', input.graduation_date)
  if (input.preferred_job_title) formData.append('preferred_job_title', input.preferred_job_title)
  for (const skill of input.skills ?? []) {
    formData.append('skills', skill)
  }
  if (input.location_city) formData.append('location_city', input.location_city)
  if (input.location_state) formData.append('location_state', input.location_state)
  if (input.location_country) formData.append('location_country', input.location_country)
  // The backend (like app.py) treats only the literal "true" as checked.
  if (input.requires_sponsorship) {
    formData.append('requires_sponsorship', 'true')
    for (const type of input.sponsorship_type ?? []) {
      formData.append('sponsorship_type', type)
    }
  }
  if (input.resume) {
    formData.append('resume', input.resume)
  }
  return api.postForm<CreateCandidateResponse>('/api/candidates', formData)
}

export function getCandidates() {
  return api.get<{ candidates: Candidate[] }>('/api/candidates')
}

export function getCandidate(candidateId: string) {
  return api.get<Candidate>(`/api/candidates/${candidateId}`)
}

export interface UpdateCandidateInput {
  candidate_name: string
  email?: string | null
  college_degree?: string | null
  graduation_date?: string | null
  preferred_job_title?: string | null
  skills?: string[]
  location_city?: string | null
  location_state?: string | null
  location_country?: string | null
  requires_sponsorship?: boolean
  sponsorship_type?: string[]
}

export function updateCandidate(candidateId: string, input: UpdateCandidateInput) {
  return api.put<{ message: string }>(`/api/candidates/${candidateId}`, input)
}

export function deleteCandidate(candidateId: string) {
  return api.delete<{ message: string }>(`/api/candidates/${candidateId}`)
}

export function getUnclaimedCandidates() {
  return api.get<{ candidates: UnclaimedCandidate[] }>('/api/candidates/unclaimed')
}

export function claimProfile(candidateId: string) {
  return api.post<{ candidate_id: string; message: string; token?: string }>('/api/user/claim-profile', {
    candidate_id: candidateId,
  })
}

export function sendMatches(candidateId: string) {
  return api.post<{ message: string }>(`/api/candidates/${candidateId}/send-matches`)
}

// Records "not interested" for a job and returns the next-best match to fill its slot
// (null when there are no more). currentCount = matches shown before the dismissal.
export function dismissJob(candidateId: string, jobId: string, currentCount: number) {
  return api.post<{ replacement: JobMatch | null }>(`/api/candidates/${candidateId}/jobs/${jobId}/dismiss`, {
    current_count: currentCount,
  })
}

// Marks a job as applied to, which takes it out of the matches like a dismissal, and
// returns the next-best match to fill its slot the same way.
export function markJobApplied(candidateId: string, jobId: string, currentCount: number) {
  return api.post<{ replacement: JobMatch | null }>(`/api/candidates/${candidateId}/jobs/${jobId}/applied`, {
    current_count: currentCount,
  })
}

// The candidate's applied jobs, most recently applied first.
export function getAppliedJobs(candidateId: string) {
  return api.get<{ applied_jobs: AppliedJob[] }>(`/api/candidates/${candidateId}/applied-jobs`)
}

// Returns the updated entry, with its new status_updated_at.
export function updateAppliedJobStatus(candidateId: string, appliedId: string, status: AppliedJobStatus) {
  return api.put<AppliedJob>(`/api/candidates/${candidateId}/applied-jobs/${appliedId}/status`, { status })
}

// Takes a job back off the applied list (appliedId is the entry's id, not the job's); the
// job can then show up in the candidate's matches again.
export function removeAppliedJob(candidateId: string, appliedId: string) {
  return api.delete<{ message: string }>(`/api/candidates/${candidateId}/applied-jobs/${appliedId}`)
}

export function getMatches(candidateId: string, limit: number) {
  return api.get<{
    candidate_id: string
    matches: JobMatch[]
    total_matches: number
    candidate_location: CandidateLocation
  }>(
    `/api/candidates/${candidateId}/matches?limit=${limit}`,
  )
}
