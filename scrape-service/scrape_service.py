"""
Standalone job-scraping microservice for the Beshton Job Matching Platform.

This exists because the platform's backend is being migrated to Java Spring Boot
(job-matching-spring), but the jobspy/ scraping library (LinkedIn, Indeed, Glassdoor,
ZipRecruiter, Google, Bayt, Naukri) is Python-only and has no Java equivalent. Rather
than reimplement every site scraper, Spring Boot calls this thin service over HTTP.

Responsibility split:
- This service: run scrape_jobs() and hand back raw results as JSON. Nothing else.
- Spring Boot (JobListingService / EmbeddingService): dedup by (job_url, site),
  persistence, and OpenAI embedding generation. Those used to happen in the original
  Flask app's app.py + jobspy/database.py (see the JobSpy-main repo); they do not
  happen here anymore, to avoid the two backends racing to write the same rows.

The jobspy/ package alongside this file is a trimmed copy of JobSpy-main's scraping
engine — everything except database.py and email_service.py, which pull in
sqlalchemy/openai/boto3 this microservice has no use for. It is a copy, not a shared
dependency: if JobSpy-main's scrapers get bugfixes (e.g. site anti-bot changes), re-sync
this jobspy/ directory manually (excluding database.py/email_service.py again).

Contract expected by com.jobmatching.demo.scrape.HttpJobScrapeClient:
    POST /scrape
    body:     {"site_name": [...], "search_term": "...", "location": "...", "results_wanted": N}
    response: {"jobs": [ {...same field names as the job_listings columns...} ]}
    (all body fields optional; defaults match app.py's old /api/jobs/scrape route)

    POST /indeed/expired
    body:     {"job_keys": ["<jk from an Indeed job_url>", ...]}
    response: {"expired": ["<the keys no longer accepting applications>", ...]}

Run:
    python scrape_service.py                                   # dev server, port 5000
    gunicorn -w 2 -b 0.0.0.0:5000 scrape_service:app            # production

Runs on the same port (5000) app.py currently uses, since it's meant to take over
app.py's scraping role once Spring Boot replaces app.py for everything else — see
jobspy.service.url in job-matching-spring's application.properties.
"""

import datetime
import json
import logging
import math

import requests
from flask import Flask, jsonify, request

from jobspy import scrape_jobs
from jobspy.indeed.constant import api_headers as indeed_api_headers

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = Flask(__name__)

# The same columns jobspy/database.py's save_jobs() maps onto job_listings — as JSON key
# -> DataFrame column, since scrape_jobs()'s output DataFrame doesn't use identical names
# throughout. Listed explicitly (rather than dumping every DataFrame column) so a new
# jobspy column doesn't silently start flowing into a table that isn't expecting it.
#
# NOTE: jobspy/__init__.py renames "company_name" to "company" when it assembles the
# final DataFrame (see desired_order in jobspy/util.py, and the `job_data["company"] =
# job_data["company_name"]` line in jobspy/__init__.py). jobspy/database.py's save_jobs()
# does `if field in job.index` per field and has never accounted for this rename, so
# company_name has apparently been silently left NULL for every job the existing Flask
# app has ever scraped — worth checking existing job_listings data for this. Handled
# correctly here via the explicit "company" source mapping below.
JOB_LISTING_FIELD_TO_COLUMN = {
    "title": "title",
    "company_name": "company",
    "job_url": "job_url",
    "job_url_direct": "job_url_direct",
    "location": "location",
    "description": "description",
    "company_url": "company_url",
    "company_url_direct": "company_url_direct",
    "company_addresses": "company_addresses",
    "company_num_employees": "company_num_employees",
    "company_revenue": "company_revenue",
    "company_description": "company_description",
    "company_logo": "company_logo",
    "company_industry": "company_industry",
    "company_rating": "company_rating",
    "company_reviews_count": "company_reviews_count",
    "job_type": "job_type",
    "job_level": "job_level",
    "job_function": "job_function",
    "is_remote": "is_remote",
    "listing_type": "listing_type",
    "interval": "interval",
    "min_amount": "min_amount",
    "max_amount": "max_amount",
    "currency": "currency",
    "salary_source": "salary_source",
    "date_posted": "date_posted",
    "site": "site",
    "emails": "emails",
    "banner_photo_url": "banner_photo_url",
    "skills": "skills",
    "experience_range": "experience_range",
    "vacancy_count": "vacancy_count",
    "work_from_home_type": "work_from_home_type",
    "job_metadata": "job_metadata",
}


