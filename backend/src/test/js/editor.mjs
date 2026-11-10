/* Loads the editor's task model without a browser: the few browser objects it reads, and a small step catalogue. */
import { registerHooks } from 'node:module';

const property = (name) => ({ name, displayName: name, type: 'STRING' });
const CATALOGUE = ['StartAction', 'PrintAction', 'SetVariableAction', 'IfAction', 'LinkedTaskAction']
  .map((name) => ({ name, propertyInformationList: [property('message'), property('continueOnFailure')] }));

const storage = () => {
  const values = new Map();
  return { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: (key) => values.delete(key) };
};

globalThis.window = { addEventListener() {}, confirm: () => true };
globalThis.document = { hidden: true };
globalThis.localStorage = storage();
globalThis.sessionStorage = storage();
globalThis.fetch = async () => ({ ok: true, status: 200, text: async () => JSON.stringify({ actions: CATALOGUE }) });
/* The store polls the server for runs. Nothing here waits on a timer, so none starts. */
globalThis.setTimeout = () => 0;

/* The editor's files are modules that webpack reads, with imports that leave out ".js". */
registerHooks({
  resolve: (specifier, context, nextResolve) => nextResolve(/^\.\/[\w-]+$/.test(specifier) ? `${specifier}.js` : specifier, context),
  load: (url, context, nextLoad) => nextLoad(url, url.includes('/static/js/flow/') ? { ...context, format: 'module' } : context),
});

const { default: bootEngine } = await import('../../main/resources/static/js/flow/engine.js');
await bootEngine();

export const DT = window.DT;

/* A task of Start and the steps named, joined in a line. Gives the store and the steps by their place in the line. */
export function taskOf(...types) {
  const store = DT.createStore();
  const steps = types.map((type) => store.addStep(type));
  return { store, steps, start: DT.rootNode(store.task) };
}

export const namesOf = (store) => store.task.nodes.map((node) => node.name);
export const nextOf = (store, node, port = 'out') => DT.edgeFrom(store.task, node.id, port)?.to;
