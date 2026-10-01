import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 로컬 개발: Spring(:8080)으로 프록시해 CORS 없이 같은 출처로 호출한다.
// 배포(Vercel)에서는 vercel.json rewrites가 같은 역할을 한다.
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/healthz': 'http://localhost:8080',
    },
  },
})
