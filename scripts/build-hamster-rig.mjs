// «Хомяк» — Androsaur companion: a chubby beige Syrian hamster with cheek pouches, round ears,
// whiskers and little paws held at the chest.
// Same contract as the other characters (CONTRACT in rive/writer.mjs) plus an optional `outfit` input.
// Run: node scripts/build-hamster-rig.mjs [output.riv] [--outfit=N] [--preview=output.svg]
//   outfit: 0 none, 1 sunflower seed in the paws, 2 bow on the ear, 3 round glasses.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRig, CONTRACT, P } from './rive/writer.mjs';

export const OUTFITS = ['NONE', 'SEED', 'BOW', 'GLASSES'];

export function createHamsterRig({ outfit = 0 } = {}) {
  const rig = createRig({ artboard: 'Хомяк' });
  const { node, ellipse, rect, animation, pose } = rig;

  const fur = 0xffe9c89b, furGold = 0xffdcaa6c, furShade = 0xffd2a066, cream = 0xfffcf1de;
  const pink = 0xfff6b1a7, pinkDeep = 0xffff8f9a, earInner = 0xffffbcb6, ink = 0xff2a1c14;
  const mouthInk = 0xff5a2f2a, tongue = 0xffff8fa0, whisker = 0xffb48b5e;

  // Soft fuzzy outline: an ellipse ringed by overlapping puffs of the same colour.
  function fluffy(name, parent, x, y, w, h, color, { puffs = 16, size = 0.22 } = {}) {
    const group = node(name, parent, x, y);
    const base = Math.min(w, h) * size;
    for (let k = 0; k < puffs; k++) {
      const a = (k / puffs) * Math.PI * 2;
      const d = base * (1 + ((k * 7) % 3 - 1) * 0.2);
      ellipse(`${name} puff ${k + 1}`, group, Math.cos(a) * (w / 2 - base * 0.35), Math.sin(a) * (h / 2 - base * 0.35), d, d, color);
    }
    ellipse(`${name} fill`, group, 0, 0, w, h, color);
    return group;
  }

  const root = node('Root', 0, 240, 240);
  ellipse('Shadow', root, 0, 196, 240, 22, 0xff1c140e);

  const body = node('Breathing body', root);
  fluffy('Body', body, 0, 78, 262, 218, fur, { puffs: 18, size: 0.2 });
  fluffy('Belly', body, 0, 100, 168, 150, cream, { puffs: 14, size: 0.17 });
  // Feet peek out in front of the round body.
  for (const s of [-1, 1]) {
    const side = s < 0 ? 'Left' : 'Right';
    ellipse(`${side} foot`, root, s * 52, 184, 50, 24, pink);
    for (const t of [-1, 0, 1]) ellipse(`${side} toe ${t + 2}`, root, s * 52 + t * 13, 192, 11, 10, 0xffeea095);
  }

  // Head: idle bob, then gaze turn. Ears behind the head, face on top.
  const headIdle = node('Head idle', body, 0, -62);
  const head = node('Head gaze', headIdle);

  function ear(side) {
    const s = side === 'Left' ? -1 : 1;
    const posed = node(`${side} ear pose`, head, s * 76, -72, { rotation: s * 0.15 });
    const twitch = node(`${side} ear twitch`, posed);
    ellipse(`${side} ear`, twitch, 0, -10, 54, 56, furGold);
    ellipse(`${side} ear inner`, twitch, 0, -8, 32, 36, earInner);
    return { posed, twitch };
  }
  const leftEar = ear('Left');
  const rightEar = ear('Right');

  fluffy('Head', head, 0, 0, 236, 190, fur, { puffs: 18, size: 0.19 });
  fluffy('Golden crown', head, 0, -58, 128, 50, furGold, { puffs: 12, size: 0.3 });
  ellipse('Golden stripe', head, 0, -38, 26, 44, furGold);

  const face = node('Face', head);
  // Cheek pouches: the hamster's signature, they puff up when attentive.
  const cheeks = node('Cheeks', face, 0, 26);
  fluffy('Left cheek', cheeks, -66, 0, 104, 86, cream, { puffs: 10, size: 0.2 });
  fluffy('Right cheek', cheeks, 66, 0, 104, 86, cream, { puffs: 10, size: 0.2 });
  const leftBlush = ellipse('Left blush', face, -76, 22, 30, 14, 0xffffa3a8, { opacity: 0.6 });
  const rightBlush = ellipse('Right blush', face, 76, 22, 30, 14, 0xffffa3a8, { opacity: 0.6 });
  ellipse('Muzzle', face, 0, 30, 86, 60, cream);

  const expressionEyes = node('Expression eyes', face, 0, -14);
  const blinkEyes = node('Blink eyes', expressionEyes);
  const pupils = node('Gaze eyes', blinkEyes);
  for (const [side, x] of [['Left', -48], ['Right', 48]]) {
    ellipse(`${side} eye`, pupils, x, 0, 34, 38, ink);
    ellipse(`${side} glint`, pupils, x - 6, -8, 11, 12, 0xffffffff);
    ellipse(`${side} small glint`, pupils, x + 6, 8, 5, 5, 0xffffffff);
  }
  const leftBrow = node('Left brow', face, -48, -46);
  rect('Left brow line', leftBrow, 0, 0, 24, 6, furShade, { radius: 3 });
  const rightBrow = node('Right brow', face, 48, -46);
  rect('Right brow line', rightBrow, 0, 0, 24, 6, furShade, { radius: 3 });

  // Whiskers twitch with the nose.
  const whiskers = [];
  for (const s of [-1, 1]) {
    const side = s < 0 ? 'Left' : 'Right';
    const group = node(`${side} whiskers`, face, s * 34, 22);
    for (const [k, angle] of [[0, -0.22], [1, 0], [2, 0.22]]) {
      rect(`${side} whisker ${k + 1}`, group, s * 30, (k - 1) * 9, 54, 2.5, whisker, { radius: 1.2, rotation: s * angle });
    }
    whiskers.push(group);
  }
  const nose = node('Nose', face, 0, 12);
  ellipse('Nose tip', nose, 0, 0, 20, 13, pinkDeep);
  ellipse('Nose shine', nose, -4, -3, 6, 3, 0xffffd2d6);

  // Mouth: crescent smile from a dark oval and a muzzle-coloured lid. Opening shows the
  // tongue and two little front teeth; a negative scaleY turns the smile into a frown.
  const mouth = node('Mouth', face, 0, 32);
  const mouthHole = ellipse('Mouth hole', mouth, 0, 0, 28, 15, mouthInk);
  const tongueShape = ellipse('Tongue', mouth, 0, 5, 15, 8, tongue, { opacity: 0 });
  const teeth = node('Teeth', mouth, 0, -4, { opacity: 0 });
  rect('Left tooth', teeth, -3.5, 0, 6, 8, 0xffffffff, { radius: 1.5 });
  rect('Right tooth', teeth, 3.5, 0, 6, 8, 0xffffffff, { radius: 1.5 });
  const lid = ellipse('Mouth lid', mouth, 0, -6, 36, 14, cream);

  // Paws held at the chest, pivot at the shoulder. The seed outfit sits between them.
  function arm(side, rest) {
    const s = side === 'Left' ? -1 : 1;
    const shoulder = node(`${side} arm`, body, s * 62, 26, { rotation: rest });
    rect(`${side} arm fur`, shoulder, 0, 26, 36, 62, furShade, { radius: 18 });
    ellipse(`${side} paw`, shoulder, 0, 56, 30, 24, pink);
    for (const t of [-1, 0, 1]) ellipse(`${side} finger ${t + 2}`, shoulder, t * 8, 66, 8, 8, pink);
    return shoulder;
  }
  const seed = node('Outfit seed', body, 0, 52, { opacity: 0, rotation: 0.35 });
  ellipse('Seed shell', seed, 0, 0, 30, 52, 0xff3a3330);
  for (const x of [-7, 0, 7]) rect(`Seed stripe ${x}`, seed, x, 0, 3, 40, 0xffe9e2d6, { radius: 1.5 });
  const leftArm = arm('Left', -0.75);
  const rightArm = arm('Right', 0.75);

  const glasses = node('Outfit glasses', face, 0, -14, { opacity: 0 });
  ellipse('Left dark lens', glasses, -48, 0, 53, 53, 0xff11151e);
  ellipse('Right dark lens', glasses, 48, 0, 53, 53, 0xff11151e);
  ellipse('Left lens', glasses, -48, 0, 56, 56, 0xff4a2e1e, { stroke: 5 });
  ellipse('Right lens', glasses, 48, 0, 56, 56, 0xff4a2e1e, { stroke: 5 });
  rect('Bridge', glasses, 0, -2, 42, 5, 0xff4a2e1e, { radius: 2.5 });
  rect('Left lens shine', glasses, -54, -10, 22, 5, 0xffb2c8df, { radius: 2.5, rotation: -0.6, opacity: 0.25 });
  rect('Right lens shine', glasses, 42, -10, 22, 5, 0xffb2c8df, { radius: 2.5, rotation: -0.6, opacity: 0.25 });

  const bow = node('Outfit bow', rightEar.twitch, -4, -34, { opacity: 0 });
  ellipse('Bow left wing', bow, -14, 0, 24, 17, 0xffff5c7a, { rotation: 0.45 });
  ellipse('Bow right wing', bow, 14, 0, 24, 17, 0xffff5c7a, { rotation: -0.45 });
  ellipse('Bow knot', bow, 0, 0, 10, 10, 0xffe03e5e);

  // --- Animations ---------------------------------------------------------------------------
  const idle = animation('Breathe, blink and sniff', [
    [body, P.scaleY, [[0, 1], [120, 1.025], [240, 1]]],
    [body, P.scaleX, [[0, 1], [120, 1.01], [240, 1]]],
    [headIdle, P.y, [[0, -62], [120, -66], [240, -62]]],
    [blinkEyes, P.scaleY, [[0, 1], [170, 1], [176, 0.08], [182, 1], [240, 1]]],
    // Sniffing: quick nose wiggle and whisker twitch twice per loop.
    [nose, P.scaleX, [[0, 1], [40, 1], [46, 1.18], [52, 0.94], [58, 1.15], [64, 1], [200, 1], [206, 1.16], [212, 1], [240, 1]]],
    [whiskers[0], P.rotation, [[0, 0], [40, 0], [46, -0.07], [52, 0.03], [58, -0.06], [64, 0], [200, 0], [206, -0.06], [212, 0], [240, 0]]],
    [whiskers[1], P.rotation, [[0, 0], [40, 0], [46, 0.07], [52, -0.03], [58, 0.06], [64, 0], [200, 0], [206, 0.06], [212, 0], [240, 0]]],
    [leftEar.twitch, P.rotation, [[0, 0], [100, 0], [106, -0.12], [114, 0], [240, 0]]],
    [rightEar.twitch, P.rotation, [[0, 0], [150, 0], [156, 0.12], [164, 0], [240, 0]]],
  ], 240, true);
  const asleep = pose('Still asleep', [
    [body, P.scaleY, 1], [body, P.scaleX, 1], [headIdle, P.y, -58], [blinkEyes, P.scaleY, 1], [nose, P.scaleX, 1],
    [whiskers[0], P.rotation, 0], [whiskers[1], P.rotation, 0], [leftEar.twitch, P.rotation, 0], [rightEar.twitch, P.rotation, 0],
  ]);

  for (const name of CONTRACT.inputs) rig.input(name);
  rig.input('outfit', outfit);

  const look = (name, values) => [-1, 1].map(v => [v, pose(`${name} ${v}`, values(v))]);
  rig.blendLayer('lookX', look('lookX', v => [[face, P.x, v * 12], [head, P.rotation, v * 0.06], [pupils, P.x, v * 4]]));
  rig.blendLayer('lookY', look('lookY', v => [[face, P.y, v * 8], [pupils, P.y, v * 4]]));
  rig.blendLayer('mouthOpen', [0, 1].map(v => [v, pose(`mouthOpen ${v}`, [
    [mouthHole.path, P.height, 15 + v * 22], [mouthHole.shape, P.y, v * 6],
    [lid.shape, P.opacity, 1 - v], [tongueShape.shape, P.opacity, v], [tongueShape.shape, P.y, 5 + v * 10],
    [teeth, P.opacity, v], [teeth, P.y, -4 - v * 4],
  ])]));

  // NEUTRAL, ATTENTIVE, LISTENING, SLEEPY, SAD
  const E = (...values) => code => values[code];
  const eyes = E(1, 1.1, 1.15, 0.1, 0.85);
  const browY = E(-46, -52, -54, -43, -48), browTilt = E(0, 0, -0.08, 0.06, -0.38);
  const mouthX = E(1, 1.12, 0.75, 0.6, 0.85), mouthY = E(1, 1.15, 0.8, 0.5, -0.8);
  const earTilt = E(0.15, 0.04, -0.02, 0.45, 0.6), earLift = E(1, 1.08, 1.14, 0.88, 0.85);
  const cheekPuff = E(1, 1.06, 1.03, 0.97, 0.95);
  const blush = E(0.6, 0.8, 0.75, 0.35, 0.25);
  rig.categoryLayer('expression', [0, 1, 2, 3, 4].map(c => pose(`expression ${CONTRACT.expression[c]}`, [
    [expressionEyes, P.scaleY, eyes(c)],
    [leftBrow, P.y, browY(c)], [rightBrow, P.y, browY(c)],
    [leftBrow, P.rotation, browTilt(c)], [rightBrow, P.rotation, -browTilt(c)],
    [mouth, P.scaleX, mouthX(c)], [mouth, P.scaleY, mouthY(c)],
    [leftEar.posed, P.rotation, -earTilt(c)], [rightEar.posed, P.rotation, earTilt(c)],
    [leftEar.posed, P.scaleY, earLift(c)], [rightEar.posed, P.scaleY, earLift(c)],
    [cheeks, P.scaleX, cheekPuff(c)], [cheeks, P.scaleY, cheekPuff(c)],
    [leftBlush.shape, P.opacity, blush(c)], [rightBlush.shape, P.opacity, blush(c)],
  ])));

  // IDLE, COMMAND, RECORDING, NOTE: the right paw goes up; ears perk while listening.
  rig.blendLayer('interaction', [0, 1, 2, 3].map(c => [c, pose(`interaction ${CONTRACT.interaction[c]}`, [
    [rightArm, P.rotation, [0.75, -2.0, -1.7, -0.9][c]],
  ])]));
  // Speaking: the left paw gestures.
  rig.blendLayer('speaking', [0, 1].map(v => [v, pose(`speaking ${v}`, [[leftArm, P.rotation, -0.75 + v * 0.9]])]));
  rig.blendLayer('sleeping', [[0, idle], [1, asleep]]);

  const groups = [null, seed, bow, glasses];
  rig.categoryLayer('outfit', OUTFITS.map((name, code) => pose(`outfit ${name}`,
    groups.filter(Boolean).map(g => [g, P.opacity, g === groups[code] ? 1 : 0]))), 150);

  return rig;
}

export function buildHamster(options = {}) { return createHamsterRig(options).build(CONTRACT.machine); }

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const outfitArg = args.find(a => a.startsWith('--outfit='));
  const outfit = outfitArg ? Number(outfitArg.split('=')[1]) : 0;
  if (!(Number.isInteger(outfit) && outfit >= 0 && outfit < OUTFITS.length)) throw new Error(`--outfit must be 0..${OUTFITS.length - 1}`);
  const output = args.find(a => !a.startsWith('--')) || fileURLToPath(new URL('../androidApp/src/main/res/raw/hamster_character.riv', import.meta.url));
  const rig = createHamsterRig({ outfit });
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
