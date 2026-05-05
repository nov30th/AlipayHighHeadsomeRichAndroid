<script setup>
import { computed, ref } from 'vue';
import { normalizeCropSelection } from './cropMath.js';

const data = ref(null);
const sessionId = ref('');
const libraryMode = ref(false);
const libraryItems = ref({ skins: [], themes: [] });
const activeView = ref('home');
const mode = ref('theme');
const status = ref('Loading editor...');
const saving = ref(false);
const cacheBust = ref(Date.now());
const debug = ref({ open: false, raw: '', summary: '' });
const cropImage = ref(null);
const cropState = ref({
  open: false,
  area: '',
  assetName: '',
  url: '',
  start: null,
  current: null,
  dragging: false,
});

const tabs = [
  ['home', 'Home'],
  ['me', 'Me'],
  ['pay', 'Pay'],
];

const hasTheme = computed(() => Boolean(data.value?.theme?.available));
const hasSkin = computed(() => Boolean(data.value?.skin?.available));
const visibleTabs = computed(() => (hasTheme.value ? tabs : [['pay', 'Pay']]));
const visibleModes = computed(() => [
  ...(hasTheme.value ? [['theme', 'Theme']] : []),
  ...(hasSkin.value ? [['skin', 'Payment']] : []),
]);

function clone(value) {
  return JSON.parse(JSON.stringify(value));
}

function resources(area) {
  return data.value?.[area]?.resources ?? [];
}

function findResource(area, position) {
  return resources(area).find((item) => item.position === position);
}

function color(position, fallback = '#081533') {
  return findResource('theme', position)?.color || fallback;
}

function gradient(position, key, fallback) {
  return findResource('skin', position)?.gradient?.[key] || fallback;
}

function imageUrl(area, name) {
  if (!data.value || !name) return '';
  return `${data.value.assetsBase}/${encodeURIComponent(area)}/${encodeAssetPath(name)}?v=${cacheBust.value}`;
}

function encodeAssetPath(path) {
  return String(path)
    .split('/')
    .map((part) => encodeURIComponent(part))
    .join('/');
}

function resourceImage(resource) {
  if (resource.image) return resource.image;
  if (resource.imageList?.length) return resource.imageList[0].path;
  return '';
}

const homeActions = computed(() => [
  ['home_scan_icon', 'Scan'],
  ['home_pay_collect_icon', 'Pay/Collect'],
  ['home_transport_icon', 'Travel'],
  ['home_pocket_icon', 'Cards'],
]);

const tabItems = computed(() => [
  ['tab_bar_home_icon_selected', 'Home'],
  ['tab_bar_wealth_icon_normal', 'Wealth'],
  ['tab_bar_life_icon_normal', 'Life'],
  ['tab_bar_msg_icon_normal', 'Messages'],
  ['tab_bar_mime_icon_normal', 'Me'],
]);

const editableResources = computed(() => {
  const area = mode.value;
  return editableResourcesFor(area);
});

function resourceKind(resource) {
  const type = String(resource?.type || '').toLowerCase();
  if (['color', 'gradient', 'image'].includes(type)) return type;
  if (resource?.gradient) return 'gradient';
  if (resource?.image || resource?.imageList?.length) return 'image';
  if (resource?.color || resource?.['dark#color']) return 'color';
  return type;
}

function editableResourcesFor(area) {
  return resources(area).filter((item) => ['color', 'gradient', 'image'].includes(resourceKind(item)));
}

const cropSelection = computed(() => {
  if (!cropState.value.start || !cropState.value.current || !cropImage.value) return null;
  const bounds = cropImage.value.getBoundingClientRect();
  return normalizeCropSelection(cropState.value.start, cropState.value.current, {
    width: bounds.width,
    height: bounds.height,
  });
});

const canSaveCrop = computed(() => {
  const selection = cropSelection.value;
  return Boolean(selection && selection.width >= 8 && selection.height >= 8);
});

