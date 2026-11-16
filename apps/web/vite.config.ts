import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import type { Plugin, ProxyOptions } from 'vite'
import { defineConfig } from 'vitest/config'

const vitestEntry = resolve(fileURLToPath(new URL('.', import.meta.url)), 'node_modules/vitest/dist/index.js')

/** One Vitest module on Windows. Two drive-letter spellings otherwise leave describe() with no runner. */
function singleVitestModule(): Plugin {
  return {
    name: 'single-vitest-module',
    enforce: 'pre',
    resolveId(source) {
      if (source === 'vitest') {
        return vitestEntry
      }
      return null
    },
  }
}

function serviceProxy(target: string): ProxyOptions {
  return {
    target,
    changeOrigin: true,
    xfwd: true,
    // Browser is same-origin to the tunnel; strip Origin so Spring CORS does not
    // reject Cloudflare hosts if the backend qa profile is missing.
    configure: (proxy) => {
      proxy.on('proxyReq', (proxyReq) => {
        proxyReq.removeHeader('origin')
      })
    },
  }
}

export default defineConfig(({ mode }) => {
  const qa = mode === 'qa'

  return {
    plugins: [react(), tailwindcss(), ...(process.env.VITEST ? [singleVitestModule()] : [])],
    resolve: process.env.VITEST ? { alias: { vitest: vitestEntry } } : undefined,
    server: {
      port: 5173,
      ...(qa
        ? {
            // QA / Cloudflare tunnels only (vite --mode qa)
            host: true,
            // Leading-dot form matches any *.trycloudflare.com quick-tunnel host.
            allowedHosts: ['.trycloudflare.com'],
            proxy: {
              '/api/auth': serviceProxy('http://127.0.0.1:8081'),
              '/api/me': serviceProxy('http://127.0.0.1:8081'),
              '/api/courses': serviceProxy('http://127.0.0.1:8082'),
              '/api/materials': serviceProxy('http://127.0.0.1:8082'),
              '/api/study': serviceProxy('http://127.0.0.1:8082'),
              '/api/rag': serviceProxy('http://127.0.0.1:8082'),
              '/api/match': serviceProxy('http://127.0.0.1:8082'),
            },
          }
        : {}),
    },
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.ts',
    },
  }
})
