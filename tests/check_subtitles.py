"""Real FFmpeg test: transparent timed overlays, seeking, and cue reset at a minute boundary.

The phone renders Persian with Android Canvas/StaticLayout. This host check uses
synthetic PNG marks to verify the actual compositor; it does not claim a phone pass.
"""
from pathlib import Path
import json
import struct
import subprocess
import tempfile
import zlib

root = Path(__file__).resolve().parents[1]

def png(file, visible):
    width, height = 180, 80
    rows = bytearray()
    for y in range(height):
        rows.append(0)
        for x in range(width):
            rows.extend((255, 0, 255, 255) if visible and 40 <= x < 140 and 20 <= y < 60 else (0, 0, 0, 0))
    def chunk(name, data):
        return struct.pack('!I', len(data)) + name + data + struct.pack('!I', zlib.crc32(name + data) & 0xffffffff)
    file.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('!2I5B', width, height, 8, 6, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))

def timeline(file, frames):
    text = 'ffconcat version 1.0\n'
    for image, duration in frames:
        text += f"file '{image}'\noption framerate 1000\nduration {duration:.3f}\n"
    text += f"file '{frames[-1][0]}'\noption framerate 1000\n"
    file.write_text(text)

def marked(file, second):
    frame = subprocess.check_output(['ffmpeg', '-v', 'error', '-ss', str(second), '-i', str(file), '-frames:v', '1', '-vf', 'crop=20:20:80:240', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-threads', '1', '-'])
    assert frame, 'No rendered frame'
    red, green, blue = (sum(frame[n::3]) / len(frame[n::3]) for n in range(3))
    return red > 220 and green < 40 and blue > 220

with tempfile.TemporaryDirectory(prefix='clipyar-captions-') as directory:
    directory = Path(directory)
    blank, caption = directory / 'blank.png', directory / 'caption.png'
    png(blank, False)
    png(caption, True)
    source = directory / 'complete.mp4'
    subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'color=c=black:s=320x180:r=30', '-f', 'lavfi', '-i', 'sine=frequency=440', '-t', '61.4', '-c:v', 'libx264', '-preset', 'ultrafast', '-c:a', 'aac', '-threads', '1', str(source)], check=True)
    # Global caption: 59.5--61.0 s. The first tested window starts at 58 s,
    # while the second begins exactly at the 60 s file boundary.
    for index, start, length, frames, checkpoints in [
        (1, 58000, 2000, [(blank, 1.5), (caption, .5)], [(0.5, False), (1.75, True)]),
        (2, 60000, 1400, [(caption, 1), (blank, .4)], [(0.25, True), (1.25, False)]),
    ]:
        script, output = directory / f'{index}.ffconcat', directory / f'{index:04d}.mp4'
        timeline(script, frames)
        command = subprocess.check_output(['java', '-cp', str(root / 'build/core-tests'), 'CoreContract', 'ffmpeg-subtitles', str(source), str(output), str(start), str(length), '180', str(script)], text=True).strip().split('\x1f')
        result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        assert result.returncode == 0, result.stderr.decode()[-5000:]
        info = json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_format', '-of', 'json', str(output)]))
        assert abs(float(info['format']['duration']) - length / 1000) < .15
        for moment, expected in checkpoints:
            assert marked(output, moment) == expected, (index, moment, 'subtitle timing/alpha error')
print('PASS: real compositor; transparent gaps; burned-in timed marks; caption continues at t=0 in next part and clears on time.')
