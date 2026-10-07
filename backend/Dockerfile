# Savr backend: yt-dlp + ffmpeg behind FastAPI. Any Docker host works (Render, Railway, Fly.io, a VPS).
FROM python:3.12-slim
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates && rm -rf /var/lib/apt/lists/*
# yt-dlp needs a JavaScript runtime to solve YouTube's player challenges; Deno is the one it enables by default
COPY --from=denoland/deno:bin /deno /usr/local/bin/deno
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY server.py .
# Optional: mount/copy a Netscape cookies.txt next to server.py for posts that need a login (many Instagram reels)
ENV PORT=8000
EXPOSE 8000
CMD ["python", "server.py"]
