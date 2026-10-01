(function () {
    function getEventElementTarget(event) {
        const target = event.target;
        if (target instanceof Element) {
            return target;
        }
        if (target && target.nodeType === Node.TEXT_NODE) {
            return target.parentElement;
        }
        return null;
    }

    function shouldShowForLinkEvent(event) {
        if (event.defaultPrevented || event.button !== 0) {
            return false;
        }
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
            return false;
        }

        const targetElement = getEventElementTarget(event);
        if (!targetElement) {
            return false;
        }
        const link = targetElement.closest("a[href]");
        if (!link) {
            return false;
        }
        if (link.dataset.noLoadingOverlay === "true") {
            return false;
        }

        const href = link.getAttribute("href");
        if (!href || href.startsWith("#") || href.startsWith("javascript:")) {
            return false;
        }
        if (link.hasAttribute("download") || link.target === "_blank") {
            return false;
        }

        let destination;
        try {
            destination = new URL(link.href, window.location.href);
        } catch (error) {
            return false;
        }

        if (destination.origin !== window.location.origin) {
            return false;
        }

        const current = window.location.pathname + window.location.search + window.location.hash;
        const next = destination.pathname + destination.search + destination.hash;
        return current !== next;
    }

    function installLoadingOverlay() {
        if (window.__appLoadingOverlayInitialized) {
            return;
        }
        window.__appLoadingOverlayInitialized = true;

        if (document.body && document.body.dataset.disableLoadingOverlay === "true") {
            return;
        }

        const overlay = document.createElement("div");
        overlay.className = "app-loading-overlay";
        overlay.id = "appLoadingOverlay";
        overlay.setAttribute("aria-hidden", "true");
        // Inline fallback styles: keep overlay hidden/positioned even if stale CSS is served from cache.
        overlay.style.position = "fixed";
        overlay.style.inset = "0";
        overlay.style.zIndex = "2000";
        overlay.style.display = "flex";
        overlay.style.alignItems = "center";
        overlay.style.justifyContent = "center";
        overlay.style.background = "rgba(255,255,255,.72)";
        overlay.style.opacity = "0";
        overlay.style.visibility = "hidden";
        overlay.style.pointerEvents = "none";
        overlay.innerHTML = ""
            + '<div class="app-loading-overlay__panel" role="status" aria-live="polite">'
            + '  <div class="spinner-border app-loading-overlay__spinner" aria-hidden="true"></div>'
            + "  <span>Caricamento in corso...</span>"
            + "</div>";
        document.body.appendChild(overlay);

        const hideOverlay = function () {
            overlay.classList.remove("is-visible");
            overlay.setAttribute("aria-hidden", "true");
            overlay.style.opacity = "0";
            overlay.style.visibility = "hidden";
            overlay.style.pointerEvents = "none";
        };

        const showOverlay = function () {
            overlay.classList.add("is-visible");
            overlay.setAttribute("aria-hidden", "false");
            overlay.style.opacity = "1";
            overlay.style.visibility = "visible";
            overlay.style.pointerEvents = "all";
        };

        window.appLoadingOverlay = {
            show: showOverlay,
            hide: hideOverlay
        };

        // Safety net: ensure overlay is not left visible after navigation/redirect edge cases.
        hideOverlay();

        document.addEventListener("submit", function (event) {
            const form = event.target;
            if (!(form instanceof HTMLFormElement)) {
                return;
            }
            if (form.dataset.noLoadingOverlay === "true") {
                return;
            }
            const method = (form.method || "").toLowerCase();
            if (method !== "get" && method !== "post") {
                return;
            }
            showOverlay();
        }, true);

        document.addEventListener("click", function (event) {
            if (!shouldShowForLinkEvent(event)) {
                return;
            }
            showOverlay();
        }, true);

        window.addEventListener("beforeunload", function () {
            showOverlay();
        });

        window.addEventListener("pageshow", hideOverlay);
        window.addEventListener("load", hideOverlay);
        window.setTimeout(hideOverlay, 0);
    }

    if (document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", installLoadingOverlay);
    } else {
        installLoadingOverlay();
    }
})();
