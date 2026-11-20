/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_TITLE: string;
  readonly VITE_APP_VERSION: string;
  readonly VITE_APP_DEFAULT_LANGUAGE: string;
  readonly VITE_SHOW_DEVELOPMENT_WATERMARK: string;
  readonly VITE_API_BASE_URL: string;
  readonly VITE_API_PROXY_TARGET?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
