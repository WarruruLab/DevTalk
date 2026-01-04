import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5174, // 여기에 원하는 포트 번호를 입력하세요
    strictPort: true, // 포트가 이미 사용 중일 때 자동으로 다음 포트로 넘어가는 것을 방지 (선택 사항)
  }
})