async function loadTheme(id) {
  const url = themeUrl(id);
  const response = await fetch(url);
  const rawText = await response.text();
  if (!response.ok) {
    debug.value = { open: false, raw: rawText, summary: `HTTP ${response.status} from ${url}` };
    throw new Error('Unable to load theme metadata');
  }
  const loaded = JSON.parse(rawText);
  data.value = clone(loaded);
  sessionId.value = loaded.sessionId;
  cacheBust.value = Date.now();
  chooseInitialMode(loaded);
  status.value = 'Loaded uploaded ZIP into a temporary editing session.';
  recordDebug(url, rawText, loaded);
}

function themeUrl(id = sessionId.value) {
  if (libraryMode.value) return `/api/library/${encodeURIComponent(data.value?.kind || '')}/${encodeURIComponent(data.value?.dirName || '')}`;
  return `/api/sessions/${id}/theme`;
}

function assetUploadUrl(area) {
  if (libraryMode.value) return `/api/library/${encodeURIComponent(data.value.kind)}/${encodeURIComponent(data.value.dirName)}/assets/${area}`;
  return `/api/sessions/${sessionId.value}/assets/${area}`;
}

async function uploadZip(event) {
  try {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const body = new FormData();
    body.append('file', file);
    status.value = 'Uploading and extracting ZIP...';
    const response = await fetch('/api/sessions', { method: 'POST', body });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'ZIP upload failed');
    }
    const session = await response.json();
    await loadTheme(session.id);
  } catch (error) {
    status.value = error.message;
  }
}

async function loadLibrary() {
  try {
    const response = await fetch('/api/library');
    if (!response.ok) throw new Error('Library API unavailable');
    libraryMode.value = true;
    libraryItems.value = await response.json();
    data.value = null;
    sessionId.value = '';
    status.value = 'Select a local skin or theme to edit.';
  } catch (_error) {
    libraryMode.value = false;
    status.value = 'Upload a theme ZIP to start editing.';
  }
}

async function selectLibraryItem(kind, item) {
  try {
    const url = `/api/library/${encodeURIComponent(kind)}/${encodeURIComponent(item.dirName)}`;
    const response = await fetch(url);
    const rawText = await response.text();
    if (!response.ok) {
      let parsed = {};
      try { parsed = JSON.parse(rawText); } catch (_) { /* keep as text */ }
      debug.value = { open: false, raw: rawText, summary: `HTTP ${response.status} from ${url}` };
      throw new Error(parsed.error || `Load failed (HTTP ${response.status})`);
    }
    const loaded = JSON.parse(rawText);
    data.value = clone(loaded);
    sessionId.value = '';
    cacheBust.value = Date.now();
    chooseInitialMode(loaded);
    status.value = `Editing ${item.displayName || item.dirName} directly.`;
    recordDebug(url, rawText, loaded);
  } catch (error) {
    status.value = error.message;
  }
}

function recordDebug(url, rawText, loaded) {
  const themeRes = loaded?.theme?.resources;
  const skinRes = loaded?.skin?.resources;
  const summary = [
    `URL: ${url}`,
    `theme.available=${loaded?.theme?.available} resources: type=${typeof themeRes} isArray=${Array.isArray(themeRes)} length=${Array.isArray(themeRes) ? themeRes.length : 'n/a'}`,
    `skin.available=${loaded?.skin?.available} resources: type=${typeof skinRes} isArray=${Array.isArray(skinRes)} length=${Array.isArray(skinRes) ? skinRes.length : 'n/a'}`,
    Array.isArray(themeRes) ? `theme.kinds=${themeRes.map((r) => resourceKind(r)).join(',')}` : `theme.resources(raw)=${JSON.stringify(themeRes)}`,
    Array.isArray(skinRes) ? `skin.kinds=${skinRes.map((r) => resourceKind(r)).join(',')}` : `skin.resources(raw)=${JSON.stringify(skinRes)}`,
  ].join('\n');
  debug.value = { open: debug.value.open, raw: rawText, summary };
  if (typeof window !== 'undefined') {
    window.__themeData = loaded;
    console.log('[theme-editor] loaded', loaded);
    console.log('[theme-editor] summary\n' + summary);
  }
}

