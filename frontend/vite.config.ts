import { fileURLToPath, URL } from 'node:url'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // Forwards to the Spring Boot backend during development so the browser
      // stays on one origin and CORS is not needed locally.
      // Default to 8080 to match backend/.env.example; override with VITE_API_TARGET for local setups (e.g. 8099).
      '/api': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      // OAuth2 sign-in is a full-page navigation, not a fetch. These paths must
      // be proxied too or the browser would hit the Vite dev server (a 404)
      // instead of the backend's authorization and callback endpoints.
      '/oauth2': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
      '/login/oauth2/code': {
        target: process.env.VITE_API_TARGET ?? 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
