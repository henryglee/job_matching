# React Implementation Guide — Beshton Job Matching Platform

This document describes the full client-side behavior of every page in the current Jinja2/HTML implementation so it can be faithfully re-built in React.

---

## Table of Contents

1. [Application Overview](#application-overview)
2. [Routing & Navigation](#routing--navigation)
3. [Auth Context & Global State](#auth-context--global-state)
4. [Design System](#design-system)
5. [Shared Components](#shared-components)
6. [Pages](#pages)
   - [Login](#login-page)
   - [Register](#register-page)
   - [Forgot / Reset Password](#forgot--reset-password-pages)
   - [Profile Setup](#profile-setup-page)
   - [Claim Profile](#claim-profile-page)
   - [Home Dashboard](#home-dashboard-page)
   - [Candidate Form (Add / Create)](#candidate-form-page)
   - [Candidates List](#candidates-list-page)
   - [Edit Profile](#edit-profile-page)
   - [Job Matches](#job-matches-page)
   - [Admin — Users](#admin--users-page)
   - [Admin — Roles](#admin--roles-page)
7. [API Reference Summary](#api-reference-summary)

---

## Application Overview

The app has three distinct user experiences depending on the active role the user selected at login:

| Role | Landing page after login | Primary workflow |
|---|---|---|
| `Admin` | Home Dashboard | Manage users, roles, candidates, trigger scrapes |
| `Staff` | Home Dashboard | Manage candidates, send match emails |
| `User` | Job Matches (if profile linked) or Profile Setup | View own job matches, edit own profile |

---

## Routing & Navigation

Suggested React Router v6 route structure:

```
/login                   → LoginPage          (public)
/register                → RegisterPage       (public)
/forgot-password         → ForgotPasswordPage (public)
/reset-password/:token   → ResetPasswordPage  (public)

/                        → HomeDashboard      (Admin, Staff only)
/apply                   → CandidateFormPage  (all roles)
/candidates              → CandidatesListPage (Admin, Staff only)
/edit-profile            → EditProfilePage    (User only)
/job-matches             → JobMatchesPage     (all roles)
/profile-setup           → ProfileSetupPage   (User, no linked profile)
/claim-profile           → ClaimProfilePage   (User, no linked profile)
/admin                   → AdminUsersPage     (Admin only)
/admin/roles             → AdminRolesPage     (Admin only)
```

**Route guards:**
- Unauthenticated users → redirect to `/login`
- `User` role visiting `/candidates` or `/admin*` → redirect or render a `403` page
- `User` with a linked profile visiting `/profile-setup` or `/claim-profile` → redirect to `/job-matches`
- `Admin`/`Staff` visiting `/profile-setup` → redirect to `/`
- `User` visiting `/apply` when they already have a linked profile → redirect to `/edit-profile`

---

## Auth Context & Global State

Store the following in a top-level React context (`AuthContext`) after a successful login:

```ts
interface AuthUser {
  id: string;           // UUID
  email: string;
  username: string;
  activeRole: 'Admin' | 'Staff' | 'User' | null;
  candidateProfileId: string | null;   // UUID or null
}
```

**Helper booleans to derive from context (used throughout the app):**

```ts
const isAdminOrStaff = ['Admin', 'Staff'].includes(activeRole);
const isUser = activeRole === 'User';
const isAdmin = activeRole === 'Admin';
const hasProfile = !!candidateProfileId;
```

**Persistence:** Store the JWT token in `localStorage` or an `httpOnly` cookie. Decode the payload to restore `AuthUser` on page refresh.

---

## Design System

### Color Palette

| Token | Value | Usage |
|---|---|---|
| `brand-gradient` | `linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)` | Page headers, primary buttons |
| `page-bg-gradient` | `linear-gradient(135deg, #667eea 0%, #764ba2 100%)` | Body background on all pages |
| `green-gradient` | `linear-gradient(135deg, #28a745 0%, #20c997 100%)` | "Candidates" card accent, similarity score badges |
| `purple-gradient` | `linear-gradient(135deg, #667eea 0%, #764ba2 100%)` | "Job Matches" card accent |
| `border-default` | `#e1e5eb` | Input borders, card borders |
| `text-primary` | `#333` | Headings |
| `text-secondary` | `#666` | Body text, labels |
| `error-bg` | `#f8d7da` / text `#721c24` | Error alerts |
| `success-bg` | `#d4edda` / text `#155724` | Success alerts |
| `info-bg` | `#f0f8ff` / text `#1a6fa8` | Info alerts |

### Typography

- Font family: `'Segoe UI', Tahoma, Geneva, Verdana, sans-serif`
- Page title (h1 in header): `2.5rem`, bold
- Section title: `1.6rem`
- Card h2: `1.4rem`
- Body: `1rem` / `0.95rem`
- Small/label: `0.9rem`

### Layout

Every page uses the same outer shell:
- `body` background: `page-bg-gradient`, `min-height: 100vh`, `padding: 20px`
- `.container`: `background: white`, `border-radius: 15px`, `box-shadow: 0 20px 40px rgba(0,0,0,0.1)`, `max-width` varies per page (800px form, 1200px list views)
- `.header`: `brand-gradient` background, white text, centered, contains the page title + `<nav>`
- `.content` / `.form-container`: `padding: 40px`

---

## Shared Components

### `<AppHeader>`

Rendered at the top of every authenticated page. Contains:
- Page title and subtitle
- `<NavBar>` — role-conditional links (see per-page nav details below)
- `<UserBadge>` — top-right corner, shows `username (activeRole)` + logout link

**UserBadge behavior:**
- Positioned absolute top-right inside the header div
- Shows: `"henry (Admin)"` style label in a semi-transparent pill
- Logout link calls `POST /logout` (or clears JWT and redirects to `/login` in React)

### `<NavBar>`

Pill-style horizontal nav rendered inside the header. Active link gets `background: rgba(255,255,255,0.25)`. All links are `border: 1px solid rgba(255,255,255,0.3)`, white text.

### `<Alert>`

Reusable component for error/success/info messages.

```tsx
<Alert type="error" | "success" | "info">message</Alert>
```

### `<Spinner>`

Circular CSS spinner (`border-top` animated, 40px). Used inside loading states.

### `<SkillPicker>`

Used on `CandidateFormPage` and `EditProfilePage`. See [CandidateFormPage](#candidate-form-page) for full behavior.

---

## Pages

---

### Login Page

**Route:** `/login`  
**Layout:** Centered card (`max-width: 420px`), no page-bg container — the card itself floats on the gradient background.

#### Two-Step Flow

The login form has two sequential steps rendered in the same card:

**Step 1 — Credentials**
- Fields: `login` (email or username), `password`
- Button: "Continue" (triggers `checkRoles()`, does NOT submit the form)
- On Enter key in password field → triggers Continue

**Step 2 — Role Selection** (shown only if user has more than 1 role)
- Animated fade-in (`opacity 0 → 1, translateY(-6px) → 0`)
- A `<select>` populated with the user's role names
- A hidden `<input name="role">` keeps the selected value in sync with the select
- "Log In" button submits the full form
- "Back" button returns to Step 1

**`checkRoles()` logic:**
1. Validate both fields are filled; show inline error if not.
2. Disable the Continue button and show a spinner inside it.
3. `POST /api/auth/check-roles` with `{ login, password }`.
4. On `401` → show error "Invalid credentials."
5. On success:
   - If `roles.length <= 1` → set the hidden role field and immediately submit the form (skip Step 2).
   - If `roles.length > 1` → populate the select and animate to Step 2.

**Post-login redirect (handled server-side in current app; handle in React after JWT decode):**
- `User` + `hasProfile` → `/job-matches`
- `User` + no profile → `/profile-setup`
- `Admin`/`Staff` → `/`

---

### Register Page

**Route:** `/register`

**Fields:**
| Field | Type | Validation |
|---|---|---|
| Email | text/email | Required |
| Username | text | Required |
| Password | password | Required, min 8 chars |
| Confirm Password | password | Must match password |

**Behavior:** Standard form POST. On success → redirect to `/profile-setup`. All validation currently server-side; add client-side length/match checks in React.

---

### Forgot / Reset Password Pages

**Routes:** `/forgot-password`, `/reset-password/:token`

**Forgot Password:**
- Single email field.
- On submit: always show "If that email is registered, a reset link has been sent." regardless of whether the email exists (prevents enumeration).

**Reset Password:**
- Fields: `password` (min 8 chars), `confirm_password`
- Token is embedded in the URL path; passed to the API automatically.
- On expired/invalid token → show error and link back to Forgot Password.
- On success → redirect to `/login` with success message.

---

### Profile Setup Page

**Route:** `/profile-setup`  
**Access:** `User` role with no linked `candidateProfileId`

Two option cards stacked vertically:

**Option 1 — Create New Profile**
- Links to `/apply`
- Icon: 📝, title "Create Your Profile"

**Option 2 — Claim Existing Profile**
- Links to `/claim-profile`
- Icon: 🔗, title "I'm Already in the System"
- Subtitle: "An admin may have already entered your information."

---

### Claim Profile Page

**Route:** `/claim-profile`  
**Access:** `User` role with no linked `candidateProfileId`

**On mount:** `GET /api/candidates/unclaimed` → renders a list of unclaimed candidate cards.

**Each card displays:** candidate name, email, preferred job title, college degree.

**Claim button behavior:**
1. `POST /api/user/claim-profile` with `{ candidate_id }`.
2. On success → update `AuthContext` `candidateProfileId` and redirect to `/job-matches`.
3. On error → show inline error message.

**Empty state:** "No unclaimed profiles found. Please create a new profile instead." with a link to `/apply`.

---

### Home Dashboard Page

**Route:** `/`  
**Access:** `Admin`, `Staff`

**Nav links:** Home (active), Add Candidate, View Candidates, Job Matches, Admin (Admin only), Roles (Admin only)

**Content:** Three clickable cards in a responsive grid (`auto-fit, minmax(260px, 1fr)`):

| Card | Color accent | Link | Description |
|---|---|---|---|
| Add Candidate | Blue (brand) | `/apply` | "Submit a new candidate profile..." |
| View Candidates | Green | `/candidates` | "Browse all candidates..." |
| Job Matches | Purple | `/job-matches` | "Look up the best matching jobs..." |

Card hover effect: `translateY(-5px)` + colored box-shadow.

**Conditional rendering:** "Add Candidate" card is hidden for `User` role with a linked profile.

---

### Candidate Form Page

**Route:** `/apply`  
**Access:** All roles

**Nav:** Home (Admin/Staff only), ← Back (User with no profile only), Add Candidate (active), View Candidates (Admin/Staff), Job Matches, Admin/Roles (Admin)

**Form fields:**

| Field | Input type | Required | Notes |
|---|---|---|---|
| Full Name | text | Yes | |
| Email Address | text | Yes | |
| College Degree | text | No | |
| Graduation Date | date | No | |
| Preferred Job Title | text | No | Drives background job scrape |
| Skills | checkbox pills | No | See SkillPicker below |
| Resume | file (PDF) | No | Max 16MB |

**SkillPicker component:**

Pre-defined skills rendered as pill-shaped checkbox labels: Python, JavaScript, Java, React, Node.js, SQL, Machine Learning, AWS, Docker, Git.

- Clicking a pill toggles a `selected` class (blue background + white text).
- "Add your own skill" text input + "Add" button appends a custom skill pill.
  - Custom pills use a `hidden` input (not a checkbox) and have a `×` remove button.
  - Duplicate detection is case-insensitive.
  - Enter key in the custom input triggers Add.
- On form reset: all pills deselected, custom pills removed.

**File upload:**
- Custom-styled dashed upload zone (`border: 2px dashed #4facfe`).
- On file selected: validate type (`application/pdf`) and size (`≤ 16MB`). Show error and clear input if invalid.
- On valid file: show green info bar with filename and size in MB.
- Label changes to "Click to change file" once a file is chosen.

**Submission:**
1. Client-side validate `candidate_name` is not empty.
2. Show loading spinner, disable submit button.
3. `POST /api/candidates` as `multipart/form-data`.
4. On success:
   - `User` role → redirect to `/job-matches`.
   - `Admin`/`Staff` → show success message, redirect to `/success` after 2 seconds.
5. On error → show error message, re-enable button.

---

### Candidates List Page

**Route:** `/candidates`  
**Access:** `Admin`, `Staff`

**Nav:** Home, Add Candidate, View Candidates (active), Job Matches, Admin/Roles (Admin)

**On mount:** `GET /api/candidates` → renders a searchable, filterable table.

#### Toolbar
- Search input — filters table client-side by candidate name (case-insensitive `includes`)
- "Refresh" button — re-calls `GET /api/candidates`
- "Scrape Jobs" button (Admin/Staff) — opens a modal (see below)
- "Add Candidate" button — links to `/apply`

#### Table columns
`Name | Email | Degree | Graduation | Preferred Title | Skills | Actions`

Skills are rendered as inline blue pill badges.

#### Row action buttons
- **Matches** — navigates to `/job-matches?candidate_id=<id>`
- **Send Email** (Admin/Staff) — `POST /api/candidates/<id>/send-matches`, shows success/error toast
- **Edit** — opens inline edit modal (same fields as candidate form, no file upload; calls `PUT /api/candidates/<id>`)
- **Delete** (Admin only) — confirmation prompt, then `DELETE /api/candidates/<id>`, removes row from state

#### Scrape Jobs Modal
Triggered by the "Scrape Jobs" toolbar button. Fields:

| Field | Default | Notes |
|---|---|---|
| Search Term | `"software engineer"` | |
| Location | `"United States"` | |
| Results Wanted | `50` | |
| Sites | `["indeed", "google"]` | Multi-select or checkboxes |

On submit: `POST /api/jobs/scrape`. Shows result summary: "Scraped X jobs, saved Y, generated Z embeddings."

#### Edit Candidate Modal
Pre-populated with the row's current data. Editable fields: name, email, degree, graduation date, preferred title, skills (SkillPicker). Calls `PUT /api/candidates/<id>` with JSON body. On success: update the row in local state.

#### Empty / loading states
- Loading: spinner centered in content area
- No candidates: "No candidates found. Add the first one."

---

### Edit Profile Page

**Route:** `/edit-profile`  
**Access:** `User` role with a linked profile

**Nav:** Edit Profile (active), Job Matches

**On mount:** `GET /api/candidates/<candidateProfileId>` → pre-populate form fields.

**Editable fields:** Full Name, Email, College Degree, Graduation Date, Preferred Job Title, Skills (SkillPicker — no file re-upload on edit).

**Save button:** `PUT /api/candidates/<candidateProfileId>` with JSON body. On success → show success message.

---

### Job Matches Page

**Route:** `/job-matches`  
**Access:** All roles (different behavior per role)

**Nav (User):** Edit Profile, Job Matches (active)  
**Nav (Admin/Staff):** Home, Add Candidate, View Candidates, Job Matches (active), Admin/Roles

#### Admin / Staff view
Shows a search bar at the top:
- Input: Candidate ID (UUID text field)
- Input: Number of Results (number, default 10, max 50)
- "Find Matches" button

On submit: `GET /api/candidates/<candidateId>/matches?limit=<limit>`

The page also accepts a `?candidate_id=` query param (set by the Candidates List page "Matches" button) to pre-fill and auto-trigger the search.

#### User view
No search bar. On mount: automatically calls `GET /api/candidates/<candidateProfileId>/matches?limit=10`. Shows "Your Top N Job Matches" header.

If `candidateProfileId` is null → show "No profile linked" message with a link to `/profile-setup`.

#### Results rendering

Each job renders as a card with:
- Job title (h2) + company name
- Similarity score badge: `(score * 100).toFixed(1) + "% Match"` — green gradient pill, top-right
- Detail grid (rendered only if value exists):
  - 📍 Location
  - 💼 Job Type
  - 🏠 Remote: Yes
  - 💰 Salary range: `$min - $max` (formatted with `toLocaleString()`)
- Description — truncated to 200 chars with "Show more / Show less" toggle (adds/removes `.expanded` class)
- Footer: "View Job →" link (opens in new tab) + "Posted: [date]" (`toLocaleDateString()`)

**Empty state:** "No Job Matches Found — We couldn't find any matching jobs yet."

---

### Admin — Users Page

**Route:** `/admin`  
**Access:** `Admin` only

**Nav:** Home, Add Candidate, View Candidates, Job Matches, Admin (active), Roles

**On mount:** `GET /api/admin/users` + `GET /api/roles`

#### Toolbar
- Search input — client-side filter by username or email

#### Users table columns
`Username | Email | Roles | Candidate Profile | Actions`

- **Roles column:** renders each role as a colored pill badge. Each pill has a `×` button → `DELETE /api/admin/users/<userId>/roles/<roleId>`.
- **Candidate Profile column:** shows the linked `candidateProfileId` (abbreviated UUID) or "None". Has an "Unlink" button if linked → `DELETE /api/admin/users/<userId>/candidate-profile`.
- **Actions:**
  - "Add Role" button → opens inline role assignment UI: a `<select>` of available roles + "Assign" button → `POST /api/admin/users/<userId>/roles` with `{ role_id }`.
  - "Link Profile" button → opens a text input for a candidate UUID + "Link" → `PUT /api/admin/users/<userId>/candidate-profile` with `{ candidate_id }`.
  - "Delete" button (disabled for own account) → confirmation prompt → `DELETE /api/admin/users/<userId>` → remove row from state.

**State management:** After any mutation, update local users array rather than re-fetching.

---

### Admin — Roles Page

**Route:** `/admin/roles`  
**Access:** `Admin` only

**On mount:** `GET /api/roles`

#### Roles table
Columns: `Role Name | Actions`

- **Edit:** inline text input replaces the name cell + Save/Cancel buttons → `PUT /api/roles/<roleId>` with `{ role_name }`.
- **Delete:** confirmation prompt → `DELETE /api/roles/<roleId>` → remove from local state.

#### Create New Role
A row at the bottom of the table (or a form above it): text input + "Create" button → `POST /api/roles` with `{ role_name }` → append new role to local state.

---

## API Reference Summary

All endpoints require a valid JWT Bearer token in the `Authorization` header (except auth endpoints). See [`API_DOCUMENTATION.md`](./API_DOCUMENTATION.md) for full request/response shapes.

| Page | Method | Endpoint | Trigger |
|---|---|---|---|
| Login | POST | `/api/auth/check-roles` | "Continue" button click |
| Claim Profile | GET | `/api/candidates/unclaimed` | On mount |
| Claim Profile | POST | `/api/user/claim-profile` | "Claim" button |
| Candidate Form | POST | `/api/candidates` | Form submit |
| Candidates List | GET | `/api/candidates` | On mount + Refresh |
| Candidates List | PUT | `/api/candidates/<id>` | Edit modal save |
| Candidates List | DELETE | `/api/candidates/<id>` | Delete button |
| Candidates List | POST | `/api/candidates/<id>/send-matches` | Send Email button |
| Candidates List | POST | `/api/jobs/scrape` | Scrape Jobs modal submit |
| Edit Profile | GET | `/api/candidates/<id>` | On mount |
| Edit Profile | PUT | `/api/candidates/<id>` | Save button |
| Job Matches | GET | `/api/candidates/<id>/matches` | On mount (User) or form submit (Admin/Staff) |
| Admin Users | GET | `/api/admin/users` | On mount |
| Admin Users | GET | `/api/roles` | On mount |
| Admin Users | POST | `/api/admin/users/<id>/roles` | Add Role |
| Admin Users | DELETE | `/api/admin/users/<id>/roles/<roleId>` | Remove role pill × |
| Admin Users | PUT | `/api/admin/users/<id>/candidate-profile` | Link Profile |
| Admin Users | DELETE | `/api/admin/users/<id>/candidate-profile` | Unlink Profile |
| Admin Users | DELETE | `/api/admin/users/<id>` | Delete User |
| Admin Roles | GET | `/api/roles` | On mount |
| Admin Roles | POST | `/api/roles` | Create Role |
| Admin Roles | PUT | `/api/roles/<id>` | Edit Role save |
| Admin Roles | DELETE | `/api/roles/<id>` | Delete Role |

---

## Suggested React Project Structure

```
src/
├── api/                  # Axios/fetch wrappers per resource
│   ├── auth.ts
│   ├── candidates.ts
│   ├── jobs.ts
│   └── admin.ts
├── components/
│   ├── AppHeader.tsx
│   ├── NavBar.tsx
│   ├── UserBadge.tsx
│   ├── Alert.tsx
│   ├── Spinner.tsx
│   └── SkillPicker.tsx
├── context/
│   └── AuthContext.tsx   # AuthUser state + JWT helpers
├── pages/
│   ├── LoginPage.tsx
│   ├── RegisterPage.tsx
│   ├── ForgotPasswordPage.tsx
│   ├── ResetPasswordPage.tsx
│   ├── ProfileSetupPage.tsx
│   ├── ClaimProfilePage.tsx
│   ├── HomeDashboard.tsx
│   ├── CandidateFormPage.tsx
│   ├── CandidatesListPage.tsx
│   ├── EditProfilePage.tsx
│   ├── JobMatchesPage.tsx
│   ├── AdminUsersPage.tsx
│   └── AdminRolesPage.tsx
├── router/
│   └── ProtectedRoute.tsx  # Role-based route guard
└── styles/
    └── tokens.css          # CSS variables for the design system
```
