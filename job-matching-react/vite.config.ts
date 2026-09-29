import react from '@vitejs/plugin-react'
import { defineConfig, type ProxyOptions } from 'vite'
import type { IncomingMessage } from 'node:http'

const backendTarget = process.env.VITE_BACKEND_URL ?? 'http://localhost:8080'

// A few backend routes (/register, /login, /forgot-password, /reset-password/:token) share
// their path with a client-side page route of the same name, because the Flask app they
// port used the same URL for the HTML form and its POST target. Only bypass the proxy back
// to Vite's own SPA handling for an actual page navigation (Accept: text/html); every
// XHR/fetch call from api/auth.ts still gets forwarded to the backend.
function apiOnly(): Pick<ProxyOptions, 'target' | 'changeOrigin' | 'bypass'> {
  return {
    target: backendTarget,
    changeOrigin: true,
    bypass: (req: IncomingMessage) => {
      if (req.headers.accept?.includes('text/html')) {
        return '/index.html'
      }
      return undefined
    },
  }
}

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      // The Spring Boot backend has no CORS configuration, so the dev server proxies
      // API + auth routes to it and the browser only ever talks to same-origin :3000.
      '/api': { target: backendTarget, changeOrigin: true },
      // MCP server, so the setup command on the job matches page can use this origin.
      '/mcp': { target: backendTarget, changeOrigin: true },
      '/login': apiOnly(),
      '/register': apiOnly(),
      '/verify-email': apiOnly(),
      '/logout': apiOnly(),
      '/forgot-password': apiOnly(),
      '/reset-password': apiOnly(),
    },
  },
})
