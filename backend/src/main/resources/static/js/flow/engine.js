/* The task model behind the Flow UI. The step catalogue comes from /task/actions, so a new action
   appears here without UI code; this file only adds labels, icons and the task graph on top. */
import { conditionWords, hasHandWrittenLogic, inWords, readFrom, readReference, rewriteReferences, stepReference, tokensIn } from './expressions';

const CATEGORIES = [
  { id: 'starts', label: 'Starts the task' },
  { id: 'logic', label: 'Logic' },
  { id: 'connection', label: 'Connections' },
  { id: 'system', label: 'This machine' },
  { id: 'browser', label: 'Browser' },
  { id: 'messages', label: 'Messages and alerts' },
  { id: 'work', label: 'Work and tickets' },
  { id: 'sales', label: 'Sales and shops' },
  { id: 'operations', label: 'Operations and servers' },
  { id: 'documents', label: 'Documents and tables' },
  { id: 'ai', label: 'AI' },
  { id: 'integration', label: 'Integrations' },
];

/* Backend action -> [key used by the UI, label, category, icon, fields a run cannot do without]. */
const PRESENTATION = {
  StartAction: ['start', 'Start', 'logic', 'play'],
  IfAction: ['if', 'If', 'logic', 'split', ['condition']],
  SwitchAction: ['switch', 'Switch', 'logic', 'signpost', ['value', 'switchCases']],
  LoopAction: ['loop', 'Loop over items', 'logic', 'repeat'],
  SetVariableAction: ['setVariable', 'Set variable', 'logic', 'braces', ['setVariableList']],
  ScriptAction: ['script', 'Script', 'logic', 'code', ['script']],
  RegexAction: ['regex', 'Extract with regex', 'logic', 'regex', ['stringToCheck', 'regexPattern']],
  XMLAction: ['xml', 'Convert XML', 'logic', 'tags', ['content']],
  LinkedTaskAction: ['linkedTask', 'Run another task', 'logic', 'workflow', ['name']],
  PrintAction: ['print', 'Print message', 'logic', 'message', ['message']],
  TimedWait: ['wait', 'Wait', 'logic', 'clock', ['seconds']],
  WebhookReplyAction: ['webhookReply', 'Reply to webhook', 'logic', 'webhook'],
  HttpAction: ['http', 'HTTP request', 'connection', 'globe', ['url']],
  SSHAction: ['ssh', 'SSH command', 'connection', 'server', ['hostname', 'command']],
  SecureCopyAction: ['secureCopy', 'Copy file over SCP', 'connection', 'send', ['hostname', 'sourceFilename', 'destinationFilename']],
  MySQLAction: ['mysql', 'MySQL query', 'connection', 'mySql', ['hostname', 'command']],
  ExecuteCommandAction: ['command', 'Run command', 'system', 'terminal', ['command']],
  ReadFileAction: ['readFile', 'Read file', 'system', 'fileText', ['filename']],
  WriteFileAction: ['writeFile', 'Write file', 'system', 'filePen', ['filename']],
  BrowseWebAction: ['browseWeb', 'Browse the web', 'browser', 'appWindow', ['url']],
  MouseAction: ['mouse', 'Use the mouse', 'browser', 'pointer'],
  OpenAIAction: ['openAI', 'OpenAI', 'ai', 'openAi', ['model', 'userInput']],
  JenkinsAction: ['jenkins', 'Jenkins', 'integration', 'jenkins', ['jenkinsUrl']],
  TelegramAction: ['telegram', 'Telegram', 'messages', 'telegram', ['token', 'chat', 'message']],
  PagerDutyAction: ['pagerduty', 'PagerDuty', 'messages', 'pagerDuty', ['integrationKey', 'summary', 'incidentKey']],
  JiraAction: ['jira', 'Jira', 'work', 'jira', ['siteUrl', 'token', 'project', 'summary', 'issueKey', 'comment']],
  RedisAction: ['redis', 'Redis', 'connection', 'redis', ['hostname', 'key']],
  PostgresAction: ['postgres', 'Postgres', 'connection', 'postgres', ['hostname', 'database', 'command']],
  EmailAction: ['email', 'Email', 'messages', 'mail', ['hostname', 'from', 'to', 'subject', 'message']],
  S3Action: ['s3', 'S3', 'documents', 's3', ['accessKeyId', 'secretAccessKey', 'bucket', 'key']],
  TeamsAction: ['teams', 'Microsoft Teams', 'messages', 'teams', ['webhookUrl', 'message']],
  MattermostAction: ['mattermost', 'Mattermost', 'messages', 'mattermost', ['siteUrl', 'token', 'channelId', 'message']],
  TwilioAction: ['twilioSms', 'SMS (Twilio)', 'messages', 'twilio', ['accountSid', 'token', 'sender', 'to', 'message']],
  GoogleSheetsAction: ['googleSheets', 'Google Sheets', 'documents', 'googleSheets', ['serviceAccountKey', 'spreadsheet', 'rowValues']],
  ClaudeAction: ['claude', 'Claude', 'ai', 'claude', ['apiKey', 'userInput']],
  ReadEmailAction: ['readEmail', 'Read email', 'messages', 'mail', ['hostname', 'username']],
  MailchimpAction: ['mailchimp', 'Mailchimp', 'sales', 'mailchimp', ['apiKey', 'audienceId', 'email', 'tagName']],
  StripeAction: ['stripePayments', 'Stripe payments', 'sales', 'stripe', ['secretKey', 'email', 'priceId', 'paymentId']],
  WooCommerceAction: ['wooCommerce', 'WooCommerce', 'sales', 'wooCommerce', ['siteUrl', 'consumerKey', 'consumerSecret', 'orderNumber', 'orderStatus', 'note', 'productId', 'stockQuantity']],
  FreshdeskAction: ['freshdeskTickets', 'Freshdesk tickets', 'work', 'tags', ['siteUrl', 'apiKey', 'ticketNumber', 'email', 'subject', 'ticketDescription', 'comment']],
  ClickUpAction: ['clickUp', 'ClickUp', 'work', 'clickUp', ['token', 'listId', 'taskId', 'title', 'comment', 'taskStatus']],
  WhatsAppAction: ['whatsApp', 'WhatsApp', 'messages', 'whatsApp', ['token', 'phoneNumberId', 'to', 'message', 'templateName']],
  GoogleDriveAction: ['googleDrive', 'Google Drive', 'documents', 'googleDrive', ['serviceAccountKey', 'fileName', 'fileId', 'folderId', 'email']],
  GoogleCalendarAction: ['googleCalendar', 'Google Calendar', 'documents', 'googleCalendar', ['serviceAccountKey', 'calendar', 'title', 'startTime', 'endTime', 'eventId']],
  MongoDBAction: ['mongoDB', 'MongoDB', 'connection', 'mongoDb', ['connectionString', 'database', 'collection', 'document', 'changes']],
  KafkaAction: ['kafka', 'Kafka', 'connection', 'kafka', ['servers', 'topic', 'messageBody', 'consumerGroup']],
  RabbitMQAction: ['rabbitMQ', 'RabbitMQ', 'connection', 'rabbitMq', ['hostname', 'routingKey', 'messageBody', 'queueName']],
  MqttAction: ['mqtt', 'MQTT', 'connection', 'mqtt', ['brokerUrl', 'topic', 'messageBody']],
  ElasticsearchAction: ['elasticsearch', 'Elasticsearch', 'connection', 'elasticsearch', ['siteUrl', 'indexName', 'documentId', 'document']],
  PrometheusAction: ['prometheus', 'Prometheus', 'operations', 'prometheus', ['siteUrl', 'query']],
  GrafanaAction: ['grafana', 'Grafana', 'operations', 'grafana', ['siteUrl', 'token', 'annotationText']],
  DatadogAction: ['datadog', 'Datadog', 'operations', 'datadog', ['apiKey', 'title', 'eventText', 'applicationKey', 'monitorId']],
  SentryAction: ['sentry', 'Sentry', 'operations', 'sentry', ['token', 'organization', 'issueId']],
  CloudflareAction: ['cloudflare', 'Cloudflare', 'operations', 'cloudflare', ['token', 'zoneId', 'addresses', 'recordName', 'recordContent', 'recordId']],
  DigitalOceanAction: ['digitalOcean', 'DigitalOcean', 'operations', 'digitalOcean', ['token', 'dropletId']],
  OktaAction: ['okta', 'Okta', 'operations', 'okta', ['siteUrl', 'token', 'userLogin']],
  GiteaAction: ['gitea', 'Gitea', 'work', 'gitea', ['siteUrl', 'token', 'owner', 'repository', 'issueNumber', 'title', 'comment', 'sourceBranch']],
  BitbucketAction: ['bitbucket', 'Bitbucket', 'work', 'bitbucket', ['email', 'token', 'workspace', 'repository', 'pullRequestId', 'title', 'sourceBranch', 'comment']],
  CircleCIAction: ['circleCI', 'CircleCI', 'work', 'circleCi', ['token', 'projectSlug', 'pipelineId', 'workflowId']],
  WordPressAction: ['wordPress', 'WordPress', 'documents', 'wordPress', ['siteUrl', 'username', 'applicationPassword', 'postId', 'title']],
  BoxAction: ['box', 'Box', 'documents', 'box', ['token', 'searchText', 'fileId', 'folderName']],
  OneDriveAction: ['oneDrive', 'OneDrive', 'documents', 'oneDrive', ['tenantId', 'clientId', 'clientSecret', 'person', 'searchText', 'fileId', 'filePath']],
  OutlookAction: ['outlook', 'Outlook', 'messages', 'mail', ['tenantId', 'clientId', 'clientSecret', 'mailbox', 'toAddresses', 'subject', 'messageId']],
  SendGridAction: ['sendGrid', 'SendGrid', 'messages', 'sendGrid', ['apiKey', 'fromAddress', 'toAddresses', 'subject', 'emailBody']],
  PayPalAction: ['payPal', 'PayPal', 'sales', 'payPal', ['clientId', 'clientSecret', 'amount', 'orderId', 'captureId']],
  /* Without these a trigger stops at once, and nothing but the server log says so. */
  IntervalTrigger: ['IntervalTrigger', 'Schedule', 'logic', 'clock', ['time']],
  ScheduleTrigger: ['ScheduleTrigger', 'Schedule', 'logic', 'clock', ['rule']],
  ActiveMQTrigger: ['ActiveMQTrigger', 'Queue', 'logic', 'server', ['url', 'queue']],
};

