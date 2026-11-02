"""Generate quiet, original five-format playback fixtures (requires imageio-ffmpeg).

pip install imageio-ffmpeg
python tools/generate_audio_fixtures.py
The generated files are test assets, not shipped in the app APK.
"""
import math
import pathlib
import struct
import subprocess
import wave
import imageio_ffmpeg

dest = pathlib.Path(__file__).resolve().parents[1] / 'app/src/androidTest/assets/audio'
dest.mkdir(parents=True, exist_ok=True)
wav = dest / 'moon.wav'
with wave.open(str(wav), 'wb') as stream:
    stream.setparams((2, 2, 44100, 0, 'NONE', 'not compressed'))
    frames = bytearray()
    for i in range(44100 * 6):
        # Quiet synthesized chord with short fades. No copyrighted music.
        fade = min(1, i / 2205, (44100 * 6 - i) / 2205)
        sample = int(500 * fade * (math.sin(2 * math.pi * 220 * i / 44100) + .5 * math.sin(2 * math.pi * 330 * i / 44100)))
        frames += struct.pack('<hh', sample, sample)
    stream.writeframes(frames)
ffmpeg = imageio_ffmpeg.get_ffmpeg_exe()
for name, codec, extra in [('moon.mp3', 'libmp3lame', []), ('moon.flac', 'flac', []),
                           ('moon.aac', 'aac', ['-f', 'adts']), ('moon-alac.m4a', 'alac', [])]:
    subprocess.run([ffmpeg, '-v', 'error', '-y', '-i', str(wav), '-c:a', codec,
                    '-metadata', 'title=月光试音 · ' + name.split('.')[-1].upper(),
                    '-metadata', 'artist=Stillune', '-metadata', 'album=静夜试音',
                    *extra, str(dest / name)], check=True)
print('Generated WAV, MP3, FLAC, AAC and ALAC test fixtures at', dest)
