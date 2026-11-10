#!/usr/bin/env python3
"""Create secrets once; print only the file location, never secret values."""
import base64
import os
from pathlib import Path
import secrets

destination = Path(__file__).resolve().parents[1] / '.env'
template = destination.with_name('.env.example').read_text()
template = template.replace('REPLACE_WITH_RANDOM_DATABASE_PASSWORD', secrets.token_urlsafe(32))
template = template.replace('REPLACE_WITH_BASE64_RANDOM_BYTES', base64.b64encode(secrets.token_bytes(48)).decode())
template = template.replace('REPLACE_WITH_STRONG_TEMPORARY_PASSWORD', 'Aa1!' + secrets.token_urlsafe(24))
try:
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
except FileExistsError:
    raise SystemExit(f'{destination} уже существует; файл не изменён.')
with os.fdopen(descriptor, 'w') as stream:
    stream.write(template)
print(f'Создан {destination}. Временный пароль администратора хранится только в этом файле.')
