const {
  Document, Packer, Paragraph, TextRun, Table, TableRow, TableCell,
  HeadingLevel, AlignmentType, BorderStyle, WidthType, ShadingType,
  VerticalAlign, PageNumber, Header, Footer, PageBreak,
  ExternalHyperlink, TabStopType, TabStopPosition
} = require('docx');
const fs = require('fs');

// ── Helpers ──────────────────────────────────────────────────────────────────

const BORDER = { style: BorderStyle.SINGLE, size: 1, color: "CCCCCC" };
const BORDERS = { top: BORDER, bottom: BORDER, left: BORDER, right: BORDER };
const NO_BORDER = { style: BorderStyle.NONE, size: 0, color: "FFFFFF" };
const NO_BORDERS = { top: NO_BORDER, bottom: NO_BORDER, left: NO_BORDER, right: NO_BORDER };

const CONTENT_WIDTH = 9360; // US Letter - 1" margins each side

function h1(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun(text)] });
}
function h2(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_2, children: [new TextRun(text)] });
}
function h3(text) {
  return new Paragraph({ heading: HeadingLevel.HEADING_3, children: [new TextRun(text)] });
}
function body(text, opts = {}) {
  return new Paragraph({ children: [new TextRun({ text, ...opts })] });
}
function spacer() {
  return new Paragraph({ children: [new TextRun("")] });
}
function bullet(text) {
  return new Paragraph({
    numbering: { reference: "bullets", level: 0 },
    children: [new TextRun(text)]
  });
}
function code(text) {
  return new Paragraph({
    children: [new TextRun({ text, font: "Courier New", size: 18, color: "C0392B" })]
  });
}

// Two-column label/value row inside a table
function infoRow(label, value, shade = false) {
  const fill = shade ? "F5F7FA" : "FFFFFF";
  return new TableRow({
    children: [
      new TableCell({
        borders: BORDERS,
        width: { size: 2200, type: WidthType.DXA },
        shading: { fill: "E8EEF6", type: ShadingType.CLEAR },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        children: [new Paragraph({ children: [new TextRun({ text: label, bold: true, size: 18, font: "Arial" })] })]
      }),
      new TableCell({
        borders: BORDERS,
        width: { size: 7160, type: WidthType.DXA },
        shading: { fill, type: ShadingType.CLEAR },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        children: [new Paragraph({ children: [new TextRun({ text: value, size: 18, font: "Courier New" })] })]
      })
    ]
  });
}

function infoTable(rows) {
  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: [2200, 7160],
    rows: rows.map(([l, v], i) => infoRow(l, v, i % 2 === 1))
  });
}

// Section divider line
function divider() {
  return new Paragraph({
    border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "2E5090", space: 1 } },
    children: [new TextRun("")]
  });
}

// Full endpoint block
function endpoint(method, path, summary, auth, roles, reqBody, queryParams, responses, businessLogic, springHints) {

  const methodColors = { GET: "1A7F4B", POST: "1A5799", PUT: "8B5E00", DELETE: "C0392B" };
  const methodColor = methodColors[method] || "333333";

  const infoRows = [
    ["Method", method],
    ["Path", path],
    ["Auth Required", auth],
    ["Allowed Roles", roles],
  ];

  const nodes = [
    spacer(),
    new Paragraph({
      children: [
        new TextRun({ text: `${method}  `, bold: true, color: methodColor, size: 24, font: "Courier New" }),
        new TextRun({ text: path, bold: true, size: 24, font: "Courier New", color: "2E3A59" }),
      ]
    }),
    new Paragraph({
      spacing: { after: 80 },
      children: [new TextRun({ text: summary, size: 20, italics: true, color: "555555", font: "Arial" })]
    }),
    infoTable(infoRows),
  ];

  if (queryParams && queryParams.length > 0) {
    nodes.push(spacer());
    nodes.push(new Paragraph({ children: [new TextRun({ text: "Query Parameters", bold: true, size: 20, font: "Arial" })] }));
    nodes.push(paramTable(queryParams));
  }

  if (reqBody) {
    nodes.push(spacer());
    nodes.push(new Paragraph({ children: [new TextRun({ text: "Request Body", bold: true, size: 20, font: "Arial" })] }));
    nodes.push(...reqBody.map(line => new Paragraph({
      children: [new TextRun({ text: line, font: "Courier New", size: 18, color: "2C3E50" })],
      indent: { left: 360 }
    })));
  }

  nodes.push(spacer());
  nodes.push(new Paragraph({ children: [new TextRun({ text: "Responses", bold: true, size: 20, font: "Arial" })] }));
  nodes.push(responseTable(responses));

  nodes.push(spacer());
  nodes.push(new Paragraph({ children: [new TextRun({ text: "Business Logic", bold: true, size: 20, font: "Arial" })] }));
  businessLogic.forEach(item => nodes.push(
    new Paragraph({
      numbering: { reference: "bullets", level: 0 },
      children: [new TextRun({ text: item, size: 18, font: "Arial" })]
    })
  ));

  if (springHints && springHints.length > 0) {
    nodes.push(spacer());
    nodes.push(new Paragraph({ children: [new TextRun({ text: "Spring Boot Implementation Notes", bold: true, size: 20, font: "Arial", color: "1A5799" })] }));
    springHints.forEach(item => nodes.push(
      new Paragraph({
        numbering: { reference: "bullets", level: 0 },
        children: [new TextRun({ text: item, size: 18, font: "Arial" })]
      })
    ));
  }

  nodes.push(spacer());
  nodes.push(divider());

  return nodes;
}

function paramTable(params) {
  const headerRow = new TableRow({
    tableHeader: true,
    children: ["Name", "Type", "Required", "Description"].map(h =>
      new TableCell({
        borders: BORDERS,
        shading: { fill: "2E5090", type: ShadingType.CLEAR },
        margins: { top: 60, bottom: 60, left: 120, right: 120 },
        width: { size: Math.floor(CONTENT_WIDTH / 4), type: WidthType.DXA },
        children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 18, font: "Arial" })] })]
      })
    )
  });

  const dataRows = params.map(([name, type, req, desc], i) =>
    new TableRow({
      children: [name, type, req, desc].map(val =>
        new TableCell({
          borders: BORDERS,
          shading: { fill: i % 2 === 0 ? "FFFFFF" : "F5F7FA", type: ShadingType.CLEAR },
          margins: { top: 60, bottom: 60, left: 120, right: 120 },
          width: { size: Math.floor(CONTENT_WIDTH / 4), type: WidthType.DXA },
          children: [new Paragraph({ children: [new TextRun({ text: val, size: 18, font: "Courier New" })] })]
        })
      )
    })
  );

  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: [2340, 1560, 1200, 4260],
    rows: [headerRow, ...dataRows]
  });
}

function responseTable(responses) {
  const colWidths = [1200, 2400, 5760];
  const headerRow = new TableRow({
    tableHeader: true,
    children: ["Status", "Content-Type", "Body / Description"].map((h, i) =>
      new TableCell({
        borders: BORDERS,
        shading: { fill: "2E5090", type: ShadingType.CLEAR },
        margins: { top: 60, bottom: 60, left: 120, right: 120 },
        width: { size: colWidths[i], type: WidthType.DXA },
        children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 18, font: "Arial" })] })]
      })
    )
  });

  const dataRows = responses.map(([status, ct, desc], i) =>
    new TableRow({
      children: [status, ct, desc].map((val, ci) =>
        new TableCell({
          borders: BORDERS,
          shading: { fill: i % 2 === 0 ? "FFFFFF" : "F5F7FA", type: ShadingType.CLEAR },
          margins: { top: 60, bottom: 60, left: 120, right: 120 },
          width: { size: colWidths[ci], type: WidthType.DXA },
          children: [new Paragraph({ children: [new TextRun({ text: val, size: 18, font: "Courier New" })] })]
        })
      )
    })
  );

  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: colWidths,
    rows: [headerRow, ...dataRows]
  });
}

// Generic header + striped rows table (widths must sum to CONTENT_WIDTH)
function simpleTable(headers, widths, rows, font = "Arial") {
  const headerRow = new TableRow({
    tableHeader: true,
    children: headers.map((h, i) =>
      new TableCell({
        borders: BORDERS,
        shading: { fill: "2E5090", type: ShadingType.CLEAR },
        margins: { top: 80, bottom: 80, left: 120, right: 120 },
        width: { size: widths[i], type: WidthType.DXA },
        children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 18, font: "Arial" })] })]
      })
    )
  });

  const dataRows = rows.map((row, i) =>
    new TableRow({
      children: row.map((val, ci) =>
        new TableCell({
          borders: BORDERS,
          shading: { fill: i % 2 === 0 ? "FFFFFF" : "F5F7FA", type: ShadingType.CLEAR },
          margins: { top: 60, bottom: 60, left: 120, right: 120 },
          width: { size: widths[ci], type: WidthType.DXA },
          children: [new Paragraph({ children: [new TextRun({ text: val, size: 18, font: ci === 0 ? "Courier New" : font })] })]
        })
      )
    })
  );

  return new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: widths,
    rows: [headerRow, ...dataRows]
  });
}

