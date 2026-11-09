"""Compile the actual platform-independent app code, not a Python reimplementation."""
from pathlib import Path
import subprocess
import shutil

root = Path(__file__).resolve().parents[1]
output = root / 'build' / 'core-tests'
output.mkdir(parents=True, exist_ok=True)
sources = sorted((root / 'android/app/src/main/java/ir/amirsobhani/clipyar/core').glob('*.java'))
compiler = ['javac'] if shutil.which('javac') else ['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main']
level = ['--release', '17'] if shutil.which('javac') else ['-source', '17', '-target', '17']
subprocess.run([*compiler, *level, '-encoding', 'UTF-8', '-d', str(output),
                *map(str, sources), str(root / 'tests/CoreContract.java')], check=True)
print(f'Compiled {len(sources)} app core classes and their contract tests.')
