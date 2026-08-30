import path from "node:path"

import tailwindcss from "@tailwindcss/vite"
import react from "@vitejs/plugin-react"
import { loadEnv } from "vite"
import { defineConfig } from "vitest/config"

export default defineConfig(({ command, mode }) => {
  if (command === "build" && !loadEnv(mode, __dirname).VITE_API_BASE_URL) {
    throw new Error(`VITE_API_BASE_URL is required for ${mode} builds`)
  }

  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      proxy: {
        "/api": "http://localhost:8080",
      },
    },
    test: {
      environment: "jsdom",
      setupFiles: "./src/test/setup.ts",
    },
  }
})
