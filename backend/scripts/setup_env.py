#!/usr/bin/env python3
"""Prepare the root .env, preserving existing KR2/KR3 settings and legacy secrets."""
import base64
import os
from pathlib import Path
import re
import secrets
import tempfile


def assignments(content):
    result = {}
    for index, line in enumerate(content.splitlines(keepends=True)):
        match = re.fullmatch(r'\s*(?:export\s+)?([A-Za-z_][A-Za-z0-9_]*)\s*=(.*)', line.rstrip('\r\n'))
        if match:
            result[match[1]] = (index, match[2].strip())
    return result


def prepare_env(root):
    destination = root / '.env'
    original = destination.read_text(encoding='utf-8') if destination.exists() else ''
    template = (root / '.env.example').read_text(encoding='utf-8')
    legacy_path = root / 'backend' / '.env'
    legacy = assignments(legacy_path.read_text(encoding='utf-8')) if legacy_path.exists() else {}
    existing = assignments(original)
    replacements = {
        'CHANGE_ME': secrets.token_hex(24),
        'REPLACE_WITH_RANDOM_DATABASE_PASSWORD': secrets.token_urlsafe(32),
        'REPLACE_WITH_BASE64_RANDOM_BYTES': base64.b64encode(secrets.token_bytes(48)).decode(),
        'REPLACE_WITH_STRONG_TEMPORARY_PASSWORD': 'Aa1!' + secrets.token_urlsafe(24),
    }
    lines = original.splitlines(keepends=True)
    additions = []
    for key, (_, default) in assignments(template).items():
        current = existing.get(key)
        if current is not None and current[1] not in replacements:
            continue
        value = legacy.get(key, (None, default))[1]
        value = replacements.get(value, value)
        line = f'{key}={value}\n'
        if current is None:
            additions.append(line)
        else:
            lines[current[0]] = line
    content = ''.join(lines)
    if additions:
        if content and not content.endswith('\n'):
            content += '\n'
        content += ''.join(additions)
    if destination.exists() and content == original:
        return False
    descriptor, name = tempfile.mkstemp(dir=root, prefix='.env-')
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, 'w', encoding='utf-8', newline='') as stream:
            stream.write(content)
        os.replace(temporary, destination)
    finally:
        temporary.unlink(missing_ok=True)
    return True


if __name__ == '__main__':
    root = Path(__file__).resolve().parents[2]
    changed = prepare_env(root)
    print(f'{root / ".env"}: ' + ('настройки подготовлены.' if changed else 'уже настроен; файл не изменён.'))
    print('Существующие значения сохранены. Пароли и токены не выводятся.')
