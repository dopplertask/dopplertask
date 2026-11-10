/* How the editor writes a value of an earlier step, the guided changes to it, and a condition in plain words.
   It reads back only the forms it writes, so text a person wrote by hand stays as written. */

const HEAD = String.raw`\$(?:steps\["[^"]+"\]\.(?:outputVariables|output|data)|loop\.(?:item|number|count|index)|parameters\.get\("[^"]+"\)|lastLog\.output(?:Variables\.\w+)?)(?!\w)`;
const PATH = String.raw`(?:\.[A-Za-z_]\w*(?![\w(])|\[\d+\]|\["[^"]+"\])*`;
const REFERENCE = HEAD + PATH;
/* Inside ${…|'…'} a reference stands without its dollar sign. */
const BARE_REFERENCE = REFERENCE.slice(2);

const PLAIN_NAME = /^[A-Za-z_]\w*$/;

/** One more level into a value: .name, [0] or ["odd name"]. A name with a double quote in it cannot be written. */
export function pathStep(key) {
  if (typeof key === 'number') return `[${key}]`;
  if (PLAIN_NAME.test(key)) return `.${key}`;
  return key.includes('"') ? null : `["${key}"]`;
}

export const stepReference = (stepName, part) => `$steps["${stepName}"].${part}`;

const pathKeys = (path) => [...path.matchAll(/\.([A-Za-z_]\w*)|\[(\d+)\]|\["([^"]+)"\]/g)].map(([, name, position, oddName]) => (position === undefined ? name ?? oddName : Number(position)));

/** The value at a written path inside a sample, or undefined when the sample has no such place. */
export function valueAt(sample, path) {
  return pathKeys(path).reduce((value, key) => (value === null || value === undefined ? undefined : value[key]), sample);
}

/** Where a reference starts and the path it then follows: { source, name, path }. */
export function readReference(reference) {
  const step = /^\$steps\["([^"]+)"\]\.(outputVariables|output|data)(.*)$/.exec(reference);
  if (step) return { source: step[2], name: step[1], path: step[3] };
  const loop = /^\$loop\.(item|number|count|index)(.*)$/.exec(reference);
  if (loop) return { source: `loop.${loop[1]}`, path: loop[2] };
  const parameter = /^\$parameters\.get\("([^"]+)"\)(.*)$/.exec(reference);
  if (parameter) return { source: 'parameter', name: parameter[1], path: parameter[2] };
  const previous = /^\$lastLog\.output(?:Variables\.(\w+))?(.*)$/.exec(reference);
  return { source: previous[1] ? 'previousVariable' : 'previousOutput', name: previous[1], path: previous[2] };
}

const LOOP_WORDS = { 'loop.item': 'Current item', 'loop.number': 'Item number', 'loop.count': 'Number of items', 'loop.index': 'Item position' };

/** A reference as a person says it: Get order › customer › email. */
export function referenceWords(reference) {
  const { source, name, path } = readReference(reference);
  const start = { data: name, output: `${name} › whole answer`, outputVariables: name, parameter: name, previousOutput: 'previous output', previousVariable: `previous ${name}` }[source] ?? LOOP_WORDS[source];
  return [start, ...pathKeys(path).map((key) => (typeof key === 'number' ? `item ${key + 1}` : key))].join(' › ');
}

/* ---------- What a sample is ---------- */

const DATE_SAMPLE = /^\d{4}-\d{2}-\d{2}(?:([T ])\d{2}:\d{2}(:\d{2})?(\.\d{3})?(Z|[+-]\d{2}:?\d{2})?)?$/;

/** The pattern that reads a date written as the sample is, or null when the sample is no date the editor knows. */
export function dateInputPattern(sample) {
  const parts = typeof sample === 'string' ? DATE_SAMPLE.exec(sample.trim()) : null;
  if (!parts) return null;
  const [, separator, seconds, fraction, zone] = parts;
  if (!separator) return 'yyyy-MM-dd';
  const zonePattern = !zone ? '' : zone === 'Z' ? 'X' : zone.includes(':') ? 'XXX' : 'XX';
  return `yyyy-MM-dd${separator === 'T' ? "'T'" : ' '}HH:mm${seconds ? ':ss' : ''}${fraction ? '.SSS' : ''}${zonePattern}`;
}

/** text, date, number, boolean, empty, list or object. The guided changes on offer follow from it. */
export function kindOf(sample) {
  if (sample === null || sample === undefined || sample === '') return 'empty';
  if (Array.isArray(sample)) return 'list';
  if (typeof sample === 'object') return 'object';
  if (typeof sample === 'string') return dateInputPattern(sample) ? 'date' : 'text';
  return typeof sample;
}

