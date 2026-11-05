// Minimal writer for Rive v7 runtime files, shared by the character scripts.
// Uses public rive-runtime core property IDs: https://github.com/rive-app/rive-runtime (MIT).
//
// Author components back to front (background first). Rive draws earlier components on top,
// so build() reverses them and remaps every reference, including animation targets.
// Every add* call returns the id that animations and children refer to.

const T = { string: 's', uint: 'u', float: 'f', color: 'c' };
// Property key -> wire type. Only properties known to v7 runtimes: the file has no field table.
const PROPERTY_TYPES = {
  4: T.string, 5: T.uint, 7: T.float, 8: T.float, // name, parentId, artboard width/height
  13: T.float, 14: T.float, 15: T.float, 16: T.float, 17: T.float, 18: T.float, // x, y, rotation, scale, opacity
  20: T.float, 21: T.float, 31: T.float, // path width/height, rectangle corner radius
  37: T.color, 47: T.float, 48: T.uint, // solid color, stroke thickness, stroke cap
  51: T.uint, 53: T.uint, 55: T.string, 56: T.uint, 57: T.uint, 59: T.uint, // keyed object/property, animation
  67: T.uint, 68: T.uint, 70: T.float, // keyframe frame, interpolation, value
  138: T.string, 140: T.float, 149: T.uint, 151: T.uint, 152: T.uint, // state machine names, input value, states
  155: T.uint, 156: T.uint, 157: T.float, 158: T.uint, // transition condition, duration
  165: T.uint, 166: T.float, 167: T.uint, // blend animations
};

export const P = { x: 13, y: 14, rotation: 15, scaleX: 16, scaleY: 17, opacity: 18, width: 20, height: 21 };

const TYPE = {
  backboard: 23, artboard: 1, node: 2, shape: 3, ellipse: 4, rectangle: 7, fill: 20, stroke: 24, solidColor: 18,
  linearAnimation: 31, keyedObject: 25, keyedProperty: 26, keyFrameDouble: 30,
  stateMachine: 53, numberInput: 56, layer: 57, anyState: 62, exitState: 64, entryState: 63,
  transition: 65, numberCondition: 70, animationState: 61, blendState1D: 76, blendAnimation1D: 75,
};
const TRANSITION_EARLY_EXIT = 1 << 5;
const CONDITION_EQUAL = 0;

class Writer {
  chunks = [Buffer.from('RIVE')];
  uint(value) {
    if (!Number.isInteger(value) || value < 0) throw new Error(`Not a uint: ${value}`);
    const bytes = [];
    do { const low = value & 127; value = Math.floor(value / 128); bytes.push(low | (value ? 128 : 0)); } while (value);
    this.chunks.push(Buffer.from(bytes));
  }
  object(type, props) {
    this.uint(type);
    for (const [key, value] of Object.entries(props)) {
      if (value === undefined) continue;
      const kind = PROPERTY_TYPES[key];
      if (!kind) throw new Error(`Property ${key} of object type ${type} is not in PROPERTY_TYPES`);
      this.uint(Number(key));
      if (kind === T.uint) this.uint(value);
      else if (kind === T.string) { const b = Buffer.from(value); this.uint(b.length); this.chunks.push(b); }
      else { const b = Buffer.alloc(4); kind === T.float ? b.writeFloatLE(value) : b.writeUInt32LE(value >>> 0); this.chunks.push(b); }
    }
    this.uint(0);
  }
  bytes() { return Buffer.concat(this.chunks); }
}

