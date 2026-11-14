/* Home: every task, the latest runs and the credentials, one tab each. The address names what is open, so Back and
   a shared link work: #/tasks, #/runs, #/credentials, #/task/<name> or #/new. */
export default function boot() {
  const { store } = DT.canvas;
  const { icon, escapeHtml, statusGlyph, toast } = DT.ui;
  const TABS = [['tasks', 'Tasks'], ['runs', 'Runs'], ['credentials', 'Credentials']];
  const SORTS = [['changed', 'Last changed'], ['created', 'Newest'], ['name', 'Name']];
  const RUNS_LISTED = 50;
  const home = document.getElementById('home');
  const fail = (error) => toast(error.message, { tone: 'danger' });
  let tab = null;
  let search = '';
  let sort = 'changed';
  let runs = [];
  let routed = false;

  const viewOf = (hash) => {
    const [, view = 'tasks', rest = ''] = hash.match(/^#\/([a-z]+)\/?(.*)$/) || [];
    return { view, name: decodeURIComponent(rest) };
  };
  const isSaved = (name) => store.tasks.some((entry) => entry.name === name);
  const editorHash = () => (isSaved(store.task.name) ? `#/task/${encodeURIComponent(store.task.name)}` : '#/new');
  const timeOf = (version) => new Date(version.created).getTime();
  const dateText = (time) => new Date(time).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' });

  /* ---------- Tasks ---------- */

  function tasksHtml() {
    if (!store.tasks.length) return '<p class="home-empty">No tasks yet. Make the first one with New task.</p>';
    const query = search.trim().toLowerCase();
    const order = {
      changed: (first, second) => timeOf(second.versions[0]) - timeOf(first.versions[0]),
      created: (first, second) => timeOf(second.versions.at(-1)) - timeOf(first.versions.at(-1)),
      name: (first, second) => first.name.localeCompare(second.name),
    }[sort];
    const shown = store.tasks.filter((entry) => entry.name.toLowerCase().includes(query)).sort(order);
    if (!shown.length) return `<p class="home-empty">No task matches “${escapeHtml(search)}”.</p>`;
    return shown.map(({ name, versions }) => {
      const current = versions.find((version) => !version.proposed);
      return `<div class="home-row" data-task="${escapeHtml(name)}">
        <button type="button" class="home-row-main" data-open-task><b>${escapeHtml(name)}</b><small>Changed ${DT.formatAgo(timeOf(versions[0]))} · Created ${dateText(timeOf(versions.at(-1)))}</small></button>
        ${versions[0].proposed ? '<span class="home-tag is-waiting">Waiting for your approval</span>' : ''}
        ${current?.active ? '<span class="home-tag is-on">On</span>' : ''}
        <button type="button" class="ui-icon-button" data-task-menu aria-label="More for ${escapeHtml(name)}" aria-haspopup="menu">${icon('more')}</button>
      </div>`;
    }).join('');
  }

  function openTaskMenu(anchor, name) {
    const items = [
      ['workflow', 'Open', () => show(`#/task/${encodeURIComponent(name)}`)],
      ['copy', 'Duplicate…', () => show(`#/task/${encodeURIComponent(name)}`).then(() => store.task.name === name && DT.ui.openSaveAsDialog(store))],
      ['download', 'Export as JSON', () => store.switchTask(name).then(() => store.task.name === name && DT.ui.exportTask(store))],
      ['trash', 'Delete…', () => confirm(`Delete ${name} and all its versions? This cannot be undone.`) && store.deleteTask(name).then(() => toast(`Deleted ${name}`))],
    ];
    const { element, close } = DT.ui.openPopover(anchor, `<div class="ui-menu">${items.map(([iconName, label], index) => `<button type="button" class="ui-option" data-item="${index}">${icon(iconName)}<span>${label}</span></button>`).join('')}</div>`);
    element.addEventListener('click', (event) => {
      const item = event.target.closest('[data-item]');
      if (!item) return;
      close();
      Promise.resolve(items[Number(item.dataset.item)][2]()).catch(fail);
    });
  }

  /* ---------- Runs ---------- */

  function runsHtml() {
    if (!runs.length) return '<p class="home-empty">No runs yet. A task runs when you press Run, or when its trigger starts it.</p>';
    return runs.map((run) => `<div class="home-row" data-run="${run.id}" data-run-task="${escapeHtml(run.taskName)}">
      <button type="button" class="home-row-main is-run" data-open-run>${statusGlyph(run.status)}<span><b>${escapeHtml(run.taskName)}</b><small>${DT.ui.RUN_STATUS_LABELS[run.status] ?? run.status} · Started ${DT.formatAgo(run.startedAt)}${run.durationMs ? ` · took ${DT.formatDuration(run.durationMs)}` : ''}</small></span></button>
      ${run.preview ? '<span class="home-tag">Preview</span>' : ''}
      ${run.startedByAgent ? '<span class="home-tag">Started by an agent</span>' : ''}
    </div>`).join('');
  }

  async function openRun(row) {
    const id = row.dataset.run;
    await show(`#/task/${encodeURIComponent(row.dataset.runTask)}`);
    if (store.runs.some((run) => run.id === id)) await store.openRun(id);
  }

  /* ---------- Page ---------- */

  function renderBody() {
    const body = document.getElementById('homeBody');
    if (tab === 'credentials') { DT.ui.mountCredentials(body, store); return; }
    body.onclick = null;
    body.innerHTML = `<div class="home-list">${tab === 'runs' ? runsHtml() : tasksHtml()}</div>`;
  }

  function renderHome() {
    home.innerHTML = `<div class="home-page">
      <header class="home-head"><div><h1>Home</h1><p>Your tasks, how they ran, and what they sign in with.</p></div>
        <button type="button" class="ui-button is-primary" data-go="new">${icon('plus')}New task</button></header>
      <nav class="home-tabs" aria-label="Home">${TABS.map(([id, label]) => `<button type="button" class="home-tab ${id === tab ? 'is-active' : ''}" data-go="${id}" ${id === tab ? 'aria-current="page"' : ''}>${label}</button>`).join('')}</nav>
      ${tab === 'tasks' ? `<div class="home-tools"><label class="home-search">${icon('search')}<input id="homeSearch" placeholder="Search tasks" aria-label="Search tasks" autocomplete="off" spellcheck="false"></label>
        <select class="home-sort" id="homeSort" aria-label="Sort tasks">${SORTS.map(([id, label]) => `<option value="${id}" ${id === sort ? 'selected' : ''}>${label}</option>`).join('')}</select></div>` : ''}
      <div id="homeBody"></div></div>`;
    const input = document.getElementById('homeSearch');
    if (input) {
      input.value = search;
      input.addEventListener('input', () => { search = input.value; renderBody(); });
      document.getElementById('homeSort').addEventListener('change', (event) => { sort = event.target.value; renderBody(); });
    }
    renderBody();
  }

  /* ---------- Address ---------- */

  async function route() {
    const { view, name } = viewOf(location.hash);
    routed = true;
    const wasHome = document.documentElement.classList.contains('is-home');
    tab = view === 'task' || view === 'new' ? null : TABS.find(([id]) => id === view)?.[0] ?? 'tasks';
    document.documentElement.classList.toggle('is-home', !!tab);
    DT.flow.renderRail();
    if (tab) {
      store.select(null);
      document.title = 'Home · DopplerTask';
      renderHome();
      if (tab === 'runs') { runs = await store.loadAllRuns(RUNS_LISTED).catch((error) => { fail(error); return []; }); if (tab === 'runs') renderBody(); }
      if (tab === 'credentials') store.loadCredentials().catch(fail);
      return;
    }
    try {
      if (view === 'task' && store.task.name !== name) await store.switchTask(name);
      if (view === 'new' && isSaved(store.task.name)) store.newTask();
    } catch (error) { fail(error); }
    /* The task may not exist, or the person kept the one with unsaved changes. The address says what is open. */
    history.replaceState(null, '', editorHash());
    DT.workbench.syncTaskControls();
    /* A hidden canvas has no size, so a task opened from home is fitted once it shows. */
    if (wasHome) { Object.assign(DT.canvas.view, DT.canvas.fitTarget()); DT.canvas.applyView(); }
  }

  /* Going somewhere adds to the history, so Back returns; the page then follows the address. */
  function show(hash) {
    if (location.hash !== hash) history.pushState(null, '', hash);
    return route();
  }

  window.addEventListener('hashchange', () => route().catch(fail));
  document.addEventListener('click', (event) => {
    const go = event.target.closest('[data-go]')?.dataset.go;
    if (go === 'new') show('#/new').catch(fail);
    else if (go) show(`#/${go}`).catch(fail);
    if (tab && event.target.closest('[data-flow="diagram"]')) show(editorHash()).catch(fail);
    const row = event.target.closest('.home-row');
    if (event.target.closest('[data-open-task]')) show(`#/task/${encodeURIComponent(row.dataset.task)}`).catch(fail);
    if (event.target.closest('[data-task-menu]')) openTaskMenu(event.target.closest('[data-task-menu]'), row.dataset.task);
    if (event.target.closest('[data-open-run]')) openRun(row).catch(fail);
  });
  /* The task in the editor changes from within it too, through the task menu, a save or a rename. */
  store.subscribe((change) => {
    if (change === 'tasks' && tab && tab !== 'runs') renderBody();
    if (change === 'task' && routed && !tab) history.replaceState(null, '', editorHash());
  });

  DT.home = { tab: () => tab };
  /* The page shows where the address points while the lists load, rather than an empty task first. */
  document.documentElement.classList.toggle('is-home', !['task', 'new'].includes(viewOf(location.hash).view));
  Promise.all([store.loadCredentials().catch(() => {}), store.loadTaskList()]).then(() => {
    /* A new task needs the saved names to pick a free one. A person with no tasks yet starts with one. */
    store.newTask();
    if (!viewOf(location.hash).view || !location.hash.startsWith('#/')) history.replaceState(null, '', store.tasks.length ? '#/tasks' : '#/new');
    return route();
  }).catch(fail);
}
