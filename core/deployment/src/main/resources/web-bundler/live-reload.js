let isReco = false;

console.log("web-bundler live-reload is enabled");

function connectToChanges() {
    console.debug("connecting to web-bundler live-reload: " + isReco)
    const eventSource = new EventSource(process.env.LIVE_RELOAD_PATH);
    eventSource.onopen = () => {
        if (isReco) {
            // server is back-on, let's reload to get the latest
            location.reload();
        }

        console.debug("connected to web-bundler live-reload");
    };
    eventSource.addEventListener('bundling-error', e => {
        eventSource.close();
        location.reload();
    });
    eventSource.addEventListener('change', e => {
        if (!e.data) {
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
                        link.parentNode.insertBefore(next, link.nextSibling);
                        return;
                    }
            }
        }
        location.reload();
    });

    eventSource.onerror = (e) => {
        console.debug("web-bundler live-reload connection lost");
        isReco = true;
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
