import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DT, taskOf, namesOf, nextOf } from './editor.mjs';

test('select many steps: add one, take one away, select all', () => {
  const { store, steps: [first, second], start } = taskOf('print', 'print');
  store.select(first.id);
  assert.equal(store.selectedId, first.id);
  store.toggleSelected(second.id);
  assert.deepEqual(store.selectedIds, [first.id, second.id]);
  assert.equal(store.selectedId, null);
  store.toggleSelected(first.id);
  assert.deepEqual(store.selectedIds, [second.id]);
  store.selectAll();
  assert.deepEqual(store.selectedIds, [start.id, first.id, second.id]);
  store.select(null);
  assert.deepEqual(store.selectedIds, []);
});

test('remove: the steps around a removed step join up, and undo brings it back', () => {
  const { store, steps: [first, second, third] } = taskOf('print', 'print', 'print');
  store.removeNodes([second.id]);
  assert.deepEqual(namesOf(store), ['Start', 'Print message', 'Print message 3']);
  assert.equal(nextOf(store, first), third.id);
  store.undo();
  assert.deepEqual(namesOf(store), ['Start', 'Print message', 'Print message 2', 'Print message 3']);
  assert.equal(nextOf(store, first), second.id);
});

test('remove: many steps go at once and the start stays', () => {
  const { store } = taskOf('print', 'print');
  store.selectAll();
  store.removeNodes(store.selectedIds);
  assert.deepEqual(namesOf(store), ['Start']);
  assert.deepEqual(store.task.edges, []);
  assert.deepEqual(store.selectedIds, ['start']);
});

test('duplicate: copies keep their settings and the connections between them', () => {
  const { store, steps: [first, second], start } = taskOf('print', 'setVariable');
  store.updateConfig(first.id, 'message', 'hello');
  const copies = store.duplicateNodes([start.id, first.id, second.id]);
  assert.deepEqual(copies.map((copy) => copy.name), ['Print message 2', 'Set variable 2']);
  assert.equal(copies[0].config.message, 'hello');
  assert.equal(nextOf(store, copies[0]), copies[1].id);
  assert.equal(nextOf(store, first), second.id);
  assert.deepEqual(store.selectedIds, copies.map((copy) => copy.id));
  const places = store.task.nodes.map((node) => `${node.position.x},${node.position.y}`);
  assert.equal(new Set(places).size, places.length);
});

test('copy and paste: the steps arrive in another task at the place given', () => {
  const { store, steps: [first, second] } = taskOf('print', 'setVariable');
  const copied = store.copiedSteps([first.id, second.id]);
  const other = taskOf('print').store;
  const pasted = other.pasteSteps(copied, { x: 500, y: 600 });
  assert.deepEqual(namesOf(other), ['Start', 'Print message', 'Print message 2', 'Set variable']);
  assert.deepEqual(pasted[0].position, { x: 500, y: 600 });
  assert.equal(nextOf(other, pasted[0]), pasted[1].id);
});

test('copy and paste: no step gives nothing to copy, and other text adds nothing', () => {
  const { store, start } = taskOf('print');
  assert.equal(store.copiedSteps([start.id]), null);
  assert.deepEqual(store.pasteSteps('some text'), []);
  assert.deepEqual(store.pasteSteps('{"nodes":[{"type":"print"}]}'), []);
  assert.deepEqual(namesOf(store), ['Start', 'Print message']);
});

test('deactivate: sets and clears the mark on steps, and leaves the start alone', () => {
  const { store, steps: [first, second], start } = taskOf('print', 'print');
  store.setDeactivated([start.id, first.id, second.id], true);
  assert.deepEqual(store.task.nodes.map(DT.isDeactivated), [false, true, true]);
  store.setDeactivated([first.id], false);
  assert.deepEqual(store.task.nodes.map(DT.isDeactivated), [false, false, true]);
  store.undo();
  assert.deepEqual(store.task.nodes.map(DT.isDeactivated), [false, true, true]);
});

test('replace: the new step takes the place, the connections and the name the person gave', () => {
  const { store, steps: [first, second, third] } = taskOf('print', 'print', 'print');
  store.updateConfig(second.id, 'message', 'hello');
  store.updateConfig(second.id, 'continueOnFailure', true);
  store.rename(second.id, 'Greet');
  store.setDeactivated([second.id], true);
  const replacement = store.replaceStep(second.id, 'setVariable');
  assert.equal(replacement.type, 'setVariable');
  assert.equal(replacement.name, 'Greet');
  assert.deepEqual(replacement.position, second.position);
  assert.deepEqual(replacement.config, { continueOnFailure: true, deactivated: true });
  assert.equal(nextOf(store, first), replacement.id);
  assert.equal(nextOf(store, replacement), third.id);
  assert.deepEqual(store.selectedIds, [replacement.id]);
});

test('replace: a step without its own name takes the name of the new kind', () => {
  const { store, steps: [first] } = taskOf('print');
  assert.equal(store.replaceStep(first.id, 'setVariable').name, 'Set variable');
});

test('replace: a connection from an output that the new step lacks goes away', () => {
  const { store, steps: [condition] } = taskOf('if');
  const whenYes = store.addStep('print', { fromId: condition.id, port: 'true' });
  store.addStep('print', { fromId: condition.id, port: 'false' });
  const replacement = store.replaceStep(condition.id, 'print');
  assert.deepEqual(store.task.edges.filter((edge) => edge.from === replacement.id), []);
  assert.ok(DT.nodeById(store.task, whenYes.id));
});

test('replace: the start stays as it is', () => {
  const { store, start } = taskOf('print');
  assert.equal(store.replaceStep(start.id, 'print'), null);
  assert.deepEqual(namesOf(store), ['Start', 'Print message']);
});

test('run step: the store names the step that runs until the server answers, also when the server fails', async () => {
  const { store, steps: [step] } = taskOf('print');
  const changes = [];
  store.subscribe((change) => { if (change === 'testing') changes.push(store.testingStepId); });
  const serverOf = (answer) => { globalThis.fetch = () => answer; };
  const fetchBefore = globalThis.fetch;
  try {
    let answerNow;
    serverOf(new Promise((resolve) => { answerNow = resolve; }));
    const running = store.testStep(step.id, {});
    assert.equal(store.testingStepId, step.id);
    answerNow({ ok: true, status: 200, text: async () => JSON.stringify({ statusCode: 'SUCCESS', output: 'done' }) });
    assert.equal((await running).status, 'success');
    assert.equal(store.testingStepId, null);

    serverOf(Promise.reject(new Error('no server')));
    await assert.rejects(store.testStep(step.id, {}));
    assert.equal(store.testingStepId, null);
    assert.deepEqual(changes, [step.id, null, step.id, null]);
  } finally {
    globalThis.fetch = fetchBefore;
  }
});
