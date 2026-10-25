import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    // Forward API calls to the Spring Boot backend during development.
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        // When the backend is down, answer 502 Bad Gateway instead of a bare 500 so the
        // UI can tell "server unreachable" apart from a real server error.
        configure: (proxy) => {
          proxy.on('error', (_err, _req, res) => {
            if ('writeHead' in res && !res.headersSent) {
              res.writeHead(502, { 'Content-Type': 'text/plain' }).end('Backend unavailable')
            }
          })
        },
      },
    },
  },
  preview: {
    host: '127.0.0.1',
  },
  test: {
    environment: 'jsdom',
    // Worker threads instead of child processes, so Deno needs no --allow-run.
    pool: 'threads',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
