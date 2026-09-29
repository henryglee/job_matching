-- When a job was found to no longer accept applications (see scrape/JobExpiryService). Expired
-- jobs are left in place rather than deleted, since deleting one would cascade to the
-- candidates' email drafts for it; matching and the job listings skip them, and the nightly
-- cleanup deletes them a week later (app.cleanup.expired-days-old) unless one has a draft,
-- which waits for the usual 30-day cleanup. Hibernate runs with ddl-auto=validate, so this column has to
-- exist before the app starts. Run once per database:
--   psql -h localhost -p 6543 -U henry -d talent_matching -f job_listings_expired_at.sql
ALTER TABLE job_listings ADD COLUMN IF NOT EXISTS expired_at timestamp;
