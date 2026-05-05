import express from 'express';
import multer from 'multer';
import { createZipSessionStore } from './zipSessionStore.js';

const app = express();
const upload = multer({ dest: 'tmp_uploads/' });
const store = createZipSessionStore();
const port = Number(process.env.PORT || 4174);

app.use(express.json({ limit: '2mb' }));

app.post('/api/sessions', upload.single('file'), async (req, res, next) => {
  try {
    if (!req.file) {
      res.status(400).json({ error: 'Missing uploaded zip file' });
      return;
    }
    res.json(await store.createFromZip(req.file.path));
  } catch (error) {
    next(error);
  }
});

app.get('/api/sessions/:id/theme', async (req, res, next) => {
  try {
    res.json(await store.load(req.params.id));
  } catch (error) {
    next(error);
  }
});

app.put('/api/sessions/:id/theme', async (req, res, next) => {
  try {
    await store.save(req.params.id, req.body);
    res.json({ ok: true });
  } catch (error) {
    next(error);
  }
});

app.get('/api/sessions/:id/assets/:area/*', (req, res, next) => {
  try {
    const stream = store.streamAsset(req.params.id, req.params.area, req.params[0]);
    stream.on('error', next);
    stream.pipe(res);
  } catch (error) {
    next(error);
  }
});

app.post('/api/sessions/:id/assets/:area', upload.single('file'), async (req, res, next) => {
  try {
    if (!req.file || !req.body.name) {
      res.status(400).json({ error: 'Missing uploaded file or asset name' });
      return;
    }
    const path = await store.saveAsset(req.params.id, req.params.area, req.body.name, req.file.path);
    res.json({ ok: true, path });
  } catch (error) {
    next(error);
  }
});

app.get('/api/sessions/:id/download', async (req, res, next) => {
  try {
    const zipPath = await store.exportZip(req.params.id);
    res.download(zipPath, 'edited-alipay-theme.zip');
  } catch (error) {
    next(error);
  }
});

app.use((error, _req, res, _next) => {
  console.error(error);
  res.status(500).json({ error: error.message || 'Unexpected server error' });
});

app.listen(port, '127.0.0.1', () => {
  console.log(`Theme editor API listening on http://127.0.0.1:${port}`);
});
