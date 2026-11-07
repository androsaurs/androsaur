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
    cmd = ["shell", "am", "broadcast", "-a", "dev.hram.androsaur.DEBUG_SCENE", "--es", "step", step["step"]]
    for key in ("name", "id", "text", "intent", "data"):
        if key in step: cmd += ["--es", key, str(step[key])]
    # am разбирает аргументы через shell телефона: кириллицу и пробелы берём в кавычки
    adb(device, "shell", " ".join(["am", "broadcast", "-a", "dev.hram.androsaur.DEBUG_SCENE", "--es", "step", step["step"]] +
        sum([["--es", k, "'" + str(step[k]).replace("'", "") + "'"] for k in ("name", "id", "text", "intent", "data") if k in step], [])))


def name_of(path): return os.path.splitext(os.path.basename(path))[0]


def record(path, device):
    sc = json.load(open(path, encoding="utf-8"))
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
    scene(device, {"step": "reset"})
    dest = os.path.join(RAW, name_of(path) + ".mp4")
    adb(device, "pull", remote, dest); adb(device, "shell", "rm", remote)
    print("записано:", dest)


def ts(sec):
    return f"{int(sec // 3600)}:{int(sec % 3600 // 60):02d}:{sec % 60:05.2f}"


def ass(sc, lang, wide):
    """Субтитры: широкий кадр — справа от телефона, вертикальный — внизу."""
    w, h = (1920, 1080) if wide else (1080, 1920)
    style = ("Style: S,DejaVu Sans,54,&H00FFFFFF,&H00FFFFFF,&H00202020,&H80000000,-1,0,0,0,100,100,0,0,1,3,1,%d,%d,%d,%d,1"
             % ((4, 1140, 60, 0) if wide else (2, 60, 60, 90)))
    head = f"[Script Info]\nScriptType: v4.00+\nPlayResX: {w}\nPlayResY: {h}\nWrapStyle: 0\n\n[V4+ Styles]\nFormat: Name,Fontname,Fontsize,PrimaryColour,SecondaryColour,OutlineColour,BackColour,Bold,Italic,Underline,StrikeOut,ScaleX,ScaleY,Spacing,Angle,BorderStyle,Outline,Shadow,Alignment,MarginL,MarginR,MarginV,Encoding\n{style}\n\n[Events]\nFormat: Layer,Start,End,Style,Name,MarginL,MarginR,MarginV,Effect,Text\n"
    if wide: head = head.replace("1,0,0,0,100,100,0,0,1,3,1,4,1140,60,0,1", "1,0,0,0,100,100,0,0,1,3,1,4,1140,60,0,1")
    return head + "".join(f"Dialogue: 0,{ts(s['from'])},{ts(s['to'])},S,,0,0,0,,{s[lang]}\n" for s in sc["subtitles"])


def compose(path):
    sc = json.load(open(path, encoding="utf-8"))
    src = os.path.join(RAW, name_of(path) + ".mp4")
    if not os.path.exists(src): sys.exit(f"нет {src}: сначала record")
    os.makedirs(OUT, exist_ok=True)
    for lang in ("en", "ru"):
        for kind in ("wide", "tall"):
            wide = kind == "wide"
            sub = os.path.join(OUT, f".{name_of(path)}-{kind}-{lang}.ass")
            open(sub, "w", encoding="utf-8").write(ass(sc, lang, wide))
            if wide:  # телефон слева на тёмном фоне, текст справа
                graph = f"[0:v]scale=-2:1000[p];color=c=0x111418:s=1920x1080:r=30[bg];[bg][p]overlay=x=(1100-w)/2+20:y=40:shortest=1,ass={sub}"
            else:     # телефон на весь вертикальный кадр 1080x1920, текст внизу
                graph = f"[0:v]scale=-2:1560,pad=1080:1920:(ow-iw)/2:30:0x111418,ass={sub}"
            out = os.path.join(OUT, f"{name_of(path)}-{kind}-{lang}.mp4")
            r = subprocess.run([ffmpeg(), "-y", "-loglevel", "error", "-i", src, "-filter_complex", graph, "-r", "30",
                                "-c:v", "libx264", "-preset", "slow", "-crf", "18", "-pix_fmt", "yuv420p", "-movflags", "+faststart", "-an", out])
            os.remove(sub)
            print(("собрано: " if r.returncode == 0 else "ОШИБКА: ") + out)


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
    a = p.parse_args()
    if a.cmd in ("record", "all"): record(a.target, pick_device(a.device))
    if a.cmd in ("compose", "all"): compose(a.target)
    if a.cmd == "frames": frames(a.target, a.times)
