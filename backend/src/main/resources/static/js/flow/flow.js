/* Flow: soft tiles on an open canvas. A brand rail, frequent steps, a step library, and floating
   tools that give way to the run player while a run is open. */
export default function boot() {
  const { store, runner, view, wires, fitTarget, applyView, renderWires } = DT.canvas;
  const { ACTIONS, CATEGORIES, formatAgo, nodeById, rootNode } = DT;
  const { icon, escapeHtml, modKey, statusGlyph } = DT.ui;
  const core = DT.workbench;
  const byId = (id) => document.getElementById(id);
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const FREQUENT_STEPS = [['command', 'Command'], ['http', 'HTTP'], ['ssh', 'SSH'], ['script', 'Script'], ['if', 'If'], ['mysql', 'MySQL'], ['openAI', 'OpenAI']].filter(([type]) => ACTIONS[type]);
  const START_OPTIONS = [
    ['manual', 'play', 'Run by hand', 'Starts only when someone runs it.'],
    ['webhook', 'webhook', 'Webhook', 'Starts when a URL is called.'],
    ['interval', 'clock', 'Schedule', 'Starts on a repeating interval.'],
    ['activemq', 'server', 'Queue message', 'Starts when an ActiveMQ message arrives.'],
  ];

  let libraryOpen = false;
  let librarySearch = '';

  const railButton = (iconName, label, attributes, active, hint = label) => `<button type="button" class="rail-button ${active ? 'is-active' : ''}" ${attributes} title="${hint}">${icon(iconName)}<span>${label}</span></button>`;

  function renderRail() {
    byId('rail').innerHTML = `
      <button type="button" class="rail-mark" data-wb="tasks" title="Tasks">${core.MARK}<span>DopplerTask</span></button>
      ${railButton('workflow', 'Diagram', 'data-flow="diagram"', !core.isHistoryOpen())}
      ${railButton('folder', 'Tasks', 'data-wb="tasks"', false, 'Switch task, new, import or export')}
      ${railButton('history', 'Runs', 'data-wb="history"', core.isHistoryOpen(), 'Run history')}
      <span class="rail-space"></span>
      ${railButton('keyboard', 'Shortcuts', 'data-flow="shortcuts"', false, 'Keyboard shortcuts (?)')}`;
  }

  /* ---------- Frequent steps and library ---------- */

  function renderQuick() {
    const showing = libraryOpen && !store.selectedId;
    byId('quick').innerHTML = `<button type="button" class="quick-button is-add ${showing ? 'is-on' : ''}" data-flow="library" aria-expanded="${showing}" title="${showing ? 'Close the step library' : 'Open the step library'}"><span class="quick-circle">${icon(showing ? 'x' : 'plus')}</span><small>${showing ? 'Close' : 'All steps'}</small></button>
      ${FREQUENT_STEPS.map(([type, label]) => `<button type="button" class="quick-button library-item" data-type="${type}" data-category="${ACTIONS[type].category}" aria-label="Add ${escapeHtml(ACTIONS[type].label)}" title="${escapeHtml(ACTIONS[type].label)}: click to add after the selected or last step, or drag onto the canvas"><span class="quick-circle">${icon(ACTIONS[type].icon)}</span><small>${label}</small></button>`).join('')}`;
  }

  function libraryListHtml() {
    const query = librarySearch.trim().toLowerCase();
    const matches = (text) => !query || text.toLowerCase().includes(query);
    const starts = START_OPTIONS.filter(([, , label, detail]) => matches(`${label} ${detail}`));
    const groups = DT.stepsMatching(query).map(({ category, items }) => ({ category, items: items.map(({ type, action }) => [type, action]) }));
    if (!starts.length && !groups.length) return `<p class="ui-empty">No step matches “${escapeHtml(librarySearch)}”.</p>`;
    return `${starts.length ? `<details class="side-group" ${query ? 'open' : ''}><summary>${icon('chevronRight')}Starts when</summary>${starts.map(([value, iconName, label, detail]) => `<button type="button" class="side-item" data-start="${value}"><span class="side-icon">${icon(iconName)}</span><span><b>${label}</b><small>${detail}</small></span></button>`).join('')}</details>` : ''}
      ${groups.map(({ category, items }, index) => `<details class="side-group" data-category="${category.id}" ${query || index === 0 ? 'open' : ''}><summary>${icon('chevronRight')}${query ? '' : '<i class="side-swatch"></i>'}${escapeHtml(category.label)}</summary>${items.map(([type, action]) => `<button type="button" class="side-item library-item" data-type="${type}" data-category="${action.category}" title="Click to add after the last step, or drag onto the canvas"><span class="side-icon">${icon(action.icon)}</span><span><b>${escapeHtml(action.label)}</b><small>${escapeHtml(action.description)}</small></span></button>`).join('')}</details>`).join('')}`;
  }

  function renderSide() {
    const side = byId('side');
    side.hidden = !libraryOpen || !!store.selectedId;
    if (side.hidden || side.querySelector('#sideList')) return;
    side.innerHTML = `<label class="side-search">${icon('search')}<input id="sideSearch" placeholder="Search steps" aria-label="Search steps" autocomplete="off" spellcheck="false"></label><div class="side-card" id="sideList">${libraryListHtml()}</div>`;
    const input = byId('sideSearch');
    input.value = librarySearch;
    input.addEventListener('input', () => { librarySearch = input.value; byId('sideList').innerHTML = libraryListHtml(); });
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') byId('sideList').querySelector('.library-item')?.click();
    });
  }

  function setLibrary(open) {
    libraryOpen = open;
    /* The step panel takes the library's place, so the library needs it out of the way. */
    if (open) store.select(null);
    renderRail();
    renderQuick();
    renderSide();
  }

  /* ---------- Run pill and tools ---------- */

  function syncRunPill() {
    const last = store.runs[0];
    byId('runPill').innerHTML = `<button type="button" class="run-main" data-action="run" title="Run this task (${modKey}↵)"><span class="run-circle">${icon('play')}</span><b>Run</b></button>
      ${last
        ? `<button type="button" class="last-run" data-run="${last.id}" title="Open the last run">${statusGlyph(last.status)}<span><b>Last run</b><small>${formatAgo(last.startedAt)}</small></span></button>`
        : '<span class="last-run is-none"><span><b>Not run yet</b></span></span>'}`;
  }

  function syncParameters() {
    const count = store.task.parameters.length;
    byId('toolParameters').title = 'Values this task asks for when it starts (P)';
    byId('toolParameters').querySelector('span').textContent = count ? `Parameters (${count})` : 'Parameters';
  }

  const toolButton = (iconName, label, attributes, hint = label) => `<button type="button" class="tool-button" ${attributes} title="${hint}">${icon(iconName)}<span>${label}</span></button>`;

  byId('tools').innerHTML = `
    <button type="button" class="tool-button" data-flow="save" id="toolSave" title="Save a new version (${modKey}S)">${icon('check')}<span>Save</span></button>
    ${toolButton('filePlus', 'New', 'data-flow="new"', 'New task')}
    ${toolButton('download', 'Export', 'data-flow="export"', 'Export as JSON')}
    ${toolButton('upload', 'Import', 'data-flow="import"', 'Import from JSON')}
    <button type="button" class="tool-button is-main" data-action="parameters" id="toolParameters">${icon('sliders')}<span>Parameters</span></button>
    ${toolButton('undo', 'Undo', 'data-action="undo" id="barUndo"', `Undo (${modKey}Z)`)}
    ${toolButton('redo', 'Redo', 'data-action="redo" id="barRedo"', `Redo (${modKey}⇧Z)`)}
    ${toolButton('tidy', 'Tidy', 'data-view="tidy"', 'Tidy layout (T)')}`;

  byId('zoom').innerHTML = `
    <button type="button" class="zoom-button" data-view="zoom-out" aria-label="Zoom out" title="Zoom out">${icon('zoomOut')}</button>
    <button type="button" class="zoom-value-button" id="zoomValue" data-view="zoom-reset" title="Reset to 100%"></button>
    <button type="button" class="zoom-button" data-view="zoom-in" aria-label="Zoom in" title="Zoom in">${icon('zoomIn')}</button>
    <button type="button" class="zoom-fit" data-view="fit" title="Fit the whole task on screen (F)">${icon('fit')}<span>Fit</span></button>`;
  DT.canvas.afterView = () => { byId('zoomValue').textContent = `${Math.round(view.zoom * 100)}%`; };

  function renderReview() {
    const { review } = store;
    const host = byId('review');
    host.hidden = !review;
    if (!review) return;
    const count = (number, what) => `${number} ${number === 1 ? 'step' : 'steps'} ${what}`;
    const summary = review.isNewTask
      ? `a new task with ${count(review.added.size, '').trim()}`
      : `a change: ${[count(review.added.size, 'added'), count(review.changed.size, 'changed'), count(review.removed.length, 'removed')].join(', ')}`;
    host.innerHTML = `<div><b>An agent proposes ${escapeHtml(summary)}.</b><small>${review.removed.length ? `Removed: ${escapeHtml(review.removed.join(', '))}. ` : ''}Nothing here runs until you approve it.</small></div>
      <button type="button" class="ui-button" data-flow="reject">Reject</button>
      <button type="button" class="ui-button is-primary" data-flow="approve">${icon('check')}Approve</button>`;
  }

  byId('title').innerHTML = `
    <div class="bar-task">
      <input class="bar-name" id="barName" aria-label="Task name" spellcheck="false" autocomplete="off">
      <button type="button" class="ui-icon-button" data-wb="tasks" aria-label="Switch task" title="Switch task" aria-haspopup="menu">${icon('chevronDown')}</button>
    </div>
    <span class="title-save" id="barSave"></span>
    <span class="title-divider"></span>
    <div class="trigger" id="barTrigger"></div>`;
  core.bindTaskName(byId('barName'), core.syncTaskControls);

  /* ---------- Connections ---------- */

  /* Each connection takes the colour of the step it leaves, ends in an arrow, and keeps an insert
     button on it. Branch names stay on the tile, next to where the connection leaves. */
  DT.canvas.afterWires = () => {
    wires.querySelectorAll('.wire').forEach((wire) => {
      const source = nodeById(store.task, wire.dataset.from);
      if (source) wire.style.setProperty('--wire', source.type === 'start' ? 'var(--ui-accent)' : `var(--cat-${ACTIONS[source.type].category})`);
      const line = wire.querySelector('.wire-line');
      const route = line.getAttribute('d').trim();
      const begin = route.match(/^M(-?[\d.]+),(-?[\d.]+)/);
      const end = route.match(/(-?[\d.]+),(-?[\d.]+)$/);
      /* The arrow stops just short of the tile and the dot just clear of it, so neither is cut by an edge. */
      const arrow = document.createElementNS(SVG_NS, 'path');
      arrow.setAttribute('class', 'wire-arrow');
      arrow.setAttribute('d', `M${end[1] - 9},${end[2] - 4} L${end[1] - 2},${end[2]} L${end[1] - 9},${Number(end[2]) + 4} Z`);
      const dot = document.createElementNS(SVG_NS, 'circle');
      dot.setAttribute('class', 'wire-dot');
      dot.setAttribute('cx', Number(begin[1]) + 3);
      dot.setAttribute('cy', begin[2]);
      dot.setAttribute('r', 3);
      line.after(arrow, dot);
      /* The insert button sits on the line; removing the connection appears beside it on hover. */
      const add = wire.querySelector('.wire-tool.is-add');
      const cut = wire.querySelector('.wire-tool:not(.is-add)');
      const middle = Number(add.getAttribute('transform').match(/translate\((-?[\d.]+),/)[1]) + 12;
      const middleY = add.getAttribute('transform').match(/,(-?[\d.]+)\)/)[1];
      add.setAttribute('transform', `translate(${middle},${middleY})`);
      cut.setAttribute('transform', `translate(${middle + 24},${middleY})`);
      if (!add.querySelector('title')) {
        add.insertAdjacentHTML('afterbegin', '<title>Insert a step here</title>');
        cut.insertAdjacentHTML('afterbegin', '<title>Remove this connection</title>');
      }
    });
  };

  /* ---------- Wiring ---------- */

  function syncRunMode() {
    document.documentElement.classList.toggle('has-run', !!runner.run);
  }

  store.subscribe((change) => {
    if (change === 'task' || change === 'review') renderReview();
    if (change === 'run') { syncRunPill(); syncRunMode(); return; }
    if (change === 'selection') { renderQuick(); renderSide(); return; }
    if (change === 'meta') { syncParameters(); return; }
    if (change === 'task') { syncRunPill(); syncRunMode(); syncParameters(); renderSide(); }
    if (change === 'structure') renderSide();
  });

  /* A clicked step lands by itself, so say where it went and offer the way back. */
  function announceAddedStep(item) {
    const added = item && store.selectedNode;
    if (!added) return;
    const incoming = store.task.edges.find((edge) => edge.to === added.id);
    const previous = incoming && nodeById(store.task, incoming.from);
    DT.ui.toast(previous ? `Added after “${previous.name}”` : `Added “${added.name}”`, { actionLabel: 'Undo', onAction: () => store.undo() });
  }

  document.addEventListener('click', (event) => {
    const action = event.target.closest('[data-flow]')?.dataset.flow;
    /* With a step selected the panel covers the library, so this asks for the library rather than to close it. */
    if (action === 'library') setLibrary(!libraryOpen || !!store.selectedId);
    if (action === 'diagram') { store.select(null); if (core.isHistoryOpen()) byId('rail').querySelector('[data-wb="history"]').click(); setLibrary(false); }
    if (action === 'shortcuts') DT.ui.openShortcutsDialog();
    if (action === 'save') store.save().then((message) => DT.ui.toast(message), (error) => DT.ui.toast(error.message, { tone: 'danger' }));
    if (action === 'approve') store.approveProposal().then(() => DT.ui.toast('Approved. This version is now the current one.'), (error) => DT.ui.toast(error.message, { tone: 'danger' }));
    if (action === 'reject') store.rejectProposal().then(() => DT.ui.toast('Proposal rejected.'), (error) => DT.ui.toast(error.message, { tone: 'danger' }));
    if (action === 'new') { store.newTask(); byId('barName').select(); }
    if (action === 'export') DT.ui.exportTask(store);
    if (action === 'import') DT.ui.importTask(store);
    if (event.target.closest('[data-wb="history"]')) renderRail();
    announceAddedStep(event.target.closest('.library-item'));
    const start = event.target.closest('[data-start]');
    if (start) {
      const root = rootNode(store.task);
      store.updateConfig(root.id, 'trigger', start.dataset.start);
      store.select(null);
      store.select(root.id);
    }
  });

  core.start({ idle: () => '' });
  renderRail();
  renderQuick();
  syncRunPill();
  syncRunMode();
  core.syncTaskControls();
  syncParameters();
  store.loadPerson().catch(() => {});
  store.loadTaskList().then(store.openLastTask).catch((error) => DT.ui.toast(error.message, { tone: 'danger' }));
  renderWires();
  Object.assign(view, fitTarget());
  applyView();
}
