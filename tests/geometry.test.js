import test from 'node:test';
import assert from 'node:assert/strict';
import { framePoint } from '../app/src/main/assets/wheelaa/geometry.js';
const rect = {left: 10, top: 20, width: 1200, height: 800};
test('maps image center through vertical letterboxing', () => {
  assert.deepEqual(framePoint(rect, 1920, 1080, 610, 420), {x: .5, y: .5});
});
test('rejects presses in black bars', () => {
  assert.equal(framePoint(rect, 1920, 1080, 610, 21), null);
});
test('clamps a captured pointer that leaves image', () => {
  assert.deepEqual(framePoint(rect, 1920, 1080, 5000, -50, true), {x: 1, y: 0});
});
test('maps portrait image inside wide viewport', () => {
  assert.deepEqual(framePoint(rect, 800, 1200, 610, 420), {x: .5, y: .5});
  assert.equal(framePoint(rect, 800, 1200, 11, 420), null);
});
test('rejects missing dimensions and non-finite coordinates', () => {
  assert.equal(framePoint(rect, 0, 1080, 610, 420), null);
  assert.equal(framePoint(rect, 1920, 1080, NaN, 420), null);
});