// Shared by every /api/drafts response
const DRAFT_OBJECT = "{ id, job_id, job_title, company_name, job_url, recipient, subject, body, status: \"DRAFT\"|\"SENT\", created_at, updated_at, sent_at }";
const DRAFT_ACCESS = "Yes (JWT with candidateProfileId)";
const DRAFT_ROLES = "Any role with a linked candidate profile (own drafts only)";

// ── Document Content ──────────────────────────────────────────────────────────

const children = [

  // ── Title Page ──
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { before: 2880, after: 240 },
    children: [new TextRun({ text: "Beshton Job Matching Platform", bold: true, size: 56, font: "Arial", color: "2E5090" })]
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
    children: [new TextRun({ text: "REST API Implementation Document", size: 36, font: "Arial", color: "555555" })]
  }),
  new Paragraph({
    alignment: AlignmentType.CENTER,
    spacing: { after: 200 },
    children: [new TextRun({ text: "Version 1.0  |  For Java Spring Boot Migration", size: 24, font: "Arial", color: "888888", italics: true })]
  }),
  new Paragraph({ children: [new PageBreak()] }),

  // ── Overview ──
  h1("1. Document Overview"),
  body("This document provides a complete reference for every HTTP endpoint in the Beshton Job Matching Platform. For each endpoint it specifies the HTTP method, URL path, authentication requirements, role-based access, request/response contracts, and full business logic. The intent is to serve as a migration blueprint for a Java Spring Boot + REST API reimplementation."),
  spacer(),
  body("Technology mapping summary:", { bold: true }),
  spacer(),

  new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: [3120, 3120, 3120],
    rows: [
      new TableRow({
        tableHeader: true,
        children: ["Concern", "Current (Flask/Python)", "Target (Spring Boot/Java)"].map(h =>
          new TableCell({
            borders: BORDERS,
            shading: { fill: "2E5090", type: ShadingType.CLEAR },
            margins: { top: 80, bottom: 80, left: 120, right: 120 },
            width: { size: 3120, type: WidthType.DXA },
            children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 18, font: "Arial" })] })]
          })
        )
      }),
      ...[
        ["Web framework", "Flask", "@RestController / @RequestMapping"],
        ["Auth / sessions", "Flask-Login + session cookie", "Spring Security + JWT"],
        ["Password hashing", "Werkzeug PBKDF2", "BCryptPasswordEncoder"],
        ["Signed tokens", "itsdangerous URLSafeTimedSerializer", "JJWT / Nimbus JOSE"],
        ["ORM", "SQLAlchemy + psycopg2", "Spring Data JPA + Hibernate"],
        ["Email", "AWS SES via boto3", "Spring Mail + AWS SES SDK"],
        ["Vector search", "pgvector (raw SQL)", "pgvector-hibernate / native query"],
        ["Embeddings", "OpenAI text-embedding-ada-002", "OpenAI Java SDK or REST call"],
        ["Background jobs", "APScheduler + threading.Thread", "@Scheduled / Spring Batch"],
        ["Validation", "Manual in view functions", "@Valid / @Validated + BindingResult"],
      ].map(([a, b, c], i) =>
        new TableRow({
          children: [a, b, c].map(val =>
            new TableCell({
              borders: BORDERS,
              shading: { fill: i % 2 === 0 ? "FFFFFF" : "F5F7FA", type: ShadingType.CLEAR },
              margins: { top: 60, bottom: 60, left: 120, right: 120 },
              width: { size: 3120, type: WidthType.DXA },
              children: [new Paragraph({ children: [new TextRun({ text: val, size: 18, font: "Courier New" })] })]
            })
          )
        })
      )
    ]
  }),

  spacer(),
  h2("1.1 Authentication Model"),
  body("All API endpoints (except /register, /login, /forgot-password, /reset-password, /api/auth/check-roles) require an authenticated session. In the current Flask app this is enforced by @login_required. In Spring Boot, replace this with a JWT bearer token filter on all protected routes."),
  spacer(),
  body("Three roles exist:", { bold: true }),
  bullet("Admin — full system access including user management, role management, and all candidate/job operations."),
  bullet("Staff — can manage candidates and jobs but cannot administer users or roles."),
  bullet("User — a job-seeker with access limited to their own candidate profile and personal job matches."),

  spacer(),
  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 2 – AUTH ENDPOINTS
  // ═══════════════════════════════════════════════════════════════
  h1("2. Authentication Endpoints"),
  divider(),

  ...endpoint(
    "POST", "/register",
    "Register a new user account and redirect to profile setup.",
    "No", "Public",
    [
      "Content-Type: application/x-www-form-urlencoded",
      "email        : string (required)",
      "username     : string (required)",
      "password     : string (min 8 chars, required)",
      "confirm_password : string (must match password)",
    ],
    null,
    [
      ["200", "text/html", "Renders register.html with validation errors"],
      ["302", "—", "Redirect to /profile-setup on success"],
    ],
    [
      "All four fields are required; missing any field returns a flash error.",
      "Password and confirm_password must match.",
      "Password must be at least 8 characters.",
      "email must be unique in user_accounts table.",
      "username must be unique in user_accounts table.",
      "Password is hashed with Werkzeug PBKDF2 before storage.",
      "On success, the user is immediately logged in (no email verification) and redirected to /profile-setup.",
      "active_role session key is cleared on new registration.",
    ],
    [
      "Map to POST /api/auth/register returning 201 Created with a JWT on success.",
      "Use @Valid with a RegisterRequest DTO; return 400 Bad Request with field-level errors.",
      "Use BCryptPasswordEncoder for hashing.",
      "Duplicate email/username should return 409 Conflict.",
    ]
  ),

  ...endpoint(
    "POST", "/api/auth/check-roles",
    "Validate credentials and return the roles assigned to the user. Used by the login form to populate the role selector before final login.",
    "No", "Public",
    [
      "Content-Type: application/json",
      "{ \"login\": \"<email or username>\", \"password\": \"<plaintext>\" }",
    ],
    null,
    [
      ["200", "application/json", "{ \"roles\": [ { \"role_id\": \"...\", \"role_name\": \"...\" } ] }"],
      ["400", "application/json", "{ \"error\": \"Login and password are required\" }"],
      ["401", "application/json", "{ \"error\": \"Invalid username/email or password\" }"],
      ["503", "application/json", "{ \"error\": \"Database unavailable\" }"],
    ],
    [
      "Accepts either email address or username in the login field.",
      "Password is verified against the stored PBKDF2 hash.",
      "Returns the list of role objects for that user only if credentials are valid.",
      "Does NOT create a session or issue a token — purely a pre-login check.",
    ],
    [
      "In Spring Boot, this can be a lightweight POST /api/auth/roles-check endpoint.",
      "Return the roles array inside a standard ApiResponse wrapper.",
      "Avoid leaking whether the account exists vs. wrong password — return 401 for both.",
    ]
  ),

  ...endpoint(
    "POST", "/login",
    "Authenticate a user, set the active role, and redirect to the appropriate landing page.",
    "No", "Public",
    [
      "Content-Type: application/x-www-form-urlencoded",
      "login    : string — email or username",
      "password : string",
      "role     : string — role_name selected from /api/auth/check-roles",
    ],
    null,
    [
      ["200", "text/html", "Renders login.html with error flash on bad credentials"],
      ["302", "—", "Admin/Staff → /  |  User (with profile) → /job-matches  |  User (no profile) → /profile-setup"],
    ],
    [
      "Looks up user by email OR username (case-insensitive for email).",
      "Verifies password against stored hash.",
      "Validates that the submitted role actually belongs to this user; rejects unknown roles.",
      "Stores active_role in the server-side session.",
      "If role is 'User' and candidate_profile_id is set, redirects to /job-matches.",
      "If role is 'User' and no profile linked, redirects to /profile-setup.",
      "Admin/Staff always redirect to the home dashboard.",
      "Supports a 'next' query parameter for post-login redirect.",
    ],
    [
      "Replace with POST /api/auth/login returning { token, role, redirectHint }.",
      "Issue a signed JWT containing userId, role, candidateProfileId.",
      "The client should use redirectHint to navigate after login.",
      "Store role in JWT claims so every subsequent request is stateless.",
    ]
  ),

  ...endpoint(
    "POST", "/forgot-password",
    "Send a password-reset email to a registered address.",
    "No", "Public",
    [
      "Content-Type: application/x-www-form-urlencoded",
      "email : string",
    ],
    null,
    [
      ["302", "—", "Always redirects to /login with an info flash message"],
    ],
    [
      "Lowercases the submitted email before lookup.",
      "Always returns the same flash message regardless of whether the email exists — prevents account enumeration.",
      "If the email IS found, generates a time-limited signed token (expires in 1 hour) using itsdangerous URLSafeTimedSerializer with salt 'password-reset'.",
      "Constructs a full reset URL and sends it via AWS SES.",
      "If the email is NOT found, no email is sent but no error is shown.",
    ],
    [
      "Use JJWT to generate a short-lived (1h) signed reset token containing the email.",
      "Store the token hash in a password_reset_tokens table to support single-use invalidation.",
      "Use Spring Mail + SES SMTP to send the email.",
      "Return 200 OK with a generic message regardless of whether the email exists.",
    ]
  ),

  ...endpoint(
    "POST", "/reset-password/<token>",
    "Verify a reset token and update the user's password.",
    "No", "Public (token-gated)",
    [
      "Content-Type: application/x-www-form-urlencoded",
      "password         : string (min 8 chars)",
      "confirm_password : string (must match)",
    ],
    null,
    [
      ["200", "text/html", "Renders reset_password.html with validation errors"],
      ["302", "—", "Redirect to /forgot-password if token expired or invalid"],
      ["302", "—", "Redirect to /login on successful password update"],
    ],
    [
      "Token is extracted from the URL path parameter.",
      "Token is verified with the same serializer/salt used during generation.",
      "SignatureExpired → flash 'link has expired', redirect to /forgot-password.",
      "BadSignature → flash 'invalid link', redirect to /forgot-password.",
      "Password and confirm_password must match and be at least 8 characters.",
      "On success, updates password_hash in user_accounts and redirects to /login.",
    ],
    [
      "Validate the JWT signature and expiry; return 400/410 for invalid/expired tokens.",
      "Mark the token as used in the database to prevent replay.",
      "Accept PATCH /api/auth/reset-password with { token, newPassword } in the body.",
    ]
  ),

  ...endpoint(
    "POST", "/logout",
    "Invalidate the current session and redirect to the login page.",
    "Yes", "Any authenticated role",
    null, null,
    [
      ["302", "—", "Redirect to /login"],
    ],
    [
      "Removes active_role from the session store.",
      "Calls Flask-Login logout_user() which clears the session cookie.",
    ],
    [
      "POST /api/auth/logout should invalidate the JWT on the server side (add to a blocklist or use short-lived tokens).",
      "Return 204 No Content.",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 3 – CANDIDATE ENDPOINTS
  // ═══════════════════════════════════════════════════════════════
  h1("3. Candidate Profile Endpoints"),
  divider(),

  ...endpoint(
    "POST", "/api/candidates",
    "Create a new candidate profile, generate an embedding, trigger a background job scrape, and optionally email job matches.",
    "Yes", "Admin, Staff, User",
    [
      "Content-Type: multipart/form-data",
      "candidate_name       : string (required)",
      "email                : string (optional)",
      "college_degree       : string (optional)",
      "graduation_date      : string ISO date (optional)",
      "preferred_job_title  : string (optional)",
      "skills               : string[] — repeated field (optional)",
      "resume               : file (PDF, max 16 MB, optional)",
    ],
    null,
    [
      ["201", "application/json", "{ \"message\": \"...\", \"candidate_id\": \"<uuid>\", \"email_sent\": bool }"],
      ["400", "application/json", "{ \"error\": \"Candidate name is required\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to save candidate information\" }"],
    ],
    [
      "candidate_name is the only required field.",
      "PDF resume (if provided) is read into memory and stored as a Base64-encoded string in the database.",
      "An OpenAI text-embedding-ada-002 embedding is generated from name + degree + preferred title + skills.",
      "The candidate is saved to candidate_info with the embedding vector.",
      "If the caller's active_role is not Admin or Staff (i.e. a User), the new candidate profile is automatically linked to the caller's user account via candidate_profile_id.",
      "If preferred_job_title is provided, a background thread calls scrape_jobs_for_title() with a 24-hour deduplication TTL.",
      "If email is provided, find_matching_jobs() is called (limit 10) and results are emailed via SES.",
    ],
    [
      "Use @RequestPart for multipart + @RequestPart(\"resume\") MultipartFile resume.",
      "Run embedding generation and job scraping asynchronously via @Async / CompletableFuture.",
      "Store resume bytes as a Base64 TEXT column or as BYTEA; consider S3 for production.",
      "Use an @EventListener or ApplicationEvent to decouple the scrape trigger from the save.",
    ]
  ),

  ...endpoint(
    "GET", "/api/candidates",
    "Retrieve all candidate profiles (Admin/Staff) or just the caller's own profile (User).",
    "Yes", "Admin, Staff, User",
    null,
    [["limit", "integer", "No", "Maximum number of records to return"]],
    [
      ["200", "application/json", "{ \"candidates\": [ { ...candidateFields } ] }"],
      ["500", "application/json", "{ \"error\": \"Failed to retrieve candidates\" }"],
    ],
    [
      "Admin and Staff receive all candidates in the database.",
      "User role receives only the candidate linked to their account (candidate_profile_id); returns an empty list if no profile is linked.",
      "The embedding vector field is included in the response (as a float array) but is typically ignored by the frontend.",
    ],
    [
      "Use a @PreAuthorize / method-level security check to filter by role.",
      "Return Page<CandidateDTO> with pagination for Admin/Staff.",
      "For User role, fetch only the single profile via the JWT's candidateProfileId claim.",
    ]
  ),

  ...endpoint(
    "GET", "/api/candidates/<candidate_id>",
    "Retrieve a single candidate by UUID.",
    "Yes", "Admin, Staff, or User (own profile only)",
    null, null,
    [
      ["200", "application/json", "{ ...candidateFields }"],
      ["403", "application/json", "{ \"error\": \"Access denied\" }"],
      ["404", "application/json", "{ \"error\": \"Candidate not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to retrieve candidate\" }"],
    ],
    [
      "A User may only fetch the candidate whose ID matches their own candidate_profile_id.",
      "Admin and Staff may fetch any candidate.",
      "Returns a flat dictionary of all candidate_info columns including the Base64 resume string.",
    ],
    [
      "Use @PathVariable UUID candidateId.",
      "Enforce ownership in a @PreAuthorize(\"hasRole('ADMIN') or #candidateId == authentication.details.candidateProfileId\") expression.",
    ]
  ),

  ...endpoint(
    "PUT", "/api/candidates/<candidate_id>",
    "Update editable fields on an existing candidate profile.",
    "Yes", "Admin, Staff, or User (own profile only)",
    [
      "Content-Type: application/json",
      "{",
      "  \"candidate_name\"      : string (optional)",
      "  \"email\"               : string (optional)",
      "  \"college_degree\"      : string (optional)",
      "  \"graduation_date\"     : string ISO date (optional)",
      "  \"preferred_job_title\" : string (optional)",
      "  \"skills\"              : string[] (optional)",
      "}",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Candidate updated successfully\" }"],
      ["403", "application/json", "{ \"error\": \"Access denied\" }"],
      ["404", "application/json", "{ \"error\": \"Candidate not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to update candidate\" }"],
    ],
    [
      "Only the fields: candidate_name, email, college_degree, graduation_date, preferred_job_title, skills are updatable via this endpoint.",
      "resume_data is NOT updatable here (separate concern).",
      "A User may only update their own linked profile.",
      "date_updated is automatically set to the current UTC time on every update.",
      "Note: this endpoint does NOT regenerate the embedding — a separate process or manual trigger is needed after profile changes.",
    ],
    [
      "Accept a CandidateUpdateRequest DTO with @Valid.",
      "Consider triggering embedding regeneration via an @EntityListeners / @PostUpdate hook.",
      "Use optimistic locking (@Version) to prevent concurrent update conflicts.",
    ]
  ),

  ...endpoint(
    "DELETE", "/api/candidates/<candidate_id>",
    "Permanently delete a candidate profile.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"Candidate deleted successfully\" }"],
      ["403", "application/json", "{ \"error\": \"Access denied\" }"],
      ["404", "application/json", "{ \"error\": \"Candidate not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to delete candidate\" }"],
    ],
    [
      "Only Admin role is permitted to delete candidates.",
      "If a user_account has candidate_profile_id pointing to this record, the FK is set to NULL (ON DELETE SET NULL) automatically by the database.",
      "The resume Base64 data stored in the row is also permanently deleted.",
    ],
    [
      "Use @PreAuthorize(\"hasRole('ADMIN')\").",
      "Verify ON DELETE SET NULL is set on the user_accounts.candidate_profile_id FK in the schema.",
      "Return 204 No Content instead of 200 for a more REST-idiomatic response.",
    ]
  ),

  ...endpoint(
    "GET", "/api/candidates/unclaimed",
    "Return all candidate profiles that are not yet linked to any user account.",
    "Yes", "Any authenticated user",
    null, null,
    [
      ["200", "application/json", "{ \"candidates\": [ { \"id\", \"candidate_name\", \"email\", \"preferred_job_title\", \"college_degree\" } ] }"],
      ["500", "application/json", "{ \"error\": \"Failed to fetch candidates\" }"],
    ],
    [
      "Uses a LEFT JOIN between candidate_info and user_accounts on candidate_profile_id.",
      "Only returns rows where user_accounts.id IS NULL (i.e. the profile is unclaimed).",
      "Results are ordered by candidate_name.",
      "Used by the /claim-profile page to let a new user self-associate with a pre-entered profile.",
    ],
    [
      "Use a JPQL query: SELECT c FROM CandidateInfo c WHERE c.userAccount IS NULL ORDER BY c.candidateName.",
      "Return a minimal DTO (no resume data, no embedding) for security and performance.",
    ]
  ),

  ...endpoint(
    "POST", "/api/user/claim-profile",
    "Associate an unclaimed candidate profile with the currently logged-in user.",
    "Yes", "User role",
    [
      "Content-Type: application/json",
      "{ \"candidate_id\": \"<uuid>\" }",
    ],
    null,
    [
      ["200", "application/json", "{ \"candidate_id\": \"<uuid>\", \"message\": \"Profile claimed successfully\" }"],
      ["400", "application/json", "{ \"error\": \"candidate_id is required\" }"],
      ["404", "application/json", "{ \"error\": \"Profile not found or already claimed\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to associate profile\" }"],
    ],
    [
      "Re-fetches unclaimed candidates list at claim time to verify the profile is still unclaimed (race-condition guard).",
      "Updates user_accounts.candidate_profile_id with the given UUID.",
      "Updates the in-memory user object's candidate_profile_id so subsequent requests reflect the change without re-login.",
    ],
    [
      "Use a SELECT ... FOR UPDATE lock on the candidate row to prevent two users claiming the same profile concurrently.",
      "Update the JWT or return a new token with the updated candidateProfileId claim.",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 4 – JOB MATCHING ENDPOINTS
  // ═══════════════════════════════════════════════════════════════
  h1("4. Job Matching Endpoints"),
  divider(),

  ...endpoint(
    "GET", "/api/candidates/<candidate_id>/matches",
    "Return the top N jobs most similar to a candidate profile using pgvector cosine distance.",
    "Yes", "Admin, Staff, or User (own profile only)",
    null,
    [["limit", "integer", "No", "Max results to return (default 10)"]],
    [
      ["200", "application/json", "{ \"candidate_id\": \"...\", \"matches\": [ { ...jobFields, \"similarity_score\": float } ], \"total_matches\": int }"],
      ["200", "application/json", "{ \"message\": \"No matches found\", \"matches\": [] }  — if no results"],
      ["403", "application/json", "{ \"error\": \"Access denied\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to get job matches\" }"],
    ],
    [
      "Fetches the candidate's 1536-dimensional embedding vector from candidate_info.",
      "Executes a raw pgvector SQL query: SELECT id, embedding <=> CAST(:emb AS vector) AS distance FROM job_listings WHERE embedding IS NOT NULL ORDER BY distance LIMIT :lim.",
      "The <=> operator is the pgvector cosine distance operator.",
      "similarity_score is computed as 1 - cosine_distance, so 1.0 = identical, 0.0 = orthogonal.",
      "Results are returned in order of descending similarity.",
      "A User may only request matches for their own candidate_profile_id.",
    ],
    [
      "Use @Query(nativeQuery = true) with a native SQL query referencing the pgvector <=> operator.",
      "Cast the embedding parameter explicitly: CAST(:emb AS vector).",
      "Return a List<JobMatchDTO> that includes similarity_score.",
      "Consider caching results with a short TTL (e.g. @Cacheable) since embeddings rarely change.",
    ]
  ),

  ...endpoint(
    "POST", "/api/candidates/<candidate_id>/send-matches",
    "Email the top 10 job matches to the candidate's email address.",
    "Yes", "Admin, Staff only",
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"Match email sent to <email>\" }"],
      ["400", "application/json", "{ \"error\": \"Candidate has no email address\" }"],
      ["404", "application/json", "{ \"error\": \"Candidate not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to send email\" }"],
    ],
    [
      "Fetches the candidate record to retrieve email and name.",
      "Calls find_matching_jobs(candidate_id, limit=10) to get the top matches.",
      "Sends a formatted HTML email via AWS SES using the SESEmailService.",
      "Returns 400 if the candidate has no email stored.",
    ],
    [
      "Inject an EmailService bean that wraps the AWS SES v2 client.",
      "Use @PreAuthorize(\"hasAnyRole('ADMIN','STAFF')\").",
      "Build the email body from a Thymeleaf or Freemarker template.",
    ]
  ),

  ...endpoint(
    "POST", "/api/email/test",
    "Send a test email with fake job matches to verify the SES integration.",
    "Yes", "Any authenticated user",
    [
      "Content-Type: application/json",
      "{ \"email\": \"<target email address>\" }",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Test email sent to <email>\" }"],
      ["400", "application/json", "{ \"error\": \"email field is required\" }"],
      ["500", "application/json", "{ \"error\": \"<exception message>\" }"],
    ],
    [
      "Sends two hardcoded fake job match entries to the provided address.",
      "Uses the same SESEmailService.send_job_matches() code path as production emails.",
      "Intended for development/ops use to verify SES credentials and templates.",
    ],
    [
      "Restrict this endpoint to ADMIN role only in production.",
      "Use Spring Boot Actuator or a dedicated /admin/email/test route.",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 5 – JOB SCRAPING ENDPOINTS
  // ═══════════════════════════════════════════════════════════════
  h1("5. Job Scraping Endpoints"),
  divider(),

  ...endpoint(
    "POST", "/api/jobs/scrape",
    "Trigger a synchronous job scrape from Indeed and/or Google Jobs, save new listings, and generate embeddings.",
    "Yes", "Admin, Staff",
    [
      "Content-Type: application/json",
      "{",
      "  \"site_name\"       : string[] (default: [\"indeed\", \"google\"])",
      "  \"search_term\"     : string   (default: \"software engineer\")",
      "  \"location\"        : string   (default: \"United States\")",
      "  \"results_wanted\"  : integer  (default: 50)",
      "}",
    ],
    null,
    [
      ["201", "application/json", "{ \"message\": \"...\", \"jobs_scraped\": int, \"jobs_saved\": int, \"embeddings_generated\": int, \"sites\": [...] }"],
      ["200", "application/json", "{ \"message\": \"No jobs found\", \"jobs_scraped\": 0 }"],
      ["500", "application/json", "{ \"error\": \"Failed to scrape jobs: <detail>\" }"],
    ],
    [
      "Uses the JobSpy Python library to scrape jobs with hours_old=24 (only listings posted in the last 24 hours).",
      "Deduplicates by (job_url, site) — existing records are skipped.",
      "For each new job, generates a 1536-dim OpenAI embedding from title + company + description + skills + type + location.",
      "Embeddings are committed in batches of 10.",
      "Returns counts of scraped, saved, and embedded records.",
    ],
    [
      "Replace JobSpy with a Java HTTP client calling the Indeed Publisher API or a scraping microservice.",
      "Run embedding generation asynchronously via a message queue (SQS) to avoid blocking the request.",
      "Implement idempotency by checking (job_url, site) uniqueness before insert.",
      "Schedule with @Scheduled(cron = \"0 0 2 * * ?\") for nightly auto-runs.",
    ]
  ),

  ...endpoint(
    "GET", "/api/jobs",
    "Retrieve stored job listings with optional site filter.",
    "Yes", "Admin, Staff",
    null,
    [
      ["site", "string", "No", "Filter by job board name (e.g. 'indeed', 'google')"],
      ["limit", "integer", "No", "Maximum number of records to return"],
    ],
    [
      ["200", "application/json", "{ \"jobs\": [ { ...jobFields } ], \"total\": int }"],
      ["500", "application/json", "{ \"error\": \"Failed to retrieve jobs\" }"],
    ],
    [
      "Returns all columns from job_listings including the embedding vector as a float array.",
      "If site is provided, filters to only that job board's results.",
      "No pagination is implemented in the current version — rely on limit to cap results.",
    ],
    [
      "Return Page<JobListingDTO> with Spring Data pagination (@PageableDefault).",
      "Exclude the embedding vector from the DTO to reduce payload size.",
      "Add @PreAuthorize(\"hasAnyRole('ADMIN','STAFF')\").",
    ]
  ),

  ...endpoint(
    "POST", "/api/jobs/delete-old",
    "Delete job listings older than a specified number of days.",
    "Yes", "Admin, Staff",
    [
      "Content-Type: application/json",
      "{ \"days_old\": integer (default 30) }",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Deleted N jobs older than X days\", \"jobs_deleted\": int }"],
      ["500", "application/json", "{ \"error\": \"Failed to delete old jobs\" }"],
    ],
    [
      "Calculates a cutoff timestamp as UTC now minus days_old.",
      "Deletes all job_listings rows where date_scraped < cutoff.",
      "This same operation is also run automatically by APScheduler every day at 02:00 UTC.",
    ],
    [
      "Implement as @Scheduled(cron = \"0 0 2 * * ?\") in a JobCleanupService.",
      "Expose the manual endpoint with @PreAuthorize(\"hasRole('ADMIN')\").",
    ]
  ),

  ...endpoint(
    "POST", "/api/jobs/generate-embeddings",
    "Generate OpenAI embeddings for job listings that do not yet have one.",
    "Yes", "Admin, Staff",
    [
      "Content-Type: application/json",
      "{ \"limit\": integer (default 50) }",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Generated embeddings for N jobs\", \"jobs_processed\": int }"],
      ["500", "application/json", "{ \"error\": \"Failed to generate job embeddings\" }"],
    ],
    [
      "Queries job_listings WHERE embedding IS NULL, limited to the specified count.",
      "For each job, constructs a text string from title + company + description + skills + job_type + location.",
      "Calls OpenAI embeddings API (model: text-embedding-ada-002) to get a 1536-float vector.",
      "Commits in batches of 10 to avoid large uncommitted transactions.",
      "If the OpenAI API key is missing, processing stops immediately.",
    ],
    [
      "Call the OpenAI Embeddings REST endpoint using WebClient or RestTemplate.",
      "Process in batches using Spring Batch ItemProcessor for resilience and restartability.",
      "Store the 1536-float array as a pgvector VECTOR(1536) column via a custom Hibernate type.",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 6 – ADMIN ENDPOINTS
  // ═══════════════════════════════════════════════════════════════
  h1("6. Administration Endpoints"),
  divider(),

  ...endpoint(
    "GET", "/api/admin/users",
    "Retrieve all user accounts with their assigned roles.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"users\": [ { \"id\", \"username\", \"email\", \"candidate_profile_id\", \"roles\": [\"Admin\"|\"Staff\"|\"User\"] } ] }"],
      ["500", "application/json", "{ \"error\": \"Failed to retrieve users\" }"],
    ],
    [
      "Joins user_accounts with user_role_mapping and roles tables.",
      "Returns every user in the system sorted by email.",
      "Roles are returned as a flat array of role_name strings per user.",
    ],
    [
      "Use a JPQL fetch join: SELECT u FROM UserAccount u LEFT JOIN FETCH u.roles ORDER BY u.email.",
      "Return List<UserAdminDTO> excluding password hash.",
    ]
  ),

  ...endpoint(
    "POST", "/api/admin/users/<user_id>/roles",
    "Assign a role to a user.",
    "Yes", "Admin only",
    [
      "Content-Type: application/json",
      "{ \"role_id\": \"<uuid>\" }",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Role assigned\" }"],
      ["400", "application/json", "{ \"error\": \"role_id is required\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to assign role\" }"],
    ],
    [
      "Checks for an existing mapping in user_role_mapping before inserting; idempotent.",
      "Inserts a new (user_id, role_id) row if none exists.",
    ],
    [
      "Use a @ManyToMany relationship between UserAccount and Role.",
      "Call userAccount.getRoles().add(role) and save via the repository.",
    ]
  ),

  ...endpoint(
    "DELETE", "/api/admin/users/<user_id>/roles/<role_id>",
    "Remove a role from a user.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"Role removed\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to remove role\" }"],
    ],
    [
      "Deletes the (user_id, role_id) row from user_role_mapping.",
      "Returns 200 even if the mapping did not exist (no-op).",
    ],
    [
      "Use userAccount.getRoles().remove(role) and save.",
      "Return 204 No Content for a more idiomatic REST response.",
    ]
  ),

  ...endpoint(
    "PUT", "/api/admin/users/<user_id>/candidate-profile",
    "Link a candidate profile to a user account (Admin override).",
    "Yes", "Admin only",
    [
      "Content-Type: application/json",
      "{ \"candidate_id\": \"<uuid>\" }",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Profile associated\" }"],
      ["400", "application/json", "{ \"error\": \"candidate_id is required\" }"],
      ["404", "application/json", "{ \"error\": \"User not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to associate profile\" }"],
    ],
    [
      "Sets user_accounts.candidate_profile_id = candidate_id for the given user.",
      "The candidate_profile_id column has a UNIQUE constraint — only one user can be linked to a profile at a time.",
    ],
    [
      "Validate uniqueness at the service layer and return 409 Conflict if already claimed.",
    ]
  ),

  ...endpoint(
    "DELETE", "/api/admin/users/<user_id>/candidate-profile",
    "Unlink a candidate profile from a user account.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"Profile disassociated\" }"],
      ["404", "application/json", "{ \"error\": \"User not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to disassociate profile\" }"],
    ],
    [
      "Sets user_accounts.candidate_profile_id = NULL for the given user.",
      "The candidate profile itself is NOT deleted.",
    ],
    [
      "Return 204 No Content for consistency.",
    ]
  ),

  ...endpoint(
    "DELETE", "/api/admin/users/<user_id>",
    "Permanently delete a user account.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"User deleted\" }"],
      ["400", "application/json", "{ \"error\": \"You cannot delete your own account\" }"],
      ["404", "application/json", "{ \"error\": \"User not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to delete user\" }"],
    ],
    [
      "Prevents self-deletion: compares current_user.id with the target user_id.",
      "Deletes the user row from user_accounts; cascades remove the user_role_mapping entries.",
      "candidate_profile_id FK on the deleted user is handled by ON DELETE SET NULL on the candidate_info side.",
    ],
    [
      "Add self-deletion guard: if (principal.getId().equals(userId)) return 400.",
      "Implement soft-delete via an is_deleted flag for auditability.",
    ]
  ),

  ...endpoint(
    "GET", "/api/admin/ai-usage",
    "Spring Boot only. AI usage per candidate since the server started, for the Admin AI Usage page. Counts only; no message content is stored.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"since\": ISO time, \"totals\": { chat_messages, drafts_generated, what_if_scores, model_tokens, moderation_blocked, moderation_unavailable, limit_hits, cut_offs, failures }, \"candidates\": [ { candidate_id, candidate_name, usage: {...}, last_activity, needs_review } ] }"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Access denied\" }"],
    ],
    [
      "Counts come from service.AiUsageMonitor, which the chat, Generate draft and what-if record into: answers (with tokens), moderation blocks and outages, hourly-limit hits, cut-offs and failures.",
      "Candidates are sorted busiest first (model tokens, then chat messages); needs_review marks anyone blocked by moderation or who hit an hourly limit.",
      "In memory: resets on restart and isn't shared between instances. Each event is also logged as one line (ai_usage event=… candidate=… tokens=…) for a lasting record.",
    ],
    [
      "Implemented in AiUsageController with class-level @PreAuthorize(\"hasRole('Admin')\").",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 7 – ROLE MANAGEMENT ENDPOINTS
  // ═══════════════════════════════════════════════════════════════
  h1("7. Role Management Endpoints"),
  divider(),

  ...endpoint(
    "GET", "/api/roles",
    "Retrieve all defined roles.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"roles\": [ { \"role_id\": \"<uuid>\", \"role_name\": \"...\" } ] }"],
      ["500", "application/json", "{ \"error\": \"Failed to retrieve roles\" }"],
    ],
    [
      "Queries the roles table ordered alphabetically by role_name.",
    ],
    [
      "Return List<RoleDTO> from a RoleRepository.findAllByOrderByRoleNameAsc().",
    ]
  ),

  ...endpoint(
    "POST", "/api/roles",
    "Create a new role.",
    "Yes", "Admin only",
    [
      "Content-Type: application/json",
      "{ \"role_name\": \"<string>\" }",
    ],
    null,
    [
      ["201", "application/json", "{ \"role_id\": \"<uuid>\", \"role_name\": \"...\" }"],
      ["400", "application/json", "{ \"error\": \"role_name is required\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to create role\" }"],
    ],
    [
      "role_name has a UNIQUE constraint in the database.",
      "Returns the generated UUID and name of the new role.",
    ],
    [
      "Return 409 Conflict if role_name already exists.",
    ]
  ),

  ...endpoint(
    "PUT", "/api/roles/<role_id>",
    "Rename an existing role.",
    "Yes", "Admin only",
    [
      "Content-Type: application/json",
      "{ \"role_name\": \"<new name>\" }",
    ],
    null,
    [
      ["200", "application/json", "{ \"message\": \"Role updated\" }"],
      ["400", "application/json", "{ \"error\": \"role_name is required\" }"],
      ["404", "application/json", "{ \"error\": \"Role not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to update role\" }"],
    ],
    [
      "Looks up the role by role_id UUID and updates role_name.",
      "All user_role_mapping entries automatically reflect the new name via the FK relationship.",
    ],
    [
      "Validate the new name for uniqueness before saving to avoid a DB constraint error.",
    ]
  ),

  ...endpoint(
    "DELETE", "/api/roles/<role_id>",
    "Permanently delete a role.",
    "Yes", "Admin only",
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"Role deleted\" }"],
      ["404", "application/json", "{ \"error\": \"Role not found\" }"],
      ["500", "application/json", "{ \"error\": \"Failed to delete role\" }"],
    ],
    [
      "Deletes the role row; cascades delete any user_role_mapping rows that reference this role.",
      "No guard against deleting built-in roles (Admin, Staff, User) — consider adding one.",
    ],
    [
      "Add a guard: prevent deletion of seeded system roles by checking a is_system flag.",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 8 – EMAIL DRAFT ENDPOINTS (Spring Boot only)
  // ═══════════════════════════════════════════════════════════════
  h1("8. Email Draft & Chat Endpoints"),
  body("Spring Boot only; there is no Flask equivalent. A draft is created in one of three ways: POST /api/drafts/generate from \"Draft email\" on a job match, where the app's own OpenAI model writes it with no setup for the candidate; the matches chat (POST /api/chat, end of this section), whose proposed draft the candidate saves with POST /api/drafts; or the candidate's own AI assistant over the MCP server (Section 9). All three follow the same writing rules (DraftingGuidelines) and save through EmailDraftService.createDraft. The other endpoints let the candidate review, edit and send drafts from the React app's Email Drafts page (/email-drafts)."),
  spacer(),
  body("The server never sends these emails. Sent from the platform's SES address, replies would not reach the candidate. The candidate sends from their own mail app, then calls mark-sent."),
  spacer(),
  body("Every endpoint acts on the candidateProfileId in the caller's JWT, so there is no candidate ID in the path. A draft belonging to another candidate returns 404, the same as one that does not exist. An account with no linked profile gets 403.", { bold: true }),
  spacer(),
  body("Draft object returned by every endpoint:", { bold: true }),
  code(DRAFT_OBJECT),
  bullet("job_title, company_name and job_url come from the job listing and are null if the job has since been deleted."),
  bullet("job_url is the employer's own posting (job_url_direct) when the scrape found one, otherwise the job board's job_url."),
  bullet("recipient is null when the job has no contact email; the draft is then a cover letter for applying on the job page."),
  divider(),

  ...endpoint(
    "GET", "/api/drafts",
    "List the caller's drafts, most recently updated first.",
    DRAFT_ACCESS, DRAFT_ROLES,
    null,
    [["status", "string", "No", "DRAFT or SENT (case-sensitive); omit for both"]],
    [
      ["200", "application/json", `{ "drafts": [ ${DRAFT_OBJECT} ] }`],
      ["400", "application/json", "{ \"error\": \"Invalid value for 'status' (expected Status)\" }"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Email drafts need a linked candidate profile\" }"],
    ],
    [
      "Reads candidateProfileId from the JWT principal; never from the request.",
      "Loads the candidate's drafts, optionally filtered by status, ordered by updated_at descending.",
      "Joins each draft to its job listing in one findByIdIn query for job_title, company_name and job_url.",
    ],
    [
      "Implemented in EmailDraftController.listDrafts and EmailDraftService.listDrafts.",
      "status binds straight to the EmailDraft.Status enum, so an unknown value is a 400 from GlobalExceptionHandler.",
    ]
  ),

  ...endpoint(
    "POST", "/api/drafts",
    "Save a draft the candidate already has, typically one the matches chat proposed (Save to Email Drafts).",
    DRAFT_ACCESS, DRAFT_ROLES,
    [
      "Content-Type: application/json",
      "{",
      "  \"job_id\"    : UUID    (required)",
      "  \"recipient\" : string  (optional; one of the job's contact emails, or null)",
      "  \"subject\"   : string  (required, max 300)",
      "  \"body\"      : string  (required, max 20000)",
      "}",
    ],
    null,
    [
      ["201", "application/json", DRAFT_OBJECT],
      ["400", "application/json", "{ \"error\": \"Recipient must be one of the job's contact emails; leave it empty if the job has none\" }  — or validation / unknown job"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Email drafts need a linked candidate profile\" }"],
    ],
    [
      "Saves through EmailDraftService.createDraft, so the recipient check is the same as for MCP: one of the job's scraped contact emails, or none.",
      "The candidate can change the recipient afterwards with PUT /api/drafts/<draft_id>.",
    ],
    null
  ),

  ...endpoint(
    "POST", "/api/drafts/generate",
    "Write an application email for one job with the app's OpenAI chat model and save it as a DRAFT. Takes a few seconds.",
    DRAFT_ACCESS, DRAFT_ROLES,
    [
      "Content-Type: application/json",
      "{",
      "  \"job_id\"       : UUID    (required)",
      "  \"instructions\" : string  (optional, max 500; e.g. \"Friendly tone, mention my capstone\")",
      "}",
    ],
    null,
    [
      ["201", "application/json", DRAFT_OBJECT],
      ["400", "application/json", "{ \"error\": \"job_id is required.\" }  — or instructions over 500 characters, or flagged by moderation (\"Those instructions can't be used...\"; the model is never called)"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Email drafts need a linked candidate profile\" }"],
      ["404", "application/json", "{ \"error\": \"Job not found\" }"],
      ["429", "application/json", "{ \"error\": \"You've generated a lot of drafts in the last hour. Try again later, or use your own AI assistant.\" }"],
      ["502", "application/json", "{ \"error\": \"Couldn't generate a draft right now. Try again, or use your own AI assistant.\" }"],
    ],
    [
      "Checks the per-candidate limit (app.drafts.generations-per-hour, default 10, sliding hour, in memory). Failed attempts count too.",
      "Builds the same job and profile context as the MCP tool get_application_context. The description is cut to 6,000 characters and sent as JSON; the system prompt says to treat it as data, not instructions. The resume is never sent.",
      "Calls the chat model (default gpt-5-mini, 30s timeout) for a structured { subject, body }. Instructions are followed unless they conflict with the rules; anything not in the profile is left out.",
      "Sets the recipient to the job's first scraped contact email, or null (a cover letter). The model never chooses the recipient.",
      "Returns 502 if the model call fails, times out, or returns an empty or unparseable draft; nothing is saved.",
    ],
    [
      "Implemented in EmailDraftController.generateDraft, EmailDraftGenerator (Spring AI ChatClient .entity(GeneratedEmail.class)) and DraftGenerationLimiter.",
      "Config: spring.ai.openai.chat.options.model (env OPENAI_CHAT_MODEL), spring.ai.openai.chat.timeout, app.drafts.generations-per-hour (env DRAFT_GENERATIONS_PER_HOUR).",
    ]
  ),

  ...endpoint(
    "PUT", "/api/drafts/<draft_id>",
    "Save the candidate's edits to an unsent draft. Replaces recipient, subject and body.",
    DRAFT_ACCESS, DRAFT_ROLES,
    [
      "Content-Type: application/json",
      "{",
      "  \"recipient\" : string  (optional; valid email, max 320; \"\" or null clears it)",
      "  \"subject\"   : string  (required, not blank, max 300)",
      "  \"body\"      : string  (required, not blank, max 20000)",
      "}",
    ],
    null,
    [
      ["200", "application/json", DRAFT_OBJECT],
      ["400", "application/json", "{ \"error\": \"<field messages joined with '; '>\" }"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Email drafts need a linked candidate profile\" }"],
      ["404", "application/json", "{ \"error\": \"Draft not found or already sent\" }"],
    ],
    [
      "Looks the draft up by id AND the caller's candidate id, so another candidate's draft is a 404.",
      "Only drafts with status DRAFT can be edited; a SENT draft is also a 404.",
      "Unlike drafts created over MCP, the candidate may enter any recipient address here.",
      "updated_at is refreshed on save.",
    ],
    [
      "Request DTO: UpdateEmailDraftRequest with @Email, @NotBlank and @Size constraints, validated with @Valid.",
      "Implemented in EmailDraftController.updateDraft and EmailDraftService.updateDraft.",
    ]
  ),

  ...endpoint(
    "POST", "/api/drafts/<draft_id>/mark-sent",
    "Record that the candidate sent the draft from their own mail app. No email is sent.",
    DRAFT_ACCESS, DRAFT_ROLES,
    null, null,
    [
      ["200", "application/json", `${DRAFT_OBJECT}  — with status "SENT"`],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Email drafts need a linked candidate profile\" }"],
      ["404", "application/json", "{ \"error\": \"Draft not found or already sent\" }"],
    ],
    [
      "Sets status to SENT and sent_at to the current time.",
      "Sent drafts are read-only from then on; they can still be deleted.",
      "The React page saves any unsaved edits before calling this, so the record matches what was sent.",
    ],
    null
  ),

  ...endpoint(
    "DELETE", "/api/drafts/<draft_id>",
    "Permanently delete a draft, sent or unsent.",
    DRAFT_ACCESS, DRAFT_ROLES,
    null, null,
    [
      ["200", "application/json", "{ \"message\": \"Draft deleted\" }"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"Email drafts need a linked candidate profile\" }"],
      ["404", "application/json", "{ \"error\": \"Draft not found\" }"],
    ],
    [
      "Deletes only if the draft belongs to the caller's candidate profile.",
      "Drafts are also removed automatically when their candidate or job listing is deleted (ON DELETE CASCADE).",
    ],
    null
  ),

  ...endpoint(
    "POST", "/api/chat",
    "Answer one message in the \"Ask about your matches\" chat on the Job Matches page (candidates only).",
    DRAFT_ACCESS, DRAFT_ROLES,
    [
      "Content-Type: application/json",
      "{ \"messages\": [ { \"role\": \"user\"|\"assistant\", \"content\": string }, ... ] }",
      "  1-20 messages, oldest first; the last must be from the user; content max 8000",
    ],
    null,
    [
      ["200", "application/json", "{ \"reply\": string, \"draft_proposals\": [ { job_id, job_title, company_name, recipient, subject, body } ] }"],
      ["400", "application/json", "{ \"error\": \"The last message must be from the user.\" }  — or other validation"],
      ["401", "application/json", "{ \"error\": \"Authentication required\" }"],
      ["403", "application/json", "{ \"error\": \"The chat needs a linked candidate profile\" }"],
      ["429", "application/json", "{ \"error\": \"You've sent a lot of messages in the last hour. Try again later, or use Draft email on a match.\" }"],
      ["502", "application/json", "{ \"error\": \"The assistant couldn't answer right now. Try again in a moment.\" }"],
    ],
    [
      "Stateless: the React panel keeps the conversation and sends the recent messages each time. Nothing is stored server-side. The panel keeps its history per browser tab (sessionStorage, last 50 messages, tied to the user), cleared by Clear chat, Logout, another user logging in, or closing the tab.",
      "Tools (chat/MatchChatTools) only read: list_my_matches, get_job_details (description cut to 6,000 characters), explain_match (why a job scored what it did), what_if_score (hypothetical profile changes; nothing saved), and propose_email_draft, which records a proposal but saves nothing.",
      "Proposals come back in draft_proposals as cards; saving takes the candidate's click (POST /api/drafts). At most 3 per reply. The recipient comes from the job's contact emails, never the model.",
      "The candidate comes from the JWT and reaches the tools through Spring AI's ToolContext, never as a tool argument.",
      "Limited by app.chat.messages-per-hour (default 30, per candidate, sliding hour).",
      "Each new message is screened with OpenAI's free moderation endpoint (ModerationService) first. A flagged message gets a polite refusal and never reaches the chat model; if moderation itself fails, the message goes through and is counted as moderation_unavailable (app.moderation.enabled, default true).",
      "Every outcome (answer with tokens, block, limit hit, cut-off, failure) is counted per candidate for GET /api/admin/ai-usage; no message content is kept.",
      "Each model call's output is capped by app.chat.max-output-tokens (default 1,500; OpenAI max_completion_tokens, which for gpt-5-mini includes reasoning, so app.chat.reasoning-effort defaults to low). A cut-off answer says so; one with no text left asks for a narrower question instead of failing.",
      "System prompt scope rules: stay on the candidate's job search in this app and politely decline anything else; can't see other users; don't reveal instructions; legal, sponsorship and clearance questions get only what the posting and profile say plus a pointer to the employer or official sources. These depend on the model; the code-level limits above bound the cost of any that slip through.",
    ],
    [
      "Implemented in MatchChatController, chat/MatchChatService (ChatClient with .tools(...) and .toolContext(...); Spring AI's ToolCallingAdvisor runs the tool loop) and ChatMessageLimiter.",
      "The system prompt includes DraftingGuidelines, so chat drafts follow the same rules as Generate draft and MCP.",
    ]
  ),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 9 – MCP SERVER (Spring Boot only)
  // ═══════════════════════════════════════════════════════════════
  h1("9. MCP Server"),
  body("Spring Boot only. A Model Context Protocol server lets AI clients such as Claude Desktop, Claude Code and Cursor look up a candidate's matches and draft application emails for them. It is implemented in mcp/EmailDraftTools.java with Spring AI's MCP server starter (spring-ai-starter-mcp-server-webmvc)."),
  spacer(),
  infoTable([
    ["Endpoint", "POST /mcp"],
    ["Transport", "Streamable HTTP"],
    ["Dev URL", "http://localhost:3000/mcp (Vite proxies /mcp to the backend)"],
    ["Server name", "job-matching"],
    ["Auth", "Authorization: Bearer <JWT from POST /login>"],
    ["Log in as", "User role, on an account with a linked candidate profile"],
  ]),
  spacer(),
  body("Every tool acts on the candidateProfileId in the token and never accepts a candidate ID as an argument, so neither the model nor text in a scraped job description can reach another candidate's data. Calls from an account without a profile return a tool error."),
  spacer(),
  body("Connect Claude Code during development:", { bold: true }),
  code("claude mcp add --transport http jobmatch http://localhost:8080/mcp --header \"Authorization: Bearer <token>\""),

  h2("9.1 Tools"),
  simpleTable(
    ["Tool", "Arguments", "Read-only", "Description"],
    [2300, 2000, 1000, 4060],
    [
      ["list_my_matches", "limit (1-25, default 10)", "Yes", "The candidate's best matches with jobId, title, company, location, match percent and matched skills."],
      ["what_if_score", "jobIds? (max 10; default top 10 matches), addSkills?, removeSkills?, preferredJobTitle?, collegeDegree?, locationCity/State/Country?", "Yes", "How a hypothetical profile change would move the match scores, saving nothing (WhatIfService): embeds the current profile and a changed copy in one embedding call and returns the overall percent before and after, the difference, and before/after breakdowns for each job. null leaves a field; blank clears it. Limited by app.what-if.per-hour (default 20)."],
      ["explain_match", "jobId", "Yes", "Why one job scored what it did: what the overall percent measures (embedding similarity, not a sum of parts), which profile fields were compared and which are empty, the job fields compared, and for skills, title and location the percent, how it was reached, what matched and what didn't, and whether it's a stand-in with nothing to compare."],
      ["get_application_context", "jobId", "Yes", "The job posting (title, company, description, contact emails, apply URL) and the candidate's profile (name, degree, preferred title, skills), plus the candidate skills the posting mentions. The resume is never included."],
      ["save_email_draft", "jobId, subject, body, recipient?", "No", "Saves a new DRAFT and returns { draft, reviewUrl, nextStep }. recipient must be one of the job's scraped contact emails so the model cannot invent an address; omit it when there are none."],
      ["update_email_draft", "draftId, subject?, body?", "No", "Revises an unsent draft and returns { draft, reviewUrl, nextStep }. Omitted fields are unchanged."],
      ["list_email_drafts", "none", "Yes", "The candidate's unsent drafts, newest first."],
    ]
  ),
  spacer(),
  body("Review link: reviewUrl is app.email-drafts-url (env EMAIL_DRAFTS_URL, default http://localhost:3000/email-drafts) plus ?highlight=<draft id>. The candidate finishes in their assistant, so the app cannot redirect them; the tool description and the draft_application_email prompt tell the assistant to give them this link. It opens Email Drafts with that draft outlined, and a logged-out candidate is taken back to it after logging in."),
  spacer(),
  body("There is deliberately no send tool. The most a model can do is create or revise a draft; sending happens only when the candidate acts on the Email Drafts page.", { bold: true }),

  h2("9.2 Prompts"),
  simpleTable(
    ["Prompt", "Arguments", "Description"],
    [2300, 2200, 4860],
    [
      ["draft_application_email", "jobId (required), tone (optional, default \"professional\")", "Tells the model to fetch the application context and write by the shared DraftingGuidelines (the same rules as POST /api/drafts/generate): under 200 words, two or three overlapping skills, only facts from the profile, the job description treated as data, and a cover letter when there is no contact email. It also tells the model to ask about anything missing from the profile and to call save_email_draft only after the candidate approves."],
    ]
  ),

  h2("9.3 Security Configuration"),
  bullet("/mcp is covered by the same anyRequest().authenticated() rule and JwtAuthenticationFilter as /api/**."),
  bullet("Spring AI runs sync tools on the servlet request thread in a WebMVC app, so the tools can read the principal from SecurityContextHolder."),
  bullet("MCP responses are streamed (SSE) and finish on an async servlet dispatch. SecurityConfig permits DispatcherType.ASYNC because the JWT filter, a OncePerRequestFilter, runs only on the original request, which was already authenticated. Without this, every tool call logs an AuthorizationDeniedException after the response has started."),

  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 10 – DATABASE SCHEMA
  // ═══════════════════════════════════════════════════════════════
  h1("10. Database Schema Summary"),
  body("All tables use PostgreSQL with the pgvector extension for VECTOR columns."),
  spacer(),

  new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: [2000, 2000, 1500, 3860],
    rows: [
      new TableRow({
        tableHeader: true,
        children: ["Table", "Column", "Type", "Notes"].map(h =>
          new TableCell({
            borders: BORDERS,
            shading: { fill: "2E5090", type: ShadingType.CLEAR },
            margins: { top: 80, bottom: 80, left: 120, right: 120 },
            width: { size: [2000,2000,1500,3860][["Table","Column","Type","Notes"].indexOf(h)], type: WidthType.DXA },
            children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 18, font: "Arial" })] })]
          })
        )
      }),
      ...[
        ["job_listings","id","UUID PK","Auto-generated"],
        ["job_listings","title","VARCHAR(500)","NOT NULL"],
        ["job_listings","company_name","VARCHAR(500)",""],
        ["job_listings","job_url","TEXT","NOT NULL; unique with site"],
        ["job_listings","description","TEXT",""],
        ["job_listings","site","VARCHAR(100)","NOT NULL (indeed/google)"],
        ["job_listings","embedding","VECTOR(1536)","OpenAI ada-002; nullable"],
        ["job_listings","date_scraped","TIMESTAMP","Auto-set on insert"],
        ["","","",""],
        ["candidate_info","id","UUID PK","Auto-generated"],
        ["candidate_info","candidate_name","VARCHAR(500)","NOT NULL"],
        ["candidate_info","email","VARCHAR(500)",""],
        ["candidate_info","skills","JSON","Array of strings"],
        ["candidate_info","resume_data","TEXT","Base64-encoded PDF"],
        ["candidate_info","embedding","VECTOR(1536)","OpenAI ada-002; nullable"],
        ["","","",""],
        ["user_accounts","id","UUID PK","Auto-generated"],
        ["user_accounts","email","VARCHAR(500)","UNIQUE NOT NULL"],
        ["user_accounts","username","VARCHAR(200)","UNIQUE NOT NULL"],
        ["user_accounts","password_hash","VARCHAR(500)","PBKDF2 via Werkzeug"],
        ["user_accounts","candidate_profile_id","UUID FK","→ candidate_info; UNIQUE; SET NULL"],
        ["","","",""],
        ["roles","role_id","UUID PK","Auto-generated"],
        ["roles","role_name","VARCHAR(200)","UNIQUE NOT NULL"],
        ["","","",""],
        ["user_role_mapping","user_id","UUID FK","→ user_accounts; composite PK"],
        ["user_role_mapping","role_id","UUID FK","→ roles; composite PK"],
        ["","","",""],
        ["email_drafts","id","UUID PK","Spring Boot only; see db/email_drafts.sql"],
        ["email_drafts","candidate_id","UUID FK","→ candidate_info; ON DELETE CASCADE"],
        ["email_drafts","job_id","UUID FK","→ job_listings; ON DELETE CASCADE"],
        ["email_drafts","recipient","VARCHAR(320)","Nullable; null = cover letter"],
        ["email_drafts","subject","VARCHAR(300)","NOT NULL"],
        ["email_drafts","body","TEXT","NOT NULL"],
        ["email_drafts","status","VARCHAR(16)","DRAFT | SENT"],
        ["email_drafts","created_at / updated_at","TIMESTAMP","Auto-set"],
        ["email_drafts","sent_at","TIMESTAMP","Set by mark-sent"],
      ].map(([table, col, type, notes], i) =>
        new TableRow({
          children: [table, col, type, notes].map((val, ci) =>
            new TableCell({
              borders: BORDERS,
              shading: { fill: i % 2 === 0 ? "FFFFFF" : "F5F7FA", type: ShadingType.CLEAR },
              margins: { top: 60, bottom: 60, left: 120, right: 120 },
              width: { size: [2000,2000,1500,3860][ci], type: WidthType.DXA },
              children: [new Paragraph({ children: [new TextRun({ text: val, size: 18, font: "Courier New" })] })]
            })
          )
        })
      )
    ]
  }),

  spacer(),
  new Paragraph({ children: [new PageBreak()] }),

  // ═══════════════════════════════════════════════════════════════
  // SECTION 11 – BACKGROUND JOBS
  // ═══════════════════════════════════════════════════════════════
  h1("11. Background Jobs & Startup Behavior"),
  spacer(),

  new Table({
    width: { size: CONTENT_WIDTH, type: WidthType.DXA },
    columnWidths: [2800, 2200, 4360],
    rows: [
      new TableRow({
        tableHeader: true,
        children: ["Job", "Trigger", "Behavior"].map(h =>
          new TableCell({
            borders: BORDERS,
            shading: { fill: "2E5090", type: ShadingType.CLEAR },
            margins: { top: 80, bottom: 80, left: 120, right: 120 },
            children: [new Paragraph({ children: [new TextRun({ text: h, bold: true, color: "FFFFFF", size: 18, font: "Arial" })] })]
          })
        )
      }),
      ...[
        ["auto_scrape_jobs()", "App startup", "Reads all distinct preferred_job_title values from candidate_info; calls scrape_jobs_for_title() for each. Falls back to 'software engineer' if no candidates exist."],
        ["scrape_jobs_for_title(title)", "New candidate created OR startup", "Checks scrape_state.json for a TTL (24 hrs). If stale/absent, scrapes Indeed+Google for that title (50 results, last 24h), saves, and generates embeddings."],
        ["delete_old_jobs()", "APScheduler — daily at 02:00 UTC", "Deletes all job_listings rows where date_scraped < (now - 30 days)."],
      ].map(([job, trigger, behavior], i) =>
        new TableRow({
          children: [job, trigger, behavior].map((val, ci) =>
            new TableCell({
              borders: BORDERS,
              shading: { fill: i % 2 === 0 ? "FFFFFF" : "F5F7FA", type: ShadingType.CLEAR },
              margins: { top: 80, bottom: 80, left: 120, right: 120 },
              width: { size: [2800,2200,4360][ci], type: WidthType.DXA },
              children: [new Paragraph({ children: [new TextRun({ text: val, size: 18, font: "Arial" })] })]
            })
          )
        })
      )
    ]
  }),

  spacer(),
  body("Spring Boot equivalents:", { bold: true }),
  bullet("Use @EventListener(ApplicationReadyEvent.class) to trigger auto_scrape_jobs on startup."),
  bullet("Use @Scheduled(cron = \"0 0 2 * * ?\") for the nightly delete job."),
  bullet("Replace the scrape_state.json TTL file with a last_scraped_at column in a job_scrape_log table."),
  bullet("Use @Async with a ThreadPoolTaskExecutor for background scraping threads."),

];

