import { test } from 'node:test';
import assert from 'node:assert/strict';
import { DT, taskOf, namesOf, nextOf } from './editor.mjs';

/* A server that keeps what the editor saves, and that already has the tasks named. */
function serverWith(...taskNames) {
  const saved = [];
  globalThis.fetch = async (url, options = {}) => {
    if (options.method === 'POST') saved.push(JSON.parse(options.body));
    const answer = url === '/task/grouped' ? Object.fromEntries([...taskNames, ...saved.map((task) => task.name)].map((name) => [name, [{ checksum: name }]])) : { checksum: 'saved' };
    return { ok: true, status: 200, text: async () => JSON.stringify(answer) };
  };
  return saved;
}

const messagesOf = (savedTask) => savedTask.actions.filter((action) => action['@type'] === 'PrintAction').map((action) => action.message);

test('move to a new task: the steps become a task, and one step runs it with the values they read', async () => {
  const saved = serverWith();
  const { store, steps: [first, second, third, fourth] } = taskOf('print', 'print', 'print', 'print');
  store.updateConfig(second.id, 'message', 'to $steps["Print message"].data.customer.email');
  store.updateConfig(third.id, 'message', '$stringUtils.upperCase($steps["Print message 2"].output) in $parameters.get("city") after $lastLog.output');
  store.updateConfig(fourth.id, 'message', "got ${steps[\"Print message 3\"].output|'nothing'}");

  const linkedStep = await store.moveStepsToTask([second.id, third.id], 'send_note');

  const [newTask, openTask] = saved;
  assert.equal(newTask.name, 'send_note');
  assert.deepEqual(newTask.parameters.map((parameter) => parameter.name), ['email', 'city']);
  assert.deepEqual(messagesOf(newTask), ['to $parameters.get("email")', '$stringUtils.upperCase($steps["Print message 2"].output) in $parameters.get("city") after $lastLog.output']);
  assert.equal(newTask.connections.length, 2);

  assert.deepEqual(namesOf(store), ['Start', 'Print message', 'send_note', 'Print message 4']);
  assert.deepEqual(linkedStep.config, {
    name: 'send_note',
    parameters: [{ parameterName: 'email', parameterValue: '$steps["Print message"].data.customer.email' }, { parameterName: 'city', parameterValue: '$parameters.get("city")' }],
  });
  assert.equal(nextOf(store, first), linkedStep.id);
  assert.equal(nextOf(store, linkedStep), fourth.id);
  assert.equal(fourth.config.message, "got ${steps[\"send_note\"].output|'nothing'}");
  assert.equal(openTask.name, store.task.name);
  assert.equal(store.saveState, 'saved');

  store.undo();
  assert.deepEqual(namesOf(store), ['Start', 'Print message', 'Print message 2', 'Print message 3', 'Print message 4']);
  assert.equal(nextOf(store, second), third.id);
});

test('move to a new task: two values with the same last word get names of their own', async () => {
  const saved = serverWith();
  const { store, steps: [, second] } = taskOf('print', 'print');
  store.updateConfig(second.id, 'message', '$steps["Print message"].data.buyer.name and $steps["Print message"].data.seller.name and $loop.item');
  await store.moveStepsToTask([second.id], 'greet');
  assert.deepEqual(saved[0].parameters.map((parameter) => parameter.name), ['name', 'name_2', 'item']);
  assert.deepEqual(messagesOf(saved[0]), ['$parameters.get("name") and $parameters.get("name_2") and $parameters.get("item")']);
});

test('move to a new task: only a line of steps with one way out can move', () => {
  const { store, steps: [first, second, third], start } = taskOf('print', 'print', 'print');
  const condition = store.addStep('if');
  assert.equal(store.canMoveToTask([second.id]), true);
  assert.equal(store.canMoveToTask([third.id, second.id]), true);
  assert.equal(store.canMoveToTask([first.id, third.id]), false);
  assert.equal(store.canMoveToTask([start.id, first.id]), false);
  assert.equal(store.canMoveToTask([third.id, condition.id]), false);
  assert.equal(store.canMoveToTask([]), false);
});

