#!/usr/bin/env python3
"""Запись демо-роликов с телефона по сценарию и сборка версий EN/RU с субтитрами.

  python3 demo.py record scenarios/launch.json            # снять экран, пока сценарий идёт (raw/<имя>.mp4)
  python3 demo.py compose scenarios/launch.json           # собрать out/<имя>-{wide,tall}-{en,ru}.mp4 из raw
  python3 demo.py all scenarios/launch.json               # оба шага
  python3 demo.py frames out/<файл>.mp4 1 10 20           # кадры PNG для проверки глазами

Сцена подаётся в приложение приёмником DEBUG_SCENE (Brain.scene), экран пишет `adb shell screenrecord`.
Телефон: --device или переменная DEMO_DEVICE (по умолчанию первый из `adb devices`).
Субтитры: ASS, шрифт DejaVu Sans (есть кириллица). ffmpeg берётся из npm (`npm install` в этой папке).
"""
import argparse, json, os, shutil, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
RAW, OUT = os.path.join(HERE, "raw"), os.path.join(HERE, "out")


def ffmpeg():
    exe = os.path.join(HERE, "node_modules", "ffmpeg-static", "ffmpeg")
    if os.path.exists(exe): return exe
    return shutil.which("ffmpeg") or sys.exit("нет ffmpeg: выполните `npm install` в scripts/demo")


def adb(device, *args, **kw):
    return subprocess.run(["adb", "-s", device, *args], capture_output=True, text=True, **kw)


def pick_device(arg):
    if arg or os.environ.get("DEMO_DEVICE"): return arg or os.environ["DEMO_DEVICE"]
    lines = [l.split()[0] for l in subprocess.run(["adb", "devices"], capture_output=True, text=True).stdout.splitlines()[1:] if l.endswith("device")]
    return lines[0] if lines else sys.exit("нет телефона по adb")


def scene(device, step):
    """Один шаг на телефон. `face` — позa персонажа приёмником DEBUG_FACE (expression, character, seconds; seconds=0 снимает позу),
    остальные шаги — приёмник DEBUG_SCENE. am разбирает аргументы через shell телефона: кириллицу и пробелы берём в кавычки."""
    q = lambda v: "'" + str(v).replace("'", "") + "'"
    if step["step"] == "face":
        cmd = ["am", "broadcast", "-a", "dev.hram.androsaur.DEBUG_FACE", "--ei", "seconds", str(int(step.get("seconds", 60)))]
        for key in ("expression", "character"):
            if key in step: cmd += ["--es", key, q(step[key])]
    else:
        cmd = ["am", "broadcast", "-a", "dev.hram.androsaur.DEBUG_SCENE", "--es", "step", step["step"]]
        for key in ("name", "id", "text", "intent", "data"):
            if key in step: cmd += ["--es", key, q(step[key])]
    adb(device, "shell", " ".join(cmd))


def load_scenario(path):
    """Читает сценарий. Сцена (`scenes`) — один элемент: время, заголовок, текст и действия на телефоне (`do`, `dt` от начала сцены,
    может быть отрицательным: действие чуть раньше подписи). Для записи и сборки сцены раскладываются на `steps` и `subtitles`;
    старый формат (отдельные `steps` и `subtitles`) тоже читается."""
    sc = json.load(open(path, encoding="utf-8"))
    if "scenes" in sc:
        steps, subs = [], []
        for scene in sc["scenes"]:
            for a in scene.get("do", []):
                steps.append({"t": round(scene["from"] + a.get("dt", 0), 3), **{k: v for k, v in a.items() if k != "dt"}})
            subs.append({k: v for k, v in scene.items() if k != "do"})
        sc["steps"], sc["subtitles"] = sorted(steps, key=lambda x: x["t"]), subs
    return sc


def name_of(path): return os.path.splitext(os.path.basename(path))[0]


