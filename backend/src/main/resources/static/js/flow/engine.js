/* The task model behind the Flow UI. The step catalogue comes from /task/actions, so a new action
   appears here without UI code; this file only adds labels, icons and the task graph on top. */
const CATEGORIES = [
  { id: 'logic', label: 'Logic' },
  { id: 'connection', label: 'Connections' },
  { id: 'system', label: 'This machine' },
  { id: 'browser', label: 'Browser' },
  { id: 'integration', label: 'Integrations' },
];

/* Backend action -> [key used by the UI, label, category, icon, fields a run cannot do without]. */
const PRESENTATION = {
  StartAction: ['start', 'Start', 'logic', 'play'],
  IfAction: ['if', 'If', 'logic', 'split', ['condition']],
  SwitchAction: ['switch', 'Switch', 'logic', 'signpost', ['value', 'switchCases']],
  SetVariableAction: ['setVariable', 'Set variable', 'logic', 'braces', ['setVariableList']],
  ScriptAction: ['script', 'Script', 'logic', 'code', ['script']],
  RegexAction: ['regex', 'Extract with regex', 'logic', 'regex', ['stringToCheck', 'regexPattern']],
  XMLAction: ['xml', 'Convert XML', 'logic', 'tags', ['content']],
  LinkedTaskAction: ['linkedTask', 'Run another task', 'logic', 'workflow', ['name']],
  PrintAction: ['print', 'Print message', 'logic', 'message', ['message']],
  TimedWait: ['wait', 'Wait', 'logic', 'clock', ['seconds']],
  HttpAction: ['http', 'HTTP request', 'connection', 'globe', ['url']],
  SSHAction: ['ssh', 'SSH command', 'connection', 'server', ['hostname', 'command']],
  SecureCopyAction: ['secureCopy', 'Copy file over SCP', 'connection', 'send', ['hostname', 'sourceFilename', 'destinationFilename']],
  MySQLAction: ['mysql', 'MySQL query', 'connection', 'database', ['hostname', 'command']],
  ExecuteCommandAction: ['command', 'Run command', 'system', 'terminal', ['command']],
  ReadFileAction: ['readFile', 'Read file', 'system', 'fileText', ['filename', 'parameterName']],
  WriteFileAction: ['writeFile', 'Write file', 'system', 'filePen', ['filename']],
  BrowseWebAction: ['browseWeb', 'Browse the web', 'browser', 'appWindow', ['url']],
  MouseAction: ['mouse', 'Use the mouse', 'browser', 'pointer'],
  OpenAIAction: ['openAI', 'OpenAI', 'integration', 'sparkles', ['userInput']],
  JenkinsAction: ['jenkins', 'Jenkins', 'integration', 'wrench', ['jenkinsUrl']],
};

/* Triggers are shown as one Start tile with a choice of how it starts. */
const TRIGGER_ACTIONS = { webhook: 'Webhook', interval: 'IntervalTrigger', activemq: 'ActiveMQTrigger' };
const TRIGGER_KINDS = Object.fromEntries(Object.entries(TRIGGER_ACTIONS).map(([kind, backend]) => [backend, kind]));
const TRIGGER_DEFAULTS = { webhook: { method: 'POST', authentication: 'none' }, interval: { time: '1', timeUnit: 'MINUTES' }, activemq: {} };
const STEP_ACTION_NAME = 'actionName';
const FAILURE_FIELD_NAMES = ['continueOnFailure', 'retries', 'retryWait', 'failOn'];
const SECRET_FIELDS = ['password', 'apiKey', 'apiToken', 'token'];
const AUTO_SAVE_DELAY_MS = 30000;
const AUTO_SAVE_KEY = 'autoSave';
const LAST_TASK_KEY = 'lastTask';

const ACTIONS = {};
const FAILURE_FIELDS = [];
const TYPE_BY_BACKEND = {};
const layout = { columnGap: 256, rowGap: 172 };

const uniqueId = () => Math.random().toString(36).slice(2, 9);
const clone = (value) => JSON.parse(JSON.stringify(value));

/* ---------- Catalogue ---------- */

function toField(information) {
  const common = { key: information.name, label: information.displayName || information.name, hint: information.description || undefined, defaultValue: information.defaultValue || undefined };
  const showWhen = information.displayOptions && Object.keys(information.displayOptions).length ? information.displayOptions : undefined;
  if (showWhen) common.showWhen = showWhen;
  switch (information.type) {
    case 'BOOLEAN': return [{ ...common, type: 'toggle' }];
    case 'NUMBER': return [{ ...common, type: 'number' }];
    case 'MULTILINE': return [{ ...common, type: 'code' }];
    case 'MAP': return [{ ...common, type: 'map', columns: information.options.map((option) => ({ key: option.name, label: option.displayName || option.name, options: option.type === 'DROPDOWN' ? option.options.map((choice) => choice.name) : null })) }];
    case 'DROPDOWN': {
      const select = { ...common, type: 'select', options: information.options.map((option) => ({ value: option.name, label: option.displayName || option.name })) };
      /* A field nested under some choices of a dropdown is shown only for those choices. */
      const nested = new Map();
      information.options.forEach((option) => (option.options || []).forEach((child) => {
        if (!nested.has(child.name)) nested.set(child.name, { child, choices: [] });
        nested.get(child.name).choices.push(option.name);
      }));
      const children = [...nested.values()].flatMap(({ child, choices }) => toField(child).map((field) => ({ ...field, showWhen: { ...field.showWhen, [information.name]: choices } })));
      return [select, ...children];
    }
    default:
      return [{ ...common, type: SECRET_FIELDS.includes(information.name) ? 'password' : 'expression' }];
  }
}

