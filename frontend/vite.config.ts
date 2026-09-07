import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// 开发服务器：5173，/api 代理到本地 Spring Boot（8080）
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      // RTP-WS：WebSocket 通知推送代理（ws:true 升级协议）
      '/ws': {
        target: 'http://127.0.0.1:8080',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  build: {
    // element-plus 独立分包后约 900KB（第三方库，长期缓存），放宽阈值避免误导性告警
    chunkSizeWarningLimit: 1000,
    rollupOptions: {
      output: {
        // B4-P9：拆分主包，框架与组件库独立分包利于缓存
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue'],
          axios: ['axios'],
          // B5-P7：echarts 独立分包（趋势/饼图/柱状，仅三个页面按需加载）
          echarts: ['echarts'],
        },
      },
    },
  },
})
