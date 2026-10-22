/// <reference types="vitest/config" />
import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// The backend from docker compose listens on 127.0.0.1:8088 (APP_PORT in .env)
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8088'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': { target: apiTarget, changeOrigin: false },
      // OIDC entry and callback of the backend (7.2); in production Caddy routes them (M7)
      '/oauth2': { target: apiTarget, changeOrigin: false },
      '/login/oauth2': { target: apiTarget, changeOrigin: false },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['src/test/setup.ts'],
    restoreMocks: true,
  },
})
