// Gives connectors without a logo the logo of their service, from Simple Icons or gilbarbara/logos, both CC0-1.0.
// A logo under another licence, or from another company of the same name, is left out, so the connector shows its initials.
//
//   git clone --depth 1 https://github.com/gilbarbara/logos.git <folder>
//   node add.mjs <folder>
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import * as simpleIcons from 'simple-icons';

const BACKEND = join(import.meta.dirname, '../../backend/src/main/resources');
const CONNECTORS = join(BACKEND, 'connectors');
const ICONS_FILE = join(BACKEND, 'static/js/flow/brand-icons.js');
const LOGOS = process.argv[2];
const LOGO_SITES = LOGOS ? new Map(JSON.parse(readFileSync(join(LOGOS, 'logos.json'), 'utf8')).flatMap((logo) => logo.files.map((file) => [file, logo.url]))) : new Map();

// The logo fills 92% of its square, the same as the other logos.
const MARGIN = 1 / 24;
// A wordmark this much wider than it is high cannot be read at the size of an icon.
const WIDEST = 1.6;

/** Logos that a person checked to be of the same company, where the connector's hosts cannot show it. Connector name to logo name. */
const CHECKED = {
  'baserow': 'baserow', 'bigcommerce': 'bigcommerce', 'campaign-monitor': 'campaignmonitor', 'dropbox': 'dropbox', 'gmail': 'gmail',
  'google-calendar': 'googlecalendar', 'google-docs': 'googledocs', 'google-drive': 'googledrive', 'google-forms': 'googleforms',
  'google-sheets': 'googlesheets', 'mailgun': 'mailgun', 'plausible': 'plausibleanalytics', 'reddit': 'reddit', 'todoist': 'todoist',
  'vtex': 'vtex', 'webflow': 'webflow', 'woocommerce': 'woocommerce', 'wordpress': 'wordpress', 'youtube': 'youtube',
  'jira-cloud': 'jira', 'telegram-bot': 'telegram', 'zoho-crm': 'zoho', 'close': 'close', 'microsoft-teams': 'microsoft-teams',
};

const comparable = (text) => text.toLowerCase().replace(/[^a-z0-9]/g, '');
const openIcons = Object.values(simpleIcons).filter((icon) => icon.slug && !icon.license);
const bySlug = new Map(openIcons.map((icon) => [icon.slug, icon]));
const byTitle = new Map(openIcons.map((icon) => [comparable(icon.title), icon]));

/** A name can belong to two companies, so a logo counts only when it comes from the connector's own domain, or a person checked it. */
const domainOf = (host) => host.toLowerCase().split('.').slice(-2).join('.');
const fromConnectorsDomain = (connector, urls) => {
  const domains = new Set(connector.hosts.map(domainOf).filter((domain) => !domain.includes('{{')));
  return urls.filter(Boolean).some((url) => domains.has(domainOf(new URL(url).hostname)));
};
const sameService = (icon, connector) => comparable(CHECKED[connector.name] ?? '') === icon.slug || fromConnectorsDomain(connector, [icon.source, icon.guidelines]);

const fromSimpleIcons = (connector) => {
  const icon = [bySlug.get(comparable(CHECKED[connector.name] ?? connector.name)), byTitle.get(comparable(connector.label))]
    .find((candidate) => candidate && sameService(candidate, connector));
  return icon && { name: icon.slug, viewBox: square(0, 0, 24, 24), body: `<g fill="#${icon.hex}"><path d="${icon.path}"/></g>` };
};

/** A file of gilbarbara/logos is named after the company, and logos.json gives the company's site. */
const fromLogos = (connector) => {
  if (!LOGOS) return null;
  const name = CHECKED[connector.name] ?? connector.name;
  const fileName = [`${name}-icon.svg`, `${name}.svg`].find((candidate) => existsSync(join(LOGOS, 'logos', candidate)));
  if (!fileName || !(CHECKED[connector.name] || fromConnectorsDomain(connector, [LOGO_SITES.get(fileName)]))) return null;
  const file = join(LOGOS, 'logos', fileName);
  const svg = readFileSync(file, 'utf8');
  const [x, y, width, height] = svg.match(/viewBox="([^"]+)"/)[1].trim().split(/[\s,]+/).map(Number);
  if (width / height > WIDEST) return null;
  const slug = comparable(name);
  // Ids are made unique, since every logo sits in the same page.
  const body = svg.replace(/^[\s\S]*?<svg[^>]*>|<\/svg>\s*$/g, '').replace(/\bid="([^"]+)"/g, `id="${slug}-$1"`).replace(/(url\(#|href="#)([^)"]+)/g, `$1${slug}-$2`);
  return { name: slug, viewBox: square(x, y, width, height), body };
};

/** The smallest square around a box, with the margin of the other logos, so that a logo is centred and not stretched. */
function square(x, y, width, height) {
  const side = Math.max(width, height) / (1 - 2 * MARGIN);
  const round = (value) => Number(value.toFixed(3));
  return [x - (side - width) / 2, y - (side - height) / 2, side, side].map(round).join(' ');
}

const source = readFileSync(ICONS_FILE, 'utf8');
const added = [];
for (const file of readdirSync(CONNECTORS).filter((name) => name.endsWith('.json'))) {
  const path = join(CONNECTORS, file);
  const connector = JSON.parse(readFileSync(path, 'utf8'));
  if (connector.icon) continue;
  const logo = fromSimpleIcons(connector) ?? fromLogos(connector);
  if (!logo) continue;
  connector.icon = logo.name;
  writeFileSync(path, `${JSON.stringify(connector, null, 2)}\n`);
  if (!source.includes(`"${logo.name}": {`) && !added.some((one) => one.name === logo.name)) added.push(logo);
}

const entries = added.map((logo) => `  "${logo.name}": {\n  "viewBox": "${logo.viewBox}",\n  "body": ${JSON.stringify(logo.body)}\n }`);
if (entries.length) writeFileSync(ICONS_FILE, source.replace(/\n};\s*$/, `,\n${entries.join(',\n')}\n};\n`));
console.log(`Logos added: ${added.map((logo) => logo.name).join(', ') || 'none'}.`);
