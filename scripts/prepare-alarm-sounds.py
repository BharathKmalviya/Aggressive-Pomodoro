"""Prepare the app's bounded alarm cues from local owner-supplied originals.

Normal builds use checked-in WAVs and need neither Python nor FFmpeg.
Run: python scripts/prepare-alarm-sounds.py --ffmpeg <path-to-ffmpeg>
"""
import argparse
import array
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import wave


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    manifest_path = root / "sounds/catalog.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    output = root / "desktopApp/resources/sounds/alarms"
    output.mkdir(parents=True, exist_ok=True)
    for entry in manifest["sounds"]:
        source = root / "sounds" / entry["source"]
        decoded = subprocess.run([
            args.ffmpeg, "-v", "error", "-i", str(source), "-t", str(manifest["maximumSeconds"]),
            "-ac", "1", "-ar", "44100", "-f", "s16le", "-acodec", "pcm_s16le", "pipe:1",
        ], check=True, capture_output=True).stdout
        samples = array.array("h", decoded)
        if sys.byteorder != "little":
            samples.byteswap()
        if not samples or len(samples) > 44_100 * manifest["maximumSeconds"]:
            raise ValueError(f"Invalid decoded length: {source.name}")
        peak = max(abs(value) for value in samples)
        if peak == 0:
            raise ValueError(f"Silent input: {source.name}")
        gain = min(1.0, manifest["peakLimit"] * 32_767 / peak)
        fade_in = 44_100 * manifest["fadeInMs"] // 1_000
        fade_out = 44_100 * manifest["fadeOutMs"] // 1_000
        for index, value in enumerate(samples):
            envelope = min(1.0, index / fade_in, (len(samples) - index - 1) / fade_out)
            samples[index] = round(value * gain * envelope)
        if sys.byteorder != "little":
            samples.byteswap()
        target = output / f"{entry['id']}.wav"
        with wave.open(str(target), "wb") as wav:
            wav.setnchannels(1)
            wav.setsampwidth(2)
            wav.setframerate(44_100)
            wav.writeframes(samples.tobytes())
        entry["sourceSha256"] = hashlib.sha256(source.read_bytes()).hexdigest()
        entry["preparedSha256"] = hashlib.sha256(target.read_bytes()).hexdigest()
        entry["preparedFrames"] = len(samples)
        print(f"{entry['id']}: {len(samples) / 44_100:.2f}s")
    manifest_path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
