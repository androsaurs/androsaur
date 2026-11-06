// «Пингвин 2» (ID `penguin2`, artboard «Пингвин») — Androsaur companion for a child's phone on a black screen. Real penguin anatomy:
// one pear-shaped body (head and torso are a single shape, no neck), a tall egg-shaped white
// belly, triangular flippers at the sides, a wedge beak and small webbed feet. Bright blue with a
// light outline and a soft glow behind, so it reads on black. Clothes are cut from the body's own
// outline, so they follow its contour.
// Same contract as the other characters (CONTRACT in rive/writer.mjs) plus an optional `outfit` input.
// Run: node scripts/build-penguin2-rig.mjs [output.riv] [--outfit=N] [--preview=output.svg]
//   outfit: 0 none, 1 bobble hat, 2 headphones, 3 scarf, 4 cap, 5 hoodie.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRig, CONTRACT, P } from './rive/writer.mjs';

export const OUTFITS = ['NONE', 'BOBBLE_HAT', 'HEADPHONES', 'SCARF', 'CAP', 'HOODIE'];

const R = Math.PI / 2;
// The pear, clockwise from the top of the head: narrow head, slight waist, wide round bottom.
const BODY = [
  { x: 0, y: -192, angle: 0, dist: 52 },
  { x: 80, y: -128, angle: R, dist: 40 },
  { x: 90, y: -40, angle: R + 0.16, dist: 46 },
  { x: 124, y: 92, angle: R, dist: 54 },
  { x: 0, y: 186, angle: 2 * R, dist: 82 },
  { x: -124, y: 92, angle: -R, dist: 54 },
  { x: -90, y: -40, angle: -R + 0.16, dist: 46 },
  { x: -80, y: -128, angle: -R, dist: 40 },
];
// Tall egg, narrow at the top under the face, wide at the bottom.
const BELLY = [
  { x: 0, y: -74, angle: 0, dist: 34 },
  { x: 72, y: 64, angle: R, dist: 64 },
  { x: 0, y: 172, angle: 2 * R, dist: 56 },
  { x: -72, y: 64, angle: -R, dist: 64 },
];
// Flipper hanging from its shoulder pivot: wide at the base, tapering to a rounded tip.
const FLIPPER = [[-21, -6, 18], [23, -4, 18], [10, 126, 13]];

