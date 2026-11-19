"""Initialize database with all tables."""
import asyncio

from sqlalchemy.ext.asyncio import create_async_engine

from app.config import get_settings
from app.models import Base, Lead, ProcessingJob  # Import all models

async def init_database():
    """Create all tables in the database."""
    settings = get_settings()

    print(f"Initializing database: {settings.database_url}")

    engine = create_async_engine(settings.database_url, echo=True)

    async with engine.begin() as conn:
        # Drop all tables (if exists)
        await conn.run_sync(Base.metadata.drop_all)
        print("✓ Dropped existing tables")

        # Create all tables
        await conn.run_sync(Base.metadata.create_all)
        print("✓ Created all tables")

    await engine.dispose()
    print("✓ Database initialized successfully!")

if __name__ == "__main__":
    asyncio.run(init_database())
