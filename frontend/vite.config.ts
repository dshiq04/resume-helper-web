import { defineConfig, loadEnv } from "vite";
import react from "@vitejs/plugin-react";
import { fileURLToPath } from "node:url";

// 前端构建产物直接写入后端静态资源目录，最终只产出一个可执行 jar。
const frontendRoot = fileURLToPath(new URL(".", import.meta.url));
const backendStatic = fileURLToPath(new URL("../jobtrack-web/src/main/resources/static", import.meta.url));

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, frontendRoot, "");
  return {
    plugins: [react()],
    server: {
      host: "127.0.0.1",
      port: 5173,
      strictPort: true,
      proxy: {
        "/api": {
          target: env.VITE_API_PROXY_TARGET || "http://127.0.0.1:18080",
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: backendStatic,
      emptyOutDir: true,
      chunkSizeWarningLimit: 2000,
    },
  };
});
