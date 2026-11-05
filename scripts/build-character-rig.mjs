// Original Androsaur test character. No downloaded artwork or editor account is required.
// Writes a small Rive v7 runtime file using public rive-runtime core property IDs.
// Source of the format: https://github.com/rive-app/rive-runtime (MIT).
// Run: node scripts/build-character-rig.mjs [output.riv] [dinosaur|cat|dog] [preview.svg]
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const kind = process.argv[3] || 'dinosaur';
if (!['dinosaur', 'cat', 'dog'].includes(kind)) throw new Error('Unknown character');
const svg = [];
const transforms = new Map([[0, '']]);
const chunks = [Buffer.from('RIVE')];
let scene = null;
const componentIds = new Map([[0, 0]]);
function uint(value) {
  const bytes = [];
  do { const low = value & 127; value = Math.floor(value / 128); bytes.push(low | (value ? 128 : 0)); } while (value);
  chunks.push(Buffer.from(bytes));
}
function float(value) { const bytes = Buffer.alloc(4); bytes.writeFloatLE(value); chunks.push(bytes); }
function color(value) { const bytes = Buffer.alloc(4); bytes.writeUInt32LE(value); chunks.push(bytes); }
function string(value) { const bytes = Buffer.from(value); uint(bytes.length); chunks.push(bytes); }
const types = {
  4: string, 5: uint, 7: float, 8: float, 13: float, 14: float, 15: float, 16: float, 17: float, 18: float,
  20: float, 21: float, 37: color, 51: uint, 53: uint, 55: string, 56: uint, 57: uint, 59: uint,
  67: uint, 68: uint, 70: float, 138: string, 140: float, 149: uint, 151: uint, 152: uint, 155: uint, 156: uint,
  157: float, 158: uint, 165: uint, 166: float, 167: uint,
};
function object(type, props = {}) {
  if (scene !== null) { scene.push({ type, props, id: scene.length + 1 }); return; }
  if (type === 25) props = { ...props, 51: componentIds.get(props[51]) };
  uint(type);
  for (const [key, value] of Object.entries(props)) { uint(Number(key)); types[key](value); }
  uint(0);
}
// Major, minor, file ID, empty property table. All properties used below are known to v7 runtimes.
[7, 0, 0, 0].forEach(uint);
object(23); // Backboard
object(1, { 4: 'Androsaur', 7: 480, 8: 480 }); // Component zero is the artboard.
scene = [];
let component = 1;
function node(name, parent, x = 0, y = 0) {
  const id = component++;
  object(2, { 4: name, 5: parent, 13: x, 14: y });
  transforms.set(id, `${transforms.get(parent)} translate(${x} ${y})`);
  return id;
}
function ellipse(name, parent, x, y, w, h, tint, triangle = false) {
  const shape = component++;
  object(3, { 4: name, 5: parent, 13: x, 14: y });
  const path = component++;
  object(triangle ? 8 : 4, { 5: shape, 20: w, 21: h });
  const fill = component++;
  object(20, { 5: shape });
  component++;
  object(18, { 5: fill, 37: tint });
  const attrs = `fill="#${(tint & 0xffffff).toString(16).padStart(6, '0')}"`;
  const mark = triangle ? `<polygon points="0,${-h/2} ${w/2},${h/2} ${-w/2},${h/2}" ${attrs}/>` : `<ellipse rx="${w/2}" ry="${h/2}" ${attrs}/>`;
  const cls = name.includes('eye') || name.includes('pupil') || name.includes('glint') ? 'eye' : name === 'Mouth' ? 'mouth' : '';
  svg.push(`<g transform="${transforms.get(parent)} translate(${x} ${y})"><g class="${cls}">${mark}</g></g>`);
  return { shape, path };
}
const green = kind === 'cat' ? 0xffbd9aff : kind === 'dog' ? 0xfff5ae64 : 0xff63e6a4;
const dark = kind === 'cat' ? 0xff38254f : kind === 'dog' ? 0xff472e22 : 0xff112a24;
const pale = kind === 'cat' ? 0xfff1dfff : kind === 'dog' ? 0xffffe4bd : 0xffc9f6d8;
const accent = kind === 'cat' ? 0xff9266d4 : kind === 'dog' ? 0xffaf673e : 0xff39ae7c;
const root = node('Root', 0, 240, 240);
ellipse('Shadow', root, 0, 190, 230, 20, 0xff142a22);
const body = node('Breathing body', root, 0, 95);
ellipse('Tail', body, 87, 40, kind === 'dinosaur' ? 125 : 65, kind === 'cat' ? 95 : 48, accent);
ellipse('Left foot', body, -56, 77, 76, 40, green);
ellipse('Right foot', body, 56, 77, 76, 40, green);
ellipse('Body', body, 0, 0, 170, 176, green);
ellipse('Belly', body, 0, 15, 106, 126, pale);
const leftArm = node('Left arm', body, -82, -20);
ellipse('Left hand', leftArm, -9, 22, 40, 84, accent);
const rightArm = node('Right arm', body, 82, -20);
ellipse('Right hand', rightArm, 9, 22, 40, 84, accent);
const headMotion = node('Head idle', root, 0, -55);
const head = node('Head gaze', headMotion);
const earY = kind === 'cat' ? -100 : kind === 'dog' ? 0 : -53;
ellipse('Left ear', head, -106, earY, kind === 'cat' ? 78 : 60, kind === 'dog' ? 190 : kind === 'cat' ? 110 : 78, accent, kind === 'cat');
ellipse('Right ear', head, 106, earY, kind === 'cat' ? 78 : 60, kind === 'dog' ? 190 : kind === 'cat' ? 110 : 78, accent, kind === 'cat');
if (kind === 'cat') {
  ellipse('Left inner ear', head, -106, -96, 44, 70, 0xfff4a9cb, true);
  ellipse('Right inner ear', head, 106, -96, 44, 70, 0xfff4a9cb, true);
}
ellipse('Head', head, 0, 0, 270, 226, green);
if (kind === 'dog') ellipse('Eye patch', head, -56, -29, 93, 110, accent);
ellipse('Snout', head, 0, 45, 200, 116, pale);
if (kind === 'dinosaur') {
  ellipse('Left nostril', head, -26, 17, 9, 6, accent);
  ellipse('Right nostril', head, 26, 17, 9, 6, accent);
} else {
  ellipse('Nose', head, 0, 30, kind === 'cat' ? 24 : 45, kind === 'cat' ? 16 : 30, dark);
  if (kind === 'cat') for (const side of [-1, 1]) for (const y of [36, 49, 62])
    ellipse(`Whisker ${side} ${y}`, head, side * 105, y, 57, 3, dark);
}
ellipse('Left cheek', head, -95, 28, 30, 16, 0xffefad91);
ellipse('Right cheek', head, 95, 28, 30, 16, 0xffefad91);
const expressionEyes = node('Expression eyes', head, 0, -29);
const blinkEyes = node('Blink eyes', expressionEyes);
ellipse('Left eye', blinkEyes, -56, 0, 62, 74, 0xffffffff);
ellipse('Right eye', blinkEyes, 56, 0, 62, 74, 0xffffffff);
const pupils = node('Gaze pupils', blinkEyes);
ellipse('Left pupil', pupils, -56, 0, 29, 40, dark);
ellipse('Right pupil', pupils, 56, 0, 29, 40, dark);
ellipse('Left glint', pupils, -61, -9, 8, 10, 0xffffffff);
ellipse('Right glint', pupils, 51, -9, 8, 10, 0xffffffff);
const leftBrow = node('Left brow', head, -56, -84);
ellipse('Left brow line', leftBrow, 0, 0, 47, 8, dark);
const rightBrow = node('Right brow', head, 56, -84);
ellipse('Right brow line', rightBrow, 0, 0, 47, 8, dark);
const mouth = ellipse('Mouth', head, 0, 60, 66, 8, dark);
const status = ellipse('Listening light', body, 0, -20, 18, 18, 0xffe6aa58);

