import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Built files go into Spring Boot's static folder, so one app serves the bot API and the dashboard.
// In dev (npm run dev) the API calls are proxied to the running Spring Boot app.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../src/main/resources/static",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://127.0.0.1:8080",
      "/auth": "http://127.0.0.1:8080",
      "/public": "http://127.0.0.1:8080",
    },
  },
});
