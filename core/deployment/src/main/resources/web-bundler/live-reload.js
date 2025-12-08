console.log("web-bundler live-reload is enabled");

// Firefox calls onerror when navigating causing weird reload issues
// When the page is unloaded, we stop acting on events
let pageClosed = false;
window.addEventListener('beforeunload', () => { pageClosed = true; });

function connectToChanges() {
    console.debug("connecting to web-bundler live-reload");
    const eventSource = new EventSource(process.env.LIVE_RELOAD_PATH);
    eventSource.onopen = () => {
        console.debug("connected to web-bundler live-reload");
    };
    eventSource.addEventListener('bundling-error', e => {
        if (pageClosed) {
            return;
        }
        eventSource.close();
        location.reload();
    });
    eventSource.addEventListener('change', e => {
        if (!e.data || pageClosed) {
            return;
        }
        const {added, removed, updated} = JSON.parse(e.data);
        const updatedCss = updated.filter(p => p.endsWith(".css")).length;
        if (!added.length && !removed.length && updated.length > 0 && updatedCss === updated.length) {
            for (const link of document.getElementsByTagName("link")) {
                const url = new URL(link.href);
                for (const css of updated)
                    if (url.host === location.host && url.pathname === css) {
                        console.log("Live-reload: " + css);
                        const next = link.cloneNode();
                        next.href = css + '?' + Math.random().toString(36).slice(2);
                        next.onload = () => link.remove();
                        next.onerror = (e) => {
                            next.remove();
                            console.error(e);
                        };
                        link.parentNode.insertBefore(next, link.nextSibling);g
                        return;
                    }
            }
        }
        location.reload();
    });

    eventSource.onerror = (e) => {
        if (pageClosed) {
           return;
        }
        console.debug("web-bundler live-reload connection lost");
        location.reload();
    };
}

fetch(process.env.LIVE_RELOAD_PATH)
    .then(response => {
        if (response.status === 429) {
            return Promise.reject(new Error("There are too many live-reload open connections."));
        }
        return response;
    })
    .then(connectToChanges)
    .catch(error => {
        console.error('Error:', error.message);
    });
