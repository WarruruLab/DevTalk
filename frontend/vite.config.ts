import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  base: '/devtalk/',
  plugins: [react()],
  server: {
    port: 5174,
    strictPort: true,
    proxy: {
      '/devtalk/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/devtalk\/api/, '/api'),
      },
    },
  },
})
