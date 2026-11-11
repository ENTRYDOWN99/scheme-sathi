import fs from 'node:fs';
import path from 'node:path';

const migrationsSrc = path.resolve('src/db/migrations');
const migrationsDest = path.resolve('dist/db/migrations');
fs.mkdirSync(migrationsDest, { recursive: true });
for (const file of fs.readdirSync(migrationsSrc)) {
  if (file.endsWith('.sql')) {
    fs.copyFileSync(path.join(migrationsSrc, file), path.join(migrationsDest, file));
  }
}

const openApiSrc = path.resolve('openapi.yaml');
const openApiDest = path.resolve('dist/openapi.yaml');
if (fs.existsSync(openApiSrc)) {
  fs.copyFileSync(openApiSrc, openApiDest);
}
console.log('Copied SQL migrations and OpenAPI spec to dist/');
