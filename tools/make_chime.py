"""Synthesizes the elevator's arrival chime: a rising C# major seventh arpeggio (C#, E#, G#, B#)
with a soft vibraphone-like tone, written as mono Ogg Vorbis.

    python tools/make_chime.py

Needs ffmpeg (C:/ffmpeg/bin/ffmpeg.exe or on PATH) to encode the Ogg.
"""
import array
import math
import subprocess
import tempfile
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/hoistway/sounds/car/chime.ogg"
FFMPEG = "C:/ffmpeg/bin/ffmpeg.exe" if Path("C:/ffmpeg/bin/ffmpeg.exe").exists() else "ffmpeg"

RATE = 44100
# C#5, E#5 (= F5), G#5, B#5 (= C6), equal temperament from A4 = 440 Hz.
NOTES = [440.0 * 2 ** ((n - 69) / 12) for n in (73, 77, 80, 84)]
SPACING = 0.15        # seconds between notes
RING = 2.2            # how long the last note rings
# (frequency ratio, level, decay time in seconds): the fundamental, then the bar's bright overtones.
PARTIALS = [(1.0, 1.0, 0.9), (2.0, 0.12, 0.5), (4.0, 0.22, 0.25), (9.9, 0.04, 0.08)]
ATTACK = 0.004


def note(start, frequency, samples):
    first = int(start * RATE)
    for i in range(first, len(samples)):
        t = (i - first) / RATE
        level = min(1.0, t / ATTACK)
        value = 0.0
        for ratio, amplitude, decay in PARTIALS:
            value += amplitude * math.exp(-t / decay) * math.sin(2 * math.pi * frequency * ratio * t)
        samples[i] += level * value


def main():
    length = SPACING * (len(NOTES) - 1) + RING
    samples = [0.0] * int(length * RATE)
    for index, frequency in enumerate(NOTES):
        note(index * SPACING, frequency, samples)

    # Fade the tail to silence and normalise to a comfortable peak.
    tail = int(0.3 * RATE)
    for i in range(tail):
        samples[-1 - i] *= i / tail
    peak = max(abs(s) for s in samples)
    pcm = array.array("h", (int(s / peak * 0.8 * 32767) for s in samples))

    with tempfile.TemporaryDirectory() as folder:
        wav = Path(folder) / "chime.wav"
        with wave.open(str(wav), "wb") as out:
            out.setnchannels(1)
            out.setsampwidth(2)
            out.setframerate(RATE)
            out.writeframes(pcm.tobytes())
        OUT.parent.mkdir(parents=True, exist_ok=True)
        subprocess.run([FFMPEG, "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(OUT)],
                       check=True)
    print(f"wrote {OUT} ({length:.2f}s, notes {', '.join(f'{f:.1f}' for f in NOTES)} Hz)")


if __name__ == "__main__":
    main()
