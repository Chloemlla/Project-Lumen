import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(__dirname, "../..");

export default defineConfig({
  plugins: [react()],
  server: {
    fs: {
      allow: [repositoryRoot],
    },
  },
  build: {
    outDir: "dist",
    emptyOutDir: true,
  },
});
