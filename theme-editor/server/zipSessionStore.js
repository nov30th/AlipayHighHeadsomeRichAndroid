import { createReadStream } from 'node:fs';
import { copyFile, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { dirname, isAbsolute, join, normalize, relative, resolve, sep } from 'node:path';
import { tmpdir } from 'node:os';
import AdmZip from 'adm-zip';

const defaultSessionsRoot = join(tmpdir(), 'alipay-theme-editor-sessions');

function prettyJson(value) {
  return `${JSON.stringify(value, null, '\t')}\n`;
}

async function readJson(filePath, fallback = null) {
  try {
    return JSON.parse(await readFile(filePath, 'utf8'));
  } catch (error) {
    if (fallback !== null) return fallback;
    throw error;
  }
}

function mergeMeta(existing, update) {
  return {
    ...existing,
    ...update,
    resource: Array.isArray(update.resource) ? update.resource : existing.resource || [],
  };
}

function assertInside(root, filePath) {
  const resolvedRoot = resolve(root);
  const resolvedFile = resolve(filePath);
  const rel = relative(resolvedRoot, resolvedFile);
  if (rel === '' || (!rel.startsWith('..') && !isAbsolute(rel))) {
    return resolvedFile;
  }
  throw new Error('Path is outside session root');
}

function assertSafeZipEntry(entryName) {
  const clean = normalize(entryName);
  if (isAbsolute(clean) || clean.startsWith('..') || clean.includes(`..${sep}`) || /^[a-zA-Z]:/.test(clean)) {
    throw new Error(`Unsafe zip entry: ${entryName}`);
  }
}

function collectResources(meta) {
  return Array.isArray(meta?.resource) ? meta.resource : [];
}

function isThemeMeta(meta) {
  return collectResources(meta).some((item) => String(item.position || '').startsWith('tab_bar_') || item.position === 'home_navi_bg');
}

function isSkinMeta(meta) {
  return collectResources(meta).some((item) => ['z01.0001', 'z02.0001', 'z02.0002', 'z02.0003'].includes(item.position));
}

async function findMetaRoots(workDir) {
  const zip = new AdmZip();
  const candidates = [];

  async function walk(dir) {
    const { readdir } = await import('node:fs/promises');
    const entries = await readdir(dir, { withFileTypes: true });
    for (const entry of entries) {
      const child = join(dir, entry.name);
      if (entry.isDirectory()) {
        await walk(child);
      } else if (entry.name === 'meta.json') {
        const meta = await readJson(child, {});
        candidates.push({ dir, meta });
      }
    }
  }

  await walk(workDir);
  const theme = candidates.find((candidate) => isThemeMeta(candidate.meta));
  const skin =
    candidates.find((candidate) => isSkinMeta(candidate.meta) && candidate.dir.endsWith(`${sep}ltp`)) ||
    candidates.find((candidate) => isSkinMeta(candidate.meta));

  return {
    hasTheme: Boolean(theme),
    hasSkin: Boolean(skin),
    themeRoot: theme?.dir || workDir,
    skinRoot: skin?.dir || join(theme?.dir || workDir, 'ltp'),
  };
}

function zipFolder(sourceDir, targetZip) {
  const zip = new AdmZip();
  zip.addLocalFolder(sourceDir);
  zip.writeZip(targetZip);
}

export function createZipSessionStore({ sessionsRoot = process.env.SESSIONS_ROOT || defaultSessionsRoot } = {}) {
  const root = resolve(sessionsRoot);
  const sessions = new Map();

  async function createFromZip(zipPath) {
    await mkdir(root, { recursive: true });
    const id = randomUUID();
    const workDir = join(root, id, 'work');
    await rm(join(root, id), { recursive: true, force: true });
    await mkdir(workDir, { recursive: true });

    const zip = new AdmZip(zipPath);
    for (const entry of zip.getEntries()) {
      assertSafeZipEntry(entry.entryName);
    }
    zip.extractAllTo(workDir, true);

    const roots = await findMetaRoots(workDir);
    sessions.set(id, { id, workDir, ...roots });
    return { id };
  }

  function getSession(id) {
    const session = sessions.get(id);
    if (!session) throw new Error('Unknown or expired editing session');
    return session;
  }

  async function load(id) {
    const session = getSession(id);
    const themeMeta = await readJson(join(session.themeRoot, 'meta.json'), { resource: [] });
    const skinMeta = await readJson(join(session.skinRoot, 'meta.json'), { resource: [] });
    return {
      sessionId: id,
      assetsBase: `/api/sessions/${id}/assets`,
      theme: {
        available: session.hasTheme,
        meta: session.hasTheme ? themeMeta : { resource: [] },
        resources: session.hasTheme ? collectResources(themeMeta) : [],
      },
      skin: {
        available: session.hasSkin,
        meta: session.hasSkin ? skinMeta : { resource: [] },
        resources: session.hasSkin ? collectResources(skinMeta) : [],
      },
    };
  }

  async function save(id, payload) {
    const session = getSession(id);
    if (payload.theme && session.hasTheme) {
      const file = join(session.themeRoot, 'meta.json');
      await writeFile(file, prettyJson(mergeMeta(await readJson(file, { resource: [] }), payload.theme)), 'utf8');
    }
    if (payload.skin && session.hasSkin) {
      const file = join(session.skinRoot, 'meta.json');
      await mkdir(dirname(file), { recursive: true });
      await writeFile(file, prettyJson(mergeMeta(await readJson(file, { resource: [] }), payload.skin)), 'utf8');
    }
  }

  async function saveAsset(id, area, assetName, tempPath) {
    const session = getSession(id);
    const areaRoot = area === 'skin' ? session.skinRoot : session.themeRoot;
    const target = assertInside(areaRoot, join(areaRoot, normalize(assetName)));
    await mkdir(dirname(target), { recursive: true });
    await copyFile(tempPath, target);
    return relative(areaRoot, target).replaceAll('\\', '/');
  }

  function streamAsset(id, area, assetName) {
    const session = getSession(id);
    const areaRoot = area === 'skin' ? session.skinRoot : session.themeRoot;
    return createReadStream(assertInside(areaRoot, join(areaRoot, normalize(assetName))));
  }

  async function exportZip(id) {
    const session = getSession(id);
    const outPath = join(root, id, 'edited-theme.zip');
    zipFolder(session.workDir, outPath);
    return outPath;
  }

  return {
    createFromZip,
    load,
    save,
    saveAsset,
    streamAsset,
    exportZip,
  };
}
