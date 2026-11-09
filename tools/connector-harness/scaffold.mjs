// Writes a first connector file and its fixtures from an Activepieces piece, so that a port starts from what a script can do.
// It maps the piece's sign-in and inputs, records each action with sample values, and turns an action that sends one
// request into a request template. What it cannot do alone is listed at the end, for a person or an agent to finish.
//
//   node scaffold.mjs @activepieces/piece-<name> <version> [out-folder]
//
// Only pieces from the MIT part of Activepieces (packages/pieces/community) are accepted.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { loadPiece } from './pieces.mjs';

const [packageName, version, out = join(import.meta.dirname, '..', '..', 'backend', 'src')] = process.argv.slice(2);
if (!packageName || !version) throw new Error('Usage: node scaffold.mjs @activepieces/piece-<name> <version> [out-folder]');
const name = packageName.replace('@activepieces/piece-', '');
await requireMitPiece(name);

const connectorFile = join(out, 'main', 'resources', 'connectors', `${name}.json`);
const fixtureFolder = join(out, 'test', 'resources', 'connector-fixtures', name);
if (existsSync(connectorFile)) throw new Error(`${connectorFile} exists already. The scaffolder only starts a new port.`);

const piece = await loadPiece(packageName, version);
const sign = signInOf(piece.auth);
const connector = {
  name,
  label: piece.displayName,
  description: piece.description || '',
  icon: name,
  hosts: [],
  upstream: { package: packageName, version },
  ...(sign.credential ? { credential: sign.credential } : {}),
  actions: {},
  triggers: {},
};
const toFinish = [];

mkdirSync(fixtureFolder, { recursive: true });
for (const [actionName, action] of Object.entries(piece.actions())) {
  const { inputs, samples, notes } = inputsOf(action.props);
  const fixturePath = join(fixtureFolder, `${actionName}.json`);
  writeFileSync(fixturePath, `${JSON.stringify({ package: packageName, version, action: actionName, auth: sign.sampleAuth, inputs: samples, credential: sign.sampleCredential, answers: [{ status: 200, body: {} }] }, null, 2)}\n`);
  try {
    execFileSync(process.execPath, [join(import.meta.dirname, 'record.mjs'), fixturePath], { stdio: 'ignore' });
  } catch {
    connector.actions[actionName] = { label: action.displayName, description: action.description || '', inputs, request: {} };
    toFinish.push(`action ${actionName}: the recorder could not run it with sample values`);
    continue;
  }
  const { exchanges } = JSON.parse(readFileSync(fixturePath, 'utf8'));
  const draft = requestOf(exchanges, samples, sign);
  connector.actions[actionName] = { label: action.displayName, description: action.description || '', inputs, request: draft.request };
  draft.hosts.forEach((host) => connector.hosts.includes(host) || connector.hosts.push(host));
  if (draft.done && !notes.length) continue;
  toFinish.push(`action ${actionName}: ${[...notes, ...draft.notes].join('; ')}`);
}
for (const [triggerName, trigger] of Object.entries(piece.triggers())) {
  connector.triggers[triggerName] = { label: trigger.displayName, description: trigger.description || '', inputs: inputsOf(trigger.props).inputs };
  toFinish.push(`trigger ${triggerName}: a ${String(trigger.type).toLowerCase()} trigger, which needs its check or webhook and its fixtures written`);
}
if (sign.note) toFinish.unshift(`credential: ${sign.note}`);

mkdirSync(dirname(connectorFile), { recursive: true });
writeFileSync(connectorFile, `${JSON.stringify(connector, null, 2)}\n`);
const actionCount = Object.keys(connector.actions).length;
const finished = actionCount - toFinish.filter((line) => line.startsWith('action')).length;
console.log(`${connectorFile}\n${finished} of ${actionCount} actions are a finished request template. To finish by hand:\n${toFinish.map((line) => `- ${line}`).join('\n')}`);

/** The MIT part of Activepieces is packages/pieces/community. A piece from anywhere else is refused. */
async function requireMitPiece(slug) {
  const url = `https://raw.githubusercontent.com/activepieces/activepieces/main/packages/pieces/community/${slug}/package.json`;
  const answer = await fetch(url);
  if (!answer.ok) throw new Error(`${slug} is not in packages/pieces/community, the MIT part of Activepieces (${url} answered ${answer.status}).`);
}

