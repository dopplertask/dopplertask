/* The three ways to fill a field: click a value an earlier step gave, change it with a guided choice, or write it by
   hand. All three end as the same text in the field, so a run reads one thing whichever way it was made. */
import { conditionWordsFor, hasHandWrittenLogic, kindOf, OPERATIONS, operationsFor, pathStep, readCondition, readReference, referenceWords, stepReference, tokensIn, tokenWords, valueAt, writeCondition, writeOperation } from './expressions';

const MAX_DEPTH = 6;
const PREVIEW_DELAY_MS = 300;
const OWN_CHOICE = '';

export default function boot() {
  const { store } = DT.canvas;
  const { icon, escapeHtml, openPopover } = DT.ui;
  const form = () => document.getElementById('panelForm');
  const fieldInput = (key) => form()?.querySelector(`[data-field="${key}"]`);

  const parsed = (text) => { try { return JSON.parse(text); } catch (error) { return undefined; } };

  /* ---------- Example values ---------- */

  /** The example value a reference points at for this step, or undefined when no run or test gave one yet. */
  function sampleAt(nodeId, reference) {
    const { source, name, path } = readReference(reference);
    if (source === 'loop.item') return valueAt(parsed(store.loopItemSample(nodeId)), path);
    if (source === 'loop.number') return 1;
    if (source === 'loop.index') return 0;
    if (source === 'loop.count') return undefined;
    const before = store.valuesBefore(nodeId);
    if (source === 'parameter') return before.parameters[name];
    if (source === 'previousOutput') return before.lastOutput;
    if (source === 'previousVariable') return before.lastOutputVariables?.[name];
    const sample = store.stepsBefore(nodeId).find(({ node }) => node.name === name)?.sample;
    if (!sample) return undefined;
    if (source === 'output') return sample.output;
    return valueAt(source === 'data' ? parsed(sample.output) : sample.outputVariables, path);
  }

  const shownValue = (value) => {
    const kind = kindOf(value);
    if (kind === 'list') return `${value.length} ${value.length === 1 ? 'item' : 'items'}`;
    if (kind === 'object') return `${Object.keys(value).length} ${Object.keys(value).length === 1 ? 'field' : 'fields'}`;
    return kind === 'empty' ? 'Empty' : String(value).split('\n')[0];
  };

  /* ---------- The values of the earlier steps, to click ---------- */

  /** One row per value, and the rows inside it. A list shows its first item, since every item has the same fields. */
  function valueRows(value, reference, label, { depth = 0, listPath = [], loopOver = null } = {}) {
    const kind = kindOf(value);
    const isBranch = kind === 'list' || kind === 'object';
    const offersLoop = loopOver && kind === 'list' && listPath.every((key) => !String(key).includes('.'));
    const row = `<div class="value-row" style="--depth:${depth}">
      <button type="button" class="data-row" data-insert="${escapeHtml(reference)}" title="Use ${escapeHtml(referenceWords(reference))}"><b>${escapeHtml(label)}</b><code>${escapeHtml(shownValue(value))}</code></button>
      ${offersLoop ? `<button type="button" class="value-action" data-loop-over="${escapeHtml(loopOver)}" data-loop-path="${escapeHtml(listPath.join('.'))}" title="Run the loop once for every item of this list">Loop over this</button>` : ''}
      ${isBranch ? '' : `<button type="button" class="ui-icon-button value-action" data-shape="${escapeHtml(reference)}" title="Change how this value is shown, or what to use when it is empty" aria-label="Change this value">${icon('sliders')}</button>`}
    </div>`;
    if (!isBranch || depth >= MAX_DEPTH) return row;
    const inside = kind === 'list' ? value.slice(0, 1).map((item) => [0, item]) : Object.entries(value);
    return row + inside.map(([key, item]) => {
      const step = pathStep(key);
      return step === null ? '' : valueRows(item, reference + step, typeof key === 'number' ? 'item 1' : key, { depth: depth + 1, listPath: [...listPath, key], loopOver });
    }).join('');
  }

  function stepValues(node, sample, selected) {
    const heading = `<h4 class="value-heading">${escapeHtml(node.name)}</h4>`;
    if (!sample) return `${heading}<p class="panel-hint">No example yet. Preview the task, or test that step, to see its values here.</p>`;
    const answer = parsed(sample.output);
    const loopOver = selected.type === 'loop' ? node.name : null;
    const whole = `<div class="value-row" style="--depth:0"><button type="button" class="data-row" data-insert="${escapeHtml(stepReference(node.name, 'output'))}" title="Use everything ${escapeHtml(node.name)} gave"><b>Whole answer</b><code>${escapeHtml(shownValue(sample.output))}</code></button>
      ${loopOver && !Array.isArray(answer) && kindOf(answer) !== 'object' && sample.output ? `<button type="button" class="value-action" data-loop-over="${escapeHtml(loopOver)}" data-loop-path="" title="Run the loop once for every line">Loop over this</button>` : ''}</div>`;
    const dataReference = stepReference(node.name, 'data');
    const fields = Array.isArray(answer)
      ? valueRows(answer, dataReference, 'List', { loopOver })
      : kindOf(answer) === 'object' ? Object.entries(answer).map(([key, item]) => (pathStep(key) === null ? '' : valueRows(item, dataReference + pathStep(key), key, { listPath: [key], loopOver }))).join('') : '';
    const variables = Object.entries(sample.outputVariables || {}).map(([name, value]) => (pathStep(name) === null ? '' : valueRows(value, stepReference(node.name, 'outputVariables') + pathStep(name), name))).join('');
    return `${heading}<div class="data-list">${whole}${fields}${variables}</div>`;
  }

  function loopValues(node) {
    const loop = store.loopAround(node.id);
    if (!loop) return '';
    const item = parsed(store.loopItemSample(node.id));
    const rows = item === undefined
      ? `<div class="value-row" style="--depth:0"><button type="button" class="data-row" data-insert="$loop.item"><b>Current item</b><code>No example yet</code></button></div>`
      : valueRows(item, '$loop.item', 'Current item');
    const counter = (reference, label, example) => `<div class="value-row" style="--depth:0"><button type="button" class="data-row" data-insert="${reference}"><b>${label}</b><code>${example}</code></button></div>`;
    return `<h4 class="value-heading">Every pass of ${escapeHtml(loop.name)}</h4><div class="data-list">${rows}${counter('$loop.number', 'Item number', '1, 2, 3…')}${counter('$loop.count', 'Number of items', 'How many the list has')}</div>`;
  }

  /** What the steps before this one gave, as values to click. */
  function valueTree(node) {
    const steps = store.stepsBefore(node.id);
    return loopValues(node) + steps.reverse().map(({ node: earlier, sample }) => stepValues(earlier, sample, node)).join('');
  }

  /* ---------- Writing to a field ---------- */

  function writeField(key, value) {
    const input = fieldInput(key);
    if (!input) { store.updateConfig(store.selectedId, key, value); return; }
    input.value = value;
    /* A change, not an input, so the field's own `$` menu stays closed. */
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  /* ---------- A guided change to one value ---------- */

  function choiceControls(operation, current) {
    if (!operation) return '';
    const isListed = operation.choices?.some(([value]) => value === current);
    const usesOwn = !operation.choices || (current !== undefined && !isListed);
    const select = operation.choices
      ? `<div class="ui-select"><select data-shape-choice aria-label="${escapeHtml(operation.label)}">${operation.choices.map(([value, label]) => `<option value="${escapeHtml(value)}" ${value === current ? 'selected' : ''}>${escapeHtml(label)}</option>`).join('')}${operation.ownChoice ? `<option value="${OWN_CHOICE}" ${usesOwn ? 'selected' : ''}>Your own…</option>` : ''}</select>${icon('chevronDown')}</div>`
      : '';
    const own = operation.ownChoice
      ? `<input class="ui-input" data-shape-own autocomplete="off" spellcheck="false" placeholder="${escapeHtml(operation.ownChoice)}" value="${escapeHtml(usesOwn ? current ?? '' : '')}" ${usesOwn ? '' : 'hidden'}>`
      : '';
    return select + own;
  }

  /** Opens the guided changes for one value. `current` is the chip being changed, if any. `onUse` gets the text. */
  function openShape(anchor, nodeId, reference, onUse, current = {}) {
    const sample = sampleAt(nodeId, reference);
    /* A change that is on the value already stays on offer, whatever the example value is now. */
    const offered = OPERATIONS.filter((operation) => operationsFor(kindOf(sample)).includes(operation) || operation.id === current.operation);
    const { element, close } = openPopover(anchor, `<div class="shape">
      <b class="shape-title">${escapeHtml(referenceWords(reference))}</b>
      <small class="shape-sample">${sample === undefined ? 'No example value yet' : `Example: ${escapeHtml(shownValue(sample))}`}</small>
      <div class="ui-select"><select data-shape-operation aria-label="Change"><option value="">Use it as it is</option>${offered.map((operation) => `<option value="${operation.id}" ${operation.id === current.operation ? 'selected' : ''}>${escapeHtml(operation.label)}</option>`).join('')}</select>${icon('chevronDown')}</div>
      <div class="shape-choice" data-shape-choices>${choiceControls(offered.find((operation) => operation.id === current.operation), current.choice)}</div>
      <p class="ui-preview" data-shape-preview hidden></p>
      <div class="shape-foot"><button type="button" class="ui-button is-primary" data-shape-use>Use</button></div>
    </div>`, { className: 'shape-popover' });
    const part = (name) => element.querySelector(`[data-shape-${name}]`);
    const written = () => {
      const operationId = part('operation').value;
      const choice = part('choice')?.value || part('own')?.value || '';
      const operation = OPERATIONS.find((candidate) => candidate.id === operationId);
      if (operation && !choice && operation.id !== 'default') return null;
      return writeOperation(operationId, reference, choice, { sample, readAs: current.readAs });
    };
    let asked = 0;
    const showPreview = async () => {
      const text = written();
      const turn = ++asked;
      part('use').disabled = text === null;
      if (text === null) { part('preview').hidden = true; return; }
      const result = await store.previewField(nodeId, text, store.valuesBefore(nodeId)).catch((error) => ({ error: error.message, missingValues: [] }));
      if (turn !== asked || !element.isConnected) return;
      part('preview').hidden = false;
      part('preview').innerHTML = previewHtml(result);
    };
    element.addEventListener('change', (event) => {
      if (event.target === part('operation')) part('choices').innerHTML = choiceControls(OPERATIONS.find((operation) => operation.id === event.target.value));
      if (event.target === part('choice') && part('own')) { part('own').hidden = event.target.value !== OWN_CHOICE; if (!part('own').hidden) part('own').focus(); }
      showPreview();
    });
    element.addEventListener('input', (event) => { if (event.target === part('own')) showPreview(); });
    part('use').addEventListener('click', () => { const text = written(); close(); onUse(text); });
    showPreview();
  }

  /* ---------- Chips and previews under a field ---------- */

  function previewHtml({ text, error, missingValues }) {
    const missing = missingValues.map((note) => `<span class="ui-preview-missing">${icon('alert')}${escapeHtml(note)}</span>`).join('');
    if (error) return `<span class="ui-preview-missing">${icon('alert')}${escapeHtml(error)}</span>${missing}`;
    return `<span>Gives</span> ${text === '' ? '<i>nothing</i>' : escapeHtml(text).slice(0, 200)}${missing}`;
  }

  function conditionPreviewHtml({ text, error, missingValues }) {
    if (error) return previewHtml({ text, error, missingValues });
    const branch = text.trim() === 'true' ? 'Yes' : 'No';
    return `<span>With the example values the run takes</span><b>${branch}</b>${missingValues.map((note) => `<span class="ui-preview-missing">${icon('alert')}${escapeHtml(note)}</span>`).join('')}`;
  }

  function renderChips(root) {
    root.querySelectorAll('[data-chips]').forEach((host) => {
      const text = root.querySelector(`[data-field="${host.dataset.chips}"]`)?.value ?? '';
      const chips = tokensIn(text).map((token, index) => `<button type="button" class="ui-chip" data-chip="${index}" data-chip-field="${escapeHtml(host.dataset.chips)}" title="${escapeHtml(token.text)}. Click to change it.">${escapeHtml(tokenWords(token))}</button>`).join('');
      const byHand = hasHandWrittenLogic(text) ? '<span class="ui-chip-note">Part of this is written by hand. It stays as written.</span>' : '';
      host.hidden = !chips && !byHand;
      host.innerHTML = chips + byHand;
    });
  }

  let previewTimer = null;
  let previewTurn = 0;
  function askPreviews(root, nodeId) {
    const turn = ++previewTurn;
    const node = DT.nodeById(store.task, nodeId);
    if (!node) return;
    const values = store.valuesBefore(nodeId);
    root.querySelectorAll('[data-preview]').forEach(async (host) => {
      const key = host.dataset.preview;
      const text = root.querySelector(`[data-field="${key}"]`)?.value ?? '';
      if (!/[$#]/.test(text)) { host.hidden = true; host.textContent = ''; return; }
      const asCondition = node.type === 'if' && key === 'condition';
      const result = await store.previewField(nodeId, text, values, { asCondition }).catch((error) => ({ text: '', error: error.message, missingValues: [] }));
      if (turn !== previewTurn || !host.isConnected) return;
      host.hidden = false;
      /* Before the first preview no step has an example, so a result worked out from nothing would mislead. */
      const noExamplesYet = result.missingValues.length > 0 && store.stepsBefore(nodeId).every(({ sample }) => !sample);
      if (noExamplesYet) { host.textContent = 'Preview the task once, and this shows what the field gives.'; return; }
      host.innerHTML = asCondition ? conditionPreviewHtml(result) : previewHtml(result);
    });
  }

  /** Brings the chips and the previews under the fields of a step up to date. The server works out each preview. */
  function refresh(root, nodeId) {
    renderChips(root);
    clearTimeout(previewTimer);
    previewTimer = setTimeout(() => askPreviews(root, nodeId), PREVIEW_DELAY_MS);
  }

  /* ---------- A condition in plain words ---------- */

  /* The condition being built, by step. It reaches the field only when the person says so. */
  let building = null;

  function buildingFor(node) {
    if (building?.nodeId !== node.id) {
      const read = readCondition(node.config.condition);
      building = { nodeId: node.id, reference: read?.reference || null, word: read?.word || 'is', value: read?.value || '', isWaiting: false };
    }
    return building;
  }

  function conditionBuilder(node) {
    if (node.type !== 'if') return '';
    const state = buildingFor(node);
    const words = conditionWordsFor(state.reference ? kindOf(sampleAt(node.id, state.reference)) : 'empty');
    const word = words.find((candidate) => candidate.id === state.word) || words[0];
    const isByHand = !!String(node.config.condition ?? '').trim() && !readCondition(node.config.condition);
    return `<div class="condition-builder" data-condition-builder>
      <span class="ui-label">Build the condition</span>
      <div class="condition-row">
        <button type="button" class="ui-button condition-slot ${state.isWaiting ? 'is-waiting' : ''}" data-condition-slot title="Then click a value under Data you can use">${escapeHtml(state.isWaiting ? 'Click a value below…' : state.reference ? referenceWords(state.reference) : 'Pick a value')}</button>
        <div class="ui-select"><select data-condition-word aria-label="How to compare">${words.map((candidate) => `<option value="${candidate.id}" ${candidate.id === word.id ? 'selected' : ''}>${escapeHtml(candidate.label)}</option>`).join('')}</select>${icon('chevronDown')}</div>
        <input class="ui-input" data-condition-value aria-label="Compare with" autocomplete="off" ${word.takes === 'number' ? 'type="number" step="any"' : ''} value="${escapeHtml(state.value)}" ${word.takes ? '' : 'hidden'}>
      </div>
      <div class="condition-foot"><button type="button" class="ui-button" data-condition-use ${state.reference ? '' : 'disabled'}>Use this condition</button>
      ${isByHand ? '<span class="ui-chip-note">The condition above is written by hand. It stays until you press this button.</span>' : ''}</div>
    </div>`;
  }

  function redrawBuilder() {
    const host = form()?.querySelector('[data-condition-builder]');
    if (host) host.outerHTML = conditionBuilder(store.selectedNode);
  }

  /** A clicked value goes to the condition being built when that waits for one. Says whether it took the value. */
  function takeValue(reference) {
    if (!building?.isWaiting || building.nodeId !== store.selectedId) return false;
    Object.assign(building, { reference, isWaiting: false });
    redrawBuilder();
    return true;
  }

  /* ---------- What a preview does with this step ---------- */

  function previewChoices(node) {
    if (node.type === 'start') return '';
    const choices = store.previewChoices(node.id);
    const toggle = (key, label, hint) => `<label class="ui-toggle" title="${escapeHtml(hint)}"><input type="checkbox" data-preview-choice="${key}" ${choices[key] ? 'checked' : ''}><span class="ui-toggle-track"></span><span>${label}</span></label>`;
    const chosen = [choices.sentForReal && 'sent for real', choices.madeToFail && 'made to fail'].filter(Boolean).join(', ');
    return `<details class="ui-disclosure" ${chosen || choices.exampleAnswer ? 'open' : ''}><summary>${icon('chevronRight')}In a preview${chosen ? `<span class="ui-pill">${chosen}</span>` : ''}</summary><div class="ui-field-grid">
      ${toggle('sentForReal', 'Send this step for real', 'A preview holds what a step would send. With this on, this step sends it.')}
      ${toggle('madeToFail', 'Make this step fail', 'Shows what the task does when this step fails: its retries, and whether the task stops or continues.')}
      <div class="ui-field"><label class="ui-label" for="preview-answer">Example answer</label><textarea class="ui-input is-mono" id="preview-answer" rows="2" spellcheck="false" data-preview-choice="exampleAnswer" placeholder='{"id": 1}'>${escapeHtml(choices.exampleAnswer ?? '')}</textarea><p class="ui-hint">While this step is held, the steps after it get this as its answer.</p></div>
    </div></details>`;
  }

  /* ---------- Wiring ---------- */

  document.addEventListener('click', (event) => {
    const chip = event.target.closest('[data-chip]');
    if (chip) {
      const key = chip.dataset.chipField;
      const token = tokensIn(fieldInput(key).value)[Number(chip.dataset.chip)];
      openShape(chip, store.selectedId, token.reference, (text) => {
        const now = fieldInput(key).value;
        /* Only this value is replaced. All around it stays as written. */
        if (now.slice(token.start, token.end) === token.text) writeField(key, now.slice(0, token.start) + text + now.slice(token.end));
      }, token);
    }
    const loopOver = event.target.closest('[data-loop-over]');
    if (loopOver) {
      writeField('items', stepReference(loopOver.dataset.loopOver, 'output'));
      writeField('path', loopOver.dataset.loopPath);
      DT.ui.toast('The loop runs once for every item of this list');
    }
    if (event.target.closest('[data-condition-slot]')) { building.isWaiting = !building.isWaiting; redrawBuilder(); }
    if (event.target.closest('[data-condition-use]')) writeField('condition', writeCondition(building.word, building.reference, building.value));
  });

  const onBuilderInput = (event) => {
    if (event.target.matches('[data-condition-word]')) { building.word = event.target.value; redrawBuilder(); }
    if (event.target.matches('[data-condition-value]')) building.value = event.target.value;
    const choice = event.target.dataset?.previewChoice;
    if (choice) store.setPreviewChoice(store.selectedId, choice, event.target.type === 'checkbox' ? event.target.checked : event.target.value);
  };
  document.addEventListener('input', onBuilderInput);
  document.addEventListener('change', (event) => { if (event.target.matches('[data-condition-word]')) onBuilderInput(event); });

  /* Another step starts its own condition. */
  store.subscribe((change) => { if (change === 'selection') building = null; });

  DT.values = { valueTree, openShape, refresh, conditionBuilder, previewChoices, takeValue };
}
