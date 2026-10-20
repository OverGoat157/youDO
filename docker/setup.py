"""Создаёт локальные настройки Compose и отдельный JDBC-конфиг, без замены конфига КР1."""

import json
import os
from pathlib import Path
import secrets
import subprocess
import sys
from urllib.parse import quote

from properties import write_properties


def main():
    root = Path(__file__).resolve().parent.parent
    env_file = root / ".env"
    if not env_file.exists():
        content = (root / ".env.example").read_text(encoding="utf-8")
        content = content.replace("YOUDO_DB_PASSWORD=CHANGE_ME", "YOUDO_DB_PASSWORD=" + secrets.token_hex(24))
        with os.fdopen(os.open(env_file, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w", encoding="utf-8") as stream:
            stream.write(content)
    result = subprocess.run(["docker", "compose", "config", "--format", "json"], cwd=root,
                            capture_output=True, text=True)
    if result.returncode:
        raise RuntimeError("Не удалось прочитать Docker Compose. Проверьте установку Docker и значения в .env.")
    service = json.loads(result.stdout)["services"]["db"]
    env = service["environment"]
    port = service["ports"][0]["published"]
    database = quote(env["POSTGRES_DB"], safe="")
    target = root / "docker" / "db.properties"
    write_properties(target, f"jdbc:postgresql://127.0.0.1:{port}/{database}",
                     env["POSTGRES_USER"], env["POSTGRES_PASSWORD"])
    print(f"Готово: .env и docker/db.properties. PostgreSQL будет доступен на 127.0.0.1:{port}.")
    print("Текущий src/main/resources/db.properties сохранён без изменений.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, KeyError, ValueError) as error:
        print(str(error), file=sys.stderr)
        sys.exit(1)
