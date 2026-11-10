/* Forms, command palette, dialogs, toasts and shortcuts. */
import { BRAND_ICONS } from './brand-icons';

export default function boot() {
  const { ACTIONS, FAILURE_FIELDS, INITIALS, formatDuration, formatAgo } = window.DT;

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
    duplicate: '<rect x="4" y="4" width="16" height="16" rx="2"/><path d="M12 9v6M9 12h6"/>',
    paste: '<rect x="5" y="5" width="14" height="16" rx="2"/><path d="M9 5V3h6v2M9 11h6M9 15h4"/>',
    more: '<path d="M5 12h.01M12 12h.01M19 12h.01" stroke-width="3"/>', power: '<path d="M12 3v9M6.3 6.3a8 8 0 1 0 11.4 0"/>',
    pen: '<path d="M4 20h4L19 9l-4-4L4 16zM13 7l4 4"/>', swap: '<path d="M4 8h14m-4-4 4 4-4 4M20 16H6m4 4-4-4 4-4"/>',
    selectAll: '<rect x="4" y="4" width="16" height="16" rx="2" stroke-dasharray="3 3"/><path d="m9 12 2 2 4-4"/>',
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

  /* A service without a free logo shows its initials, in a colour that stays the same for its name. */
  const initialsOf = (name) => {
    const words = name.replace(/([a-z])([A-Z])/g, '$1 $2').split(/[\s\-_.]+/).filter(Boolean);
    return words.length > 1 ? (words[0][0] + words[1][0]).toUpperCase() : name.slice(0, 1).toUpperCase() + name.slice(1, 2).toLowerCase();
  };
  const hueOf = (name) => [...name].reduce((hash, character) => (hash * 31 + character.charCodeAt(0)) % 360, 7);
  const initialsIcon = (connector, className) => `<svg class="icon brand-icon ${className}" viewBox="0 0 24 24" aria-hidden="true"><rect x="1" y="1" width="22" height="22" rx="6" fill="hsl(${hueOf(connector)} 52% 42%)"/><text x="12" y="16" text-anchor="middle" font-size="10.5" font-weight="600" font-family="inherit" fill="#fff">${escapeHtml(initialsOf(ACTIONS[connector]?.label ?? connector))}</text></svg>`;

  const icon = (name, className = '') => {
    if (BRAND_ICONS[name]) return `<svg class="icon brand-icon ${className}" viewBox="${BRAND_ICONS[name].viewBox}" aria-hidden="true">${BRAND_ICONS[name].body}</svg>`;
    if (name.startsWith(INITIALS)) return initialsIcon(name.slice(INITIALS.length), className);
    if (!ICON_PATHS[name]) console.error(`There is no icon called ${name}.`);
    return `<svg class="icon ${className}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICON_PATHS[name] || ''}</svg>`;
  };


  // The fields each form loaded from its service, by step and field, for as long as the page is open.
  const loadedForms = new Map();
  const formValues = (text) => { try { return JSON.parse(text || '{}'); } catch { return {}; } };

  // Above this many choices a list is searched by typing.
  const LONG_LIST = 12;
  // Where a step sends its key, and the notes such an address can get. They only inform; the address is the person's choice.
  const ADDRESS_FIELDS = ['baseUrl', 'siteUrl', 'signInUrl', 'servers', 'hostname', 'brokerUrl', 'jenkinsUrl', 'region'];
  const ADDRESS_NOTES = {
    unencrypted: { text: 'Not encrypted', applies: (address) => /^\s*http:\/\//i.test(address) },
    taskData: { text: 'Uses task data: data from outside the task can choose where your key goes.', applies: (address) => /[$#]/.test(address) },
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

  /* The open popovers, the innermost last. A dropdown inside a popover opens over it; any other popover closes first. */
  const openPopovers = [];
  function openPopover(anchor, html, { align = 'start', className = '', minWidth = 0 } = {}) {
    while (openPopovers.length && !openPopovers.at(-1).element.contains(anchor)) openPopovers.at(-1).close();
    const element = document.createElement('div');
    element.className = `ui-popover ${className}`;
    element.setAttribute('role', 'dialog');
    element.setAttribute('aria-label', anchor.getAttribute('aria-label') || anchor.title || 'Menu');
    element.style.minWidth = `${minWidth}px`;
    element.innerHTML = html;
    /* A dialog shows above the page, so what opens from inside it lives in it. */
    (anchor.closest('dialog') || document.body).append(element);
    const anchorBox = anchor.getBoundingClientRect();
    /* offset sizes ignore the opening scale animation, which would make the popover measure small. */
    const box = { width: element.offsetWidth, height: element.offsetHeight };
    const left = align === 'end' ? anchorBox.right - box.width : anchorBox.left;
    const opensUpward = anchorBox.bottom + box.height + 8 > innerHeight;
    const top = opensUpward ? Math.max(8, anchorBox.top - box.height - 6) : anchorBox.bottom + 6;
    element.style.left = `${Math.max(8, Math.min(left, innerWidth - box.width - 8))}px`;
    element.style.top = `${top}px`;
    element.style.transformOrigin = `${align === 'end' ? 'right' : 'left'} ${opensUpward ? 'bottom' : 'top'}`;
    const popover = { element, close };
    const holds = (target) => anchor.contains(target) || openPopovers.slice(openPopovers.indexOf(popover)).some((open) => open.element.contains(target));
    const onOutside = (event) => { if (!holds(event.target)) close(); };
    /* Escape closes the innermost popover only, and leaves a dialog around it open. */
    const onKey = (event) => { if (event.key === 'Escape' && openPopovers.at(-1) === popover) { event.preventDefault(); close(); } };
    function close() {
      if (!openPopovers.includes(popover)) return;
      while (openPopovers.at(-1) !== popover) openPopovers.at(-1).close();
      openPopovers.pop();
      element.remove();
      document.removeEventListener('pointerdown', onOutside, true);
      document.removeEventListener('keydown', onKey);
    }
    document.addEventListener('pointerdown', onOutside, true);
    document.addEventListener('keydown', onKey);
    openPopovers.push(popover);
    return popover;
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

  /* The one dropdown: a button that carries its value as a select does, and says a new one with a change event. */
  function dropdown(options, current, attributes = '') {
    const choices = options.map((option) => (typeof option === 'string' ? { value: option, label: option } : option));
    const chosen = choices.find((choice) => choice.value === current) ?? choices[0];
    return `<button type="button" class="ui-input ui-picker" aria-haspopup="listbox" data-dropdown="${escapeHtml(JSON.stringify(choices))}" value="${escapeHtml(chosen.value)}" ${attributes}><span>${escapeHtml(chosen.label)}</span>${icon('chevronDown')}</button>`;
  }
  document.addEventListener('click', (event) => {
    const button = event.target.closest('[data-dropdown]');
    if (!button) return;
    const choices = JSON.parse(button.dataset.dropdown);
    openPicker(button, choices, button.value, (value) => {
      button.value = value;
      button.querySelector('span').textContent = choices.find((choice) => choice.value === value).label;
      button.focus();
      button.dispatchEvent(new Event('change', { bubbles: true }));
      /* A change can draw the form again, which puts a new button in the place of this one. */
      if (!button.isConnected && button.id) document.getElementById(button.id)?.focus();
    }, button.dataset.grouped !== undefined);
  });

  function renderField(field, node) {
    const inputId = `field-${node.id}-${field.key}`;
    const fieldValue = node.config[field.key];
    const common = `id="${inputId}" data-field="${field.key}"`;
    const label = `<label class="ui-label" for="${inputId}">${escapeHtml(field.label)}${field.required ? '' : '<span class="ui-optional">optional</span>'}</label>`;
    const hint = field.hint ? `<p class="ui-hint">${escapeHtml(field.hint)}</p>` : '';
    /* The value notes come last, under the words that say what the field is for. */
    const wrap = (inner, notes = '') => `<div class="ui-field ${field.half ? 'is-half' : ''}">${inner}${hint}${notes}</div>`;
    const placeholder = `placeholder="${escapeHtml(field.placeholder || '')}"`;
    /* Where the values of the field show as chips, and what the field gives with the example values. */
    const valueNotes = `<div class="ui-chips" data-chips="${field.key}" hidden></div><p class="ui-preview" data-preview="${field.key}" hidden></p>`;
    switch (field.type) {
      case 'select':
        /* The choices of a big connector carry their group in the label, which the open list shows as headings. */
        return wrap(`${label.replace('<span class="ui-optional">optional</span>', '')}${dropdown(field.options, fieldValue, `${common} ${field.options.length > LONG_LIST ? 'data-grouped' : ''}`)}`);
      case 'credential': {
        const choices = DT.canvas.store.credentials.filter((credential) => credential.type === field.credentialType);
        const missing = fieldValue && !choices.some((credential) => credential.name === fieldValue);
        const options = [{ value: '', label: choices.length ? 'Choose a credential' : 'No credential yet' }, ...choices.map(({ name }) => ({ value: name, label: name })), ...(missing ? [{ value: fieldValue, label: `${fieldValue} (does not exist)` }] : [])];
        return wrap(`${label}<div class="ui-credential-row">${dropdown(options, fieldValue || '', common)}${fieldValue && !missing ? '<button type="button" class="ui-button" data-credential-edit>Edit</button>' : ''}<button type="button" class="ui-button" data-credential-new>New</button></div>`);
      }
      case 'number': return wrap(`${label}<input class="ui-input" type="number" min="0" ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}">`);
      case 'password': return wrap(`${label}<div class="ui-input-wrap"><input class="ui-input" type="password" autocomplete="off" ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}"><button type="button" class="ui-input-action" data-reveal aria-label="Show value">${icon('eye')}</button></div>`);
      case 'toggle': return wrap(`<label class="ui-toggle"><input type="checkbox" ${common} ${fieldValue ? 'checked' : ''}><span class="ui-toggle-track"></span><span>${escapeHtml(field.label)}</span></label>`);
      case 'code': return wrap(`${label}<textarea class="ui-input is-mono" rows="${Math.min(8, Math.max(3, String(fieldValue ?? '').split('\n').length + 1))}" spellcheck="false" data-variables ${common} ${placeholder}>${escapeHtml(fieldValue ?? '')}</textarea><p class="ui-hint">Type <code>$</code> to insert a parameter or earlier output.</p>`, node.type === 'script' ? '' : valueNotes);
      case 'expression': {
        /* Which task to run is picked from the saved ones, and a connector reads the choices of a field from its service
           when the field is entered, rather than a person typing an id from memory. */
        const savedTasks = node.type === 'linkedTask' && field.key === 'name'
          ? DT.canvas.store.tasks.filter((task) => task.name !== DT.canvas.store.task.name).map((task) => `<option value="${escapeHtml(task.name)}">`).join('')
          : null;
        const suggestions = (field.files ? DT.canvas.store.filesBefore(node.id).map((name) => ({ value: name, label: name })) : field.suggestions)
          ?.map((choice) => `<option value="${escapeHtml(choice.value)}" label="${escapeHtml(choice.label)}">`).join('');
        const choices = savedTasks ?? suggestions ?? (field.choices ? '' : null);
        const list = choices === null ? '' : `<datalist id="${inputId}-choices">${choices}</datalist>`;
        const control = field.multiline
          ? `<textarea class="ui-input is-mono" rows="3" spellcheck="false" data-variables ${common} ${placeholder}>${escapeHtml(fieldValue ?? '')}</textarea>`
          : `<input class="ui-input is-mono" spellcheck="false" autocomplete="off" data-variables ${list ? `list="${inputId}-choices"` : ''} ${field.choices ? 'data-choices' : ''} ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}">`;
        const addressNotes = ADDRESS_FIELDS.includes(field.key)
          ? Object.entries(ADDRESS_NOTES).map(([name, note]) => `<p class="ui-hint" data-address-note="${name}" data-for="${field.key}" hidden>${escapeHtml(note.text)}</p>`).join('')
          : '';
        return wrap(`${label}${control}${list}${addressNotes}${field.choices ? `<p class="ui-hint" data-choices-note="${field.key}" hidden></p>` : ''}`, valueNotes);
      }
      case 'form': {
        /* Saved values show by their key until the fields are loaded, which gives their labels and any new ones. */
        const values = formValues(fieldValue);
        const fields = loadedForms.get(`${node.id}.${field.key}`) || Object.keys(values).map((key) => ({ key, label: key }));
        return `<fieldset class="ui-field ui-list" data-form="${field.key}"><legend class="ui-label">${escapeHtml(field.label)}${field.required ? '' : '<span class="ui-optional">optional</span>'}</legend>
          ${fields.map((one) => `<label class="ui-label" for="${inputId}-${escapeHtml(one.key)}">${escapeHtml(one.label)}</label><input class="ui-input is-mono" id="${inputId}-${escapeHtml(one.key)}" spellcheck="false" autocomplete="off" data-variables data-form-key="${escapeHtml(one.key)}" value="${escapeHtml(values[one.key] ?? '')}">`).join('')}
          <button type="button" class="ui-button" data-form-load>${icon('download')}${fields.length ? 'Load the fields again' : 'Load the fields'}</button>${hint}</fieldset>`;
      }
      case 'map': {
        const rows = fieldValue || [];
        const cell = (column, row, index) => (column.options
          ? dropdown(column.options, row[column.key], `aria-label="${escapeHtml(column.label)}" data-map-index="${index}" data-map-part="${column.key}"`)
          : `<input class="ui-input is-mono" data-variables placeholder="${escapeHtml(column.label)}" aria-label="${escapeHtml(column.label)}" data-map-index="${index}" data-map-part="${column.key}" value="${escapeHtml(row[column.key] ?? '')}">`);
        return `<fieldset class="ui-field ui-list" data-map="${field.key}"><legend class="ui-label">${escapeHtml(field.label)}${field.required ? '' : '<span class="ui-optional">optional</span>'}</legend>${rows.map((row, index) => `<div class="ui-list-row">${field.columns.map((column) => cell(column, row, index)).join('')}<button type="button" class="ui-icon-button" data-map-remove="${index}" aria-label="Remove">${icon('x')}</button></div>`).join('')}<button type="button" class="ui-link-button" data-map-add>${icon('plus')}Add</button></fieldset>`;
      }
      case 'address': {
        /* The server gives where services reach it, which a proxy can make differ from this page's address. */
        const address = field.defaultValue.replace('{suffix}', node.config.triggerSuffix);
        return addressRow(field.label, address.startsWith('http') ? address : window.location.origin + address, `${field.hint} Works once the task is saved with its trigger on.`);
      }
      default: return wrap(`${label}<input class="ui-input ${field.mono ? 'is-mono' : ''}" autocomplete="off" spellcheck="false" ${common} ${placeholder} value="${escapeHtml(fieldValue ?? '')}">`);
    }
  }

  function webhookUrl(task, node) {
    return `${window.location.origin}/webhook/${encodeURIComponent(task.name)}/Webhook/${node.config.triggerSuffix}/${node.config.path || ''}`;
  }

  /* An address that a service calls, with a button that copies it. */
  const addressRow = (label, url, hint) => `<div class="ui-field"><span class="ui-label">${escapeHtml(label)}</span><div class="ui-copy-row"><code data-address>${escapeHtml(url)}</code><button type="button" class="ui-icon-button" data-copy="${escapeHtml(url)}" aria-label="Copy address">${icon('copy')}</button></div><p class="ui-hint">${escapeHtml(hint)}</p></div>`;

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
      ${DT.values.conditionBuilder(node)}
      ${node.type === 'start' && node.config.trigger === 'webhook' ? addressRow('Call this URL', webhookUrl(store.task, node), 'Works once the task is saved with its trigger on.') : ''}
      ${canFail ? `<details class="ui-disclosure" ${hasFailureSettings ? 'open' : ''}><summary>${icon('chevronRight')}If this step fails${node.config.retries ? `<span class="ui-pill">${node.config.retries} ${node.config.retries === 1 ? 'retry' : 'retries'}</span>` : ''}</summary><div class="ui-field-grid">${FAILURE_FIELDS.map((field) => renderField(field, node)).join('')}</div></details>` : ''}
      ${advancedFields.length ? `<details class="ui-disclosure" ${node.config.scriptLanguage === 'JAVASCRIPT' || node.config.baseUrl ? 'open' : ''}><summary>${icon('chevronRight')}Advanced</summary><div class="ui-field-grid">${advancedFields.map((field) => renderField(field, node)).join('')}</div></details>` : ''}
      <p class="ui-backend-name">${escapeHtml(action.backend)}</p>`;
  }


  function updatePreviews(root, nodeId) {
    DT.values.refresh(root, nodeId);
    updateAddressNotes(root);
  }

  /* Shows the notes that apply to each address as it is typed, in a step or in its credential. */
  function updateAddressNotes(root) {
    root.querySelectorAll('[data-address-note]').forEach((note) => {
      note.hidden = !ADDRESS_NOTES[note.dataset.addressNote].applies(root.querySelector(`[data-field="${note.dataset.for}"]`)?.value ?? '');
    });
  }

  /* Renders a step's fields into `root` and keeps them bound to the store. */
  function mountForm(root, store, nodeId) {
    const render = () => {
      const node = DT.nodeById(store.task, nodeId);
      if (!node) { root.innerHTML = ''; return; }
      root.innerHTML = renderForm(node, store);
      updatePreviews(root, nodeId);
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
        if (event.type === 'change' && (target.matches('[data-dropdown]') || target.type === 'checkbox') && (hasConditionalFields() || target.dataset.field === 'credential')) render();
        updatePreviews(root, nodeId);
        const urlElement = node.config.trigger === 'webhook' && root.querySelector('[data-address]');
        if (urlElement) {
          urlElement.textContent = webhookUrl(store.task, node);
          urlElement.nextElementSibling.dataset.copy = urlElement.textContent;
        }
      } else if (target.dataset.formKey !== undefined) {
        const key = target.closest('[data-form]').dataset.form;
        const values = { ...formValues(node.config[key]), [target.dataset.formKey]: target.value };
        store.updateConfig(nodeId, key, JSON.stringify(Object.fromEntries(Object.entries(values).filter(([, value]) => value !== ''))));
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
      if (button.dataset.formLoad !== undefined) {
        const key = button.closest('[data-form]').dataset.form;
        store.connectorFields(node, key).then((fields) => { loadedForms.set(`${nodeId}.${key}`, fields); render(); }, (error) => toast(error.message, { tone: 'danger' }));
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
    /* A list is read when its field is entered, and again once a setting it is read with has changed. What is
       still missing is said under the field, rather than asked of the service. */
    const readLists = new Map();
    const onFocus = async (event) => {
      const input = event.target;
      if (input.dataset.choices === undefined) return;
      const node = currentNode();
      const visible = (key) => ACTIONS[node.type].fields.find((field) => field.key === key && DT.fieldVisible(field, node.config));
      const { dependsOn } = visible(input.dataset.field);
      const note = root.querySelector(`[data-choices-note="${input.dataset.field}"]`);
      const missing = node.config.credential ? dependsOn.find((key) => !node.config[key]) : 'credential';
      note.hidden = !missing;
      note.textContent = missing ? `Choose ${visible(missing)?.label ?? missing} first, to pick from a list.` : '';
      if (missing) return;
      const readWith = JSON.stringify([input.dataset.field, node.config.credential, ...dependsOn.map((key) => node.config[key])]);
      try {
        if (!readLists.has(readWith)) readLists.set(readWith, store.connectorChoices(node, input.dataset.field));
        const choices = await readLists.get(readWith);
        input.list.innerHTML = choices.map((choice) => `<option value="${escapeHtml(choice.value)}" label="${escapeHtml(choice.label)}">`).join('');
      } catch (error) {
        readLists.delete(readWith);
        note.hidden = false;
        note.textContent = error.message;
      }
    };
    const onVariableKey = attachVariableMenu(root, store);
    root.addEventListener('focusin', onFocus);
    root.addEventListener('input', onInput);
    root.addEventListener('change', onInput);
    root.addEventListener('click', onClick);
    render();
    return { render, dispose() { root.removeEventListener('focusin', onFocus); root.removeEventListener('input', onInput); root.removeEventListener('change', onInput); root.removeEventListener('click', onClick); onVariableKey(); } };
  }

  /* The open list of a dropdown. A long list is narrowed by the words typed. A grouped one is headed by the group in a
     "Group: Choice" label, and keeps the order of the list inside each group. */
  function openPicker(anchor, options, current, onPick, isGrouped = false) {
    const groupOf = (option) => (isGrouped && option.label.includes(': ') ? option.label.slice(0, option.label.indexOf(': ')) : '');
    const sorted = [...options].sort((first, second) => groupOf(first).localeCompare(groupOf(second)));
    let shown = sorted;
    let activeIndex = Math.max(0, sorted.findIndex((option) => option.value === current));
    const listHtml = (query) => {
      const words = query.toLowerCase().split(/\s+/).filter(Boolean);
      shown = sorted.filter((option) => words.every((word) => option.label.toLowerCase().includes(word)));
      activeIndex = Math.min(activeIndex, Math.max(0, shown.length - 1));
      if (!shown.length) return `<p class="ui-empty">Nothing matches “${escapeHtml(query)}”.</p>`;
      return shown.map((option, index) => {
        const group = groupOf(option);
        const heading = group && group !== groupOf(shown[index - 1] ?? { label: '' }) ? `<h3>${escapeHtml(group)}</h3>` : '';
        const name = group ? option.label.slice(group.length + 2) : option.label;
        const isCurrent = option.value === current;
        return `${heading}<button type="button" role="option" aria-selected="${isCurrent}" class="ui-option ${index === activeIndex ? 'is-active' : ''}" data-index="${index}"><span class="ui-palette-label">${escapeHtml(name)}</span>${isCurrent ? icon('check') : ''}</button>`;
      }).join('');
    };
    const search = options.length > LONG_LIST ? `<div class="ui-palette-search">${icon('search')}<input placeholder="Search" aria-label="Search the choices" autocomplete="off" spellcheck="false"></div>` : '';
    const { element, close } = openPopover(anchor, `${search}<div class="ui-palette-list" role="listbox" tabindex="-1">${listHtml('')}</div>`, { className: 'ui-picker-menu', minWidth: Math.max(anchor.offsetWidth, 180) });
    const input = element.querySelector('input');
    const list = element.querySelector('.ui-palette-list');
    const draw = () => { list.innerHTML = listHtml(input?.value ?? ''); list.querySelector('.is-active')?.scrollIntoView({ block: 'nearest' }); };
    const pick = (option) => { if (!option) return; close(); onPick(option.value); };
    input?.addEventListener('input', () => { activeIndex = 0; draw(); });
    element.addEventListener('keydown', (event) => {
      if ((event.key === 'ArrowDown' || event.key === 'ArrowUp') && shown.length) {
        event.preventDefault();
        activeIndex = (activeIndex + (event.key === 'ArrowDown' ? 1 : -1) + shown.length) % shown.length;
        draw();
      } else if (event.key === 'Enter') { event.preventDefault(); pick(shown[activeIndex]); }
    });
    list.addEventListener('click', (event) => { const button = event.target.closest('[data-index]'); if (button) pick(shown[Number(button.dataset.index)]); });
    list.querySelector('.is-active')?.scrollIntoView({ block: 'center' });
    (input ?? list).focus();
  }

  /* The ways a task can start, built in and from every app, in one searchable list. */
  function openTriggerPicker(store, anchor) {
    const builtIn = ACTIONS.start.fields.find((field) => field.key === 'trigger').options
      .filter((option) => option.value !== 'manual' && option.value !== DT.APP_TRIGGER)
      .map((option) => ({ value: option.value, label: `Built in: ${option.label}` }));
    const apps = Object.entries(ACTIONS).filter(([, action]) => action.category === 'starts').map(([type, action]) => ({ value: type, label: `Apps: ${action.label}` }));
    openPicker(anchor, [...builtIn, ...apps], null, (value) => (ACTIONS[value] ? store.addStep(value) : store.addTrigger(value)), true);
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
      menu.innerHTML = suggestions.map((item, index) => `<button type="button" class="ui-option ${index === activeIndex ? 'is-active' : ''}" data-index="${index}"><span>${escapeHtml(item.label)}</span><small>${escapeHtml(item.detail)}</small></button>`).join('');
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
        menu.className = 'ui-popover ui-menu ui-var-menu';
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
      list.innerHTML = groups.length ? groups.map((group) => `<div class="ui-palette-group"><h3>${escapeHtml(group.label)}</h3>${group.items.map((item) => { position += 1; return `<button type="button" role="option" class="ui-option ${position === activeIndex ? 'is-active' : ''}" data-index="${position}" ${item.category ? `data-category="${item.category}"` : ''}><span class="ui-palette-icon">${icon(item.icon)}</span><span class="ui-palette-label">${escapeHtml(item.label)}</span>${item.detail ? `<small>${escapeHtml(item.detail)}</small>` : ''}${item.shortcut ? kbd(item.shortcut) : ''}</button>`; }).join('')}</div>`).join('') : `<p class="ui-empty">Nothing matches “${escapeHtml(input.value)}”.</p>`;
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

  function openRunDialog(store, onRun, { onFixIssue, values, preview = false } = {}) {
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
    const previewChoices = preview ? DT.values.previewChoices(store.task) : '';
    if (!store.task.parameters.length && !previewChoices) { onRun({}); return; }
    const lastValues = values || store.runs[0]?.parameters || {};
    const dialog = openDialog(`${dialogHeader(`${preview ? 'Preview' : 'Run'} “${store.task.name}”`, preview ? 'Choose what each step does in this preview.' : 'Choose the values for this run.')}
      <form method="dialog" class="ui-form" novalidate>
        ${store.task.parameters.map((parameter) => `<div class="ui-field"><label class="ui-label" for="run-${escapeHtml(parameter.name)}">${escapeHtml(parameter.name)}<span class="ui-optional">${parameter.required ? 'required' : 'optional'}</span></label><input class="ui-input" id="run-${escapeHtml(parameter.name)}" name="${escapeHtml(parameter.name)}" data-required="${parameter.required ? 1 : ''}" autocomplete="off" spellcheck="false" value="${escapeHtml(lastValues[parameter.name] ?? parameter.defaultValue ?? '')}">${parameter.description ? `<p class="ui-hint">${escapeHtml(parameter.description)}</p>` : ''}</div>`).join('')}
        ${previewChoices}
        <footer class="ui-dialog-foot"><button type="button" class="ui-button" data-dialog-close>Cancel</button><button type="submit" class="ui-button is-primary">${icon(preview ? 'eye' : 'play')}${preview ? 'Preview' : 'Run'} ${kbd(`${modKey}↵`)}</button></footer>
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
    form.querySelector('input')?.select();
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
    const dialog = openDialog(`${dialogHeader('Tasks', 'Open any saved version, or delete a task.')}<div class="ui-task-list">${store.tasks.length ? store.tasks.map((task) => `<div class="ui-task-row ${task.id === store.task.id ? 'is-current' : ''}" data-task="${escapeHtml(task.id)}"><span><b>${escapeHtml(task.name)}</b>${dropdown(task.versions.map((version) => ({ value: version.checksum, label: versionLabel(version) })), task.versions[0].checksum, `aria-label="Version of ${escapeHtml(task.name)}"`)}</span><button type="button" class="ui-button" data-open>Open</button><button type="button" class="ui-button" data-delete aria-label="Delete ${escapeHtml(task.name)}">${icon('trash')}Delete</button></div>`).join('') : '<p class="ui-empty">No saved tasks yet.</p>'}</div><footer class="ui-dialog-foot"><button type="button" class="ui-button" data-import>${icon('upload')}Import…</button><span class="ui-spacer"></span><button type="button" class="ui-button is-primary" data-new>${icon('plus')}New task</button></footer>`, { className: 'is-medium' });
    dialog.addEventListener('click', async (event) => {
      const row = event.target.closest('[data-task]');
      if (event.target.closest('[data-open]')) {
        dialog.close();
        store.openVersion(row.querySelector('[data-dropdown]').value).catch((error) => toast(error.message, { tone: 'danger' }));
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
    dialog.addEventListener('input', () => updateAddressNotes(dialog));
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
    /* There is a kind for every service, so the kind is searched for by name, and the kinds already in use come first. */
    const used = new Set(store.credentials.map((credential) => credential.type));
    const kinds = [...new Set(Object.values(ACTIONS).flatMap((action) => action.fields).filter((field) => field.type === 'credential').map((field) => field.credentialType))]
      .map((type) => [typeLabel(type), type])
      .sort(([first, firstType], [second, secondType]) => used.has(secondType) - used.has(firstType) || first.localeCompare(second));
    const kindByLabel = new Map(kinds);
    const dialog = openDialog(`${dialogHeader('Credentials', 'What steps sign in with. Tasks only hold the name, so they can be shared and exported safely.')}
      <div class="ui-task-list">${store.credentials.length ? store.credentials.map((credential) => `<div class="ui-task-row" data-credential="${escapeHtml(credential.name)}"><span><b>${escapeHtml(credential.name)}</b><small>${escapeHtml(typeLabel(credential.type))}. ${escapeHtml(usedBy(credential))}</small></span><button type="button" class="ui-button" data-edit>Edit</button><button type="button" class="ui-button" data-delete>Delete</button></div>`).join('') : '<p class="ui-hint">No credentials yet. Create one here, or from a step that needs it.</p>'}</div>
      <footer class="ui-dialog-foot"><input class="ui-input" id="credential-kind" list="credential-kinds" autocomplete="off" placeholder="Kind, such as Slack" aria-label="Kind of credential"><datalist id="credential-kinds">${kinds.map(([label]) => `<option value="${escapeHtml(label)}">`).join('')}</datalist><button type="button" class="ui-button is-primary" data-new>New credential</button><button type="button" class="ui-button" data-dialog-close>Close</button></footer>`);
    dialog.addEventListener('click', (event) => {
      const name = event.target.closest('[data-credential]')?.dataset.credential;
      const credential = store.credentials.find((candidate) => candidate.name === name);
      if (event.target.closest('[data-new]')) {
        const type = kindByLabel.get(dialog.querySelector('#credential-kind').value.trim());
        if (!type) { toast('Choose a kind from the list, such as Slack.', { tone: 'danger' }); return; }
        dialog.close();
        openCredentialDialog(store, credentialFieldOf(type), null);
      }
      if (event.target.closest('[data-edit]')) { dialog.close(); openCredentialDialog(store, credentialFieldOf(credential.type), credential); }
      if (event.target.closest('[data-delete]') && confirm(`Delete ${name}? This cannot be undone.`)) {
        dialog.close();
        store.deleteCredential(name).then(() => toast(`Deleted ${name}`), (error) => toast(error.message, { tone: 'danger' }));
      }
    });
    dialog.querySelector('#credential-kind').addEventListener('keydown', (event) => {
      if (event.key === 'Enter') dialog.querySelector('[data-new]').click();
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

  function openMoveToTaskDialog(store, steps) {
    const stepIds = steps.map((node) => node.id);
    const notes = store.moveToTaskNotes(stepIds).map((note) => `<p class="ui-note is-warn">${icon('alert')}${escapeHtml(note)}</p>`).join('');
    const dialog = openDialog(`${dialogHeader('Move to a new task', `${countOf(steps)} become${steps.length === 1 ? 's' : ''} a task that one step here runs. Other tasks can run it too.`)}<form method="dialog" class="ui-form"><div class="ui-field"><label class="ui-label" for="move-to-task-name">Name of the new task</label><input class="ui-input" id="move-to-task-name" autocomplete="off" required><p class="ui-hint">Each value the steps read from the rest of this task goes in as a parameter.</p></div>${notes}<footer class="ui-dialog-foot"><button type="button" class="ui-button" data-dialog-close>Cancel</button><button type="submit" class="ui-button is-primary">Move</button></footer></form>`, { className: 'is-small' });
    const nameInput = dialog.querySelector('#move-to-task-name');
    nameInput.focus();
    dialog.querySelector('form').addEventListener('submit', () => {
      const name = nameInput.value.trim().replace(/\s+/g, '_');
      store.moveStepsToTask(stepIds, name)
        .then(() => toast(`Moved ${countOf(steps)} to ${name}`, { actionLabel: 'Open task', onAction: () => store.switchTask(name) }), (error) => toast(error.message, { tone: 'danger' }));
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
      [`${modKey}Z`, 'Undo'], [`${modKey}⇧Z`, 'Redo'],
      ...stepActions(shortcuts.store, shortcuts.handlers).flat().filter((action) => action.shortcut).map((action) => [action.shortcut, action.label]),
      ['⇧ click', 'Add a step to the selection'], ['P', 'Task parameters'], ['O', 'Open a task'], ['esc', 'Close or clear selection'], ['?', 'This list'],
    ];
    openDialog(`${dialogHeader('Keyboard shortcuts')}<dl class="ui-shortcuts">${rows.map(([keys, label]) => `<div><dt>${escapeHtml(label)}</dt><dd>${kbd(keys)}</dd></div>`).join('')}</dl>`, { className: 'is-small', animate: false });
  }

  const saveStatus = (store) => (store.review ? 'Proposal, not running' : { saving: 'Saving…', saved: 'Saved', unsaved: store.showingOlderVersion ? 'Older version, not current' : store.task.checksum ? 'Unsaved changes' : 'Not saved yet' }[store.saveState]);

  /* ---------- Step actions ---------- */

  const countOf = (nodes) => (nodes.length === 1 ? `“${nodes[0].name}”` : `${nodes.length} steps`);
  const pressed = (key, { withMod = false } = {}) => (event) => (event.metaKey || event.ctrlKey) === withMod && event.key.toLowerCase() === key;

  /* Everything a person can do with steps, in the groups the menu shows. The menu, the buttons over a tile, the
     buttons of the step panel and the keys all read this list, so an action is written once. */
  function stepActions(store, handlers, nodeIds = store.selectedIds) {
    const steps = nodeIds.map((nodeId) => DT.nodeById(store.task, nodeId)).filter((node) => node && node.type !== 'start');
    const onlyStep = nodeIds.length === 1 ? steps[0] : null;
    const allDeactivated = steps.length > 0 && steps.every(DT.isDeactivated);
    const stepIds = steps.map((node) => node.id);
    /* While a step runs on its own, every action waits for it. */
    const waits = store.testingStepId !== null;
    return [
      [
        { id: 'run-step', label: 'Run step', icon: 'play', applies: !!onlyStep, run: () => handlers.testStep() },
        { id: 'preview-step', label: 'Preview step', icon: 'eye', applies: !!onlyStep, run: () => handlers.testStep({ preview: true }) },
        { id: 'rename', label: 'Rename', icon: 'pen', applies: !!onlyStep, run: () => handlers.renameStep() },
        { id: 'replace', label: 'Replace…', icon: 'swap', applies: !!onlyStep, run: () => handlers.replaceStep(onlyStep.id) },
        { id: 'move-to-task', label: 'Move to a new task…', icon: 'workflow', applies: store.canMoveToTask(stepIds), run: () => openMoveToTaskDialog(store, steps) },
      ],
      [
        { id: 'deactivate', label: allDeactivated ? 'Activate' : 'Deactivate', icon: 'power', shortcut: 'D', matches: pressed('d'), applies: steps.length > 0, run: () => store.setDeactivated(stepIds, !allDeactivated) },
        { id: 'duplicate', label: 'Duplicate', icon: 'duplicate', shortcut: `${modKey}D`, matches: pressed('d', { withMod: true }), applies: steps.length > 0, run: () => store.duplicateNodes(stepIds) },
        /* The keys copy and paste through the browser's own copy and paste events, so only the menu has to ask
           the browser for the clipboard. */
        { id: 'copy', label: 'Copy', icon: 'copy', shortcut: `${modKey}C`, applies: steps.length > 0, run: () => navigator.clipboard.writeText(store.copiedSteps(stepIds)).then(() => toast(`Copied ${countOf(steps)}`), () => toast(`The browser keeps the clipboard closed here. Press ${modKey}C to copy.`)) },
        { id: 'paste', label: 'Paste', icon: 'paste', shortcut: `${modKey}V`, applies: true, run: () => navigator.clipboard.readText().then((text) => pasteSteps(store, handlers, text), () => toast(`The browser keeps the clipboard closed here. Press ${modKey}V to paste.`)) },
      ],
      [
        { id: 'select-all', label: 'Select all', icon: 'selectAll', shortcut: `${modKey}A`, matches: pressed('a', { withMod: true }), applies: true, run: () => store.selectAll() },
        { id: 'clear-selection', label: 'Clear selection', icon: 'x', applies: nodeIds.length > 1, run: () => store.select(null) },
        ...(handlers.extraCommands || []).filter((command) => command.id === 'tidy').map((command) => ({ ...command, applies: true })),
      ],
      [
        { id: 'delete', label: 'Delete', icon: 'trash', shortcut: '⌫', matches: (event) => pressed('delete')(event) || pressed('backspace')(event), applies: nodeIds.some((nodeId) => nodeId !== DT.rootNode(store.task).id), run: () => removeWithUndo(store, nodeIds) },
      ],
    ].map((group) => group.map((action) => ({ ...action, waits })));
  }

  function runStepAction(store, handlers, actionId) {
    stepActions(store, handlers).flat().find((action) => action.id === actionId && action.applies && !action.waits)?.run();
  }

  /* A menu at the pointer has no control to hang from, so it hangs from a point that moves there. */
  let pointerAnchor = null;
  function anchorAt({ x, y }) {
    pointerAnchor ??= document.body.appendChild(Object.assign(document.createElement('span'), { className: 'ui-menu-point', title: 'Step actions' }));
    Object.assign(pointerAnchor.style, { left: `${x}px`, top: `${y}px` });
    return pointerAnchor;
  }

  /* Opens under a control, or at a point of the page. */
  function openStepMenu(store, handlers, anchor) {
    const groups = stepActions(store, handlers).map((group) => group.filter((action) => action.applies)).filter((group) => group.length);
    const { element, close } = openPopover(anchor instanceof Element ? anchor : anchorAt(anchor), `<div class="ui-menu">${groups.map((group) => group.map((action) => `<button type="button" class="ui-option" data-step-action="${action.id}" ${action.waits ? 'disabled' : ''}>${icon(action.icon)}<span>${escapeHtml(action.label)}</span>${action.shortcut ? kbd(action.shortcut) : ''}</button>`).join('')).join('<hr>')}</div>`);
    element.addEventListener('click', (event) => {
      const chosen = event.target.closest('[data-step-action]');
      if (!chosen) return;
      close();
      runStepAction(store, handlers, chosen.dataset.stepAction);
    });
  }

  function pasteSteps(store, handlers, text) {
    if (!store.pasteSteps(text, handlers.pastePosition()).length) toast('The clipboard holds no copied steps.');
  }

  function removeWithUndo(store, nodeIds) {
    const removed = store.removeNodes(nodeIds);
    toast(`Deleted ${countOf(removed)}`, { actionLabel: 'Undo', onAction: () => store.undo() });
  }

  /* ---------- Shortcuts ---------- */

  /* What the keys work on. The list of shortcuts reads its step rows from the same place. */
  const shortcuts = {};
  function bindShortcuts(store, handlers) {
    Object.assign(shortcuts, { store, handlers });
    const isTyping = (event) => event.target.matches?.('input, textarea, select, [contenteditable="true"]');
    /* The keys belong to an open dialog or menu until it closes. */
    const isCovered = () => !!document.querySelector('dialog[open]') || openPopovers.length > 0;
    document.addEventListener('keydown', (event) => {
      const modified = event.metaKey || event.ctrlKey;
      if (modified && event.key.toLowerCase() === 'k') { event.preventDefault(); if (!document.querySelector('dialog[open]')) handlers.palette(); return; }
      if (isCovered()) return;
      if (modified && event.key === 'Enter') { event.preventDefault(); handlers.run(); return; }
      if (modified && event.key.toLowerCase() === 's') { event.preventDefault(); store.save().then((message) => toast(message), (error) => toast(error.message, { tone: 'danger' })); return; }
      if (isTyping(event)) { if (event.key === 'Escape') event.target.blur(); return; }
      if (modified && event.key.toLowerCase() === 'z') { event.preventDefault(); if (event.shiftKey) store.redo(); else store.undo(); return; }
      const stepAction = stepActions(store, handlers).flat().find((action) => action.applies && action.matches?.(event));
      if (stepAction) { event.preventDefault(); if (!stepAction.waits) stepAction.run(); return; }
      if (modified) return;
      if (event.key === 'Escape') { handlers.escape ? handlers.escape() : store.select(null); return; }
      if (event.key === '?') { openShortcutsDialog(); return; }
      if (event.key.toLowerCase() === 'p') { openParametersDialog(store); return; }
      if (event.key.toLowerCase() === 'o') { openTaskDialog(store); return; }
      handlers.key?.(event);
    });
    /* With text marked on the page the key copies that text, as it does everywhere. */
    document.addEventListener('copy', (event) => {
      const copied = getSelection().isCollapsed && !isTyping(event) && !isCovered() && store.copiedSteps(store.selectedIds);
      if (!copied) return;
      event.preventDefault();
      event.clipboardData.setData('text/plain', copied);
      toast(`Copied ${countOf(JSON.parse(copied).nodes)}`);
    });
    document.addEventListener('paste', (event) => {
      if (isTyping(event) || isCovered()) return;
      event.preventDefault();
      pasteSteps(store, handlers, event.clipboardData.getData('text/plain'));
    });
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
      ${(step.missingValues || []).map((note) => `<p class="ui-note is-warn">${icon('alert')}${escapeHtml(note)}</p>`).join('')}
      ${step.held ? `<div class="ui-result is-held"><div class="ui-result-head"><span class="ui-label">What this step would send</span><span class="ui-pill">Not sent</span></div><pre>${escapeHtml(readableOutput(step.held))}</pre></div>${DT.values.heldAnswer(step)}` : ''}
      ${step.output ? `<div class="ui-result"><div class="ui-result-head"><span class="ui-label">Output</span>${navigator.clipboard ? `<button type="button" class="ui-link-button" data-copy-output>${icon('copy')}Copy</button>` : ''}</div><pre>${escapeHtml(readableOutput(step.output))}</pre></div>` : ''}`;
  }

  const STATE_GLYPHS = { success: 'check', failed: 'x', warning: 'alert', waiting: 'pause', stopped: 'stop' };
  const statusGlyph = (state) => `<span class="status-glyph is-${state}" role="img" aria-label="${state}">${STATE_GLYPHS[state] ? icon(STATE_GLYPHS[state]) : ''}</span>`;

  const RUN_STATUS_LABELS = { success: 'Succeeded', failed: 'Failed', warning: 'Finished with warnings', running: 'Running', waiting: 'Waiting for an answer', stopped: 'Stopped' };
  const runParameterSummary = (run) => Object.values(run.parameters).join(' · ');

  window.DT.ui = {
    statusGlyph, RUN_STATUS_LABELS, runParameterSummary,
    icon, escapeHtml, modKey, toast, openPopover, dropdown, mountForm, openPalette, openTriggerPicker,
    openRunDialog, openParametersDialog, openTaskDialog, openCredentialsDialog, openSaveAsDialog, exportTask, importTask, openShortcutsDialog,
    saveStatus, bindShortcuts, stepActions, runStepAction, openStepMenu, renderStepResult, formatDuration, formatAgo,
  };
}
