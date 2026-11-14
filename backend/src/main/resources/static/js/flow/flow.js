/* Flow: soft tiles on an open canvas. A brand rail, frequent steps, a step library, and floating
   tools that give way to the run player while a run is open. */
export default function boot() {
  const { store, runner, view, wires, fitTarget, applyView, renderWires } = DT.canvas;
  const { ACTIONS, formatAgo, nodeById, rootNode } = DT;
  const { icon, escapeHtml, modKey, statusGlyph } = DT.ui;
  const core = DT.workbench;
  const byId = (id) => document.getElementById(id);
  const SVG_NS = 'http://www.w3.org/2000/svg';
  const FREQUENT_STEPS = [['command', 'Command'], ['http', 'HTTP'], ['ssh', 'SSH'], ['script', 'Script'], ['if', 'If'], ['mysql', 'MySQL'], ['openAI', 'OpenAI']].filter(([type]) => ACTIONS[type]);
  /* The column beside the canvas holds one panel at a time: the step library or the AI, or for an empty task how it starts. */
  let panel = null;

  const railButton = (iconName, label, attributes, active, hint = label) => `<button type="button" class="rail-button ${active ? 'is-active' : ''}" ${attributes} title="${hint}">${icon(iconName)}<span>${label}</span></button>`;

  /* On the home page Runs lists every task's runs; in a task it opens that task's run history. */
  function renderRail() {
    const tab = DT.home?.tab();
    byId('rail').innerHTML = `
      <button type="button" class="rail-mark" data-go="tasks" title="Home">${core.MARK}<span>DopplerTask</span></button>
      ${railButton('folder', 'Tasks', 'data-go="tasks"', tab === 'tasks', 'All tasks')}
      ${railButton('workflow', 'Diagram', 'data-flow="diagram"', !tab && !core.isHistoryOpen(), 'The task you are working on')}
      ${railButton('history', 'Runs', tab ? 'data-go="runs"' : 'data-wb="history"', tab === 'runs' || (!tab && core.isHistoryOpen()), tab ? 'The latest runs of every task' : 'Run history of this task')}
      ${railButton('key', 'Credentials', 'data-go="credentials"', tab === 'credentials', 'What steps sign in with')}
      <span class="rail-space"></span>
      ${railButton('keyboard', 'Shortcuts', 'data-flow="shortcuts"', false, 'Keyboard shortcuts (?)')}
      ${store.person ? railButton('logOut', 'Sign out', 'data-flow="sign-out"', false, `Sign out ${escapeHtml(store.person.email)}`) : ''}`;
  }

  /* ---------- Frequent steps and library ---------- */

  function renderQuick() {
    const showing = panel === 'library' && !store.selectedId;
    const asking = panel === 'assistant' && !store.selectedId;
    byId('quick').innerHTML = `${store.assistantReady ? `<button type="button" class="quick-button is-ai ${asking ? 'is-on' : ''}" data-flow="assistant" aria-expanded="${asking}" title="${asking ? 'Close Build with AI' : 'Build or change this task by saying what it should do'}"><span class="quick-circle">${icon(asking ? 'x' : 'sparkles')}</span><small>${asking ? 'Close' : 'Ask AI'}</small></button>` : ''}<button type="button" class="quick-button is-add ${showing ? 'is-on' : ''}" data-flow="library" aria-expanded="${showing}" title="${showing ? 'Close the step library' : 'Open the step library'}"><span class="quick-circle">${icon(showing ? 'x' : 'plus')}</span><small>${showing ? 'Close' : 'All steps'}</small></button>
      ${FREQUENT_STEPS.map(([type, label]) => `<button type="button" class="quick-button library-item" data-type="${type}" data-category="${ACTIONS[type].category}" aria-label="Add ${escapeHtml(ACTIONS[type].label)}" title="${escapeHtml(ACTIONS[type].label)}: click to add after the selected or last step, or drag onto the canvas"><span class="quick-circle">${icon(ACTIONS[type].icon)}</span><small>${label}</small></button>`).join('')}`;
  }

  /* An empty task asks how it starts, in the same place the library opens. Choosing once is enough for that task. */
  let startsChosenFor = null;
  let startsShowApps = false;
  let sideSearch = '';
  let shownMode = null;
  const startsOpen = () => store.task.nodes.length === 1 && startsChosenFor !== store.task.id && !store.review;
  const sideMode = () => (store.selectedId ? null : panel ?? (startsOpen() ? 'starts' : null));

  const startItem = ({ value, label, icon: iconName, detail }, attributes = `data-start="${value}"`) => `<button type="button" class="ui-option side-item" ${attributes}><span class="side-icon">${icon(iconName)}</span><span><b>${escapeHtml(label)}</b><small>${escapeHtml(detail)}</small></span></button>`;
  const noMatch = (what) => `<p class="ui-empty">No ${what} matches “${escapeHtml(sideSearch)}”.</p>`;

  function libraryListHtml() {
    const query = sideSearch.trim().toLowerCase();
    const starts = DT.startChoices().builtIn.filter((choice) => !query || `${choice.label} ${choice.detail}`.toLowerCase().includes(query));
    const groups = DT.stepsMatching(query).map(({ category, items }) => ({ category, items: items.map(({ type, action }) => [type, action]) }));
    if (!starts.length && !groups.length) return noMatch('step');
    return `${starts.length ? `<details class="side-group" ${query ? 'open' : ''}><summary>${icon('chevronRight')}Starts when</summary>${starts.map((choice) => startItem(choice)).join('')}</details>` : ''}
      ${groups.map(({ category, items }, index) => `<details class="side-group" data-category="${category.id}" ${query || index === 0 ? 'open' : ''}><summary>${icon('chevronRight')}${query ? '' : '<i class="side-swatch"></i>'}${escapeHtml(category.label)}</summary>${items.map(([type, action]) => `<button type="button" class="ui-option side-item library-item" data-type="${type}" data-category="${action.category}" title="Click to add after the last step, or drag onto the canvas"><span class="side-icon">${icon(action.icon)}</span><span><b>${escapeHtml(action.label)}</b><small>${escapeHtml(action.description)}</small></span></button>`).join('')}</details>`).join('')}`;
  }

  function startsListHtml() {
    const { builtIn, apps, appOption } = DT.startChoices();
    const query = sideSearch.trim().toLowerCase();
    if (query) {
      const found = [...builtIn, ...apps].filter((choice) => `${choice.label} ${choice.detail}`.toLowerCase().includes(query));
      return found.length ? found.map((choice) => startItem(choice)).join('') : noMatch('trigger');
    }
    if (startsShowApps) return `<button type="button" class="side-back" data-starts-apps="false">${icon('chevronRight')}All the ways to start</button>${apps.map((choice) => startItem(choice)).join('')}`;
    return `${builtIn.map((choice) => startItem(choice)).join('')}${appOption && apps.length ? startItem({ ...appOption, label: `${appOption.label} (${apps.length} apps)` }, 'data-starts-apps="true"') : ''}`;
  }

  const refreshSideList = () => { byId('sideList').innerHTML = shownMode === 'starts' ? startsListHtml() : libraryListHtml(); };

  function renderSide() {
    const side = byId('side');
    const mode = sideMode();
    side.hidden = !mode;
    if (mode === shownMode) return;
    shownMode = mode;
    sideSearch = '';
    startsShowApps = false;
    if (!mode) { side.innerHTML = ''; return; }
    if (mode === 'assistant') { DT.assistant.mount(side); return; }
    side.innerHTML = `${mode === 'starts' ? '<div class="side-head"><h2>What starts this task?</h2><p>A trigger is the step that starts a task. You can change it at any time.</p></div>' : ''}
      <label class="side-search">${icon('search')}<input id="sideSearch" placeholder="${mode === 'starts' ? 'Search triggers and apps' : 'Search steps'}" aria-label="${mode === 'starts' ? 'Search triggers' : 'Search steps'}" autocomplete="off" spellcheck="false"></label><div class="side-card" id="sideList"></div>`;
    refreshSideList();
    const input = byId('sideSearch');
    input.addEventListener('input', () => { sideSearch = input.value; refreshSideList(); });
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter') byId('sideList').querySelector('[data-start], .library-item')?.click();
    });
  }

  function setPanel(name) {
    panel = name;
    /* The step panel takes this column too, so an opened panel needs it out of the way. */
    if (name) store.select(null);
    renderRail();
    renderQuick();
    renderSide();
  }

  /* ---------- Run pill and tools ---------- */

  function syncRunPill() {
    const last = store.runs[0];
    byId('runPill').innerHTML = `<button type="button" class="run-main" data-action="run" title="Run this task (${modKey}↵)"><span class="run-circle">${icon('play')}</span><b>Run</b></button>
      <button type="button" class="preview-main" data-action="preview" title="Go through the task with nothing sent, to see what every step would do">${icon('eye')}<b>Preview</b></button>
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
    host.innerHTML = `<div><b>An agent proposes ${escapeHtml(summary)}.</b><small>${review.removed.length ? `Removed: ${escapeHtml(review.removed.join(', '))}. ` : ''}Nothing here runs until you approve it. A preview shows what it would do, and sends nothing.</small></div>
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
    if (change === 'task' || change === 'review') { renderReview(); renderSide(); }
    if (change === 'run') { syncRunPill(); syncRunMode(); return; }
    if (change === 'selection') { renderQuick(); renderSide(); return; }
    if (change === 'meta') { syncParameters(); renderQuick(); renderSide(); return; }
    if (change === 'task') { syncRunPill(); syncRunMode(); syncParameters(); }
    if (change === 'structure') renderSide();
  });

  /* A clicked step lands by itself, so say where it went and offer the way back. */
  function announceAddedStep(item) {
    const added = item && store.selectedNode;
    if (!added) return;
    if (added.type === 'start') { DT.ui.toast(`The task ${added === rootNode(store.task) ? 'now' : 'also'} starts on ${ACTIONS[item.dataset.type].label}`, { actionLabel: 'Undo', onAction: () => store.undo() }); return; }
    const incoming = store.task.edges.find((edge) => edge.to === added.id);
    const previous = incoming && nodeById(store.task, incoming.from);
    DT.ui.toast(previous ? `Added after “${previous.name}”` : `Added “${added.name}”`, { actionLabel: 'Undo', onAction: () => store.undo() });
  }

  document.addEventListener('click', (event) => {
    const action = event.target.closest('[data-flow]')?.dataset.flow;
    /* With a step selected its panel covers this one, so the button asks for it rather than to close it. */
    if (action === 'library' || action === 'assistant') setPanel(panel !== action || store.selectedId ? action : null);
    if (action === 'diagram') { store.select(null); if (core.isHistoryOpen()) byId('rail').querySelector('[data-wb="history"]').click(); setPanel(null); }
    if (action === 'shortcuts') DT.ui.openShortcutsDialog();
    if (action === 'sign-out') store.signOut();
    if (action === 'save') store.save().then((message) => DT.ui.toast(message), (error) => DT.ui.toast(error.message, { tone: 'danger' }));
    if (action === 'approve') store.approveProposal().then(() => DT.ui.toast('Approved. This version is now the current one.'), (error) => DT.ui.toast(error.message, { tone: 'danger' }));
    if (action === 'reject') store.rejectProposal().then(() => DT.ui.toast('Proposal rejected.'), (error) => DT.ui.toast(error.message, { tone: 'danger' }));
    if (action === 'new') { store.newTask(); byId('barName').select(); }
    if (action === 'export') DT.ui.exportTask(store);
    if (action === 'import') DT.ui.importTask(store);
    if (event.target.closest('[data-wb="history"]')) renderRail();
    announceAddedStep(event.target.closest('.library-item'));
    const start = event.target.closest('[data-start]');
    if (start) { startsChosenFor = store.task.id; store.addStart(start.dataset.start); }
    const appsToggle = event.target.closest('[data-starts-apps]');
    if (appsToggle) { startsShowApps = appsToggle.dataset.startsApps === 'true'; refreshSideList(); }
  });

  core.start({ idle: () => '' });
  renderRail();
  renderQuick();
  renderSide();
  syncRunPill();
  syncRunMode();
  core.syncTaskControls();
  syncParameters();
  store.loadPerson().then(renderRail, () => {});
  store.loadAssistant().catch(() => {});
  DT.flow = { renderRail };
  renderWires();
  Object.assign(view, fitTarget());
  applyView();
}
