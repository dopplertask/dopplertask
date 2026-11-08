// Gives connectors without a logo the logo of their service from Simple Icons, when Simple Icons has it under CC0-1.0.
// A logo under another licence, or from another company of the same name, is left out, so the connector keeps the general icon.
//
//   node add.mjs
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import * as simpleIcons from 'simple-icons';

const BACKEND = join(import.meta.dirname, '../../backend/src/main/resources');
const CONNECTORS = join(BACKEND, 'connectors');
const ICONS_FILE = join(BACKEND, 'static/js/flow/brand-icons.js');

// The same size as the other logos: the 24 by 24 square of Simple Icons fills 92% of the icon.
const VIEW_BOX = '-1.091 -1.091 26.182 26.182';

const comparable = (text) => text.toLowerCase().replace(/[^a-z0-9]/g, '');
const openIcons = Object.values(simpleIcons).filter((icon) => !icon.license);
const bySlug = new Map(openIcons.map((icon) => [icon.slug, icon]));
const byTitle = new Map(openIcons.map((icon) => [comparable(icon.title), icon]));

/** A name can belong to two companies, so a logo counts only when it comes from the connector's own domain. */
const domainOf = (host) => host.toLowerCase().split('.').slice(-2).join('.');
const sameService = (icon, connector) => {
  const domains = new Set(connector.hosts.filter((host) => !host.includes('{{')).map(domainOf));
  return [icon.source, icon.guidelines].filter(Boolean).some((url) => domains.has(domainOf(new URL(url).hostname)));
};

const source = readFileSync(ICONS_FILE, 'utf8');
const added = [];
for (const file of readdirSync(CONNECTORS).filter((name) => name.endsWith('.json'))) {
  const path = join(CONNECTORS, file);
  const connector = JSON.parse(readFileSync(path, 'utf8'));
  if (connector.icon) continue;
  const icon = [bySlug.get(comparable(connector.name)), byTitle.get(comparable(connector.label))].find((candidate) => candidate && sameService(candidate, connector));
  if (!icon) continue;
  connector.icon = icon.slug;
  writeFileSync(path, `${JSON.stringify(connector, null, 2)}\n`);
  if (!source.includes(`"${icon.slug}": {`)) added.push(icon);
}

const entries = added.map((icon) => `  "${icon.slug}": {\n  "viewBox": "${VIEW_BOX}",\n  "body": ${JSON.stringify(`<g fill="#${icon.hex}"><path d="${icon.path}"/></g>`)}\n }`);
if (entries.length) writeFileSync(ICONS_FILE, source.replace(/\n};\s*$/, `,\n${entries.join(',\n')}\n};\n`));
console.log(`Logos added: ${added.map((icon) => icon.slug).join(', ') || 'none'}.`);