def record(path, device):
    sc = load_scenario(path)
    os.makedirs(RAW, exist_ok=True)
    remote = f"/sdcard/Download/demo-{name_of(path)}.mp4"
    adb(device, "shell", "am", "start", "-n", "dev.hram.androsaur/.MainActivity")
    adb(device, "shell", "cmd", "statusbar", "collapse")
    time.sleep(3)  # приложение открыто на лице
    for step in sc.get("setup", []): scene(device, step)  # исходное состояние до записи: первый кадр уже «в сцене»
    time.sleep(float(sc.get("settle", 3)))
    limit = int(sc["duration"]) + 2
    rec = subprocess.Popen(["adb", "-s", device, "shell", "screenrecord", "--bit-rate", "10000000", "--time-limit", str(limit), remote])
    t0 = time.time() + 0.6  # screenrecord стартует не мгновенно
    for step in sc["steps"]:
        delay = t0 + step["t"] - time.time()
        if delay > 0: time.sleep(delay)
        scene(device, step)
    rec.wait()
    time.sleep(1)
    scene(device, {"step": "face", "seconds": 0})  # снять позу DEBUG_FACE, если сцена её ставила
    scene(device, {"step": "reset"})
    dest = os.path.join(RAW, name_of(path) + ".mp4")
    adb(device, "pull", remote, dest); adb(device, "shell", "rm", remote)
    print("записано:", dest)


def ts(sec):
    return f"{int(sec // 3600)}:{int(sec % 3600 // 60):02d}:{sec % 60:05.2f}"


def ass(sc, lang, wide):
    """Субтитры: широкий кадр — справа от телефона, вертикальный — внизу."""
    w, h = (1920, 1080) if wide else (1080, 1920)
    # tall: текст поверх телефона на уровне живота персонажа (≈77% высоты кадра), на полупрозрачной плашке
    style = ("Style: S,DejaVu Sans,54,&H00FFFFFF,&H00FFFFFF,&H00202020,&H80000000,-1,0,0,0,100,100,0,0,1,3,1,%d,%d,%d,%d,1"
             % ((4, 1140, 60, 0) if wide else (5, 90, 90, 0)) if wide else
             "Style: S,DejaVu Sans,52,&H00FFFFFF,&H00FFFFFF,&H99000000,&H99000000,-1,0,0,0,100,100,0,0,3,16,0,5,90,90,0,1")
    head = f"[Script Info]\nScriptType: v4.00+\nPlayResX: {w}\nPlayResY: {h}\nWrapStyle: 0\n\n[V4+ Styles]\nFormat: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\n{style}\n\n[Events]\nFormat: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n"
    pos = "" if wide else "{\\pos(540,1470)}"
    return head + "".join(f"Dialogue: 0,{ts(s['from'])},{ts(s['to'])},S,,0,0,0,,{pos}{s[lang]}\n" for s in sc["subtitles"])


TILT_DEG = -3.0  # лёгкий наклон телефона, как на образце


