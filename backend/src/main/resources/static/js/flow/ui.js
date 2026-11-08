/* Forms, command palette, dialogs, toasts and shortcuts. */
import { BRAND_ICONS } from './brand-icons';

export default function boot() {
  const { ACTIONS, FAILURE_FIELDS, formatDuration, formatAgo } = window.DT;

  const ICON_PATHS = {
    logOut: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
    history: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5M12 7v5l3 2"/>',
    replay: '<path d="M3 12a9 9 0 1 0 3-6.7L3 8M3 3v5h5"/>',
    play: '<path d="M7 4.5v15l12-7.5z"/>',
    pause: '<path d="M8 5v14M16 5v14"/>',
    stop: '<rect x="6" y="6" width="12" height="12" rx="1.5"/>',
    split: '<path d="M4 12h5l6-6h5M9 12l6 6h5"/><path d="m17 3 3 3-3 3M17 15l3 3-3 3"/>',
    signpost: '<path d="M12 3v18M12 5h6l3 3-3 3h-6M12 11H6l-3 3 3 3h6"/>',
    repeat: '<path d="m17 2 4 4-4 4M3 11v-1a4 4 0 0 1 4-4h14M7 22l-4-4 4-4M21 13v1a4 4 0 0 1-4 4H3"/>',
    braces: '<path d="M8 3H7a2 2 0 0 0-2 2v5a2 2 0 0 1-2 2 2 2 0 0 1 2 2v5a2 2 0 0 0 2 2h1M16 3h1a2 2 0 0 1 2 2v5a2 2 0 0 0 2 2 2 2 0 0 0-2 2v5a2 2 0 0 1-2 2h-1"/>',
    code: '<path d="m8 7-5 5 5 5M16 7l5 5-5 5M14 4l-4 16"/>',
    regex: '<path d="M16 4v8M12.5 6l7 4M19.5 6l-7 4"/><circle cx="7" cy="17" r="3"/>',
    tags: '<path d="m7 8-4 4 4 4M17 8l4 4-4 4"/><path d="M9.5 17h5"/>',
    workflow: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/><path d="M6.5 10v3a3 3 0 0 0 3 3H14"/>',
    message: '<path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
    globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
    server: '<rect x="3" y="4" width="18" height="7" rx="1.5"/><rect x="3" y="13" width="18" height="7" rx="1.5"/><path d="M7 7.5h.01M7 16.5h.01"/>',
    send: '<path d="M21 3 10 14M21 3l-7 18-4-7-7-4z"/>',
    database: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
    key: '<circle cx="8" cy="15" r="4"/><path d="m11 12 9-9M16 7l3 3M13 10l2 2"/>',
    terminal: '<path d="m5 7 5 5-5 5M12 18h7"/>',
    fileText: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 13h6M9 17h6"/>',
    filePen: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M9 17l5-5 1.5 1.5-5 5H9z"/>',
    appWindow: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M7 6.5h.01M10 6.5h.01"/>',
    pointer: '<path d="m5 4 6 16 2.5-6.5L20 11z"/>',
    sparkles: '<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9zM19 16l.8 2.2L22 19l-2.2.8L19 22l-.8-2.2L16 19l2.2-.8z"/>',
    wrench: '<path d="M14.7 6.3a4 4 0 0 1 5-1.1l-3 3 1.9 1.9 3-3a4 4 0 0 1-5.4 5l-8.4 8.4a2 2 0 0 1-2.8-2.8l8.4-8.4a4 4 0 0 1 .8-3z"/>',
    plus: '<path d="M12 5v14M5 12h14"/>', x: '<path d="M6 6l12 12M18 6 6 18"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>', search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    undo: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10a6 6 0 0 1 0 12h-3"/>', redo: '<path d="m15 14 5-5-5-5"/><path d="M20 9H10a6 6 0 0 0 0 12h3"/>',
    chevronDown: '<path d="m6 9 6 6 6-6"/>', chevronRight: '<path d="m9 6 6 6-6 6"/>', chevronUp: '<path d="m6 15 6-6 6 6"/>',
    trash: '<path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v5M14 11v5"/>',
    copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15H4V4h11v1"/>',
    alert: '<path d="M12 3 2.5 20h19zM12 10v4M12 17h.01"/>', clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
    sliders: '<path d="M4 7h10M18 7h2M4 17h2M10 17h10"/><circle cx="16" cy="7" r="2"/><circle cx="8" cy="17" r="2"/>',
    zoomIn: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5M11 8v6M8 11h6"/>', zoomOut: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5M8 11h6"/>',
    fit: '<path d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"/>', tidy: '<rect x="3" y="4" width="6" height="5" rx="1"/><rect x="15" y="4" width="6" height="5" rx="1"/><rect x="15" y="15" width="6" height="5" rx="1"/><path d="M9 6.5h6M12 6.5v11h3"/>',
    download: '<path d="M12 4v11m-4-4 4 4 4-4M4 20h16"/>', upload: '<path d="M12 16V5m-4 4 4-4 4 4M4 20h16"/>',
    folder: '<path d="M3 6a2 2 0 0 1 2-2h4l2 3h8a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>', filePlus: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5M12 11v6M9 14h6"/>',
    eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7z"/><circle cx="12" cy="12" r="3"/>', eyeOff: '<path d="M4 4l16 16M10 5.2A10 10 0 0 1 22 12a14 14 0 0 1-3 3.6M6.5 7A14 14 0 0 0 2 12s3.5 7 10 7a10 10 0 0 0 4-.8"/>',
    mail: '<rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/>',
    keyboard: '<rect x="2" y="6" width="20" height="13" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M7 15h10"/>',
    webhook: '<circle cx="12" cy="6" r="3"/><circle cx="6" cy="17" r="3"/><circle cx="18" cy="17" r="3"/><path d="m10.5 8.6-3 5.8M9 17h6M13.5 8.6l3 5.8"/>',
  };

  const icon = (name, className = '') => {
    if (BRAND_ICONS[name]) return `<svg class="icon brand-icon ${className}" viewBox="${BRAND_ICONS[name].viewBox}" aria-hidden="true">${BRAND_ICONS[name].body}</svg>`;
    if (!ICON_PATHS[name]) console.error(`There is no icon called ${name}.`);
    return `<svg class="icon ${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name] || ''}</svg>`;
  };


  const escapeHtml = (value) => String(value ?? '').replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[character]);
  const isMac = /Mac|iPhone|iPad/.test(navigator.platform);
  const modKey = isMac ? '⌘' : 'Ctrl+';
  const kbd = (text) => `<kbd class="ui-kbd">${text}</kbd>`;

  /* ---------- Toast ---------- */

  const MAX_TOASTS = 2;
  const MAX_JSON_TO_FORMAT = 500000;

  /* Copies the output as it is shown. */
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-copy-output]');
    if (!button) return;
    navigator.clipboard.writeText(button.closest('.ui-result').querySelector('pre').textContent).then(() => toast('Output copied'));
  });

  function toast(message, { actionLabel, onAction, tone } = {}) {
    let host = document.querySelector('.ui-toasts');
    if (!host) { host = document.createElement('div'); host.className = 'ui-toasts'; host.setAttribute('role', 'status'); document.body.append(host); }
    const element = document.createElement('div');
    element.className = `ui-toast ${tone ? `is-${tone}` : ''}`;
    element.innerHTML = `<span>${escapeHtml(message)}</span>${actionLabel ? `<button type="button">${escapeHtml(actionLabel)}</button>` : ''}`;
    const dismiss = () => { element.classList.add('is-leaving'); setTimeout(() => element.remove(), 160); };
    element.querySelector('button')?.addEventListener('click', () => { onAction?.(); dismiss(); });
    /* Fast work should not pile up messages over the diagram. */
    if (host.children.length >= MAX_TOASTS) host.firstChild.remove();
    host.append(element);
    setTimeout(dismiss, actionLabel ? 6000 : 3000);
  }

  /* ---------- Popover & dialog ---------- */

  let closeActivePopover = null;
  function openPopover(anchor, html, { align = 'start', className = '' } = {}) {
    closeActivePopover?.();
    const element = document.createElement('div');
    element.className = `ui-popover ${className}`;
    element.setAttribute('role', 'dialog');
    element.setAttribute('aria-label', anchor.getAttribute('aria-label') || anchor.title || 'Menu');
    element.innerHTML = html;
    document.body.append(element);
    const anchorBox = anchor.getBoundingClientRect();
    /* offset sizes ignore the opening scale animation, which would make the popover measure small. */
    const box = { width: element.offsetWidth, height: element.offsetHeight };
    const left = align === 'end' ? anchorBox.right - box.width : anchorBox.left;
    const opensUpward = anchorBox.bottom + box.height + 8 > innerHeight;
    const top = opensUpward ? Math.max(8, anchorBox.top - box.height - 6) : anchorBox.bottom + 6;
    element.style.left = `${Math.max(8, Math.min(left, innerWidth - box.width - 8))}px`;
    element.style.top = `${top}px`;
    element.style.transformOrigin = `${align === 'end' ? 'right' : 'left'} ${opensUpward ? 'bottom' : 'top'}`;
    const onOutside = (event) => { if (!element.contains(event.target) && !anchor.contains(event.target)) close(); };
    const onKey = (event) => { if (event.key === 'Escape') close(); };
    function close() {
      element.remove();
      document.removeEventListener('pointerdown', onOutside, true);
      document.removeEventListener('keydown', onKey);
      closeActivePopover = null;
    }
    document.addEventListener('pointerdown', onOutside, true);
    document.addEventListener('keydown', onKey);
    closeActivePopover = close;
    return { element, close };
  }

  function openDialog(html, { className = '', animate = true } = {}) {
    const dialog = document.createElement('dialog');
    dialog.className = `ui-dialog ${className} ${animate ? '' : 'no-motion'}`;
    dialog.innerHTML = html;
    document.body.append(dialog);
    const nativeClose = dialog.close.bind(dialog);
    dialog.close = (...closeArguments) => { nativeClose(...closeArguments); dialog.remove(); };
    dialog.addEventListener('close', () => dialog.remove());
    dialog.addEventListener('pointerdown', (event) => { if (event.target === dialog) dialog.close(); });
    dialog.querySelectorAll('[data-dialog-close]').forEach((button) => button.addEventListener('click', () => dialog.close()));
    dialog.showModal();
    return dialog;
  }

  const dialogHeader = (title, subtitle) => `<header class="ui-dialog-head"><div><h2>${escapeHtml(title)}</h2>${subtitle ? `<p>${escapeHtml(subtitle)}</p>` : ''}</div><button type="button" class="ui-icon-button" data-dialog-close aria-label="Close">${icon('x')}</button></header>`;

  /* ---------- Step form ---------- */

  const variableSuggestions = (store) => [
    ...store.task.parameters.map((parameter) => ({ insert: `$parameters.get("${parameter.name}")`, label: parameter.name, detail: parameter.description || 'Task parameter', group: 'Parameters' })),
    { insert: '$lastLog.output', label: 'Previous output', detail: 'What the step before produced', group: 'From the run' },
    ...Object.entries(store.selectedNode ? store.outputsBefore(store.selectedNode.id) : {}).map(([name, label]) => ({ insert: `$lastLog.outputVariables.${name}`, label, detail: 'What the step before gives', group: 'From the run' })),
    { insert: '$executionId', label: 'Run ID', detail: 'Unique to this run', group: 'From the run' },
    { insert: '$parameters.get("TRIGGER_body")', label: 'Webhook body', detail: 'Body of the incoming call', group: 'From the trigger' },
  ];

  function renderField(field, node) {
    const inputId = `field-${node.id}-${field.key}`;
    const fieldValue = node.config[field.key];
    const common = `id="${inputId}" data-field="${field.key}"`;
    const label = `<label class="ui-label" for="${inputId}">${escapeHtml(field.label)}${field.required ? '' : '<span class="ui-optional">optional</span>'}</label>`;
    const hint = field.hint ? `<p class="ui-hint">${escapeHtml(field.hint)}</p>` : '';
    const wrap = (inner) => `<div class="ui-field ${field.half ? 'is-half' : ''}">${inner}${hint}</div>`;
    const placeholder = `placeholder="${escapeHtml(field.placeholder || '')}"`;
    switch (field.type) {
      case 'select': {
        const options = field.options.map((option) => (typeof option === 'string' ? { value: option, label: option } : option));
        const current = fieldValue ?? options[0].value;
        return wrap(`${label.replace('<span class="ui-optional">optional</span>', '')}<div class="ui-select"><select ${common}>${options.map((option) => `<option value="${escapeHtml(option.value)}" ${option.value === current ? 'selected' : ''}>${escapeHtml(option.label)}</option>`).join('')}</select>${icon('chevronDown')}</div>`);
      }
      case 'credential': {
        const choices = DT.canvas.store.credentials.filter((credential) => credential.type === field.credentialType);
        const missing = fieldValue && !choices.some((credential) => credential.name === fieldValue);
        const options = [{ name: '', text: choices.length ? 'Choose a credential' : 'No credential yet' }, ...choices.map(({ name }) => ({ name, text: name })), ...(missing ? [{ name: fieldValue, text: `${fieldValue} (does not exist)` }] : [])];
        return wrap(`${label}<div class="ui-credential-row"><div class="ui-select"><select ${common}>${options.map(({ name, text }) => `<option value="${escapeHtml(name)}" ${name === (fieldValue || '') ? 'selected' : ''}>${escapeHtml(text)}</option>`).join('')}</select>${icon('chevronDown')}</div>${fieldValue && !missing ? '<button type="button" class="ui-button" data-credential-edit>Edit</button>' : ''}<button type="button" class="ui-button" data-credential-new>New</button></div>`);
      }
      case 'number': return wrap(`${label}<input class="ui-input" type="number" min="0" ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}">`);
      case 'password': return wrap(`${label}<div class="ui-input-wrap"><input class="ui-input" type="password" autocomplete="off" ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}"><button type="button" class="ui-input-action" data-reveal aria-label="Show value">${icon('eye')}</button></div>`);
      case 'toggle': return wrap(`<label class="ui-toggle"><input type="checkbox" ${common} ${fieldValue ? 'checked' : ''}><span class="ui-toggle-track"></span><span>${escapeHtml(field.label)}</span></label>`);
      case 'code': return wrap(`${label}<textarea class="ui-input is-mono" rows="${Math.min(8, Math.max(3, String(fieldValue ?? '').split('\n').length + 1))}" spellcheck="false" data-variables ${common} ${placeholder}>${escapeHtml(fieldValue ?? '')}</textarea><p class="ui-hint">Type <code>$</code> to insert a parameter or earlier output.</p>`);
      case 'expression': {
        /* Which task to run is picked from the saved ones, rather than typed from memory. */
        const taskChoices = node.type === 'linkedTask' && field.key === 'name'
          ? `<datalist id="${inputId}-tasks">${DT.canvas.store.tasks.filter((task) => task.name !== DT.canvas.store.task.name).map((task) => `<option value="${escapeHtml(task.name)}">`).join('')}</datalist>`
          : '';
        const control = field.multiline
          ? `<textarea class="ui-input is-mono" rows="3" spellcheck="false" data-variables ${common} ${placeholder}>${escapeHtml(fieldValue ?? '')}</textarea>`
          : `<input class="ui-input is-mono" spellcheck="false" autocomplete="off" data-variables ${taskChoices ? `list="${inputId}-tasks"` : ''} ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}">`;
        return wrap(`${label}${control}${taskChoices}<p class="ui-preview" data-preview="${field.key}"></p>`);
      }
      case 'map': {
        const rows = fieldValue || [];
        const cell = (column, row, index) => (column.options
          ? `<div class="ui-select"><select aria-label="${escapeHtml(column.label)}" data-map-index="${index}" data-map-part="${column.key}">${column.options.map((choice) => `<option ${choice === row[column.key] ? 'selected' : ''}>${escapeHtml(choice)}</option>`).join('')}</select>${icon('chevronDown')}</div>`
          : `<input class="ui-input is-mono" data-variables placeholder="${escapeHtml(column.label)}" aria-label="${escapeHtml(column.label)}" data-map-index="${index}" data-map-part="${column.key}" value="${escapeHtml(row[column.key] ?? '')}">`);
        return `<fieldset class="ui-field ui-list" data-map="${field.key}"><legend class="ui-label">${escapeHtml(field.label)}${field.required ? '' : '<span class="ui-optional">optional</span>'}</legend>${rows.map((row, index) => `<div class="ui-list-row">${field.columns.map((column) => cell(column, row, index)).join('')}<button type="button" class="ui-icon-button" data-map-remove="${index}" aria-label="Remove">${icon('x')}</button></div>`).join('')}<button type="button" class="ui-link-button" data-map-add>${icon('plus')}Add</button></fieldset>`;
      }
      default: return wrap(`${label}<input class="ui-input ${field.mono ? 'is-mono' : ''}" autocomplete="off" spellcheck="false" ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}">`);
    }
  }

  function webhookUrl(task, node) {
    return `${window.location.origin}/webhook/${encodeURIComponent(task.name)}/Webhook/${node.config.triggerSuffix}/${node.config.path || ''}`;
  }

  function renderForm(node, store) {
    const action = ACTIONS[node.type];
    /* Most steps never need another language or address, and in a script the language is the first thing to decide. */
    const isAdvanced = (field) => field.key === 'baseUrl' || (field.key === 'scriptLanguage' && node.type !== 'script');
    const visibleFields = action.fields.filter((field) => DT.fieldVisible(field, node.config) && !isAdvanced(field));
    const advancedFields = action.fields.filter(isAdvanced);
    const issues = store.issues.filter((issue) => issue.nodeId === node.id && !issue.field);
    const canFail = node.type !== 'start';
    const hasFailureSettings = canFail && (node.config.retries || node.config.continueOnFailure || node.config.failOn);
    return `
      ${issues.map((issue) => `<p class="ui-note is-warn">${icon('alert')}${escapeHtml(issue.message)}</p>`).join('')}
      <div class="ui-field-grid">${visibleFields.map((field) => renderField(field, node)).join('')}</div>
      ${node.type === 'start' && node.config.trigger === 'webhook' ? `<div class="ui-field"><span class="ui-label">Call this URL</span><div class="ui-copy-row"><code data-webhook-url>${escapeHtml(webhookUrl(store.task, node))}</code><button type="button" class="ui-icon-button" data-copy="${escapeHtml(webhookUrl(store.task, node))}" aria-label="Copy URL">${icon('copy')}</button></div><p class="ui-hint">Works once the task is saved with its trigger on.</p></div>` : ''}
      ${canFail ? `<details class="ui-disclosure" ${hasFailureSettings ? 'open' : ''}><summary>${icon('chevronRight')}If this step fails${node.config.retries ? `<span class="ui-pill">${node.config.retries} ${node.config.retries === 1 ? 'retry' : 'retries'}</span>` : ''}</summary><div class="ui-field-grid">${FAILURE_FIELDS.map((field) => renderField(field, node)).join('')}</div></details>` : ''}
      ${advancedFields.length ? `<details class="ui-disclosure" ${node.config.scriptLanguage === 'JAVASCRIPT' || node.config.baseUrl ? 'open' : ''}><summary>${icon('chevronRight')}Advanced</summary><div class="ui-field-grid">${advancedFields.map((field) => renderField(field, node)).join('')}</div></details>` : ''}
      <p class="ui-backend-name">${escapeHtml(action.backend)}</p>`;
  }


  function updatePreviews(root, store) {
    root.querySelectorAll('[data-preview]').forEach((previewElement) => {
      const input = root.querySelector(`[data-field="${previewElement.dataset.preview}"]`);
      if (!input || !input.value.includes('$')) { previewElement.textContent = ''; previewElement.hidden = true; return; }
      const lastStep = store.runs[0]?.steps.at(-1);
      if (!lastStep) { previewElement.hidden = true; return; }
      const resolved = DT.interpolate(input.value, lastStep.variables);
      previewElement.hidden = false;
      previewElement.innerHTML = `<span>Last run</span> ${escapeHtml(resolved).slice(0, 140)}`;
    });
  }

  /* Renders a step's fields into `root` and keeps them bound to the store. */
  function mountForm(root, store, nodeId) {
    const render = () => {
      const node = DT.nodeById(store.task, nodeId);
      if (!node) { root.innerHTML = ''; return; }
      root.innerHTML = renderForm(node, store);
      updatePreviews(root, store);
    };
    const currentNode = () => DT.nodeById(store.task, nodeId);
    const hasConditionalFields = () => ACTIONS[currentNode().type].fields.some((field) => field.showWhen);
    const onInput = (event) => {
      const target = event.target;
      const node = currentNode();
      if (target.dataset.field) {
        const newValue = target.type === 'checkbox' ? target.checked : target.type === 'number' ? (target.value === '' ? '' : Number(target.value)) : target.value;
        /* Leaving a field fires change with the value input already saved. Saving it again rebuilds the panel
           under the pointer, which would swallow the click that left the field. */
        if (node.config[target.dataset.field] !== newValue) store.updateConfig(nodeId, target.dataset.field, newValue);
        if (event.type === 'change' && (target.tagName === 'SELECT' || target.type === 'checkbox') && (hasConditionalFields() || target.dataset.field === 'credential')) render();
        updatePreviews(root, store);
        const urlElement = root.querySelector('[data-webhook-url]');
        if (urlElement) {
          urlElement.textContent = webhookUrl(store.task, node);
          urlElement.nextElementSibling.dataset.copy = urlElement.textContent;
        }
      } else if (target.dataset.mapIndex !== undefined) {
        const key = target.closest('[data-map]').dataset.map;
        const rows = node.config[key].map((row) => ({ ...row }));
        rows[Number(target.dataset.mapIndex)][target.dataset.mapPart] = target.value;
        store.updateConfig(nodeId, key, rows);
      }
    };
    const onClick = (event) => {
      const button = event.target.closest('button');
      if (!button) return;
      const node = currentNode();
      const mapKey = button.closest('[data-map]')?.dataset.map;
      if (button.dataset.credentialNew !== undefined || button.dataset.credentialEdit !== undefined) {
        const field = ACTIONS[node.type].fields.find((candidate) => candidate.key === 'credential' && DT.fieldVisible(candidate, node.config));
        const editing = button.dataset.credentialEdit !== undefined ? store.credentials.find((credential) => credential.name === node.config.credential) : null;
        openCredentialDialog(store, field, editing, (name) => { store.updateConfig(nodeId, 'credential', name); render(); });
        return;
      }
      if (button.dataset.mapAdd !== undefined) {
        const { columns } = ACTIONS[node.type].fields.find((field) => field.key === mapKey);
        store.updateConfig(nodeId, mapKey, [...(node.config[mapKey] || []), Object.fromEntries(columns.map((column) => [column.key, column.options ? column.options[0] : '']))]);
        render();
        root.querySelector(`[data-map="${mapKey}"] .ui-list-row:last-of-type input`)?.focus();
      } else if (button.dataset.mapRemove !== undefined) { store.updateConfig(nodeId, mapKey, node.config[mapKey].filter((row, index) => index !== Number(button.dataset.mapRemove))); render(); }
      else if (button.dataset.reveal !== undefined) {
        const input = button.parentElement.querySelector('input');
        const revealing = input.type === 'password';
        input.type = revealing ? 'text' : 'password';
        button.innerHTML = icon(revealing ? 'eyeOff' : 'eye');
        button.setAttribute('aria-label', revealing ? 'Hide value' : 'Show value');
      } else if (button.dataset.copy) { navigator.clipboard?.writeText(button.dataset.copy); toast('Webhook URL copied'); }
    };
    const onVariableKey = attachVariableMenu(root, store);
    root.addEventListener('input', onInput);
    root.addEventListener('change', onInput);
    root.addEventListener('click', onClick);
    render();
    return { render, dispose() { root.removeEventListener('input', onInput); root.removeEventListener('change', onInput); root.removeEventListener('click', onClick); onVariableKey(); } };
  }

  /* `$` in any expression-capable input opens a menu of what is available at this point. */
  function attachVariableMenu(root, store) {
    let menu = null;
    let activeIndex = 0;
    let suggestions = [];
    let activeInput = null;
    let tokenStart = 0;
    const closeMenu = () => { menu?.remove(); menu = null; };
    const renderMenu = () => {
      menu.innerHTML = suggestions.map((item, index) => `<button type="button" class="ui-var-item ${index === activeIndex ? 'is-active' : ''}" data-index="${index}"><span>${escapeHtml(item.label)}</span><small>${escapeHtml(item.detail)}</small></button>`).join('');
    };
    const insert = (item) => {
      const end = activeInput.selectionStart;
      activeInput.value = activeInput.value.slice(0, tokenStart) + item.insert + activeInput.value.slice(end);
      activeInput.selectionStart = activeInput.selectionEnd = tokenStart + item.insert.length;
      activeInput.dispatchEvent(new Event('input', { bubbles: true }));
      activeInput.focus();
      closeMenu();
    };
    const onInput = (event) => {
      const input = event.target;
      if (!input.matches('[data-variables]')) return;
      const before = input.value.slice(0, input.selectionStart);
      const token = before.match(/\$([\w.]*)$/);
      if (!token) { closeMenu(); return; }
      activeInput = input;
      tokenStart = before.length - token[0].length;
      const query = token[1].toLowerCase();
      suggestions = variableSuggestions(store).filter((item) => `${item.label} ${item.insert}`.toLowerCase().includes(query));
      if (!suggestions.length) { closeMenu(); return; }
      activeIndex = 0;
      if (!menu) {
        menu = document.createElement('div');
        menu.className = 'ui-var-menu';
        menu.addEventListener('pointerdown', (pointerEvent) => {
          pointerEvent.preventDefault();
          const item = pointerEvent.target.closest('[data-index]');
          if (item) insert(suggestions[Number(item.dataset.index)]);
        });
        (input.closest('dialog') || document.body).append(menu);
      }
      const box = input.getBoundingClientRect();
      menu.style.left = `${box.left}px`;
      menu.style.top = `${box.bottom + 4}px`;
      menu.style.width = `${Math.max(box.width, 260)}px`;
      renderMenu();
    };
    const onKey = (event) => {
      if (!menu) return;
      if (event.key === 'ArrowDown') { event.preventDefault(); activeIndex = (activeIndex + 1) % suggestions.length; renderMenu(); }
      else if (event.key === 'ArrowUp') { event.preventDefault(); activeIndex = (activeIndex - 1 + suggestions.length) % suggestions.length; renderMenu(); }
      else if (event.key === 'Enter' || event.key === 'Tab') { event.preventDefault(); event.stopPropagation(); insert(suggestions[activeIndex]); }
      else if (event.key === 'Escape') { event.stopPropagation(); closeMenu(); }
    };
    const onBlur = () => setTimeout(closeMenu, 100);
    root.addEventListener('input', onInput);
    root.addEventListener('keydown', onKey);
    root.addEventListener('focusout', onBlur);
    return () => { root.removeEventListener('input', onInput); root.removeEventListener('keydown', onKey); root.removeEventListener('focusout', onBlur); closeMenu(); };
  }

  /* ---------- Command palette ---------- */

  function buildCommands(store, handlers) {
    return [
      { id: 'run', label: 'Run task', icon: 'play', shortcut: `${modKey}↵`, run: handlers.run },
      { id: 'params', label: 'Task parameters', icon: 'sliders', shortcut: 'P', run: () => openParametersDialog(store) },
      { id: 'new', label: 'New task', icon: 'filePlus', run: () => store.newTask() },
      { id: 'open', label: 'Open task…', icon: 'folder', shortcut: 'O', run: () => openTaskDialog(store) },
      { id: 'credentials', label: 'Credentials…', icon: 'key', run: () => openCredentialsDialog(store) },
      { id: 'duplicate', label: 'Save as…', icon: 'copy', run: () => openSaveAsDialog(store) },
      { id: 'export', label: 'Export task as JSON', icon: 'download', run: () => exportTask(store) },
      { id: 'import', label: 'Import task from JSON…', icon: 'upload', run: () => importTask(store) },
      ...(handlers.extraCommands || []),
      { id: 'help', label: 'Keyboard shortcuts', icon: 'keyboard', shortcut: '?', run: openShortcutsDialog },
    ];
  }

  /* Opens instantly with no animation: this is a repeated, keyboard-driven surface. */
  function openPalette({ store, handlers, title = 'Add a step or run a command', onPick, stepsOnly = false }) {
    const commands = stepsOnly ? [] : buildCommands(store, handlers);
    const dialog = openDialog(`<div class="ui-palette-search">${icon('search')}<input aria-label="${escapeHtml(title)}" placeholder="${escapeHtml(title)}" autocomplete="off" spellcheck="false"></div><div class="ui-palette-list" role="listbox"></div><footer class="ui-palette-foot"><span>${kbd('↑')}${kbd('↓')} move</span><span>${kbd('↵')} choose</span><span>${kbd('esc')} close</span></footer>`, { className: 'ui-palette', animate: false });
    const input = dialog.querySelector('input');
    const list = dialog.querySelector('.ui-palette-list');
    let activeIndex = 0;
    let flatItems = [];
    const render = () => {
      const query = input.value.trim().toLowerCase();
      const matches = (text) => !query || text.toLowerCase().includes(query);
      const groups = DT.stepsMatching(query).map(({ category, items }) => ({
        label: category.label,
        items: items.map(({ type, action }) => ({ kind: 'step', type, label: action.label, detail: action.description, icon: action.icon, category: action.category })),
      }));
      const commandItems = commands.filter((command) => matches(command.label)).map((command) => ({ kind: 'command', ...command }));
      if (commandItems.length) groups.push({ label: 'Commands', items: commandItems });
      flatItems = groups.flatMap((group) => group.items);
      activeIndex = Math.min(activeIndex, Math.max(0, flatItems.length - 1));
      let position = -1;
      list.innerHTML = groups.length ? groups.map((group) => `<div class="ui-palette-group"><h3>${escapeHtml(group.label)}</h3>${group.items.map((item) => { position += 1; return `<button type="button" role="option" class="ui-palette-item ${position === activeIndex ? 'is-active' : ''}" data-index="${position}" ${item.category ? `data-category="${item.category}"` : ''}><span class="ui-palette-icon">${icon(item.icon)}</span><span class="ui-palette-label">${escapeHtml(item.label)}</span>${item.detail ? `<small>${escapeHtml(item.detail)}</small>` : ''}${item.shortcut ? kbd(item.shortcut) : ''}</button>`; }).join('')}</div>`).join('') : `<p class="ui-empty">Nothing matches “${escapeHtml(input.value)}”.</p>`;
    };
    const choose = (item) => {
      if (!item) return;
      dialog.close();
      if (item.kind === 'command') item.run(); else onPick(item.type);
    };
    input.addEventListener('input', () => { activeIndex = 0; render(); });
    dialog.addEventListener('keydown', (event) => {
      if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
        event.preventDefault();
        const step = event.key === 'ArrowDown' ? 1 : -1;
        activeIndex = (activeIndex + step + flatItems.length) % flatItems.length;
        render();
        list.querySelector('.is-active')?.scrollIntoView({ block: 'nearest' });
      } else if (event.key === 'Enter') { event.preventDefault(); choose(flatItems[activeIndex]); }
    });
    list.addEventListener('click', (event) => {
      const button = event.target.closest('[data-index]');
      if (button) choose(flatItems[Number(button.dataset.index)]);
    });
    render();
    input.focus();
  }

  /* ---------- Task-level dialogs ---------- */

  function openRunDialog(store, onRun, { onFixIssue, values } = {}) {
    const issues = store.issues;
    if (issues.length) {
      const dialog = openDialog(`${dialogHeader(`${issues.length} ${issues.length === 1 ? 'thing needs' : 'things need'} attention before running`)}<ul class="ui-issue-list">${issues.map((issue) => `<li><button type="button" data-node="${issue.nodeId}">${icon('alert')}<span><b>${escapeHtml(DT.nodeById(store.task, issue.nodeId).name)}</b>${escapeHtml(issue.message)}</span>${icon('chevronRight')}</button></li>`).join('')}</ul>`, { className: 'is-small' });
      dialog.addEventListener('click', (event) => {
        const button = event.target.closest('[data-node]');
        if (!button) return;
        dialog.close();
        store.select(button.dataset.node);
        onFixIssue?.(button.dataset.node);
      });
      return;
    }
    if (!store.task.parameters.length) { onRun({}); return; }
    const lastValues = values || store.runs[0]?.parameters || {};
    const dialog = openDialog(`${dialogHeader(`Run “${store.task.name}”`, 'Choose the values for this run.')}
      <form method="dialog" class="ui-form" novalidate>
        ${store.task.parameters.map((parameter) => `<div class="ui-field"><label class="ui-label" for="run-${escapeHtml(parameter.name)}">${escapeHtml(parameter.name)}<span class="ui-optional">${parameter.required ? 'required' : 'optional'}</span></label><input class="ui-input" id="run-${escapeHtml(parameter.name)}" name="${escapeHtml(parameter.name)}" data-required="${parameter.required ? 1 : ''}" autocomplete="off" spellcheck="false" value="${escapeHtml(lastValues[parameter.name] ?? parameter.defaultValue ?? '')}">${parameter.description ? `<p class="ui-hint">${escapeHtml(parameter.description)}</p>` : ''}</div>`).join('')}
        <footer class="ui-dialog-foot"><button type="button" class="ui-button" data-dialog-close>Cancel</button><button type="submit" class="ui-button is-primary">${icon('play')}Run ${kbd(`${modKey}↵`)}</button></footer>
      </form>`, { className: 'is-small' });
    const form = dialog.querySelector('form');
    const submit = (event) => {
      event.preventDefault();
      const missing = [...form.querySelectorAll('[data-required="1"]')].filter((input) => !input.value.trim());
      form.querySelectorAll('.ui-input').forEach((input) => {
        input.classList.toggle('is-invalid', missing.includes(input));
        input.setAttribute('aria-invalid', missing.includes(input));
      });
      if (missing.length) { missing[0].focus(); return; }
      const values = Object.fromEntries(new FormData(form));
      dialog.close();
      onRun(values);
    };
    form.addEventListener('submit', submit);
    dialog.addEventListener('keydown', (event) => { if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) submit(event); });
    form.querySelector('input').select();
  }

  function openParametersDialog(store) {
    let parameters = store.task.parameters.map((parameter) => ({ ...parameter }));
    const dialog = openDialog(`${dialogHeader('Task parameters', 'Values a person, webhook or another task provides when this task starts.')}<div class="ui-params"></div><footer class="ui-dialog-foot"><button type="button" class="ui-link-button" data-add>${icon('plus')}Add parameter</button><span class="ui-spacer"></span><button type="button" class="ui-button" data-dialog-close>Cancel</button><button type="button" class="ui-button is-primary" data-save>Save parameters</button></footer>`, { className: 'is-medium' });
    const host = dialog.querySelector('.ui-params');
    const render = () => {
      host.innerHTML = parameters.length ? `<div class="ui-params-head"><span>Name</span><span>Default</span><span>Description</span><span>Required</span><span></span></div>${parameters.map((parameter, index) => `<div class="ui-params-row" data-index="${index}"><input class="ui-input is-mono" aria-label="Name" data-part="name" value="${escapeHtml(parameter.name)}" placeholder="name"><input class="ui-input" aria-label="Default value" data-part="defaultValue" value="${escapeHtml(parameter.defaultValue ?? '')}" placeholder="None"><input class="ui-input" aria-label="Description" data-part="description" value="${escapeHtml(parameter.description ?? '')}" placeholder="What is it for?"><label class="ui-toggle" aria-label="Required"><input type="checkbox" data-part="required" ${parameter.required ? 'checked' : ''}><span class="ui-toggle-track"></span></label><button type="button" class="ui-icon-button" data-remove aria-label="Remove parameter">${icon('x')}</button></div>`).join('')}` : '<p class="ui-empty">This task runs without any input. Add a parameter to ask for values when it starts.</p>';
    };
    host.addEventListener('input', (event) => {
      const row = event.target.closest('[data-index]');
      if (!row) return;
      parameters[Number(row.dataset.index)][event.target.dataset.part] = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    });
    dialog.addEventListener('click', (event) => {
      if (event.target.closest('[data-remove]')) { parameters.splice(Number(event.target.closest('[data-index]').dataset.index), 1); render(); }
      if (event.target.closest('[data-add]')) { parameters.push({ name: '', defaultValue: '', description: '', required: false }); render(); host.querySelector('.ui-params-row:last-child input').focus(); }
      if (event.target.closest('[data-save]')) {
        const named = parameters.filter((parameter) => parameter.name.trim());
        const names = named.map((parameter) => parameter.name.trim());
        const repeated = names.find((name, index) => names.indexOf(name) !== index);
        if (repeated) { toast(`Two parameters are called ${repeated}. Give each its own name.`, { tone: 'danger' }); return; }
        store.setParameters(named);
        dialog.close();
        toast('Parameters saved');
      }
    });
    render();
  }

  /* The sv-SE locale formats local time as YYYY-MM-DD HH:mm:ss. */
  const versionLabel = (version) => `${new Date(version.created).toLocaleString('sv-SE')}${version.description ? ` - ${version.description}` : ''}${version.autoSaved ? ' - AUTO SAVED' : ''}${version.proposed ? ' - PROPOSED BY AN AGENT' : version.madeByAgent ? ` - MADE BY AN AGENT${version.approvedBy ? `, APPROVED BY ${version.approvedBy}` : ''}` : ''}`;

  function openTaskDialog(store) {
    const dialog = openDialog(`${dialogHeader('Tasks', 'Open any saved version, or delete a task.')}<div class="ui-task-list">${store.tasks.length ? store.tasks.map((task) => `<div class="ui-task-row ${task.id === store.task.id ? 'is-current' : ''}" data-task="${escapeHtml(task.id)}"><span><b>${escapeHtml(task.name)}</b><div class="ui-select"><select aria-label="Version of ${escapeHtml(task.name)}">${task.versions.map((version) => `<option value="${version.checksum}">${escapeHtml(versionLabel(version))}</option>`).join('')}</select>${icon('chevronDown')}</div></span><button type="button" class="ui-button" data-open>Open</button><button type="button" class="ui-button" data-delete aria-label="Delete ${escapeHtml(task.name)}">${icon('trash')}Delete</button></div>`).join('') : '<p class="ui-empty">No saved tasks yet.</p>'}</div><footer class="ui-dialog-foot"><button type="button" class="ui-button" data-import>${icon('upload')}Import…</button><span class="ui-spacer"></span><button type="button" class="ui-button is-primary" data-new>${icon('plus')}New task</button></footer>`, { className: 'is-medium' });
    dialog.addEventListener('click', async (event) => {
      const row = event.target.closest('[data-task]');
      if (event.target.closest('[data-open]')) {
        dialog.close();
        store.openVersion(row.querySelector('select').value).catch((error) => toast(error.message, { tone: 'danger' }));
      }
      if (event.target.closest('[data-delete]')) {
        const name = row.dataset.task;
        const { versions } = store.tasks.find((task) => task.id === name);
        if (!confirm(`Delete ${name} and its ${versions.length} saved version(s)? This cannot be undone.`)) return;
        dialog.close();
        store.deleteTask(name).then(() => toast(`Deleted ${name}`), (error) => toast(`Could not delete ${name}. ${error.message}`, { tone: 'danger' }));
      }
      if (event.target.closest('[data-new]')) { dialog.close(); store.newTask(); }
      if (event.target.closest('[data-import]')) { dialog.close(); importTask(store); }
    });
  }

  /* A saved value is never sent back, so a field is shown empty and stays as it is unless something is typed. */
  function openCredentialDialog(store, field, editing, onSaved) {
    const form = { id: 'credential', config: {} };
    const asField = (one) => renderField({ ...one, required: one.required && !editing, placeholder: editing?.fieldsSet.includes(one.key) ? 'Saved. Leave empty to keep it.' : one.placeholder }, form);
    /* Signing in with an account is its own part, and its tokens come from connecting, never from typing. */
    const accountFields = field.fields.filter((one) => one.key.startsWith('oauth') && !ACCOUNT_TOKENS.includes(one.key));
    const ownFields = field.fields.filter((one) => !one.key.startsWith('oauth'));
    const connected = ACCOUNT_TOKENS.some((key) => editing?.fieldsSet.includes(key));
    const account = accountFields.length ? `<details class="ui-disclosure" ${connected || !ownFields.length ? 'open' : ''}><summary>${icon('chevronRight')}Sign in with an account<span class="ui-pill">${connected ? 'Connected' : 'Not connected'}</span></summary>
        <div class="ui-field-grid"><p class="ui-hint">Register <code>${escapeHtml(location.origin)}/oauth/callback</code> as the return address with the service.</p>
        ${accountFields.map(asField).join('')}</div></details>` : '';
    const dialog = openDialog(`${dialogHeader(editing ? `Edit ${editing.name}` : 'New credential', 'Encrypted when saved, and never shown again. Steps name it, and get its values only while they run.')}
      <form class="ui-form">
        ${editing ? '' : '<div class="ui-field"><label class="ui-label" for="credential-name">Name</label><input class="ui-input" id="credential-name" autocomplete="off" spellcheck="false" placeholder="such as prod-slack"></div>'}
        ${ownFields.map(asField).join('')}
        ${account}
        <footer class="ui-dialog-foot"><button type="button" class="ui-button" data-dialog-close>Cancel</button>${accountFields.length ? `<button type="button" class="ui-button" data-connect>${connected ? 'Save and connect again' : 'Save and connect'}</button>` : ''}<button type="submit" class="ui-button is-primary">Save</button></footer>
      </form>`);
    dialog.querySelector('.ui-input')?.focus();
    dialog.addEventListener('click', (event) => {
      const reveal = event.target.closest('[data-reveal]');
      if (reveal) { const input = reveal.parentElement.querySelector('input'); input.type = input.type === 'password' ? 'text' : 'password'; }
    });
    const nameOf = () => (editing ? editing.name : dialog.querySelector('#credential-name').value.trim());
    const valuesOf = () => Object.fromEntries([...dialog.querySelectorAll('[data-field]')].map((input) => [input.dataset.field, input.value]));

    /* Saving comes first, so a credential is kept even when connecting its account fails, and the person is told which. */
    async function save(signInWindow) {
      const name = nameOf();
      try {
        await store.saveCredential(name, field.credentialType, valuesOf(), !!editing);
      } catch (error) { signInWindow?.close(); toast(error.message, { tone: 'danger' }); return; }
      dialog.close();
      onSaved?.(name);
      if (!signInWindow) { toast(editing ? `Saved ${name}` : `Created ${name}`); return; }
      try {
        signInWindow.location = await store.connectCredential(name);
        watchConnection(store, name);
      } catch (error) { signInWindow.close(); toast(`Saved ${name}, but the account is not connected. ${error.message}`, { tone: 'danger' }); }
    }

    /* Checked before the sign-in window opens, so that a missing field leaves no empty window behind. */
    const complete = () => {
      const values = valuesOf();
      const missing = nameOf() ? field.fields.find((one) => one.required && !editing && !values[one.key])?.label : 'Name';
      if (missing) toast(`${missing} is required`, { tone: 'danger' });
      return !missing;
    };
    dialog.querySelector('form').addEventListener('submit', (event) => {
      event.preventDefault();
      if (complete()) save(null);
    });
    dialog.querySelector('[data-connect]')?.addEventListener('click', () => {
      /* Opened at once, inside the click, so that the browser does not block it. */
      if (complete()) save(window.open('', 'dopplertask-connect', 'width=520,height=700'));
    });
  }

  const ACCOUNT_TOKENS = ['oauthRefreshToken', 'oauthAccessToken'];

  /* The sign-in page says when the account is connected, so that the list shows it without a reload. */
  function watchConnection(store, name) {
    const done = async (event) => {
      if (event.origin !== location.origin || event.data !== 'dopplertask-account-connected') return;
      window.removeEventListener('message', done);
      await store.loadCredentials();
      toast(`Connected the account of ${name}`);
    };
    window.addEventListener('message', done);
  }

  const credentialFieldOf = (type) => Object.values(ACTIONS).flatMap((action) => action.fields).find((field) => field.type === 'credential' && field.credentialType === type);
  const typeLabel = (type) => Object.values(ACTIONS).find((action) => action.backend === type)?.label || type;

  function openCredentialsDialog(store) {
    const usedBy = ({ usedBy: names }) => (names.length ? `Used by ${names.join(', ')}` : 'Not used by a task');
    const types = [...new Set(Object.values(ACTIONS).flatMap((action) => action.fields).filter((field) => field.type === 'credential').map((field) => field.credentialType))]
      .sort((first, second) => typeLabel(first).localeCompare(typeLabel(second)));
    const dialog = openDialog(`${dialogHeader('Credentials', 'What steps sign in with. Tasks only hold the name, so they can be shared and exported safely.')}
      <div class="ui-task-list">${store.credentials.length ? store.credentials.map((credential) => `<div class="ui-task-row" data-credential="${escapeHtml(credential.name)}"><span><b>${escapeHtml(credential.name)}</b><small>${escapeHtml(typeLabel(credential.type))}. ${escapeHtml(usedBy(credential))}</small></span><button type="button" class="ui-button" data-edit>Edit</button><button type="button" class="ui-button" data-delete>Delete</button></div>`).join('') : '<p class="ui-hint">No credentials yet. Create one here, or from a step that needs it.</p>'}</div>
      <footer class="ui-dialog-foot"><div class="ui-select"><select id="credential-kind" aria-label="Kind of credential">${types.map((type) => `<option value="${escapeHtml(type)}">${escapeHtml(typeLabel(type))}</option>`).join('')}</select>${icon('chevronDown')}</div><button type="button" class="ui-button is-primary" data-new>New credential</button><button type="button" class="ui-button" data-dialog-close>Close</button></footer>`);
    dialog.addEventListener('click', (event) => {
      const name = event.target.closest('[data-credential]')?.dataset.credential;
      const credential = store.credentials.find((candidate) => candidate.name === name);
      if (event.target.closest('[data-new]')) {
        const type = dialog.querySelector('#credential-kind').value;
        dialog.close();
        openCredentialDialog(store, credentialFieldOf(type), null);
      }
      if (event.target.closest('[data-edit]')) { dialog.close(); openCredentialDialog(store, credentialFieldOf(credential.type), credential); }
      if (event.target.closest('[data-delete]') && confirm(`Delete ${name}? This cannot be undone.`)) {
        dialog.close();
        store.deleteCredential(name).then(() => toast(`Deleted ${name}`), (error) => toast(error.message, { tone: 'danger' }));
      }
    });
  }

  function openSaveAsDialog(store) {
    const dialog = openDialog(`${dialogHeader('Save as', 'Saves a new version. A new name starts a separate task.')}<form method="dialog" class="ui-form"><div class="ui-field"><label class="ui-label" for="save-as-name">Task name</label><input class="ui-input" id="save-as-name" autocomplete="off" value="${escapeHtml(store.task.name)}"></div><div class="ui-field"><label class="ui-label" for="save-as-description">Description<span class="ui-optional">optional</span></label><input class="ui-input" id="save-as-description" autocomplete="off" value="${escapeHtml(store.task.description || '')}"><p class="ui-hint">Shown next to this version in the task list.</p></div><footer class="ui-dialog-foot"><button type="button" class="ui-button" data-dialog-close>Cancel</button><button type="submit" class="ui-button is-primary">Save</button></footer></form>`, { className: 'is-small' });
    const nameInput = dialog.querySelector('#save-as-name');
    nameInput.select();
    dialog.querySelector('form').addEventListener('submit', () => {
      store.saveAs(nameInput.value.trim().replace(/\s+/g, '_') || store.task.name, dialog.querySelector('#save-as-description').value)
        .then((message) => toast(message), (error) => toast(error.message, { tone: 'danger' }));
    });
  }

  function exportTask(store) {
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([store.exportTask()], { type: 'application/json' }));
    link.download = `${store.task.name}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
    toast(`Exported ${link.download}`);
  }

  function importTask(store) {
    const picker = document.createElement('input');
    picker.type = 'file';
    picker.accept = 'application/json,.json';
    picker.addEventListener('change', async () => {
      try { if (store.importTask(await picker.files[0].text())) toast('Task imported'); }
      catch (error) { toast(error instanceof SyntaxError ? 'That file is not valid JSON, so it is not a task.' : error.message, { tone: 'danger' }); }
    });
    picker.click();
  }

  function openShortcutsDialog() {
    const rows = [
      [`${modKey}K`, 'Add a step or run a command'], [`${modKey}↵`, 'Run the task'], [`${modKey}S`, 'Save a new version'],
      [`${modKey}Z`, 'Undo'], [`${modKey}⇧Z`, 'Redo'], [`${modKey}D`, 'Duplicate the selected step'],
      ['⌫', 'Delete the selected step'], ['P', 'Task parameters'], ['O', 'Open a task'], ['esc', 'Close or clear selection'], ['?', 'This list'],
    ];
    openDialog(`${dialogHeader('Keyboard shortcuts')}<dl class="ui-shortcuts">${rows.map(([keys, label]) => `<div><dt>${escapeHtml(label)}</dt><dd>${kbd(keys)}</dd></div>`).join('')}</dl>`, { className: 'is-small', animate: false });
  }

  const saveStatus = (store) => (store.review ? 'Proposal, not running' : { saving: 'Saving…', saved: 'Saved', unsaved: store.showingOlderVersion ? 'Older version, not current' : store.task.checksum ? 'Unsaved changes' : 'Not saved yet' }[store.saveState]);

  /* ---------- Shortcuts ---------- */

  function bindShortcuts(store, handlers) {
    document.addEventListener('keydown', (event) => {
      const modified = event.metaKey || event.ctrlKey;
      const typing = event.target.matches?.('input, textarea, select, [contenteditable="true"]');
      const dialogOpen = !!document.querySelector('dialog[open]');
      if (modified && event.key.toLowerCase() === 'k') { event.preventDefault(); if (!dialogOpen) handlers.palette(); return; }
      if (dialogOpen) return;
      if (modified && event.key === 'Enter') { event.preventDefault(); handlers.run(); return; }
      if (modified && event.key.toLowerCase() === 's') { event.preventDefault(); store.save().then((message) => toast(message), (error) => toast(error.message, { tone: 'danger' })); return; }
      if (typing) { if (event.key === 'Escape') event.target.blur(); return; }
      if (modified && event.key.toLowerCase() === 'z') { event.preventDefault(); if (event.shiftKey) store.redo(); else store.undo(); return; }
      if (modified && event.key.toLowerCase() === 'd' && store.selectedId) { event.preventDefault(); store.duplicateNode(store.selectedId); return; }
      if (modified) return;
      if ((event.key === 'Delete' || event.key === 'Backspace') && store.selectedId) { event.preventDefault(); removeWithUndo(store, store.selectedId); return; }
      if (event.key === 'Escape') { handlers.escape ? handlers.escape() : store.select(null); return; }
      if (event.key === '?') { openShortcutsDialog(); return; }
      if (event.key.toLowerCase() === 'p') { openParametersDialog(store); return; }
      if (event.key.toLowerCase() === 'o') { openTaskDialog(store); return; }
      handlers.key?.(event);
    });
  }

  function removeWithUndo(store, nodeId) {
    const node = DT.nodeById(store.task, nodeId);
    if (!node) return;
    if (node.type === 'start') { toast('Every task needs its Start step.'); return; }
    const name = node.name;
    store.removeNode(nodeId);
    toast(`Deleted “${name}”`, { actionLabel: 'Undo', onAction: () => store.undo() });
  }

  /* The output of a step in a run. */
  /* JSON is what most services answer with, and one long line of it cannot be read. */
  function readableOutput(output) {
    const text = String(output);
    if (!/^\s*[{[]/.test(text) || text.length > MAX_JSON_TO_FORMAT) return text;
    try { return JSON.stringify(JSON.parse(text), null, 2); } catch (error) { return text; }
  }

  function renderStepResult(step) {
    if (!step) return '<p class="ui-empty">This step did not run.</p>';
    return `
      ${step.error ? `<p class="ui-note is-danger">${icon('alert')}${escapeHtml(step.error)}</p>` : ''}
      ${step.output ? `<div class="ui-result"><div class="ui-result-head"><span class="ui-label">Output</span>${navigator.clipboard ? `<button type="button" class="ui-link-button" data-copy-output>${icon('copy')}Copy</button>` : ''}</div><pre>${escapeHtml(readableOutput(step.output))}</pre></div>` : ''}`;
  }

  const STATE_GLYPHS = { success: 'check', failed: 'x', warning: 'alert', stopped: 'stop' };
  const statusGlyph = (state) => `<span class="status-glyph is-${state}" role="img" aria-label="${state}">${STATE_GLYPHS[state] ? icon(STATE_GLYPHS[state]) : ''}</span>`;

  const RUN_STATUS_LABELS = { success: 'Succeeded', failed: 'Failed', warning: 'Finished with warnings', running: 'Running', stopped: 'Stopped' };
  const runParameterSummary = (run) => Object.values(run.parameters).join(' · ');

  window.DT.ui = {
    statusGlyph, RUN_STATUS_LABELS, runParameterSummary,
    icon, escapeHtml, modKey, toast, openPopover, mountForm, openPalette,
    openRunDialog, openParametersDialog, openTaskDialog, openCredentialsDialog, openSaveAsDialog, exportTask, importTask, openShortcutsDialog,
    saveStatus, bindShortcuts, removeWithUndo, renderStepResult, formatDuration, formatAgo,
  };
}
