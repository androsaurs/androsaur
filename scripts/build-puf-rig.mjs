// «Пуф» — original Androsaur companion: a soft lilac creature with a fluffy head, torso, arms
// and legs, two tufts and swappable outfits.
// Same contract as the dinosaur (CONTRACT in rive/writer.mjs) plus an optional `outfit` input.
// Run: node scripts/build-puf-rig.mjs [output.riv] [--outfit=N] [--preview=output.svg]
//   outfit: 0 none, 1 scarf, 2 flower crown, 3 round glasses, 4 bow. It is also the file's default.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRig, CONTRACT, P } from './rive/writer.mjs';

export const OUTFITS = ['NONE', 'SCARF', 'FLOWER_CROWN', 'GLASSES', 'BOW'];

export function createPufRig({ outfit = 0 } = {}) {
  const rig = createRig({ artboard: 'Пуф' });
  const { node, ellipse, rect, animation, pose } = rig;

  const fur = 0xffa99bff, furArm = 0xff9a8bfa, furDark = 0xff8b7bf0, furDeep = 0xff7565d8, furLight = 0xffc4b9ff, cream = 0xfff1edff;
  const ink = 0xff2b2148, iris = 0xff5b4bd6, blushPink = 0xffff9ec4, mouthInk = 0xff3a2a5c, tongue = 0xffff7fa6;

  // Soft fuzzy outline: an ellipse ringed by overlapping puffs of the same colour.
  function fluffy(name, parent, x, y, w, h, color, { puffs = 16, size = 0.24, jitter = 0.06 } = {}) {
    const group = node(name, parent, x, y);
    const base = Math.min(w, h) * size;
    for (let k = 0; k < puffs; k++) {
      const a = (k / puffs) * Math.PI * 2;
      const d = base * (1 + ((k * 7) % 3 - 1) * jitter * 4);
      ellipse(`${name} puff ${k + 1}`, group, Math.cos(a) * (w / 2 - base * 0.35), Math.sin(a) * (h / 2 - base * 0.35), d, d, color);
    }
    ellipse(`${name} fill`, group, 0, 0, w, h, color);
    return group;
  }

  const root = node('Root', 0, 240, 236);
  ellipse('Shadow', root, 0, 198, 210, 20, 0xff17132a);

  // Legs: short and sturdy, pivot at the hip.
  function leg(side) {
    const s = side === 'Left' ? -1 : 1;
    const hip = node(`${side} leg`, root, s * 40, 134);
    rect(`${side} thigh`, hip, 0, 20, 50, 56, furDark, { radius: 22 });
    ellipse(`${side} foot`, hip, s * 6, 52, 66, 30, furDeep);
    ellipse(`${side} toe pad`, hip, s * 6, 54, 30, 11, furDark);
    return hip;
  }
  leg('Left'); leg('Right');

  const body = node('Breathing body', root);
  fluffy('Torso', body, 0, 76, 186, 168, fur, { puffs: 16, size: 0.26 });
  fluffy('Belly', body, 0, 88, 112, 104, cream, { puffs: 12, size: 0.2, jitter: 0.03 });

  // Outfit: scarf sits on the neck, over the torso and under the head.
  const scarf = node('Outfit scarf', body, 0, 0, { opacity: 0 });
  rect('Scarf tail', scarf, 44, 58, 30, 66, 0xffe8566a, { radius: 11, rotation: 0.16 });
  rect('Scarf tail stripe', scarf, 47, 74, 30, 8, 0xffffc2cb, { rotation: 0.16 });
  rect('Scarf band', scarf, 0, 22, 150, 30, 0xffff6f82, { radius: 15 });
  for (const x of [-48, -16, 16, 48]) rect(`Scarf stripe ${x}`, scarf, x, 22, 9, 30, 0xffffc2cb);

  // Arms: pivot at the shoulder, paws lighter. Rest pose angles outward a little.
  function arm(side, rest) {
    const s = side === 'Left' ? -1 : 1;
    const shoulder = node(`${side} arm`, body, s * 78, 30, { rotation: rest });
    rect(`${side} upper arm`, shoulder, 0, 38, 42, 92, furArm, { radius: 21 });
    for (let k = 0; k < 4; k++) ellipse(`${side} arm puff ${k + 1}`, shoulder, s * 17, 10 + k * 22, 18, 18, furArm);
    ellipse(`${side} paw`, shoulder, 0, 84, 46, 40, furLight);
    return shoulder;
  }
  const leftArm = arm('Left', 0.3);
  const rightArm = arm('Right', -0.3);

  // Head with an idle bob and a gaze turn. Tufts are behind it, the face on top.
  const headIdle = node('Head idle', body, 0, -64);
  const head = node('Head gaze', headIdle);

  function tuft(side) {
    const s = side === 'Left' ? -1 : 1;
    const posed = node(`${side} tuft pose`, head, s * 36, -74, { rotation: s * 0.12 });
    const sway = node(`${side} tuft sway`, posed);
    rect(`${side} tuft stem`, sway, 0, -20, 9, 42, furDark, { radius: 4.5 });
    const glow = ellipse(`${side} tuft glow`, sway, 0, -46, 50, 50, 0xffffd66b, { opacity: 0 });
    const rec = ellipse(`${side} tuft record`, sway, 0, -46, 50, 50, 0xffff5c6c, { opacity: 0 });
    ellipse(`${side} tuft ball`, sway, 0, -46, 34, 34, fur);
    ellipse(`${side} tuft shine`, sway, -6, -52, 11, 9, furLight);
    return { posed, sway, glow, rec };
  }
  const leftTuft = tuft('Left');
  const rightTuft = tuft('Right');

  fluffy('Head', head, 0, 0, 206, 176, fur, { puffs: 18, size: 0.22 });
  ellipse('Head shine', head, -50, -50, 58, 26, furLight, { rotation: -0.45, opacity: 0.75 });

  const face = node('Face', head);
  const leftBlush = ellipse('Left blush', face, -62, 20, 30, 14, blushPink, { opacity: 0.7 });
  const rightBlush = ellipse('Right blush', face, 62, 20, 30, 14, blushPink, { opacity: 0.7 });
  ellipse('Muzzle', face, 0, 30, 92, 58, cream);

  const expressionEyes = node('Expression eyes', face, 0, -10);
  const blinkEyes = node('Blink eyes', expressionEyes);
  ellipse('Left eye', blinkEyes, -38, 0, 46, 54, 0xffffffff);
  ellipse('Right eye', blinkEyes, 38, 0, 46, 54, 0xffffffff);
  const pupils = node('Gaze pupils', blinkEyes);
  for (const [side, x] of [['Left', -38], ['Right', 38]]) {
    ellipse(`${side} iris`, pupils, x, 2, 32, 38, iris);
    ellipse(`${side} pupil`, pupils, x, 3, 17, 21, ink);
    ellipse(`${side} glint`, pupils, x - 7, -8, 10, 11, 0xffffffff);
    ellipse(`${side} small glint`, pupils, x + 6, 10, 5, 5, 0xffffffff);
  }
  const leftBrow = node('Left brow', face, -38, -50);
  rect('Left brow line', leftBrow, 0, 0, 26, 7, furDeep, { radius: 3.5 });
  const rightBrow = node('Right brow', face, 38, -50);
  rect('Right brow line', rightBrow, 0, 0, 26, 7, furDeep, { radius: 3.5 });

  ellipse('Nose', face, 0, 12, 16, 11, furDeep);
  ellipse('Nose shine', face, -3, 10, 5, 3, furLight);
  // Mouth: a dark oval with a muzzle-coloured lid makes a crescent smile. Opening drops the lid
  // and shows the tongue; a negative scaleY on the whole mouth turns the smile into a frown.
  const mouth = node('Mouth', face, 0, 36);
  const mouthHole = ellipse('Mouth hole', mouth, 0, 0, 30, 16, mouthInk);
  const tongueShape = ellipse('Tongue', mouth, 0, 5, 16, 8, tongue, { opacity: 0 });
  const lid = ellipse('Mouth lid', mouth, 0, -6, 38, 15, cream);

  // Outfits on the head.
  const glasses = node('Outfit glasses', face, 0, -10, { opacity: 0 });
  ellipse('Left dark lens', glasses, -38, 0, 57, 57, 0xff11151e);
  ellipse('Right dark lens', glasses, 38, 0, 57, 57, 0xff11151e);
  ellipse('Left lens', glasses, -38, 0, 60, 60, 0xff3a2a5c, { stroke: 5 });
  ellipse('Right lens', glasses, 38, 0, 60, 60, 0xff3a2a5c, { stroke: 5 });
  rect('Bridge', glasses, 0, -4, 18, 5, 0xff3a2a5c, { radius: 2.5 });
  rect('Left lens shine', glasses, -44, -10, 23, 5, 0xffb2c8df, { radius: 2.5, rotation: -0.6, opacity: 0.25 });
  rect('Right lens shine', glasses, 32, -10, 23, 5, 0xffb2c8df, { radius: 2.5, rotation: -0.6, opacity: 0.25 });

  const crown = node('Outfit flower crown', head, 0, 0, { opacity: 0 });
  const petals = [0xffffb3d1, 0xffffffff, 0xffffe28a, 0xffffffff, 0xffffb3d1, 0xffffffff, 0xffffe28a];
  for (let k = 0; k < 7; k++) {
    const a = Math.PI * (1.2 + k * 0.1);
    if (k < 6) {
      const b = a + Math.PI * 0.05;
      ellipse(`Leaf ${k + 1}`, crown, Math.cos(b) * 92, Math.sin(b) * 80, 20, 9, 0xff5fbf7f, { rotation: b + Math.PI / 2 });
    }
    const flower = node(`Flower ${k + 1}`, crown, Math.cos(a) * 90, Math.sin(a) * 78);
    for (let p = 0; p < 5; p++) {
      const pa = (p / 5) * Math.PI * 2 + k;
      ellipse(`Petal ${k + 1}.${p + 1}`, flower, Math.cos(pa) * 7.5, Math.sin(pa) * 7.5, 12, 12, petals[k]);
    }
    ellipse(`Flower centre ${k + 1}`, flower, 0, 0, 8, 8, 0xfff4a93b);
  }

  const bow = node('Outfit bow', leftTuft.sway, 0, -46, { opacity: 0 });
  ellipse('Bow left wing', bow, -14, 2, 24, 17, 0xffff5c7a, { rotation: 0.45 });
  ellipse('Bow right wing', bow, 14, 2, 24, 17, 0xffff5c7a, { rotation: -0.45 });
  ellipse('Bow knot', bow, 0, 2, 10, 10, 0xffe03e5e);

  // --- Animations ---------------------------------------------------------------------------
  // Arms are keyed by interaction/speaking; idle sways the tufts and breathes instead.
  const idle = animation('Breathe, blink and sway', [
    [body, P.scaleY, [[0, 1], [120, 1.022], [240, 1]]],
    [headIdle, P.y, [[0, -64], [120, -69], [240, -64]]],
    [headIdle, P.rotation, [[0, 0], [90, 0.025], [200, -0.02], [240, 0]]],
    [blinkEyes, P.scaleY, [[0, 1], [150, 1], [156, 0.08], [162, 1], [240, 1]]],
    [leftTuft.sway, P.rotation, [[0, 0], [80, -0.09], [200, 0.05], [240, 0]]],
    [rightTuft.sway, P.rotation, [[0, 0], [60, 0.08], [180, -0.06], [240, 0]]],
  ], 240, true);
  const asleep = pose('Still asleep', [
    [body, P.scaleY, 1], [headIdle, P.y, -60], [headIdle, P.rotation, 0.06], [blinkEyes, P.scaleY, 1],
    [leftTuft.sway, P.rotation, 0], [rightTuft.sway, P.rotation, 0],
  ]);

  for (const name of CONTRACT.inputs) rig.input(name);
  rig.input('outfit', outfit);

  const look = (name, values) => [-1, 1].map(v => [v, pose(`${name} ${v}`, values(v))]);
  rig.blendLayer('lookX', look('lookX', v => [[face, P.x, v * 13], [head, P.rotation, v * 0.06], [pupils, P.x, v * 7]]));
  rig.blendLayer('lookY', look('lookY', v => [[face, P.y, v * 9], [pupils, P.y, v * 7]]));
  rig.blendLayer('mouthOpen', [0, 1].map(v => [v, pose(`mouthOpen ${v}`, [
    [mouthHole.path, P.height, 16 + v * 24], [mouthHole.shape, P.y, v * 6],
    [lid.shape, P.opacity, 1 - v], [tongueShape.shape, P.opacity, v], [tongueShape.shape, P.y, 5 + v * 11],
  ])]));

  // NEUTRAL, ATTENTIVE, LISTENING, SLEEPY, SAD
  const E = (...values) => code => values[code];
  const eyes = E(1, 1.08, 1.15, 0.1, 0.8);
  const browY = E(-50, -57, -59, -47, -52), browTilt = E(0, 0, -0.08, 0.06, -0.38);
  const mouthX = E(1, 1.12, 0.75, 0.6, 0.85), mouthY = E(1, 1.15, 0.8, 0.5, -0.8);
  const tuftTilt = E(0.12, 0.04, 0.02, 0.6, 0.9), tuftLift = E(1, 1.06, 1.12, 0.9, 0.92);
  const blush = E(0.7, 0.85, 0.8, 0.35, 0.3);
  rig.categoryLayer('expression', [0, 1, 2, 3, 4].map(c => pose(`expression ${CONTRACT.expression[c]}`, [
    [expressionEyes, P.scaleY, eyes(c)],
    [leftBrow, P.y, browY(c)], [rightBrow, P.y, browY(c)],
    [leftBrow, P.rotation, browTilt(c)], [rightBrow, P.rotation, -browTilt(c)],
    [mouth, P.scaleX, mouthX(c)], [mouth, P.scaleY, mouthY(c)],
    [leftTuft.posed, P.rotation, -tuftTilt(c)], [rightTuft.posed, P.rotation, tuftTilt(c)],
    [leftTuft.posed, P.scaleY, tuftLift(c)], [rightTuft.posed, P.scaleY, tuftLift(c)],
    [leftBlush.shape, P.opacity, blush(c)], [rightBlush.shape, P.opacity, blush(c)],
  ])));

  // IDLE, COMMAND, RECORDING, NOTE: tufts light up (red while recording), right arm goes up.
  rig.blendLayer('interaction', [0, 1, 2, 3].map(c => [c, pose(`interaction ${CONTRACT.interaction[c]}`, [
    [leftTuft.glow.shape, P.opacity, [0, 0.9, 0, 0.5][c]], [rightTuft.glow.shape, P.opacity, [0, 0.9, 0, 0.5][c]],
    [leftTuft.rec.shape, P.opacity, [0, 0, 0.9, 0][c]], [rightTuft.rec.shape, P.opacity, [0, 0, 0.9, 0][c]],
    [rightArm, P.rotation, [-0.3, -2.5, -2.2, -1.1][c]],
  ])]));
  // Speaking: the left arm gestures.
  rig.blendLayer('speaking', [0, 1].map(v => [v, pose(`speaking ${v}`, [[leftArm, P.rotation, 0.3 + v * 0.7]])]));
  rig.blendLayer('sleeping', [[0, idle], [1, asleep]]);

  const groups = [null, scarf, crown, glasses, bow];
  rig.categoryLayer('outfit', OUTFITS.map((name, code) => pose(`outfit ${name}`,
    groups.filter(Boolean).map(g => [g, P.opacity, g === groups[code] ? 1 : 0]))), 150);

  return rig;
}

export function buildPuf(options = {}) { return createPufRig(options).build(CONTRACT.machine); }

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const outfitArg = args.find(a => a.startsWith('--outfit='));
  const outfit = outfitArg ? Number(outfitArg.split('=')[1]) : 0;
  if (!(Number.isInteger(outfit) && outfit >= 0 && outfit < OUTFITS.length)) throw new Error(`--outfit must be 0..${OUTFITS.length - 1}`);
  const output = args.find(a => !a.startsWith('--')) || fileURLToPath(new URL('../androidApp/src/main/res/raw/puf_character.riv', import.meta.url));
  const rig = createPufRig({ outfit });
  const bytes = rig.build(CONTRACT.machine);
  mkdirSync(dirname(resolve(output)), { recursive: true });
  writeFileSync(output, bytes);
  const preview = args.find(a => a.startsWith('--preview='))?.slice('--preview='.length);
  if (preview) {
    mkdirSync(dirname(resolve(preview)), { recursive: true });
    writeFileSync(preview, rig.svg());
  }
  console.log(`Wrote ${output}: ${bytes.length} bytes, outfit ${OUTFITS[outfit]}`);
}
