"""Полный прогон JUnit/JavaFX/JDBC в контейнере с виртуальным дисплеем."""

import os
import subprocess
import sys
from urllib.parse import quote

from properties import write_properties


database = quote(os.environ["POSTGRES_DB"], safe="")
host = os.environ.get("YOUDO_TEST_DB_HOST", "db")
config = "/tmp/youdo-db.properties"
write_properties(config, f"jdbc:postgresql://{host}:5432/{database}",
                 os.environ["POSTGRES_USER"], os.environ["POSTGRES_PASSWORD"])
result = subprocess.run([
    "xvfb-run", "-a", "mvn", "-B", "-Dmaven.repo.local=/home/app/.m2/repository",
    "-Dyoudo.fx.tests=true", "-Dyoudo.db.tests=true", f"-Dyoudo.test.db.config={config}",
    "clean", "verify",
])
sys.exit(result.returncode)
