/* What Workbench and Flow share: task controls, the step panel, the run player and run history.
   A failure opens beside the settings that fix it, and rerunning is one click from there. */
export default function boot() {
  const { options, store, runner, revealSelectedNode } = DT.canvas;
  const { ACTIONS, formatDuration, formatAgo, nodeById, rootNode } = DT;
  const { icon, escapeHtml, modKey, statusGlyph, RUN_STATUS_LABELS, runParameterSummary, renderStepResult } = DT.ui;
  const isLive = () => !!runner.run && !runner.finished;
  const completedRuns = () => store.runs.filter((run) => run !== runner.run || runner.finished);
  const reachedSteps = () => (runner.run ? runner.run.steps.filter((step) => runner.cursorMs >= step.startMs) : []);
  const stepIsDone = (step) => runner.cursorMs >= step.startMs + step.durationMs;
  const seconds = (ms) => DT.formatDuration(ms);

  /* Drag anywhere on a track to move through the run. The track must survive pause. */
  function bindScrub(host, trackSelector) {
    host.addEventListener('pointerdown', (event) => {
      const track = event.target.closest(trackSelector);
      if (!track || !runner.run) return;
      runner.pause();
      const box = track.getBoundingClientRect();
      const seekTo = (clientX) => runner.seek(Math.max(0, Math.min(1, (clientX - box.left) / box.width)) * runner.run.durationMs);
      seekTo(event.clientX);
      const onMove = (moveEvent) => seekTo(moveEvent.clientX);
      const onUp = () => { document.removeEventListener('pointermove', onMove); document.removeEventListener('pointerup', onUp); };
      document.addEventListener('pointermove', onMove);
      document.addEventListener('pointerup', onUp);
    });
  }
  const byId = (id) => document.getElementById(id);

  /* DopplerTask's hexagon with circuit traces, drawn for small sizes. */
  const MARK = '<svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M15 2.5 22 6.6v10.8l-7 4.1-7-4.1V6.6z"/><path d="M2 8.5h8.5M2 12h11.5M2 15.5h8.5"/></svg>';

  let historyOpen = false;
  let testResult = null;
  let testing = false;
  let form = null;
  let lastDataField = null;
  let idleStrip = () => '';
  let onHistoryToggle = () => {};

  /* Browsing history should not keep opening the panel over what you are reading. */
  options.shouldOpenFailure = () => !historyOpen;
  options.closeHistory = () => setHistory(false);

  /* ---------- Task controls ---------- */

  const TRIGGER_ICONS = { webhook: 'webhook', schedule: 'clock', interval: 'clock', activemq: 'server', manual: 'play' };
  function triggerText(config) {
    if (config.trigger === 'webhook') return `${config.method || 'POST'} /${config.path || ''}`;
    if (config.trigger === 'schedule') return DT.scheduleText(config).replace(/^./, (letter) => letter.toUpperCase());
    if (config.trigger === 'interval') return `Every ${DT.intervalText(config)}`;
    if (config.trigger === 'activemq') return config.queue || 'Queue';
    return 'Run by hand';
  }

  function bindTaskName(input, onBlur) {
    /* The backend does not allow whitespace in a task name. */
    input.addEventListener('input', () => {
      const position = input.selectionStart;
      input.value = input.value.replace(/\s/g, '_');
      input.setSelectionRange(position, position);
      store.updateTask({ name: input.value });
    });
    input.addEventListener('blur', () => { if (!input.value) store.updateTask({ name: 'untitled_task' }); onBlur(); });
    input.addEventListener('keydown', (event) => { if (event.key === 'Enter') input.blur(); });
  }

  function syncTaskControls() {
    const name = byId('barName');
    if (document.activeElement !== name) name.value = store.task.name;
    document.title = `${store.task.name} · DopplerTask`;
    byId('barSave').textContent = DT.ui.saveStatus(store);
    byId('barUndo').disabled = !store.canUndo;
    byId('barRedo').disabled = !store.canRedo;
    const { config } = rootNode(store.task);
    const trigger = config.trigger || 'manual';
    const active = !!store.task.active;
    const host = byId('barTrigger');
    host.classList.toggle('is-off', trigger !== 'manual' && !active);
    host.innerHTML = `<button type="button" class="trigger-open" data-wb="trigger" title="Change how this task starts">${icon(TRIGGER_ICONS[trigger])}${trigger === 'manual' ? `<span>${triggerText(config)}</span>` : `<span>Trigger</span><code>${escapeHtml(triggerText(config))}</code>`}</button>
      ${trigger === 'manual' ? '' : `<label class="ui-toggle"><input type="checkbox" data-wb-trigger ${active ? 'checked' : ''}><span class="ui-toggle-track"></span><span>${active ? 'On' : 'Off'}</span></label>`}`;
  }

  function openTaskSwitcher(anchor) {
    const others = [
      ['filePlus', 'New task', () => { store.newTask(); byId('barName').select(); }],
      ['history', 'Versions and delete…', () => DT.ui.openTaskDialog(store)],
      ['copy', 'Save as…', () => DT.ui.openSaveAsDialog(store)],
      ['download', 'Export as JSON', () => DT.ui.exportTask(store)],
      ['upload', 'Import from JSON…', () => DT.ui.importTask(store)],
      ['keyboard', 'Keyboard shortcuts', () => DT.ui.openShortcutsDialog()],
      ...(store.person ? [['logOut', `Sign out ${store.person.email}`, () => store.signOut()]] : []),
    ];
    const { element, close } = DT.ui.openPopover(anchor, `<div class="ui-menu task-switcher">
      <div class="task-switcher-list">${store.tasks.map((task) => `<button type="button" class="is-task ${task.id === store.task.id ? 'is-current' : ''}" data-switch="${escapeHtml(task.id)}">${icon('workflow')}<span><b>${escapeHtml(task.name)}</b><small>${task.versions[0].proposed ? 'A proposal is waiting' : `${task.versions.length} ${task.versions.length === 1 ? 'version' : 'versions'}`}</small></span></button>`).join('')}</div>
      ${store.tasks.length ? '<hr>' : ''}
      ${others.map((item, index) => `<button type="button" data-other="${index}">${icon(item[0])}<span>${item[1]}</span></button>`).join('')}
      <hr>
      <label class="ui-toggle" title="Saves a marked version 30 seconds after the last change. Pauses while the task's triggers are on."><input type="checkbox" data-wb-autosave ${store.autoSave ? 'checked' : ''}><span class="ui-toggle-track"></span><span>Auto save</span></label>
    </div>`);
    element.querySelector('.is-task.is-current')?.scrollIntoView({ block: 'nearest' });
    /* Opened from the rail, it sits beside the rail rather than on top of it. */
    if (anchor.closest('.rail')) element.style.left = `${anchor.getBoundingClientRect().right + 12}px`;
    element.addEventListener('click', (event) => {
      const taskButton = event.target.closest('[data-switch]');
      const other = event.target.closest('[data-other]');
      if (taskButton) { close(); store.switchTask(taskButton.dataset.switch).catch((error) => DT.ui.toast(error.message, { tone: 'danger' })); }
      if (other) { close(); others[Number(other.dataset.other)][2](); }
    });
  }

  /* ---------- Step panel ---------- */

  /* What this step would see: the values just before it in the run on screen, or else the latest run that reached it. */
  function variablesBefore(nodeId) {
    const run = runner.run || store.runs[0];
    for (const candidate of [run, ...store.runs].filter(Boolean)) {
      const step = candidate.steps.findLast((candidateStep) => candidateStep.nodeId === nodeId);
      if (step) return step.variables;
    }
    return { parameters: { ...store.defaults, ...run?.parameters }, lastOutput: '', lastOutputVariables: {}, executionId: run?.id || '' };
  }

  /* What each trigger hands to the task, as parameters named by the backend. */
  const TRIGGER_DATA = {
    webhook: [['Request body', 'TRIGGER_body']],
    activemq: [['Queue message', 'TRIGGER_Message']],
  };

  function dataRows(node) {
    const values = variablesBefore(node.id);
    const rows = store.task.parameters.map((parameter) => ({ label: parameter.name, insert: `$parameters.get("${parameter.name}")`, value: values.parameters[parameter.name] ?? parameter.defaultValue }));
    store.task.nodes.filter((candidate) => candidate.type === 'setVariable').flatMap((candidate) => candidate.config.setVariableList || []).forEach((variable) => { if (variable.name) rows.push({ label: variable.name, insert: `$parameters.get("${variable.name}")`, value: values.parameters[variable.name] ?? '' }); });
    TRIGGER_DATA[rootNode(store.task).config.trigger]?.forEach(([label, name]) => rows.push({ label, insert: `$parameters.get("${name}")`, value: 'Set by the trigger' }));
    if (rootNode(store.task).id !== node.id) rows.push({ label: 'Previous output', insert: '$lastLog.output', value: values.lastOutput });
    Object.entries(store.outputsBefore(node.id)).forEach(([name, label]) => rows.push({ label, insert: `$lastLog.outputVariables.${name}`, value: values.lastOutputVariables?.[name]?.toString() }));
    rows.push({ label: 'Run ID', insert: '$executionId', value: values.executionId });
    return rows;
  }

  function renderData(node) {
    const host = byId('panelData');
    if (!host) return;
    const accepts = !!byId('panelForm').querySelector('[data-variables]');
    host.hidden = !accepts;
    if (!accepts) return;
    host.innerHTML = `<h3>Data you can use</h3><div class="data-list">${dataRows(node).map((row) => `<button type="button" class="data-row" data-insert="${escapeHtml(row.insert)}" title="Insert ${escapeHtml(row.insert)}"><b>${escapeHtml(row.label)}</b><code>${escapeHtml(String(row.value ?? '').split('\n')[0]) || 'No value yet'}</code></button>`).join('')}</div><p class="panel-hint">Click to insert at the cursor, or type <code>$</code> in a field. Values are from the latest run.</p>`;
  }

  function insertData(text) {
    const field = lastDataField?.isConnected ? lastDataField : byId('panelForm').querySelector('[data-variables]');
    if (!field) return;
    const start = field.selectionStart ?? field.value.length;
    const end = field.selectionEnd ?? start;
    field.value = field.value.slice(0, start) + text + field.value.slice(end);
    field.focus();
    field.selectionStart = field.selectionEnd = start + text.length;
    /* The field's own `$` menu must not open for a value that was just placed. */
    field.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function resultParts(step, { tested = false } = {}) {
    const label = step.status === 'success' ? 'Succeeded' : step.status === 'warning' ? 'Failed, task continued' : 'Failed';
    return `<h3>${statusGlyph(step.status)}${tested ? `Test: ${label.toLowerCase()}` : label}<small>${formatDuration(step.durationMs)}</small></h3>${renderStepResult(step)}`;
  }

  const changeParametersButton = () => (store.task.parameters.length ? `<button type="button" class="ui-button" data-action="run" title="Run from the start with different values">Change parameters</button>` : '');
  /* A run can continue at its failed step while that step is still in the task. */
  const canResume = (run, failed) => failed && failed === run.steps.at(-1) && run.steps.length > 1 && nodeById(store.task, failed.nodeId);
  const runAgainButton = '<button type="button" class="ui-button" data-wb="again" title="Run from the start with the same parameters">Run again</button>';
  const rerunButton = () => {
    const run = runner.run;
    const resumable = canResume(run, run.steps.find((step) => step.status === 'failed'));
    return `<div class="result-actions">${resumable ? `<button type="button" class="ui-button is-primary" data-wb="resume" title="Keep the results before this step and continue from here">${icon('replay')}Rerun from this step</button>` : ''}${runAgainButton}${changeParametersButton()}<button type="button" class="ui-link-button" data-wb="continue-on-failure">Let the task continue when this step fails</button></div>`;
  };

  let resultKey = null;
  function renderResult() {
    const node = store.selectedNode;
    const host = byId('panelResult');
    if (!node || !host) return;
    const tested = testResult?.nodeId === node.id ? testResult : null;
    const state = runner.run ? runner.stateOf(node.id) : null;
    const key = `${node.id}:${tested ? 'test' : ''}:${runner.run?.id}:${state}:${runner.finished}`;
    if (key === resultKey) return;
    resultKey = key;
    const step = runner.stepFor(node.id);
    host.className = 'result panel-section';
    if (tested) {
      host.classList.toggle('is-failed', tested.status === 'failed');
      host.innerHTML = `${resultParts(tested, { tested: true })}${runner.finished && state === 'failed' ? rerunButton() : ''}`;
    } else if (store.review?.changed.has(node.id)) {
      host.innerHTML = `<h3>The agent changed this step</h3>${store.review.changed.get(node.id).map(({ key, before }) => `<p class="result-note"><b>${escapeHtml(key)}</b>${before === null || before === undefined ? '' : `, was <code>${escapeHtml(typeof before === 'string' ? before : JSON.stringify(before))}</code>`}</p>`).join('')}`;
    } else if (store.review?.added.has(node.id)) {
      host.innerHTML = '<h3>The agent added this step</h3>';
    } else if (!runner.run) host.innerHTML = '';
    else if (state === 'running') host.innerHTML = `<h3>${statusGlyph('running')}Running…</h3>`;
    else if (state === 'pending') host.innerHTML = '<p class="result-note">This run has not reached this step yet.</p>';
    else if (state === 'skipped' || !step) host.innerHTML = '<p class="result-note">This run took a different path.</p>';
    else {
      host.classList.toggle('is-failed', state === 'failed');
      host.innerHTML = `${resultParts(step)}${runner.finished && state === 'failed' ? rerunButton() : ''}`;
    }
  }

  /* A structural change rebuilds the form, so the field being typed in has to survive it. */
  function focusedFieldSelector() {
    const active = document.activeElement;
    if (!byId('panelForm')?.contains(active)) return null;
    for (const name of ['field', 'mapIndex']) {
      if (active.dataset[name] !== undefined) {
        const attribute = name.replace(/[A-Z]/g, (letter) => `-${letter.toLowerCase()}`);
        const part = active.dataset.mapPart ? `[data-map-part="${active.dataset.mapPart}"]` : '';
        return { selector: `[data-${attribute}="${active.dataset[name]}"]${part}`, start: active.selectionStart, end: active.selectionEnd };
      }
    }
    return null;
  }

  function mountPanel() {
    const panel = byId('inspector');
    const focused = focusedFieldSelector();
    form?.dispose();
    form = null;
    resultKey = null;
    const node = store.selectedNode;
    if (!node) { panel.innerHTML = ''; return; }
    const action = ACTIONS[node.type];
    const isStart = node.type === 'start';
    panel.innerHTML = `
      <div class="panel-head">
        <span class="node-icon ${isStart ? 'is-start' : ''}" data-category="${action.category}">${icon(action.icon)}</span>
        <div class="panel-title">
          <input class="inspector-name" aria-label="Step name" title="Click to rename this step" value="${escapeHtml(node.name)}" ${isStart ? 'readonly' : ''}>
          <small>${escapeHtml(isStart ? 'How this task starts' : action.description)}</small>
        </div>
        <button type="button" class="ui-icon-button" data-inspector="close" aria-label="Close panel" title="Close (esc)">${icon('x')}</button>
      </div>
      <div class="panel-body" id="panelBody">
        <div id="panelResult"></div>
        <section class="panel-section"><h3>Settings</h3><div id="panelForm"></div></section>
        <section class="panel-section" id="panelData"></section>
      </div>
      ${isStart ? '' : `<div class="panel-foot"><div class="panel-foot-row"><button type="button" class="ui-button" data-wb="test" id="testButton" ${testing ? 'disabled' : ''}>${icon('play')}${testing ? 'Testing…' : 'Test this step'}</button><span class="panel-foot-space"></span><button type="button" class="labelled-button" data-inspector="duplicate" title="Duplicate (${modKey}D)">${icon('copy')}<span>Duplicate</span></button><button type="button" class="labelled-button" data-inspector="delete" title="Delete (⌫)">${icon('trash')}<span>Delete</span></button></div><small>Test runs only this step, with the values shown under Data you can use.</small></div>`}`;
    form = DT.ui.mountForm(byId('panelForm'), store, node.id);
    panel.querySelector('.inspector-name').addEventListener('input', (event) => store.rename(node.id, event.target.value));
    byId('panelForm').addEventListener('focusin', (event) => { if (event.target.matches('[data-variables]')) lastDataField = event.target; });
    renderData(node);
    renderResult();
    if (focused) {
      const field = byId('panelForm').querySelector(focused.selector);
      if (field) { field.focus(); if (focused.start !== null && focused.start !== undefined) field.setSelectionRange?.(focused.start, focused.end); }
    }
  }

  function testSelectedStep() {
    const node = store.selectedNode;
    testing = true;
    syncTestButton();
    store.testStep(node.id, variablesBefore(node.id)).then((result) => { testResult = result; }, (error) => {
      testResult = { nodeId: node.id, status: 'failed', error: error.message, output: '', durationMs: 0 };
    }).then(() => {
      testing = false;
      syncTestButton();
      resultKey = null;
      renderResult();
      if (byId('panelBody')) byId('panelBody').scrollTop = 0;
    });
  }

  function syncTestButton() {
    const button = byId('testButton');
    if (!button) return;
    button.disabled = testing;
    button.innerHTML = `${icon('play')}${testing ? 'Testing…' : 'Test this step'}`;
  }

  /* ---------- Run player ---------- */

  const failedStepOf = (run) => run.steps.find((step) => step.status === 'failed');
  function runHeadline(run) {
    const failed = failedStepOf(run);
    return failed ? `Failed at ${failed.name}` : run.failure || RUN_STATUS_LABELS[run.status];
  }
  const historyToggle = () => `<button type="button" class="labelled-button history-toggle ${historyOpen ? 'is-on' : ''}" data-wb="history" aria-expanded="${historyOpen}" title="Run history">${icon(historyOpen ? 'chevronDown' : 'chevronUp')}<span>History</span></button>`;

  let stripRun;
  let stripSteps = 0;
  function renderStrip() {
    const run = runner.run;
    const host = byId('stripHost');
    stripRun = run;
    stripSteps = run?.steps.length || 0;
    if (!run) {
      const last = store.runs[0];
      host.innerHTML = idleStrip(last);
      return;
    }
    host.innerHTML = `
      <div class="strip-summary" id="stripStatus" role="status"></div>
      <button type="button" class="labelled-button" data-wb="toggle-play" id="stripToggle"></button>
      <span class="strip-time" id="stripNow"></span>
      <div class="track" id="stripTrack" role="slider" aria-label="Position in run" aria-valuemin="0" aria-valuemax="${run.durationMs}" tabindex="0">${run.steps.map((step) => `<span class="track-part is-${step.status}" style="flex:${Math.max(step.durationMs, 1)} 1 0" data-start="${step.startMs}" data-duration="${step.durationMs}" title="${escapeHtml(step.name)}"><i></i></span>`).join('')}</div>
      <span class="strip-time">${seconds(run.durationMs)}</span>
      <div class="strip-actions" id="stripActions"></div>`;
    updateStrip();
  }

  /* A failure that is open in the panel already offers its own way to change the values. */
  function restartActionsHtml(failed) {
    /* While the panel shows the failed step, these actions are already there. */
    if (failed?.nodeId === store.selectedId) return '';
    const resume = canResume(runner.run, failed) ? `<button type="button" class="ui-button is-primary" data-wb="resume" title="Keep the results before the failed step and continue from it">${icon('replay')}Rerun from failed step</button>` : '';
    return `${resume}${runAgainButton}`;
  }

  let statusKey = null;
  function updateStrip() {
    const run = runner.run;
    const track = byId('stripTrack');
    if (!run || !track) return;
    track.querySelectorAll('.track-part').forEach((part) => {
      const progress = (runner.cursorMs - Number(part.dataset.start)) / Math.max(Number(part.dataset.duration), 1);
      part.firstElementChild.style.transform = `scaleX(${Math.max(0, Math.min(1, progress))})`;
    });
    track.setAttribute('aria-valuenow', Math.round(runner.cursorMs));
    byId('stripNow').textContent = seconds(runner.cursorMs);
    const active = runner.activeStep();
    const key = `${run.id}:${run.live}:${run.nextNodeId}:${runner.playing}:${runner.finished}:${active?.nodeId}:${store.selectedId}`;
    if (key === statusKey) return;
    statusKey = key;
    byId('stripStatus').innerHTML = runner.finished
      ? `${statusGlyph(run.status)}<b>${escapeHtml(runHeadline(run))}</b><small>${run.resumedFrom ? `from ${escapeHtml(run.resumedFrom)}, ` : ''}${formatAgo(run.startedAt)}</small>`
      : `${statusGlyph(runner.playing || run.live ? 'running' : 'pending')}<b>${escapeHtml((run.live ? nodeById(store.task, run.nextNodeId)?.name : active?.name) || 'Finishing')}</b><small>${run.live ? 'running' : runner.playing ? 'replaying' : 'paused'}</small>`;
    const toggle = byId('stripToggle');
    /* Nothing to replay while it runs, or in a run from before steps were recorded. */
    const nothingToReplay = run.live || !run.steps.length;
    toggle.hidden = nothingToReplay;
    byId('stripHost').classList.toggle('is-live', nothingToReplay);
    toggle.innerHTML = `${icon(runner.playing ? 'pause' : runner.finished ? 'replay' : 'play')}<span>${runner.playing ? 'Pause' : runner.finished ? 'Replay' : 'Play'}</span>`;
    toggle.title = runner.playing ? 'Pause this run' : runner.finished ? 'Watch this run again' : 'Continue this run';
    const failed = failedStepOf(run);
    byId('stripActions').innerHTML = `${runner.finished ? restartActionsHtml(failed) : ''}
      ${historyToggle()}
      <button type="button" class="labelled-button" data-run-action="close" title="Close this run and go back to editing (esc)">${icon('x')}<span>Close</span></button>`;
  }

  /* ---------- History ---------- */

  let historyKey = null;
  function renderHistory() {
    const host = byId('historyHost');
    host.hidden = !historyOpen;
    if (!historyOpen) { historyKey = null; return; }
    const shown = runner.run || store.runs[0];
    const steps = runner.run ? reachedSteps() : shown?.steps || [];
    const key = `${store.runs.length}:${shown?.id}:${steps.length}:${steps.filter(stepIsDone).length}:${runner.finished}:${store.selectedId}`;
    if (key === historyKey) return;
    historyKey = key;
    const finished = completedRuns();
    const successes = finished.filter((run) => run.status === 'success').length;
    const average = finished.length ? finished.reduce((total, run) => total + run.durationMs, 0) / finished.length : 0;
    const runsScroll = host.querySelector('.history-runs')?.scrollTop || 0;
    const traceScroll = host.querySelector('.trace')?.scrollTop || 0;
    host.innerHTML = `<div class="history-runs">
        <h3 class="history-heading">Recent runs</h3>
        <p class="history-stat">${finished.length ? `${successes} of ${finished.length} succeeded, ${formatDuration(average)} on average` : 'No runs yet. Press Run to start the first one.'}</p>
        ${store.runs.map((run) => { const live = run === runner.run && isLive(); return `<button type="button" class="history-run ${run === shown ? 'is-current' : ''}" data-run="${run.id}">${statusGlyph(live ? 'running' : run.status)}<b>Run #${run.id}</b><em>${live ? 'now' : `${formatDuration(run.durationMs)}, ${formatAgo(run.startedAt)}`}</em><small>${run.startedByAgent ? 'Started by an agent. ' : ''}${escapeHtml(runParameterSummary(run)) || 'No parameters'}</small></button>`; }).join('')}
      </div>
      <div class="trace"><div class="trace-head"><span></span><span>Step</span><span>Output</span><span>Time</span></div>${steps.map((step) => { const done = !runner.run || stepIsDone(step); const shownStatus = done ? step.status : 'running'; return `<button type="button" class="trace-row ${shownStatus === 'failed' ? 'is-failed' : ''} ${store.selectedId === step.nodeId ? 'is-selected' : ''}" data-step="${step.nodeId}">${statusGlyph(shownStatus)}<b>${escapeHtml(step.name)}</b><code>${done ? escapeHtml(step.error || String(step.output || '').split('\n')[0]) : ''}</code><em>${done ? formatDuration(step.durationMs) : ''}</em></button>`; }).join('')}${!steps.length && shown?.logLines?.length ? `<p class="ui-empty">This run is from before each step was recorded, so here is its log.</p><pre class="ui-log trace-log">${escapeHtml(shown.logLines.join('\n'))}</pre>` : ''}</div>`;
    host.querySelector('.history-runs').scrollTop = runsScroll;
    host.querySelector('.trace').scrollTop = traceScroll;
  }

  function setHistory(open) {
    historyOpen = open;
    renderHistory();
    statusKey = null;
    renderStrip();
    onHistoryToggle();
  }

  /* ---------- Wiring ---------- */

  store.subscribe((change) => {
    if (change === 'tick') { updateStrip(); renderResult(); renderHistory(); return; }
    if (change === 'save') { byId('barSave').textContent = DT.ui.saveStatus(store); return; }
    if (change === 'move') { byId('barUndo').disabled = !store.canUndo; return; }
    if (change === 'run') {
      if (runner.run && runner.playing) testResult = null;
      /* The track is rebuilt whenever a live run reaches another step. */
      if (runner.run !== stripRun || runner.run?.steps.length !== stripSteps) { statusKey = null; renderStrip(); } else if (runner.run) updateStrip();
      renderResult(); renderHistory();
      const node = store.selectedNode;
      if (node) renderData(node);
      return;
    }
    if (change === 'selection') {
      if (testResult && testResult.nodeId !== store.selectedId) testResult = null;
      mountPanel(); renderHistory(); updateStrip();
      return;
    }
    if (change === 'config') { testResult = null; resultKey = null; renderResult(); syncTaskControls(); return; }
    if (change === 'meta') {
      syncTaskControls();
      /* The webhook URL on the Start panel contains the task name. */
      if (store.selectedNode?.type === 'start') mountPanel();
      return;
    }
    if (change === 'structure') {
      syncTaskControls(); mountPanel(); renderHistory();
      /* A step just added lands to the right, possibly behind the panel that opened for it. */
      if (store.selectedNode) revealSelectedNode();
      return;
    }
    if (change === 'task') { testResult = null; syncTaskControls(); statusKey = null; renderStrip(); mountPanel(); renderHistory(); }
  });

  document.addEventListener('pointerdown', (event) => {
    /* Keeps the caret in the field while a data row is clicked. */
    if (event.target.closest('[data-insert]')) event.preventDefault();
  });

  document.addEventListener('change', (event) => {
    /* A switch that does nothing until Save would leave a schedule running after it looks off, so this saves at once. */
    if (event.target.matches('[data-wb-trigger]')) {
      const { checked } = event.target;
      store.updateTask({ active: checked });
      store.save().then(() => DT.ui.toast(checked ? 'Trigger is on' : 'Trigger is off'), (error) => {
        /* A refused save, such as a schedule that cannot start the task, leaves the trigger as it was. */
        store.updateTask({ active: !checked });
        event.target.checked = !checked;
        DT.ui.toast(error.message, { tone: 'danger' });
      });
    }
    if (event.target.matches('[data-wb-autosave]')) store.setAutoSave(event.target.checked);
  });

  document.addEventListener('click', (event) => {
    const target = event.target;
    const actionElement = target.closest('[data-wb]');
    const action = actionElement?.dataset.wb;
    if (action === 'tasks') openTaskSwitcher(actionElement);
    if (action === 'trigger') store.select(rootNode(store.task).id);
    if (action === 'test') testSelectedStep();
    if (action === 'history') setHistory(!historyOpen);
    if (action === 'continue-on-failure') {
      store.updateConfig(store.selectedId, 'continueOnFailure', true);
      mountPanel();
      DT.ui.toast('This step no longer stops the task. It takes effect from the next run.');
    }
    if (action === 'resume') { store.select(null); store.startRun(runner.run.parameters, { resumeFrom: runner.run }).catch((error) => DT.ui.toast(error.message, { tone: 'danger' })); }
    if (action === 'again') { store.select(null); store.startRun(runner.run.parameters).catch((error) => DT.ui.toast(error.message, { tone: 'danger' })); }
    if (action === 'toggle-play') { if (runner.playing) runner.pause(); else runner.play(); }
    const insert = target.closest('[data-insert]');
    if (insert) insertData(insert.dataset.insert);
    const runButton = target.closest('[data-run]');
    if (runButton) store.openRun(runButton.dataset.run).catch((error) => DT.ui.toast(error.message, { tone: 'danger' }));
    const stepRow = target.closest('[data-step]');
    if (stepRow && nodeById(store.task, stepRow.dataset.step)) store.select(stepRow.dataset.step);
  });

  DT.workbench = {
    MARK, syncTaskControls, bindTaskName,
    isHistoryOpen: () => historyOpen,
    /* The page supplies the two hosts, what the strip shows with no run open, and any history side effect. */
    start({ idle, onToggle }) {
      idleStrip = idle;
      if (onToggle) onHistoryToggle = onToggle;
      byId('historyHost').hidden = true;
      bindScrub(byId('stripHost'), '#stripTrack');
      renderStrip();
      renderHistory();
    },
  };
}
