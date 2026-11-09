// Starts a new connector from a piece in the MIT part of Activepieces, so that a port begins with what a script can do
// and a person or an agent only finishes what needs judgement.
//
//   node scaffold.mjs @activepieces/piece-<name> <version> [out-folder]
//
// It writes the connector file and one fixture per action, and prints what is left to finish by hand.
import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { loadPiece } from './pieces.mjs';

// Step 1: This will read the piece name and version from the command line, so we can name the connector after the piece,
// so that the connector file and its fixtures land in the folders the engine and ConnectorCheck read.
const [packageName, version, out = join(import.meta.dirname, '..', '..', 'backend', 'src')] = process.argv.slice(2);
if (!packageName || !version) throw new Error('Usage: node scaffold.mjs @activepieces/piece-<name> <version> [out-folder]');
const name = packageName.replace('@activepieces/piece-', '');
const connectorFile = join(out, 'main', 'resources', 'connectors', `${name}.json`);
const fixtureFolder = join(out, 'test', 'resources', 'connector-fixtures', name);

// Step 2: This will refuse a piece outside the MIT folder of Activepieces, so we can only start from code we may use,
// so that every connector we ship is free to use under its licence.
await requireMitPiece(name);

// Step 3: This will refuse to write over a connector that exists, so we can run the script without fear,
// so that a connector someone finished by hand is never lost.
if (existsSync(connectorFile)) throw new Error(`${connectorFile} exists already. The scaffolder only starts a new port.`);

// Step 4: This will load the piece and turn its sign-in into our credential section, so we can let the engine sign
// every request, so that a key lives in one place and not in each action.
const piece = await loadPiece(packageName, version);
const signIn = signInOf(piece.auth);
const connector = {
  name,
  label: piece.displayName,
  description: piece.description || '',
  icon: name,
  hosts: [],
  upstream: { package: packageName, version },
  ...(signIn.credential ? { credential: signIn.credential } : {}),
  actions: {},
  triggers: {},
};
const toFinish = signIn.note ? [`credential: ${signIn.note}`] : [];

// Step 5: This will run every action of the piece once with sample values and record what it sends, so we can turn each
// sample back into the input it came from, so that an action that sends one request becomes a request template.
mkdirSync(fixtureFolder, { recursive: true });
for (const [actionName, action] of Object.entries(piece.actions())) {
  const { inputs, samples, notes } = inputsOf(action.props);
  const exchanges = recordWithSamples(actionName, samples);
  const draft = exchanges ? requestOf(exchanges, samples, signIn) : { request: {}, hosts: [], done: false, notes: ['the recorder could not run it with sample values'] };
  connector.actions[actionName] = { label: action.displayName, description: action.description || '', inputs, request: draft.request };
  draft.hosts.forEach((host) => connector.hosts.includes(host) || connector.hosts.push(host));
  if (!draft.done || notes.length) toFinish.push(`action ${actionName}: ${[...notes, ...draft.notes].join('; ')}`);
}

// Step 6: This will copy each trigger's name and inputs, so we can see every trigger in the file,
// so that a person knows which checks or webhooks are left to write.
for (const [triggerName, trigger] of Object.entries(piece.triggers())) {
  connector.triggers[triggerName] = { label: trigger.displayName, description: trigger.description || '', inputs: inputsOf(trigger.props).inputs };
  toFinish.push(`trigger ${triggerName}: a ${String(trigger.type).toLowerCase()} trigger, which needs its check or webhook and its fixtures written`);
}

// Step 7: This will write the connector file and print what is left, so we can hand over only the parts that need
// judgement, so that a person or an agent spends time where a script cannot help.
mkdirSync(dirname(connectorFile), { recursive: true });
writeFileSync(connectorFile, `${JSON.stringify(connector, null, 2)}\n`);
const actionCount = Object.keys(connector.actions).length;
const finished = actionCount - toFinish.filter((line) => line.startsWith('action')).length;
console.log(`${connectorFile}\n${finished} of ${actionCount} actions are a finished request template. To finish by hand:\n${toFinish.map((line) => `- ${line}`).join('\n')}`);

/** Refuses any piece that is not in packages/pieces/community, the MIT part of Activepieces. */
async function requireMitPiece(slug) {
  const url = `https://raw.githubusercontent.com/activepieces/activepieces/main/packages/pieces/community/${slug}/package.json`;
  const answer = await fetch(url);
  if (!answer.ok) throw new Error(`${slug} is not in packages/pieces/community, the MIT part of Activepieces (${url} answered ${answer.status}).`);
}

/**
 * Writes a fixture with the sample values, runs the original with record.mjs, and gives back what it sent,
 * or null when the original could not run with them. The fixture's answers are empty, so a person adds real ones later.
 */
function recordWithSamples(actionName, samples) {
  const fixturePath = join(fixtureFolder, `${actionName}.json`);
  const fixture = { package: packageName, version, action: actionName, auth: signIn.sampleAuth, inputs: samples, credential: signIn.sampleCredential, answers: [{ status: 200, body: {} }] };
  writeFileSync(fixturePath, `${JSON.stringify(fixture, null, 2)}\n`);
  try {
    execFileSync(process.execPath, [join(import.meta.dirname, 'record.mjs'), fixturePath], { stdio: 'ignore' });
  } catch {
    return null;
  }
  return JSON.parse(readFileSync(fixturePath, 'utf8')).exchanges;
}

