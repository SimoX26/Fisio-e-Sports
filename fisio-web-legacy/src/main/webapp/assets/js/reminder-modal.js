document.addEventListener('DOMContentLoaded', () => {
    const contextPath = document.body.dataset.contextPath || '';
    const whatsAppConfigured = (document.body.dataset.whatsappConfigured || '').trim() === 'true';
    const modalEl = document.getElementById('reminderModal');
    const dateInput = document.getElementById('reminderDate');
    const appointmentListEl = document.getElementById('reminderAppointmentList');
    const appointmentEmptyEl = document.getElementById('reminderAppointmentEmpty');
    const selectAllBtn = document.getElementById('selectAllReminderAppointmentsBtn');
    const templateInput = document.getElementById('reminderTemplate');
    const previewListEl = document.getElementById('reminderPreviewList');
    const previewEmptyEl = document.getElementById('reminderPreviewEmpty');
    const refreshBtn = document.getElementById('refreshReminderPreviewBtn');
    const sendBtn = document.getElementById('sendReminderBtn');
    const homeOpenBtn = document.getElementById('openHomeReminderModalBtn');
    const closeBtn = document.getElementById('closeReminderModalBtn');
    const cancelBtn = document.getElementById('cancelReminderModalBtn');

    if (!modalEl || !dateInput || !appointmentListEl || typeof bootstrap === 'undefined') {
        return;
    }

    const modal = new bootstrap.Modal(modalEl);
    let recipients = [];
    let preselectedAppointmentIds = new Set();
    let noticeCounter = 0;

    function pad2(value) {
        return String(value).padStart(2, '0');
    }

    function toIsoDate(date) {
        return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
    }

    function defaultReminderTemplate() {
        return "Le ricordiamo l'appuntamento fissato per {giorno} per l'orario {ora inizio - ora fine}.";
    }

    function escapeHtml(value) {
        return String(value || '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    function showNotice(message, variant = 'error') {
        const container = document.getElementById('appNoticeContainer');
        if (!container) {
            return;
        }
        const normalizedVariant = ['success', 'info', 'error'].includes(variant) ? variant : 'error';
        const notice = document.createElement('div');
        notice.className = `app-notice app-notice--${normalizedVariant}`;
        notice.setAttribute('role', normalizedVariant === 'error' ? 'alert' : 'status');
        notice.dataset.noticeId = String(++noticeCounter);
        notice.innerHTML = `
            <div class="app-notice__body">${escapeHtml(message || 'Operazione non riuscita')}</div>
            <button type="button" class="app-notice__close" aria-label="Chiudi notifica">&times;</button>
        `;
        notice.querySelector('.app-notice__close')?.addEventListener('click', () => notice.remove());
        container.appendChild(notice);
        window.setTimeout(() => notice.classList.add('app-notice--visible'), 10);
        window.setTimeout(() => {
            notice.classList.remove('app-notice--visible');
            window.setTimeout(() => notice.remove(), 220);
        }, 5200);
    }

    async function parseApiPayload(response) {
        const text = await response.text();
        if (!text) {
            return {};
        }
        try {
            const parsed = JSON.parse(text);
            return parsed?.data || parsed;
        } catch (error) {
            return {};
        }
    }

    async function parseApiError(response, fallbackMessage) {
        const text = await response.text();
        if (!text) {
            return new Error(fallbackMessage);
        }
        try {
            const parsed = JSON.parse(text);
            return new Error(parsed?.message || parsed?.error || fallbackMessage);
        } catch (error) {
            return new Error(text || fallbackMessage);
        }
    }

    function reminderResultNotice(payload) {
        const processedCount = payload?.processedCount || 0;
        const sentCount = payload?.sentCount || 0;
        const skippedCount = payload?.skippedCount || 0;
        const failedCount = payload?.failedCount || 0;
        return `Promemoria elaborati: ${processedCount}, inviati: ${sentCount}, saltati: ${skippedCount}, falliti: ${failedCount}.`;
    }

    function selectedAppointmentIds() {
        return Array.from(appointmentListEl.querySelectorAll('input[name="reminderAppointment"]:checked'))
            .map((input) => input.value)
            .filter(Boolean);
    }

    function renderMessage(template, recipient) {
        const message = (template || defaultReminderTemplate()).trim() || defaultReminderTemplate();
        return message
            .replaceAll('{nome paziente}', recipient.patientName || '')
            .replaceAll('{giorno}', recipient.dayLabel || '')
            .replaceAll('{ora inizio}', recipient.startTime || '')
            .replaceAll('{ora fine}', recipient.endTime || '')
            .replaceAll('{ora inizio - ora fine}', recipient.timeRange || '');
    }

    function renderAppointments() {
        appointmentListEl.innerHTML = '';
        const hasRecipients = recipients.length > 0;
        appointmentEmptyEl?.classList.toggle('d-none', hasRecipients);

        recipients.forEach((recipient) => {
            const id = String(recipient.appointmentId || '');
            const item = document.createElement('label');
            item.className = 'reminder-appointment-item';
            item.innerHTML = `
                <input type="checkbox" name="reminderAppointment" value="${escapeHtml(id)}">
                <span>
                    <strong>${escapeHtml(recipient.patientName || '-')}</strong>
                    <small>${escapeHtml(recipient.timeRange || '-')}</small>
                </span>
            `;
            const checkbox = item.querySelector('input');
            checkbox.checked = preselectedAppointmentIds.size === 0 || preselectedAppointmentIds.has(id);
            checkbox.addEventListener('change', renderPreview);
            appointmentListEl.appendChild(item);
        });
        renderPreview();
    }

    function renderPreview() {
        if (!previewListEl || !previewEmptyEl) {
            return;
        }
        const selectedIds = new Set(selectedAppointmentIds());
        const selectedRecipients = recipients.filter((recipient) => selectedIds.has(String(recipient.appointmentId || '')));
        previewListEl.innerHTML = '';

        if (selectedRecipients.length === 0) {
            previewEmptyEl.classList.remove('d-none');
            return;
        }

        previewEmptyEl.classList.add('d-none');
        selectedRecipients.forEach((recipient) => {
            const item = document.createElement('div');
            item.className = 'reminder-preview-item';
            item.innerHTML = `
                <div class="reminder-preview-head">
                    <strong>${escapeHtml(recipient.patientName || '-')}</strong>
                    <span>${escapeHtml(recipient.timeRange || '-')}</span>
                </div>
                <div class="reminder-preview-msg">${escapeHtml(renderMessage(templateInput?.value, recipient))}</div>
            `;
            previewListEl.appendChild(item);
        });
    }

    async function loadAppointments() {
        const dateValue = dateInput.value;
        if (!dateValue) {
            return;
        }

        const query = new URLSearchParams({
            reminderPreview: 'true',
            date: dateValue
        });
        if (templateInput && templateInput.value.trim()) {
            query.set('template', templateInput.value.trim());
        }

        try {
            const response = await fetch(`${contextPath}/calendar?${query.toString()}`, {
                headers: { Accept: 'application/json' }
            });
            if (!response.ok) {
                throw new Error('Impossibile caricare gli appuntamenti.');
            }
            const payload = await parseApiPayload(response);
            recipients = Array.isArray(payload?.recipients) ? payload.recipients : [];
            if (templateInput && !templateInput.value.trim()) {
                templateInput.value = payload?.template || payload?.defaultTemplate || defaultReminderTemplate();
            }
            renderAppointments();
        } catch (error) {
            recipients = [];
            renderAppointments();
            showNotice(error.message || 'Errore durante il caricamento dei promemoria.', 'error');
        }
    }

    function openForDate(dateIso) {
        preselectedAppointmentIds = new Set();
        dateInput.value = dateIso || toIsoDate(new Date());
        if (templateInput) {
            templateInput.value = '';
        }
        recipients = [];
        renderAppointments();
        loadAppointments();
        modal.show();
    }

    function openForAppointment(dateIso, appointmentId) {
        preselectedAppointmentIds = new Set([String(appointmentId || '')]);
        dateInput.value = dateIso || toIsoDate(new Date());
        if (templateInput) {
            templateInput.value = '';
        }
        recipients = [];
        renderAppointments();
        loadAppointments();
        modal.show();
    }

    async function sendSelected() {
        const dateValue = dateInput.value;
        const selectedIds = selectedAppointmentIds();
        if (!dateValue) {
            showNotice('Seleziona un giorno.', 'info');
            return;
        }
        if (selectedIds.length === 0) {
            showNotice('Seleziona almeno un appuntamento.', 'info');
            return;
        }
        if (!whatsAppConfigured) {
            showNotice('Servizio WhatsApp non configurato per questo account. Contattare l\'amministratore di sistema.', 'info');
            return;
        }

        const data = new URLSearchParams();
        data.append('action', 'send-reminders');
        data.append('date', dateValue);
        data.append('template', templateInput && templateInput.value.trim() ? templateInput.value.trim() : defaultReminderTemplate());
        selectedIds.forEach((id) => data.append('appointmentId', id));

        try {
            const response = await fetch(`${contextPath}/calendar`, {
                method: 'POST',
                body: data
            });
            if (!response.ok) {
                throw await parseApiError(response, 'Errore durante l\'invio dei promemoria.');
            }
            const payload = await parseApiPayload(response);
            showNotice(reminderResultNotice(payload), payload?.failedCount > 0 ? 'info' : 'success');
            modal.hide();
        } catch (error) {
            showNotice(error.message || 'Errore durante l\'invio dei promemoria.', 'error');
        }
    }

    dateInput.addEventListener('change', () => {
        preselectedAppointmentIds = new Set();
        loadAppointments();
    });
    templateInput?.addEventListener('input', renderPreview);
    refreshBtn?.addEventListener('click', loadAppointments);
    sendBtn?.addEventListener('click', sendSelected);
    selectAllBtn?.addEventListener('click', () => {
        const checkboxes = Array.from(appointmentListEl.querySelectorAll('input[name="reminderAppointment"]'));
        const shouldCheck = checkboxes.some((checkbox) => !checkbox.checked);
        checkboxes.forEach((checkbox) => {
            checkbox.checked = shouldCheck;
        });
        renderPreview();
    });
    homeOpenBtn?.addEventListener('click', () => openForDate(toIsoDate(new Date())));
    closeBtn?.addEventListener('click', () => modal.hide());
    cancelBtn?.addEventListener('click', () => modal.hide());
    window.fisioReminderModal = {
        openForDate,
        openForAppointment
    };
});
