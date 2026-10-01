document.addEventListener('DOMContentLoaded', () => {
    const statusUrl = `${document.body.dataset.contextPath || ''}/settings/baileys-status`;
    const qrFrame = document.getElementById('baileysQrFrame');
    const readyBox = document.getElementById('baileysReadyBox');
    const badge = document.getElementById('baileysStatusBadge');
    const stateValue = document.getElementById('baileysStateValue');
    const messageBlock = document.getElementById('baileysMessageBlock');
    const messageValue = document.getElementById('baileysMessageValue');
    const startBtn = document.getElementById('startBaileysBtn');
    const stopBtn = document.getElementById('stopBaileysBtn');

    if (!badge || !stateValue) {
        return;
    }

    let pollTimer = null;

    function updateUi(status) {
        const ready = Boolean(status?.ready);
        const reachable = Boolean(status?.reachable);
        const qrRequired = Boolean(status?.qrRequired);
        const state = String(status?.state || 'OFFLINE').trim() || 'OFFLINE';
        const lastError = String(status?.lastError || '').trim();

        stateValue.textContent = state;
        badge.classList.remove('settings-status--ok', 'settings-status--wait', 'settings-status--off');
        if (ready) {
            badge.textContent = 'Connesso';
            badge.classList.add('settings-status--ok');
        } else if (reachable) {
            badge.textContent = 'Da autenticare';
            badge.classList.add('settings-status--wait');
        } else {
            badge.textContent = 'Non attivo';
            badge.classList.add('settings-status--off');
        }

        if (messageBlock && messageValue) {
            messageValue.textContent = lastError;
            messageBlock.classList.toggle('d-none', !lastError);
        }

        if (readyBox) {
            readyBox.classList.toggle('d-none', !ready);
        }
        if (qrFrame) {
            qrFrame.classList.toggle('d-none', ready);
            if (!ready && qrRequired) {
                qrFrame.src = `${statusUrl.replace('/baileys-status', '/whatsapp-qr')}?t=${Date.now()}`;
            }
        }

        if (startBtn) {
            startBtn.disabled = reachable;
        }
        if (stopBtn) {
            stopBtn.disabled = !reachable;
        }
    }

    async function refreshStatus() {
        if (document.hidden) {
            return;
        }
        try {
            const response = await fetch(`${statusUrl}?t=${Date.now()}`, {
                headers: { Accept: 'application/json' },
                cache: 'no-store'
            });
            if (!response.ok) {
                return;
            }
            const status = await response.json();
            updateUi(status);
        } catch (_) {
            // Leave current UI untouched on transient polling errors.
        }
    }

    function schedulePolling() {
        if (pollTimer) {
            window.clearInterval(pollTimer);
        }
        pollTimer = window.setInterval(refreshStatus, 5000);
    }

    document.addEventListener('visibilitychange', () => {
        if (!document.hidden) {
            refreshStatus();
        }
    });

    refreshStatus();
    schedulePolling();
});
