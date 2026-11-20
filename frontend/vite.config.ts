import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// 前端构建产物直接写入后端静态资源目录，最终只产出一个可执行 jar。
const backendStatic = fileURLToPath(new URL("../jobtrack-web/src/main/resources/static", import.meta.url));

export default defineConfig({
  plugins: [react()],
  server: {
    host: "127.0.0.1",
    port: 5173,
    strictPort: true,
    proxy: {
      "/api": {
        target: process.env.JOBTRACK_API || "http://127.0.0.1:18080",
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: backendStatic,
    emptyOutDir: true,
    chunkSizeWarningLimit: 2000,
  },
});
