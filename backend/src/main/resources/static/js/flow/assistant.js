/* Build with AI: the person says what they want, and the assistant proposes a task for them to approve. The
   conversation stays in the page, so it carries on across tasks until the page is reloaded. */
export default function boot() {
  const { store } = DT.canvas;
  const { icon, escapeHtml, toast } = DT.ui;
  const conversation = [];
  let working = null;

  const messageHtml = ({ role, content }) => `<div class="ai-message is-${role}">${escapeHtml(content)}</div>`;
  const workingHtml = () => (working ? `<div class="ai-working" role="status">${working.steps.map((step, index) => `<span class="${index === working.steps.length - 1 ? 'is-current' : ''}">${index === working.steps.length - 1 ? '<i class="ai-dot"></i>' : icon('check')}${escapeHtml(step)}</span>`).join('')}</div>` : '');

  function mount(host) {
    host.innerHTML = `<div class="side-head"><h2>${icon('sparkles')}Build with AI</h2><p>Say what should happen. The AI proposes a task, and nothing runs until you approve it.</p></div>
      <div class="side-card ai-log" id="aiLog"></div>
      <form class="ai-form" id="aiForm"><textarea id="aiInput" rows="3" placeholder="When a form arrives, sort it with AI and save it" aria-label="What should the task do?"></textarea>
        <button type="submit" class="ui-button is-primary" aria-label="Send">${icon('send')}</button></form>`;
    const input = host.querySelector('#aiInput');
    const draw = () => {
      const log = host.querySelector('#aiLog');
      if (!log) return;
      log.innerHTML = conversation.length || working ? `${conversation.map(messageHtml).join('')}${workingHtml()}` : '<p class="ai-hint">Such as: every morning, read new rows from a sheet and post a summary to Slack.</p>';
      log.scrollTop = log.scrollHeight;
      host.querySelector('[type="submit"]').disabled = !!working;
    };
    host.querySelector('#aiForm').addEventListener('submit', (event) => {
      event.preventDefault();
      const content = input.value.trim();
      if (!content || working) return;
      input.value = '';
      ask(content, draw);
    });
    input.addEventListener('keydown', (event) => {
      if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); host.querySelector('#aiForm').requestSubmit(); }
    });
    draw();
    input.focus();
  }

  async function ask(content, draw) {
    conversation.push({ role: 'user', content });
    working = { steps: ['Thinking'], proposed: null };
    draw();
    try {
      await store.askAssistant(conversation, (event) => {
        if (event.type === 'step') working.steps.push(event.text);
        if (event.type === 'proposal') working.proposed = event.task;
        if (event.type === 'answer') conversation.push({ role: 'assistant', content: event.text });
        if (event.type === 'error') toast(event.text, { tone: 'danger' });
        draw();
      });
      /* The proposal opens with its changes marked, ready to approve or reject. */
      if (working.proposed) {
        await store.loadTaskList();
        await store.switchTask(working.proposed);
      }
    } catch (error) {
      toast(error.message, { tone: 'danger' });
    } finally {
      working = null;
      draw();
    }
  }

  DT.assistant = { mount };
}
