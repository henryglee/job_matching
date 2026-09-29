# Beshton Job Matching — React Client

React + TypeScript + Vite client for the [job-matching-spring](../job-matching-spring) backend, built from [`REACT_IMPLEMENTATION.md`](../REACT_IMPLEMENTATION.md) and cross-checked against the actual Spring Boot controllers.

## Running

```bash
npm install
npm run dev
```

The dev server listens on `:3000` and proxies `/api`, `/login`, `/register`, `/logout`, `/forgot-password` and `/reset-password/*` to the Spring Boot backend (`http://localhost:8080` by default — override with `VITE_BACKEND_URL`). This keeps dev requests same-origin so no env config is needed locally.

Start the backend separately (needs Postgres+pgvector, `JWT_SECRET`, `DB_PASSWORD`, `OPENAI_API_KEY`, `SES_SENDER_EMAIL` — see `job-matching-spring/src/main/resources/application.properties`):

```bash
cd ../job-matching-spring
SPRING_PROFILES_ACTIVE=local JWT_SECRET=... DB_PASSWORD=... OPENAI_API_KEY=... SES_SENDER_EMAIL=... ./mvnw spring-boot:run
```

### Production build

There's no dev proxy outside `vite dev`, so a built app needs to talk to the backend directly — which is why the backend now has `SecurityConfig.corsConfigurationSource()`, driven by `app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS` (defaults to `http://localhost:3000`). Point the build at the deployed backend and make sure that origin is in the backend's allow-list:

```bash
VITE_API_BASE_URL=https://api.example.com npm run build
```

See `.env.example`. The CORS policy allows `GET/POST/PUT/DELETE/OPTIONS` with `Authorization`/`Content-Type` headers and no credentials (the JWT travels in the `Authorization` header, not a cookie).

## Notes on the backend contract

- Every JSON request/response body is **snake_case** (the backend sets `spring.jackson.property-naming-strategy=SNAKE_CASE` globally), *except* the decoded JWT payload, whose claims (`activeRole`, `candidateProfileId`) are hardcoded camelCase literals in `JwtService` and bypass that setting.
- `/register` requires `first_name`/`last_name` in addition to the fields listed in `REACT_IMPLEMENTATION.md`/`API_DOCUMENTATION.md`.
- Errors always come back as `{ "error": "message" }`.

## Structure

Mirrors the layout suggested in `REACT_IMPLEMENTATION.md`: `src/api` (fetch wrappers), `src/context` (auth), `src/components` (shared UI), `src/pages` (one per route), plus `src/nav.ts` and `src/routing.ts` for the role-based nav/redirect rules.
