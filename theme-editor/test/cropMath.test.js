import assert from 'node:assert/strict';
import { test } from 'node:test';
import { normalizeCropSelection } from '../src/cropMath.js';

test('normalizes crop selection dragged in any direction', () => {
  const crop = normalizeCropSelection({ x: 120, y: 80 }, { x: 30, y: 20 }, { width: 200, height: 100 });

  assert.deepEqual(crop, { x: 30, y: 20, width: 90, height: 60 });
});

test('clamps crop selection to image bounds', () => {
  const crop = normalizeCropSelection({ x: -20, y: 30 }, { x: 240, y: 140 }, { width: 200, height: 100 });

  assert.deepEqual(crop, { x: 0, y: 30, width: 200, height: 70 });
});
