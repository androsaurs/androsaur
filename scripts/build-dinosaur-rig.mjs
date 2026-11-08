// Dinosaur: the default Androsaur character (green, rim light, tail with plates, crest, short clawed arms).
// Same contract (artboard, machine, inputs) as the other characters; built directly on rive/writer.mjs.
// Expressions 5 TIRED and 6 EXHAUSTED belong to the health mode (too long at the computer): they are living,
// looping scenes meant to make the user want to help, and the only way to help is to step away and rest.
// Run: node scripts/build-dinosaur-rig.mjs [output.riv] [--preview=output.svg]
import { fileURLToPath } from 'node:url';
import { createRig, CONTRACT, P } from './rive/writer.mjs';
import { runAnimalCli } from './rive/animal.mjs';

const LOOP = 360; // 6 s at 60 fps: length of the looping expression scenes

export function createDinosaurRig() {
  const rig = createRig({ artboard: 'Androsaur' });
  const { node, ellipse, outline, clip, animation, pose } = rig;
  const green = 0xff63e6a4, dark = 0xff112a24, pale = 0xffc9f6d8, accent = 0xff39ae7c, deep = 0xff2a8f63;
  const rim = { color: 0xffa8f5c8, width: 3 };
  const water = 0xff9fdcff, white = 0xffffffff, claw = 0xfff2fff6;

  const root = node('Root', 0, 240, 240);
  const glow = ellipse('Glow', root, 0, -20, 470, 470, 0, { glow: [[0, 0x3a63e6a4], [0.55, 0x1463e6a4], [1, 0x0063e6a4]] });
  ellipse('Shadow', root, 0, 190, 250, 20, 0xff0f2a20);

  // Tail on the ground behind the figure: it sways from its root and stays put when the figure leans.
  const tail = node('Tail sway', root, 40, 150);
  const tailPlates = [[58.6, -27.4, 1.15, 0.197], [87.0, -36.6, 1.05, -0.333], [123.6, -60.4, 0.85, -0.841]];
  for (const [x, y, s, r] of tailPlates) {
    outline(`Tail plate ${x}`, tail, x, y, [[-14, 8, 4], [14, 8, 4], [0, -18, 5]].map(([px, py, c]) => [px * s, py * s, c]), accent, { rotation: r });
  }
  outline('Tail', tail, 0, 0, [[-20, -38, 0], [60, -22, 30], [112, -40, 28], [146, -78, 6], [148, -32, 24], [108, 14, 32], [40, 30, 20], [-20, 28, 0]],
    green, { outline: rim });

  // The figure leans and sinks around a pivot on the ground, so the feet (outside it) stay planted.
  const GROUND = 175;
  const figure = node('Figure', root, 0, GROUND);
  const body = node('Breathing body', figure, 0, 95 - GROUND);
  ellipse('Body', body, 0, 0, 172, 178, green, { outline: rim });
  ellipse('Belly', body, 0, 15, 108, 128, pale);
  for (const [y, w] of [[-22, 70], [8, 94], [38, 94], [64, 70]]) ellipse(`Belly stripe ${y}`, body, 0, y, w, 4, 0xffa6e8be);
  const arm = side => {
    const n = side < 0 ? 'Left' : 'Right';
    const hang = node(`${n} arm hang`, body, side * 72, -30);
    const a = node(`${n} arm`, hang);
    ellipse(`${n} hand`, a, side * -2, 18, 28, 50, accent, { rotation: side * -0.4 });
    ellipse(`${n} claw a`, a, side * -12, 40, 7, 7, claw);
    ellipse(`${n} claw b`, a, side * -3, 43, 7, 7, claw);
    return { hang, a };
  };
  const L = arm(-1), R = arm(1);

  // Feet in front of the body, outside the figure.
  const foot = side => {
    const n = side < 0 ? 'Left' : 'Right';
    const f = node(`${n} foot`, root, side * 56, 172);
    ellipse(`${n} foot shape`, f, 0, 0, 80, 42, green, { outline: rim });
    for (const t of [-1, 0, 1]) ellipse(`${n} claw ${t}`, f, t * 20, 12, 13, 11, claw);
    return f;
  };
  const leftFoot = foot(-1), rightFoot = foot(1);

  const headMotion = node('Head idle', figure, 0, -55 - GROUND);
  const headDroop = node('Head droop', headMotion);
  const head = node('Head gaze', headDroop);
  const crest = node('Crest', head, 0, -110);
  const status = ellipse('Listening light', crest, 0, -8, 96, 96, 0,
    { glow: [[0, 0xccffc35a], [0.5, 0x55ffc35a], [1, 0x00ffc35a]], opacity: 0 });
  outline('Crest left', crest, -58, 12, [[-22, 20, 6], [22, 20, 6], [-4, -30, 8]], accent, { rotation: -0.4 });
  outline('Crest right', crest, 58, 12, [[-22, 20, 6], [22, 20, 6], [4, -30, 8]], accent, { rotation: 0.4 });
  outline('Crest middle', crest, 0, -6, [[-26, 24, 6], [26, 24, 6], [0, -38, 9]], accent);
  ellipse('Head', head, 0, 0, 272, 228, green, { outline: rim });
  ellipse('Head shine', head, -78, -62, 54, 26, 0xffa8f5c8, { rotation: -0.5, opacity: 0.6 });
  ellipse('Snout', head, 0, 45, 204, 118, pale);
  ellipse('Left nostril', head, -24, 16, 13, 8, deep, { rotation: 0.3 });
  ellipse('Right nostril', head, 24, 16, 13, 8, deep, { rotation: -0.3 });
  ellipse('Left cheek', head, -96, 30, 34, 18, 0xffefad91);
  ellipse('Right cheek', head, 96, 30, 34, 18, 0xffefad91);

  const expressionEyes = node('Expression eyes', head, 0, -29);
  const blinkEyes = node('Blink eyes', expressionEyes);
  const eyeShapes = [ellipse('Left eye', blinkEyes, -56, 0, 64, 76, white), ellipse('Right eye', blinkEyes, 56, 0, 64, 76, white)];
  const pupils = node('Gaze pupils', blinkEyes);
  const mood = node('Pupil mood', pupils); // big glossy "please" pupils when tired
  for (const s of [-1, 1]) {
    const eye = node(`${s < 0 ? 'Left' : 'Right'} pupil mood`, mood, s * 56 + 2, 2);
    ellipse(`${s < 0 ? 'Left' : 'Right'} pupil`, eye, 0, 0, 32, 44, dark);
    ellipse(`${s < 0 ? 'Left' : 'Right'} glint`, eye, -6, -10, 11, 13, white);
    ellipse(`${s < 0 ? 'Left' : 'Right'} glint small`, eye, 6, 10, 5, 5, white);
  }
  // Heavy eyelids: head-coloured caps with a lash line, cut to each eye, that slide down; outer corners droop.
  const lids = node('Eyelids', blinkEyes, 0, -40, { opacity: 0 });
  [-1, 1].forEach((s, i) => {
    const lid = node(`Lid ${s}`, lids, s * 56, 0, { rotation: s * 0.2 });
    ellipse(`Lid cap ${s}`, lid, 0, -40, 110, 80, green);
    ellipse(`Lid line ${s}`, lid, 0, 0, 80, 6, dark);
    clip(lid, eyeShapes[i].shape);
  });
  // Wet line along the lower lid: the eyes look watery.
  const wet = node('Wet eyes', blinkEyes, 0, 30, { opacity: 0 });
  for (const s of [-1, 1]) ellipse(`Wet ${s}`, wet, s * 56, 0, 40, 6, water);

  // Closed eyes (exhausted): soft downward arcs.
  const closed = node('Closed eyes', head, 0, -22, { opacity: 0 });
  for (const s of [-1, 1]) outline(`Closed eye ${s}`, closed, s * 56, 0, [[-27, -5, 3], [0, 13, 26], [27, -5, 3], [0, 5, 22]], dark);
  // A tear that rolls down the cheek.
  const tear = node('Tear', head, -78, 4, { opacity: 0, scaleX: 1.5, scaleY: 1.5 });
  outline('Tear drop', tear, 0, 0, [[0, -12, 0], [8, 3, 8], [0, 11, 6], [-8, 3, 8]], water);
  ellipse('Tear shine', tear, -2, 2, 3, 4, white);
  // A drop of sweat on the forehead.
  const sweat = node('Sweat', head, 112, -62, { opacity: 0 });
  outline('Sweat drop', sweat, 0, 0, [[0, -16, 0], [11, 4, 11], [0, 15, 8], [-11, 4, 11]], water, { outline: { color: white, width: 2 } });
  ellipse('Sweat shine', sweat, -3, 4, 4, 6, white);

  const leftBrow = node('Left brow', head, -56, -80);
  ellipse('Left brow line', leftBrow, 0, 0, 44, 9, dark);
  const rightBrow = node('Right brow', head, 56, -80);
  ellipse('Right brow line', rightBrow, 0, 0, 44, 9, dark);
  const mouth = node('Mouth', head, 0, 60);
  const mouthHole = ellipse('Mouth hole', mouth, 0, 0, 56, 22, dark);
  ellipse('Tooth', mouth, 14, -5, 8, 9, white);
  const mouthLid = ellipse('Mouth lid', mouth, 0, -6, 66, 19, pale);

  // Daydream bubble: a sun over a green hill. What he wishes for is a break outside.
  const thought = node('Thought', root, 0, 0, { opacity: 0 });
  ellipse('Thought dot 1', thought, 90, -108, 12, 12, 0xffe8fff1);
  ellipse('Thought dot 2', thought, 106, -128, 20, 18, 0xffe8fff1);
  const bubbleNode = node('Thought bubble', thought, 148, -170);
  const bubble = ellipse('Bubble', bubbleNode, 0, 0, 124, 92, 0xffe8fff1, { outline: { color: 0xffffffff, width: 3 } });
  const scene = node('Bubble scene', bubbleNode);
  ellipse('Sky', scene, 0, 0, 124, 92, 0xffbfe9ff);
  ellipse('Sun glow', scene, -22, -14, 52, 52, 0, { glow: [[0, 0xccffd36b], [1, 0x00ffd36b]] });
  ellipse('Sun', scene, -22, -14, 26, 26, 0xffffc93c);
  ellipse('Far hill', scene, 40, 40, 110, 60, 0xff8fe0a8);
  ellipse('Hill', scene, -10, 50, 150, 64, 0xff4fcf86);
  ellipse('Tree crown', scene, 30, 4, 22, 24, 0xff2f9a5c);
  ellipse('Tree trunk', scene, 30, 18, 4, 12, 0xff8a5a3c);
  clip(scene, bubble.shape);

  // Idle: 10 s loop; irregular blinks incl. a double blink; tail sways; crest bobs.
  const idle = animation('Breathe, blink, sway', [
    [body, P.scaleY, [[0, 1], [150, 1.025], [300, 1], [450, 1.025], [600, 1]]],
    [headMotion, P.y, [[0, -55 - GROUND], [150, -59 - GROUND], [300, -55 - GROUND], [450, -59 - GROUND], [600, -55 - GROUND]]],
    [tail, P.rotation, [[0, 0], [100, -0.07], [300, 0.04], [480, -0.05], [600, 0]]],
    [crest, P.rotation, [[0, 0], [170, 0.03], [340, -0.03], [600, 0]]],
    [blinkEyes, P.scaleY, [[0, 1], [130, 1], [136, 0.06], [142, 1], [330, 1], [336, 0.06], [342, 1], [354, 1], [360, 0.06], [366, 1], [540, 1], [546, 0.06], [552, 1], [600, 1]]],
  ], 600, true);
  const blend = (name, stops, makeTracks) => rig.blendLayer(name, stops.map(value => [value, pose(`${name} ${value}`, makeTracks(value))]));
  for (const name of CONTRACT.inputs) rig.input(name);
  blend('lookX', [-1, 1], v => [[pupils, P.x, v * 13], [head, P.x, v * 12], [head, P.rotation, v * 0.045]]);
  blend('lookY', [-1, 1], v => [[pupils, P.y, v * 12], [head, P.y, v * 8]]);
  blend('mouthOpen', [0, 1], v => [[mouthHole.path, P.height, 22 + v * 38], [mouthHole.shape, P.y, v * 7], [mouthLid.shape, P.opacity, 1 - v]]);

  // Expression table: one column per code 0..6. A value is a number (held) or [[frame, value], ...] (looped scene).
  // Every expression keys every row, so leaving one never leaves a value behind.
  const k = frames => frames; // marks a keyed track for readability
  const rows = [
    [glow.shape, P.opacity, [1, 1, 1, 0.8, 0.8, 0.55, 0.25]],
    [expressionEyes, P.scaleY, [1, 1.06, 1.17, 0.12, 0.72, 1, 1]],
    [expressionEyes, P.opacity, [1, 1, 1, 1, 1, 1, 0]],
    [closed, P.opacity, [0, 0, 0, 0, 0, 0, 1]],
    [lids, P.opacity, [0, 0, 0, 0, 0, 1, 0]],
    // Dozing off: the lids sink, the head nods… and jerks back up.
    [lids, P.y, [-40, -40, -40, -40, -40, k([[0, -16], [150, -2], [168, -24], [220, -16], [LOOP, -16]]), -40]],
    [wet, P.opacity, [0, 0, 0, 0, 0.6, 1, 0]],
    [mood, P.scaleX, [1, 1, 1, 1, 1.08, 1.18, 1]],
    [mood, P.scaleY, [1, 1, 1, 1, 1.08, 1.18, 1]],
    [mood, P.y, [0, 0, 0, 0, -2, -4, 0]],
    [leftBrow, P.rotation, [0, -0.12, -0.22, 0.05, -0.3, -0.42, -0.5]],
    [rightBrow, P.rotation, [0, 0.12, 0.22, -0.05, 0.3, 0.42, 0.5]],
    [leftBrow, P.y, [-80, -86, -90, -76, -80, -84, -72]],
    [rightBrow, P.y, [-80, -86, -90, -76, -80, -84, -72]],
    [mouth, P.scaleX, [1, 1.12, 0.75, 0.6, 0.85, 0.7, 0.55]],
    [mouth, P.scaleY, [1, 1.15, 0.8, 0.5, -0.8, -0.6, -0.5]],
    [crest, P.scaleY, [1, 1.12, 1.18, 0.9, 0.85, 0.72, 0.55]],
    [tail, P.y, [150, 150, 150, 150, 154, 154, 160]],
    [tail, P.scaleY, [1, 1, 1, 1, 1, 0.92, 0.8]],
    // Exhausted: he plops down on his bottom and leans; a slow heavy sigh runs through the body.
    [figure, P.y, [GROUND, GROUND, GROUND, GROUND, GROUND, GROUND + 6, GROUND + 26]],
    [figure, P.rotation, [0, 0, 0, 0, 0, 0, k([[0, 0.07], [180, 0.09], [LOOP, 0.07]])]],
    [figure, P.scaleY, [1, 1, 1, 1, 1, 0.98, k([[0, 0.9], [120, 0.87], [240, 0.9], [LOOP, 0.9]])]],
    [headDroop, P.y, [0, 0, 0, 0, 0, k([[0, 8], [150, 22], [168, 4], [220, 8], [LOOP, 8]]), 30]],
    [headDroop, P.rotation, [0, 0, 0, 0, 0, k([[0, 0.05], [150, 0.1], [168, 0.03], [LOOP, 0.05]]), 0.2]],
    [L.hang, P.rotation, [0, 0, 0, 0, 0, 0.12, 0.45]],
    [R.hang, P.rotation, [0, 0, 0, 0, 0, -0.12, -0.45]],
    [leftFoot, P.x, [-56, -56, -56, -56, -56, -56, -76]],
    [rightFoot, P.x, [56, 56, 56, 56, 56, 56, 76]],
    [leftFoot, P.rotation, [0, 0, 0, 0, 0, 0, -0.3]],
    [rightFoot, P.rotation, [0, 0, 0, 0, 0, 0, 0.3]],
    [sweat, P.opacity, [0, 0, 0, 0, 0, k([[0, 0], [30, 1], [280, 1], [340, 0], [LOOP, 0]]), 1]],
    [sweat, P.y, [-62, -62, -62, -62, -62, k([[0, -64], [340, -48], [LOOP, -64]]), -56]],
    [tear, P.opacity, [0, 0, 0, 0, 0, 0, k([[0, 0], [30, 1], [250, 1], [320, 0], [LOOP, 0]])]],
    [tear, P.y, [4, 4, 4, 4, 4, 4, k([[0, 0], [300, 34], [LOOP, 34]])]],
    [thought, P.opacity, [0, 0, 0, 0, 0, 1, 1]],
    [bubbleNode, P.y, [-170, -170, -170, -170, -170, k([[0, -170], [180, -175], [LOOP, -170]]), -170]],
    [bubbleNode, P.scaleX, [1, 1, 1, 1, 1, 1, k([[0, 1], [180, 1.05], [LOOP, 1]])]],
    [bubbleNode, P.scaleY, [1, 1, 1, 1, 1, 1, k([[0, 1], [180, 1.05], [LOOP, 1]])]],
  ];
  const expressions = [0, 1, 2, 3, 4, 5, 6].map(v => {
    const tracks = rows.map(([id, key, values]) => [id, key, Array.isArray(values[v]) ? values[v] : [[0, values[v]]]]);
    const looped = tracks.some(([, , frames]) => frames.length > 1);
    return animation(`expression ${v}`, tracks, looped ? LOOP : 60, looped);
  });
  rig.categoryLayer('expression', expressions);
  blend('interaction', [0, 1, 2, 3], v => [[status.shape, P.opacity, [0, 1, 1, 0.5][v]], [R.a, P.rotation, [0, -1.5, -1.7, -0.9][v]]]);
  blend('speaking', [0, 1], v => [[L.a, P.rotation, v * 0.6]]);
  const asleep = pose('Still asleep', [[body, P.scaleY, 1], [headMotion, P.y, -55 - GROUND], [blinkEyes, P.scaleY, 1], [tail, P.rotation, 0], [crest, P.rotation, 0]]);
  rig.blendLayer('sleeping', [[0, idle], [1, asleep]]);
  return rig;
}

export const buildDinosaur = () => createDinosaurRig().build(CONTRACT.machine);

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await runAnimalCli('dinosaur', fileURLToPath(new URL('../androidApp/src/main/res/raw/androsaur_character.riv', import.meta.url)), createDinosaurRig);
}
