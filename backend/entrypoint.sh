#!/bin/bash
set -e

echo "Waiting for database..."
python - <<'PY'
import asyncio
import sys

from sqlalchemy import text
from sqlalchemy.ext.asyncio import create_async_engine

from app.config import get_settings


async def wait() -> None:
    engine = create_async_engine(get_settings().database_url)
    for attempt in range(30):
        try:
            async with engine.connect() as conn:
                await conn.execute(text("SELECT 1"))
            print("Database is ready")
            await engine.dispose()
            return
        except Exception as exc:  # noqa: BLE001
            print(f"  not ready yet ({attempt + 1}/30): {type(exc).__name__}")
            await asyncio.sleep(2)
    print("Database did not become ready in time")
    sys.exit(1)


asyncio.run(wait())
PY

echo "Running database migrations..."
alembic upgrade head

echo "Starting application..."
exec uvicorn app.main:app --host 0.0.0.0 --port 8000