def _json_safe(value):
    """
    NaN/NaT -> None, datetimes -> ISO strings, everything else passes through.

    date_posted in particular comes back from jobspy as a bare `date` (no time
    component), which serializes to e.g. "2026-09-15" via plain .isoformat() — but the
    Spring Boot side maps date_posted to java.time.LocalDateTime (the actual Postgres
    column is TIMESTAMP), and Jackson's default LocalDateTime deserializer rejects a
    date-only string. Pad it to midnight so it round-trips as a full ISO datetime.
    """
    if value is None:
        return None
    if isinstance(value, float) and math.isnan(value):
        return None
    if isinstance(value, datetime.datetime):
        return value.isoformat()
    if isinstance(value, datetime.date):
        return datetime.datetime.combine(value, datetime.time.min).isoformat()
    if hasattr(value, "isoformat"):
        return value.isoformat()
    return value


@app.route("/scrape", methods=["POST"])
def scrape():
    data = request.get_json(silent=True) or {}
    site_name = data.get("site_name") or ["indeed", "google"]
    search_term = data.get("search_term") or "software engineer"
    location = data.get("location") or "United States"
    results_wanted = data.get("results_wanted") or 50

    logger.info(
        "Scraping site_name=%s search_term=%r location=%r results_wanted=%s",
        site_name, search_term, location, results_wanted,
    )

    try:
        jobs_df = scrape_jobs(
            site_name=site_name,
            search_term=search_term,
            location=location,
            results_wanted=results_wanted,
            hours_old=24,
        )
    except Exception as e:
        logger.exception("Scrape failed")
        return jsonify({"error": f"Scrape failed: {e}"}), 500

    if jobs_df is None or jobs_df.empty:
        return jsonify({"jobs": []}), 200

    jobs_df = jobs_df.where(jobs_df.notna(), None)
    jobs = [
        {
            field: _json_safe(row[source_column])
            for field, source_column in JOB_LISTING_FIELD_TO_COLUMN.items()
            if source_column in jobs_df.columns
        }
        for _, row in jobs_df.iterrows()
    ]

    logger.info("Scraped %d jobs", len(jobs))
    return jsonify({"jobs": jobs}), 200


# Same GraphQL API and app headers jobspy's Indeed scraper searches with. Its jobData query
# looks jobs up by key and says whether each has expired; a key it doesn't return at all is
# a job Indeed has removed. Checked live: 100 keys per call take well under a second.
INDEED_API_URL = "https://apis.indeed.com/graphql"
INDEED_KEYS_PER_CALL = 100


def _expired_indeed_keys(job_keys):
    headers = indeed_api_headers.copy()
    headers["indeed-co"] = "US"
    expired = []
    for i in range(0, len(job_keys), INDEED_KEYS_PER_CALL):
        batch = job_keys[i:i + INDEED_KEYS_PER_CALL]
        query = "query { jobData(jobKeys: %s) { results { job { key expired } } } }" % json.dumps(batch)
        response = requests.post(INDEED_API_URL, headers=headers, json={"query": query}, timeout=15)
        response.raise_for_status()
        body = response.json()
        results = ((body.get("data") or {}).get("jobData") or {}).get("results")
        if results is None:
            # A GraphQL error, not an answer: don't report the whole batch as expired.
            raise RuntimeError(f"Unexpected Indeed response: {str(body)[:300]}")
        live = {r["job"]["key"] for r in results if r.get("job") and not r["job"].get("expired")}
        expired.extend(k for k in batch if k not in live)
    return expired


@app.route("/indeed/expired", methods=["POST"])
def indeed_expired():
    data = request.get_json(silent=True) or {}
    job_keys = [k for k in dict.fromkeys(data.get("job_keys") or []) if isinstance(k, str) and k]
    if not job_keys:
        return jsonify({"expired": []}), 200
    try:
        expired = _expired_indeed_keys(job_keys)
    except Exception as e:
        logger.exception("Indeed expiry check failed")
        return jsonify({"error": f"Indeed expiry check failed: {e}"}), 502
    logger.info("Checked %d Indeed jobs, %d expired", len(job_keys), len(expired))
    return jsonify({"expired": expired}), 200


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=False)
