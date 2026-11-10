"""Adds sound to the recorded demo video.

The demo client (`gradlew runDemo`) writes run-demo/recording/raw.mp4 (silent) and cues.txt,
a list of "seconds sound_event" lines for every sound the game played. This script mixes the
elevator music under the whole video and the real Minecraft sounds for the cues that matter,
then writes the finished video.

    python tools/finish_demo.py <minecraft assets root> <music file> <output.mp4>

The assets root is the folder holding indexes/ and objects/ (see build/moddev/minecraft_assets.properties).
"""
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RECORDING = ROOT / "run-demo/recording"
FFMPEG = "C:/ffmpeg/bin/ffmpeg.exe" if Path("C:/ffmpeg/bin/ffmpeg.exe").exists() else "ffmpeg"

# Sound events worth hearing in the video, and how loud.
KEEP = {
    "hoistway:car_chime": 0.9,
    "minecraft:block.stone_button.click_on": 0.9,
    "minecraft:block.lever.click": 0.8,
    "minecraft:block.piston.extend": 0.35,
    "minecraft:block.anvil.place": 0.5,
    "minecraft:block.anvil.land": 0.4,
    "minecraft:block.metal.place": 0.8,
}
MUSIC_VOLUME = 0.45


def sound_files(assets_root):
    """Maps each sound event to the asset file of its first sound."""
    index_name = "17"
    props = ROOT / "build/moddev/minecraft_assets.properties"
    if props.exists():
        for line in props.read_text().splitlines():
            if line.startswith("asset_index="):
                index_name = line.split("=", 1)[1].strip()
    objects = json.loads((assets_root / "indexes" / f"{index_name}.json").read_text())["objects"]
    digest = objects["minecraft/sounds.json"]["hash"]
    events = json.loads((assets_root / "objects" / digest[:2] / digest).read_text(encoding="utf-8"))
    files = {}
    mod_assets = ROOT / "src/main/resources/assets/hoistway"
    mod_events = json.loads((mod_assets / "sounds.json").read_text(encoding="utf-8"))
    for event in KEEP:
        if event.startswith("hoistway:"):
            first = mod_events[event.split(":", 1)[1]]["sounds"][0]
            name = first if isinstance(first, str) else first["name"]
            files[event] = mod_assets / "sounds" / f"{name.split(':', 1)[1]}.ogg"
            continue
        entry = events.get(event.split(":", 1)[1])
        if not entry:
            continue
        first = entry["sounds"][0]
        name = first if isinstance(first, str) else first["name"]
        obj = objects.get(f"minecraft/sounds/{name.replace('minecraft:', '')}.ogg")
        if obj:
            digest = obj["hash"]
            files[event] = assets_root / "objects" / digest[:2] / digest
    return files


def main():
    assets_root, music, output = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    files = sound_files(assets_root)
    cues = []
    last = {}
    for line in (RECORDING / "cues.txt").read_text().splitlines():
        seconds, event = line.split(" ", 1)
        seconds = float(seconds)
        # The car's motor starts and stops a lot; skip repeats that land on top of each other.
        if event in files and seconds - last.get(event, -10.0) > 0.25:
            cues.append((seconds, event))
            last[event] = seconds

    duration = float(subprocess.run(
        [FFMPEG.replace("ffmpeg.exe", "ffprobe.exe") if FFMPEG.endswith(".exe") else "ffprobe",
         "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", str(RECORDING / "raw.mp4")],
        capture_output=True, text=True, check=True).stdout.strip())

    inputs = ["-i", str(RECORDING / "raw.mp4"), "-stream_loop", "-1", "-i", str(music)]
    filters = [f"[1:a]atrim=0:{duration:.3f},volume={MUSIC_VOLUME},afade=t=in:d=1.5,"
               f"afade=t=out:st={max(0.0, duration - 3):.3f}:d=3,aresample=44100,aformat=channel_layouts=stereo[music]"]
    labels = ["[music]"]
    for i, (seconds, event) in enumerate(cues):
        inputs += ["-i", str(files[event])]
        delay = int(seconds * 1000)
        filters.append(f"[{i + 2}:a]volume={KEEP[event]},aresample=44100,aformat=channel_layouts=stereo,"
                       f"adelay={delay}|{delay}[s{i}]")
        labels.append(f"[s{i}]")
    filters.append(f"{''.join(labels)}amix=inputs={len(labels)}:normalize=0:duration=first[aout]")

    output.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([FFMPEG, "-y", "-loglevel", "error", *inputs,
                    "-filter_complex", ";".join(filters),
                    "-map", "0:v", "-map", "[aout]",
                    "-c:v", "libx264", "-preset", "slow", "-crf", "25", "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "160k",
                    "-movflags", "+faststart", str(output)], check=True)
    print(f"wrote {output} ({duration:.1f}s, {len(cues)} sound cues)")


if __name__ == "__main__":
    main()
