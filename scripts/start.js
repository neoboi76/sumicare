/**
 * Runtime startup script for Railway.
 *
 * This script runs at CONTAINER STARTUP (not build time) and:
 * 1. Reads API_BASE_URL from the runtime environment
 * 2. Replaces the placeholder in all built JS files
 * 3. Starts the static file server
 *
 * This approach is more robust than build-time injection because
 * Railway env vars are always available at runtime.
 */

const { execSync } = require('child_process');
const fs = require('fs');
const path = require('path');

const DIST_DIR = path.join(__dirname, '..', 'dist', 'apps', 'sumicare-web', 'browser');
const API_BASE_URL = process.env.API_BASE_URL || '';
const PORT = process.env.PORT || 8080;

// Replace placeholder in all JS files in the dist folder
function replaceInFiles(dir) {
  if (!fs.existsSync(dir)) {
    console.error(`[start] ERROR: dist directory not found: ${dir}`);
    process.exit(1);
  }

  const files = fs.readdirSync(dir);
  let replacedCount = 0;

  files.forEach((file) => {
    const filePath = path.join(dir, file);
    const stat = fs.statSync(filePath);

    if (stat.isDirectory()) {
      replacedCount += replaceInFiles(filePath);
      return;
    }

    if (!file.endsWith('.js')) return;

    let content = fs.readFileSync(filePath, 'utf-8');
    if (content.includes('${API_BASE_URL}')) {
      content = content.replace(/\$\{API_BASE_URL\}/g, API_BASE_URL);
      fs.writeFileSync(filePath, content, 'utf-8');
      replacedCount++;
      console.log(`[start] Replaced API_BASE_URL in ${file}`);
    }
  });

  return replacedCount;
}

console.log(`[start] API_BASE_URL = ${API_BASE_URL || '(not set)'}`);
const count = replaceInFiles(DIST_DIR);
console.log(`[start] Replaced placeholder in ${count} file(s)`);

// Start the static file server
console.log(`[start] Starting server on port ${PORT}...`);
execSync(`npx serve ${DIST_DIR} -l ${PORT} -s`, { stdio: 'inherit' });
