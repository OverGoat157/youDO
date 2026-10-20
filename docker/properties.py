"""Запись Java .properties без потери Unicode, пробелов и обратных слешей."""

import os
from pathlib import Path


def escape(value):
    result = []
    for char in str(value):
        if char in "\\ \t\r\n":
            result.append({"\\": "\\\\", " ": "\\ ", "\t": "\\t", "\r": "\\r", "\n": "\\n"}[char])
        elif 32 <= ord(char) < 127:
            result.append(char)
        else:
            encoded = char.encode("utf-16-be")
            result.extend("\\u" + encoded[i:i + 2].hex() for i in range(0, len(encoded), 2))
    return "".join(result)


def write_properties(path, url, user, password):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    values = {"db.url": url, "db.user": user, "db.password": password}
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600), "w", encoding="ascii") as stream:
        stream.write("# Generated from the local Docker configuration.\n")
        for key, value in values.items():
            stream.write(f"{key}={escape(value)}\n")
    path.chmod(0o600)
