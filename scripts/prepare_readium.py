#!/usr/bin/env python3
"""Materialize a checksum-pinned Readium navigator and apply the reviewed local patch."""
import hashlib
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import sys
import tarfile
import urllib.request

COMMIT = '71074ea0c424eabbc39a93b48a4cefe366f7741b'
SHA256 = '0fb071a8a9966eb41a22065e3a38d629502b38f1394e36f461cd0129835051ae'
root = Path(__file__).resolve().parent.parent
patch = root / 'patches/readium-selection.patch'
destination = Path(sys.argv[1]).resolve()
fingerprint = COMMIT + hashlib.sha256(patch.read_bytes() + Path(__file__).read_bytes()).hexdigest()
marker = destination / '.readle-source'
if marker.exists() and marker.read_text() == fingerprint:
    sys.exit(0)
cache = Path.home() / '.cache/readle'
cache.mkdir(parents=True, exist_ok=True)
archive = cache / (COMMIT + '.tar.gz')
if not archive.exists() or hashlib.sha256(archive.read_bytes()).hexdigest() != SHA256:
    temporary = archive.with_suffix('.download')
    with urllib.request.urlopen('https://codeload.github.com/readium/kotlin-toolkit/tar.gz/' + COMMIT, timeout=90) as response:
        with temporary.open('wb') as output:
            shutil.copyfileobj(response, output)
    if hashlib.sha256(temporary.read_bytes()).hexdigest() != SHA256:
        temporary.unlink()
        raise RuntimeError('Readium source checksum mismatch')
    temporary.replace(archive)
if destination.exists():
    shutil.rmtree(destination)
destination.mkdir(parents=True)
prefix = 'kotlin-toolkit-' + COMMIT + '/readium/navigator/'
with tarfile.open(archive) as source:
    for member in source.getmembers():
        if member.isfile() and member.name.startswith(prefix):
            relative = PurePosixPath(member.name[len(prefix):])
            if '..' in relative.parts or relative.is_absolute():
                raise RuntimeError('Unsafe archive entry')
            if str(relative).startswith(('src/main/', 'libs/')):
                target = destination / str(relative)
                target.parent.mkdir(parents=True, exist_ok=True)
                with source.extractfile(member) as input_file, target.open('wb') as output:
                    shutil.copyfileobj(input_file, output)
    license_file = source.extractfile('kotlin-toolkit-' + COMMIT + '/LICENSE').read()
    target = destination / 'src/main/assets/readle_readium_LICENSE.txt'
    target.write_bytes(license_file)
subprocess.run(['patch', '-p1', '--batch', '--forward', '-i', str(patch)], cwd=destination, check=True)
marker.write_text(fingerprint)
print('Prepared Readium 3.1.2 at ' + COMMIT)