const fieldsOf = (information) => information.flatMap(toField);

function loadCatalogue(actions) {
  const triggers = {};
  actions.forEach((action) => {
    const backend = action.name;
    const [type, label, category, iconName, required] = PRESENTATION[backend] || [backend, backend.replace(/Action$/, ''), 'integration', 'workflow'];
    const fields = fieldsOf(action.propertyInformationList.filter((information) => information.name !== STEP_ACTION_NAME));
    if (!FAILURE_FIELDS.length) FAILURE_FIELDS.push(...fields.filter((field) => FAILURE_FIELD_NAMES.includes(field.key)));
    const own = fields.filter((field) => !FAILURE_FIELD_NAMES.includes(field.key)).map((field) => ({ ...field, required: !!required?.includes(field.key) }));
    /* The language control stays last, so what the step does comes first. */
    own.sort((first, second) => (first.key === 'scriptLanguage') - (second.key === 'scriptLanguage'));
    if (action.trigger) triggers[backend] = own.filter((field) => !['scriptLanguage', 'triggerSuffix'].includes(field.key));
    TYPE_BY_BACKEND[backend] = type;
    ACTIONS[type] = { backend, label, category, icon: iconName, description: action.description, fields: own, hidden: type === 'start' || !!action.trigger, trigger: !!action.trigger };
  });

  ACTIONS.switch.ports = (node) => [...(node.config.switchCases || []).map((switchCase, index) => ({ id: `case:${index}`, label: switchCase.currentCase || `Case ${index + 1}` })), { id: 'default', label: 'Default' }];
  ACTIONS.if.ports = () => [{ id: 'true', label: 'Yes' }, { id: 'false', label: 'No' }];
  ACTIONS.start.fields = [
    { key: 'trigger', label: 'Starts', type: 'select', options: [
      { value: 'manual', label: 'Only when I run it' },
      { value: 'webhook', label: 'When a webhook is called' },
      { value: 'interval', label: 'On a schedule' },
      { value: 'activemq', label: 'When a queue message arrives' },
    ] },
    ...Object.entries(TRIGGER_ACTIONS).flatMap(([kind, backend]) => triggers[backend].map((field) => ({ ...field, showWhen: { ...field.showWhen, trigger: [kind] } }))),
  ];
}

/* ---------- Graph helpers ---------- */

const portsOf = (node) => (ACTIONS[node.type].ports ? ACTIONS[node.type].ports(node) : [{ id: 'out', label: '' }]);
const edgeFrom = (task, nodeId, portId) => task.edges.find((edge) => edge.from === nodeId && edge.port === portId);
const nodeById = (task, nodeId) => task.nodes.find((node) => node.id === nodeId);
const rootNode = (task) => task.nodes.find((node) => node.type === 'start');
const isEntry = (node) => node.type === 'start' || ACTIONS[node.type].trigger;

function reachableIds(task, fromIds = task.nodes.filter(isEntry).map((node) => node.id), seen = new Set()) {
  fromIds.forEach((fromId) => {
    if (seen.has(fromId)) return;
    seen.add(fromId);
    reachableIds(task, task.edges.filter((edge) => edge.from === fromId).map((edge) => edge.to), seen);
  });
  return seen;
}

function downstreamIds(task, fromId) {
  const ids = reachableIds(task, [fromId]);
  ids.delete(fromId);
  return ids;
}

/* Tree layout: every leaf takes a row, a parent sits at the middle of its children. */
function computeLayout(task) {
  const placements = {};
  const seen = new Set();
  let nextRow = 0;
  const place = (nodeId, depth) => {
    seen.add(nodeId);
    const node = nodeById(task, nodeId);
    const childRows = portsOf(node)
      .map((port) => edgeFrom(task, nodeId, port.id))
      .filter((edge) => edge && !seen.has(edge.to))
      .map((edge) => place(edge.to, depth + 1));
    const row = childRows.length ? (childRows[0] + childRows[childRows.length - 1]) / 2 : nextRow++;
    placements[nodeId] = { column: depth, row };
    return row;
  };
  place(rootNode(task).id, 0);
  task.nodes.filter((node) => !seen.has(node.id)).forEach((node) => { placements[node.id] = { column: 1, row: nextRow++ }; });
  return placements;
}

function layoutTask(task) {
  const placements = computeLayout(task);
  task.nodes.forEach((node) => {
    node.position = { x: placements[node.id].column * layout.columnGap, y: placements[node.id].row * layout.rowGap };
  });
  return task;
}

/* ---------- Human descriptions ---------- */

function readableExpression(text) {
  return String(text ?? '')
    .replace(/\$parameters\.get\("([^"]+)"\)/g, '$1')
    .replace(/\$lastLog\.output/g, 'previous output')
    .replace(/\s*==\s*"?([^"]*)"?$/, ' is $1')
    .replace(/\s*!=\s*"?([^"]*)"?$/, ' is not $1');
}

/* Keeps literal text readable while showing where a parameter lands: /etc/app/{env}.yml */
const inlineVariables = (text) => String(text ?? '')
  .replace(/\$parameters\.get\("([^"]+)"\)/g, '{$1}')
  .replace(/\$lastLog\.output/g, '{previous output}');

const firstLine = (text) => String(text ?? '').split('\n')[0];