/* Triggers are shown as one Start tile with a choice of how it starts. */
const TRIGGER_ACTIONS = { webhook: 'Webhook', schedule: 'ScheduleTrigger', interval: 'IntervalTrigger', activemq: 'ActiveMQTrigger' };
const TRIGGER_KINDS = Object.fromEntries(Object.entries(TRIGGER_ACTIONS).map(([kind, backend]) => [backend, kind]));
/* A connector's trigger is one more way to start, chosen by app, since there is one for every service. */
const APP_TRIGGER = 'app';
/* The icon of a connector with no free logo: its name, which the form draws as initials. */
const INITIALS = 'initials:';
const TRIGGER_ICONS = { webhook: 'webhook', schedule: 'clock', interval: 'clock', activemq: 'server', manual: 'play' };
/* A start shows what starts it, such as the logo of its app. */
const iconOf = (node) => (node.type !== 'start' ? ACTIONS[node.type].icon
  : node.config.trigger === APP_TRIGGER ? ACTIONS[TYPE_BY_BACKEND[node.config.app]]?.icon ?? 'workflow' : TRIGGER_ICONS[node.config.trigger] ?? 'play');
const APP_TRIGGER_SUFFIX = '.trigger';
const isAppTrigger = (backend) => backend?.endsWith(APP_TRIGGER_SUFFIX);
/* The connector a step reads its lists from: its own, or on a start tile that of its app's trigger. */
const connectorOf = (node) => (node.type === 'start' ? node.config.app.slice(0, -APP_TRIGGER_SUFFIX.length) : ACTIONS[node.type].backend);
const triggerBackend = (config) => (config.trigger === APP_TRIGGER ? config.app : TRIGGER_ACTIONS[config.trigger]);
const TRIGGER_DEFAULTS = { webhook: { method: 'POST', authentication: 'none' }, schedule: { rule: 'DAYS', every: '1', atHour: '9', atMinute: '0' }, interval: { time: '1', timeUnit: 'MINUTES' }, activemq: {} };
const STEP_ACTION_NAME = 'actionName';
const FAILURE_FIELD_NAMES = ['continueOnFailure', 'retries', 'retryWait', 'failOn'];
/* A deactivated step stays in the task and a run passes over it. The step menu sets it, so no form shows it. */
const STEP_DEACTIVATED = 'deactivated';
const isDeactivated = (node) => !!node.config[STEP_DEACTIVATED];
/* Copied steps travel as text, so they also paste into another task or another window. */
const COPIED_STEPS_KIND = 'dopplertask/steps';
const COPY_OFFSET = { x: 28, y: 96 };
const withoutNumber = (name) => name.replace(/ \d+$/, '');
const AUTO_SAVE_DELAY_MS = 30000;
const AUTO_SAVE_KEY = 'autoSave';
const LAST_TASK_KEY = 'lastTask';
const RUNS_SHOWN = 100;
const LIVE_POLL_MS = 250;
/* Runs that a schedule or a webhook starts show up in the history this soon. */
const RUNS_POLL_MS = 3000;

const ACTIONS = {};
const FAILURE_FIELDS = [];
const TYPE_BY_BACKEND = {};
const layout = { columnGap: 256, rowGap: 172 };

const CREDENTIAL_CATEGORIES = ['CREDENTIAL', 'SECRET'];
const uniqueId = () => Math.random().toString(36).slice(2, 9);
const clone = (value) => JSON.parse(JSON.stringify(value));
/* The settings of a step with each text in them written anew. */
const withTexts = (value, change) => {
  if (typeof value === 'string') return change(value);
  if (Array.isArray(value)) return value.map((item) => withTexts(item, change));
  return value && typeof value === 'object' ? Object.fromEntries(Object.entries(value).map(([key, item]) => [key, withTexts(item, change)])) : value;
};
const textsIn = (value) => { const texts = []; withTexts(value, (text) => texts.push(text)); return texts; };

/* ---------- Catalogue ---------- */

function toField(information) {
  /* A hint that only repeats the label takes room and says nothing. */
  const label = information.displayName || information.name;
  const hint = information.description && information.description.replace(/\.$/, '').toLowerCase() !== label.toLowerCase() ? information.description : undefined;
  const common = { key: information.name, label, hint, defaultValue: information.defaultValue || undefined, credential: CREDENTIAL_CATEGORIES.includes(information.category), required: !!information.required };
  const showWhen = information.displayOptions && Object.keys(information.displayOptions).length ? information.displayOptions : undefined;
  if (showWhen) common.showWhen = showWhen;
  switch (information.type) {
    case 'BOOLEAN': return [{ ...common, type: 'toggle' }];
    case 'NUMBER': return [{ ...common, type: 'number' }];
    case 'MULTILINE': return [{ ...common, type: 'code' }];
    case 'CHOICES': return [{ ...common, type: 'expression', choices: true, dependsOn: information.dependsOn || [] }];
    case 'FIELDS': return [{ ...common, type: 'form' }];
    case 'FILE': return [{ ...common, type: 'expression', files: true }];
    case 'ADDRESS': return [{ ...common, type: 'address' }];
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
      return [{ ...common, type: information.category === 'SECRET' ? 'password' : 'expression' }];
  }
}

const fieldsOf = (information) => information.flatMap(toField);

/* What a step signs in with lives in a credential, so the step has one field that names it, where its first one was.
   It shows when any of them would, such as for a webhook with one of its ways of signing in. */
function withCredentialField(fields, credentialType) {
  const inCredential = fields.filter((field) => field.credential);
  if (!inCredential.length) return fields;
  const conditions = inCredential.map((field) => field.showWhen);
  const showWhen = conditions.every(Boolean) ? conditions.reduce((all, condition) => {
    Object.entries(condition).forEach(([key, choices]) => { all[key] = [...(all[key] || []), ...choices]; });
    return all;
  }, {}) : undefined;
  const field = { key: 'credential', label: 'Credential', type: 'credential', credentialType, fields: inCredential, required: inCredential.some((one) => one.required) || !!showWhen, ...(showWhen ? { showWhen } : {}) };
  const first = fields.indexOf(inCredential[0]);
  return [...fields.slice(0, first), field, ...fields.slice(first).filter((one) => !one.credential)];
}

function loadCatalogue(actions) {
  const triggers = {};
  actions.forEach((action) => {
    const backend = action.name;
    /* A connector names its own label and icon, since it is described in a file and not here. */
    const [type, label, category, iconName, required] = PRESENTATION[backend] || [backend, action.label || backend.replace(/Action$/, ''), isAppTrigger(backend) ? 'starts' : 'integration', action.icon || `${INITIALS}${isAppTrigger(backend) ? backend.slice(0, -APP_TRIGGER_SUFFIX.length) : backend}`];
    const fields = fieldsOf(action.propertyInformationList.filter((information) => ![STEP_ACTION_NAME, STEP_DEACTIVATED].includes(information.name)));
    if (!FAILURE_FIELDS.length) FAILURE_FIELDS.push(...fields.filter((field) => FAILURE_FIELD_NAMES.includes(field.key)));
    const own = withCredentialField(fields.filter((field) => !FAILURE_FIELD_NAMES.includes(field.key)).map((field) => ({ ...field, required: field.required || !!required?.includes(field.key) })), action.credentialType);
    /* The language control stays last, so what the step does comes first. */
    own.sort((first, second) => (first.key === 'scriptLanguage') - (second.key === 'scriptLanguage'));
    if (action.trigger) triggers[backend] = own.filter((field) => !['scriptLanguage', 'triggerSuffix'].includes(field.key));
    TYPE_BY_BACKEND[backend] = type;
    ACTIONS[type] = { backend, label, category, icon: iconName, description: action.description, outputs: action.outputs || {}, fields: own, hidden: type === 'start' || (!!action.trigger && !isAppTrigger(backend)), trigger: !!action.trigger };
  });

  /* An install can leave step kinds out, so each of these may be missing. */
  const branchPorts = {
    switch: (node) => [...(node.config.switchCases || []).map((switchCase, index) => ({ id: `case:${index}`, label: switchCase.currentCase || `Case ${index + 1}` })), { id: 'default', label: 'Default' }],
    if: () => [{ id: 'true', label: 'Yes' }, { id: 'false', label: 'No' }],
    loop: () => [{ id: 'each', label: 'Each item' }, { id: 'done', label: 'Done' }],
  };
  Object.entries(branchPorts).forEach(([type, ports]) => { if (ACTIONS[type]) ACTIONS[type].ports = ports; });
  const appTriggers = actions.filter((action) => action.trigger && isAppTrigger(action.name)).map(({ name, label }) => ({ name, label: label || name }));
  ACTIONS.start.fields = [
    { key: 'trigger', label: 'Starts', type: 'select', options: [
      { value: 'manual', label: 'Only when I run it' },
      { value: 'webhook', label: 'When a webhook is called' },
      { value: 'schedule', label: 'On a schedule' },
      { value: 'interval', label: 'After a fixed pause (older)' },
      { value: 'activemq', label: 'When a queue message arrives' },
    // A server can leave out kinds of trigger that it does not offer.
      { value: APP_TRIGGER, label: 'When something new appears in an app' },
    // A server can leave out kinds of trigger that it does not offer.
    ].filter((option) => (option.value === APP_TRIGGER ? appTriggers.length : !TRIGGER_ACTIONS[option.value] || triggers[TRIGGER_ACTIONS[option.value]])) },
    { key: 'app', label: 'App', type: 'select', required: true, options: appTriggers.map(({ name, label }) => ({ value: name, label })), showWhen: { trigger: [APP_TRIGGER] } },
    ...Object.entries(TRIGGER_ACTIONS).flatMap(([kind, backend]) => (triggers[backend] || []).map((field) => ({ ...field, showWhen: { ...field.showWhen, trigger: [kind] } }))),
    ...appTriggers.flatMap(({ name }) => triggers[name].map((field) => ({ ...field, showWhen: { ...field.showWhen, trigger: [APP_TRIGGER], app: [name] } }))),
  ];
}

