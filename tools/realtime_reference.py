"""Independent synthetic metering references. Usage: python tools/realtime_reference.py --ffmpeg PATH.

Writes results only; no downloaded music, calibration claims, or app runtime dependency.
"""
import argparse
import json
import math
from pathlib import Path
import re
import struct
import subprocess
import tempfile


def run_reference(ffmpeg: str, recipe: str, channels: int, destination: Path) -> dict:
    seconds = 36 if recipe == 'dynamic' else 5
    with destination.open('wb') as stream:
        for n in range(seconds * 48000):
            amplitude = [0.04, 0.2, 0.07, 0.32, 0.0][min(n // (8 * 48000), 4)] if recipe == 'dynamic' else 0.1
            value = amplitude * math.sin(2 * math.pi * 1000 * n / 48000)
            stream.write(struct.pack('<' + 'f' * channels, *([value] * channels)))
    result = subprocess.run([ffmpeg, '-hide_banner', '-f', 'f32le', '-ar', '48000', '-ac', str(channels),
        '-i', str(destination), '-af', 'ebur128=peak=true', '-f', 'null', '-'], capture_output=True, text=True, check=True)
    summary = result.stderr.rsplit('Summary:', 1)[1]
    def read(label: str) -> float:
        return float(re.search(label + r':\s+([-\d.]+)', summary).group(1))
    return {'recipe': recipe, 'channels': channels, 'sample_rate': 48000, 'seconds': seconds,
            'integrated_lufs': read('I'), 'lra_lu': read('LRA'), 'true_peak_dbtp': read('Peak')}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--ffmpeg', default='ffmpeg')
    parser.add_argument('--temp-dir', default=None, help='Optional writable scratch directory')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='stillune-reference-', dir=args.temp_dir) as folder:
        results = [run_reference(args.ffmpeg, recipe, channels, Path(folder) / 'source.f32')
                   for recipe, channels in [('steady', 2), ('steady', 1), ('dynamic', 2)]]
    version = subprocess.run([args.ffmpeg, '-version'], capture_output=True, text=True, check=True).stdout.splitlines()[0]
    print(json.dumps({'reference': version, 'algorithm': 'FFmpeg ebur128, peak=true', 'results': results}, indent=2))


if __name__ == '__main__':
    main()
