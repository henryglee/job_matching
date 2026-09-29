# Job Matching

A job-matching app made of three parts that live in this one repository.

| Folder | What it is | Stack | Default port |
|---|---|---|---|
| [`job-matching-spring`](job-matching-spring) | Backend REST API (auth, matching, chat, email drafts) | Spring Boot, Postgres + pgvector | 8080 |
| [`job-matching-react`](job-matching-react) | Web client | React, TypeScript, Vite | 3000 |
| [`scrape-service`](scrape-service) | Job scraping microservice (wraps JobSpy) | Python, Flask | 5000 |

The React app calls the Spring API. Spring calls the scraper over HTTP (`POST /scrape`).

## Running locally

Start each part in its own terminal.

```bash
# 1. Scraper
cd scrape-service
pip install -r requirements.txt
python scrape_service.py

# 2. Backend (needs Postgres with pgvector on localhost:6543)
cd job-matching-spring
SPRING_PROFILES_ACTIVE=local JWT_SECRET=... DB_PASSWORD=... OPENAI_API_KEY=... SES_SENDER_EMAIL=... ./mvnw spring-boot:run

# 3. Frontend
cd job-matching-react
npm install
npm run dev
```

Secrets are read from environment variables. Nothing sensitive is committed. See
`job-matching-spring/src/main/resources/application.properties` for the full list.

## Docs

- [`API_DOCUMENTATION.md`](API_DOCUMENTATION.md): backend API reference
- [`REACT_IMPLEMENTATION.md`](REACT_IMPLEMENTATION.md): frontend design notes
- [`SPRINGBOOT_DB_CONFIG.md`](SPRINGBOOT_DB_CONFIG.md): database configuration
