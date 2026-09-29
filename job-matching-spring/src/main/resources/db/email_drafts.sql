-- Application email drafts written by an AI client over MCP (see mcp/EmailDraftTools) and
-- reviewed by the candidate on the React "Email Drafts" page. Hibernate runs with
-- ddl-auto=validate, so this table has to exist before the app starts. Run once per database:
--   psql -h localhost -p 6543 -U henry -d talent_matching -f email_drafts.sql
CREATE TABLE IF NOT EXISTS email_drafts (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    candidate_id  uuid NOT NULL REFERENCES candidate_info(id) ON DELETE CASCADE,
    job_id        uuid NOT NULL REFERENCES job_listings(id) ON DELETE CASCADE,
    recipient     varchar(320),                   -- null when the job has no contact email
    subject       varchar(300) NOT NULL,
    body          text NOT NULL,
    status        varchar(16) NOT NULL DEFAULT 'DRAFT',   -- DRAFT | SENT
    created_at    timestamp NOT NULL DEFAULT now(),
    updated_at    timestamp NOT NULL DEFAULT now(),
    sent_at       timestamp
);

CREATE INDEX IF NOT EXISTS idx_email_drafts_candidate ON email_drafts(candidate_id);