/* Without a search the steps are grouped by category. With one they form a single list, names before descriptions. */
function stepsMatching(query) {
  const steps = Object.entries(ACTIONS).filter(([, action]) => !action.hidden).map(([type, action]) => ({ type, action }));
  const needle = query.trim().toLowerCase();
  if (!needle) {
    return CATEGORIES.map((category) => ({ category, items: steps.filter(({ action }) => action.category === category.id) })).filter((group) => group.items.length);
  }
  const rankOf = ({ type, action }) => {
    if (action.label.toLowerCase().startsWith(needle)) return 0;
    if (action.label.toLowerCase().includes(needle)) return 1;
    if (`${action.backend} ${type}`.toLowerCase().includes(needle)) return 2;
    return String(action.description).toLowerCase().includes(needle) ? 3 : null;
  };
  const items = steps.filter((step) => rankOf(step) !== null).sort((first, second) => rankOf(first) - rankOf(second));
  return items.length ? [{ category: { id: 'matches', label: 'Matching steps' }, items }] : [];
}

/* ---------- Graph helpers ---------- */

const portsOf = (node) => (ACTIONS[node.type].ports ? ACTIONS[node.type].ports(node) : [{ id: 'out', label: '' }]);
const edgeFrom = (task, nodeId, portId) => task.edges.find((edge) => edge.from === nodeId && edge.port === portId);
const nodeById = (task, nodeId) => task.nodes.find((node) => node.id === nodeId);
/* A copy of a task gets triggers of its own: an address a service calls belongs to one task only. */
function newTriggerAddresses(task) {
  task.nodes.filter((node) => node.type === 'start' && node.config.triggerSuffix).forEach((node) => { node.config.triggerSuffix = crypto.randomUUID(); });
  return task;
}

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
  task.nodes.filter(isEntry).forEach((node) => { if (!seen.has(node.id)) place(node.id, 0); });
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

/* A condition or a value in words. One the editor wrote reads as it was built, and any other keeps its own signs. */
function readableExpression(text) {
  return conditionWords(text) || inWords(text)
    .replace(/\s*==\s*["']?([^"']*)["']?$/, ' is $1')
    .replace(/\s*!=\s*["']?([^"']*)["']?$/, ' is not $1');
}

/* Keeps literal text readable while showing where a value lands: /etc/app/{env}.yml */
const inlineVariables = (text) => inWords(text, (words) => `{${words}}`);

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
        schedule: ['Starts ', { value: scheduleText(config) }],
        interval: ['Starts every ', { value: intervalText(config) }],
        activemq: ['Starts on messages from ', value(config.queue, 'no queue')],
        app: ['Starts on new ', value(ACTIONS[config.app]?.label, 'items in an app')],
      })[config.trigger] || ['Starts'];
    case 'if': return ['If ', value(readableExpression(config.condition), 'a condition')];
    case 'switch': return ['Check ', value(readableExpression(config.value), 'a value')];
    case 'loop': return ['For each item in ', value(config.path || readableExpression(config.items), 'a list')];
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
    case 'telegram': return ['Send ', value(inlineVariables(config.message), 'a message'), ' to ', value(config.chat, 'a chat')];
    case 'pagerduty': return config.operation === 'resolve' ? ['Resolve ', value(inlineVariables(config.incidentKey), 'an incident')] : config.operation === 'acknowledge' ? ['Acknowledge ', value(inlineVariables(config.incidentKey), 'an incident')] : ['Open an incident: ', value(inlineVariables(config.summary), 'no summary yet')];
    case 'jira': return config.operation === 'addComment' ? ['Comment on ', value(inlineVariables(config.issueKey), 'an issue')] : ['Create ', value(inlineVariables(config.summary), 'an issue'), ' in ', value(config.project, 'a project')];
    case 'redis': return config.operation === 'set' ? ['Write ', value(config.key, 'a key')] : config.operation === 'increment' ? ['Increment ', value(config.key, 'a key')] : config.operation === 'delete' ? ['Delete ', value(config.key, 'a key')] : ['Read ', value(config.key, 'a key')];
    case 'postgres': return ['On ', value(config.database && `${config.database}@${config.hostname}`, 'a database'), ' run ', value(inlineVariables(config.command), 'a query')];
    case 'email': return ['Send ', value(inlineVariables(config.subject), 'an email'), ' to ', value(inlineVariables(config.to), 'someone')];
    case 's3': return [config.operation === 'write' ? 'Write ' : config.operation === 'delete' ? 'Delete ' : 'Read ', value(inlineVariables(config.key), 'a file'), ' in ', value(config.bucket, 'a bucket')];
    case 'teams': return ['Send ', value(inlineVariables(config.message), 'a message'), ' to Teams'];
    case 'mattermost': return ['Send ', value(inlineVariables(config.message), 'a message'), ' to Mattermost'];
    case 'twilioSms': return ['Send SMS to ', value(inlineVariables(config.to), 'a number')];
    case 'googleSheets': return config.operation === 'addRow' ? ['Add a row to ', value(config.sheet, 'a sheet')] : ['Read the rows of ', value(config.sheet, 'a sheet')];
    case 'claude': return ['Ask Claude: ', value(inlineVariables(config.userInput), 'no prompt yet')];
    case 'readEmail': return ['Read ', value(config.folder, 'INBOX'), config.onlyUnread === false ? '' : ' (unread)'];
    case 'openAI': return ['Ask ', { value: config.model || 'OpenAI' }, ' ', value(readableExpression(config.userInput), 'a prompt')];
    case 'jenkins': return [{ value: config.operation || 'Call' }, ' on Jenkins'];
    default: {
      const { fields, description, label } = ACTIONS[node.type];
      const chosen = fields.find((field) => field.key === 'operation')?.options.find((option) => option.value === config.operation);
      return [chosen?.label || description || label];
    }
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
    if (node.type === 'start' && node !== rootNode(task) && !triggerBackend(node.config)) issues.push({ nodeId: node.id, field: 'trigger', message: 'Choose what starts the task here. Running by hand starts at the first start' });
  });
  return issues;
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
  const defaults = Object.fromEntries(ACTIONS[type].fields.filter((field) => field.type === 'select').map((field) => [field.key, field.defaultValue ?? field.options[0]?.value]));
  return { id, type, name: name || ACTIONS[type].label, config: { ...(type === 'start' ? { trigger: 'manual' } : defaults), ...(type === 'script' ? { scriptLanguage: 'JAVASCRIPT' } : {}), ...(type === 'switch' ? { switchCases: [{ currentCase: 'first' }, { currentCase: 'second' }] } : {}), ...config }, position: { x: 0, y: 0 } };
}

function triggerConfigKeys(backend) {
  return (ACTIONS[TYPE_BY_BACKEND[backend]]?.fields || []).map((field) => field.key);
}

/* Each start tile is its trigger in the saved task. The first tile is also the Start action, so the task
   can always be run by hand, and both lead to the same step. */
function toBackend(task) {
  const actions = [];
  const connections = [];
  const root = rootNode(task);
  task.nodes.forEach((node) => {
    const position = { guiXPos: Math.round(node.position.x), guiYPos: Math.round(node.position.y) };
    const inputs = isEntry(node) ? [] : [{ externalId: portId(node.id, INPUT_PORT), portType: 'INPUT' }];
    const outputs = portsOf(node).map((port) => ({ externalId: portId(node.id, port.id), portType: 'OUTPUT' }));
    if (node.type === 'start') {
      if (node === root) actions.push({ '@type': ACTIONS.start.backend, ports: outputs, ...position });
      const backend = triggerBackend(node.config);
      if (backend) {
        const settings = Object.fromEntries([...triggerConfigKeys(backend), 'triggerSuffix'].filter((key) => key in node.config).map((key) => [key, node.config[key]]));
        actions.push({ '@type': backend, ...withoutEmptyValues(settings), ports: [{ externalId: portId(node.id, TRIGGER_PORT), portType: 'OUTPUT' }], ...position });
      }
    } else {
      actions.push({ '@type': ACTIONS[node.type].backend, [STEP_ACTION_NAME]: node.name, ...withoutEmptyValues(node.config), ports: [...inputs, ...outputs], ...position });
    }
  });
  task.edges.forEach((edge) => {
    const from = nodeById(task, edge.from);
    const target = { externalId: portId(edge.to, INPUT_PORT) };
    if (from.type !== 'start' || from === root) connections.push({ source: { externalId: portId(edge.from, edge.port) }, target });
    if (from.type === 'start' && triggerBackend(from.config)) connections.push({ source: { externalId: portId(edge.from, TRIGGER_PORT) }, target });
  });
  return { name: task.name, active: !!task.active, description: task.description || '', parameters: task.parameters, actions, connections };
}

const outputPortsOf = (action) => (action.ports || []).filter((port) => port.portType === 'OUTPUT');

