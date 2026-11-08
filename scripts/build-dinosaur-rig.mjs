// Dinosaur: the default Androsaur character (green, long tail, round ears).
// Run: node scripts/build-dinosaur-rig.mjs [output.riv] [--preview=output.svg]
import { fileURLToPath } from 'node:url';
import { CONTRACT } from './rive/writer.mjs';
import { createAnimalRig, runAnimalCli } from './rive/animal.mjs';

export const spec = {
  palette: {
    green: 0xff63e6a4,
    dark: 0xff112a24,
    pale: 0xffc9f6d8,
    accent: 0xff39ae7c,
  },
  tail: [125, 48],
  ears: ({ ellipse, head, palette }) => {
    ellipse('Left ear', head, -106, -53, 60, 78, palette.accent);
    ellipse('Right ear', head, 106, -53, 60, 78, palette.accent);
  },
  nose: ({ ellipse, head, palette }) => {
    ellipse('Left nostril', head, -26, 17, 9, 6, palette.accent);
    ellipse('Right nostril', head, 26, 17, 9, 6, palette.accent);
  },
};

export const createDinosaurRig = () => createAnimalRig(spec);
export const buildDinosaur = () => createDinosaurRig().build(CONTRACT.machine);

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await runAnimalCli('dinosaur', fileURLToPath(new URL('../androidApp/src/main/res/raw/androsaur_character.riv', import.meta.url)), createDinosaurRig);
}
