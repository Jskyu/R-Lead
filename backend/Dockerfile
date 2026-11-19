# Multi-stage build for Python (FastAPI) backend

# Stage 1: Build stage
FROM python:3.12-slim AS builder

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y --no-install-recommends gcc     && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
COPY requirements-simple.txt .
RUN pip install --no-cache-dir --user -r requirements-simple.txt

# Stage 2: Runtime stage
FROM python:3.12-slim

WORKDIR /app

# 한글/이모지 로그가 어떤 터미널에서도 깨지거나 죽지 않게 한다
ENV PYTHONUTF8=1 PYTHONUNBUFFERED=1

# Copy Python packages from builder
COPY --from=builder /root/.local /root/.local

# Make sure scripts in .local are usable
ENV PATH=/root/.local/bin:$PATH

# Copy application code
COPY app ./app
COPY alembic.ini .
COPY alembic ./alembic

# Copy entrypoint script
COPY entrypoint.sh .
RUN sed -i 's/$//' entrypoint.sh && chmod +x entrypoint.sh

EXPOSE 8000

# Health check (slim 이미지에는 wget/curl 이 없으므로 python 으로 확인)
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3     CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health', timeout=3)" || exit 1

# Run the application with entrypoint script (runs migrations first)
ENTRYPOINT ["./entrypoint.sh"]
