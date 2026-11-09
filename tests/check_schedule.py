"""Exercise fixed-Iran scheduling, full coverage, URL validation and chart freshness."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
subprocess.run(['java', '-cp', str(root / 'build/core-tests'), 'CoreContract'], check=True)
