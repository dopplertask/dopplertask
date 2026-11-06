import bootEngine from './flow/engine';
import bootUi from './flow/ui';
import bootCanvas from './flow/canvas';
import bootWorkbench from './flow/workbench';
import bootFlow from './flow/flow';

bootEngine().then(() => {
  /* Tiles are taller and narrower than cards, so they get their own spacing. */
  window.DT.canvasOptions = { nodeWidth: 176, headHeight: 132, rowHeight: 30, wireLead: 12, topInset: 76, bottomInset: 92, leftInset: 92, rightInset: 20 };
  bootUi();
  bootCanvas();
  bootWorkbench();
  bootFlow();
}, (error) => {
  if (error.status === 401) askForToken();
  else document.body.textContent = `DopplerTask could not reach its server. ${error.message}`;
});

async function askForToken() {
  const status = await fetch('/sso/status').then((response) => response.json());
  const signInError = new URLSearchParams(window.location.search).get('signInError');
  const tokenForm = `<label class="ui-label" for="accessToken">Access token</label>
    <input class="ui-input" id="accessToken" type="password" autocomplete="current-password">
    <button type="submit" class="ui-button ${status.sso ? '' : 'is-primary'}">Open with token</button>`;
  document.body.innerHTML = `<form class="sign-in">
    <h1>DopplerTask</h1>
    <p class="sign-in-error" role="alert" hidden></p>
    ${status.sso ? '<a class="ui-button is-primary" href="/sso/login">Sign in with SSO</a>' : ''}
    ${status.sso && status.tokens ? '<p class="sign-in-or">or</p>' : ''}
    ${status.tokens ? tokenForm : ''}
  </form>`;
  if (signInError) {
    const message = document.querySelector('.sign-in-error');
    message.textContent = signInError;
    message.hidden = false;
  }
  document.querySelector('form').addEventListener('submit', (event) => {
    event.preventDefault();
    sessionStorage.setItem('accessToken', document.getElementById('accessToken').value);
    window.location.reload();
  });
}
