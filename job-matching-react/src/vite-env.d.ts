/// <reference types="vite/client" />

interface ImportMetaEnv {
  // Absolute backend origin for a production build (no dev proxy exists outside `vite dev`).
  // Leave unset in dev — requests stay relative and go through the Vite proxy instead.
  readonly VITE_API_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
