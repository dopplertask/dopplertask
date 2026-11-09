// Runs one Activepieces action or trigger with the answers in a fixture, and writes into the fixture what the action sent.
// DopplerTask's ConnectorFixturesTest then runs the same inputs through the connector and compares the requests.
// Nothing leaves the machine: every request is answered here, and a request that has no answer fails the run.
//
//   node record.mjs ../../backend/src/test/resources/connector-fixtures/<piece>/<action>.json
import { readFileSync, writeFileSync } from 'node:fs';
import { BatchInterceptor } from '@mswjs/interceptors';
import { ClientRequestInterceptor } from '@mswjs/interceptors/ClientRequest';
import { FetchInterceptor } from '@mswjs/interceptors/fetch';
import { createMockActionContext } from '@activepieces/pieces-framework';
import { loadPiece } from './pieces.mjs';

// Headers that differ between HTTP clients without changing what a service receives,
// or that carry a new random value on each request, such as the id Microsoft Graph logs a request by.
const NOISE = new Set(['user-agent', 'accept-encoding', 'accept', 'content-length', 'host', 'connection', 'accept-language', 'sec-fetch-mode', 'client-request-id']);

// In UTC, as the check runs ours, so that a time is written the same on every machine.
process.env.TZ = 'UTC';

if (Number(process.versions.node.split('.')[0]) < 22) throw new Error(`The recorder needs Node 22 or later, and this is ${process.version}.`);

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
  const status = answer.status || 200;
  // A 204, 205 or 304 answer has no body at all.
  const text = [204, 205, 304].includes(status) ? null : typeof answer.body === 'string' ? answer.body : JSON.stringify(answer.body ?? {});
  controller.respondWith(new Response(text, { status, headers: { 'content-type': 'application/json', ...(answer.headers || {}) } }));
});

const piece = await loadPiece(fixture.package, fixture.version);
// A trigger runs one check from the place its fixture gives, such as {"lastPoll": 1700000000000} or {"lastItem": "c2"}.
const step = fixture.trigger ? piece.getTrigger(fixture.trigger) : piece.getAction(fixture.action);
if (!step) throw new Error(`${fixture.package} has no ${fixture.trigger ? `trigger ${fixture.trigger}` : `action ${fixture.action}`}.`);
// A file input gets what Activepieces passes: its name, extension and bytes, from the fixture's "files".
const files = Object.fromEntries(Object.entries(fixture.files || {}).map(([input, file]) => {
  const data = Buffer.from(file.base64, 'base64');
  return [input, { filename: file.name, extension: file.name.split('.').pop(), data, base64: data.toString('base64') }];
}));
const context = createMockActionContext({ propsValue: { ...(fixture.inputs || {}), ...files } });
context.auth = fixture.auth;
// A trigger of an app's events only says which events it listens for; the app's address routes them to it.
context.app = { createListeners: () => {} };
// A stored file is named by its own name, as DopplerTask names the files of a run.
context.files = { write: async ({ fileName }) => fileName };
const memory = new Map(Object.entries(fixture.store || {}));
context.store = { put: async (key, value) => (memory.set(key, value), value), get: async (key) => memory.get(key) ?? null, delete: async (key) => { memory.delete(key); } };
// A webhook trigger subscribes with a fixed address, then reads the fixture's event as a service would send it.
const WEBHOOK_URL = 'https://tasks.example.com/hooks/test-key';
// An action that waits gets a fixed address, as each run's own address. Older pieces ask with generateResumeUrl.
const RESUME_URL = 'https://tasks.example.com/hooks/resume/test-key';
const resumeUrlWith = ({ queryParams = {} } = {}) => Object.keys(queryParams).length ? `${RESUME_URL}?${new URLSearchParams(queryParams)}` : RESUME_URL;
context.run = { ...context.run, createWaitpoint: async () => ({ id: 'test-waitpoint', resumeUrl: RESUME_URL, buildResumeUrl: resumeUrlWith }), waitForWaitpoint: () => {} };
context.generateResumeUrl = resumeUrlWith;
let output;
try {
  if (fixture.webhook) {
    context.webhookUrl = WEBHOOK_URL;
    await step.onEnable(context);
    context.payload = { body: fixture.event, headers: fixture.eventHeaders || {}, queryParams: {} };
  }
  output = await step.run(context);
  // The answer to a waiting action, such as an approval link opened: the step runs again and reads it.
  if (fixture.answer) {
    context.executionType = 'RESUME';
    context.resumePayload = { body: fixture.answer.body ?? {}, headers: fixture.answer.headers || {}, queryParams: fixture.answer.query || {} };
    output = await step.run(context);
  }
} catch (error) {
  output = { error: String(error?.message || error) };
} finally {
  interceptor.dispose();
}
// What the original keeps in its store after the run, which our trigger's memory must match.
const storeAfter = fixture.store || memory.size ? { storeAfter: Object.fromEntries(memory) } : {};
writeFileSync(fixturePath, `${JSON.stringify({ ...fixture, exchanges, output, ...storeAfter }, null, 2)}\n`);
console.log(`${fixture.package} ${fixture.trigger || fixture.action}: ${exchanges.length} request(s) recorded.`);

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