/* ---------- Guided changes ---------- */

const escapePattern = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
const oneOf = (choices) => choices.map(([value]) => escapePattern(value)).join('|');
const singleQuoted = (text) => `'${String(text).replace(/'/g, "''")}'`;
const SINGLE_QUOTED = String.raw`'((?:[^']|'')*)'`;
const unquoted = (text) => text.replace(/''/g, "'");

const LETTER_CHOICES = [['upperCase', 'UPPER CASE'], ['lowerCase', 'lower case'], ['capitalize', 'First letter as a capital'], ['trim', 'No spaces around it']];
/* A number pattern stands in single quotes, since ## starts a comment inside double quotes. */
const NUMBER_CHOICES = [['0', 'Whole number'], ['0.00', '2 decimals'], ['#,##0', 'Thousands apart'], ['#,##0.00', 'Thousands apart, 2 decimals']];
const DATE_CHOICES = [['dd.MM.yyyy', '09.03.2026'], ['yyyy-MM-dd', '2026-03-09'], ['d MMMM yyyy', '9 March 2026'], ['dd.MM.yyyy HH:mm', '09.03.2026 14:05'], ['HH:mm', '14:05'], ['MM/dd/yyyy', '03/09/2026']];

/* One entry per guided change: who it is for, how it is written, and the pattern that reads it back. In a pattern the
   first group is the choice and the last is the reference, apart from the default, where the reference comes first. */
export const OPERATIONS = [
  {
    id: 'letters', label: 'Change the letters', kinds: ['text'], choices: LETTER_CHOICES,
    write: (reference, choice) => `$stringUtils.${choice}(${reference})`,
    pattern: String.raw`\$stringUtils\.(${oneOf(LETTER_CHOICES)})\((${REFERENCE})\)`,
    words: (choice) => LETTER_CHOICES.find(([value]) => value === choice)[1],
  },
  {
    id: 'number', label: 'Show the number as', kinds: ['number'], choices: NUMBER_CHOICES,
    write: (reference, choice) => `$numberTool.format('${choice}', ${reference})`,
    pattern: String.raw`\$numberTool\.format\('(${oneOf(NUMBER_CHOICES)})', (${REFERENCE})\)`,
    words: (choice) => NUMBER_CHOICES.find(([value]) => value === choice)[1].toLowerCase(),
  },
  {
    id: 'date', label: 'Show the date as', kinds: ['date'], choices: DATE_CHOICES, ownChoice: 'Your own pattern, such as EEEE d MMMM',
    write: (reference, choice, readAs) => `$dateTool.format("${choice}", $dateTool.toDate("${readAs}", ${reference}))`,
    pattern: String.raw`\$dateTool\.format\("([^"$#]*)", \$dateTool\.toDate\("([^"$#]*)", (${REFERENCE})\)\)`,
    words: (choice) => `as ${choice}`,
  },
  {
    id: 'default', label: 'When it is empty, use', kinds: ['text', 'date', 'number', 'boolean', 'empty'], ownChoice: 'What to use in its place',
    write: (reference, choice) => `\${${reference.slice(1)}|${singleQuoted(choice)}}`,
    pattern: String.raw`\$\{(${BARE_REFERENCE})\|${SINGLE_QUOTED}\}`,
    words: (choice) => `or “${choice}” when empty`,
  },
];

const operationById = (id) => OPERATIONS.find((operation) => operation.id === id);

function readGuided(text) {
  for (const operation of OPERATIONS) {
    const groups = new RegExp(`^${operation.pattern}$`).exec(text);
    if (!groups) continue;
    if (operation.id === 'default') return { operation: operation.id, reference: `$${groups[1]}`, choice: unquoted(groups[2]) };
    if (operation.id === 'date') return { operation: operation.id, choice: groups[1], readAs: groups[2], reference: groups[3] };
    return { operation: operation.id, choice: groups[1], reference: groups[2] };
  }
  return null;
}

const TOKEN = new RegExp(`${OPERATIONS.map((operation) => operation.pattern).join('|')}|${REFERENCE}`, 'g');

/** Every value in a text that the editor can show as a chip: { start, end, text, reference, operation, choice, readAs }. */
export function tokensIn(text) {
  return [...String(text ?? '').matchAll(TOKEN)].map((match) => ({
    start: match.index, end: match.index + match[0].length, text: match[0],
    ...(readGuided(match[0]) || { reference: match[0] }),
  }));
}

