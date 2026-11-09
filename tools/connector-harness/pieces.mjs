// Loads an Activepieces piece for the recorder.
import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';

/** Each piece gets its own folder, so that several pieces can be loaded at the same time. */
export async function loadPiece(name, version) {
  const folder = join(import.meta.dirname, '.pieces', name.replace(/[@/]/g, '_'), version);
  if (!existsSync(join(folder, 'node_modules', name))) {
    execFileSync('npm', ['install', '--prefix', folder, '--ignore-scripts', '--no-audit', '--no-fund', '--no-save', `${name}@${version}`], { stdio: 'inherit' });
  }
  const module = createRequire(join(folder, 'package.json'))(name);
  const found = Object.values(module).find((value) => value && typeof value.getAction === 'function');
  if (!found) throw new Error(`${name} exports no piece.`);
  return found;
}
