// «Пингвин» — Androsaur companion for a child's phone on a black screen: a chibi penguin with a
// big head, a round torso, short thick arms and legs, in bright colours with a light outline and
// a soft glow behind, so it reads on black. Swappable outfits are built from the body's own
// shapes, so clothes follow its contours.
// Same contract as the other characters (CONTRACT in rive/writer.mjs) plus an optional `outfit` input.
// Run: node scripts/build-penguin-rig.mjs [output.riv] [--outfit=N] [--preview=output.svg]
//   outfit: 0 none, 1 bobble hat, 2 headphones, 3 scarf, 4 cap, 5 hoodie.
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRig, CONTRACT, P } from './rive/writer.mjs';

export const OUTFITS = ['NONE', 'BOBBLE_HAT', 'HEADPHONES', 'SCARF', 'CAP', 'HOODIE'];

// Body geometry, shared by the body and by the clothes cut to it.
const HEAD = { y: -162, w: 196, h: 180 };
const TORSO = { y: -46, w: 158, h: 162 };
const ARM = { x: 66, y: -92, len: 78, w: 42 };

export function createPenguinRig({ outfit = 0 } = {}) {
  const rig = createRig({ artboard: 'Пингвин' });
  const { node, ellipse, rect, outline, clip, animation, pose } = rig;

  const blue = 0xff4c7cf3, blueLight = 0xff9dbcff, blueDeep = 0xff3b63d6, rim = { color: 0xffb9d0ff, width: 3 };
  const white = 0xfffdfdff, belly = 0xfff3f6ff, orange = 0xffffa63d, orangeDeep = 0xffe9861f;
  const ink = 0xff141a2e, iris = 0xff2f6fd0, mouthInk = 0xff7a2e2a, blushPink = 0xffff9fb5;
  const coral = 0xffff6f61, coralDeep = 0xffe2544a, coralLight = 0xffffa79e;

  const root = node('Root', 0, 240, 340);
  // Soft light behind the figure: separates it from the black screen without a hard frame.
  ellipse('Glow', root, 0, -120, 440, 440, 0, { glow: [[0, 0x402f5bd6], [0.55, 0x182f5bd6], [1, 0x002f5bd6]] });
  ellipse('Shadow', root, 0, 76, 170, 18, 0xff0b1020, { opacity: 0.9 });

  // Legs: short and thick, big orange feet.
  for (const s of [-1, 1]) {
    const side = s < 0 ? 'Left' : 'Right';
    const hip = node(`${side} leg`, root, s * 34, 10);
    rect(`${side} leg shape`, hip, 0, 22, 46, 58, blue, { radius: 23, outline: rim });
    // Webbed foot: narrow at the ankle, fanning out into three rounded toes joined by webbing.
    const foot = node(`${side} foot`, hip, s * 10, 54, { rotation: s * 0.18 });
    outline(`${side} webbed foot`, foot, 0, 0, [
      [-12, -12, 8], [12, -12, 8], [40, 10, 9], [27, 20, 2], [14, 15, 2], [0, 24, 9],
      [-14, 15, 2], [-27, 20, 2], [-40, 10, 9],
    ], orange, { outline: { color: 0xffffd29a, width: 2.5 } });
    for (const t of [-1, 1]) rect(`${side} web line ${t}`, foot, t * 11, 6, 3, 22, orangeDeep, { radius: 1.5, rotation: -t * 0.75, opacity: 0.8 });
  }

  // Upper body pivots at the hips.
  const figure = node('Figure', root); // the upper body sinks and leans from the hips as a whole; the legs stay planted
  const body = node('Upper body', figure, 0, 0);
  const torso = node('Breathing torso', body);
  ellipse('Torso', torso, 0, TORSO.y, TORSO.w, TORSO.h, blue, { outline: rim });
  ellipse('Belly', torso, 0, TORSO.y + 8, 106, 118, belly);
  ellipse('Belly shine', torso, -22, TORSO.y - 22, 24, 40, white, { rotation: 0.3, opacity: 0.9 });

  // Hoodie body: the torso shape grown by 7 px; hem, pocket and strings are clipped to it.
  const hoodieBody = node('Outfit hoodie', torso, 0, 0, { opacity: 0 });
  const hoodieShape = ellipse('Hoodie body', hoodieBody, 0, TORSO.y, TORSO.w + 14, TORSO.h + 14, coral,
    { outline: { color: 0xffffc2bb, width: 3 } });
  const hoodieDetails = node('Hoodie details', hoodieBody);
  clip(hoodieDetails, hoodieShape.shape);
  ellipse('Hoodie shade', hoodieDetails, 34, TORSO.y + 30, 120, 150, coralDeep, { opacity: 0.35 });
  ellipse('Hoodie shine', hoodieDetails, -38, TORSO.y - 26, 26, 52, coralLight, { rotation: 0.3, opacity: 0.6 });
  rect('Hoodie hem', hoodieDetails, 0, TORSO.y + 76, 200, 26, coralDeep);
  for (let x = -84; x <= 84; x += 14) rect(`Hem rib ${x}`, hoodieDetails, x, TORSO.y + 76, 2.5, 26, coral);
  rect('Hoodie pocket', hoodieDetails, 0, TORSO.y + 46, 96, 38, coralDeep, { radius: 16 });
  rect('Hoodie pocket opening', hoodieDetails, 0, TORSO.y + 31, 80, 5, coral, { radius: 2.5 });
  for (const s of [-1, 1]) {
    rect(`Drawstring ${s}`, hoodieDetails, s * 16, TORSO.y - 4, 4.5, 34, 0xfffff7f5, { radius: 2 });
    ellipse(`Drawstring tip ${s}`, hoodieDetails, s * 16, TORSO.y + 16, 8, 11, 0xffeadfdc);
  }

  // Scarf at the neck, between torso and chin.
  const scarf = node('Outfit scarf', torso, 0, 0, { opacity: 0 });
  rect('Scarf tail', scarf, -38, TORSO.y + 18, 26, 62, 0xff2fb36b, { radius: 10, rotation: -0.12, outline: { color: 0xffb8f0cf, width: 2 } });
  rect('Scarf band', scarf, 0, TORSO.y - 14, 140, 28, 0xff3cc97a, { radius: 14, outline: { color: 0xffb8f0cf, width: 2 } });
  for (const x of [-44, -16, 12, 40]) rect(`Scarf stripe ${x}`, scarf, x, TORSO.y - 14, 9, 28, 0xffd8f5e4);

  // Arms: short and thick, mitten hands. Shoulder (interaction/speaking) -> swing (idle).
  const hangs = {};
  function arm(side, rest) {
    const s = side === 'Left' ? -1 : 1;
    hangs[side] = node(`${side} arm hang`, body, s * ARM.x, ARM.y); // tired poses let the arm hang from here
    const shoulder = node(`${side} arm`, hangs[side], 0, 0, { rotation: rest });
    const swing = node(`${side} arm swing`, shoulder);
    ellipse(`${side} arm shape`, swing, 0, ARM.len / 2, ARM.w, ARM.len, blue, { outline: rim });
    // Sleeve: the arm shape grown by 6 px, its cuff clipped to it.
    const sleeve = node(`Outfit hoodie ${side.toLowerCase()} sleeve`, swing, 0, 0, { opacity: 0 });
    const sleeveShape = ellipse(`${side} sleeve`, sleeve, 0, ARM.len / 2, ARM.w + 12, ARM.len + 10, coral,
      { outline: { color: 0xffffc2bb, width: 3 } });
    const cuff = node(`${side} cuff clip`, sleeve);
    clip(cuff, sleeveShape.shape);
    rect(`${side} cuff`, cuff, 0, ARM.len - 2, 70, 18, coralDeep);
    ellipse(`${side} hand`, swing, 0, ARM.len + 6, 40, 38, blueLight, { outline: rim });
    ellipse(`${side} thumb`, swing, -s * 17, ARM.len - 2, 15, 20, blueLight, { rotation: -s * 0.5 });
    return { shoulder, swing, sleeve };
  }
  const left = arm('Left', 0.22);
  const right = arm('Right', -0.22);

  // Head: posture (expression) -> idle bob -> gaze.
  const headPose = node('Head pose', body, 0, HEAD.y);
  const headIdle = node('Head idle', headPose);
  const head = node('Head gaze', headIdle);

  const tuftPose = node('Tuft pose', head, 0, -HEAD.h / 2 + 6);
  const tuft = node('Tuft sway', tuftPose);
  ellipse('Tuft feather 1', tuft, -10, -12, 14, 34, blue, { rotation: -0.45, outline: rim });
  ellipse('Tuft feather 3', tuft, 13, -11, 12, 30, blue, { rotation: 0.5, outline: rim });
  ellipse('Tuft feather 2', tuft, 2, -18, 14, 40, blue, { rotation: 0.05, outline: rim });

  // Hood back: the head shape grown by 14 px, behind the head.
  const hoodBack = node('Outfit hoodie hood back', head, 0, 0, { opacity: 0 });
  ellipse('Hood back', hoodBack, 0, -4, HEAD.w + 22, HEAD.h + 24, coralDeep, { outline: { color: 0xffffc2bb, width: 3 } });

  ellipse('Head', head, 0, 0, HEAD.w, HEAD.h, blue, { outline: rim });
  ellipse('Head shine', head, -50, -50, 46, 20, blueLight, { rotation: -0.5, opacity: 0.8 });

  // Hood front: covers the head to its contour (clipped), the face shows through its opening.
  const hoodFront = node('Outfit hoodie hood front', head, 0, 0, { opacity: 0 });
  const hoodOutline = ellipse('Hood outline', hoodFront, 0, -2, HEAD.w + 12, HEAD.h + 12, coral);
  const hoodDetails = node('Hood details', hoodFront);
  clip(hoodDetails, hoodOutline.shape);
  ellipse('Hood shine', hoodDetails, -56, -58, 50, 20, coralLight, { rotation: -0.55, opacity: 0.7 });
  ellipse('Hood rim', hoodFront, 0, 6, 178, 152, coralDeep, { stroke: 9 });

  const face = node('Face', head);
  ellipse('Mask left', face, -36, -2, 96, 102, white);
  ellipse('Mask right', face, 36, -2, 96, 102, white);
  ellipse('Mask chin', face, 0, 34, 116, 80, white);
  const leftBlush = ellipse('Left blush', face, -60, 30, 26, 12, blushPink, { opacity: 0.75 });
  const rightBlush = ellipse('Right blush', face, 60, 30, 26, 12, blushPink, { opacity: 0.75 });

  const expressionEyes = node('Expression eyes', face, 0, -4);
  const blinkEyes = node('Blink eyes', expressionEyes);
  const pupils = node('Gaze pupils', blinkEyes);
  for (const [side, x] of [['Left', -36], ['Right', 36]]) {
    ellipse(`${side} iris`, pupils, x, 0, 40, 45, iris);
    ellipse(`${side} pupil`, pupils, x, 2, 24, 28, ink);
    ellipse(`${side} glint`, pupils, x - 8, -9, 12, 13, 0xffffffff);
    ellipse(`${side} small glint`, pupils, x + 8, 10, 6, 6, 0xffffffff);
  }
  const leftBrow = node('Left brow', face, -36, -42);
  rect('Left brow line', leftBrow, 0, 0, 26, 7, blueDeep, { radius: 3.5 });
  const rightBrow = node('Right brow', face, 36, -42);
  rect('Right brow line', rightBrow, 0, 0, 26, 7, blueDeep, { radius: 3.5 });

  // Beak: a pointed triangle. Opening drops the lower half and shows the mouth behind it.
  const beak = node('Beak', face, 0, 30);
  const mouthInside = ellipse('Mouth inside', beak, 0, 8, 24, 14, mouthInk, { opacity: 0 });
  const lowerBeak = node('Lower beak', beak, 0, 4);
  outline('Lower beak shape', lowerBeak, 0, 0, [[-15, 0, 3], [15, 0, 3], [0, 14, 3]], orangeDeep);
  outline('Upper beak', beak, 0, 0, [[-25, -9, 7], [25, -9, 7], [0, 18, 4]], orange,
    { outline: { color: 0xffffd29a, width: 2 } });
  outline('Beak shine', beak, 0, 0, [[-15, -6, 2], [-4, -6, 2], [-8, 2, 2]], 0xffffd8a8);

  // Outfits worn on the head.
  const phones = node('Outfit headphones', head, 0, 0, { opacity: 0 });
  for (let k = 0; k <= 72; k++) {
    const a = Math.PI * (1 + k / 72);
    ellipse(`Headphones band ${k + 1}`, phones, Math.cos(a) * 100, Math.sin(a) * 94 - 2, 13, 13, 0xffff5470);
  }
  ellipse('Left ear cup', phones, -96, 4, 30, 56, 0xffff5470, { outline: { color: 0xffffc0cb, width: 2.5 } });
  ellipse('Right ear cup', phones, 96, 4, 30, 56, 0xffff5470, { outline: { color: 0xffffc0cb, width: 2.5 } });

  const hat = node('Outfit bobble hat', head, 0, -56, { opacity: 0 });
  ellipse('Hat dome', hat, 0, -16, 162, 92, 0xffffc93c, { outline: { color: 0xfffff0b8, width: 3 } });
  rect('Hat stripe', hat, 0, -24, 146, 11, 0xffff6f61, { radius: 5.5 });
  rect('Hat band', hat, 0, 20, 180, 32, 0xffff6f61, { radius: 16, outline: { color: 0xffffc2bb, width: 2.5 } });
  ellipse('Pompom', hat, 0, -66, 42, 42, 0xffffffff);

  const cap = node('Outfit cap', head, 0, -58, { opacity: 0 });
  rect('Cap visor', cap, 52, 18, 100, 20, 0xff22b07d, { radius: 10, rotation: 0.08 });
  ellipse('Cap crown', cap, 0, -4, 172, 90, 0xff2fd396, { outline: { color: 0xffb6f5dc, width: 3 } });
  rect('Cap seam', cap, 0, -4, 5, 78, 0xff22b07d, { radius: 2.5 });
  ellipse('Cap badge', cap, -34, 4, 26, 26, 0xffffffff);

  // --- Animations ---------------------------------------------------------------------------
  const idle = animation('Breathe, blink and sway', [
    [torso, P.scaleY, [[0, 1], [120, 1.02], [240, 1]]],
    [body, P.rotation, [[0, 0], [60, 0.02], [180, -0.02], [240, 0]]],
    [headIdle, P.y, [[0, 0], [120, -4], [240, 0]]],
    [left.swing, P.rotation, [[0, 0], [60, -0.05], [180, 0.05], [240, 0]]],
    [right.swing, P.rotation, [[0, 0], [60, -0.05], [180, 0.05], [240, 0]]],
    [blinkEyes, P.scaleY, [[0, 1], [140, 1], [146, 0.08], [152, 1], [240, 1]]],
    [tuft, P.rotation, [[0, 0], [70, 0.12], [190, -0.1], [240, 0]]],
  ], 240, true);
  const asleep = pose('Still asleep', [
    [torso, P.scaleY, 1], [body, P.rotation, 0], [headIdle, P.y, 4], [left.swing, P.rotation, 0],
    [right.swing, P.rotation, 0], [blinkEyes, P.scaleY, 1], [tuft, P.rotation, 0],
  ]);

  for (const name of CONTRACT.inputs) rig.input(name);
  rig.input('outfit', outfit);

  const look = (name, values) => [-1, 1].map(v => [v, pose(`${name} ${v}`, values(v))]);
  rig.blendLayer('lookX', look('lookX', v => [[face, P.x, v * 12], [head, P.rotation, v * 0.07], [pupils, P.x, v * 6]]));
  rig.blendLayer('lookY', look('lookY', v => [[face, P.y, v * 8], [pupils, P.y, v * 6]]));
  rig.blendLayer('mouthOpen', [0, 1].map(v => [v, pose(`mouthOpen ${v}`, [
    [lowerBeak, P.y, 4 + v * 14],
    [mouthInside.shape, P.opacity, v], [mouthInside.path, P.height, 14 + v * 16], [mouthInside.shape, P.y, 8 + v * 6],
  ])]));

  // NEUTRAL, ATTENTIVE, LISTENING, SLEEPY, SAD: face, tuft and posture.
  const E = (...values) => code => values[code];
  const eyes = E(1, 1.08, 1.14, 0.1, 0.82, 0.55, 0.08);
  const browY = E(-42, -49, -51, -39, -44, -38, -36), browTilt = E(0, 0, -0.08, 0.06, -0.4, -0.48, -0.55);
  const beakDrop = E(30, 29, 29, 32, 33, 34, 37);
  const tuftTilt = E(0, -0.05, 0.05, 0.5, 0.7, 0.8, 1.0), tuftLift = E(1, 1.1, 1.18, 0.85, 0.8, 0.78, 0.7);
  const blush = E(0.75, 0.9, 0.85, 0.4, 0.3, 0.3, 0.3);
  const headY = E(HEAD.y, HEAD.y - 4, HEAD.y - 3, HEAD.y + 5, HEAD.y + 7, HEAD.y + 14, HEAD.y + 32), headTilt = E(0, 0, 0.08, -0.05, 0.06, 0.1, 0.3);
  // Posture of the tired poses; every pose keys every property, so leaving one never leaves its values behind.
  const sinkY = E(0, 0, 0, 0, 0, 10, 36), leanTilt = E(0, 0, 0, 0, 0, 0, 0.18), sinkScale = E(1, 1, 1, 1, 1, 0.97, 0.9), armHang = E(0, 0, 0, 0, 0, 0.1, 0.4);
  rig.categoryLayer('expression', [0, 1, 2, 3, 4, 5, 6].map(c => pose(`expression ${CONTRACT.expression[c]}`, [
    [expressionEyes, P.scaleY, eyes(c)],
    [leftBrow, P.y, browY(c)], [rightBrow, P.y, browY(c)],
    [leftBrow, P.rotation, browTilt(c)], [rightBrow, P.rotation, -browTilt(c)],
    [beak, P.y, beakDrop(c)],
    [tuftPose, P.rotation, tuftTilt(c)], [tuftPose, P.scaleY, tuftLift(c)],
    [leftBlush.shape, P.opacity, blush(c)], [rightBlush.shape, P.opacity, blush(c)],
    [headPose, P.y, headY(c)], [headPose, P.rotation, headTilt(c)],
    [figure, P.y, sinkY(c)], [figure, P.rotation, leanTilt(c)], [figure, P.scaleY, sinkScale(c)],
    [hangs.Left, P.rotation, armHang(c)], [hangs.Right, P.rotation, -armHang(c)],
  ])));

  // IDLE, COMMAND, RECORDING, NOTE: the right hand goes up and waves.
  rig.blendLayer('interaction', [0, 1, 2, 3].map(c => [c, pose(`interaction ${CONTRACT.interaction[c]}`, [
    [right.shoulder, P.rotation, [-0.22, -2.5, -2.1, -1.2][c]],
  ])]));
  rig.blendLayer('speaking', [0, 1].map(v => [v, pose(`speaking ${v}`, [[left.shoulder, P.rotation, 0.22 + v * 0.8]])]));
  rig.blendLayer('sleeping', [[0, idle], [1, asleep]]);

  const hoodieParts = [hoodieBody, left.sleeve, right.sleeve, hoodBack, hoodFront];
  const groups = [[], [hat], [phones], [scarf], [cap], hoodieParts];
  rig.categoryLayer('outfit', OUTFITS.map((name, code) => pose(`outfit ${name}`,
    groups.flat().map(g => [g, P.opacity, groups[code].includes(g) ? 1 : 0]))), 150);

  return rig;
}

export function buildPenguin(options = {}) { return createPenguinRig(options).build(CONTRACT.machine); }

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const args = process.argv.slice(2);
  const outfitArg = args.find(a => a.startsWith('--outfit='));
  const outfit = outfitArg ? Number(outfitArg.split('=')[1]) : 0;
  if (!(Number.isInteger(outfit) && outfit >= 0 && outfit < OUTFITS.length)) throw new Error(`--outfit must be 0..${OUTFITS.length - 1}`);
  const output = args.find(a => !a.startsWith('--')) || fileURLToPath(new URL('../androidApp/src/main/res/raw/penguin_character.riv', import.meta.url));
  const rig = createPenguinRig({ outfit });
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