/* A step as a sentence: strings are prose, objects are values the reader can edit. */
function describeNode(node) {
  const config = node.config;
  const value = (text, fallback) => ({ value: text ? firstLine(text) : fallback, empty: !text });
  switch (node.type) {
    case 'start':
      return ({
        manual: ['Starts ', { value: 'when I run it' }],
        webhook: ['Starts on ', { value: `${config.method || 'POST'} /${config.path || ''}` }],
        interval: ['Starts every ', { value: `${config.time || 1} ${(config.timeUnit || 'HOURS').toLowerCase()}` }],
        activemq: ['Starts on messages from ', value(config.queue, 'no queue')],
      })[config.trigger] || ['Starts'];
    case 'if': return ['If ', value(readableExpression(config.condition), 'a condition')];
    case 'switch': return ['Check ', value(readableExpression(config.value), 'a value')];
    case 'setVariable': return ['Set ', value((config.setVariableList || [])[0]?.name, 'a variable')];
    case 'script': return ['Run ', value(config.script, 'a script')];
    case 'regex': return ['Extract ', value(config.regexPattern, 'a pattern'), ' from ', value(readableExpression(config.stringToCheck), 'text')];
    case 'xml': return ['Convert ', value(readableExpression(config.content), 'content')];
    case 'linkedTask': return ['Run the task ', value(config.name, 'not chosen')];
    case 'print': return ['Print ', value(inlineVariables(config.message), 'a message')];
    case 'wait': return ['Wait ', value(config.seconds && `${config.seconds} seconds`, 'a while')];
    case 'http': return [{ value: config.method || 'GET' }, ' ', value(inlineVariables(config.url), 'a URL')];
    case 'ssh': return ['On ', value(config.hostname, 'a host'), ' run ', value(inlineVariables(config.command), 'a command')];
    case 'secureCopy': return ['Copy ', value(config.sourceFilename, 'a file'), ' to ', value(config.hostname, 'a host')];
    case 'mysql': return ['On ', value(config.database && `${config.database}@${config.hostname}`, 'a database'), ' run ', value(inlineVariables(config.command), 'SQL')];
    case 'command': return ['Run ', value(inlineVariables(config.command), 'a command')];
    case 'readFile': return ['Read ', value(inlineVariables(config.filename), 'a file')];
    case 'writeFile': return ['Write ', value(inlineVariables(config.filename), 'a file')];
    case 'browseWeb': return ['Open ', value(inlineVariables(config.url), 'a page')];
    case 'mouse': return [{ value: (config.action || 'CLICK').toLowerCase() }, ' the mouse'];
    case 'openAI': return ['Ask ', { value: config.model || 'OpenAI' }, ' ', value(readableExpression(config.userInput), 'a prompt')];
    case 'jenkins': return [{ value: config.operation || 'Call' }, ' on Jenkins'];
    default: return [ACTIONS[node.type].description || ACTIONS[node.type].label];
  }
}

const summaryText = (node) => describeNode(node).map((part) => (typeof part === 'string' ? part : part.value)).join('');

/* ---------- Validation ---------- */

function fieldVisible(field, config) {
  if (!field.showWhen) return true;
  return Object.entries(field.showWhen).every(([key, expected]) => (Array.isArray(expected) ? expected.includes(String(config[key] ?? '')) : config[key] === expected));
}

function findIssues(task) {
  const issues = [];
  const reachable = reachableIds(task);
  task.nodes.forEach((node) => {
    ACTIONS[node.type].fields.forEach((field) => {
      if (!field.required || !fieldVisible(field, node.config)) return;
      const fieldValue = node.config[field.key];
      const empty = fieldValue === undefined || fieldValue === '' || (Array.isArray(fieldValue) && fieldValue.length === 0);
      if (empty) issues.push({ nodeId: node.id, field: field.key, message: `${field.label} is required` });
    });
    if (!reachable.has(node.id)) issues.push({ nodeId: node.id, message: 'Not connected to the task, so it will not run' });
  });
  return issues;
}

function interpolate(text, variables) {
  return String(text ?? '')
    .replace(/\$parameters\.get\("([^"]+)"\)/g, (match, name) => variables.parameters[name] ?? '')
    .replace(/\$lastLog\.output/g, variables.lastOutput ?? '')
    .replace(/\$executionId/g, variables.executionId);
}

/* ---------- The saved task format ---------- */

const portId = (nodeId, port) => `${nodeId}:${port}`;
const INPUT_PORT = 'in';
const TRIGGER_PORT = 'trigger';
const LAYOUT_FIELDS = ['@type', 'ports', 'guiXPos', 'guiYPos', STEP_ACTION_NAME];

const hasText = (row) => Object.values(row).some((value) => value !== '');

function withoutEmptyValues(config) {
  return Object.fromEntries(Object.entries(config)
    .filter(([, value]) => value !== '' && value !== undefined && value !== null)
    .map(([key, value]) => [key, Array.isArray(value) ? value.filter(hasText) : value]));
}

function createNode(id, type, name, config) {
  const defaults = Object.fromEntries(ACTIONS[type].fields.filter((field) => field.type === 'select').map((field) => [field.key, field.defaultValue ?? field.options[0].value]));
  return { id, type, name: name || ACTIONS[type].label, config: { ...(type === 'start' ? { trigger: 'manual' } : defaults), ...(type === 'switch' ? { switchCases: [{ currentCase: 'first' }, { currentCase: 'second' }] } : {}), ...config }, position: { x: 0, y: 0 } };
}

function triggerConfigKeys(kind) {
  return ACTIONS[TYPE_BY_BACKEND[TRIGGER_ACTIONS[kind]]].fields.map((field) => field.key);
}

/* Start and its trigger are one tile here, and two actions that lead to the same step in the saved
   task, so that a task with a trigger can still be run by hand. */
