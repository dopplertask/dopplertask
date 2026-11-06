/* The diagram: direct manipulation of the graph, and the run shown on its wires. */
export default function boot() {
  const { ACTIONS, CATEGORIES, portsOf, edgeFrom, nodeById, summaryText, describeNode, formatDuration } = DT;
  const { icon, escapeHtml, statusGlyph } = DT.ui;

  const options = { ...DT.canvasOptions };
  const store = DT.createStore();
  const runner = store.runner;
  const NODE_WIDTH = options.nodeWidth;
  const HEAD_HEIGHT = options.headHeight;
  const ROW_HEIGHT = options.rowHeight;

  const elements = Object.fromEntries(['stage', 'world', 'wires', 'nodes', 'inspector', 'emptyCard'].map((id) => [id, document.getElementById(id)]));

  const view = { x: 0, y: 0, zoom: 1 };
  const displayed = new Map(); /* nodeId -> position currently drawn, for layout animation */
  const nodeElements = new Map();
  let positionAnimation = null;
  let viewBeforeReveal = null; /* Canvas offset to return to when the settings panel closes. */

  const hasBranches = (node) => !!ACTIONS[node.type].ports;
  const categoryOf = (node) => ACTIONS[node.type].category;
  const portY = (node, portId) => {
    if (!hasBranches(node)) return HEAD_HEIGHT / 2;
    const index = portsOf(node).findIndex((port) => port.id === portId);
    return HEAD_HEIGHT + ROW_HEIGHT * index + ROW_HEIGHT / 2;
  };
  const positionOf = (nodeId) => displayed.get(nodeId) || nodeById(store.task, nodeId).position;

  /* ---------- View transform ---------- */

  function applyView() {
    elements.world.style.transform = `translate(${view.x}px, ${view.y}px) scale(${view.zoom})`;
    const size = 22 * view.zoom;
    elements.stage.style.backgroundSize = `${size}px ${size}px`;
    elements.stage.style.backgroundPosition = `${view.x}px ${view.y}px`;
    DT.canvas?.afterView?.();
    /* The first-step card stays anchored just below Start, never on top of it. */
    if (!elements.emptyCard.hidden) {
      const start = DT.rootNode(store.task).position;
      elements.emptyCard.style.left = `${view.x + (start.x + NODE_WIDTH / 2) * view.zoom}px`;
      elements.emptyCard.style.top = `${view.y + (start.y + HEAD_HEIGHT) * view.zoom + 24}px`;
    }
  }

  function nodeHeight(node) { return HEAD_HEIGHT + (hasBranches(node) ? portsOf(node).length * ROW_HEIGHT : 0); }

  const MIN_READABLE_ZOOM = 0.6;
  const BACK_WIRE_GAP = 36;

  function fitTarget() {
    const nodes = store.task.nodes;
    const minX = Math.min(...nodes.map((node) => node.position.x));
    const minY = Math.min(...nodes.map((node) => node.position.y));
    const maxX = Math.max(...nodes.map((node) => node.position.x + NODE_WIDTH + 40));
    const maxY = Math.max(...nodes.map((node) => node.position.y + nodeHeight(node) + 20));
    const box = elements.stage.getBoundingClientRect();
    const left = options.leftInset;
    const usableWidth = box.width - left - options.rightInset - 40;
    const usableHeight = box.height - options.topInset - options.bottomInset;
    const zoom = Math.max(0.4, Math.min(1, usableWidth / (maxX - minX), usableHeight / (maxY - minY)));
    return {
      zoom,
      /* A diagram too long to fit starts at Start, rather than cut off at both ends. */
      x: left + 20 + Math.max(0, usableWidth - (maxX - minX) * zoom) / 2 - minX * zoom,
      y: options.topInset + (usableHeight - (maxY - minY) * zoom) / 2 - minY * zoom,
    };
  }

  function fitView() {
    Object.assign(view, fitTarget());
    applyView();
  }

  /* A panel opening or the window shrinking must not leave steps out of sight. */
  new ResizeObserver(() => {
    if (!store.task.nodes.some((node) => overflowOf(node, 0) > 0)) return;
    const fitted = fitTarget();
    if (fitted.zoom >= MIN_READABLE_ZOOM) { Object.assign(view, fitted); glideView(); }
  }).observe(elements.stage);

  function glideView() {
    elements.world.style.transition = 'transform 240ms cubic-bezier(0.23, 1, 0.32, 1)';
    applyView();
    setTimeout(() => { elements.world.style.transition = ''; }, 260);
  }

  const overflowOf = (node, panelEdge) => view.x + (node.position.x + NODE_WIDTH + 40) * view.zoom - (elements.stage.clientWidth - panelEdge);

  /* Keeps the selected step clear of the settings panel by showing the whole diagram while it still
     reads well, and by gliding otherwise. Closing the panel goes back. */
  function revealSelectedNode() {
    const node = store.selectedNode;
    if (!node) {
      if (viewBeforeReveal !== null) {
        Object.assign(view, viewBeforeReveal);
        viewBeforeReveal = null;
        glideView();
      }
      return;
    }
    const overflow = overflowOf(node, 24);
    if (overflow <= 0) return;
    if (viewBeforeReveal === null) viewBeforeReveal = { ...view };
    const fitted = fitTarget();
    if (fitted.zoom >= MIN_READABLE_ZOOM) Object.assign(view, fitted);
    else view.x -= overflow;
    glideView();
  }

  function zoomAround(factor, clientX, clientY) {
    const box = elements.stage.getBoundingClientRect();
    const anchorX = clientX === undefined ? box.width / 2 : clientX - box.left;
    const anchorY = clientY === undefined ? box.height / 2 : clientY - box.top;
    const nextZoom = Math.max(0.3, Math.min(1.8, view.zoom * factor));
    view.x = anchorX - ((anchorX - view.x) / view.zoom) * nextZoom;
    view.y = anchorY - ((anchorY - view.y) / view.zoom) * nextZoom;
    view.zoom = nextZoom;
    applyView();
  }

  const toWorld = (clientX, clientY) => {
    const box = elements.stage.getBoundingClientRect();
    return { x: (clientX - box.left - view.x) / view.zoom, y: (clientY - box.top - view.y) / view.zoom };
  };

  /* ---------- Top bar ---------- */

  /* ---------- Nodes ---------- */

  const summaryHtml = (node) => describeNode(node).map((part) => (typeof part === 'string' ? escapeHtml(part) : `<span class="val ${part.empty ? 'is-empty' : ''}">${escapeHtml(part.value)}</span>`)).join('');

  function nodeInnerHtml(node) {
    const issue = store.issues.some((candidate) => candidate.nodeId === node.id);
    const portsHtml = portsOf(node).map((port) => {
      const connected = !!edgeFrom(store.task, node.id, port.id);
      const top = portY(node, port.id) - (connected ? 9 : 11);
      return `<button type="button" class="port-out ${connected ? '' : 'is-open'}" ${connected ? 'tabindex="-1"' : ''} style="top:${top}px" data-port="${escapeHtml(port.id)}" aria-label="${connected ? 'Rewire' : 'Add next step'}${port.label ? ` for ${escapeHtml(port.label)}` : ''}">${connected ? '' : icon('plus')}</button>`;
    }).join('');
    return `
      ${node.type === 'start' || ACTIONS[node.type].trigger ? '' : '<span class="port-in"></span>'}
      <div class="node-head">
        <span class="node-icon" data-category="${categoryOf(node)}">${icon(ACTIONS[node.type].icon)}</span>
        <span class="node-text"><div class="node-kind">${node.type === 'start' ? 'Trigger' : CATEGORIES.find((category) => category.id === categoryOf(node)).label}</div><div class="node-name">${escapeHtml(node.name)}</div><div class="node-summary">${summaryHtml(node)}</div></span>
      </div>
      ${hasBranches(node) ? portsOf(node).map((port) => `<div class="branch-row" data-branch="${escapeHtml(port.id)}"><span>${escapeHtml(port.label)}</span></div>`).join('') : ''}
      ${portsHtml}
      <span class="node-status"></span>
      ${issue ? `<span class="node-issue" title="Needs attention">${icon('alert')}</span>` : ''}
      <div class="node-result"></div>`;
  }

  function placeNode(element, position) {
    element.style.transform = `translate(${position.x}px, ${position.y}px)`;
  }

  function renderNodes() {
    const task = store.task;
    const liveIds = new Set(task.nodes.map((node) => node.id));
    nodeElements.forEach((element, nodeId) => {
      if (!liveIds.has(nodeId)) { element.remove(); nodeElements.delete(nodeId); displayed.delete(nodeId); }
    });
    task.nodes.forEach((node) => {
      let element = nodeElements.get(node.id);
      if (!element) {
        element = document.createElement('div');
        element.className = 'node';
        element.dataset.node = node.id;
        element.tabIndex = 0;
        /* A tile holds its own port buttons, so it is a group of controls rather than one button. */
        element.setAttribute('role', 'group');
        nodeElements.set(node.id, element);
        elements.nodes.append(element);
        displayed.set(node.id, { ...node.position });
      }
      element.innerHTML = nodeInnerHtml(node);
      element.setAttribute('aria-label', `${node.name}: ${summaryText(node)}`);
      element.dataset.category = categoryOf(node);
      element.dataset.type = node.type;
      element.classList.toggle('is-selected', node.id === store.selectedId);
      placeNode(element, displayed.get(node.id));
    });
    animateToTargets();
    updateRunState();
    elements.emptyCard.hidden = task.nodes.length > 1;
    if (task.nodes.length === 1) { renderEmptyCard(); applyView(); }
  }

  /* Nodes and wires ease together when the layout changes, so the eye can follow. */
  function animateToTargets() {
    cancelAnimationFrame(positionAnimation);
    const targets = store.task.nodes.map((node) => ({ id: node.id, from: { ...displayed.get(node.id) }, to: node.position }));
    const moving = targets.some((target) => target.from.x !== target.to.x || target.from.y !== target.to.y);
    if (!moving || matchMedia('(prefers-reduced-motion: reduce)').matches) {
      targets.forEach((target) => { displayed.set(target.id, { ...target.to }); placeNode(nodeElements.get(target.id), target.to); });
      renderWires();
      return;
    }
    const startTime = performance.now();
    const step = (now) => {
      const progress = Math.min(1, (now - startTime) / 240);
      const eased = 1 - Math.pow(1 - progress, 3);
      targets.forEach((target) => {
        const current = { x: target.from.x + (target.to.x - target.from.x) * eased, y: target.from.y + (target.to.y - target.from.y) * eased };
        displayed.set(target.id, current);
        const element = nodeElements.get(target.id);
        if (element) placeNode(element, current);
      });
      renderWires();
      if (progress < 1) positionAnimation = requestAnimationFrame(step);
    };
    positionAnimation = requestAnimationFrame(step);
  }

  /* ---------- Wires ---------- */

  /* A wire going back runs in a lane under the steps it passes, since a curve would hide behind them. */
  function backLaneY(fromPoint, toPoint) {
    const passed = store.task.nodes.filter((node) => {
      const { x } = positionOf(node.id);
      return x + NODE_WIDTH >= toPoint.x && x <= fromPoint.x;
    });
    return Math.max(...passed.map((node) => positionOf(node.id).y + nodeHeight(node))) + BACK_WIRE_GAP;
  }

  function backWirePath(fromPoint, toPoint) {
    const lane = backLaneY(fromPoint, toPoint);
    const right = fromPoint.x + BACK_WIRE_GAP;
    const left = toPoint.x - BACK_WIRE_GAP;
    const radius = 14;
    return `M${fromPoint.x},${fromPoint.y} L${right - radius},${fromPoint.y} Q${right},${fromPoint.y} ${right},${fromPoint.y + radius} L${right},${lane - radius} Q${right},${lane} ${right - radius},${lane} L${left + radius},${lane} Q${left},${lane} ${left},${lane - radius} L${left},${toPoint.y + radius} Q${left},${toPoint.y} ${left + radius},${toPoint.y} L${toPoint.x},${toPoint.y}`;
  }

  function wirePath(fromPoint, toPoint) {
    if (toPoint.x <= fromPoint.x) return backWirePath(fromPoint, toPoint);
    /* A short straight run at each end lets the line meet a step head-on, so an arrow sits true. */
    const lead = options.wireLead || 0;
    if (lead) {
      const startX = fromPoint.x + lead;
      const endX = toPoint.x - lead;
      const leadHandle = Math.max(24, Math.abs(endX - startX) * 0.5);
      return `M${fromPoint.x},${fromPoint.y} L${startX},${fromPoint.y} C${startX + leadHandle},${fromPoint.y} ${endX - leadHandle},${toPoint.y} ${endX},${toPoint.y} L${toPoint.x},${toPoint.y}`;
    }
    const handle = Math.max(40, Math.abs(toPoint.x - fromPoint.x) * 0.5);
    return `M${fromPoint.x},${fromPoint.y} C${fromPoint.x + handle},${fromPoint.y} ${toPoint.x - handle},${toPoint.y} ${toPoint.x},${toPoint.y}`;
  }

  function wireEndpoints(edge) {
    const fromNode = nodeById(store.task, edge.from);
    const toNode = nodeById(store.task, edge.to);
    if (!fromNode || !toNode) return null;
    const fromPosition = positionOf(edge.from);
    const toPosition = positionOf(edge.to);
    return {
      from: { x: fromPosition.x + NODE_WIDTH, y: fromPosition.y + portY(fromNode, edge.port) },
      to: { x: toPosition.x, y: toPosition.y + HEAD_HEIGHT / 2 },
    };
  }

  function wireState(edge) {
    if (!runner.run) return '';
    const step = runner.stepFor(edge.from);
    /* Any finished visit counts, since a loop leaves by another branch on its last visit. */
    const taken = runner.run.steps.some((visit) => visit.nodeId === edge.from && visit.port === edge.port && runner.cursorMs >= visit.startMs + visit.durationMs);
    if (taken && runner.stepFor(edge.to)) return 'is-taken';
    return runner.finished || (step && runner.cursorMs >= step.startMs + step.durationMs) ? 'is-dim' : '';
  }

  function renderWires() {
    const wiresHtml = store.task.edges.map((edge) => {
      const points = wireEndpoints(edge);
      if (!points) return '';
      const path = wirePath(points.from, points.to);
      const midX = (points.from.x + points.to.x) / 2;
      const midY = points.to.x <= points.from.x ? backLaneY(points.from, points.to) : (points.from.y + points.to.y) / 2;
      return `<g class="wire ${wireState(edge)}" data-from="${edge.from}" data-to="${edge.to}" data-port="${escapeHtml(edge.port)}">
        <path class="wire-line" d="${path}"/><path class="wire-hit" d="${path}"/>
        <g class="wire-tools">
          <g class="wire-tool is-add" data-wire-action="add" transform="translate(${midX - 12},${midY})" role="button" tabindex="0" aria-label="Insert step"><circle r="10"/><path d="M-4 0H4M0 -4V4"/></g>
          <g class="wire-tool" data-wire-action="cut" transform="translate(${midX + 12},${midY})" role="button" tabindex="0" aria-label="Remove connection"><circle r="10"/><path d="M-3 -3L3 3M3 -3L-3 3"/></g>
        </g>
      </g>`;
    }).join('');
    elements.wires.innerHTML = wiresHtml + (draftWire ? `<path class="wire-draft" d="${wirePath(draftWire.from, draftWire.to)}"/>` : '');
    api.afterWires?.();
  }

  /* ---------- Run overlay ---------- */

  function updateRunState() {
    store.task.nodes.forEach((node) => {
      const element = nodeElements.get(node.id);
      if (!element) return;
      const state = runner.stateOf(node.id);
      const previous = element.dataset.state;
      if (previous !== state) {
        element.dataset.state = state;
        element.querySelector('.node-status').innerHTML = statusGlyph(state);
      }
      const step = runner.stepFor(node.id);
      const resultElement = element.querySelector('.node-result');
      const done = step && !['pending', 'running'].includes(state);
      const resultText = done ? `${formatDuration(step.durationMs)}${step.error ? ' · failed' : step.output && node.type !== 'start' ? ' · ' : ''}${step.error ? '' : node.type !== 'start' && step.output ? `<b>${escapeHtml(step.output.split('\n')[0])}</b>` : ''}` : '';
      if (resultElement.dataset.text !== resultText) { resultElement.innerHTML = resultText; resultElement.dataset.text = resultText; }
      element.querySelectorAll('.branch-row').forEach((row) => row.classList.toggle('is-chosen', !!done && step.port === row.dataset.branch));
    });
    elements.wires.querySelectorAll('.wire').forEach((wireElement) => {
      const edge = store.task.edges.find((candidate) => candidate.from === wireElement.dataset.from && candidate.port === wireElement.dataset.port);
      if (!edge) return;
      const state = wireState(edge);
      wireElement.classList.toggle('is-taken', state === 'is-taken');
      wireElement.classList.toggle('is-dim', state === 'is-dim');
    });
  }

  /* ---------- Inspector ---------- */

  function renderInspector() {
    elements.inspector.classList.toggle('is-hidden', !store.selectedNode);
  }

  /* ---------- Library & empty state ---------- */

  function renderEmptyCard() {
    const suggestions = ['command', 'http', 'ssh', 'script', 'if', 'readFile'];
    elements.emptyCard.innerHTML = `<h2>What should happen first?</h2><p>Choose a step. You can change or rearrange anything later.</p><div class="empty-options">${suggestions.map((type) => `<button type="button" data-first-step="${type}"><span class="node-icon" data-category="${ACTIONS[type].category}">${icon(ACTIONS[type].icon)}</span>${escapeHtml(ACTIONS[type].label)}</button>`).join('')}</div>`;
  }

  /* ---------- Actions ---------- */

  function requestRun() {
    DT.ui.openRunDialog(store, (values) => { store.select(null); store.startRun(values).catch((error) => DT.ui.toast(error.message, { tone: 'danger' })); }, { values: runner.run?.parameters, onFixIssue: () => { renderInspector(); } });
  }

  /* Picked by keyboard, so the keyboard should land on what was added. */
  function addAndFocus(type, target) {
    store.addStep(type, target);
    requestAnimationFrame(() => elements.inspector.querySelector('[data-field]')?.focus());
  }

  function openStepPicker(target) {
    DT.ui.openPalette({ store, handlers, stepsOnly: true, title: 'Find a step to add', onPick: (type) => addAndFocus(type, target) });
  }

  const api = DT.canvas = { options, store, runner, view, applyView, fitTarget, revealSelectedNode, renderWires, wires: elements.wires };

  const handlers = {
    run: requestRun,
    palette: () => DT.ui.openPalette({ store, handlers, onPick: (type) => addAndFocus(type) }),
    extraCommands: [
      { id: 'fit', label: 'Fit to screen', icon: 'fit', shortcut: 'F', run: fitView },
      { id: 'tidy', label: 'Tidy layout', icon: 'tidy', shortcut: 'T', run: () => store.tidy() },
    ],
    escape: () => { if (store.selectedId) store.select(null); else if (runner.run && runner.finished) runner.clear(); },
    key: (event) => {
      const key = event.key.toLowerCase();
      if (key === 'f') fitView();
      else if (key === 't') store.tidy();
      else if (key === 'a' || key === '/') { event.preventDefault(); openStepPicker(); }
    },
  };


  /* ---------- Pointer: pan, drag, wire ---------- */

  let draftWire = null;
  let gesture = null;

  elements.stage.addEventListener('pointerdown', (event) => {
    if (event.button !== 0) return;
    const portButton = event.target.closest('.port-out');
    const nodeElement = event.target.closest('.node');
    if (event.target.closest('.inspector, .empty-card')) return;
    if (event.target.closest('.wire-tool, .wire-hit')) return;
    if (portButton && nodeElement) {
      gesture = { kind: 'wire', fromId: nodeElement.dataset.node, port: portButton.dataset.port, startX: event.clientX, startY: event.clientY, moved: false };
    } else if (nodeElement) {
      const position = nodeById(store.task, nodeElement.dataset.node).position;
      gesture = { kind: 'node', nodeId: nodeElement.dataset.node, element: nodeElement, startX: event.clientX, startY: event.clientY, origin: { ...position }, moved: false };
    } else {
      gesture = { kind: 'pan', startX: event.clientX, startY: event.clientY, originX: view.x, originY: view.y, moved: false };
      elements.stage.classList.add('is-panning');
    }
    elements.stage.setPointerCapture(event.pointerId);
  });

  elements.stage.addEventListener('pointermove', (event) => {
    if (!gesture) return;
    const deltaX = event.clientX - gesture.startX;
    const deltaY = event.clientY - gesture.startY;
    if (!gesture.moved && Math.hypot(deltaX, deltaY) < 4) return;
    const justStarted = !gesture.moved;
    gesture.moved = true;
    if (gesture.kind === 'pan') {
      viewBeforeReveal = null;
      view.x = gesture.originX + deltaX; view.y = gesture.originY + deltaY; applyView();
    } else if (gesture.kind === 'node') {
      if (justStarted) { store.beginMove(); gesture.element.classList.add('is-dragging'); cancelAnimationFrame(positionAnimation); }
      const snap = (value) => Math.round(value / 8) * 8;
      const position = { x: snap(gesture.origin.x + deltaX / view.zoom), y: snap(gesture.origin.y + deltaY / view.zoom) };
      displayed.set(gesture.nodeId, position);
      store.moveNode(gesture.nodeId, position);
    } else if (gesture.kind === 'wire') {
      const fromNode = nodeById(store.task, gesture.fromId);
      const fromPosition = positionOf(gesture.fromId);
      draftWire = { from: { x: fromPosition.x + NODE_WIDTH, y: fromPosition.y + portY(fromNode, gesture.port) }, to: toWorld(event.clientX, event.clientY) };
      renderWires();
      nodeElements.forEach((element) => element.classList.remove('is-drop-target'));
      const over = document.elementsFromPoint(event.clientX, event.clientY).find((candidate) => candidate.classList?.contains('node'));
      if (over && over.dataset.node !== gesture.fromId) over.classList.add('is-drop-target');
    }
  });

  function finishGesture(event) {
    if (!gesture) return;
    const finished = gesture;
    gesture = null;
    elements.stage.classList.remove('is-panning');
    if (finished.kind === 'pan' && !finished.moved) { store.select(null); return; }
    if (finished.kind === 'node') {
      finished.element.classList.remove('is-dragging');
      if (finished.moved) store.endMove();
      else { store.select(finished.nodeId); }
    }
    if (finished.kind === 'wire') {
      nodeElements.forEach((element) => element.classList.remove('is-drop-target'));
      const dropPoint = draftWire?.to;
      draftWire = null;
      renderWires();
      if (!finished.moved) {
        if (!edgeFrom(store.task, finished.fromId, finished.port)) openStepPicker({ fromId: finished.fromId, port: finished.port });
        return;
      }
      const over = document.elementsFromPoint(event.clientX, event.clientY).find((candidate) => candidate.classList?.contains('node'));
      if (over) {
        if (!store.connect(finished.fromId, finished.port, over.dataset.node)) DT.ui.toast('A step cannot connect to itself or to the start.');
      } else {
        const offset = HEAD_HEIGHT / 2;
        DT.ui.openPalette({ store, handlers, stepsOnly: true, title: 'Find a step to add', onPick: (type) => {
          const node = store.addStep(type, { fromId: finished.fromId, port: finished.port });
          displayed.set(node.id, { x: dropPoint.x, y: dropPoint.y - offset });
          store.moveNode(node.id, { x: dropPoint.x, y: dropPoint.y - offset });
          renderNodes();
        } });
      }
    }
  }
  elements.stage.addEventListener('pointerup', finishGesture);
  elements.stage.addEventListener('pointercancel', finishGesture);

  elements.stage.addEventListener('wheel', (event) => {
    if (event.target.closest('.library, .inspector')) return;
    event.preventDefault();
    if (event.ctrlKey || event.metaKey) zoomAround(Math.exp(-event.deltaY * 0.008), event.clientX, event.clientY);
    else { view.x -= event.deltaX; view.y -= event.deltaY; applyView(); }
  }, { passive: false });

  /* Library: click adds; drag drops on a wire or free space. */
  document.addEventListener('pointerdown', (event) => {
    const item = event.target.closest('.library-item');
    if (!item) return;
    const type = item.dataset.type;
    const startX = event.clientX;
    const startY = event.clientY;
    let ghost = null;
    let dropWire = null;
    const onMove = (moveEvent) => {
      if (!ghost && Math.hypot(moveEvent.clientX - startX, moveEvent.clientY - startY) < 5) return;
      if (!ghost) {
        ghost = document.createElement('div');
        ghost.className = 'drag-ghost';
        ghost.dataset.category = ACTIONS[type].category;
        ghost.innerHTML = `<span class="node-icon" style="width:26px;height:26px">${icon(ACTIONS[type].icon)}</span>${escapeHtml(ACTIONS[type].label)}`;
        document.body.append(ghost);
      }
      ghost.style.left = `${moveEvent.clientX + 6}px`;
      ghost.style.top = `${moveEvent.clientY + 6}px`;
      dropWire?.classList.remove('is-drop');
      dropWire = nearestWire(moveEvent.clientX, moveEvent.clientY);
      dropWire?.classList.add('is-drop');
    };
    const onUp = (upEvent) => {
      document.removeEventListener('pointermove', onMove);
      document.removeEventListener('pointerup', onUp);
      if (!ghost) { store.addStep(type); return; }
      ghost.remove();
      if (dropWire) store.addStep(type, { fromId: dropWire.dataset.from, port: dropWire.dataset.port });
      else if (upEvent.target.closest('.stage') && !upEvent.target.closest('.library, .inspector')) {
        const point = toWorld(upEvent.clientX, upEvent.clientY);
        store.addStep(type, { position: { x: point.x - NODE_WIDTH / 2, y: point.y - HEAD_HEIGHT / 2 } });
      }
    };
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
  });

  function nearestWire(clientX, clientY) {
    const point = toWorld(clientX, clientY);
    let best = null;
    let bestDistance = 70;
    elements.wires.querySelectorAll('.wire').forEach((wireElement) => {
      const edge = store.task.edges.find((candidate) => candidate.from === wireElement.dataset.from && candidate.port === wireElement.dataset.port);
      const points = edge && wireEndpoints(edge);
      if (!points) return;
      const distance = Math.hypot(point.x - (points.from.x + points.to.x) / 2, point.y - (points.from.y + points.to.y) / 2);
      if (distance < bestDistance) { bestDistance = distance; best = wireElement; }
    });
    return best;
  }

  document.addEventListener('click', (event) => {
    /* Pointers add from the library on release. A keyboard only produces this click. */
    const libraryItem = event.target.closest('.library-item');
    if (libraryItem && event.detail === 0) store.addStep(libraryItem.dataset.type);
    const openPort = event.target.closest('.port-out.is-open');
    if (openPort && event.detail === 0) { /* Keyboard activation; pointers are handled by the wire gesture. */
      openStepPicker({ fromId: openPort.closest('.node').dataset.node, port: openPort.dataset.port });
      return;
    }
    const tool = event.target.closest('.wire-tool');
    if (tool) {
      const wireElement = tool.closest('.wire');
      if (tool.dataset.wireAction === 'add') openStepPicker({ fromId: wireElement.dataset.from, port: wireElement.dataset.port });
      else store.disconnect(wireElement.dataset.from, wireElement.dataset.port);
      return;
    }
    const topAction = event.target.closest('[data-action]')?.dataset.action;
    if (topAction === 'undo') store.undo();
    if (topAction === 'redo') store.redo();
    if (topAction === 'parameters') DT.ui.openParametersDialog(store);
    if (topAction === 'run') requestRun();
    if (topAction === 'add') handlers.palette();
    const viewAction = event.target.closest('[data-view]')?.dataset.view;
    if (viewAction === 'zoom-in') zoomAround(1.2);
    if (viewAction === 'zoom-out') zoomAround(1 / 1.2);
    if (viewAction === 'zoom-reset') zoomAround(1 / view.zoom);
    if (viewAction === 'fit') fitView();
    if (viewAction === 'tidy') store.tidy();
    const inspectorAction = event.target.closest('[data-inspector]')?.dataset.inspector;
    if (inspectorAction === 'close') store.select(null);
    if (inspectorAction === 'delete') DT.ui.removeWithUndo(store, store.selectedId);
    if (inspectorAction === 'duplicate') store.duplicateNode(store.selectedId);
    if (event.target.closest('[data-run-action="close"]')) runner.clear();
    const firstStep = event.target.closest('[data-first-step]');
    if (firstStep) store.addStep(firstStep.dataset.firstStep, { fromId: DT.rootNode(store.task).id });
  });

  /* SVG buttons do not turn Enter or Space into a click by themselves. */
  elements.wires.addEventListener('keydown', (event) => {
    const tool = event.target.closest('.wire-tool');
    if (!tool || (event.key !== 'Enter' && event.key !== ' ')) return;
    event.preventDefault();
    tool.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });

  elements.nodes.addEventListener('keydown', (event) => {
    if (event.key === 'Enter' && event.target.classList.contains('node')) store.select(event.target.dataset.node);
  });

  /* ---------- Store wiring ---------- */

  store.subscribe((change) => {
    if (change === 'tick') { updateRunState(); return; }
    if (change === 'run') {
      updateRunState();
      if (runner.finished && runner.run?.status === 'failed' && !store.selectedId) showFailure();
      else if (store.selectedId) renderInspector();
      return;
    }
    if (change === 'move') { store.task.nodes.forEach((node) => { const element = nodeElements.get(node.id); if (element && displayed.get(node.id)) placeNode(element, displayed.get(node.id)); }); renderWires(); return; }
    if (change === 'selection') {
      nodeElements.forEach((element, nodeId) => element.classList.toggle('is-selected', nodeId === store.selectedId));
      renderInspector();
      revealSelectedNode();
      return;
    }
    if (change === 'config') { renderNodes(); return; }
    if (change === 'task') {
      nodeElements.forEach((element) => element.remove());
      nodeElements.clear(); displayed.clear();
      renderNodes(); renderInspector(); fitView();
      return;
    }
    if (change === 'structure') { renderNodes(); renderInspector(); }
  });

  function showFailure() {
    if (options.shouldOpenFailure && !options.shouldOpenFailure()) return;
    const failedStep = runner.run.steps.find((step) => step.status === 'failed');
    if (failedStep) store.select(failedStep.nodeId);
  }

  /* ---------- Boot ---------- */

  DT.ui.bindShortcuts(store, handlers);
  renderNodes();
  renderInspector();
  fitView();
  addEventListener('resize', applyView);
}