function chooseInitialMode(loaded) {
  const themeEditable = (loaded.theme?.resources || []).filter((item) => ['color', 'gradient', 'image'].includes(resourceKind(item)));
  const skinEditable = (loaded.skin?.resources || []).filter((item) => ['color', 'gradient', 'image'].includes(resourceKind(item)));
  if (themeEditable.length > 0) {
    mode.value = 'theme';
    activeView.value = 'home';
  } else if (skinEditable.length > 0) {
    mode.value = 'skin';
    activeView.value = 'pay';
  } else {
    mode.value = loaded.theme?.available ? 'theme' : 'skin';
    activeView.value = loaded.theme?.available ? 'home' : 'pay';
  }
}

async function saveTheme() {
  try {
    saving.value = true;
    status.value = 'Saving edits to the temporary session...';
    const response = await fetch(themeUrl(), {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        theme: { resource: resources('theme') },
        skin: { resource: resources('skin') },
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || 'Save failed');
    }
    status.value = libraryMode.value ? 'Saved directly to local files.' : 'Saved. Download the ZIP when you are ready.';
  } catch (error) {
    status.value = error.message;
  } finally {
    saving.value = false;
  }
}

async function uploadAsset(area, resource, event) {
  try {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const assetName = resourceImage(resource);
    const body = new FormData();
    body.append('name', assetName);
    body.append('file', file);
    status.value = `Replacing ${assetName}...`;
    const response = await fetch(assetUploadUrl(area), { method: 'POST', body });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'Upload failed');
    }
    cacheBust.value = Date.now();
    status.value = `Replaced ${assetName}.`;
  } catch (error) {
    status.value = error.message;
  }
}

function openCrop(area, resource) {
  const assetName = resourceImage(resource);
  cropState.value = {
    open: true,
    area,
    assetName,
    url: imageUrl(area, assetName),
    start: null,
    current: null,
    dragging: false,
  };
}

function closeCrop() {
  cropState.value = {
    open: false,
    area: '',
    assetName: '',
    url: '',
    start: null,
    current: null,
    dragging: false,
  };
}

function cropPointer(event) {
  const bounds = cropImage.value.getBoundingClientRect();
  return {
    x: Math.max(0, Math.min(event.clientX - bounds.left, bounds.width)),
    y: Math.max(0, Math.min(event.clientY - bounds.top, bounds.height)),
  };
}

function startCrop(event) {
  const point = cropPointer(event);
  cropState.value.start = point;
  cropState.value.current = point;
  cropState.value.dragging = true;
}

function moveCrop(event) {
  if (!cropState.value.dragging) return;
  cropState.value.current = cropPointer(event);
}

function finishCrop() {
  cropState.value.dragging = false;
}

async function canvasToBlob(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob((blob) => {
      if (blob) resolve(blob);
      else reject(new Error('Unable to create cropped image'));
    }, 'image/png');
  });
}

async function saveCrop() {
  try {
    if (!canSaveCrop.value || !cropImage.value) {
      status.value = 'Select a crop rectangle first.';
      return;
    }

    const image = cropImage.value;
    const display = image.getBoundingClientRect();
    const selection = cropSelection.value;
    const scaleX = image.naturalWidth / display.width;
    const scaleY = image.naturalHeight / display.height;
    const sourceX = Math.round(selection.x * scaleX);
    const sourceY = Math.round(selection.y * scaleY);
    const sourceWidth = Math.round(selection.width * scaleX);
    const sourceHeight = Math.round(selection.height * scaleY);
    const canvas = document.createElement('canvas');
    canvas.width = sourceWidth;
    canvas.height = sourceHeight;
    canvas.getContext('2d').drawImage(image, sourceX, sourceY, sourceWidth, sourceHeight, 0, 0, sourceWidth, sourceHeight);

    const body = new FormData();
    body.append('name', cropState.value.assetName);
    body.append('file', await canvasToBlob(canvas), cropState.value.assetName);
    status.value = `Saving cropped ${cropState.value.assetName}...`;
    const response = await fetch(assetUploadUrl(cropState.value.area), { method: 'POST', body });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'Crop save failed');
    }
    cacheBust.value = Date.now();
    closeCrop();
    status.value = 'Cropped image saved and overwritten.';
  } catch (error) {
    status.value = error.message;
  }
}