function toBackend(task) {
  const actions = [];
  const connections = [];
  task.nodes.forEach((node) => {
    const position = { guiXPos: Math.round(node.position.x), guiYPos: Math.round(node.position.y) };
    const inputs = isEntry(node) ? [] : [{ externalId: portId(node.id, INPUT_PORT), portType: 'INPUT' }];
    const outputs = portsOf(node).map((port) => ({ externalId: portId(node.id, port.id), portType: 'OUTPUT' }));
    if (node.type === 'start') {
      actions.push({ '@type': ACTIONS.start.backend, ports: outputs, ...position });
      const kind = node.config.trigger;
      if (TRIGGER_ACTIONS[kind]) {
        const keys = triggerConfigKeys(kind);
        const settings = Object.fromEntries([...keys, 'triggerSuffix'].filter((key) => key in node.config).map((key) => [key, node.config[key]]));
        actions.push({ '@type': TRIGGER_ACTIONS[kind], ...withoutEmptyValues(settings), ports: [{ externalId: portId(node.id, TRIGGER_PORT), portType: 'OUTPUT' }], ...position });
      }
    } else {
      actions.push({ '@type': ACTIONS[node.type].backend, [STEP_ACTION_NAME]: node.name, ...withoutEmptyValues(node.config), ports: [...inputs, ...outputs], ...position });
    }
  });
  task.edges.forEach((edge) => {
    const target = { externalId: portId(edge.to, INPUT_PORT) };
    connections.push({ source: { externalId: portId(edge.from, edge.port) }, target });
    if (edge.from === rootNode(task).id && TRIGGER_ACTIONS[rootNode(task).config.trigger]) connections.push({ source: { externalId: portId(edge.from, TRIGGER_PORT) }, target });
  });
  return { name: task.name, active: !!task.active, description: task.description || '', parameters: task.parameters, actions, connections };
}

const outputPortsOf = (action) => (action.ports || []).filter((port) => port.portType === 'OUTPUT');

/* Reads any saved or imported task, including ones made before this UI, whose port ids carry no meaning. */
function fromBackend(saved) {
  const portOwners = {};
  if (!Array.isArray(saved.actions)) throw new Error('That file is not a task, since it has no steps.');
  const nodes = saved.actions.map((action) => {
    const type = TYPE_BY_BACKEND[action['@type']];
    if (!type) throw new Error(`This task uses a step this version does not know: ${action['@type']}.`);
    const config = Object.fromEntries(Object.entries(action).filter(([key]) => !LAYOUT_FIELDS.includes(key)));
    /* Keeping the saved id keeps the checksum, so saving an unchanged task adds no version. */
    const savedId = !ACTIONS[type].trigger && action.ports?.[0]?.externalId.match(/^([^:]+):/)?.[1];
    const node = createNode(savedId || `n${uniqueId()}`, type, action[STEP_ACTION_NAME] && action[STEP_ACTION_NAME] !== action['@type'] ? action[STEP_ACTION_NAME] : undefined, config);
    node.position = { x: action.guiXPos || 0, y: action.guiYPos || 0 };
    node.backendAction = action;
    (action.ports || []).forEach((port) => { portOwners[port.externalId] = node.id; });
    return node;
  });
  if (!nodes.some((node) => node.type === 'start')) throw new Error('This task has no start step.');

  const outputName = (node, externalId) => {
    const index = outputPortsOf(node.backendAction).findIndex((port) => port.externalId === externalId);
    return portsOf(node)[index]?.id || 'out';
  };
  const task = { id: saved.name, name: saved.name, description: saved.description || '', parameters: saved.parameters || [], active: !!saved.active, checksum: saved.checksum, nodes, edges: [], portOwners };
  (saved.connections || []).forEach(({ source, target }) => {
    const from = nodeById(task, portOwners[source.externalId]);
    const to = portOwners[target.externalId];
    if (from && to) task.edges.push({ from: from.id, port: outputName(from, source.externalId), to });
  });

  /* A single trigger joins the Start tile when both lead to the same step. Anything else stays as its own tile. */
  const start = rootNode(task);
  const triggers = nodes.filter((node) => ACTIONS[node.type].trigger);
  const startEdge = task.edges.find((edge) => edge.from === start.id);
  const triggerEdge = triggers.length === 1 && task.edges.find((edge) => edge.from === triggers[0].id);
  if (triggers.length === 1 && (!startEdge || !triggerEdge || startEdge.to === triggerEdge.to)) {
    const trigger = triggers[0];
    Object.assign(start.config, trigger.config, { trigger: TRIGGER_KINDS[trigger.backendAction['@type']] });
    outputPortsOf(trigger.backendAction).forEach((port) => { portOwners[port.externalId] = start.id; });
    task.nodes = nodes.filter((node) => node !== trigger);
    task.edges = task.edges.filter((edge) => edge.from !== trigger.id && edge.from !== start.id);
    if (startEdge || triggerEdge) task.edges.push({ from: start.id, port: 'out', to: (startEdge || triggerEdge).to });
  }
  task.nodes.forEach((node) => { delete node.backendAction; });

  /* Tasks without usable positions are laid out, since their steps would otherwise sit on top of each other. */
  const positions = new Set(task.nodes.map((node) => `${node.position.x},${node.position.y}`));
  if (positions.size < task.nodes.length) layoutTask(task);
  return task;
}

/* ---------- Server ---------- */

async function request(url, options = {}) {
  const response = await fetch(url, { ...options, headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}) } })
    .catch(() => { throw new Error('The server could not be reached. Nothing was changed there.'); });
  const text = await response.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch (error) { body = null; }
  if (!response.ok) throw new Error(body?.message || body?.error || `The server answered ${response.status}.`);
  return body;
}

/* ---------- Runs ---------- */

const RUN_STATUSES = { FINISHED: 'success', FAILED: 'failed' };
const SWITCH_PORT = /port nr: (\d+)/;