/* Reads any saved or imported task, including ones made before this UI, whose port ids carry no meaning. */
function fromBackend(saved) {
  const portOwners = {};
  if (!Array.isArray(saved.actions)) throw new Error('That file is not a task, since it has no steps.');
  const savedIdOf = (action) => action?.ports?.[0]?.externalId.match(/^([^:]+):/)?.[1];
  const startId = savedIdOf(saved.actions.find((action) => action['@type'] === ACTIONS.start.backend));
  const nodes = saved.actions.map((action) => {
    const type = TYPE_BY_BACKEND[action['@type']];
    if (!type) throw new Error(`This task uses a step this version does not know: ${action['@type']}.`);
    const config = Object.fromEntries(Object.entries(action).filter(([key]) => !LAYOUT_FIELDS.includes(key)));
    /* Keeping the saved id keeps the checksum, so saving an unchanged task adds no version. The trigger of the
       Start tile carries the id of that tile. */
    const savedId = savedIdOf(action);
    const ownId = savedId && !(ACTIONS[type].trigger && savedId === startId) ? savedId : undefined;
    const node = createNode(ownId || `n${uniqueId()}`, type, action[STEP_ACTION_NAME] && action[STEP_ACTION_NAME] !== action['@type'] ? action[STEP_ACTION_NAME] : undefined, config);
    node.position = { x: action.guiXPos || 0, y: action.guiYPos || 0 };
    node.savedPort = action.ports?.[0]?.externalId;
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

  /* Every trigger is a start tile. The first that leads where Start leads joins the Start tile, and the others
     keep a tile of their own. */
  const start = rootNode(task);
  let startEdge = task.edges.find((edge) => edge.from === start.id);
  nodes.filter((node) => ACTIONS[node.type].trigger).forEach((trigger) => {
    const backend = trigger.backendAction['@type'];
    const settings = { ...trigger.config, ...(isAppTrigger(backend) ? { trigger: APP_TRIGGER, app: backend } : { trigger: TRIGGER_KINDS[backend] }) };
    const triggerEdge = task.edges.find((edge) => edge.from === trigger.id);
    task.edges = task.edges.filter((edge) => edge.from !== trigger.id);
    if (start.config.trigger === 'manual' && (!startEdge || !triggerEdge || startEdge.to === triggerEdge.to)) {
      Object.assign(start.config, settings);
      outputPortsOf(trigger.backendAction).forEach((port) => { portOwners[port.externalId] = start.id; });
      task.nodes = task.nodes.filter((node) => node !== trigger);
      if (!startEdge && triggerEdge) { startEdge = { from: start.id, port: 'out', to: triggerEdge.to }; task.edges.push(startEdge); }
    } else {
      Object.assign(trigger, { type: 'start', config: settings });
      if (triggerEdge) task.edges.push({ from: trigger.id, port: 'out', to: triggerEdge.to });
    }
  });
  task.nodes = [start, ...task.nodes.filter((node) => node !== start)];
  task.nodes.forEach((node) => { delete node.backendAction; });

  /* Tasks without usable positions are laid out, since their steps would otherwise sit on top of each other. */
  const positions = new Set(task.nodes.map((node) => `${node.position.x},${node.position.y}`));
  if (positions.size < task.nodes.length) layoutTask(task);
  return task;
}

/* ---------- Review ---------- */

/* What a proposal changes compared to the current version. A step is the same step if it keeps its first port. */
function compareVersions(current, proposed) {
  const keyOf = (node) => node.savedPort || node.id;
  const earlier = new Map(current.nodes.map((node) => [keyOf(node), node]));
  const nextOf = (task, node) => task.edges.filter((edge) => edge.from === node.id).map((edge) => `${edge.port}>${keyOf(nodeById(task, edge.to))}`).sort().join();
  const added = new Set();
  const changed = new Map();
  proposed.nodes.forEach((node) => {
    const before = earlier.get(keyOf(node));
    if (!before) { added.add(node.id); return; }
    const fields = [...new Set([...Object.keys(node.config), ...Object.keys(before.config)])]
      .filter((key) => JSON.stringify(node.config[key] ?? null) !== JSON.stringify(before.config[key] ?? null))
      .map((key) => ({ key, before: before.config[key] }));
    if (node.name !== before.name) fields.push({ key: 'name', before: before.name });
    if (nextOf(proposed, node) !== nextOf(current, before)) fields.push({ key: 'next step', before: null });
    if (fields.length) changed.set(node.id, fields);
  });
  const kept = new Set(proposed.nodes.map(keyOf));
  const removed = current.nodes.filter((node) => !kept.has(keyOf(node))).map((node) => node.name);
  return { added, changed, removed };
}

/* ---------- Server ---------- */

const ACCESS_TOKEN_KEY = 'accessToken';

/* What a connector reads from its service for a field, on the server with the step's credential. */
function lookUp(node, input, what) {
  const settings = Object.fromEntries(Object.entries(node.config).filter(([, value]) => typeof value === 'string'));
  return request(`/connectors/${encodeURIComponent(connectorOf(node))}/${what}`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ operation: node.config.operation, input, credential: node.config.credential, settings }) });
}

async function request(url, options = {}) {
  const accessToken = sessionStorage.getItem(ACCESS_TOKEN_KEY);
  const response = await fetch(url, { ...options, headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...(accessToken ? { Authorization: `Bearer ${accessToken}` } : {}) } })
    .catch(() => { throw new Error('The server could not be reached. Nothing was changed there.'); });
  const text = await response.text();
  let body = null;
  try { body = text ? JSON.parse(text) : null; } catch (error) { body = null; }
  if (response.status === 401) sessionStorage.removeItem(ACCESS_TOKEN_KEY);
  if (!response.ok) throw Object.assign(new Error(body?.message || body?.error || `The server answered ${response.status}.`), { status: response.status });
  return body;
}

/* ---------- Runs ---------- */

const RUN_STATUSES = { FINISHED: 'success', FAILED: 'failed', WAITING: 'waiting', ABORTED: 'stopped' };
const SWITCH_PORT = /port nr: (\d+)/;