function downloadZip() {
  if (libraryMode.value || !sessionId.value) return;
  window.location.href = `/api/sessions/${sessionId.value}/download`;
}

async function copyDebug() {
  const text = (debug.value.summary || '') + '\n\n--- RAW ---\n' + (debug.value.raw || '');
  try {
    await navigator.clipboard.writeText(text);
    status.value = 'Debug info copied to clipboard.';
  } catch (_error) {
    status.value = 'Copy failed - select the text manually.';
  }
}

function cancelEdit() {
  data.value = null;
  sessionId.value = '';
  activeView.value = 'home';
  mode.value = 'theme';
  status.value = libraryMode.value ? 'Select a local skin or theme to edit.' : 'Upload a theme ZIP to start editing.';
}

loadLibrary();
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div>
        <h1>Alipay Theme Editor</h1>
        <p>{{ status }}</p>
      </div>
      <div class="actions">
        <label class="upload-button">
          Upload ZIP
          <input type="file" accept=".zip,application/zip" @change="uploadZip" />
        </label>
        <button class="primary" :disabled="saving || !data" @click="saveTheme">Save</button>
        <button v-if="!libraryMode" :disabled="!data" @click="downloadZip">Download ZIP</button>
        <button :disabled="!data" @click="cancelEdit">Cancel Edit</button>
        <button @click="debug.open = !debug.open">{{ debug.open ? 'Hide Debug' : 'Debug' }}</button>
      </div>
    </header>

    <section v-if="debug.open" class="debug-panel">
      <header>
        <strong>Debug</strong>
        <button @click="copyDebug">Copy</button>
        <button @click="debug.open = false">Close</button>
      </header>
      <pre class="debug-summary">{{ debug.summary || 'No data loaded yet.' }}</pre>
      <details>
        <summary>Raw response ({{ (debug.raw || '').length }} bytes)</summary>
        <pre class="debug-raw">{{ debug.raw }}</pre>
      </details>
    </section>

    <section v-if="!data && libraryMode" class="library-start">
      <section>
        <h2>Local Skins</h2>
        <div class="library-grid">
          <button v-for="item in libraryItems.skins" :key="`skin-${item.dirName}`" @click="selectLibraryItem('skins', item)">
            <strong>{{ item.displayName || item.dirName }}</strong>
            <span>{{ item.dirName }}</span>
          </button>
        </div>
      </section>
      <section>
        <h2>Local Themes</h2>
        <div class="library-grid">
          <button v-for="item in libraryItems.themes" :key="`theme-${item.dirName}`" @click="selectLibraryItem('themes', item)">
            <strong>{{ item.displayName || item.dirName }}</strong>
            <span>{{ item.dirName }}</span>
          </button>
        </div>
      </section>
    </section>

    <section v-else-if="!data" class="empty-state">
      <h2>Upload a theme package</h2>
      <p>The first version expects a ZIP with meta.json at the theme root and optionally ltp/meta.json for the payment skin.</p>
      <label class="large-upload">
        Choose ZIP
        <input type="file" accept=".zip,application/zip" @change="uploadZip" />
      </label>
    </section>

    <section v-else class="layout">
      <aside class="panel">
        <div class="segmented">
          <button
            v-for="[key, label] in visibleModes"
            :key="key"
            :class="{ active: mode === key }"
            @click="mode = key"
          >
            {{ label }}
          </button>
        </div>

        <div class="resource-list">
          <div v-if="editableResources.length === 0" class="resource-empty">
            No editable color, gradient, or image resources were found in this item.
          </div>
          <article v-for="resource in editableResources" :key="`${mode}-${resource.position}`" class="resource-row">
            <div>
              <strong>{{ resource.description || resource.position }}</strong>
              <span>{{ resource.position }}</span>
            </div>

            <label v-if="resourceKind(resource) === 'color'" class="color-control">
              <input v-model="resource.color" type="color" />
              <input v-model="resource.color" />
            </label>

            <div v-else-if="resourceKind(resource) === 'gradient'" class="gradient-control">
              <label>
                <span>Start</span>
                <input v-model="resource.gradient.start" type="color" />
              </label>
              <label>
                <span>End</span>
                <input v-model="resource.gradient.end" type="color" />
              </label>
            </div>

            <div v-else class="image-control">
              <img :src="imageUrl(mode, resourceImage(resource))" alt="" />
              <label class="file-button">
                Replace
                <input type="file" accept="image/*" @change="uploadAsset(mode, resource, $event)" />
              </label>
              <button class="file-button" @click="openCrop(mode, resource)">Crop</button>
            </div>
          </article>
        </div>
      </aside>

      <section class="preview-area">
        <div class="preview-tabs">
          <button
            v-for="[key, label] in visibleTabs"
            :key="key"
            :class="{ active: activeView === key }"
            @click="activeView = key"
          >
            {{ label }}
          </button>
        </div>

        <div class="phone">
          <template v-if="activeView !== 'pay'">
            <div
              class="navi"
              :style="{
                backgroundImage: `linear-gradient(${color(activeView === 'me' ? 'me_navi_mask' : 'home_navi_mask')}cc, ${color(activeView === 'me' ? 'me_navi_mask' : 'home_navi_mask')}22), url(${imageUrl('theme', activeView === 'me' ? 'me_navi_bg' : 'home_navi_bg')})`,
                color: color(activeView === 'me' ? 'me_navi_theme_fg_color' : 'home_navi_theme_fg_color', '#ffffff'),
              }"
            >
              <span>{{ activeView === 'me' ? 'Me' : 'Alipay' }}</span>
              <strong>{{ activeView === 'me' ? 'Account Center' : 'Pay Collect Scan' }}</strong>
            </div>

            <div class="quick-actions">
              <div v-for="[icon, label] in homeActions" :key="icon">
                <img :src="imageUrl('theme', icon)" alt="" />
                <span>{{ label }}</span>
              </div>
            </div>

            <div class="content-blocks">
              <div></div>
              <div></div>
              <div></div>
            </div>

            <nav class="tabbar" :style="{ backgroundColor: color('tab_bar_theme_color') }">
              <div v-for="[icon, label] in tabItems" :key="icon">
                <img :src="imageUrl('theme', icon)" alt="" />
                <span :style="{ color: icon.includes('selected') ? color('tab_bar_text_color_selected') : color('tab_bar_text_color_normal') }">
                  {{ label }}
                </span>
              </div>
            </nav>
          </template>

          <template v-else>
            <div class="payment-bg" :style="{ backgroundImage: `url(${imageUrl('skin', 'background_2x1')})` }">
              <section class="pay-card">
                <header
                  :style="{
                    background: `linear-gradient(90deg, ${gradient('z02.0001', 'start', '#D9B58D')}, ${gradient('z02.0001', 'end', '#F9ECD2')})`,
                  }"
                >
                  <img class="logo" :src="imageUrl('skin', 'logo')" alt="" />
                  <img class="mask" :src="imageUrl('skin', 'mask')" alt="" />
                </header>
                <div class="qr"></div>
                <div class="barcode"></div>
                <button>Pay Now</button>
              </section>
            </div>
          </template>
        </div>
      </section>
    </section>

    <div v-if="cropState.open" class="crop-modal" @pointerup="finishCrop">
      <section class="crop-dialog">
        <header>
          <div>
            <h2>Crop Image</h2>
            <p>{{ cropState.assetName }}</p>
          </div>
          <button @click="closeCrop">Close</button>
        </header>
        <div class="crop-stage">
          <div class="crop-image-wrap">
            <img
              ref="cropImage"
              :src="cropState.url"
              alt=""
              draggable="false"
              @pointerdown.prevent="startCrop"
              @pointermove.prevent="moveCrop"
              @pointerup.prevent="finishCrop"
            />
            <div
              v-if="cropSelection"
              class="crop-box"
              :style="{
                left: `${cropSelection.x}px`,
                top: `${cropSelection.y}px`,
                width: `${cropSelection.width}px`,
                height: `${cropSelection.height}px`,
              }"
            ></div>
          </div>
        </div>
        <footer>
          <button @click="closeCrop">Cancel</button>
          <button class="primary" :disabled="!canSaveCrop" @click="saveCrop">Save Crop</button>
        </footer>
      </section>
    </div>
  </main>
</template>
