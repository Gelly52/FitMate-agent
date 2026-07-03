import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

function resolveManualChunk(id: string): string | undefined {
  const normalizedId = id.split(path.win32.sep).join("/");
  if (!normalizedId.includes("node_modules")) {
    return undefined;
  }

  if (normalizedId.includes("@vue") || normalizedId.includes("/vue/")) {
    return "vendor-vue";
  }
  if (normalizedId.includes("axios")) {
    return "vendor-axios";
  }
  if (normalizedId.includes("marked")) {
    return "vendor-marked";
  }

  return "vendor";
}

export default defineConfig({
  plugins: [vue()],
  base: "./",
  server: {
    host: "127.0.0.1",
    port: 5500,
    strictPort: true,
  },
  build: {
    outDir: "dist",
    rollupOptions: {
      input: {
        fitMateVite: fileURLToPath(new URL("./fitmate-vite.html", import.meta.url)),
      },
      output: {
        manualChunks(id) {
          return resolveManualChunk(id);
        },
      },
    },
  },
});
