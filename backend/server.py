"""Savr extract backend.

Implements the contract the app expects (see README):
  POST /v1/extract {"url": "..."} ->
  {"title","caption","hashtags":[],"thumbnail","duration","type","qualities":[{"label","url","bytes"}]}
  Errors: 400 invalid, 422 unsupported, 403/404 private.

yt-dlp resolves the post to media URLs. Those URLs often need the same headers/cookies yt-dlp used, so instead of handing
them to the phone directly every quality points at /v1/stream/<token>.<ext> on this server, which relays the bytes and
forwards Range requests (the app's downloader resumes with Range).

Run:  backend\\.venv\\Scripts\\python.exe backend\\server.py      (listens on 0.0.0.0:8000)
Optional: put a Netscape-format cookies.txt next to this file for posts that need a login (many Instagram reels do).
"""
import re
import secrets
import shutil
import threading
import time
from pathlib import Path

import requests
import uvicorn
import yt_dlp
from fastapi import FastAPI, HTTPException, Request
from fastapi.concurrency import run_in_threadpool
from fastapi.responses import FileResponse, StreamingResponse
from pydantic import BaseModel

HERE = Path(__file__).parent
COOKIES = HERE / "cookies.txt"
TOKEN_TTL = 6 * 3600  # media URLs from the platforms expire after a few hours anyway
CACHE = HERE / "cache"  # merged (video+audio) files, deleted after TOKEN_TTL
FFMPEG = shutil.which("ffmpeg")

app = FastAPI(title="Savr backend")
_streams: dict[str, tuple[float, str, dict]] = {}  # token -> (created, media url, http headers)
_merges: dict[str, tuple[float, str, str]] = {}  # token -> (created, page url, yt-dlp format selector)
_merge_locks: dict[str, threading.Lock] = {}
_http = requests.Session()


# Only these sites are resolved. Once the server is public, an open "fetch any URL" endpoint could be abused as a proxy
# or to reach machines on the host's network, so everything else is refused (direct file links never reach the backend).
ALLOWED_HOSTS = ("instagram.com", "facebook.com", "fb.watch", "fb.com", "youtube.com", "youtu.be")


def _allowed(url: str) -> bool:
    from urllib.parse import urlparse
    h = (urlparse(url).hostname or "").lower()
    return any(h == d or h.endswith("." + d) for d in ALLOWED_HOSTS)


class ExtractRequest(BaseModel):
    url: str


def _ydl_opts() -> dict:
    o = {"quiet": True, "no_warnings": True, "skip_download": True, "noplaylist": True}
    if COOKIES.exists():
        o["cookiefile"] = str(COOKIES)
    return o


def _http_error(e: Exception) -> HTTPException:
    m = str(e).lower()
    if "unsupported url" in m or "no suitable extractor" in m:
        return HTTPException(422, "unsupported")
    if any(k in m for k in ("private", "login", "log in", "sign in", "not available", "unavailable", "removed",
                            "does not exist", "404", "403", "restricted", "age")):
        return HTTPException(403, "private")
    return HTTPException(502, "extract failed")


def _register(url: str, headers: dict) -> str:
    now = time.time()
    for k in [k for k, v in _streams.items() if now - v[0] > TOKEN_TTL]:
        _streams.pop(k, None)
    t = secrets.token_urlsafe(16)
    _streams[t] = (now, url, headers)
    return t


def _register_merge(page_url: str, selector: str) -> str:
    t = secrets.token_urlsafe(16)
    _merges[t] = (time.time(), page_url, selector)
    return t


def _merged_size(fmts, max_h):
    """Approximate size of best video <= max_h + best audio, for the size shown in the app."""
    v = [f for f in fmts if f.get("vcodec") not in (None, "none") and (f.get("height") or 0) <= max_h and _size(f)]
    a = [f for f in fmts if f.get("vcodec") in (None, "none") and f.get("acodec") not in (None, "none") and _size(f)]
    if not v or not a:
        return None
    bv = max(v, key=lambda f: (f.get("height") or 0, f.get("tbr") or 0)); ba = max(a, key=lambda f: f.get("abr") or 0)
    return _size(bv) + _size(ba)


def _progressive(fmts):
    """Formats with both audio and video in one plain-HTTP file (no DASH/HLS merging needed)."""
    return [f for f in fmts if f.get("url") and f.get("vcodec") not in (None, "none") and f.get("acodec") not in (None, "none")
            and f.get("protocol", "https") in ("http", "https")]


def _audio(fmts):
    a = [f for f in fmts if f.get("url") and f.get("vcodec") in (None, "none") and f.get("acodec") not in (None, "none")
         and f.get("protocol", "https") in ("http", "https")]
    m4a = [f for f in a if f.get("ext") == "m4a"]  # app saves audio as audio/mp4
    return max(m4a or a, key=lambda f: f.get("abr") or f.get("tbr") or 0, default=None)


def _size(f):
    return f.get("filesize") or f.get("filesize_approx")


