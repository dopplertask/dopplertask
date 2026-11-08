// Runs one Activepieces action with the answers in a fixture, and writes into the fixture what the action sent.
// DopplerTask's ConnectorFixturesTest then runs the same inputs through the connector and compares the requests.
// Nothing leaves the machine: every request is answered here, and a request that has no answer fails the run.
//
//   node record.mjs ../../backend/src/test/resources/connector-fixtures/<piece>/<action>.json
import { execFileSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';
import { BatchInterceptor } from '@mswjs/interceptors';
import { ClientRequestInterceptor } from '@mswjs/interceptors/ClientRequest';
import { FetchInterceptor } from '@mswjs/interceptors/fetch';
import { createMockActionContext } from '@activepieces/pieces-framework';

// Headers that differ between HTTP clients without changing what a service receives.
const NOISE = new Set(['user-agent', 'accept-encoding', 'accept', 'content-length', 'host', 'connection', 'accept-language', 'sec-fetch-mode']);

const fixturePath = process.argv[2];
const fixture = JSON.parse(readFileSync(fixturePath, 'utf8'));
const exchanges = [];
const answers = [...(fixture.answers || [])];
// Before the piece loads, since a piece keeps the fetch it found when it loaded.
const interceptor = new BatchInterceptor({ name: 'connector-harness', interceptors: [new ClientRequestInterceptor(), new FetchInterceptor()] });
interceptor.apply();
interceptor.on('request', async ({ request, controller }) => {
  const answer = answers.shift();
  const body = await request.clone().text();
  exchanges.push({ request: { method: request.method, url: request.url, headers: headersOf(request.headers), body: parsed(body) } });
  if (!answer) return controller.respondWith(Response.json({ error: 'The fixture has no answer for this request.' }, { status: 599 }));
  const text = typeof answer.body === 'string' ? answer.body : JSON.stringify(answer.body ?? {});
  controller.respondWith(new Response(text, { status: answer.status || 200, headers: { 'content-type': 'application/json', ...(answer.headers || {}) } }));
});

const piece = await loadPiece(fixture.package, fixture.version);
const action = piece.getAction(fixture.action);
if (!action) throw new Error(`${fixture.package} has no action ${fixture.action}. It has ${Object.keys(piece.actions()).join(', ')}.`);
const context = createMockActionContext({ propsValue: fixture.inputs || {} });
context.auth = fixture.auth;
let output;
try {
  output = await action.run(context);
} catch (error) {
  output = { error: String(error?.message || error) };
} finally {
  interceptor.dispose();
}
writeFileSync(fixturePath, `${JSON.stringify({ ...fixture, exchanges, output }, null, 2)}\n`);
console.log(`${fixture.package} ${fixture.action}: ${exchanges.length} request(s) recorded.`);

/** Each piece gets its own folder, so that several pieces can be recorded at the same time. */
async function loadPiece(name, version) {
  const folder = join(import.meta.dirname, '.pieces', name.replace(/[@/]/g, '_'), version);
  if (!existsSync(join(folder, 'node_modules', name))) {
    execFileSync('npm', ['install', '--prefix', folder, '--ignore-scripts', '--no-audit', '--no-fund', '--no-save', `${name}@${version}`], { stdio: 'inherit' });
  }
  const module = createRequire(join(folder, 'package.json'))(name);
  const found = Object.values(module).find((value) => value && typeof value.getAction === 'function');
  if (!found) throw new Error(`${name} exports no piece.`);
  return found;
}

function headersOf(headers) {
  return Object.fromEntries([...headers.entries()].filter(([key]) => !NOISE.has(key.toLowerCase())).map(([key, value]) => [key.toLowerCase(), value]));
}

function parsed(text) {
  if (!text) return null;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}
