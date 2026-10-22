"""Resolve the actual APK from AGP output metadata instead of guessing its name."""
import argparse
import json
from pathlib import Path

def locate_tv_apk(root: Path, variant: str = 'tvDebug') -> Path:
    root = root.resolve()
    matches = []
    for metadata in root.rglob('output-metadata.json'):
        data = json.loads(metadata.read_text(encoding='utf-8'))
        if data.get('variantName') != variant:
            continue
        for element in data.get('elements', []):
            candidate = (metadata.parent / element['outputFile']).resolve()
            if not candidate.is_relative_to(root):
                raise ValueError('APK output escapes the build output directory')
            if candidate.suffix == '.apk' and candidate.is_file():
                matches.append(candidate)
    if len(matches) != 1:
        found = [str(p.relative_to(root)) for p in root.rglob('*.apk')]
        raise ValueError(f'Expected one {variant} APK in AGP metadata; got {len(matches)}; APK files: {found}')
    return matches[0]

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path('app/build'))
    parser.add_argument('--variant', default='tvDebug')
    args = parser.parse_args()
    print(locate_tv_apk(args.root, args.variant))