/** The connector's credential, and sample values for both sides of a fixture, by the kind of sign-in the piece has. */
function signInOf(auth) {
  const kind = Array.isArray(auth) ? auth[0] : auth;
  if (!kind) return { sampleAuth: undefined, sampleCredential: {}, values: {} };
  if (kind.type === 'SECRET_TEXT') {
    const secret = credentialSample(0);
    return {
      credential: { fields: { token: { label: kind.displayName, description: kind.description || '', secret: true, required: true } } },
      sampleAuth: { type: 'SECRET_TEXT', secret_text: secret },
      sampleCredential: { token: secret },
      values: { [secret]: 'token' },
    };
  }
  if (kind.type === 'BASIC_AUTH') {
    return {
      credential: { fields: { username: { label: kind.username?.displayName || 'Username', required: true }, password: { label: kind.password?.displayName || 'Password', secret: true, required: true } }, basic: { username: '{{credential.username}}', password: '{{credential.password}}' } },
      sampleAuth: { username: credentialSample(0), password: credentialSample(1) },
      sampleCredential: { username: credentialSample(0), password: credentialSample(1) },
      values: { [credentialSample(0)]: 'username', [credentialSample(1)]: 'password' },
      note: 'basic sign-in mapped; check that the piece sends it as Basic',
    };
  }
  if (kind.type === 'CUSTOM_AUTH') {
    const fields = {};
    const props = {};
    const values = {};
    for (const [index, [field, prop]] of Object.entries(kind.props || {}).entries()) {
      const address = /url|domain|host|site/i.test(field);
      const value = address ? `https://${credentialSample(index)}.example.com` : credentialSample(index);
      fields[field] = { label: prop.displayName, description: prop.description || '', ...(prop.type === 'SECRET_TEXT' ? { secret: true } : {}), ...(prop.required ? { required: true } : {}) };
      props[field] = value;
      values[address ? `${credentialSample(index)}.example.com` : value] = field;
    }
    return { credential: { fields }, sampleAuth: { type: 'CUSTOM_AUTH', props }, sampleCredential: props, values, note: 'custom sign-in fields mapped; check each field and where it goes' };
  }
  if (kind.type === 'OAUTH2') {
    return {
      credential: { oauth2: { authUrl: kind.authUrl, tokenUrl: kind.tokenUrl, scope: (kind.scope || []).join(' ') } },
      sampleAuth: { type: 'OAUTH2', access_token: credentialSample(0), data: {} },
      sampleCredential: { oauthAccessToken: credentialSample(0) },
      values: { [credentialSample(0)]: 'oauthAccessToken' },
    };
  }
  return { sampleAuth: kind, sampleCredential: {}, values: {}, note: `sign-in kind ${kind.type} is not mapped` };
}

/** Our inputs for the piece's props, sample values that are easy to find again in a request, and what a person must finish. */
function inputsOf(props = {}) {
  const inputs = {};
  const samples = {};
  const notes = [];
  for (const [index, [field, prop]] of Object.entries(props).entries()) {
    const input = { label: prop.displayName, ...(prop.description ? { description: prop.description } : {}), ...(prop.required ? { required: true } : {}) };
    switch (prop.type) {
      case 'SHORT_TEXT': case 'SECRET_TEXT': case 'DATE_TIME': case 'COLOR':
        samples[field] = inputSample(index);
        break;
      case 'LONG_TEXT': case 'RICH_TEXT':
        input.type = 'multiline';
        samples[field] = inputSample(index);
        break;
      case 'NUMBER':
        input.type = 'number';
        samples[field] = 987650000 + index;
        break;
      case 'CHECKBOX':
        input.type = 'boolean';
        break;
      case 'STATIC_DROPDOWN': case 'STATIC_MULTI_SELECT_DROPDOWN':
        input.type = prop.type === 'STATIC_DROPDOWN' ? 'select' : 'json';
        input.options = (prop.options?.options || []).map(({ label, value }) => ({ value, label }));
        break;
      case 'DROPDOWN': case 'MULTI_SELECT_DROPDOWN':
        input.type = prop.type === 'DROPDOWN' ? 'select' : 'json';
        samples[field] = inputSample(index);
        notes.push(`${field} reads its choices from the service, which needs a list`);
        break;
      case 'JSON': case 'OBJECT': case 'ARRAY':
        input.type = 'json';
        break;
      case 'FILE':
        input.type = 'file';
        notes.push(`${field} is a file`);
        break;
      case 'MARKDOWN':
        continue;
      case 'DYNAMIC':
        notes.push(`${field} has fields that depend on another choice, which become one variant per choice`);
        continue;
      default:
        notes.push(`${field} has the input kind ${prop.type}, which is not mapped`);
    }
    if (prop.defaultValue !== undefined && typeof prop.defaultValue !== 'object') input.defaultValue = String(prop.defaultValue);
    inputs[field] = input;
  }
  return { inputs, samples, notes };
}

