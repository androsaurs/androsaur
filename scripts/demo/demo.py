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


def glow_background(L, chip_text, phone_box, footer=True):
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
    d = ImageDraw.Draw(img)
    ft = L.get("footer")  # строка внизу кадра (адрес проекта)
    if ft and footer: d.text((W / 2, ft["y"]), ft["text"], font=ImageFont.truetype(L["font"], ft["size"]), fill=tuple(ft["color"]), anchor="mm")
    if not c.get("show", True): return img  # шаблон без плашки проекта
    f = ImageFont.truetype(L["font"], c["size"]); cw = d.textlength(chip_text, font=f) + 70
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


def load_regions():
    """Области экрана для приближения (layouts/regions.json): центр и ширина как доли экрана телефона."""
    return json.load(open(os.path.join(HERE, "layouts", "regions.json"), encoding="utf-8"))


def source_size(src):
    """Размер записи (ширина, высота) из вывода ffmpeg."""
    import re
    err = subprocess.run([ffmpeg(), "-hide_banner", "-i", src], capture_output=True, text=True).stderr
    m = re.search(r"Video:.*?, (\d{3,5})x(\d{3,5})", err)
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2460)


def view_state(view, L, screen_rect, src_wh, regions):
    """Вид камеры: (окно на кадре, масштаб, центр в записи, видна ли рамка телефона). `full` — весь экран в рамке телефона,
    область из regions.json — широкое окно без рамки (stage в шаблоне), записи приближается так, чтобы область заняла окно."""
    SW, SH = src_wh
    if view == "full": return screen_rect, screen_rect[2] / SW, (SW / 2, SH / 2), 1.0
    reg = view if isinstance(view, dict) else regions[view]; st = L["stage"]
    return (st["x"], st["y"], st["w"], st["h"]), st["w"] / (SW * reg["w"]), (reg["cx"] * SW, reg["cy"] * SH), 0.0


def extend_edges(tile, pos, canvas, q=4, blur=14):
    """Продолжает вырезанный кусок экрана на весь кадр: крайние пиксели тянутся наружу, углы заливаются цветом угла, всё слегка
    размывается. Фон вокруг окна — это сам кадр приложения, а не подобранный цвет: шва нет по построению.
    Считается в 1/q размера (фон гладкий) и растягивается."""
    from PIL import Image, ImageFilter
    W, H = canvas; w, h = W // q, H // q
    small = tile.reduce(q); sw, sh = small.size
    x, y = round(pos[0] / q), round(pos[1] / q); rx, by = x + sw, y + sh
    low = Image.new("RGB", (w, h))
    if x > 0: low.paste(small.crop((0, 0, 1, sh)).resize((x, sh), Image.NEAREST), (0, y))
    if rx < w: low.paste(small.crop((sw - 1, 0, sw, sh)).resize((w - rx, sh), Image.NEAREST), (rx, y))
    if y > 0: low.paste(small.crop((0, 0, sw, 1)).resize((sw, y), Image.NEAREST), (x, 0))
    if by < h: low.paste(small.crop((0, sh - 1, sw, sh)).resize((sw, h - by), Image.NEAREST), (x, by))
    for (cx0, cy0, cx1, cy1), px in (((0, 0, x, y), (0, 0)), ((rx, 0, w, y), (sw - 1, 0)), ((0, by, x, h), (0, sh - 1)), ((rx, by, w, h), (sw - 1, sh - 1))):
        if cx1 > cx0 and cy1 > cy0: low.paste(small.getpixel(px), (cx0, cy0, cx1, cy1))
    low.paste(small, (x, y))
    return low.filter(ImageFilter.GaussianBlur(blur)).resize((W, H), Image.BILINEAR)


def edge_signature(tile):
    """Отпечаток краёв окна (по 12 пикселей на сторону): по нему видно, что края изменились и фон надо строить заново."""
    from PIL import Image
    w, h = tile.size; B = Image.BOX
    strips = (tile.crop((0, 0, w, 6)).resize((12, 1), B), tile.crop((0, h - 6, w, h)).resize((12, 1), B),
              tile.crop((0, 0, 6, h)).resize((1, 12), B), tile.crop((w - 6, 0, w, h)).resize((1, 12), B))
    return b"".join(x.tobytes() for x in strips)


