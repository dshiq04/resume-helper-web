import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// 前后端分离：前端产物默认输出到 frontend/dist，由 Nginx 等静态服务器托管；
// 开发环境通过 Vite 代理把 /api 转发到后端。
const frontendRoot = fileURLToPath(new URL(".", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, frontendRoot, "");
  // 默认监听所有网卡，方便同一局域网内的手机/平板访问开发服务器。
  // 如需只允许本机访问，可设置 VITE_DEV_HOST=127.0.0.1。
  const devHost = env.VITE_DEV_HOST || "0.0.0.0";
  return {
    plugins: [react()],
    server: {
      host: devHost,
      port: 5173,
      strictPort: true,
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET || "http://127.0.0.1:18080",
          changeOrigin: true,
        },
      },
    },
    preview: {
      host: devHost,
      port: 4173,
      strictPort: true,
    },
    build: {
      outDir: "dist",
      emptyOutDir: true,
      chunkSizeWarningLimit: 2000,
    },
  };
});