export function createRig({ artboard, width = 480, height = 480 }) {
  const components = []; // authoring order, id = index + 1; the artboard is 0
  const animations = [];
  const inputs = [];
  const layers = [];

  const add = (type, props) => { components.push({ type, props }); return components.length; };
  const transform = ({ x, y, rotation, scaleX, scaleY, opacity } = {}) =>
    ({ [P.x]: x, [P.y]: y, [P.rotation]: rotation, [P.scaleX]: scaleX, [P.scaleY]: scaleY, [P.opacity]: opacity });

  function node(name, parent, x = 0, y = 0, extra = {}) {
    return add(TYPE.node, { 4: name, 5: parent, ...transform({ x, y, ...extra }) });
  }
  // A filled or stroked ellipse/rectangle. Returns { shape, path } ids: animate the shape's
  // transform or opacity, the path's width/height.
  function shape(kind, name, parent, x, y, w, h, color, { rotation, scaleX, scaleY, opacity, radius, stroke, cap = 1 } = {}) {
    const id = add(TYPE.shape, { 4: name, 5: parent, ...transform({ x, y, rotation, scaleX, scaleY, opacity }) });
    const path = add(kind, { 5: id, [P.width]: w, [P.height]: h, 31: kind === TYPE.rectangle ? radius : undefined });
    const paint = stroke ? add(TYPE.stroke, { 5: id, 47: stroke, 48: cap }) : add(TYPE.fill, { 5: id });
    add(TYPE.solidColor, { 5: paint, 37: color });
    return { shape: id, path };
  }
  const ellipse = (...args) => shape(TYPE.ellipse, ...args);
  const rect = (...args) => shape(TYPE.rectangle, ...args);

  // tracks: [[targetId, propertyKey, [[frame, value], ...]], ...] at 60 fps.
  function animation(name, tracks, duration = 60, loop = false) {
    animations.push({ name, tracks, duration, loop });
    return animations.length - 1;
  }
  // A one-frame pose: [[targetId, propertyKey, value], ...]
  const pose = (name, values) => animation(name, values.map(([id, key, value]) => [id, key, [[0, value]]]));

  function input(name, value = 0) {
    if (inputs.some(i => i.name === name)) throw new Error(`Duplicate input ${name}`);
    inputs.push({ name, value });
  }
  // Continuous input: blends between poses keyed at input values.
  function blendLayer(inputName, stops) { layers.push({ kind: 'blend', input: inputName, stops }); }
  // Category input: one state per integer code, crossfading directly between any two codes.
  // The app must set exact codes; animating the number is not needed and would be ignored.
  function categoryLayer(inputName, poses, mixMs = 300) { layers.push({ kind: 'category', input: inputName, poses, mixMs }); }

  function build(machineName = 'Character') {
    const w = new Writer();
    const n = components.length;
    const remap = id => (id === 0 ? 0 : n - id + 1);
    [7, 0, 0, 0].forEach(v => w.uint(v)); // major, minor, file id, empty field table
    w.object(TYPE.backboard, {});
    w.object(TYPE.artboard, { 4: artboard, 7: width, 8: height });
    for (let i = n - 1; i >= 0; i--) {
      const { type, props } = components[i];
      w.object(type, { ...props, 5: remap(props[5]) });
    }
    for (const { name, tracks, duration, loop } of animations) {
      w.object(TYPE.linearAnimation, { 55: name, 56: 60, 57: duration, 59: loop ? 1 : 0 });
      const byTarget = new Map();
      for (const [id, key, frames] of tracks) {
        if (!byTarget.has(id)) byTarget.set(id, []);
        byTarget.get(id).push([key, frames]);
      }
      for (const [id, properties] of byTarget) {
        if (!(id >= 1 && id <= n)) throw new Error(`Animation ${name} targets unknown component ${id}`);
        w.object(TYPE.keyedObject, { 51: remap(id) });
        for (const [key, frames] of properties) {
          w.object(TYPE.keyedProperty, { 53: key });
          for (const [frame, value] of frames) w.object(TYPE.keyFrameDouble, { 67: frame, 68: 1, 70: value });
        }
      }
    }
    w.object(TYPE.stateMachine, { 55: machineName });
    for (const { name, value } of inputs) w.object(TYPE.numberInput, { 138: name, 140: value });
    const inputIndex = name => {
      const index = inputs.findIndex(i => i.name === name);
      if (index < 0) throw new Error(`Layer uses undeclared input ${name}`);
      return index;
    };
    for (const layer of layers) {
      w.object(TYPE.layer, { 138: layer.input });
      w.object(TYPE.anyState, {}); // state 0
      if (layer.kind === 'category') {
        layer.poses.forEach((_, code) => {
          w.object(TYPE.transition, { 151: 3 + code, 152: TRANSITION_EARLY_EXIT, 158: layer.mixMs });
          w.object(TYPE.numberCondition, { 155: inputIndex(layer.input), 156: CONDITION_EQUAL, 157: code });
        });
      }
      w.object(TYPE.exitState, {}); // state 1
      w.object(TYPE.entryState, {}); // state 2
      w.object(TYPE.transition, { 151: 3 });
      if (layer.kind === 'category') {
        for (const animationId of layer.poses) w.object(TYPE.animationState, { 149: animationId }); // states 3..
      } else {
        w.object(TYPE.blendState1D, { 167: inputIndex(layer.input) }); // state 3
        for (const [value, animationId] of layer.stops) w.object(TYPE.blendAnimation1D, { 165: animationId, 166: value });
      }
    }
    return w.bytes();
  }

  // Browser preview uses the same authored geometry; runtime animation remains in Rive.
  function svg() {
    const escape = value => String(value).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;').replaceAll('>', '&gt;');
    const color = value => '#' + (value & 0xffffff).toString(16).padStart(6, '0');
    function children(parent) {
      return components.map((c, i) => ({...c, id: i + 1}))
        .filter(c => c.props[5] === parent && (c.type === TYPE.node || c.type === TYPE.shape))
        .map(draw).join('');
    }
    function draw({type, props, id}) {
      const transform = `translate(${props[P.x] ?? 0} ${props[P.y] ?? 0}) rotate(${(props[P.rotation] ?? 0) * 180 / Math.PI}) scale(${props[P.scaleX] ?? 1} ${props[P.scaleY] ?? 1})`;
      let content;
      if (type === TYPE.node) content = children(id);
      else {
        const path = components.find(c => c.props[5] === id && [TYPE.ellipse, TYPE.rectangle].includes(c.type));
        const paint = components.find(c => c.props[5] === id && [TYPE.fill, TYPE.stroke].includes(c.type));
        if (!path || !paint) throw new Error(`Missing SVG geometry: ${props[4]}`);
        const paintId = components.indexOf(paint) + 1;
        const tint = components.find(c => c.type === TYPE.solidColor && c.props[5] === paintId).props[37];
        const w = path.props[P.width], h = path.props[P.height];
        const attrs = paint.type === TYPE.stroke ? `fill="none" stroke="${color(tint)}" stroke-width="${paint.props[47]}"` : `fill="${color(tint)}"`;
        content = path.type === TYPE.ellipse
          ? `<ellipse rx="${w / 2}" ry="${h / 2}" ${attrs}/>`
          : `<rect x="${-w / 2}" y="${-h / 2}" width="${w}" height="${h}" rx="${path.props[31] ?? 0}" ${attrs}/>`;
      }
      const cls = ({'Breathing body': 'figure', 'Blink eyes': 'eye', 'Mouth': 'mouth'})[props[4]] ?? '';
      return `<g transform="${transform}" opacity="${props[P.opacity] ?? 1}"><g class="${cls}">${content}</g></g>`;
    }
    const style = `<style>@keyframes breathe{50%{transform:translateY(-4px)}}@keyframes blink{0%,68%,76%,100%{transform:scaleY(1)}72%{transform:scaleY(.08)}}@keyframes talk{50%{transform:scaleY(1.7)}}.figure{animation:breathe 4s ease-in-out infinite}.eye{animation:blink 4s infinite}.mouth{animation:talk .7s ease-in-out infinite;animation-play-state:paused}svg:hover .mouth{animation-play-state:running}@media(prefers-reduced-motion:reduce){.figure,.eye,.mouth{animation:none}}</style>`;
    return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${height}" role="img" aria-label="${escape(artboard)}">${style}${children(0)}</svg>`;
  }

  return { node, ellipse, rect, animation, pose, input, blendLayer, categoryLayer, build, svg, P };
}

// The contract RiveCharacter.kt relies on. Codes are stable: persist and send them as is.
export const CONTRACT = {
  machine: 'Character',
  inputs: ['lookX', 'lookY', 'mouthOpen', 'expression', 'interaction', 'speaking', 'sleeping'],
  expression: ['NEUTRAL', 'ATTENTIVE', 'LISTENING', 'SLEEPY', 'SAD'],
  interaction: ['IDLE', 'COMMAND', 'RECORDING', 'NOTE'],
};