function choosePort(node, output) {
  if (node.type === 'if') return output === 'true' ? 'true' : 'false';
  if (node.type === 'switch') return portsOf(node)[Number((output || '').match(SWITCH_PORT)?.[1])]?.id || null;
  return 'out';
}

function stepStateAt(step, cursorMs) {
  if (cursorMs < step.startMs) return 'pending';
  if (cursorMs < step.startMs + step.durationMs) return 'running';
  return step.status;
}

function createRunner(onChange) {
  const runner = {
    run: null, cursorMs: 0, playing: false, frame: null, lastTick: 0, speed: 1,
    load(run, { atEnd = true } = {}) { runner.stop(); runner.run = run; runner.cursorMs = atEnd ? run.durationMs : 0; onChange(); },
    play() {
      if (!runner.run || runner.run.live) return;
      if (runner.cursorMs >= runner.run.durationMs) runner.cursorMs = 0;
      runner.playing = true; runner.lastTick = performance.now();
      const tick = (now) => {
        if (!runner.playing) return;
        runner.cursorMs = Math.min(runner.run.durationMs, runner.cursorMs + (now - runner.lastTick) * runner.speed);
        runner.lastTick = now;
        if (runner.cursorMs >= runner.run.durationMs) { runner.playing = false; onChange('finished'); return; }
        onChange('tick');
        runner.frame = requestAnimationFrame(tick);
      };
      onChange();
      runner.frame = requestAnimationFrame(tick);
    },
    pause() { runner.playing = false; cancelAnimationFrame(runner.frame); onChange(); },
    stop() { runner.playing = false; cancelAnimationFrame(runner.frame); },
    seek(ms) { runner.cursorMs = Math.max(0, Math.min(runner.run.durationMs, ms)); onChange('tick'); },
    clear() { runner.stop(); runner.run = null; runner.cursorMs = 0; onChange(); },
    get finished() { return !!runner.run && !runner.run.live && runner.cursorMs >= runner.run.durationMs; },
    stepFor(nodeId) { return runner.run && runner.run.steps.find((step) => step.nodeId === nodeId); },
    stateOf(nodeId) {
      if (!runner.run) return 'idle';
      const step = runner.stepFor(nodeId);
      if (step) return stepStateAt(step, runner.cursorMs);
      if (runner.run.live && runner.run.nextNodeId === nodeId) return 'running';
      return runner.finished ? 'skipped' : 'pending';
    },
    activeStep() {
      if (!runner.run) return null;
      const steps = runner.run.steps;
      return steps.find((step) => stepStateAt(step, runner.cursorMs) === 'running') || [...steps].reverse().find((step) => step.startMs <= runner.cursorMs) || steps[0];
    },
  };
  return runner;
}

/* ---------- Store ---------- */

function freshTask() {
  return { id: `task_${uniqueId()}`, name: `task_${uniqueId()}`, description: '', parameters: [], active: false, nodes: [createNode('start', 'start', 'Start')], edges: [], portOwners: {} };
}