/** A request template from what the original sent: each sample value becomes its input, and the sign-in its credential. */
function requestOf(exchanges, samples, sign) {
  const notes = [];
  const used = new Set();
  // Every sample has its own fixed form, so that one never matches inside another, and all are replaced in one pass.
  const replacements = new Map([
    ...Object.entries(samples).map(([field, sample]) => [String(sample), field]),
    ...Object.entries(sign.values).map(([sample, field]) => [sample, `credential.${field}`]),
  ]);
  const pattern = new RegExp([...replacements.keys()].map((sample) => `(?<![\\w.])${sample.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![\\w])`).join('|') || '(?!)', 'g');
  const template = (value) => {
    if (typeof value === 'string') {
      return value.replace(pattern, (sample) => {
        const name = replacements.get(sample);
        if (!name.startsWith('credential.')) used.add(name);
        return `{{${name}}}`;
      });
    }
    if (typeof value === 'number') {
      const field = Object.keys(samples).find((key) => samples[key] === value);
      if (field) { used.add(field); return `{{${field}}}`; }
      return value;
    }
    if (Array.isArray(value)) return value.map(template);
    if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key, inner]) => [key, template(inner)]));
    return value;
  };
  if (exchanges.length !== 1) {
    notes.push(exchanges.length ? `sends ${exchanges.length} requests, which needs a script` : 'sent nothing with sample values');
    const first = exchanges[0]?.request;
    return { request: first ? { method: first.method, url: template(first.url) } : {}, hosts: first ? [template(new URL(first.url).host)] : [], done: false, notes };
  }
  const sent = exchanges[0].request;
  const url = new URL(sent.url);
  const query = {};
  for (const [key, value] of url.searchParams) {
    const filled = template(value);
    if (filled.includes('{{credential.')) sign.credential.query = { ...sign.credential.query, [key]: filled };
    else query[key] = filled;
  }
  const headers = {};
  for (const [key, value] of Object.entries(sent.headers || {})) {
    if (key === 'content-type' && String(value).startsWith('application/json')) continue;
    const filled = template(String(value));
    // The engine signs a request with the account's token by itself.
    if (filled.includes('{{credential.oauthAccessToken}}')) continue;
    if (filled.includes('{{credential.')) { sign.credential.headers = { ...sign.credential.headers, [key]: filled }; continue; }
    headers[key] = filled;
  }
  if (Object.keys(sign.values).length && !sign.credential?.oauth2 && !sign.credential?.headers && !sign.credential?.query) notes.push('the sign-in was not found in a header or the query; check where the credential goes');
  const request = {
    method: sent.method,
    url: template(`${url.origin}${decodeURIComponent(url.pathname)}`),
    ...(Object.keys(query).length ? { query } : {}),
    ...(Object.keys(headers).length ? { headers } : {}),
    ...(sent.body !== null && sent.body !== undefined ? { body: template(sent.body) } : {}),
  };
  const unused = Object.keys(samples).filter((field) => !used.has(field));
  if (unused.length) notes.push(`inputs not found in the request: ${unused.join(', ')}, which may need a script`);
  const host = template(url.host);
  if (Object.entries(sign.credential?.fields || {}).some(([field, settings]) => settings.secret && host.includes(`credential.${field}`))) notes.push('a secret credential value stands in the host, which the format refuses');
  return { request, hosts: [host], done: !unused.length && !notes.length, notes };
}

/** A sample input value: fixed length, so that no sample is part of another. */
function inputSample(index) {
  return `sample${String(index).padStart(3, '0')}z`;
}

/** A sample credential value, which never looks like an input sample. */
function credentialSample(index) {
  return `credential${String(index).padStart(3, '0')}z`;
}
