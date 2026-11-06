import bootEngine from './flow/engine';
import bootUi from './flow/ui';
import bootCanvas from './flow/canvas';
import bootWorkbench from './flow/workbench-core';
import bootFlow from './flow/flow';

bootEngine().then(() => {
  /* Tiles are taller and narrower than cards, so they get their own spacing. */
  window.DT.canvasOptions = { nodeWidth: 176, headHeight: 132, rowHeight: 30, wireLead: 12, topInset: 76, bottomInset: 92, leftInset: 92, rightInset: 20 };
  bootUi();
  bootCanvas();
  bootWorkbench();
  bootFlow();
}, (error) => {
  document.body.textContent = `DopplerTask could not reach its server. ${error.message}`;
});