/** Whether the text holds logic beyond the values the editor shows as chips, which then stays as the person wrote it. */
export function hasHandWrittenLogic(text) {
  const tokens = tokensIn(text);
  const rest = tokens.reduceRight((remaining, token) => `${remaining.slice(0, token.start)}\u0000${remaining.slice(token.end)}`, String(text ?? ''));
  return /[$#]|\u0000\.\w+\(/.test(rest);
}

export function tokenWords(token) {
  const words = referenceWords(token.reference);
  return token.operation ? `${words}, ${operationById(token.operation).words(token.choice)}` : words;
}

/** A text with each value in words, for a tile or a summary. `mark` wraps a value that stands inside other text. */
export function inWords(text, mark = (words) => words) {
  return String(text ?? '').replace(TOKEN, (match) => mark(tokenWords({ ...(readGuided(match) || { reference: match }) })));
}

/** A text where `change` gives what takes the place of each value. It gets the value as `tokensIn` gives it. */
export function rewriteReferences(text, change) {
  return text.replace(TOKEN, (match) => change({ text: match, ...(readGuided(match) || { reference: match }) }));
}

/** The same value read from another place. A guided change on it stays on it. */
export const readFrom = (token, reference) => writeOperation(token.operation, reference, token.choice, { readAs: token.readAs });

/** The guided changes that fit a sample of this kind. */
export const operationsFor = (kind) => OPERATIONS.filter((operation) => operation.kinds.includes(kind));

/** Writes a guided change. For a date, `sample` tells how the date is written where it comes from. */
export function writeOperation(operationId, reference, choice, { sample, readAs } = {}) {
  if (!operationId) return reference;
  return operationById(operationId).write(reference, choice, readAs || dateInputPattern(sample));
}

/* ---------- Conditions in plain words ---------- */

const NUMBER = String.raw`(-?\d+(?:\.\d+)?)`;
const compares = (id, label, sign) => ({ id, label, takes: 'text', write: (reference, value) => `${reference} ${sign} ${singleQuoted(value)}`, pattern: `^(${REFERENCE}) ${sign} ${SINGLE_QUOTED}$`, read: unquoted });
const measures = (id, label, sign) => ({ id, label, takes: 'number', kinds: ['number', 'text'], write: (reference, value) => `${reference} ${sign} ${value}`, pattern: `^(${REFERENCE}) ${sign} ${NUMBER}$`, read: (value) => value });

/* `takes` is what the person fills in after the word. `kinds` limits a word to samples it works on. A number that is 0
   counts as empty to the server, so the words say so for a number. */
export const CONDITION_WORDS = [
  compares('is', 'is', '=='),
  compares('isNot', 'is not', '!='),
  { id: 'contains', label: 'contains', takes: 'text', kinds: ['text', 'date'], write: (reference, value) => `${reference}.contains(${singleQuoted(value)})`, pattern: String.raw`^(${REFERENCE})\.contains\(${SINGLE_QUOTED}\)$`, read: unquoted },
  measures('above', 'is more than', '>'),
  measures('below', 'is less than', '<'),
  { id: 'filled', label: 'has a value', labels: { boolean: 'is true', number: 'has a value other than 0' }, write: (reference) => reference, pattern: `^(${REFERENCE})$` },
  { id: 'empty', label: 'is empty', labels: { boolean: 'is false', number: 'is empty or 0' }, write: (reference) => `!${reference}`, pattern: `^!(${REFERENCE})$` },
];

export const conditionWordsFor = (kind) => CONDITION_WORDS.filter((word) => !word.kinds || word.kinds.includes(kind) || kind === 'empty')
  .map((word) => ({ ...word, label: word.labels?.[kind] || word.label }));

export function writeCondition(wordId, reference, value) {
  return CONDITION_WORDS.find((word) => word.id === wordId).write(reference, value);
}

/** A condition the editor wrote, as { reference, word, value }, or null for one written by hand. */
export function readCondition(text) {
  for (const word of CONDITION_WORDS) {
    const groups = new RegExp(word.pattern).exec(String(text ?? '').trim());
    if (groups) return { reference: groups[1], word: word.id, value: word.read ? word.read(groups[2]) : '' };
  }
  return null;
}

export function conditionWords(text) {
  const condition = readCondition(text);
  if (!condition) return null;
  const word = CONDITION_WORDS.find((candidate) => candidate.id === condition.word);
  return `${referenceWords(condition.reference)} ${word.label}${word.takes ? ` ${condition.value}` : ''}`;
}