function createStore() {
  const listeners = new Set();
  let tasks = [];
  let task = freshTask();
  let selectedId = null;
  let saveState = 'unsaved';
  let autoSave = localStorage.getItem(AUTO_SAVE_KEY) === 'true';
  let autoSaveTimer = null;
  let undoStack = [];
  let redoStack = [];
  let coalesceKey = null;
  let coalesceTime = 0;
  let runs = [];
  /* The task this editor last read or wrote, which a Save may add a version to. */
  let savedName = null;

  const emit = (change) => listeners.forEach((listener) => listener(change));

  window.addEventListener('beforeunload', (event) => {
    if (saveState === 'unsaved' && task.nodes.length > 1) event.preventDefault();
  });

  const setSaveState = (state) => { saveState = state; emit('save'); };

  /* A new version takes effect immediately, so auto save pauses while triggers are live. */
  const scheduleAutoSave = () => {
    clearTimeout(autoSaveTimer);
    if (autoSave && !task.active && saveState === 'unsaved' && task.checksum) autoSaveTimer = setTimeout(() => store.save({ autoSaved: true }).catch(() => {}), AUTO_SAVE_DELAY_MS);
  };
  const markChanged = () => { setSaveState('unsaved'); scheduleAutoSave(); };

  const snapshot = () => JSON.stringify(task);
  const remember = (key) => {
    const now = Date.now();
    if (key && key === coalesceKey && now - coalesceTime < 900) { coalesceTime = now; return; }
    coalesceKey = key || null; coalesceTime = now;
    undoStack.push(snapshot());
    if (undoStack.length > 100) undoStack.shift();
    redoStack = [];
  };
  const restore = (serialized) => {
    task = JSON.parse(serialized);
    if (selectedId && !nodeById(task, selectedId)) selectedId = null;
  };
  const changed = (change) => { markChanged(); emit(change); };

  const runner = createRunner((kind) => emit(kind === 'tick' ? 'tick' : 'run'));

  const loadTaskList = async () => {
    const grouped = await request('/task/grouped');
    tasks = Object.entries(grouped).map(([name, versions]) => ({ id: name, name, versions }));
    emit('tasks');
  };

  const runListItem = (execution) => ({
    id: execution.executionId, status: RUN_STATUSES[execution.status] || 'running', taskName: execution.taskName,
    startedAt: new Date(execution.startDate).getTime(), durationMs: execution.endDate ? new Date(execution.endDate) - new Date(execution.startDate) : 0,
    parameters: {}, steps: [], complete: false,
  });

  const loadRuns = async () => {
    const { executions } = await request('/executions');
    runs = executions.filter((execution) => execution.taskName === task.name && execution.startDate).map(runListItem).sort((first, second) => second.startedAt - first.startedAt);
    /* The latest run supplies the values shown in the step panel, also after a reload. */
    if (runs[0]) applyExecution(runs[0], await request(`/execution/${runs[0].id}`));
    emit('run');
  };

  /* A step names its action by the first port; on the Start tile that can be the trigger's port. */
  const nodeForPort = (externalId, actionType) => {
    const owner = task.portOwners?.[externalId] || externalId?.split(':')[0];
    if (owner && nodeById(task, owner)) return nodeById(task, owner);
    const type = TYPE_BY_BACKEND[actionType];
    if (TRIGGER_KINDS[actionType]) return rootNode(task);
    return task.nodes.find((node) => node.type === type) || null;
  };

  const toStep = (raw, startMs, previous, parameters, executionId) => {
    const node = nodeForPort(raw.portId, raw.actionType);
    if (!node) return null;
    const failed = raw.status === 'FAILURE';
    const output = raw.output || '';
    return {
      nodeId: node.id, type: node.type, name: node.name, startMs, durationMs: Math.max(raw.durationMs, 1),
      status: failed ? (node.config.continueOnFailure ? 'warning' : 'failed') : 'success',
      output: failed && !raw.error ? '' : output, error: failed ? raw.error || output || 'This step failed.' : null,
      port: failed && !node.config.continueOnFailure ? null : choosePort(node, output),
      variables: { parameters, lastOutput: previous?.output || '', executionId },
    };
  };

  const stepsOf = (rawSteps, parameters, executionId) => {
    const steps = [];
    let cursor = 0;
    rawSteps.forEach((raw) => {
      const step = toStep(raw, cursor, steps[steps.length - 1], parameters, executionId);
      if (!step) return;
      steps.push(step);
      cursor += step.durationMs;
    });
    return steps;
  };

  const applyExecution = (run, execution) => {
    run.status = RUN_STATUSES[execution.status] || 'running';
    run.parameters = execution.parameters || run.parameters;
    run.steps = stepsOf(execution.steps || [], run.parameters, run.id);
    run.logLines = execution.output || [];
    run.durationMs = run.steps.reduce((total, step) => total + step.durationMs, 0);
    run.complete = true;
    /* A failure with no step, such as a missing parameter, is still shown as a failed run. */
    if (run.status === 'failed' && !run.steps.some((step) => step.status === 'failed')) {
      run.failure = run.logLines.find((line) => /failed|not active|could not/i.test(line)) || 'This run failed before reaching a step.';
    }
    return run;
  };

  const nextNodeAfter = (step) => {
    const edge = step.port ? edgeFrom(task, step.nodeId, step.port) : null;
    return edge ? edge.to : null;
  };

  /* The broker streams each step as it finishes. The execution record decides the final result, and
     is polled rather than read on the last message, because that message arrives before the run commits. */
  const followRun = (run) => {
    let finished = false;
    let client = null;
    const finish = async () => {
      if (finished) return;
      finished = true;
      clearInterval(poll);
      try { client?.disconnect(); } catch (error) { /* already closed */ }
      applyExecution(run, await request(`/execution/${run.id}`));
      run.live = false;
      if (runner.run === run) { runner.cursorMs = run.durationMs; runner.playing = false; }
      emit('run');
    };
    const poll = setInterval(async () => {
      try {
        const execution = await request(`/execution/${run.id}`);
        if (RUN_STATUSES[execution.status]) finish();
      } catch (error) { /* the next poll tries again */ }
    }, 400);
    try {
      client = window.Stomp.client(`ws://${window.location.hostname}:61614/stomp`, 'v11.stomp');
      client.debug = null;
      client.connect('admin', 'admin', () => {
        client.subscribe('/queue/taskexecution_destination', (message) => {
          message.ack();
          if (finished) return;
          const { headers } = message;
          if (headers.stepPortId !== undefined || headers.stepActionType) {
            const body = JSON.parse(message.body);
            const failed = headers.stepStatus === 'FAILURE';
            const step = toStep({ portId: headers.stepPortId, actionType: headers.stepActionType, status: headers.stepStatus, output: failed ? '' : body.output, error: failed ? body.output : null, durationMs: Number(headers.stepDurationMs) }, run.durationMs, run.steps[run.steps.length - 1], run.parameters, run.id);
            if (step) {
              run.steps.push(step);
              run.durationMs += step.durationMs;
              run.nextNodeId = nextNodeAfter(step);
              if (runner.run === run) runner.cursorMs = run.durationMs;
              emit('run');
            }
          }
        }, { id: `run-${run.id}`, ack: 'client', selector: `executionId=${run.id}` });
      }, () => { /* No live stream, so the poll still reports the result. */ });
    } catch (error) { /* same as above */ }
  };

  const store = {
    runner,
    get task() { return task; },
    get selectedId() { return selectedId; },
    get selectedNode() { return selectedId ? nodeById(task, selectedId) : null; },
    get saveState() { return saveState; },
    get canUndo() { return undoStack.length > 0; },
    get canRedo() { return redoStack.length > 0; },
    get tasks() { return tasks; },
    get runs() { return runs; },
    get issues() { return findIssues(task); },
    get defaults() { return Object.fromEntries(task.parameters.map((parameter) => [parameter.name, parameter.defaultValue || ''])); },
    get autoSave() { return autoSave; },
    get hasSecrets() { return task.nodes.some((node) => SECRET_FIELDS.some((key) => node.config[key])); },
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
    loadTaskList,

    select(nodeId) { if (nodeId === selectedId) return; selectedId = nodeId; emit('selection'); },
    updateConfig(nodeId, key, value) {
      remember(`${nodeId}.${key}`);
      const node = nodeById(task, nodeId);
      node.config[key] = value;
      if (node.type === 'start' && key === 'trigger' && TRIGGER_ACTIONS[value]) {
        Object.entries(TRIGGER_DEFAULTS[value]).forEach(([name, preset]) => { if (node.config[name] === undefined) node.config[name] = preset; });
        if (value === 'webhook') node.config.triggerSuffix ??= uniqueId();
        task.active = true;
      }
      changed(key === 'switchCases' ? 'structure' : 'config');
    },
    rename(nodeId, name) { remember(`${nodeId}.name`); nodeById(task, nodeId).name = name; changed('config'); },
    updateTask(fields) { remember(`task.${Object.keys(fields)[0]}`); Object.assign(task, fields); changed('meta'); },
    setParameters(parameters) { remember(); task.parameters = parameters; changed('meta'); },
    setAutoSave(enabled) {
      autoSave = enabled;
      localStorage.setItem(AUTO_SAVE_KEY, enabled);
      scheduleAutoSave();
      emit('meta');
    },

    beginMove() { remember(); },
    moveNode(nodeId, position) { nodeById(task, nodeId).position = position; emit('move'); },
    endMove() { markChanged(); },

    addStep(type, { fromId, port, position } = {}) {
      remember();
      const node = createNode(`n${uniqueId()}`, type, nameWithNumber(task, ACTIONS[type].label));
      const anchor = fromId || selectedId || lastOnMainPath(task);
      const anchorNode = nodeById(task, anchor);
      const anchorPort = port || (portsOf(anchorNode).find((candidate) => !edgeFrom(task, anchor, candidate.id)) || portsOf(anchorNode)[0]).id;
      const existing = position && !fromId ? null : edgeFrom(task, anchor, anchorPort);
      if (position && !fromId) {
        node.position = position;
        task.nodes.push(node);
      } else if (existing) {
        const target = nodeById(task, existing.to);
        const shifted = new Set([existing.to, ...downstreamIds(task, existing.to)]);
        node.position = { ...target.position };
        task.nodes.forEach((candidate) => { if (shifted.has(candidate.id)) candidate.position = { x: candidate.position.x + layout.columnGap, y: candidate.position.y }; });
        task.nodes.push(node);
        existing.to = node.id;
        const nextPort = portsOf(node)[0];
        if (nextPort) task.edges.push({ from: node.id, port: nextPort.id, to: target.id });
      } else {
        const portIndex = Math.max(0, portsOf(anchorNode).findIndex((candidate) => candidate.id === anchorPort));
        node.position = position || { x: anchorNode.position.x + layout.columnGap, y: anchorNode.position.y + (portsOf(anchorNode).length > 1 ? (portIndex - (portsOf(anchorNode).length - 1) / 2) * layout.rowGap : 0) };
        task.nodes.push(node);
        task.edges.push({ from: anchor, port: anchorPort, to: node.id });
      }
      selectedId = node.id;
      changed('structure');
      return node;
    },
    removeNode(nodeId) {
      const node = nodeById(task, nodeId);
      if (!node || node.type === 'start') return false;
      remember();
      const incoming = task.edges.filter((edge) => edge.to === nodeId);
      const outgoing = task.edges.filter((edge) => edge.from === nodeId);
      if (outgoing.length === 1) incoming.forEach((edge) => { edge.to = outgoing[0].to; });
      task.edges = task.edges.filter((edge) => edge.to !== nodeId && edge.from !== nodeId);
      task.nodes = task.nodes.filter((candidate) => candidate.id !== nodeId);
      if (selectedId === nodeId) selectedId = null;
      changed('structure');
      return true;
    },
    duplicateNode(nodeId) {
      const source = nodeById(task, nodeId);
      if (!source || source.type === 'start') return null;
      remember();
      const copy = { ...clone(source), id: `n${uniqueId()}`, name: nameWithNumber(task, source.name.replace(/ \d+$/, '')), position: { x: source.position.x + 28, y: source.position.y + 96 } };
      task.nodes.push(copy);
      selectedId = copy.id;
      changed('structure');
      return copy;
    },
    connect(fromId, port, toId) {
      if (fromId === toId || reachableIds(task, [toId]).has(fromId)) return false;
      remember();
      task.edges = task.edges.filter((edge) => !(edge.from === fromId && edge.port === port));
      task.edges.push({ from: fromId, port, to: toId });
      changed('structure');
      return true;
    },
    disconnect(fromId, port) {
      remember();
      task.edges = task.edges.filter((edge) => !(edge.from === fromId && edge.port === port));
      changed('structure');
    },
    tidy() { remember(); layoutTask(task); changed('structure'); },
    undo() { if (!undoStack.length) return; redoStack.push(snapshot()); restore(undoStack.pop()); coalesceKey = null; changed('structure'); },
    redo() { if (!redoStack.length) return; undoStack.push(snapshot()); restore(redoStack.pop()); coalesceKey = null; changed('structure'); },

    /* Every save is a new version on the server, unless the content is unchanged. */
    async save({ autoSaved = false } = {}) {
      clearTimeout(autoSaveTimer);
      const renamedFrom = savedName && savedName !== task.name ? savedName : null;
      if (task.name !== savedName && tasks.some((candidate) => candidate.name === task.name)) {
        throw new Error(`A task called ${task.name} already exists. Choose another name, or open that task to change it.`);
      }
      setSaveState('saving');
      try {
        const { checksum } = await request(autoSaved ? '/task?autoSaved=true' : '/task', { method: 'POST', body: JSON.stringify(toBackend(task)) });
        task.checksum = checksum;
        task.id = task.name;
        localStorage.setItem(LAST_TASK_KEY, task.name);
        savedName = task.name;
        setSaveState('saved');
        await loadTaskList();
        return renamedFrom ? `Saved as a new task. ${renamedFrom} is still there.` : 'Saved';
      } catch (error) {
        setSaveState('unsaved');
        throw error;
      }
    },
    async saveAs(name, description) {
      task.name = name;
      task.description = description;
      emit('meta');
      return store.save();
    },
    async openVersion(checksum) {
      task = fromBackend(await request(`/task/${checksum}/checksum`));
      localStorage.setItem(LAST_TASK_KEY, task.name);
      savedName = task.name;
      selectedId = null; undoStack = []; redoStack = [];
      runner.clear();
      saveState = 'saved';
      emit('task');
      await loadRuns();
    },
    openLastTask() { return store.switchTask(localStorage.getItem(LAST_TASK_KEY)); },
    async switchTask(name) {
      const entry = tasks.find((candidate) => candidate.id === name);
      if (entry) await store.openVersion(entry.versions[0].checksum);
    },
    async deleteTask(name) {
      const entry = tasks.find((candidate) => candidate.id === name);
      /* One at a time, so the last deletion can see that no versions remain and stop the task's triggers. */
      for (const version of entry.versions) await request(`/task/${version.checksum}`, { method: 'DELETE' });
      if (task.name === name) { savedName = null; task.checksum = undefined; setSaveState('unsaved'); runs = []; emit('run'); }
      await loadTaskList();
    },
    newTask() {
      task = freshTask();
      savedName = null;
      selectedId = null; undoStack = []; redoStack = []; runs = [];
      runner.clear();
      saveState = 'unsaved';
      emit('task');
    },
    importTask(serialized) {
      task = fromBackend(JSON.parse(serialized));
      /* Importing under an existing name is how an exported version is restored. */
      savedName = task.name;
      selectedId = null; undoStack = []; redoStack = []; runs = [];
      runner.clear();
      saveState = 'unsaved';
      emit('task');
    },
    exportTask() { return JSON.stringify(toBackend(task), null, 2); },

    /* With resumeFrom, continues a failed run at its failed step: the steps before it keep their results. */
    async startRun(parameterValues, { resumeFrom } = {}) {
      if (saveState !== 'saved') await store.save();
      const { id } = await request('/schedule/task', { method: 'POST', body: JSON.stringify({ taskName: task.name, parameters: parameterValues, resumeFromExecutionId: resumeFrom?.id }) });
      const finishedSteps = resumeFrom ? resumeFrom.steps.slice(0, -1) : [];
      const run = { id: String(id), status: 'running', live: true, parameters: parameterValues, startedAt: Date.now(), durationMs: finishedSteps.reduce((total, step) => total + step.durationMs, 0), steps: [...finishedSteps], complete: false, nextNodeId: resumeFrom ? resumeFrom.steps.at(-1).nodeId : rootNode(task).id };
      runs = [run, ...runs];
      runner.load(run, { atEnd: true });
      followRun(run);
      return run;
    },
    async openRun(runId) {
      const run = runs.find((candidate) => candidate.id === runId);
      if (!run.complete) applyExecution(run, await request(`/execution/${run.id}`));
      runner.load(run);
    },
    /* Runs one step on its own with the values given, without touching the run history. */
    async testStep(nodeId, { parameters, lastOutput }) {
      const node = nodeById(task, nodeId);
      const action = { '@type': ACTIONS[node.type].backend, ...withoutEmptyValues(node.config), ports: [] };
      const started = performance.now();
      const result = await request('/task/action/test', { method: 'POST', body: JSON.stringify({ action, parameters, lastOutput }) });
      const failed = result.statusCode === 'FAILURE';
      return { nodeId, status: failed ? 'failed' : 'success', output: failed && !result.errorMsg ? '' : result.output, error: failed ? result.errorMsg || result.output || 'This step failed.' : null, durationMs: performance.now() - started };
    },
  };
  return store;
}

