import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// El front habla siempre con /api/*; en desarrollo Vite lo redirige al gateway
// (localhost:8080) para evitar problemas de CORS. Para apuntar a otra URL,
// definir VITE_API_URL en un archivo .env.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