def camera_at(t, keys, states):
    """Положение камеры в момент t: от прошлого вида к текущему за `dur` с плавным разгоном и торможением.
    Окно и центр меняются линейно, масштаб геометрически (приближение ощущается равномерным); рамка гаснет в начале
    приближения и появляется в конце отдаления."""
    i = max(j for j, k in enumerate(keys) if k[0] <= t)
    t0, _, dur = keys[i]
    p = 1.0 if i == 0 else min(1.0, max(0.0, (t - t0) / dur)); p = p * p * (3 - 2 * p)
    (w0, s0, c0, b0), (w1, s1, c1, b1) = states[max(i - 1, 0)], states[i]
    lerp = lambda a, b: a + (b - a) * p
    bez = b0 + (b1 - b0) * min(1.0, max(0.0, (p * 2) if b1 < b0 else (p - 0.5) * 2))
    mix = 1 - lerp(b0, b1)  # доля фона приложения: растёт вместе с приближением
    return tuple(lerp(a, b) for a, b in zip(w0, w1)), s0 * (s1 / s0) ** p, (lerp(c0[0], c1[0]), lerp(c0[1], c1[1])), bez, mix


def compose_tall_camera(sc, src, lang, out, layout):
    """Вертикальный шортс с «камерой»: ролик начинается с телефона целиком, по `view` у сцены приближается к области экрана
    (персонаж, часы, погода, календарь…), рамка телефона при этом исчезает. Запись берётся в родном размере."""
    from PIL import Image, ImageDraw
    L = load_layout(layout); W, H = L["canvas"]; FPS = 30
    ph = L["phone"]; body, top, bz = ph["body_height"], ph["top"], ph["bezel"]
    frame_png, bw, sw, sh = phone_frame(body, bezel=bz)
    px = (W - bw) // 2
    screen = (px + bz, top + bz, sw, sh)
    SW, SH = source_size(src); regions = load_regions()
    bg = glow_background(L, sc["chip"][lang], (px, top, bw, body), footer=False).convert("RGB")
    footer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if L.get("footer"):  # строка внизу кадра не должна перетекать вместе с фоном: рисуется поверх
        from PIL import ImageFont
        ft = L["footer"]; ImageDraw.Draw(footer).text((W / 2, ft["y"]), ft["text"], font=ImageFont.truetype(L["font"], ft["size"]), fill=tuple(ft["color"]) + (255,), anchor="mm")
    bezel_img = Image.open(frame_png).convert("RGBA")
    keys = [(0.0, "full", 1.0)] + [(c["from"], c["view"], c.get("view_dur", 1.0)) for c in sc["subtitles"] if "view" in c]
    states = [view_state(v, L, screen, (SW, SH), regions) for _, v, _ in keys]
    def with_footer(layer): l = footer.copy(); l.alpha_composite(layer); return l
    layers = [(c["from"], c["to"], with_footer(caption_layer(L, c["title"][lang], c[lang], (c.get("label") or {}).get(lang)))) for c in sc["subtitles"]]
    dec = subprocess.Popen([ffmpeg(), "-loglevel", "error", "-i", src, "-vf", f"fps={FPS}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
    enc = subprocess.Popen([ffmpeg(), "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-",
                            "-c:v", "libx264", "-preset", "medium", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", out], stdin=subprocess.PIPE)
    size, n, masks, cache = SW * SH * 3, 0, {}, None
    while True:
        buf = dec.stdout.read(size)
        if len(buf) < size: break
        t = n / FPS
        (wx, wy, ww, wh), s, (cx, cy), bez, mix = camera_at(t, keys, states)
        ww, wh = round(ww), round(wh)
        cw, ch = ww / s, wh / s
        x0 = min(max(cx - cw / 2, 0), max(SW - cw, 0)); y0 = min(max(cy - ch / 2, 0), max(SH - ch, 0))
        raw = Image.frombytes("RGB", (SW, SH), buf)
        tile = raw.resize((ww, wh), Image.LANCZOS, box=(x0, y0, min(x0 + cw, SW), min(y0 + ch, SH)))
        # наезд переводит фон кадра из зелёного свечения в продолжение самого окна (его края тянутся на весь кадр), за то же время
        # Пока камера стоит и края окна не изменились (фон приложения спокоен), фон не считается заново, берётся прошлый кадр
        if mix > 0.005:
            geom, sig = (round(wx), round(wy), ww, wh), edge_signature(tile)
            if not (cache and cache["geom"] == geom and abs(cache["mix"] - mix) < 1e-3 and max(abs(a - b) for a, b in zip(cache["sig"], sig)) <= 2):
                ext = extend_edges(tile, (round(wx), round(wy)), (W, H))
                cache = {"geom": geom, "sig": sig, "mix": mix, "img": Image.blend(bg, ext, min(1.0, mix)).convert("RGBA")}
            img = cache["img"].copy()
        else: img = bg.convert("RGBA")
        # без рамки края окна размываются (по бокам сильнее, сверху и снизу слабее: там голова персонажа и подпись): персонаж плавает в свечении фона
        feather = round(24 * (1 - bez))
        if feather >= 2:
            key = (ww, wh, feather)
            if key not in masks:
                from PIL import ImageFilter
                m = Image.new("L", (ww, wh), 0)
                ImageDraw.Draw(m).rounded_rectangle((feather, round(feather * 0.5), ww - 1 - feather, wh - 1 - round(feather * 0.2)), radius=feather, fill=255)
                masks[key] = m.filter(ImageFilter.GaussianBlur(feather * 0.45))
            img.paste(tile, (round(wx), round(wy)), masks[key])
        else: img.paste(tile, (round(wx), round(wy)))
        if bez > 0.01:
            b = bezel_img if (ww, wh) == (sw, sh) else bezel_img.resize((ww + 2 * bz, wh + 2 * bz), Image.LANCZOS)
            if bez < 0.99: b = b.copy(); b.putalpha(b.getchannel("A").point(lambda v: int(v * bez)))
            img.alpha_composite(b, (round(wx) - bz, round(wy) - bz))
        img.alpha_composite(next((l for f, e, l in layers if f <= t < e), footer))
        enc.stdin.write(img.convert("RGB").tobytes()); n += 1
    enc.stdin.close(); enc.wait(); dec.wait()
    return enc.returncode == 0 and n > 0


def compose_tall(sc, src, lang, out, layout="tall-b"):
    """Вертикальный шортс по шаблону layouts/<layout>.json.
    Кадры собираются потоком: ffmpeg лишь декодирует запись и кодирует результат, наложение делает Python по одному кадру
    (память не растёт; граф ffmpeg с зацикленными картинками съедал гигабайты)."""
    from PIL import Image
    L = load_layout(layout); W, H = L["canvas"]; FPS = 30
    if "stage" in L and any("view" in c for c in sc["subtitles"]): return compose_tall_camera(sc, src, lang, out, layout)
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


def compose(path, langs=("en", "ru"), kinds=("wide", "tall"), layout=None):
    sc = load_scenario(path)
    src = os.path.join(RAW, name_of(path) + ".mp4")
    if not os.path.exists(src): sys.exit(f"нет {src}: сначала record")
    os.makedirs(OUT, exist_ok=True)
    for lang in langs:
        for kind in kinds:
            # --layout пробует другой шаблон, не трогая ролик со своим: файл получает имя шаблона
            out = os.path.join(OUT, f"{name_of(path)}-{layout}-{lang}.mp4" if layout and kind == "tall" else f"{name_of(path)}-{kind}-{lang}.mp4")
            ok = compose_wide(sc, src, lang, out, name_of(path)) if kind == "wide" else compose_tall(sc, src, lang, out, layout or sc.get("layout", "tall-b"))
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
    p.add_argument("--device"); p.add_argument("--layout", help="шаблон из layouts/ вместо указанного в сценарии (для пробы)")
    p.add_argument("--lang", choices=["en", "ru"]); p.add_argument("--kind", choices=["wide", "tall"])
    a = p.parse_args()
    if a.cmd in ("record", "all"): record(a.target, pick_device(a.device))
    if a.cmd in ("compose", "all"): compose(a.target, (a.lang,) if a.lang else ("en", "ru"), (a.kind,) if a.kind else ("wide", "tall"), a.layout)
    if a.cmd == "frames": frames(a.target, a.times)