def phone_frame(body_h, bezel=34):
    """Толстая рамка телефона (PNG с прозрачным экраном) под запись 1080x2460. Возвращает (путь, ширина корпуса, размер экрана)."""
    from PIL import Image, ImageDraw
    sh = body_h - 2 * bezel
    sw = round(sh * 1080 / 2460)
    w, h, r = sw + 2 * bezel, body_h, round(sw * 0.10)
    k = 3  # сглаживание: рисуем втрое крупнее
    img = Image.new("RGBA", (w * k, h * k), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    d.rounded_rectangle((0, 0, w * k - 1, h * k - 1), radius=(r + bezel) * k, fill=(92, 98, 100, 255))                        # светлая кромка
    d.rounded_rectangle((3 * k, 3 * k, w * k - 3 * k, h * k - 3 * k), radius=(r + bezel - 3) * k, fill=(52, 55, 56, 255))      # корпус
    hole = Image.new("L", img.size, 255); ImageDraw.Draw(hole).rounded_rectangle((bezel * k, bezel * k, (w - bezel) * k, (h - bezel) * k), radius=r * k, fill=0)
    img.putalpha(Image.composite(img.getchannel("A"), Image.new("L", img.size, 0), hole))                                       # экран прозрачный
    img = img.resize((w, h), Image.LANCZOS)
    out = os.path.join(OUT, f".frame-{body_h}.png"); img.save(out)
    return out, w, sw, sh


def background(path, canvas, circle, shadow):
    """Фон: тёмно-зелёный, большой светлый круг за телефоном и мягкая тень под ним."""
    from PIL import Image, ImageDraw, ImageFilter
    W, H = canvas
    img = Image.new("RGB", canvas, (6, 52, 43)); d = ImageDraw.Draw(img)
    cx, cy, cr = circle
    d.ellipse((cx - cr, cy - cr, cx + cr, cy + cr), fill=(11, 84, 66))
    sx, sy, srx, sry = shadow
    layer = Image.new("L", canvas, 0); ImageDraw.Draw(layer).ellipse((sx - srx, sy - sry, sx + srx, sy + sry), fill=150)
    layer = layer.filter(ImageFilter.GaussianBlur(14))
    img.paste(Image.new("RGB", canvas, (3, 28, 22)), (0, 0), layer)
    img.save(path)


def load_layout(name):
    """Шаблон компоновки из layouts/<имя>.json: все размеры вертикального кадра в одном месте."""
    return json.load(open(os.path.join(HERE, "layouts", name + ".json"), encoding="utf-8"))


def glow_background(L, chip_text, phone_box):
    """Тёмный фон с зелёным свечением, плашка проекта вверху и тень под телефоном (вертикальный кадр)."""
    from PIL import Image, ImageDraw, ImageFilter, ImageFont
    W, H = L["canvas"]; bg, c = L["background"], L["chip"]
    img = Image.new("RGB", (W, H), tuple(bg["base"]))
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).ellipse((W // 2 - bg["glow_rx"], bg["glow_center_y"] - bg["glow_ry"], W // 2 + bg["glow_rx"], bg["glow_center_y"] + bg["glow_ry"]), fill=170)
    img.paste(Image.new("RGB", (W, H), tuple(bg["glow"])), (0, 0), m.filter(ImageFilter.GaussianBlur(bg["glow_blur"])))
    x, y, w, h = phone_box; above, below = L["phone"]["shadow_above"], L["phone"]["shadow_below"]
    sh = Image.new("L", (W, H), 0); ImageDraw.Draw(sh).ellipse((x - 10, y + h - above, x + w + 10, y + h + below), fill=200)
    img.paste(Image.new("RGB", (W, H), (0, 0, 0)), (0, 0), sh.filter(ImageFilter.GaussianBlur(18)))
    d = ImageDraw.Draw(img); f = ImageFont.truetype(L["font"], c["size"]); cw = d.textlength(chip_text, font=f) + 70
    top = c["y"]
    d.rounded_rectangle((W / 2 - cw / 2, top, W / 2 + cw / 2, top + c["height"]), radius=c["height"] // 2, fill=tuple(c["fill"]), outline=tuple(c["outline"]), width=2)
    d.ellipse((W / 2 - cw / 2 + 24, top + 24, W / 2 - cw / 2 + 36, top + 36), fill=tuple(c["dot"]))
    d.text((W / 2 - cw / 2 + 50, top + 14), chip_text, font=f, fill=tuple(c["text"]))
    return img


def caption_layer(L, title, text, label=None):
    """Прозрачный слой с заголовком сцены и карточкой с подписью (перенос по словам)."""
    from PIL import Image, ImageDraw, ImageFont
    W, H = L["canvas"]; t, k = L["title"], L["card"]
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    f = ImageFont.truetype(L["font"], k["size"])
    if t.get("show", True): d.text((W / 2, t["y"]), title, font=ImageFont.truetype(L["font"], t["size"]), fill=tuple(t["color"]), anchor="mt")
    merge = k.get("merge_title", False)  # заголовок сцены уходит в карточку цветной строкой, отправитель — префиксом текста
    head = title if merge else label
    if merge and label: text = f"{label}: {text}"
    lines, cur = [], ""
    for word in text.split():
        trial = (cur + " " + word).strip()
        if d.textlength(trial, font=f) > k["max_text_width"] and cur: lines.append(cur); cur = word
        else: cur = trial
    lines.append(cur)
    cw = max(d.textlength(l, font=f) for l in lines) + k["padding_x"]
    lab = 40 if head else 0
    step = k["size"] + 14
    ch = len(lines) * step + 42 + lab
    y = k["y"]
    d.rounded_rectangle((W / 2 - cw / 2, y, W / 2 + cw / 2, y + ch), radius=k["radius"], fill=tuple(k["fill"]), outline=tuple(k["outline"]), width=2)
    if head: d.text((W / 2, y + 18), head, font=ImageFont.truetype(L["font"], k["label_size"]), fill=tuple(k["label_color"]), anchor="mt")
    for i, l in enumerate(lines): d.text((W / 2, y + 22 + lab + i * step), l, font=f, fill=(255, 255, 255), anchor="mt")
    return img


def compose_tall(sc, src, lang, out, layout="tall-b"):
    """Вертикальный шортс по шаблону layouts/<layout>.json.
    Кадры собираются потоком: ffmpeg лишь декодирует запись и кодирует результат, наложение делает Python по одному кадру
    (память не растёт; граф ffmpeg с зацикленными картинками съедал гигабайты)."""
    from PIL import Image
    L = load_layout(layout); W, H = L["canvas"]; FPS = 30
    ph = L["phone"]; body, top, bz = ph["body_height"], ph["top"], ph["bezel"]
    frame_png, bw, sw, sh = phone_frame(body, bezel=bz)
    px = (W - bw) // 2
    bg = glow_background(L, sc["chip"][lang], (px, top, bw, body)).convert("RGBA")
    frame = Image.new("RGBA", (W, H), (0, 0, 0, 0)); frame.alpha_composite(Image.open(frame_png).convert("RGBA"), (px, top))
    overs = []
    for c in sc["subtitles"]:
        layer = caption_layer(L, c["title"][lang], c[lang], (c.get("label") or {}).get(lang))
        over = frame.copy(); over.alpha_composite(layer); overs.append((c["from"], c["to"], over))
    dec = subprocess.Popen([ffmpeg(), "-loglevel", "error", "-i", src, "-vf", f"scale={sw}:{sh},fps={FPS}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    enc = subprocess.Popen([ffmpeg(), "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                            "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", out], stdin=subprocess.PIPE)
    size, n = sw * sh * 3, 0
    while True:
        buf = dec.stdout.read(size)
        if len(buf) < size: break
        t = n / FPS
        img = bg.copy(); img.paste(Image.frombytes("RGB", (sw, sh), buf), (px + bz, top + bz))
        img.alpha_composite(next((o for f, e, o in overs if f <= t < e), frame))
        enc.stdin.write(img.convert("RGB").tobytes()); n += 1
    enc.stdin.close(); enc.wait(); dec.wait()
    return enc.returncode == 0 and n > 0


def compose_wide(sc, src, lang, out, name):
    """Горизонтальный кадр: телефон слева в рамке на фоне со светлым кругом, текст справа (ASS)."""
    sub = os.path.join(OUT, f".{name}-wide-{lang}.ass")
    open(sub, "w", encoding="utf-8").write(ass(sc, lang, True))
    body = 960
    frame, bw, sw, sh = phone_frame(body, bezel=22)
    px, py = 570, 40
    cx, cy = px + bw // 2, py + body // 2
    bgp = os.path.join(OUT, ".bg-wide.png")
    background(bgp, (1920, 1080), (cx, cy + 30, 540), (cx, py + body + 20, bw // 2 + 10, 28))
    graph = (f"[1:v]format=rgba[bg];[0:v]scale={sw}:{sh}[v];[bg][v]overlay=x={px + 22}:y={py + 22}:shortest=1[a];"
             f"[a][2:v]overlay=x={px}:y={py}:shortest=1,ass={sub}")
    r = subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-i", src, "-loop", "1", "-framerate", "30", "-i", bgp,
                        "-loop", "1", "-framerate", "30", "-i", frame, "-filter_complex", graph, "-r", "30",
                        "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", out])
    os.remove(sub)
    return r.returncode == 0


def compose(path, langs=("en", "ru"), kinds=("wide", "tall")):
    sc = load_scenario(path)
    src = os.path.join(RAW, name_of(path) + ".mp4")
    if not os.path.exists(src): sys.exit(f"нет {src}: сначала record")
    os.makedirs(OUT, exist_ok=True)
    for lang in langs:
        for kind in kinds:
            out = os.path.join(OUT, f"{name_of(path)}-{kind}-{lang}.mp4")
            ok = compose_wide(sc, src, lang, out, name_of(path)) if kind == "wide" else compose_tall(sc, src, lang, out, sc.get("layout", "tall-b"))
            print(("собрано: " if ok else "ОШИБКА: ") + out)
    for f in os.listdir(OUT):
        if f.startswith((".frame-", ".bg-")): os.remove(os.path.join(OUT, f))


def frames(path, times):
    os.makedirs(os.path.join(OUT, "frames"), exist_ok=True)
    for t in times:
        out = os.path.join(OUT, "frames", f"{name_of(path)}-{float(t):05.1f}.png")
        subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-ss", str(t), "-i", path, "-frames:v", "1", out]); print(out)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("cmd", choices=["record", "compose", "all", "frames"])
    p.add_argument("target"); p.add_argument("times", nargs="*")
    p.add_argument("--device")
    p.add_argument("--lang", choices=["en", "ru"]); p.add_argument("--kind", choices=["wide", "tall"])
    a = p.parse_args()
    if a.cmd in ("record", "all"): record(a.target, pick_device(a.device))
    if a.cmd in ("compose", "all"): compose(a.target, (a.lang,) if a.lang else ("en", "ru"), (a.kind,) if a.kind else ("wide", "tall"))
    if a.cmd == "frames": frames(a.target, a.times)
