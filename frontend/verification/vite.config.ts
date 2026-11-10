import { defineConfig } from 'vitest/config';
import { fileURLToPath } from 'node:url';
export default defineConfig({
  esbuild: { jsx: 'automatic' },
  resolve: { alias: {
    'react-dom': fileURLToPath(new URL('./node_modules/react-dom', import.meta.url)),
    'react': fileURLToPath(new URL('./node_modules/react', import.meta.url))
  } },
  build: { lib: { entry: 'index.ts', formats: ['es'], fileName: 'users' }, rollupOptions: { external: ['react', 'react/jsx-runtime', 'react-dom'] } },
  test: { environment: 'jsdom', setupFiles: ['./setup.ts'], include: ['*.test.tsx'] }
});
