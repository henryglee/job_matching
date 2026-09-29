import { api } from './client'
import type { ScrapeSummary } from '../types'

export interface ScrapeJobsInput {
  site_name?: string[]
  search_term?: string
  location?: string
  results_wanted?: number
}

export function scrapeJobs(input: ScrapeJobsInput) {
  return api.post<ScrapeSummary>('/api/jobs/scrape', input)
}
