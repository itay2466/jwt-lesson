// Node.js 18+: execute the IntelliJ .http examples against an already running server.
// No packages required. Run: node scripts/run-http-tests.mjs [base URL]
import fs from 'node:fs';
import vm from 'node:vm';

const values = new Map([['host', process.argv[2] ?? 'http://localhost:8080']]);
let passed = 0;
const client = {
  global: { set: (key, value) => values.set(key, value), get: key => values.get(key) },
  assert: (condition, message) => { if (!condition) throw new Error(message); },
  test: (name, fn) => { fn(); passed++; console.log(`PASS ${name}`); },
};
const substitute = text => text.replace(/\{\{([^}]+)\}\}/g, (_, name) => {
  if (name === '$timestamp') return `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  if (!values.has(name)) throw new Error(`Missing variable: ${name}`);
  return values.get(name);
});

for (const file of ['http/security-tests.http', 'http/extension-tests.http']) {
  const text = fs.readFileSync(new URL(`../${file}`, import.meta.url), 'utf8');
  for (const section of text.split(/^###.*$/m)) {
    const request = section.match(/^(GET|POST|PUT|DELETE|PATCH) (.+)$/m);
    if (!request) continue;
    const remainder = section.slice(request.index + request[0].length).trimStart();
    const scriptStart = remainder.indexOf('> {%');
    const wire = substitute(scriptStart < 0 ? remainder : remainder.slice(0, scriptStart));
    const [headerText, ...bodyParts] = wire.split(/\r?\n\s*\r?\n/);
    const headers = Object.fromEntries(headerText.split(/\r?\n/).filter(line => line.includes(':'))
      .map(line => { const colon = line.indexOf(':'); return [line.slice(0, colon), line.slice(colon + 1).trim()]; }));
    const body = bodyParts.join('\n\n').trim();
    const result = await fetch(substitute(request[2]), {
      method: request[1], headers, ...(body && !['GET', 'HEAD'].includes(request[1]) ? { body } : {}),
      signal: AbortSignal.timeout(15000),
    });
    const raw = await result.text();
    const response = { status: result.status, body: raw ? JSON.parse(raw) : null };
    if (scriptStart >= 0) {
      const script = remainder.slice(scriptStart + 4, remainder.indexOf('%}', scriptStart));
      vm.runInNewContext(script, { client, response, console }, { timeout: 5000, filename: file });
    }
  }
}
console.log(`All ${passed} HTTP tests passed.`);
