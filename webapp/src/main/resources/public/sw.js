/*
 * Service worker for the ElephantChess PWA / Trusted Web Activity (Android).
 *
 * Strategy is intentionally conservative because the app is highly dynamic
 * (live games, websockets, per-request HTML):
 *   - Static media (images, audio) uses cache-first; these assets are immutable
 *     and safe to serve from cache.
 *   - Everything else (HTML pages, JS, CSS, API, websockets) is network-first so
 *     users never get stale application code or game state.
 *   - When the network is unavailable for a navigation request, a minimal offline
 *     page is shown.
 */

const CACHE_VERSION = "ec-pwa-v1";
const MEDIA_CACHE = `${CACHE_VERSION}-media`;

const OFFLINE_HTML = `<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Offline - ElephantChess</title>
<style>
  body{font-family:system-ui,sans-serif;background:#fedaa4;color:#9b1b1b;
       display:flex;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center}
  div{max-width:22rem;padding:1.5rem}
  img{width:96px;height:96px}
</style></head>
<body><div>
  <img src="/images/pwa/icon-192.png" alt="ElephantChess">
  <h1>You are offline</h1>
  <p>ElephantChess needs an internet connection to play. Please reconnect and try again.</p>
</div></body></html>`;

self.addEventListener("install", (event) => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    (async () => {
      const keys = await caches.keys();
      await Promise.all(
        keys
          .filter((key) => key !== MEDIA_CACHE)
          .map((key) => caches.delete(key)),
      );
      await self.clients.claim();
    })(),
  );
});

function isMediaRequest(url) {
  return (
    url.origin === self.location.origin &&
    (url.pathname.startsWith("/images/") || url.pathname.startsWith("/audio/"))
  );
}

self.addEventListener("fetch", (event) => {
  const { request } = event;

  if (request.method !== "GET") {
    return;
  }

  const url = new URL(request.url);

  if (isMediaRequest(url)) {
    event.respondWith(
      (async () => {
        const cache = await caches.open(MEDIA_CACHE);
        const cached = await cache.match(request);
        if (cached) {
          return cached;
        }
        const response = await fetch(request);
        if (response && response.ok) {
          cache.put(request, response.clone());
        }
        return response;
      })(),
    );
    return;
  }

  if (request.mode === "navigate") {
    event.respondWith(
      (async () => {
        try {
          return await fetch(request);
        } catch (err) {
          return new Response(OFFLINE_HTML, {
            headers: { "Content-Type": "text/html; charset=utf-8" },
          });
        }
      })(),
    );
  }
});
