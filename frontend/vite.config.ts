import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Один origin в итоговой сборке: backend раздаёт содержимое dist/.
// В разработке Vite проксирует /api на локальный backend, поэтому cookie и Origin совпадают.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
      '/actuator': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: { outDir: 'dist', sourcemap: false },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
  },
});
