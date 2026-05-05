<script setup>
import { computed, ref } from 'vue';
import { normalizeCropSelection } from './cropMath.js';

const data = ref(null);
const sessionId = ref('');
const libraryMode = ref(false);
const libraryItems = ref({ skins: [], themes: [] });
const activeView = ref('home');
const mode = ref('theme');
const status = ref('正在加载皮肤修改器...');
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
  ['home', '首页'],
  ['me', '我的'],
  ['pay', '付款码'],
];

const hasTheme = computed(() => Boolean(data.value?.theme?.available));
const hasSkin = computed(() => Boolean(data.value?.skin?.available));
const editingTheme = computed(() => libraryMode.value && data.value?.kind === 'themes');
const editingSkin = computed(() => libraryMode.value && data.value?.kind === 'skins');
const visibleTabs = computed(() => (hasTheme.value ? tabs : [['pay', '付款码']]));
const visibleModes = computed(() => [
  ...(hasTheme.value ? [['theme', '主题']] : []),
  ...(hasSkin.value ? [['skin', '付款皮肤']] : []),
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

function skinImageFor(position) {
  const resource = findResource('skin', position);
  if (!resource) return '';
  const name = resourceImage(resource);
  return name ? imageUrl('skin', name) : '';
}

function paymentBgUrl() {
  const resource = findResource('skin', 'z01.0001');
  if (resource?.imageList?.length) {
    const match = resource.imageList.find((item) => String(item?.path || '').includes('2x1'))
      || resource.imageList[0];
    if (match?.path) return imageUrl('skin', match.path);
  }
  if (resource?.image) return imageUrl('skin', resource.image);
  return imageUrl('skin', 'background_2x1');
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
  ['home_scan_icon', '扫一扫'],
  ['home_pay_collect_icon', '收付款'],
  ['home_transport_icon', '出行'],
  ['home_pocket_icon', '卡包'],
]);

const tabBarBgImage = computed(() => {
  const list = resources('theme');
  const resource = list.find((item) => {
    const pos = String(item?.position || '');
    return (pos === 'tab_bar_bg' || pos.startsWith('tab_bar_bg_')) && resourceKind(item) === 'image';
  });
  if (!resource) return '';
  const name = resourceImage(resource);
  return name ? imageUrl('theme', name) : '';
});

const tabItems = computed(() => [
  { base: 'tab_bar_home_icon', label: '首页' },
  { base: 'tab_bar_wealth_icon', label: '理财' },
  { base: 'tab_bar_life_icon', label: '生活' },
  { base: 'tab_bar_msg_icon', label: '消息' },
  { base: 'tab_bar_mime_icon', label: '我的' },
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
    throw new Error('无法加载主题配置');
  }
  const loaded = JSON.parse(rawText);
  data.value = clone(loaded);
  sessionId.value = loaded.sessionId;
  cacheBust.value = Date.now();
  chooseInitialMode(loaded);
  status.value = '已载入 ZIP，可临时编辑。';
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
    status.value = '正在上传并解压 ZIP...';
    const response = await fetch('/api/sessions', { method: 'POST', body });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || 'ZIP 上传失败');
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
    if (!response.ok) throw new Error('本地皮肤库接口不可用');
    libraryMode.value = true;
    libraryItems.value = await response.json();
    data.value = null;
    sessionId.value = '';
    status.value = '请选择本地付款皮肤或主题进行编辑。';
  } catch (_error) {
    libraryMode.value = false;
    status.value = '请上传主题 ZIP 开始编辑。';
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
      throw new Error(parsed.error || `加载失败 (HTTP ${response.status})`);
    }
    const loaded = JSON.parse(rawText);
    data.value = clone(loaded);
    sessionId.value = '';
    cacheBust.value = Date.now();
    chooseInitialMode(loaded);
    status.value = `正在直接编辑 ${item.displayName || item.dirName}。`;
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
    status.value = '正在保存修改...';
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
      throw new Error(body.error || '保存失败');
    }
    status.value = libraryMode.value ? '已保存到本地文件。' : '已保存，可下载 ZIP。';
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
    status.value = `正在替换 ${assetName}...`;
    const response = await fetch(assetUploadUrl(area), { method: 'POST', body });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || '上传失败');
    }
    cacheBust.value = Date.now();
    status.value = `已替换 ${assetName}。`;
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
    status.value = '请先选择裁剪区域。';
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
    status.value = `正在保存裁剪后的 ${cropState.value.assetName}...`;
    const response = await fetch(assetUploadUrl(cropState.value.area), { method: 'POST', body });
    if (!response.ok) {
      const error = await response.json().catch(() => ({}));
      throw new Error(error.error || '裁剪保存失败');
    }
    cacheBust.value = Date.now();
    closeCrop();
    status.value = '已保存并覆盖裁剪后的图片。';
  } catch (error) {
    status.value = error.message;
  }
}

