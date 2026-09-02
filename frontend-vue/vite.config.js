import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// 前端只连两个后端：
//   /java-api -> Java(8080)，去掉前缀后 Java 侧仍是 /api/**
//   /py-api   -> Python(8000)
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/java-api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/java-api/, '')
      },
      '/py-api': {
        target: 'http://127.0.0.1:8000',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/py-api/, '')
      }
    }
  }
})
