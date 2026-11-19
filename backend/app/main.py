"""FastAPI application entrypoint."""

import sys
from collections.abc import AsyncGenerator
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import cards, export, leads
from app.config import get_settings

# Windows(cp949) 등 UTF-8 이 아닌 콘솔/리다이렉트에서도 한글·이모지 로그로 죽지 않게 한다.
for _stream in (sys.stdout, sys.stderr):
    if hasattr(_stream, "reconfigure"):
        _stream.reconfigure(encoding="utf-8", errors="replace")

settings = get_settings()


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncGenerator[None, None]:
    """Application lifespan handler."""
    # Startup
    print(f"🚀 Starting R-Lead API (Environment: {settings.environment})")

    # MinIO 버킷 확인 및 생성
    from app.services.storage import storage_service

    await storage_service.ensure_bucket_exists()

    # 백그라운드 워커 시작
    import asyncio

    from app.services.background_worker import background_worker

    worker_task = asyncio.create_task(background_worker.start())

    yield

    # Shutdown
    print("👋 Shutting down R-Lead API")
    if "worker_task" in locals():
        await background_worker.stop()
        await worker_task


app = FastAPI(
    title="R-Lead API",
    description="명함 → 리드 자동화 웹 프로그램 API",
    version="0.1.0",
    docs_url="/api/docs",
    redoc_url="/api/redoc",
    openapi_url="/api/openapi.json",
    lifespan=lifespan,
)

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/")
async def root() -> dict[str, str]:
    """Root endpoint."""
    return {
        "message": "R-Lead API",
        "version": "0.1.0",
        "environment": settings.environment,
    }


@app.get("/health")
async def health() -> dict[str, str]:
    """Health check endpoint."""
    return {"status": "ok"}


# Include API routers

app.include_router(cards.router, prefix="/api/cards", tags=["cards"])
app.include_router(leads.router, prefix="/api/leads", tags=["leads"])
app.include_router(export.router, prefix="/api/export", tags=["export"])
