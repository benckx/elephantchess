// Registers the PWA service worker (see /sw.js). Enables installability and
// "Add to Home screen", and backs the Android Trusted Web Activity wrapper.
if ("serviceWorker" in navigator) {
    window.addEventListener("load", () => {
        navigator.serviceWorker.register("/sw.js").catch((err) => {
            console.warn("Service worker registration failed:", err);
        });
    });
}
