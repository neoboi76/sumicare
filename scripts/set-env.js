/**
 * Pre-build script that injects environment variables into Angular environment files.
 *
 * Railway (and other CI/CD platforms) set environment variables at build time.
 * Since Angular compiles everything statically, we need to write the values
 * into the environment files BEFORE the Angular build runs.
 *
 * Usage:  node scripts/set-env.js
 *
 * Required env vars:
 *   API_BASE_URL  – The backend API base URL (e.g. https://backend-production-xxxx.up.railway.app)
 */

const fs = require('fs');
const path = require('path');

const API_BASE_URL = process.env.API_BASE_URL || '';

const envFiles = [
  path.join(__dirname, '..', 'apps', 'sumicare-web', 'src', 'environments', 'environment.ts'),
  path.join(__dirname, '..', 'apps', 'sumicare-web', 'src', 'environments', 'environment.production.ts'),
];

envFiles.forEach((filePath) => {
  if (!fs.existsSync(filePath)) {
    console.warn(`[set-env] Skipping missing file: ${filePath}`);
    return;
  }

  let content = fs.readFileSync(filePath, 'utf-8');
  content = content.replace(/\$\{API_BASE_URL\}/g, API_BASE_URL);
  fs.writeFileSync(filePath, content, 'utf-8');

  console.log(`[set-env] Injected API_BASE_URL into ${path.basename(filePath)}`);
});

console.log('[set-env] Done.');
