// Cat: lilac, pointed ears with pink insides, whiskers.
// Run: node scripts/build-cat-rig.mjs [output.riv] [--preview=output.svg]
import { fileURLToPath } from 'node:url';
import { CONTRACT } from './rive/writer.mjs';
import { createAnimalRig, runAnimalCli } from './rive/animal.mjs';

export const spec = {
  palette: {
    green: 0xffbd9aff,
    dark: 0xff38254f,
    pale: 0xfff1dfff,
    accent: 0xff9266d4,
  },
  tail: [65, 95],
  ears: ({ ellipse, triangle, head, palette }) => {
    triangle('Left ear', head, -106, -100, 78, 110, palette.accent);
    triangle('Right ear', head, 106, -100, 78, 110, palette.accent);
    triangle('Left inner ear', head, -106, -96, 44, 70, 0xfff4a9cb);
    triangle('Right inner ear', head, 106, -96, 44, 70, 0xfff4a9cb);
  },
  nose: ({ ellipse, head, palette }) => {
    ellipse('Nose', head, 0, 30, 24, 16, palette.dark);
    for (const side of [-1, 1]) for (const y of [36, 49, 62])
      ellipse(`Whisker ${side} ${y}`, head, side * 105, y, 57, 3, palette.dark);
  },
};

export const createCatRig = () => createAnimalRig(spec);
export const buildCat = () => createCatRig().build(CONTRACT.machine);

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await runAnimalCli('cat', fileURLToPath(new URL('../androidApp/src/main/res/raw/androsaur_cat.riv', import.meta.url)), createCatRig);
}
