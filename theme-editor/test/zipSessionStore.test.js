import assert from 'node:assert/strict';
import { mkdir, mkdtemp, readFile, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';
import AdmZip from 'adm-zip';
import { createZipSessionStore } from '../server/zipSessionStore.js';

async function createZipFixture(root) {
  const source = join(root, 'source');
  await mkdir(join(source, 'ltp'), { recursive: true });
  await writeFile(
    join(source, 'meta.json'),
    JSON.stringify({
      skinId: 'SKIN_THEME',
      description: 'Theme',
      resource: [{ type: 'color', position: 'tab_bar_theme_color', color: '#112233' }],
    }),
  );
  await writeFile(
    join(source, 'ltp', 'meta.json'),
    JSON.stringify({
      skinId: 'SKIN_PAY',
      resource: [{ type: 'gradient', position: 'z02.0001', gradient: { start: '#000000', end: '#ffffff' } }],
    }),
  );
  await writeFile(join(source, 'home_navi_bg'), 'fake image');

  const zipPath = join(root, 'theme.zip');
  const zip = new AdmZip();
  zip.addLocalFolder(source);
  zip.writeZip(zipPath);
  return zipPath;
}

async function createPaymentOnlyZipFixture(root) {
  const source = join(root, 'payment-source');
  await mkdir(source, { recursive: true });
  await writeFile(
    join(source, 'meta.json'),
    JSON.stringify({
      skinId: 'SKIN_PAY_ONLY',
      resource: [
        { type: 'image', position: 'z01.0001', imageList: [{ path: 'background_2x1', resolution: 2 }] },
        { type: 'gradient', position: 'z02.0001', gradient: { start: '#000000', end: '#ffffff' } },
      ],
    }),
  );
  await writeFile(join(source, 'background_2x1'), 'fake image');

  const zipPath = join(root, 'payment.zip');
  const zip = new AdmZip();
  zip.addLocalFolder(source);
  zip.writeZip(zipPath);
  return zipPath;
}

test('creates a temporary editing session from an uploaded theme zip', async () => {
  const root = await mkdtemp(join(tmpdir(), 'theme-editor-zip-load-'));
  const zipPath = await createZipFixture(root);
  const store = createZipSessionStore({ sessionsRoot: join(root, 'sessions') });

  const session = await store.createFromZip(zipPath);
  const data = await store.load(session.id);

  assert.equal(data.sessionId, session.id);
  assert.equal(data.theme.meta.skinId, 'SKIN_THEME');
  assert.equal(data.skin.meta.skinId, 'SKIN_PAY');
  assert.equal(data.theme.resources[0].color, '#112233');
});

test('marks payment-only zips so the UI can hide theme editing', async () => {
  const root = await mkdtemp(join(tmpdir(), 'theme-editor-payment-only-'));
  const zipPath = await createPaymentOnlyZipFixture(root);
  const store = createZipSessionStore({ sessionsRoot: join(root, 'sessions') });

  const session = await store.createFromZip(zipPath);
  const data = await store.load(session.id);

  assert.equal(data.theme.available, false);
  assert.equal(data.theme.resources.length, 0);
  assert.equal(data.skin.available, true);
  assert.equal(data.skin.meta.skinId, 'SKIN_PAY_ONLY');
});

test('saves edits into the temp session and exports a modified zip', async () => {
  const root = await mkdtemp(join(tmpdir(), 'theme-editor-zip-save-'));
  const zipPath = await createZipFixture(root);
  const store = createZipSessionStore({ sessionsRoot: join(root, 'sessions') });
  const session = await store.createFromZip(zipPath);

  await store.save(session.id, {
    theme: { resource: [{ type: 'color', position: 'tab_bar_theme_color', color: '#abcdef' }] },
    skin: { resource: [{ type: 'gradient', position: 'z02.0001', gradient: { start: '#111111', end: '#222222' } }] },
  });

  const exported = await store.exportZip(session.id);
  const zip = new AdmZip(await readFile(exported));
  const themeMeta = JSON.parse(zip.readAsText('meta.json'));
  const skinMeta = JSON.parse(zip.readAsText('ltp/meta.json'));

  assert.equal(themeMeta.resource[0].color, '#abcdef');
  assert.equal(skinMeta.resource[0].gradient.end, '#222222');
  assert.ok(zip.getEntry('home_navi_bg'));
});
