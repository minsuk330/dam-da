import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 로컬 개발: Spring으로 프록시해 CORS 없이 같은 출처로 호출한다. 다른 포트면 API_PROXY_TARGET으로 바꾼다.
// 배포(Vercel)에서는 vercel.json rewrites가 같은 역할을 한다.
const apiTarget = process.env.API_PROXY_TARGET ?? 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    proxy: {
      '/api': apiTarget,
      '/healthz': apiTarget,
    },
  },
})
