-- Jobs a candidate has marked as applied to from the Job Matches page, listed on their
-- Applied Jobs page. Like candidate_dismissed_jobs, these are left out of the candidate's
-- matches (and a replacement takes their place). Each row keeps its own copy of the job's
-- title/company/location/link, and job_id is set to null rather than the row deleted when the
-- retention cleanup removes the job, so the candidate's applied history outlives the listing.
-- Hibernate runs with ddl-auto=validate, so this table has to exist before the app starts.
-- Safe to re-run, including on a database that has the earlier version of this table. Run:
--   psql -h localhost -p 6543 -U henry -d talent_matching -f candidate_applied_jobs.sql
CREATE TABLE IF NOT EXISTS candidate_applied_jobs (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id  uuid NOT NULL REFERENCES candidate_info(id) ON DELETE CASCADE,
    job_id        uuid,
    job_title     varchar(500),
    company_name  varchar(500),
    location      varchar(1000),
    job_url       text,
    applied_at    timestamp NOT NULL DEFAULT now(),
    status        varchar(16) NOT NULL DEFAULT 'APPLIED',   -- APPLIED | INTERVIEWING | WAITING | OFFER | REJECTED
    status_updated_at timestamp,                           -- null until the status is first changed
    UNIQUE (candidate_id, job_id)
);

-- Upgrades earlier versions of the table (job_id NOT NULL and cascading, no job details, no status).
ALTER TABLE candidate_applied_jobs ADD COLUMN IF NOT EXISTS job_title varchar(500);
ALTER TABLE candidate_applied_jobs ADD COLUMN IF NOT EXISTS company_name varchar(500);
ALTER TABLE candidate_applied_jobs ADD COLUMN IF NOT EXISTS location varchar(1000);
ALTER TABLE candidate_applied_jobs ADD COLUMN IF NOT EXISTS job_url text;
ALTER TABLE candidate_applied_jobs ADD COLUMN IF NOT EXISTS status varchar(16) NOT NULL DEFAULT 'APPLIED';
ALTER TABLE candidate_applied_jobs ADD COLUMN IF NOT EXISTS status_updated_at timestamp;
ALTER TABLE candidate_applied_jobs ALTER COLUMN job_id DROP NOT NULL;
ALTER TABLE candidate_applied_jobs DROP CONSTRAINT IF EXISTS candidate_applied_jobs_job_id_fkey;
ALTER TABLE candidate_applied_jobs ADD CONSTRAINT candidate_applied_jobs_job_id_fkey
    FOREIGN KEY (job_id) REFERENCES job_listings(id) ON DELETE SET NULL;

-- Fills in job details for rows saved before those columns existed.
UPDATE candidate_applied_jobs a
SET job_title = j.title, company_name = j.company_name, location = j.location,
    job_url = COALESCE(NULLIF(j.job_url_direct, ''), j.job_url)
FROM job_listings j
WHERE a.job_id = j.id AND a.job_title IS NULL;

CREATE INDEX IF NOT EXISTS idx_candidate_applied_jobs_candidate ON candidate_applied_jobs(candidate_id);