/** Our credential section for the piece's kind of sign-in, with sample values for both sides of a fixture. */
function signInOf(auth) {
  const kind = Array.isArray(auth) ? auth[0] : auth;
  if (!kind) return { sampleAuth: undefined, sampleCredential: {}, values: {}, signedBy: [] };
  if (kind.type === 'SECRET_TEXT') {
    const secret = credentialSample(0);
    return {
      credential: { fields: { token: { label: kind.displayName, description: kind.description || '', secret: true, required: true } } },
      sampleAuth: { type: 'SECRET_TEXT', secret_text: secret },
      sampleCredential: { token: secret },
      values: { [secret]: 'token' },
      signedBy: [],
    };
  }
  if (kind.type === 'BASIC_AUTH') {
    const [username, password] = [credentialSample(0), credentialSample(1)];
    return {
      credential: { fields: { username: { label: kind.username?.displayName || 'Username', required: true }, password: { label: kind.password?.displayName || 'Password', secret: true, required: true } }, basic: { username: '{{credential.username}}', password: '{{credential.password}}' } },
      sampleAuth: { username, password },
      sampleCredential: { username, password },
      values: { [username]: 'username', [password]: 'password' },
      // The engine signs with these itself, so the recorded header must not stay in the template.
      signedBy: [`Basic ${Buffer.from(`${username}:${password}`).toString('base64')}`],
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
    return { credential: { fields }, sampleAuth: { type: 'CUSTOM_AUTH', props }, sampleCredential: props, values, signedBy: [], note: 'custom sign-in fields mapped; check each field and where it goes' };
  }
  if (kind.type === 'OAUTH2') {
    const token = credentialSample(0);
    return {
      credential: { oauth2: { authUrl: kind.authUrl, tokenUrl: kind.tokenUrl, scope: (kind.scope || []).join(' ') } },
      sampleAuth: { type: 'OAUTH2', access_token: token, data: {} },
      sampleCredential: { oauthAccessToken: token },
      values: { [token]: 'oauthAccessToken' },
      signedBy: [],
    };
  }
  return { sampleAuth: kind, sampleCredential: {}, values: {}, signedBy: [], note: `sign-in kind ${kind.type} is not mapped` };
}

/** Our inputs for the piece's props, a sample value for each input we can find again in a request, and what is left. */
function inputsOf(props = {}) {
  const inputs = {};
  const samples = {};
  const notes = [];
  const unchecked = [];
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
        unchecked.push(field);
        break;
      case 'STATIC_DROPDOWN': case 'STATIC_MULTI_SELECT_DROPDOWN':
        input.type = prop.type === 'STATIC_DROPDOWN' ? 'select' : 'json';
        input.options = (prop.options?.options || []).map(({ label, value }) => ({ value, label }));
        unchecked.push(field);
        break;
      case 'DROPDOWN': case 'MULTI_SELECT_DROPDOWN':
        input.type = prop.type === 'DROPDOWN' ? 'select' : 'json';
        samples[field] = inputSample(index);
        notes.push(`${field} reads its choices from the service, which needs a list`);
        break;
      case 'JSON': case 'OBJECT': case 'ARRAY':
        input.type = 'json';
        unchecked.push(field);
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
  // A checkbox, a fixed choice or a JSON value has no sample to find again, so the request cannot show where it goes.
  if (unchecked.length) notes.push(`check where ${unchecked.join(', ')} go in the request, since they had no sample value`);
  return { inputs, samples, notes };
}

/** A request template from what the original sent: each sample value becomes its input, and the sign-in its credential. */
function requestOf(exchanges, samples, signIn) {
  const notes = [];
  const used = new Set();
  // Every sample has its own fixed form, so that one never matches inside another, and all are replaced in one pass.
  const replacements = new Map([
    ...Object.entries(samples).map(([field, sample]) => [String(sample), field]),
    ...Object.entries(signIn.values).map(([sample, field]) => [sample, `credential.${field}`]),
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

  // An action that sends no request or several requests needs a script, so only its first address is kept.
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
    // A credential value in the query goes to the credential section, where the engine adds it.
    if (filled.includes('{{credential.')) signIn.credential.query = { ...signIn.credential.query, [key]: filled };
    else query[key] = filled;
  }
  const headers = {};
  for (const [key, value] of Object.entries(sent.headers || {})) {
    if (key === 'content-type' && String(value).startsWith('application/json')) continue;
    if (signIn.signedBy.includes(String(value))) continue;
    const filled = template(String(value));
    // The engine signs with the account's token itself, in whatever header the piece put it.
    if (filled.includes('{{credential.oauthAccessToken}}')) continue;
    // A credential value in a header goes to the credential section, where the engine adds it.
    if (filled.includes('{{credential.')) { signIn.credential.headers = { ...signIn.credential.headers, [key]: filled }; continue; }
    headers[key] = filled;
  }
  if (Object.keys(signIn.values).length && !signIn.credential?.oauth2 && !signIn.credential?.basic && !signIn.credential?.headers && !signIn.credential?.query) notes.push('the sign-in was not found in a header or the query; check where the credential goes');
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
  if (Object.entries(signIn.credential?.fields || {}).some(([field, settings]) => settings.secret && host.includes(`credential.${field}`))) notes.push('a secret credential value stands in the host, which the format refuses');
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