/* Run command, Run command 2: a failure or a line in the history must point at one step. */
function nameWithNumber(task, baseName) {
  const names = new Set(task.nodes.map((node) => node.name));
  if (!names.has(baseName)) return baseName;
  let number = 2;
  while (names.has(`${baseName} ${number}`)) number += 1;
  return `${baseName} ${number}`;
}

function lastOnMainPath(task) {
  let current = rootNode(task);
  const seen = new Set();
  while (current && !seen.has(current.id)) {
    seen.add(current.id);
    const edge = portsOf(current).map((port) => edgeFrom(task, current.id, port.id)).find(Boolean);
    if (!edge) return current.id;
    current = nodeById(task, edge.to);
  }
  return rootNode(task).id;
}

function formatDuration(ms) {
  if (ms < 1000) return `${Math.round(ms)} ms`;
  return `${(ms / 1000).toFixed(1)} s`;
}

function formatAgo(timestamp) {
  const minutes = Math.round((Date.now() - timestamp) / 60000);
  if (minutes < 1) return 'just now';
  if (minutes < 60) return `${minutes} min ago`;
  if (minutes < 1440) return `${Math.round(minutes / 60)} h ago`;
  return `${Math.round(minutes / 1440)} d ago`;
}

export default async function bootEngine() {
  loadCatalogue((await request('/task/actions')).actions);
  window.DT = {
    ACTIONS, CATEGORIES, FAILURE_FIELDS,
    createStore, portsOf, edgeFrom, nodeById, rootNode,
    describeNode, summaryText, fieldVisible, interpolate,
    formatDuration, formatAgo,
  };
}