async function downloadZip() {
  if (libraryMode.value || !sessionId.value) return;
  try {
    saving.value = true;
    status.value = '下载前正在保存修改...';
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
      throw new Error(body.error || '保存失败');
    }
    status.value = '正在下载 ZIP...';
    window.location.href = `/api/sessions/${sessionId.value}/download`;
  } catch (error) {
    status.value = error.message;
  } finally {
    saving.value = false;
  }
}

async function activateAndApply() {
  if (!libraryMode.value || !data.value) return;
  try {
    saving.value = true;
    status.value = editingTheme.value ? '正在启用并应用主题...' : '正在保存并更新付款皮肤...';
    const url = `/api/library/${encodeURIComponent(data.value.kind)}/${encodeURIComponent(data.value.dirName)}/activate`;
    const response = await fetch(url, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        theme: { resource: resources('theme') },
        skin: { resource: resources('skin') },
      }),
    });
    if (!response.ok) {
      const body = await response.json().catch(() => ({}));
      throw new Error(body.error || (editingTheme.value ? '启用失败' : '更新失败'));
    }
    if (editingTheme.value) {
      status.value = '已启用。请完全关闭并重新打开支付宝，让新主题复制生效。';
    } else {
      status.value = '已保存并创建付款皮肤更新请求，请重新打开支付宝付款码生效。';
    }
    if (editingTheme.value && typeof window !== 'undefined') {
      window.alert('主题已启用并应用。\n\n请完全关闭并重新打开支付宝，让新主题复制生效。');
    }
  } catch (error) {
    status.value = error.message;
  } finally {
    saving.value = false;
  }
}

async function copyDebug() {
  const text = (debug.value.summary || '') + '\n\n--- RAW ---\n' + (debug.value.raw || '');
  try {
    await navigator.clipboard.writeText(text);
    status.value = '调试信息已复制。';
  } catch (_error) {
    status.value = '复制失败，请手动选择文本。';
  }
}

function openSiblingColorPicker(event) {
  const target = event.currentTarget;
  const sibling = target?.parentElement?.querySelector('input[type="color"]');
  if (!sibling) return;
  if (typeof sibling.showPicker === 'function') {
    try {
      sibling.showPicker();
      return;
    } catch (_error) {
      // fall through to click fallback
    }
  }
  sibling.click();
}

async function downloadAsset(area, resource) {
  const assetName = resourceImage(resource);
  if (!assetName) return;
  try {
    const url = imageUrl(area, assetName);
    const response = await fetch(url);
    if (!response.ok) throw new Error(`下载失败 (HTTP ${response.status})`);
    const blob = await response.blob();
    const objectUrl = URL.createObjectURL(blob);
    const baseName = String(assetName).split('/').pop() || assetName;
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = baseName;
    document.body.appendChild(anchor);
    anchor.click();
    document.body.removeChild(anchor);
    setTimeout(() => URL.revokeObjectURL(objectUrl), 1000);
    status.value = `已下载 ${baseName}。`;
  } catch (error) {
    status.value = error.message;
  }
}

function cancelEdit() {
  data.value = null;
  sessionId.value = '';
  activeView.value = 'home';
  mode.value = 'theme';
  status.value = libraryMode.value ? '请选择本地付款皮肤或主题进行编辑。' : '请上传主题 ZIP 开始编辑。';
}

loadLibrary();
</script>