export function createPenguin2Rig({ outfit = 0 } = {}) {
  const rig = createRig({ artboard: 'Пингвин', reversePoints: true });
  const { node, ellipse, rect, outline, clip, animation, pose } = rig;

  const blue = 0xff4c7cf3, blueLight = 0xff9dbcff, rim = { color: 0xffb9d0ff, width: 3.5 };
  const white = 0xfffdfdff, belly = 0xfff3f6ff, orange = 0xffffa63d, orangeDeep = 0xffe9861f;
  const ink = 0xff141a2e, brow = 0xff1c2a66, mouthInk = 0xff7a2e2a, blushPink = 0xffff9fb5;
  const coral = 0xffff6f61, coralDeep = 0xffe2544a, coralLight = 0xffffa79e, coralRim = { color: 0xffffc2bb, width: 3 };

  const root = node('Root', 0, 240, 262);
  ellipse('Glow', root, 0, -10, 460, 460, 0, { glow: [[0, 0x402f5bd6], [0.55, 0x182f5bd6], [1, 0x002f5bd6]] });
  ellipse('Shadow', root, 0, 196, 210, 20, 0xff0b1020, { opacity: 0.9 });

  // The figure sways around its feet; everything except the feet is inside.
  const sway = node('Sway', root, 0, 186);
  const figure = node('Figure', sway, 0, -186);

  const body = node('Breathing body', figure);
  const bodyShape = outline('Body', body, 0, 0, BODY, blue, { outline: rim });
  outline('Belly', body, 0, 0, BELLY, belly);
  ellipse('Body shine', body, -46, -142, 30, 50, blueLight, { rotation: 0.45, opacity: 0.7 });
  ellipse('Belly shine', body, -34, 40, 22, 60, white, { rotation: 0.2, opacity: 0.9 });

  // Hoodie: the body outline grown by 5 %, hip length (cut at the hem), details cut to it.
  const hoodie = node('Outfit hoodie', body, 0, 0, { opacity: 0 });
  const hoodieLength = rect('Hoodie length', hoodie, 0, -30, 400, 352, 0, { opacity: 0 });
  clip(hoodie, hoodieLength.shape);
  const hoodieShape = outline('Hoodie body', hoodie, 0, 0, BODY, coral, { outline: coralRim, scaleX: 1.06, scaleY: 1.04 });
  const hoodieDetails = node('Hoodie details', hoodie);
  clip(hoodieDetails, hoodieShape.shape);
  ellipse('Hoodie shade', hoodieDetails, 70, 60, 150, 260, coralDeep, { opacity: 0.3 });
  ellipse('Hoodie shine', hoodieDetails, -52, 0, 26, 70, coralLight, { rotation: 0.2, opacity: 0.6 });
  rect('Hoodie hem', hoodieDetails, 0, 132, 300, 28, coralDeep);
  for (let x = -126; x <= 126; x += 14) rect(`Hem rib ${x}`, hoodieDetails, x, 132, 2.5, 28, coral);
  rect('Hoodie pocket', hoodieDetails, 0, 72, 120, 48, coralDeep, { radius: 18 });
  rect('Hoodie pocket opening', hoodieDetails, 0, 52, 100, 6, coral, { radius: 3 });

  // Scarf where the head meets the body, cut to the body outline so its ends follow the sides.
  const scarf = node('Outfit scarf', body, 0, 0, { opacity: 0 });
  const scarfFit = outline('Scarf fit', scarf, 0, 0, BODY, 0, { opacity: 0, scaleX: 1.08, scaleY: 1.03 });
  const scarfBand = node('Scarf band', scarf);
  clip(scarfBand, scarfFit.shape);
  rect('Scarf wrap', scarfBand, 0, -62, 260, 30, 0xff3cc97a);
  for (let x = -100; x <= 100; x += 30) rect(`Scarf stripe ${x}`, scarfBand, x, -62, 10, 30, 0xffd8f5e4);
  rect('Scarf tail', scarf, -42, -22, 28, 70, 0xff2fb36b, { radius: 10, rotation: -0.12, outline: { color: 0xffb8f0cf, width: 2 } });
  for (const y of [-34, -10]) rect(`Scarf tail stripe ${y}`, scarf, -41 - (y + 22) * 0.12, y, 28, 7, 0xffd8f5e4, { rotation: -0.12 });

  // Flippers in front of the body, hanging from its sides. Shoulder (interaction or speaking)
  // -> swing (idle).
  function flipper(side, rest) {
    const s = side === 'Left' ? -1 : 1;
    const shoulder = node(`${side} flipper`, figure, s * 84, -50, { rotation: rest });
    const swing = node(`${side} flipper swing`, shoulder);
    outline(`${side} flipper shape`, swing, 0, 0, FLIPPER, blue, { outline: rim, scaleX: s });
    outline(`${side} flipper shine`, swing, s * 2, 18, [[-4, 0, 4], [6, 0, 4], [3, 70, 4]], blueLight, { opacity: 0.55 });
    // Hoodie sleeve: the flipper outline grown, cut off before the tip.
    const sleeve = node(`Outfit hoodie ${side.toLowerCase()} sleeve`, swing, 0, 0, { opacity: 0 });
    const length = rect(`${side} sleeve length`, sleeve, 0, 36, 140, 96, 0, { opacity: 0 });
    clip(sleeve, length.shape);
    const sleeveShape = outline(`${side} sleeve`, sleeve, 0, 0, FLIPPER, coral, { outline: coralRim, scaleX: s * 1.18, scaleY: 1.05 });
    const cuff = node(`${side} cuff clip`, sleeve);
    clip(cuff, sleeveShape.shape);
    rect(`${side} cuff`, cuff, 0, 78, 140, 12, coralDeep);
    return { shoulder, swing, sleeve };
  }
  const left = flipper('Left', 0.36);
  const right = flipper('Right', -0.36);

  // Face on the head part of the pear: posture (expression) -> idle bob -> gaze.
  const facePose = node('Face pose', figure, 0, -118);
  const faceIdle = node('Face idle', facePose);
  const face = node('Face', faceIdle);

  // Hood, worn up: the head part of the pear in coral, with an opening for the face.
  const hood = node('Outfit hoodie hood', figure, 0, 0, { opacity: 0 });
  const hoodTop = node('Hood top', hood);
  clip(hoodTop, bodyShape.shape);
  ellipse('Hood', hoodTop, 0, -122, 230, 196, coral);
  ellipse('Hood shine', hoodTop, -48, -160, 46, 22, coralLight, { rotation: -0.5, opacity: 0.7 });
  ellipse('Hood opening', hood, 0, -110, 140, 118, blue);
  ellipse('Hood rim', hood, 0, -110, 140, 118, coralDeep, { stroke: 10 });

  const leftBlush = ellipse('Left blush', face, -54, 20, 24, 11, blushPink, { opacity: 0.8 });
  const rightBlush = ellipse('Right blush', face, 54, 20, 24, 11, blushPink, { opacity: 0.8 });

  const expressionEyes = node('Expression eyes', face, 0, -8);
  const blinkEyes = node('Blink eyes', expressionEyes);
  ellipse('Left eye white', blinkEyes, -36, 0, 44, 48, white);
  ellipse('Right eye white', blinkEyes, 36, 0, 44, 48, white);
  const pupils = node('Gaze pupils', blinkEyes);
  for (const [side, x] of [['Left', -36], ['Right', 36]]) {
    ellipse(`${side} pupil`, pupils, x, 2, 26, 30, ink);
    ellipse(`${side} glint`, pupils, x - 6, -6, 10, 11, 0xffffffff);
    ellipse(`${side} small glint`, pupils, x + 6, 9, 5, 5, 0xffffffff);
  }
  const leftBrow = node('Left brow', face, -36, -46);
  rect('Left brow line', leftBrow, 0, 0, 26, 7, brow, { radius: 3.5 });
  const rightBrow = node('Right brow', face, 36, -46);
  rect('Right brow line', rightBrow, 0, 0, 26, 7, brow, { radius: 3.5 });

  // Beak: a wedge pointing down; opening drops the lower half and shows the mouth.
  const beak = node('Beak', face, 0, 26);
  const mouthInside = ellipse('Mouth inside', beak, 0, 8, 26, 14, mouthInk, { opacity: 0 });
  const lowerBeak = node('Lower beak', beak, 0, 4);
  outline('Lower beak shape', lowerBeak, 0, 0, [[-17, 0, 4], [17, 0, 4], [0, 15, 4]], orangeDeep);
  outline('Upper beak', beak, 0, 0, [[-30, -10, 9], [30, -10, 9], [0, 20, 5]], orange,
    { outline: { color: 0xffffd29a, width: 2 } });
  outline('Beak shine', beak, 0, 0, [[-19, -7, 3], [-6, -7, 3], [-11, 1, 3]], 0xffffd8a8);

  // Outfits worn on the head.
  const phones = node('Outfit headphones', figure, 0, -122, { opacity: 0 });
  for (let k = 0; k <= 72; k++) {
    const a = Math.PI * (1 + k / 72);
    ellipse(`Headphones band ${k + 1}`, phones, Math.cos(a) * 88, Math.sin(a) * 84 + 6, 13, 13, 0xffff5470);
  }
  ellipse('Left ear cup', phones, -86, 14, 30, 56, 0xffff5470, { outline: { color: 0xffffc0cb, width: 2.5 } });
  ellipse('Right ear cup', phones, 86, 14, 30, 56, 0xffff5470, { outline: { color: 0xffffc0cb, width: 2.5 } });

  const hat = node('Outfit bobble hat', figure, 0, -176, { opacity: 0 });
  ellipse('Hat dome', hat, 0, -12, 150, 86, 0xffffc93c, { outline: { color: 0xfffff0b8, width: 3 } });
  rect('Hat stripe', hat, 0, -20, 134, 10, 0xffff6f61, { radius: 5 });
  rect('Hat band', hat, 0, 22, 168, 30, 0xffff6f61, { radius: 15, outline: { color: 0xffffc2bb, width: 2.5 } });
  ellipse('Pompom', hat, 0, -60, 40, 40, 0xffffffff);

  const cap = node('Outfit cap', figure, 0, -176, { opacity: 0 });
  rect('Cap visor', cap, 50, 20, 96, 20, 0xff22b07d, { radius: 10, rotation: 0.08 });
  ellipse('Cap crown', cap, 0, 0, 160, 84, 0xff2fd396, { outline: { color: 0xffb6f5dc, width: 3 } });
  rect('Cap seam', cap, 0, 0, 5, 72, 0xff22b07d, { radius: 2.5 });
  ellipse('Cap badge', cap, -32, 6, 24, 24, 0xffffffff);

  // Feet in front, not swaying.
  for (const s of [-1, 1]) {
    const side = s < 0 ? 'Left' : 'Right';
    const foot = node(`${side} foot`, root, s * 46, 184, { rotation: s * 0.2 });
    outline(`${side} webbed foot`, foot, 0, 0, [
      [-12, -12, 8], [12, -12, 8], [40, 10, 9], [27, 20, 2], [14, 15, 2], [0, 24, 9],
      [-14, 15, 2], [-27, 20, 2], [-40, 10, 9],
    ], orange, { outline: { color: 0xffffd29a, width: 2.5 } });
    for (const t of [-1, 1]) rect(`${side} web line ${t}`, foot, t * 11, 6, 3, 22, orangeDeep, { radius: 1.5, rotation: -t * 0.75, opacity: 0.8 });
  }

  // --- Animations ---------------------------------------------------------------------------
  const idle = animation('Breathe, blink and sway', [
    [body, P.scaleY, [[0, 1], [120, 1.015], [240, 1]]],
    [sway, P.rotation, [[0, 0], [60, 0.022], [180, -0.022], [240, 0]]],
    [faceIdle, P.y, [[0, 0], [120, -3], [240, 0]]],
    [left.swing, P.rotation, [[0, 0], [60, -0.06], [180, 0.06], [240, 0]]],
    [right.swing, P.rotation, [[0, 0], [60, -0.06], [180, 0.06], [240, 0]]],
    [blinkEyes, P.scaleY, [[0, 1], [140, 1], [146, 0.08], [152, 1], [240, 1]]],
  ], 240, true);
  const asleep = pose('Still asleep', [
    [body, P.scaleY, 1], [sway, P.rotation, 0], [faceIdle, P.y, 4],
    [left.swing, P.rotation, 0], [right.swing, P.rotation, 0], [blinkEyes, P.scaleY, 1],
  ]);

  for (const name of CONTRACT.inputs) rig.input(name);
  rig.input('outfit', outfit);

  const look = (name, values) => [-1, 1].map(v => [v, pose(`${name} ${v}`, values(v))]);
  rig.blendLayer('lookX', look('lookX', v => [[face, P.x, v * 14], [face, P.rotation, v * 0.05], [pupils, P.x, v * 6]]));
  rig.blendLayer('lookY', look('lookY', v => [[face, P.y, v * 8], [pupils, P.y, v * 6]]));
  rig.blendLayer('mouthOpen', [0, 1].map(v => [v, pose(`mouthOpen ${v}`, [
    [lowerBeak, P.y, 4 + v * 14],
    [mouthInside.shape, P.opacity, v], [mouthInside.path, P.height, 14 + v * 16], [mouthInside.shape, P.y, 8 + v * 6],
  ])]));

  // NEUTRAL, ATTENTIVE, LISTENING, SLEEPY, SAD: eyes, brows, beak, blush and posture.
  const E = (...values) => code => values[code];
  const eyes = E(1, 1.08, 1.14, 0.1, 0.82);
  const browY = E(-46, -53, -55, -43, -48), browTilt = E(0, 0, -0.08, 0.06, -0.4);
  const beakDrop = E(26, 25, 25, 28, 29);
  const blush = E(0.8, 0.95, 0.9, 0.4, 0.3);
  const faceY = E(-118, -122, -121, -112, -110), faceTilt = E(0, 0, 0.06, -0.04, 0.05);
  rig.categoryLayer('expression', [0, 1, 2, 3, 4].map(c => pose(`expression ${CONTRACT.expression[c]}`, [
    [expressionEyes, P.scaleY, eyes(c)],
    [leftBrow, P.y, browY(c)], [rightBrow, P.y, browY(c)],
    [leftBrow, P.rotation, browTilt(c)], [rightBrow, P.rotation, -browTilt(c)],
    [beak, P.y, beakDrop(c)],
    [leftBlush.shape, P.opacity, blush(c)], [rightBlush.shape, P.opacity, blush(c)],
    [facePose, P.y, faceY(c)], [facePose, P.rotation, faceTilt(c)],
  ])));

  // IDLE, COMMAND, RECORDING, NOTE: the right flipper goes up and waves.
  rig.blendLayer('interaction', [0, 1, 2, 3].map(c => [c, pose(`interaction ${CONTRACT.interaction[c]}`, [
    [right.shoulder, P.rotation, [-0.36, -2.3, -1.9, -1.1][c]],
  ])]));
  rig.blendLayer('speaking', [0, 1].map(v => [v, pose(`speaking ${v}`, [[left.shoulder, P.rotation, 0.36 + v * 0.75]])]));
  rig.blendLayer('sleeping', [[0, idle], [1, asleep]]);

  const hoodieParts = [hoodie, left.sleeve, right.sleeve, hood];
  const groups = [[], [hat], [phones], [scarf], [cap], hoodieParts];
  rig.categoryLayer('outfit', OUTFITS.map((name, code) => pose(`outfit ${name}`,
    groups.flat().map(g => [g, P.opacity, groups[code].includes(g) ? 1 : 0]))), 150);

  return rig;
}

export function buildPenguin2(options = {}) { return createPenguin2Rig(options).build(CONTRACT.machine); }

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const outfitArg = args.find(a => a.startsWith('--outfit='));
  const outfit = outfitArg ? Number(outfitArg.split('=')[1]) : 0;
  if (!(Number.isInteger(outfit) && outfit >= 0 && outfit < OUTFITS.length)) throw new Error(`--outfit must be 0..${OUTFITS.length - 1}`);
  const output = args.find(a => !a.startsWith('--')) || fileURLToPath(new URL('../androidApp/src/main/res/raw/penguin2_character.riv', import.meta.url));
  const rig = createPenguin2Rig({ outfit });
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
