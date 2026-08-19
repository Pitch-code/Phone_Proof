import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { ALPHABET, checkCharacter, format, generate, isWellFormed, normalise } from '../src/passcode.js';

const here = dirname(fileURLToPath(import.meta.url));
const vectorFile = join(here, '..', '..', 'code-test-vectors.txt');

/**
 * The same vectors the Kotlin reader is tested against. This file existing is the only thing preventing
 * the two implementations from drifting into the worst failure this design has.
 */
const vectors = readFileSync(vectorFile, 'utf8')
  .split('\n')
  .filter((line) => line.trim() !== '' && !line.trimStart().startsWith('#'))
  .map((line) => {
    const fields = line.split('\t');
    return { input: fields[0], valid: fields[1]?.trim() === 'VALID', canonical: fields[2]?.trim() };
  })
  .filter((vector) => vector.input !== undefined && vector.canonical !== undefined);

test('the vector file is actually being read', () => {
  assert.ok(vectors.length > 0, 'no vectors parsed');
  assert.ok(vectors.filter((v) => v.valid).length >= 10);
  assert.ok(vectors.filter((v) => !v.valid).length >= 8);
});

test('every vector agrees with this implementation', () => {
  const disagreements = vectors
    .filter((v) => isWellFormed(v.input) !== v.valid)
    .map((v) => `'${v.input}' should be ${v.valid ? 'VALID' : 'INVALID'}`);
  assert.deepEqual(disagreements, []);
});

test('valid vectors normalise to the canonical body they name', () => {
  for (const vector of vectors) {
    if (!vector.valid || vector.canonical === '-') continue;
    assert.equal(normalise(vector.input), vector.canonical, `for '${vector.input}'`);
  }
});

test('changing any single character is caught', () => {
  // Guaranteed by the odd weights. If this ever fails, the weights were "tidied".
  const body = 'N6WEDKZE';
  const payload = body.slice(0, 7);
  for (let i = 0; i < payload.length; i++) {
    for (const replacement of ALPHABET) {
      if (replacement === payload[i]) continue;
      const broken = payload.slice(0, i) + replacement + payload.slice(i + 1) + body[7];
      assert.equal(isWellFormed(broken), false, `missed a change at ${i} to '${replacement}'`);
    }
  }
});

test('the documented transposition gap is exactly that, and no wider', () => {
  // '0' and 'G' are 16 apart and survive a swap. Every other adjacent pair must not.
  const payload = '0G23456';
  const code = payload + checkCharacter(payload);
  assert.equal(isWellFormed(code), true);
  assert.equal(isWellFormed('G023456' + code[7]), true);

  const body = 'N6WEDKZE';
  for (let i = 0; i < 6; i++) {
    if (body[i] === body[i + 1]) continue;
    const gap = Math.abs(ALPHABET.indexOf(body[i]) - ALPHABET.indexOf(body[i + 1]));
    if (gap === ALPHABET.length / 2) continue;
    const swapped = body.slice(0, i) + body[i + 1] + body[i] + body.slice(i + 2);
    assert.equal(isWellFormed(swapped), false, `missed a swap at ${i}`);
  }
});

test('generated codes are well formed, and never start with the prefix', () => {
  // Deterministic bytes rather than real randomness: a flaky test about a checksum is worthless.
  let seed = 1;
  const bytes = (n) => Uint8Array.from({ length: n }, () => (seed = (seed * 1103515245 + 12345) & 0x7fffffff) >>> 16);

  for (let i = 0; i < 500; i++) {
    const code = generate(bytes);
    assert.equal(code.length, 8);
    assert.equal(isWellFormed(code), true, `generated an invalid code: ${code}`);
    assert.ok(!code.startsWith('PP'), `generated an ambiguous code: ${code}`);
    // And it survives a round trip through the display format, which is how a buyer will hand it back.
    assert.equal(normalise(format(code)), code);
  }
});