def _build(info: dict, base: str) -> dict:
    if info.get("_type") == "playlist" and info.get("entries"):  # carousels / multi-video posts: take the first item
        info = next(e for e in info["entries"] if e)
    fmts = info.get("formats") or ([info] if info.get("url") else [])
    prog = sorted(_progressive(fmts), key=lambda f: (f.get("height") or 0, f.get("tbr") or 0))
    qualities = []

    def add(label, f, ext):
        token = _register(f["url"], f.get("http_headers") or info.get("http_headers") or {})
        qualities.append({"label": label, "url": f"{base}v1/stream/{token}.{ext}", "bytes": _size(f)})

    best_h = max([f.get("height") or 0 for f in prog], default=0)
    video_only_h = max([f.get("height") or 0 for f in fmts if f.get("vcodec") not in (None, "none") and f.get("acodec") in (None, "none")], default=0)
    if FFMPEG and video_only_h > best_h and info.get("webpage_url"):
        # Platform only offers separate video/audio streams at higher quality (YouTube): merge on this server.
        page = info["webpage_url"]
        # Label by the resolution actually available: HD only if >= 720p exists; SD (<= 480p) only when it differs.
        tiers = [("HD", 1080), ("SD", 480)] if video_only_h >= 720 else [("SD", 1080)]
        for label, h in tiers:
            sel = f"bv*[height<={h}][ext=mp4]+ba[ext=m4a]/bv*[height<={h}]+ba/b[height<={h}]/b"
            qualities.append({"label": label, "url": f"{base}v1/merge/{_register_merge(page, sel)}.mp4", "bytes": _merged_size(fmts, h)})
    elif prog:
        best = prog[-1]
        add("HD" if (best.get("height") or 0) >= 720 else "SD", best, "mp4")
        smaller = [f for f in prog if (f.get("height") or 0) < (best.get("height") or 0)]
        if qualities[0]["label"] == "HD" and smaller:
            # pick the SD option closest to 480p
            add("SD", min(smaller, key=lambda f: abs((f.get("height") or 0) - 480)), "mp4")
    a = _audio(fmts)
    if a:
        add("Audio", a, "m4a")

    is_image = not qualities and info.get("url") and info.get("ext") in ("jpg", "jpeg", "png", "webp")
    if is_image:
        add("Original", info, info["ext"])
    if not qualities:
        raise HTTPException(502, "no downloadable format")

    caption = (info.get("description") or "").strip()
    tags = list(dict.fromkeys(re.findall(r"#(\w+)", caption) + [t for t in (info.get("tags") or []) if " " not in t]))[:30]
    title = info.get("title") or ""
    if not title or title.lower().startswith(("video by", "post by")):
        title = (caption.split("\n")[0][:80] or f"{info.get('extractor_key', 'Video')} by {info.get('uploader') or 'unknown'}")
    return {
        # thumbnails go through /v1/stream too: Instagram/Facebook CDNs reject requests without the right headers
        "title": title, "caption": caption, "hashtags": tags,
        "thumbnail": f"{base}v1/stream/{_register(info['thumbnail'], info.get('http_headers') or {})}.jpg" if info.get("thumbnail") else "",
        "duration": int(info.get("duration") or 0), "type": "image" if is_image else "video", "qualities": qualities,
    }


@app.post("/v1/extract")
async def extract(body: ExtractRequest, request: Request):
    url = body.url.strip()
    if not re.match(r"^https?://\S+$", url):
        raise HTTPException(400, "invalid")
    if not _allowed(url):
        raise HTTPException(422, "unsupported")
    try:
        info = await run_in_threadpool(lambda: yt_dlp.YoutubeDL(_ydl_opts()).extract_info(url, download=False))
    except yt_dlp.utils.DownloadError as e:
        raise _http_error(e)
    if not info:
        raise HTTPException(404, "not found")
    return _build(info, str(request.base_url))


@app.get("/v1/stream/{name}")
@app.head("/v1/stream/{name}")
def stream(name: str, request: Request):
    entry = _streams.get(name.split(".")[0])
    if not entry:
        raise HTTPException(410, "expired")  # app treats 4xx as an expired link and asks to retry from the post
    _, url, headers = entry
    h = dict(headers)
    if "range" in request.headers:
        h["Range"] = request.headers["range"]
    r = _http.get(url, headers=h, stream=True, timeout=30)
    if r.status_code >= 400:
        r.close()
        raise HTTPException(410 if r.status_code in (403, 404, 410) else 502, "upstream")
    out = {k: v for k, v in r.headers.items() if k.lower() in ("content-length", "content-range", "content-type", "accept-ranges")}
    if request.method == "HEAD":
        r.close()
        return StreamingResponse(iter(()), status_code=r.status_code, headers=out)
    return StreamingResponse(r.iter_content(256 * 1024), status_code=r.status_code, headers=out, background=None)


@app.get("/v1/merge/{name}")
def merge(name: str):
    """Download + merge on first request (can take a while for long videos), then serve the file with Range support."""
    token = name.split(".")[0]
    entry = _merges.get(token)
    if not entry:
        raise HTTPException(410, "expired")
    _, page, selector = entry
    CACHE.mkdir(exist_ok=True)
    now = time.time()
    for f in CACHE.glob("*"):
        if now - f.stat().st_mtime > TOKEN_TTL:
            f.unlink(missing_ok=True)
    out = CACHE / f"{token}.mp4"
    with _merge_locks.setdefault(token, threading.Lock()):  # app retries/resumes must not start a second merge
        if not out.exists():
            opts = {**_ydl_opts(), "skip_download": False, "format": selector, "merge_output_format": "mp4",
                    "outtmpl": str(CACHE / f"{token}.tmp.%(ext)s"), "ffmpeg_location": FFMPEG}
            try:
                yt_dlp.YoutubeDL(opts).download([page])
            except yt_dlp.utils.DownloadError as e:
                raise _http_error(e)
            done = next(iter(CACHE.glob(f"{token}.tmp.*")), None)
            if not done:
                raise HTTPException(502, "merge failed")
            done.replace(out)
    return FileResponse(out, media_type="video/mp4")


@app.get("/health")
def health():
    return {"ok": True, "yt_dlp": yt_dlp.version.__version__, "cookies": COOKIES.exists()}


if __name__ == "__main__":
    import os
    # proxy_headers: behind a cloud load balancer, request.base_url must be the public https URL the phone used
    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8000")), proxy_headers=True, forwarded_allow_ips="*")
