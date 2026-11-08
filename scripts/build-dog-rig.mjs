// Dog: orange, long floppy ears, eye patch.
// Run: node scripts/build-dog-rig.mjs [output.riv] [--preview=output.svg]
import { fileURLToPath } from 'node:url';
import { CONTRACT } from './rive/writer.mjs';
import { createAnimalRig, runAnimalCli } from './rive/animal.mjs';

export const spec = {
  palette: {
    green: 0xfff5ae64,
    dark: 0xff472e22,
    pale: 0xffffe4bd,
    accent: 0xffaf673e,
  },
  tail: [65, 48],
  ears: ({ ellipse, head, palette }) => {
    ellipse('Left ear', head, -106, 0, 60, 190, palette.accent);
    ellipse('Right ear', head, 106, 0, 60, 190, palette.accent);
  },
  afterHead: ({ ellipse, head, palette }) => ellipse('Eye patch', head, -56, -29, 93, 110, palette.accent),
  nose: ({ ellipse, head, palette }) => ellipse('Nose', head, 0, 30, 45, 30, palette.dark),
};

export const createDogRig = () => createAnimalRig(spec);
export const buildDog = () => createDogRig().build(CONTRACT.machine);

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  await runAnimalCli('dog', fileURLToPath(new URL('../androidApp/src/main/res/raw/androsaur_dog.riv', import.meta.url)), createDogRig);
}
