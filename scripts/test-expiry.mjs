// Run against the short-token profile. This really waits until the issued token expires.
import assert from 'node:assert/strict';
const host = process.argv[2] ?? 'http://localhost:8080';
const login = await fetch(`${host}/api/auth/login`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'user', password: 'User123!' }),
});
assert.equal(login.status, 200);
const session = await login.json();
assert.equal(session.expiresInMs, 60000);
const me = () => fetch(`${host}/api/user/me`, { headers: { Authorization: `Bearer ${session.token}` } });
assert.equal((await me()).status, 200);
console.log('Token works before expiry; waiting 61 seconds...');
await new Promise(resolve => setTimeout(resolve, 61000));
assert.equal((await me()).status, 401);
const refresh = await fetch(`${host}/api/auth/refresh`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ refreshToken: session.refreshToken }),
});
assert.equal(refresh.status, 200);
const renewed = await refresh.json();
assert.equal((await fetch(`${host}/api/user/me`, {
  headers: { Authorization: `Bearer ${renewed.token}` },
})).status, 200);
console.log('PASS: token expired after 60 seconds; refresh restored access.');
