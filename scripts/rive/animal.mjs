// Shared body of the dinosaur, cat and dog: the same skeleton, expressions and animations on top of rive/writer.mjs.
// A character file (build-dinosaur-rig.mjs, ...) describes only what differs: colours, tail, ears, nose.
import { createRig, CONTRACT, P } from './writer.mjs';

export function createAnimalRig(spec) {
  const rig = createRig({ artboard: 'Androsaur' });
  const { node, ellipse, triangle, animation, pose } = rig;
  const { green, dark, pale, accent } = spec.palette;

  const root = node('Root', 0, 240, 240);
  ellipse('Shadow', root, 0, 190, 230, 20, 0xff142a22);
  const figure = node('Figure', root); // slumps and leans as a whole (tired poses)
  const body = node('Breathing body', figure, 0, 95);
  ellipse('Tail', body, 87, 40, spec.tail[0], spec.tail[1], accent);
  ellipse('Left foot', body, -56, 77, 76, 40, green);
  ellipse('Right foot', body, 56, 77, 76, 40, green);
  ellipse('Body', body, 0, 0, 170, 176, green);
  ellipse('Belly', body, 0, 15, 106, 126, pale);
  const leftHang = node('Left arm hang', body, -82, -20); // the arm hangs from here (tired poses)
  const leftArm = node('Left arm', leftHang);
  ellipse('Left hand', leftArm, -9, 22, 40, 84, accent);
  const rightHang = node('Right arm hang', body, 82, -20);
  const rightArm = node('Right arm', rightHang);
  ellipse('Right hand', rightArm, 9, 22, 40, 84, accent);
  const headMotion = node('Head idle', figure, 0, -55);
  const headDroop = node('Head droop', headMotion); // the head sags and tilts (tired poses)
  const head = node('Head gaze', headDroop);
  const parts = { ellipse, triangle, head, palette: spec.palette };
  spec.ears(parts);
  ellipse('Head', head, 0, 0, 270, 226, green);
  spec.afterHead?.(parts);
  ellipse('Snout', head, 0, 45, 200, 116, pale);
  spec.nose(parts);
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
  // Mouth: a dark oval with a snout-coloured lid over its top makes a light crescent smile (as on Puf and the hamster).
  // Speaking drops the lid and opens the oval; a negative scaleY on the whole mouth turns the smile into a frown.
  const mouth = node('Mouth', head, 0, 60);
  const mouthHole = ellipse('Mouth hole', mouth, 0, 0, 54, 20, dark);
  const mouthLid = ellipse('Mouth lid', mouth, 0, -6, 64, 18, pale);
  const status = ellipse('Listening light', body, 0, -20, 18, 18, 0xffe6aa58);

  const idle = animation('Breathe and blink', [
    [body, P.scaleY, [[0, 1], [120, 1.025], [240, 1]]],
    [headMotion, P.y, [[0, -55], [120, -59], [240, -55]]],
    [blinkEyes, P.scaleY, [[0, 1], [168, 1], [174, 0.06], [180, 1], [240, 1]]],
  ], 240, true);
  const blend = (name, stops, makeTracks) =>
    rig.blendLayer(name, stops.map(value => [value, pose(`${name} ${value}`, makeTracks(value))]));
  for (const name of CONTRACT.inputs) rig.input(name);
  blend('lookX', [-1, 1], v => [[pupils, P.x, v * 13], [head, P.x, v * 12], [head, P.rotation, v * 0.045]]);
  blend('lookY', [-1, 1], v => [[pupils, P.y, v * 12], [head, P.y, v * 8]]);
  blend('mouthOpen', [0, 1], v => [[mouthHole.path, P.height, 20 + v * 36], [mouthHole.shape, P.y, v * 6], [mouthLid.shape, P.opacity, 1 - v]]);
  // Expressions are categories, not a scale: each code is its own state and Rive crossfades directly between any two.
  // TIRED follows the first reminder to stop sitting (sad, weary, a little sagging), EXHAUSTED the final one (eyes shut,
  // the whole figure sinks and leans, the head droops, the arms hang limp). Every pose keys every property below,
  // so leaving a pose never leaves a value of it behind.
  rig.categoryLayer('expression', [0, 1, 2, 3, 4, 5, 6].map(v => pose(`expression ${v}`, [
    [expressionEyes, P.scaleY, [1, 1.06, 1.17, 0.12, 0.72, 0.5, 0.08][v]],
    [leftBrow, P.rotation, [0, -0.12, -0.22, 0.05, -0.3, -0.38, -0.45][v]],
    [rightBrow, P.rotation, [0, 0.12, 0.22, -0.05, 0.3, 0.38, 0.45][v]],
    [mouth, P.scaleX, [1, 1.12, 0.75, 0.6, 0.85, 0.8, 0.6][v]],
    [mouth, P.scaleY, [1, 1.15, 0.8, 0.5, -0.8, -0.55, -0.4][v]],
    [figure, P.y, [0, 0, 0, 0, 0, 12, 42][v]],
    [figure, P.rotation, [0, 0, 0, 0, 0, 0, 0.2][v]],
    [figure, P.scaleY, [1, 1, 1, 1, 1, 0.97, 0.88][v]],
    [headDroop, P.y, [0, 0, 0, 0, 0, 10, 34][v]],
    [headDroop, P.rotation, [0, 0, 0, 0, 0, 0.08, 0.3][v]],
    [leftHang, P.rotation, [0, 0, 0, 0, 0, 0.12, 0.5][v]],
    [rightHang, P.rotation, [0, 0, 0, 0, 0, -0.12, -0.5][v]],
  ])));
  blend('interaction', [0, 1, 2, 3], v => [[status.shape, P.opacity, [0, 1, 1, 0.4][v]], [rightArm, P.rotation, [0, -0.5, -0.7, -0.2][v]]]);
  blend('speaking', [0, 1], v => [[leftArm, P.rotation, v * 0.3]]);
  // A dedicated idle/sleep blend stops the autonomous motion before the Android renderer pauses.
  const asleep = pose('Still asleep', [[body, P.scaleY, 1], [headMotion, P.y, -55], [blinkEyes, P.scaleY, 1]]);
  rig.blendLayer('sleeping', [[0, idle], [1, asleep]]);
  return rig;
}

// Entry point of a character file: writes the .riv (argv[2]) and, optionally, a preview SVG (--preview=file).
export async function runAnimalCli(kind, defaultOutput, createRigFn) {
  const { mkdirSync, writeFileSync } = await import('node:fs');
  const { dirname, resolve } = await import('node:path');
  const args = process.argv.slice(2);
  const output = args.find(a => !a.startsWith('--')) || defaultOutput;
  const rig = createRigFn();
  const bytes = rig.build(CONTRACT.machine);
  mkdirSync(dirname(resolve(output)), { recursive: true });
  writeFileSync(output, bytes);
  const preview = args.find(a => a.startsWith('--preview='))?.slice('--preview='.length);
  if (preview) {
    mkdirSync(dirname(resolve(preview)), { recursive: true });
    writeFileSync(preview, rig.svg());
  }
  console.log(`Wrote ${output}: ${bytes.length} bytes (${kind})`);
}