<template>
  <main class="workspace">
    <header class="topbar">
      <div>
        <h1>HoHo皮肤修改器</h1>
        <p>{{ status }}</p>
      </div>
      <div class="actions">
        <label v-if="!libraryMode" class="upload-button">
          上传 ZIP
          <input type="file" accept=".zip,application/zip" @change="uploadZip" />
        </label>
        <button v-if="editingTheme" class="primary" :disabled="saving || !data" @click="activateAndApply">启用并应用</button>
        <button v-if="editingSkin" class="primary" :disabled="saving || !data" @click="activateAndApply">保存并更新</button>
        <button v-if="libraryMode" :disabled="saving || !data" @click="saveTheme">保存</button>
        <button v-if="!libraryMode" class="primary" :disabled="saving || !data" @click="downloadZip">下载 ZIP</button>
        <button :disabled="!data" @click="cancelEdit">返回</button>
        <button @click="debug.open = !debug.open">{{ debug.open ? '隐藏调试' : '调试' }}</button>
      </div>
    </header>

    <section v-if="debug.open" class="debug-panel">
      <header>
        <strong>调试</strong>
        <button @click="copyDebug">复制</button>
        <button @click="debug.open = false">关闭</button>
      </header>
      <pre class="debug-summary">{{ debug.summary || '尚未加载数据。' }}</pre>
      <details>
        <summary>原始响应 ({{ (debug.raw || '').length }} 字节)</summary>
        <pre class="debug-raw">{{ debug.raw }}</pre>
      </details>
    </section>

    <section v-if="!data && libraryMode" class="library-start">
      <section>
        <h2>本地付款皮肤</h2>
        <div class="library-grid">
          <button v-for="item in libraryItems.skins" :key="`skin-${item.dirName}`" @click="selectLibraryItem('skins', item)">
            <strong>{{ item.displayName || item.dirName }}</strong>
            <span>{{ item.dirName }}</span>
          </button>
        </div>
      </section>
      <section>
        <h2>本地主题</h2>
        <div class="library-grid">
          <button v-for="item in libraryItems.themes" :key="`theme-${item.dirName}`" @click="selectLibraryItem('themes', item)">
            <strong>{{ item.displayName || item.dirName }}</strong>
            <span>{{ item.dirName }}</span>
          </button>
        </div>
      </section>
    </section>

    <section v-else-if="!data" class="empty-state">
      <h2>上传主题包</h2>
      <p>ZIP 顶层需要包含主题 meta.json；如果包含付款皮肤，请放在 ltp/meta.json。</p>
      <label class="large-upload">
        选择 ZIP
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
            当前项目没有可编辑的颜色、渐变或图片资源。
          </div>
          <article v-for="resource in editableResources" :key="`${mode}-${resource.position}`" class="resource-row">
            <div>
              <strong>{{ resource.description || resource.position }}</strong>
              <span>{{ resource.position }}</span>
            </div>

            <label v-if="resourceKind(resource) === 'color'" class="color-control">
              <input v-model="resource.color" type="color" />
              <input
                v-model="resource.color"
                @focus="openSiblingColorPicker"
                @click="openSiblingColorPicker"
              />
            </label>

            <div v-else-if="resourceKind(resource) === 'gradient'" class="gradient-control">
              <label>
                <span>起始</span>
                <input v-model="resource.gradient.start" type="color" />
              </label>
              <label>
                <span>结束</span>
                <input v-model="resource.gradient.end" type="color" />
              </label>
            </div>

            <div v-else class="image-control">
              <img
                :src="imageUrl(mode, resourceImage(resource))"
                alt=""
                title="点击下载"
                @click="downloadAsset(mode, resource)"
              />
              <label class="file-button">
                替换
                <input type="file" accept="image/*" @change="uploadAsset(mode, resource, $event)" />
              </label>
              <button class="file-button" @click="openCrop(mode, resource)">裁剪</button>
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
              <span>{{ activeView === 'me' ? '我的' : '支付宝' }}</span>
              <div v-if="activeView === 'me'" class="navi-title">个人中心</div>
              <div v-else class="navi-actions">
                <div v-for="[icon, label] in homeActions" :key="icon">
                  <img :src="imageUrl('theme', icon)" alt="" />
                  <span>{{ label }}</span>
                </div>
              </div>
            </div>

            <div class="content-blocks">
              <div></div>
              <div></div>
              <div></div>
            </div>

            <nav
              class="tabbar"
              :style="tabBarBgImage
                ? { backgroundImage: `url(${tabBarBgImage})`, backgroundSize: '100% 100%', backgroundRepeat: 'no-repeat' }
                : { backgroundColor: color('home_navi_theme_fg_color', '#ffffff') }"
            >
              <div v-for="item in tabItems" :key="item.base" :class="['tab-item', { active: item.active }]">
                <span class="tab-icon">
                  <img class="icon-normal" :src="imageUrl('theme', `${item.base}_normal`)" alt="" />
                  <img class="icon-selected" :src="imageUrl('theme', `${item.base}_selected`)" alt="" />
                </span>
                <span
                  class="tab-label"
                  :style="{
                    '--tab-color-normal': color('tab_bar_text_color_normal'),
                    '--tab-color-selected': color('tab_bar_text_color_selected'),
                  }"
                >
                  {{ item.label }}
                </span>
              </div>
            </nav>
          </template>

          <template v-else>
            <div class="payment-bg" :style="{ backgroundImage: `url(${paymentBgUrl()})` }">
              <section
                class="pay-card"
                :style="{
                  '--pay-strip-gradient': `linear-gradient(90deg, ${gradient('z02.0001', 'start', '#D9B58D')}, ${gradient('z02.0001', 'end', '#F9ECD2')})`,
                }"
              >
                <header>
                  <span class="member-label">钻石会员</span>
                  <img class="logo" :src="skinImageFor('z02.0002')" alt="" />
                  <img class="mask" :src="skinImageFor('z02.0003')" alt="" />
                </header>
                <div class="barcode"></div>
                <div class="qr"></div>
              </section>
            </div>
          </template>
        </div>
      </section>
    </section>

    <footer class="copyright">
      作者:
      <a href="https://github.com/nov30th/AlipayHighHeadsomeRichAndroid" target="_blank" rel="noopener noreferrer">Nov30th, HOHO``</a>
    </footer>

    <div v-if="cropState.open" class="crop-modal" @pointerup="finishCrop">
      <section class="crop-dialog">
        <header>
          <div>
            <h2>裁剪图片</h2>
            <p>{{ cropState.assetName }}</p>
          </div>
          <button @click="closeCrop">关闭</button>
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
          <button @click="closeCrop">取消</button>
          <button class="primary" :disabled="!canSaveCrop" @click="saveCrop">保存裁剪</button>
        </footer>
      </section>
    </div>
  </main>
</template>