// ── Build Document ─────────────────────────────────────────────────────────────

const doc = new Document({
  numbering: {
    config: [
      {
        reference: "bullets",
        levels: [{
          level: 0,
          format: "bullet",
          text: "•",
          alignment: AlignmentType.LEFT,
          style: { paragraph: { indent: { left: 540, hanging: 360 } } }
        }]
      }
    ]
  },
  styles: {
    default: {
      document: { run: { font: "Arial", size: 20 } }
    },
    paragraphStyles: [
      {
        id: "Heading1", name: "Heading 1", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 40, bold: true, font: "Arial", color: "2E5090" },
        paragraph: { spacing: { before: 400, after: 160 }, outlineLevel: 0,
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "2E5090", space: 1 } } }
      },
      {
        id: "Heading2", name: "Heading 2", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 28, bold: true, font: "Arial", color: "1A3A6B" },
        paragraph: { spacing: { before: 280, after: 120 }, outlineLevel: 1 }
      },
      {
        id: "Heading3", name: "Heading 3", basedOn: "Normal", next: "Normal", quickFormat: true,
        run: { size: 24, bold: true, font: "Arial", color: "333333" },
        paragraph: { spacing: { before: 200, after: 80 }, outlineLevel: 2 }
      },
    ]
  },
  sections: [{
    properties: {
      page: {
        size: { width: 12240, height: 15840 },
        margin: { top: 1440, right: 1440, bottom: 1440, left: 1440 }
      }
    },
    headers: {
      default: new Header({
        children: [new Paragraph({
          border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: "2E5090", space: 1 } },
          children: [
            new TextRun({ text: "Beshton Job Matching Platform  —  REST API Implementation Document", size: 16, font: "Arial", color: "888888" }),
          ]
        })]
      })
    },
    footers: {
      default: new Footer({
        children: [new Paragraph({
          border: { top: { style: BorderStyle.SINGLE, size: 2, color: "CCCCCC", space: 1 } },
          tabStops: [{ type: TabStopType.RIGHT, position: TabStopPosition.MAX }],
          children: [
            new TextRun({ text: "Confidential  |  For internal use", size: 16, font: "Arial", color: "AAAAAA" }),
            new TextRun({ text: "\tPage ", size: 16, font: "Arial", color: "AAAAAA" }),
            new TextRun({ children: [PageNumber.CURRENT], size: 16, font: "Arial", color: "AAAAAA" }),
          ]
        })]
      })
    },
    children
  }]
});

Packer.toBuffer(doc).then(buffer => {
  fs.writeFileSync("Beshton_API_Implementation_Document.docx", buffer);
  console.log("Done: Beshton_API_Implementation_Document.docx");
});
