"""Integration test with real FFmpeg: exact plan, vertical MP4 and complete coverage."""
from pathlib import Path
import json
import shutil
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
def probe(file):
    return json.loads(subprocess.check_output(['ffprobe', '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(file)]))

with tempfile.TemporaryDirectory(prefix='clipyar-video-') as directory:
    directory = Path(directory)
    source = directory / 'source.mp4'
    subprocess.run(['ffmpeg', '-v', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=320x180:rate=30',
                    '-f', 'lavfi', '-i', 'sine=frequency=440:sample_rate=48000', '-t', '123.4',
                    '-c:v', 'libx264', '-preset', 'ultrafast', '-c:a', 'aac', str(source)], check=True)
    total = 0.0
    for index, (start, length) in enumerate([(0, 60000), (60000, 60000), (120000, 3400)], 1):
        out = directory / f'{index:04d}.mp4'
        command = subprocess.check_output(['java', '-cp', str(root / 'build/core-tests'), 'CoreContract', 'ffmpeg',
                                           str(source), str(out), str(start), str(length), '180'], text=True).strip().split('\x1f')
        subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        info = probe(out)
        video = next(stream for stream in info['streams'] if stream['codec_type'] == 'video')
        audio = next(stream for stream in info['streams'] if stream['codec_type'] == 'audio')
        duration = float(info['format']['duration'])
        assert video['codec_name'] == 'h264' and audio['codec_name'] == 'aac'
        assert video['width'] == 180 and video['height'] == 320
        assert 0 < duration <= 60.1 and abs(duration - length / 1000) < 0.1
        total += duration
    assert abs(total - 123.4) < .25
    # Silent sources must work as well; no artificial audio stream is required.
    silent = directory / 'silent.mp4'
    subprocess.run(['ffmpeg', '-v', 'error', '-i', str(source), '-t', '1', '-an', '-c:v', 'copy', str(silent)], check=True)
    command = subprocess.check_output(['java', '-cp', str(root / 'build/core-tests'), 'CoreContract', 'ffmpeg',
                                       str(silent), str(directory / 'silent-part.mp4'), '0', '1000', '180'], text=True).strip().split('\x1f')
    subprocess.run(command, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
print('PASS: 123.4 seconds exported as 60 + 60 + 3.4; 9:16 H264/AAC; silent source accepted.')