test('move to a new task: the notes name the values that do not pass between two tasks', () => {
  const { store, steps: [first, second, third] } = taskOf('print', 'setVariable', 'print');
  store.updateConfig(first.id, 'message', '#if($steps["x"].output) yes #end');
  store.updateConfig(third.id, 'message', '$steps["Print message"].output');
  assert.deepEqual(store.moveToTaskNotes([first.id, second.id]), [
    'Other steps read values of “Print message”. Only the answer of “Set variable” comes back from the new task.',
    '“Print message” has logic written by hand. Check the values it reads in the new task.',
    'The variables that “Set variable” sets stay inside the new task.',
  ]);
  assert.deepEqual(store.moveToTaskNotes([third.id]), []);
});

test('move to a new task: a name that a task has already is refused, and nothing changes', async () => {
  const saved = serverWith('send_note');
  const { store, steps: [first] } = taskOf('print');
  await store.loadTaskList();
  await assert.rejects(store.moveStepsToTask([first.id], 'send_note'), /A task called send_note already exists/);
  await assert.rejects(store.moveStepsToTask([first.id], store.task.name), /needs a name of its own/);
  assert.deepEqual(saved, []);
  assert.deepEqual(namesOf(store), ['Start', 'Print message']);
});

test('move to a new task: a value with a text for when it is empty keeps that text', async () => {
  const saved = serverWith();
  const { store, steps: [, second] } = taskOf('print', 'print');
  const whenEmpty = "${steps[\"Print message\"].data.city|'none'}";
  store.updateConfig(second.id, 'message', `in ${whenEmpty}`);
  const linkedStep = await store.moveStepsToTask([second.id], 'locate');
  assert.deepEqual(messagesOf(saved[0]), ['in $parameters.get("city")']);
  assert.deepEqual(linkedStep.config.parameters, [{ parameterName: 'city', parameterValue: whenEmpty }]);
});

test('move to a new task: a step after a deactivated first step gets the previous output as a parameter', async () => {
  const saved = serverWith();
  const { store, steps: [, second, third] } = taskOf('print', 'print', 'print');
  store.setDeactivated([second.id], true);
  store.updateConfig(third.id, 'message', 'after $lastLog.output');
  await store.moveStepsToTask([second.id, third.id], 'report');
  assert.deepEqual(saved[0].parameters.map((parameter) => parameter.name), ['previousOutput']);
});

test('move to a new task: the notes name what the steps after the line lose', () => {
  const { store, steps: [, second, third, fourth] } = taskOf('print', 'print', 'print', 'print');
  store.updateConfig(third.id, 'message', '$lastLog.outputVariables.total');
  store.updateConfig(fourth.id, 'message', '${steps["Print message 2"].output}');
  assert.deepEqual(store.moveToTaskNotes([second.id]), [
    'Other steps read values of “Print message 2”. Only the answer of “Print message 2” comes back from the new task.',
    '“Print message 4” reads the moved steps in logic written by hand. Point it to the new step after the move.',
  ]);
});

test('move to a new task: when the save of this task fails, the new name is taken from then on', async () => {
  const saved = serverWith();
  const keep = globalThis.fetch;
  globalThis.fetch = async (url, options = {}) => (options.method === 'POST' && saved.length === 1 ? { ok: false, status: 500, text: async () => '{"message":"no"}' } : keep(url, options));
  const { store, steps: [first] } = taskOf('print');
  await assert.rejects(store.moveStepsToTask([first.id], 'once'));
  store.undo();
  await assert.rejects(store.moveStepsToTask([first.id], 'once'), /A task called once already exists/);
  assert.equal(saved.length, 1);
});

test('move to a new task: a name with a double quote is refused', async () => {
  const saved = serverWith();
  const { store, steps: [first] } = taskOf('print');
  await assert.rejects(store.moveStepsToTask([first.id], 'say"hi'), /double quote/);
  assert.deepEqual(saved, []);
});
