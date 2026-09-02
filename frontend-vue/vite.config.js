import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { execSync } from 'node:child_process'

// Java 仍在 Windows 本机(8080)。Python 端跑在 WSL，这里动态取 WSL 的 IP 作为代理目标；
// 前端本身跑在 WSL 内、或想手工指定时，用 PY_BASE 覆盖（如 http://127.0.0.1:8000）。
function pythonBase() {
  if (process.env.PY_BASE) return process.env.PY_BASE
  try {
    const ips = execSync('wsl hostname -I', { encoding: 'utf8', timeout: 5000 }).trim()
    const ip = ips.split(/\s+/)[0]
    if (ip) return `http://${ip}:8000`
  } catch (_) {
    // 取不到 WSL IP（如在 WSL 内跑前端），退回本地
  }
  return 'http://127.0.0.1:8000'
}

const PY = pythonBase()
console.log('[vite] /py-api ->', PY)

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
        target: PY,
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/py-api/, '')
      }
    }
  }
})