// Rive draws earlier components on top. Author back-to-front above, then reverse and remap
// every reference, including keyed animation targets. Children may precede their parents.
const ordered = scene.reverse();
scene = null;
ordered.forEach((record, index) => componentIds.set(record.id, index + 1));
for (const {type, props} of ordered) object(type, {...props, 5: componentIds.get(props[5])});

let animationCount = 0;
function animation(name, tracks, duration = 60, loop = 0) {
  const id = animationCount++;
  object(31, { 55: name, 56: 60, 57: duration, 59: loop });
  for (const [id, properties] of tracks) {
    object(25, { 51: id });
    for (const [key, frames] of properties) {
      object(26, { 53: key });
      for (const [frame, value] of frames) object(30, { 67: frame, 68: 1, 70: value });
    }
  }
  return id;
}
const pose = (name, tracks) => animation(name, tracks.map(([id, key, value]) => [id, [[key, [[0, value]]]] ]));
const idle = animation('Breathe and blink', [
  [body, [[17, [[0, 1], [120, 1.025], [240, 1]]]]],
  [headMotion, [[14, [[0, -55], [120, -59], [240, -55]]]]],
  [blinkEyes, [[17, [[0, 1], [168, 1], [174, 0.06], [180, 1], [240, 1]]]]],
], 240, 1);
const layers = [];
function blend(name, stops, makeTracks) {
  const animations = stops.map(value => [value, pose(`${name} ${value}`, makeTracks(value))]);
  layers.push({ name, animations });
}
blend('lookX', [-1, 1], v => [[pupils, 13, v * 13], [head, 13, v * 12], [head, 15, v * 0.045]]);
blend('lookY', [-1, 1], v => [[pupils, 14, v * 12], [head, 14, v * 8]]);
blend('mouthOpen', [0, 1], v => [[mouth.path, 21, 8 + v * 48]]);
// Stable expression codes: NEUTRAL=0, ATTENTIVE=1, LISTENING=2, SLEEPY=3, SAD=4.
// Expressions are categories, not a scale: a 1D blend from 0 to 4 would pass through every code
// in between (NEUTRAL -> SAD closed the eyes at SLEEPY). Each code is its own state instead, and
// Rive crossfades directly between any two of them. The app sets the code without animating it.
const expressionPoses = [0, 1, 2, 3, 4].map(v => pose(`expression ${v}`, [
  [expressionEyes, 17, [1, 1.06, 1.17, 0.12, 0.72][v]],
  [leftBrow, 15, [0, -0.12, -0.22, 0.05, -0.3][v]],
  [rightBrow, 15, [0, 0.12, 0.22, -0.05, 0.3][v]],
  [mouth.shape, 16, [1, 1.18, 0.65, 0.8, 0.8][v]],
]));
const EXPRESSION_MIX_MS = 300;
blend('interaction', [0, 1, 2, 3], v => [[status.shape, 18, [0, 1, 1, 0.4][v]], [rightArm, 15, [0, -0.5, -0.7, -0.2][v]]]);
blend('speaking', [0, 1], v => [[leftArm, 15, v * 0.3]]);
// A dedicated idle/sleep blend stops the autonomous motion before the Android renderer pauses.
const asleep = pose('Still asleep', [[body, 17, 1], [headMotion, 14, -55], [blinkEyes, 17, 1]]);
object(53, { 55: 'Character' });
// Input order is the public contract; the Android adapter checks the names.
const inputNames = ['lookX', 'lookY', 'mouthOpen', 'expression', 'interaction', 'speaking', 'sleeping'];
for (const name of inputNames) object(56, { 138: name, 140: 0 });
const input = name => inputNames.indexOf(name);
const blendLayers = [...layers, { name: 'sleeping', animations: [[0, idle], [1, asleep]] }];
function blendLayer({ name, animations }) {
  object(57, { 138: name });
  object(62); // Any, state 0. Older runtimes require all three system states.
  object(64); // Exit, state 1
  object(63); // Entry, state 2
  object(65, { 151: 3 }); // Immediately enter the blend state.
  object(76, { 167: input(name) }); // BlendState1DInput, state 3
  for (const [value, animation] of animations) object(75, { 165: animation, 166: value });
}
function expressionLayer() {
  object(57, { 138: 'expression' });
  object(62); // Any, state 0: from wherever the layer is, go straight to the requested expression.
  expressionPoses.forEach((_, code) => {
    // Early exit lets a new code interrupt a crossfade that is still running.
    object(65, { 151: 3 + code, 152: 32, 158: EXPRESSION_MIX_MS });
    object(70, { 155: input('expression'), 156: 0, 157: code }); // expression == code
  });
  object(64); // Exit, state 1
  object(63); // Entry, state 2
  object(65, { 151: 3 }); // Start at NEUTRAL; the Any transitions take over on the first frame.
  for (const animation of expressionPoses) object(61, { 149: animation }); // States 3..7
}
blendLayers.slice(0, 3).forEach(blendLayer); // lookX, lookY, mouthOpen
expressionLayer();
blendLayers.slice(3).forEach(blendLayer); // interaction, speaking, sleeping
const output = process.argv[2] || fileURLToPath(new URL('../androidApp/src/main/res/raw/androsaur_character.riv', import.meta.url));
mkdirSync(dirname(resolve(output)), { recursive: true });
writeFileSync(output, Buffer.concat(chunks));
const svgStyle = `<style>@keyframes breathe{50%{transform:translateY(-4px)}}@keyframes blink{0%,68%,76%,100%{transform:scaleY(1)}72%{transform:scaleY(.08)}}@keyframes talk{50%{transform:scaleY(5)}}.figure{animation:breathe 4s ease-in-out infinite}.eye{animation:blink 4s infinite}.mouth{animation:talk .7s ease-in-out infinite;animation-play-state:paused}svg:hover .mouth{animation-play-state:running}@media(prefers-reduced-motion:reduce){.figure,.eye,.mouth{animation:none}}</style>`;
const svgOutput = process.argv[4] || resolve('artifacts/characters', kind + '.svg');
mkdirSync(dirname(resolve(svgOutput)), { recursive: true });
writeFileSync(svgOutput, `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 480 480" role="img" aria-label="${kind}">${svgStyle}<g class="figure">${svg.join('')}</g></svg>`);
console.log(`Wrote ${output}: ${Buffer.concat(chunks).length} bytes, ${component} components, ${animationCount} animations`);
