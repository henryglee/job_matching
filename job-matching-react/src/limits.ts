// Size limits on a candidate profile; keep in step with the backend's CandidateLimits.java, which
// enforces them (these just stop the user typing past them).
export const NAME_MAX = 500
export const EMAIL_MAX = 500
export const DEGREE_MAX = 500
export const JOB_TITLE_MAX = 500
export const LOCATION_MAX = 200
export const MAX_SKILLS = 50
export const SKILL_MAX = 100

// Passwords. New ones (register, reset) are 8-128 characters; login allows up to 1024 so an older,
// longer password still works. Both match the backend's request validation.
export const PASSWORD_MAX = 128
export const LOGIN_PASSWORD_MAX = 1024