function choosePort(node, output) {
  if (node.type === 'if') return output === 'true' ? 'true' : 'false';
  if (node.type === 'loop') return (output || '').startsWith('Done') ? 'done' : 'each';
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
    /* In a loop a step runs several times, and the latest run reached so far is the one to show. */
    stepFor(nodeId) {
      if (!runner.run) return null;
      const steps = runner.run.steps.filter((step) => step.nodeId === nodeId);
      return steps.findLast((step) => step.startMs <= runner.cursorMs) || steps[0];
    },
    stateOf(nodeId) {
      if (!runner.run) return 'idle';
      const step = runner.stepFor(nodeId);
      if (step) return stepStateAt(step, runner.cursorMs);
      if (runner.run.live && runner.run.nextNodeId === nodeId) return 'running';
      /* A run with no recorded steps says nothing about which steps it reached. */
      if (runner.finished) return runner.run.steps.length ? 'skipped' : 'idle';
      return 'pending';
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

/* my_task, my_task_2: a name that reads well, and that a Save will not find taken. */
function freshTask(takenNames = []) {
  let name = 'my_task';
  for (let number = 2; takenNames.includes(name); number += 1) name = `my_task_${number}`;
  return { id: name, name, description: '', parameters: [], active: false, nodes: [createNode('start', 'start', 'Start')], edges: [], portOwners: {} };
}

function createStore() {
  const listeners = new Set();
  let tasks = [];
  let credentials = [];
  let task = freshTask();
  let selectedIds = [];
  const onlySelectedId = () => (selectedIds.length === 1 ? selectedIds[0] : null);
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
  let showingOlderVersion = false;
  let review = null;
  let person = null;

  const emit = (change) => listeners.forEach((listener) => listener(change));

  const hasUnsavedWork = () => saveState === 'unsaved' && task.nodes.length > 1 && !showingOlderVersion;
  window.addEventListener('beforeunload', (event) => {
    if (hasUnsavedWork()) event.preventDefault();
  });
  const mayLeaveTask = () => !hasUnsavedWork() || window.confirm(`${task.name} has unsaved changes. Leave it and lose them?`);

  const setSaveState = (state) => { saveState = state; emit('save'); };

  /* A new version takes effect immediately, so auto save pauses while triggers are live. */
  const scheduleAutoSave = () => {
    clearTimeout(autoSaveTimer);
    if (autoSave && !task.active && saveState === 'unsaved' && task.checksum) autoSaveTimer = setTimeout(() => store.save({ autoSaved: true }).catch(() => {}), AUTO_SAVE_DELAY_MS);
  };
  const markChanged = () => { showingOlderVersion = false; setSaveState('unsaved'); scheduleAutoSave(); };

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
    selectedIds = selectedIds.filter((nodeId) => nodeById(task, nodeId));
  };
  const changed = (change) => { markChanged(); emit(change); };

  const runner = createRunner((kind) => emit(kind === 'tick' ? 'tick' : 'run'));

  /* ---------- Example values: what each step gave the last time it ran or was tested ---------- */

  /* What a step gave when it was tested on its own, by step. A run that starts replaces them. */
  let tested = new Map();
  /* The step that runs on its own now. One step runs at a time. */
  let testingStepId = null;

  const earlierNodes = (nodeId) => {
    const earlier = new Set();
    const visit = (id) => task.edges.filter((edge) => edge.to === id).forEach((edge) => { if (!earlier.has(edge.from)) { earlier.add(edge.from); visit(edge.from); } });
    visit(nodeId);
    return [...earlier].map((id) => nodeById(task, id));
  };

  const latestRecord = (nodeId, fits = () => true) => {
    for (const run of [runner.run, ...runs].filter(Boolean)) {
      const step = run.steps.findLast((candidate) => candidate.nodeId === nodeId && candidate.status !== 'failed' && fits(candidate));
      if (step) return step;
    }
    return null;
  };

  const sampleOf = (nodeId) => tested.get(nodeId) || latestRecord(nodeId);

  /* The loop whose pass this step is in. Inside two loops the inner one counts, as it does in a run. */
  const loopAround = (nodeId) => task.nodes.filter((node) => node.type === 'loop' && node.id !== nodeId)
    .map((loop) => ({ loop, pass: reachableIds(task, task.edges.filter((edge) => edge.from === loop.id && edge.port === 'each').map((edge) => edge.to), new Set([loop.id])) }))
    .filter(({ pass }) => pass.has(nodeId))
    .sort((first, second) => first.pass.size - second.pass.size)[0]?.loop || null;

  /* The latest pass of the loop around this step: its item, where the item is in the list, and how many the list has. */
  const loopPassSample = (nodeId) => {
    const loop = loopAround(nodeId);
    return (loop && latestRecord(loop.id, (step) => step.outputVariables.item !== undefined)?.outputVariables) || null;
  };

  const loopItemSample = (nodeId) => loopPassSample(nodeId)?.item ?? null;

  /* What the server needs to read values of earlier steps and of the current item, when a step runs on its own. */
  const examplesBefore = (nodeId) => ({
    steps: Object.fromEntries(earlierNodes(nodeId).map((node) => [node.name, sampleOf(node.id)]).filter(([, sample]) => sample)
      .map(([name, sample]) => [name, { output: sample.output, outputVariables: sample.outputVariables }])),
    loopItem: loopPassSample(nodeId)?.item,
    loopIndex: loopPassSample(nodeId)?.index,
    loopCount: loopPassSample(nodeId)?.count,
  });

  /* ---------- Preview choices: kept in this browser, by task and step ---------- */

  const previewKey = () => `preview:${task.name}`;
  const allPreviewChoices = () => JSON.parse(localStorage.getItem(previewKey()) || '{}');

  /* The server knows a step by its first port, which is the saved one until the task is saved from this editor. */
  const previewRequest = () => {
    const choices = { sentForReal: [], madeToFail: [], exampleAnswers: {} };
    Object.entries(allPreviewChoices()).forEach(([nodeId, choice]) => {
      const node = nodeById(task, nodeId);
      if (!node) return;
      [...new Set([node.savedPort, portId(node.id, INPUT_PORT)])].filter(Boolean).forEach((port) => {
        if (choice.mode === 'sent') choices.sentForReal.push(port);
        if (choice.mode === 'fails') choices.madeToFail.push(port);
        if (choice.exampleAnswer) choices.exampleAnswers[port] = choice.exampleAnswer;
      });
    });
    return choices;
  };

  const testOnServer = (action, values, nodeId, preview) => request('/task/action/test', { method: 'POST', body: JSON.stringify({ action, parameters: values.parameters, lastOutput: values.lastOutput, lastOutputVariables: values.lastOutputVariables, ...examplesBefore(nodeId), preview }) });

  const loadCredentials = async () => {
    credentials = await request('/credentials');
    emit('tasks');
  };

  const loadTaskList = async () => {
    const grouped = await request('/task/grouped');
    tasks = Object.entries(grouped).map(([name, versions]) => ({ id: name, name, versions }));
    emit('tasks');
  };

  const runListItem = (execution) => ({
    id: execution.executionId, status: RUN_STATUSES[execution.status] || 'running', taskName: execution.taskName,
    startedByAgent: execution.startedByAgent, startedAt: new Date(execution.startDate).getTime(), durationMs: execution.endDate ? new Date(execution.endDate) - new Date(execution.startDate) : 0,
    parameters: {}, steps: [], complete: false, preview: !!execution.preview,
  });

  /* A schedule can leave many thousands of runs, and only the latest of this task are shown. */
  const latestRuns = async () => (await request(`/executions?taskName=${encodeURIComponent(task.name)}&limit=${RUNS_SHOWN}`)).executions
    .filter((execution) => execution.startDate).map(runListItem);

  const loadRuns = async () => {
    runs = await latestRuns();
    /* The latest run supplies the values shown in the step panel, also after a reload. */
    if (runs[0]) applyExecution(runs[0], await request(`/execution/${runs[0].id}`));
    emit('run');
  };

  /* Adds the runs that started since, and notes the ones that ended, without touching a run that is followed. */
  const refreshRuns = async () => {
    /* A task that was never saved, or is renamed and not yet saved, has no runs under its name. */
    if (document.hidden || review || task.name !== savedName) return;
    const name = task.name;
    const latest = await latestRuns();
    if (name !== task.name) return;
    const known = new Map(runs.map((run) => [run.id, run]));
    let changed = false;
    latest.forEach((item) => {
      const run = known.get(item.id);
      if (!run) { changed = true; return; }
      if (!run.following && run.status !== item.status) { Object.assign(run, item); changed = true; }
    });
    const added = latest.filter((item) => !known.has(item.id));
    if (added.length) runs = [...added, ...runs].slice(0, RUNS_SHOWN);
    if (changed) emit('run');
  };
  const keepRunsCurrent = async () => {
    try { await refreshRuns(); } catch (error) { /* The next look tries again. */ }
    setTimeout(keepRunsCurrent, RUNS_POLL_MS);
  };
  setTimeout(keepRunsCurrent, RUNS_POLL_MS);

  /* A step names its action by the first port; on the Start tile that can be the trigger's port. */
  const nodeForPort = (externalId, actionType) => {
    const owner = task.portOwners?.[externalId] || externalId?.split(':')[0];
    if (owner && nodeById(task, owner)) return nodeById(task, owner);
    const type = TYPE_BY_BACKEND[actionType];
    if (TRIGGER_KINDS[actionType] || isAppTrigger(actionType)) return rootNode(task);
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
      outputVariables: raw.outputVariables || {}, held: raw.held, missingValues: raw.missingValues || [],
      variables: { parameters, lastOutput: previous?.output || '', lastOutputVariables: previous?.outputVariables || {}, executionId },
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
    run.preview = !!execution.preview;
    run.parameters = execution.parameters || run.parameters;
    run.steps = stepsOf(execution.steps || [], run.parameters, run.id);
    run.logLines = execution.output || [];
    run.durationMs = run.steps.reduce((total, step) => total + step.durationMs, 0);
    run.complete = true;
    /* The step that was going when a person stopped the run did not fail. */
    const interrupted = run.status === 'stopped' && run.steps.at(-1)?.status === 'failed' ? run.steps.at(-1) : null;
    if (interrupted) interrupted.status = 'stopped';
    /* A failure with no step, such as a missing parameter, is still shown as a failed run. */
    if (run.status === 'failed' && !run.steps.some((step) => step.status === 'failed')) {
      run.failure = run.logLines.find((line) => /failed|not active|could not/i.test(line)) || 'This run failed before reaching a step.';
    }
    return run;
  };

  const nextNodeAfter = (step) => {
    let edge = step.port ? edgeFrom(task, step.nodeId, step.port) : null;
    const passedOver = new Set();
    while (edge && isDeactivated(nodeById(task, edge.to)) && !passedOver.has(edge.to)) {
      passedOver.add(edge.to);
      edge = edgeFrom(task, edge.to, portsOf(nodeById(task, edge.to))[0].id);
    }
    return edge ? edge.to : null;
  };

  /* The chosen steps and the connections between them, without start tiles: a task keeps the starts it has. */
  const stepsWithConnections = (nodeIds) => {
    const steps = task.nodes.filter((node) => nodeIds.includes(node.id) && node.type !== 'start');
    const stepIds = new Set(steps.map((node) => node.id));
    return clone({
      nodes: steps.map(({ id, type, name, config, position }) => ({ id, type, name, config, position })),
      edges: task.edges.filter((edge) => stepIds.has(edge.from) && stepIds.has(edge.to)),
    });
  };

  /* The chosen steps in the order they run, when they are one line that other steps enter at its first step
     only. Each has one way out, so the line leaves from its last step only. Null for any other choice. */
  const lineOf = (nodeIds) => {
    const steps = task.nodes.filter((node) => nodeIds.includes(node.id));
    if (!steps.length || steps.some((node) => isEntry(node) || portsOf(node).length !== 1)) return null;
    const chosen = new Set(steps.map((node) => node.id));
    const entered = task.edges.filter((edge) => chosen.has(edge.to));
    const firstSteps = steps.filter((node) => !entered.some((edge) => edge.to === node.id && chosen.has(edge.from)));
    if (firstSteps.length !== 1 || entered.some((edge) => !chosen.has(edge.from) && edge.to !== firstSteps[0].id)) return null;
    const line = [firstSteps[0]];
    for (let next = edgeFrom(task, line[0].id, portsOf(line[0])[0].id); next && chosen.has(next.to); next = edgeFrom(task, next.to, portsOf(nodeById(task, next.to))[0].id)) line.push(nodeById(task, next.to));
    return line.length === steps.length ? line : null;
  };

  const refuseTakenName = (name) => {
    if (tasks.some((candidate) => candidate.name === name)) throw new Error(`A task called ${name} already exists. Choose another name, or open that task to change it.`);
  };

  /* While a run is going the server reports the steps finished so far. Its final record decides the result. */
  const followRun = (run) => {
    if (run.following) return;
    run.following = true;
    run.live = true;
    const poll = async () => {
      try {
        const execution = await request(`/execution/${run.id}`);
        if (RUN_STATUSES[execution.status]) {
          applyExecution(run, execution);
          run.live = false;
          run.following = false;
          if (runner.run === run) { runner.cursorMs = run.durationMs; runner.playing = false; }
          emit('run');
          return;
        }
        const steps = stepsOf(execution.steps || [], run.parameters, run.id);
        if (steps.length > run.steps.length) {
          run.steps = steps;
          run.durationMs = steps.reduce((total, step) => total + step.durationMs, 0);
          run.nextNodeId = nextNodeAfter(steps.at(-1));
          if (runner.run === run) runner.cursorMs = run.durationMs;
          emit('run');
        }
      } catch (error) {
        /* A run the server no longer has is not followed for ever. Anything else, such as a lost connection, is tried again. */
        if (error.status === 400 || error.status === 404) {
          Object.assign(run, { live: false, following: false, status: 'failed', failure: 'This run is no longer on the server.' });
          if (runner.run === run) { runner.cursorMs = run.durationMs; runner.playing = false; }
          emit('run');
          return;
        }
      }
      setTimeout(poll, LIVE_POLL_MS);
    };
    poll();
  };

  const store = {
    runner,
    get task() { return task; },
    get selectedIds() { return selectedIds; },
    /* The one selected step. The step panel and the single-step actions work on it. */
    get selectedId() { return onlySelectedId(); },
    get testingStepId() { return testingStepId; },
    get selectedNode() { return onlySelectedId() ? nodeById(task, onlySelectedId()) : null; },
    get saveState() { return saveState; },
    get showingOlderVersion() { return showingOlderVersion; },
    get review() { return review; },
    get canUndo() { return undoStack.length > 0; },
    get canRedo() { return redoStack.length > 0; },
    get tasks() { return tasks; },
    get credentials() { return credentials; },
    loadCredentials,
    async saveCredential(name, type, values, editing) {
      await request(editing ? `/credentials/${encodeURIComponent(name)}` : '/credentials', { method: editing ? 'PUT' : 'POST', body: JSON.stringify(editing ? values : { name, type, values }) });
      await loadCredentials();
    },
    /* The address of the service's sign-in page, where a person connects the credential's account. */
    /* The choices of a connector's input, read on the server with the step's credential. */
    async connectorChoices(node, input) { return lookUp(node, input, 'choices'); },
    /* The fields of a form that follows the service, such as the columns of a table. */
    async connectorFields(node, input) { return lookUp(node, input, 'fields'); },
    async connectCredential(name) {
      return (await request(`/credentials/${encodeURIComponent(name)}/connect`, { method: 'POST' })).url;
    },
    async deleteCredential(name) {
      await request(`/credentials/${encodeURIComponent(name)}`, { method: 'DELETE' });
      await loadCredentials();
    },
    get runs() { return runs; },
    get issues() { return findIssues(task); },
    get defaults() { return Object.fromEntries(task.parameters.map((parameter) => [parameter.name, parameter.defaultValue || ''])); },
    get autoSave() { return autoSave; },
    get person() { return person; },
    async loadPerson() { person = (await request('/sso/status')).person || null; },
    async signOut() {
      await request('/sso/logout', { method: 'POST' });
      window.location.assign('/public/index.html');
    },
    subscribe(listener) { listeners.add(listener); return () => listeners.delete(listener); },
    loadTaskList,

    /* The files the run keeps before this step: what each earlier Read file keeps, by the name later steps choose it by. */
    filesBefore(nodeId) {
      return earlierNodes(nodeId).filter((node) => node.type === 'readFile' && node.config.filename)
        .map((node) => node.config.keepAs || node.config.filename.split(/[\\/]/).pop());
    },
    /* What this step would see: the values just before it in the run on screen, or else in the latest run that reached it. */
    valuesBefore(nodeId) {
      const shown = runner.run || runs[0];
      for (const run of [shown, ...runs].filter(Boolean)) {
        const step = run.steps.findLast((candidate) => candidate.nodeId === nodeId);
        if (step) return step.variables;
      }
      return { parameters: { ...this.defaults, ...shown?.parameters }, lastOutput: '', lastOutputVariables: {}, executionId: shown?.id || '' };
    },
    /* The steps a run reaches before this one, each with what it gave the last time, for the values a person can click. */
    stepsBefore(nodeId) { return earlierNodes(nodeId).filter((node) => node.type !== 'start').map((node) => ({ node, sample: sampleOf(node.id) })); },
    loopAround,
    loopItemSample,
    previewChoices(nodeId) { return allPreviewChoices()[nodeId] || {}; },
    setPreviewChoice(nodeId, key, value) {
      const all = allPreviewChoices();
      all[nodeId] = { ...all[nodeId], [key]: value };
      localStorage.setItem(previewKey(), JSON.stringify(all));
      emit('preview');
    },
    /* What the steps just before this one give, by name, for $lastLog.outputVariables. */
    outputsBefore(nodeId) { return Object.assign({}, ...task.edges.filter((edge) => edge.to === nodeId).map((edge) => ACTIONS[nodeById(task, edge.from).type]?.outputs)); },
    select(nodeId) { this.selectMany(nodeId ? [nodeId] : []); },
    selectMany(nodeIds) {
      if (nodeIds.length === selectedIds.length && nodeIds.every((nodeId) => selectedIds.includes(nodeId))) return;
      selectedIds = nodeIds;
      emit('selection');
    },
    toggleSelected(nodeId) { this.selectMany(selectedIds.includes(nodeId) ? selectedIds.filter((selected) => selected !== nodeId) : [...selectedIds, nodeId]); },
    selectAll() { this.selectMany(task.nodes.map((node) => node.id)); },
    updateConfig(nodeId, key, value) {
      remember(`${nodeId}.${key}`);
      const node = nodeById(task, nodeId);
      node.config[key] = value;
      if (node.type === 'start' && key === 'trigger' && (TRIGGER_ACTIONS[value] || value === APP_TRIGGER)) {
        Object.entries(TRIGGER_DEFAULTS[value] || {}).forEach(([name, preset]) => { if (node.config[name] === undefined) node.config[name] = preset; });
        /* Triggers of one kind are told apart by their suffix. It is random, since an address that a person copies into a service is made of it. */
        node.config.triggerSuffix ??= crypto.randomUUID();
        task.active = true;
      }
      /* A further start is named after its trigger, as it is when the task is opened again. */
      if (node.type === 'start' && node !== rootNode(task) && (key === 'trigger' || key === 'app')) node.name = ACTIONS[TYPE_BY_BACKEND[triggerBackend(node.config)]]?.label ?? node.name;
      changed(key === 'switchCases' ? 'structure' : 'config');
    },
    /* The Start tile takes the first trigger. Each further one gets a start tile of its own under the others,
       leading where Start leads, since most tasks do the same work however they start. */
    addTrigger(trigger, app) {
      const root = rootNode(task);
      let tile = root;
      if (root.config.trigger !== 'manual' && trigger !== 'manual') {
        remember();
        const starts = task.nodes.filter((node) => node.type === 'start');
        tile = createNode(`n${uniqueId()}`, 'start', nameWithNumber(task, 'Start'));
        tile.position = { x: root.position.x, y: Math.max(...starts.map((node) => node.position.y)) + layout.rowGap };
        task.nodes.push(tile);
        const next = edgeFrom(task, root.id, 'out');
        if (next) task.edges.push({ from: tile.id, port: 'out', to: next.to });
        changed('structure');
      }
      this.updateConfig(tile.id, 'trigger', trigger);
      if (app) this.updateConfig(tile.id, 'app', app);
      selectedIds = [];
      this.select(tile.id);
      return tile;
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
      if (isAppTrigger(ACTIONS[type].backend)) return this.addTrigger(APP_TRIGGER, ACTIONS[type].backend);
      remember();
      const node = createNode(`n${uniqueId()}`, type, nameWithNumber(task, ACTIONS[type].label));
      const anchor = fromId || onlySelectedId() || lastOnMainPath(task);
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
      selectedIds = [node.id];
      changed('structure');
      return node;
    },
    /* The steps around a removed step join up when it led to one step. The first start stays. */
    removeNodes(nodeIds) {
      const removed = nodeIds.map((nodeId) => nodeById(task, nodeId)).filter((node) => node && node !== rootNode(task));
      if (!removed.length) return removed;
      remember();
      removed.forEach(({ id }) => {
        const outgoing = task.edges.filter((edge) => edge.from === id);
        if (outgoing.length === 1) task.edges.filter((edge) => edge.to === id && edge.from !== outgoing[0].to).forEach((edge) => { edge.to = outgoing[0].to; });
        task.edges = task.edges.filter((edge) => edge.to !== id && edge.from !== id);
      });
      task.nodes = task.nodes.filter((node) => !removed.includes(node));
      selectedIds = selectedIds.filter((nodeId) => nodeById(task, nodeId));
      changed('structure');
      return removed;
    },
    /* Adds copies of steps with the connections between them, and selects the copies. Without a position they
       land beside the steps they come from. */
    insertSteps({ nodes, edges }, position) {
      const steps = nodes.filter((step) => ACTIONS[step?.type] && !isEntry(step));
      if (!steps.length) return [];
      remember();
      const placeOf = (step) => ({ x: Number(step.position?.x) || 0, y: Number(step.position?.y) || 0 });
      const corner = { x: Math.min(...steps.map((step) => placeOf(step).x)), y: Math.min(...steps.map((step) => placeOf(step).y)) };
      const moveBy = position ? { x: position.x - corner.x, y: position.y - corner.y } : { x: 0, y: 0 };
      const landsOnTile = () => steps.some((step) => task.nodes.some((node) => node.position.x === placeOf(step).x + moveBy.x && node.position.y === placeOf(step).y + moveBy.y));
      const height = Math.max(...steps.map((step) => placeOf(step).y)) - corner.y;
      while (landsOnTile()) { moveBy.x += COPY_OFFSET.x; moveBy.y += height + COPY_OFFSET.y; }
      const copies = new Map();
      steps.forEach((step) => {
        const copy = createNode(`n${uniqueId()}`, step.type, nameWithNumber(task, withoutNumber(String(step.name || ACTIONS[step.type].label))), clone(step.config || {}));
        copy.position = { x: placeOf(step).x + moveBy.x, y: placeOf(step).y + moveBy.y };
        task.nodes.push(copy);
        copies.set(step.id, copy);
      });
      (edges || []).forEach((edge) => {
        const from = copies.get(edge.from);
        const to = copies.get(edge.to);
        if (from && to && from !== to && portsOf(from).some((port) => port.id === edge.port) && !edgeFrom(task, from.id, edge.port)) task.edges.push({ from: from.id, port: edge.port, to: to.id });
      });
      selectedIds = [...copies.values()].map((copy) => copy.id);
      changed('structure');
      return [...copies.values()];
    },
    duplicateNodes(nodeIds) { return this.insertSteps(stepsWithConnections(nodeIds)); },
    /* The text that Copy puts on the clipboard, or null when no step is chosen. */
    copiedSteps(nodeIds) {
      const steps = stepsWithConnections(nodeIds);
      return steps.nodes.length ? JSON.stringify({ kind: COPIED_STEPS_KIND, ...steps }) : null;
    },
    /* Pastes text that Copy made. Any other text adds nothing. */
    pasteSteps(text, position) {
      let copied = null;
      try { copied = JSON.parse(text); } catch { return []; }
      return copied?.kind === COPIED_STEPS_KIND && Array.isArray(copied.nodes) ? this.insertSteps(copied, position) : [];
    },
    setDeactivated(nodeIds, deactivated) {
      const steps = nodeIds.map((nodeId) => nodeById(task, nodeId)).filter((node) => node && node.type !== 'start');
      if (!steps.length) return;
      remember();
      steps.forEach((node) => { if (deactivated) node.config[STEP_DEACTIVATED] = true; else delete node.config[STEP_DEACTIVATED]; });
      changed('config');
    },
    /* Puts a step of another kind in the same place. It keeps the connections that fit, the name the person gave,
       what happens on a failure, and whether it is deactivated. Its own settings start empty. */
    replaceStep(nodeId, type) {
      const replaced = nodeById(task, nodeId);
      if (!replaced || isEntry(replaced) || isEntry({ type })) return null;
      remember();
      const kept = Object.fromEntries([...FAILURE_FIELD_NAMES, STEP_DEACTIVATED].filter((key) => key in replaced.config).map((key) => [key, replaced.config[key]]));
      const hasOwnName = withoutNumber(replaced.name) !== ACTIONS[replaced.type].label;
      const index = task.nodes.indexOf(replaced);
      task.nodes.splice(index, 1);
      const node = createNode(`n${uniqueId()}`, type, hasOwnName ? replaced.name : nameWithNumber(task, ACTIONS[type].label), kept);
      node.position = replaced.position;
      task.nodes.splice(index, 0, node);
      const portIds = portsOf(node).map((port) => port.id);
      task.edges = task.edges.filter((edge) => edge.from !== nodeId || portIds.includes(edge.port));
      task.edges.forEach((edge) => {
        if (edge.from === nodeId) edge.from = node.id;
        if (edge.to === nodeId) edge.to = node.id;
      });
      selectedIds = [node.id];
      changed('structure');
      return node;
    },
    canMoveToTask(nodeIds) { return !review && !showingOlderVersion && !!lineOf(nodeIds); },
    /* What a person has to know before the move: the values that do not pass between two tasks. */
    moveToTaskNotes(nodeIds) {
      const line = lineOf(nodeIds);
      const lastStep = line.at(-1);
      const namesOfMoved = new Set(line.map((node) => node.name));
      const readLater = new Set();
      const stepAfter = edgeFrom(task, lastStep.id, portsOf(lastStep)[0].id)?.to;
      const readingByHand = [];
      task.nodes.filter((node) => !line.includes(node)).forEach((node) => {
        const texts = textsIn(node.config);
        texts.flatMap(tokensIn).forEach((token) => {
          const { source, name } = readReference(token.reference);
          if (namesOfMoved.has(name) && source !== 'parameter' && (name !== lastStep.name || source === 'outputVariables')) readLater.add(name);
          if (source === 'previousVariable' && node.id === stepAfter) readLater.add(lastStep.name);
        });
        const writtenByHand = texts.map((text) => rewriteReferences(text, () => ''));
        if ([...namesOfMoved].some((name) => writtenByHand.some((text) => text.includes(`steps["${name}"]`)))) readingByHand.push(node);
      });
      const named = (nodes) => nodes.map((node) => `“${node.name}”`).join(', ');
      const withLogic = line.filter((node) => textsIn(node.config).some(hasHandWrittenLogic));
      const settingVariables = line.filter((node) => node.type === 'setVariable');
      const withFiles = line.filter((node) => node.type === 'readFile' || ACTIONS[node.type].fields.some((field) => field.files && node.config[field.key]));
      return [
        readLater.size && `Other steps read values of ${[...readLater].map((name) => `“${name}”`).join(', ')}. Only the answer of “${lastStep.name}” comes back from the new task.`,
        readingByHand.length && `${named(readingByHand)} reads the moved steps in logic written by hand. Point it to the new step after the move.`,
        withLogic.length && `${named(withLogic)} has logic written by hand. Check the values it reads in the new task.`,
        settingVariables.length && `The variables that ${named(settingVariables)} sets stay inside the new task.`,
        withFiles.length && `A file stays in the task that reads it: ${named(withFiles)}.`,
      ].filter(Boolean);
    },
    /* Makes a task of a line of steps, and puts one step in their place that runs it. Each value the steps read
       from outside the line becomes a parameter of the new task, which the one step gives it. */
    async moveStepsToTask(nodeIds, name) {
      const line = lineOf(nodeIds);
      if (name === task.name) throw new Error('The new task needs a name of its own.');
      if (name.includes('"')) throw new Error('A task name cannot have a double quote in it.');
      refuseTakenName(name);
      const [firstStep, lastStep] = [line[0], line.at(-1)];
      const namesOfMoved = new Set(line.map((node) => node.name));
      const parameterNames = new Map();
      /* `given` is what the step here gives the parameter: the value, or the value with its text for when it is empty. */
      const parameterFor = (reference, given = reference) => {
        if (!parameterNames.has(given)) {
          const { source, name: sourceName, path } = readReference(reference);
          const baseName = path.match(/[A-Za-z_]\w*/g)?.pop() ?? sourceName ?? source.split('.').pop();
          let parameterName = baseName;
          for (let number = 2; [...parameterNames.values()].includes(parameterName); number += 1) parameterName = `${baseName}_${number}`;
          parameterNames.set(given, parameterName);
        }
        return `$parameters.get("${parameterNames.get(given)}")`;
      };
      const runsBefore = (step) => line.slice(0, line.findIndex((node) => node.id === step.id)).some((node) => !node.config[STEP_DEACTIVATED]);
      const moved = stepsWithConnections(nodeIds);
      moved.nodes.forEach((step) => {
        step.config = withTexts(step.config, (text) => rewriteReferences(text, (token) => {
          const { source, name: sourceName } = readReference(token.reference);
          const staysInside = source.startsWith('previous') ? runsBefore(step) : namesOfMoved.has(sourceName) && source !== 'parameter';
          if (staysInside) return token.text;
          /* Only where the value comes from can it be missing, so its text for when it is empty stays there. */
          return token.operation === 'default' ? parameterFor(token.reference, token.text) : readFrom(token, parameterFor(token.reference));
        }));
      });
      const start = createNode('start', 'start', 'Start');
      start.position = { x: firstStep.position.x - layout.columnGap, y: firstStep.position.y };
      const newTask = {
        name, description: '', active: false, nodes: [start, ...moved.nodes],
        parameters: [...parameterNames.values()].map((parameterName) => ({ name: parameterName, defaultValue: '', description: '', required: false })),
        edges: [{ from: start.id, port: portsOf(start)[0].id, to: firstStep.id }, ...moved.edges],
      };
      await request('/task', { method: 'POST', body: JSON.stringify(toBackend(newTask)) });
      await loadTaskList();

      remember();
      const linkedStep = createNode(`n${uniqueId()}`, 'linkedTask', nameWithNumber(task, name), {
        name, parameters: [...parameterNames].map(([given, parameterName]) => ({ parameterName, parameterValue: given })),
      });
      linkedStep.position = firstStep.position;
      const linePlace = task.nodes.indexOf(firstStep);
      task.nodes = task.nodes.filter((node) => !line.includes(node));
      task.nodes.splice(Math.min(linePlace, task.nodes.length), 0, linkedStep);
      const chosen = new Set(line.map((node) => node.id));
      task.edges = task.edges.filter((edge) => !(chosen.has(edge.from) && chosen.has(edge.to)));
      task.edges.forEach((edge) => {
        if (edge.to === firstStep.id) edge.to = linkedStep.id;
        if (edge.from === lastStep.id) Object.assign(edge, { from: linkedStep.id, port: portsOf(linkedStep)[0].id });
      });
      /* The step answers what the last moved step answered, so the steps that read that answer now read it here. */
      task.nodes.forEach((node) => {
        if (node === linkedStep) return;
        node.config = withTexts(node.config, (text) => rewriteReferences(text, (token) => {
          const { source, name: sourceName, path } = readReference(token.reference);
          return sourceName === lastStep.name && (source === 'output' || source === 'data') ? readFrom(token, stepReference(linkedStep.name, source) + path) : token.text;
        }));
      });
      selectedIds = [linkedStep.id];
      changed('structure');
      await store.save();
      return linkedStep;
    },
    connect(fromId, port, toId) {
      /* A loop back to an earlier step is fine: the backend limits how often a step can run. */
      if (fromId === toId || isEntry(nodeById(task, toId))) return false;
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
    /* Needs nothing but this machine, so a first run works and shows steps, a branch and a value passed along. */
    addExample() {
      remember();
      const greet = createNode(`n${uniqueId()}`, 'print', 'Say hello', { message: 'Hello from DopplerTask' });
      const check = createNode(`n${uniqueId()}`, 'if', 'Did it say hello?', { condition: '$lastLog.output.contains("Hello")' });
      const yes = createNode(`n${uniqueId()}`, 'print', 'Yes', { message: 'Yes, the first step said hello.' });
      const no = createNode(`n${uniqueId()}`, 'print', 'No', { message: 'No, the first step said something else.' });
      task.nodes.push(greet, check, yes, no);
      task.edges.push({ from: rootNode(task).id, port: 'out', to: greet.id }, { from: greet.id, port: 'out', to: check.id }, { from: check.id, port: 'true', to: yes.id }, { from: check.id, port: 'false', to: no.id });
      layoutTask(task);
      changed('task');
    },
    tidy() { remember(); layoutTask(task); changed('structure'); },
    undo() { if (!undoStack.length) return; redoStack.push(snapshot()); restore(undoStack.pop()); coalesceKey = null; changed('structure'); },
    redo() { if (!redoStack.length) return; undoStack.push(snapshot()); restore(redoStack.pop()); coalesceKey = null; changed('structure'); },

    /* Every save is a new version on the server, unless the content is unchanged. */
    async save({ autoSaved = false } = {}) {
      clearTimeout(autoSaveTimer);
      const renamedFrom = savedName && savedName !== task.name ? savedName : null;
      if (task.name !== savedName) refuseTakenName(task.name);
      setSaveState('saving');
      try {
        const { checksum } = await request(autoSaved ? '/task?autoSaved=true' : '/task', { method: 'POST', body: JSON.stringify(toBackend(task)) });
        task.checksum = checksum;
        task.id = task.name;
        localStorage.setItem(LAST_TASK_KEY, task.name);
        savedName = task.name;
        /* What a person saves replaces the proposal it started from. */
        if (review && review.checksum !== checksum) await request(`/task/${review.checksum}`, { method: 'DELETE' });
        review = null;
        emit('review');
        const restored = showingOlderVersion;
        showingOlderVersion = false;
        setSaveState('saved');
        await loadTaskList();
        if (restored) return 'This version is now the current one.';
        return renamedFrom ? `Saved as a new task. ${renamedFrom} is still there.` : 'Saved';
      } catch (error) {
        setSaveState('unsaved');
        throw error;
      }
    },
    async saveAs(name, description) {
      task.name = name;
      task.description = description;
      newTriggerAddresses(task);
      emit('meta');
      return store.save();
    },
    async openVersion(checksum) {
      if (!mayLeaveTask()) return;
      task = fromBackend(await request(`/task/${checksum}/checksum`));
      const versions = tasks.find((candidate) => candidate.name === task.name)?.versions || [];
      const current = versions.find((version) => !version.proposed);
      review = null;
      if (versions.find((version) => version.checksum === checksum)?.proposed) {
        const baseline = current ? fromBackend(await request(`/task/${current.checksum}/checksum`)) : { nodes: [], edges: [] };
        review = { checksum, isNewTask: !current, ...compareVersions(baseline, task) };
      }
      localStorage.setItem(LAST_TASK_KEY, task.name);
      savedName = task.name;
      selectedIds = []; undoStack = []; redoStack = [];
      runner.clear();
      /* A run always uses the newest version, so an older one on screen has to be saved before it can run. */
      showingOlderVersion = !review && current?.checksum !== checksum;
      saveState = showingOlderVersion ? 'unsaved' : 'saved';
      emit('task');
      await loadRuns();
    },
    /* Otherwise the empty task keeps a name that is free now that the saved ones are known. */
    openLastTask() {
      if (tasks.some((candidate) => candidate.name === localStorage.getItem(LAST_TASK_KEY))) return store.switchTask(localStorage.getItem(LAST_TASK_KEY));
      const { id, name } = freshTask(tasks.map((candidate) => candidate.name));
      Object.assign(task, { id, name });
      emit('meta');
      return null;
    },
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
      if (!mayLeaveTask()) return;
      showingOlderVersion = false;
      review = null;
      task = freshTask(tasks.map((candidate) => candidate.name));
      savedName = null;
      selectedIds = []; undoStack = []; redoStack = []; runs = [];
      runner.clear();
      saveState = 'unsaved';
      emit('task');
    },
    importTask(serialized) {
      const imported = fromBackend(JSON.parse(serialized));
      /* A new task gets addresses of its own; restoring a saved one keeps the addresses its services know. */
      if (!tasks.some((saved) => saved.name === imported.name)) newTriggerAddresses(imported);
      if (!mayLeaveTask()) return false;
      showingOlderVersion = false;
      review = null;
      task = imported;
      /* Importing under an existing name is how an exported version is restored. */
      savedName = task.name;
      selectedIds = []; undoStack = []; redoStack = []; runs = [];
      runner.clear();
      saveState = 'unsaved';
      emit('task');
      return true;
    },
    exportTask() { return JSON.stringify(toBackend(task), null, 2); },

    /* With resumeFrom, continues a failed run at its failed step: the steps before it keep their results. */
    async approveProposal() {
      const { checksum } = review;
      await request(`/task/${checksum}/approve`, { method: 'POST' });
      await loadTaskList();
      await store.openVersion(checksum);
    },
    async rejectProposal() {
      const { name } = task;
      await request(`/task/${review.checksum}`, { method: 'DELETE' });
      await loadTaskList();
      review = null;
      if (tasks.some((candidate) => candidate.name === name)) await store.switchTask(name);
      else store.newTask();
    },
    /* A preview goes through every step with real values and holds what a step would send. One that continues a preview is one too. */
    async startRun(parameterValues, { resumeFrom, preview = !!resumeFrom?.preview } = {}) {
      if (review && !preview) throw new Error('This is a proposal. Approve or reject it before running. A preview shows what it would do.');
      if (saveState !== 'saved') await store.save();
      /* A proposal is known by its checksum, since the name gives the approved version. */
      const { id } = await request('/schedule/task', { method: 'POST', body: JSON.stringify({ taskName: review ? review.checksum : task.name, parameters: parameterValues, resumeFromExecutionId: resumeFrom?.id, preview: preview ? previewRequest() : undefined }) });
      const finishedSteps = resumeFrom ? resumeFrom.steps.slice(0, -1) : [];
      tested = new Map();
      const run = { id: String(id), preview, status: 'running', live: true, parameters: parameterValues, startedAt: Date.now(), durationMs: finishedSteps.reduce((total, step) => total + step.durationMs, 0), steps: [...finishedSteps], complete: false, nextNodeId: resumeFrom ? resumeFrom.steps.at(-1).nodeId : rootNode(task).id };
      runs = [run, ...runs];
      runner.load(run, { atEnd: true });
      followRun(run);
      return run;
    },
    async openRun(runId) {
      const run = runs.find((candidate) => candidate.id === runId);
      if (!run.complete && !run.following) applyExecution(run, await request(`/execution/${run.id}`));
      /* One that a schedule or a webhook started is followed as it goes, like one started here. */
      if (run.status === 'running' && !run.following) {
        run.nextNodeId = run.steps.length ? nextNodeAfter(run.steps.at(-1)) : rootNode(task).id;
        followRun(run);
      }
      runner.load(run);
    },
    /* The run ends as stopped once the server has stopped it, which following it shows. Nothing follows a waiting run
       while it waits, so a stop starts following it. */
    async stopRun(run) {
      await request(`/execution/${run.id}/stop`, { method: 'POST' });
      if (!run.live) followRun(run);
    },
    /* Runs one step on its own with the values given, without touching the run history. In a preview the step
       holds what it would send. What it gives becomes the example the steps after it read. */
    async testStep(nodeId, values, { preview = false } = {}) {
      testingStepId = nodeId;
      emit('testing');
      try {
        const node = nodeById(task, nodeId);
        const started = performance.now();
        const result = await testOnServer({ '@type': ACTIONS[node.type].backend, ...withoutEmptyValues(node.config), ports: [] }, values, nodeId, preview);
        const failed = result.statusCode === 'FAILURE';
        const step = { nodeId, preview, status: failed ? 'failed' : 'success', output: failed && !result.errorMsg ? '' : result.output, error: failed ? result.errorMsg || result.output || 'This step failed.' : null, outputVariables: result.outputVariables || {}, held: result.held, missingValues: result.missingValues || [], durationMs: performance.now() - started };
        if (!failed) { tested.set(nodeId, step); emit('run'); }
        return step;
      } finally {
        testingStepId = null;
        emit('testing');
      }
    },
    /* What a text or a condition of this step gives with the example values, worked out by the server that runs the
       task, so a preview and a run agree. Nothing is sent. */
    async previewField(nodeId, text, values, { asCondition = false } = {}) {
      const { scriptLanguage } = nodeById(task, nodeId).config;
      const action = asCondition ? { '@type': 'IfAction', condition: text } : { '@type': 'PrintAction', message: text };
      const result = await testOnServer({ ...action, scriptLanguage, ports: [] }, values, nodeId, true);
      const problem = result.statusCode === 'FAILURE' ? result.errorMsg || result.output : null;
      const isEmpty = problem === 'No output';
      return { text: problem ? '' : result.output, error: isEmpty ? null : problem, missingValues: result.missingValues || [] };
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

function intervalText(config) {
  const time = config.time || 1;
  const unit = (config.timeUnit || 'HOURS').toLowerCase();
  return `${time} ${String(time) === '1' ? unit.replace(/s$/, '') : unit}`;
}

const SCHEDULE_UNITS = { SECONDS: 'second', MINUTES: 'minute', HOURS: 'hour', DAYS: 'day', WEEKS: 'week', MONTHS: 'month' };

/* The schedule as a short sentence, such as "every 15 minutes" or "every week on MON at 09:00". */
function scheduleText(config) {
  const rule = config.rule || 'MINUTES';
  const zone = config.timezone ? ` (${config.timezone})` : '';
  if (rule === 'CRON') return `by cron ${firstLine(config.cronExpression || '') || '…'}${zone}`;
  const every = Number(config.every || 1);
  const unit = SCHEDULE_UNITS[rule] || 'minute';
  const often = every === 1 ? `every ${unit}` : `every ${every} ${unit}s`;
  const time = `${String(config.atHour || 0).padStart(2, '0')}:${String(config.atMinute || 0).padStart(2, '0')}`;
  const when = { HOURS: ` at minute ${config.atMinute || 0}`, DAYS: ` at ${time}`, WEEKS: ` on ${config.onDays || 'MON'} at ${time}`, MONTHS: ` on day ${config.onDayOfMonth || 1} at ${time}` }[rule] || '';
  return `${often}${when}${config.weekdaysOnly && ['SECONDS', 'MINUTES', 'HOURS', 'DAYS'].includes(rule) ? ' on weekdays' : ''}${zone}`;
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
    ACTIONS, CATEGORIES, FAILURE_FIELDS, INITIALS, APP_TRIGGER, iconOf,
    createStore, stepsMatching, portsOf, edgeFrom, nodeById, rootNode, isDeactivated,
    describeNode, summaryText, fieldVisible,
    formatDuration, formatAgo, intervalText, scheduleText,
  };
}
