document.addEventListener('DOMContentLoaded', () => {
    const DEFAULT_APPOINTMENT_DURATION_MINUTES = 60;
    const APPOINTMENT_STEP_MINUTES = 15;
    const whatsAppConfigured = (document.body.dataset.whatsappConfigured || '').trim() === 'true';

    function toLocalDateTimeInputValue(date) {
        const pad = (n) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }

    function addMinutes(date, minutes) {
        return new Date(date.getTime() + minutes * 60 * 1000);
    }

    function startOfDay(date) {
        const dayStart = new Date(date);
        dayStart.setHours(0, 0, 0, 0);
        return dayStart;
    }

    function nextDay(date) {
        const next = new Date(date);
        next.setDate(next.getDate() + 1);
        return next;
    }

    function ceilToQuarterHour(date) {
        const rounded = new Date(date);
        const minutes = rounded.getMinutes();
        const remainder = minutes % APPOINTMENT_STEP_MINUTES;
        if (remainder !== 0) {
            rounded.setMinutes(minutes + (APPOINTMENT_STEP_MINUTES - remainder));
        }
        rounded.setSeconds(0, 0);
        return rounded;
    }

    const contextPath = document.body.dataset.contextPath || '';
    const urlSearchParams = new URLSearchParams(window.location.search);
    const highlightAppointmentIdParam = (urlSearchParams.get('highlightAppointmentId') || '').trim();
    const highlightPatientIdParam = (urlSearchParams.get('highlightPatientId') || '').trim();
    const highlightAppointmentId = /^\d+$/.test(highlightAppointmentIdParam)
        ? Number.parseInt(highlightAppointmentIdParam, 10)
        : null;
    const highlightPatientId = /^\d+$/.test(highlightPatientIdParam)
        ? Number.parseInt(highlightPatientIdParam, 10)
        : null;
    const calendarEl = document.getElementById('calendar');
    const saveButton = document.getElementById('saveAppointmentBtn');
    const openModalButton = document.getElementById('openAppointmentModalBtn');
    const appointmentModalEl = document.getElementById('appointmentModal');
    const eventModalEl = document.getElementById('eventModal');
    const completeTreatmentModalEl = document.getElementById('completeTreatmentModal');
    const confirmDeleteModalEl = document.getElementById('confirmDeleteModal');
    const reminderModalEl = document.getElementById('reminderModal');
    const startInput = document.getElementById('start');
    const endInput = document.getElementById('end');
    const appointmentDateInput = document.getElementById('appointmentDate');
    const timeSelectionSection = document.getElementById('timeSelectionSection');
    const startTimeNativeInput = document.getElementById('startTimeNative');
    const endTimeNativeInput = document.getElementById('endTimeNative');
    const notesInput = document.getElementById('notes');
    const appointmentFormErrorEl = document.getElementById('appointmentFormError');
    const patientNameLabel = document.getElementById('patientNameLabel');
    const patientNameInput = document.getElementById('patientName');
    const patientPhoneInput = document.getElementById('patientPhone');
    const patientSuggestionsMenuEl = document.getElementById('patientSuggestionsMenu');
    const allDayInput = document.getElementById('allDay');
    const nonTreatmentEventInput = document.getElementById('nonTreatmentEvent');
    const appointmentModalTitleEl = document.getElementById('appointmentModalTitle');
    const modalTitleEl = document.getElementById('modalTitle');
    const modalTitleTimeEl = document.getElementById('modalTitleTime');
    const modalNotesEl = document.getElementById('modalNotes');
    const openPatientDetailsBtn = document.getElementById('openPatientDetailsBtn');
    const sendSingleReminderBtn = document.getElementById('sendSingleReminderBtn');
    const editAppointmentBtn = document.getElementById('editAppointmentBtn');
    const deleteAppointmentBtn = document.getElementById('deleteAppointmentBtn');
    const completeAppointmentBtn = document.getElementById('completeAppointmentBtn');
    const eventModalStateHintEl = document.getElementById('eventModalStateHint');
    const treatmentPlanTitleInput = document.getElementById('treatmentPlanTitle');
    const treatmentGoalsInput = document.getElementById('treatmentGoals');
    const treatmentFrequencyPerWeekInput = document.getElementById('treatmentFrequencyPerWeek');
    const treatmentTotalSessionsPlannedInput = document.getElementById('treatmentTotalSessionsPlanned');
    const treatmentExpectedEndDateInput = document.getElementById('treatmentExpectedEndDate');
    const treatmentPainScorePreInput = document.getElementById('treatmentPainScorePre');
    const treatmentPainScorePostInput = document.getElementById('treatmentPainScorePost');
    const treatmentSessionOutcomeInput = document.getElementById('treatmentSessionOutcome');
    const treatmentHomeExercisesInput = document.getElementById('treatmentHomeExercises');
    const treatmentNotesInput = document.getElementById('treatmentNotes');
    const confirmCompleteTreatmentBtn = document.getElementById('confirmCompleteTreatmentBtn');
    const confirmDeleteAppointmentBtn = document.getElementById('confirmDeleteAppointmentBtn');
    const reminderDayLabelEl = document.getElementById('reminderDayLabel');
    const reminderDateInput = document.getElementById('reminderDate');
    const reminderAppointmentIdInput = document.getElementById('reminderAppointmentId');
    const reminderTemplateInput = document.getElementById('reminderTemplate');
    const reminderPreviewListEl = document.getElementById('reminderPreviewList');
    const reminderPreviewEmptyEl = document.getElementById('reminderPreviewEmpty');
    const sendReminderBtn = document.getElementById('sendReminderBtn');
    const refreshReminderPreviewBtn = document.getElementById('refreshReminderPreviewBtn');
    const searchHighlightNoticeEl = document.getElementById('searchHighlightNotice');
    let reminderTemplateSaveDebounce = null;

    if (!calendarEl || !saveButton || !appointmentModalEl || typeof FullCalendar === 'undefined') {
        return;
    }

    if (searchHighlightNoticeEl) {
        const hasHighlightFromSearch = highlightAppointmentId !== null || highlightPatientId !== null;
        searchHighlightNoticeEl.classList.toggle('d-none', !hasHighlightFromSearch);
    }

    const appointmentModal = new bootstrap.Modal(appointmentModalEl);
    const eventModal = eventModalEl ? new bootstrap.Modal(eventModalEl) : null;
    const completeTreatmentModal = completeTreatmentModalEl ? new bootstrap.Modal(completeTreatmentModalEl) : null;
    const confirmDeleteModal = confirmDeleteModalEl ? new bootstrap.Modal(confirmDeleteModalEl) : null;
    const reminderModal = reminderModalEl ? new bootstrap.Modal(reminderModalEl) : null;
    let selectedEvent = null;
    let editingAppointmentId = null;
    let currentHeightMode = 'auto';
    let patientSuggestionDebounce = null;
    let patientSuggestionItems = [];
    let highlightedSuggestionIndex = -1;
    let reminderPreviewData = [];
    let noticeCounter = 0;

    function pad2(value) {
        return String(value).padStart(2, '0');
    }

    function toIsoDate(date) {
        return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
    }

    function fromDateAndTime(dateIso, timeValue) {
        return new Date(`${dateIso}T${timeValue}:00`);
    }

    function toTimeValue(date) {
        return `${pad2(date.getHours())}:${pad2(date.getMinutes())}`;
    }

    function toggleTimeSelectionVisibility() {
        if (!timeSelectionSection || !allDayInput) {
            return;
        }
        timeSelectionSection.classList.toggle('d-none', allDayInput.checked);
    }

    function isNonTreatmentEventEnabled() {
        return Boolean(nonTreatmentEventInput?.checked);
    }

    function updatePatientFieldUi() {
        const nonTreatmentEvent = isNonTreatmentEventEnabled();
        if (patientNameLabel) {
            patientNameLabel.textContent = nonTreatmentEvent ? 'Titolo' : 'Paziente';
        }
        if (patientNameInput) {
            patientNameInput.placeholder = nonTreatmentEvent
                ? "Inserisci il titolo dell'evento"
                : 'Nome e cognome paziente';
        }
    }

    function syncVisibleTimeControlsFromDateTimeInputs() {
        if (!startInput || !endInput || !appointmentDateInput || !startTimeNativeInput || !endTimeNativeInput) {
            return;
        }
        if (!startInput.value || !endInput.value) {
            return;
        }

        const startDate = new Date(startInput.value);
        const endDate = new Date(endInput.value);
        if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
            return;
        }

        appointmentDateInput.value = toIsoDate(startDate);
        startTimeNativeInput.value = toTimeValue(startDate);
        endTimeNativeInput.value = toTimeValue(endDate);
    }

    function syncDateTimeInputsFromVisibleControls(preserveSelectedEnd = true) {
        if (!startInput || !endInput || !appointmentDateInput || !startTimeNativeInput || !endTimeNativeInput) {
            return;
        }

        const dateIso = appointmentDateInput.value;
        const startTime = startTimeNativeInput.value;
        if (!dateIso || !startTime) {
            return;
        }

        const startDate = fromDateAndTime(dateIso, startTime);
        let endDate = addMinutes(startDate, DEFAULT_APPOINTMENT_DURATION_MINUTES);
        if (preserveSelectedEnd && endTimeNativeInput.value) {
            const selectedEnd = fromDateAndTime(dateIso, endTimeNativeInput.value);
            if (selectedEnd > startDate) {
                endDate = selectedEnd;
            }
        }

        startInput.value = toLocalDateTimeInputValue(startDate);
        endInput.value = toLocalDateTimeInputValue(endDate);
        endTimeNativeInput.value = toTimeValue(endDate);
    }

    function showGlobalNotice(message, variant = 'error') {
        const container = document.getElementById('appNoticeContainer');
        if (!container) {
            return;
        }

        const normalizedVariant = ['success', 'info', 'error'].includes(variant) ? variant : 'error';
        const notice = document.createElement('div');
        notice.className = `app-notice app-notice--${normalizedVariant}`;
        notice.setAttribute('role', normalizedVariant === 'error' ? 'alert' : 'status');
        notice.setAttribute('aria-live', normalizedVariant === 'error' ? 'assertive' : 'polite');
        notice.dataset.noticeId = String(++noticeCounter);
        notice.innerHTML = `
            <div class="app-notice__body">${escapeHtml(message || 'Operazione non riuscita')}</div>
            <button type="button" class="app-notice__close" aria-label="Chiudi notifica">&times;</button>
        `;

        const closeButton = notice.querySelector('.app-notice__close');
        closeButton?.addEventListener('click', () => {
            notice.remove();
        });

        container.appendChild(notice);
        window.setTimeout(() => {
            notice.classList.add('app-notice--visible');
        }, 10);

        window.setTimeout(() => {
            notice.classList.remove('app-notice--visible');
            window.setTimeout(() => notice.remove(), 220);
        }, 5200);
    }

    function applyViewClass(calendarInstance) {
        const viewType = calendarInstance.view ? calendarInstance.view.type : '';
        document.body.classList.toggle('calendar-view-day', viewType === 'timeGridDay');
        document.body.classList.toggle('calendar-view-week', viewType === 'timeGridWeek');
        document.body.classList.toggle('calendar-view-month', viewType === 'dayGridMonth');
    }

    function applyDesktopWeekFillMode(calendarInstance) {
        const desiredHeight = 'auto';

        if (desiredHeight !== currentHeightMode) {
            currentHeightMode = desiredHeight;
            calendarInstance.setOption('height', desiredHeight);
        }

        document.body.classList.remove('calendar-week-fill');
        window.requestAnimationFrame(() => calendarInstance.updateSize());
    }


    function toIsoDateValue(date) {
        const pad = (n) => String(n).padStart(2, '0');
        return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
    }

    function toItalianDateLabel(date) {
        return date.toLocaleDateString('it-IT');
    }

    function toItalianTimeLabel(date) {
        return date.toLocaleTimeString('it-IT', {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        });
    }

    function toReminderDayLabel(date) {
        return date.toLocaleDateString('it-IT', {
            weekday: 'long',
            day: '2-digit',
            month: 'long',
            year: 'numeric'
        });
    }

    function normalizeOptionalText(value) {
        const normalized = (value || '').trim();
        return normalized || null;
    }

    function parseOptionalInteger(value, fieldLabel) {
        const normalized = (value || '').trim();
        if (!normalized) {
            return null;
        }
        const parsed = Number.parseInt(normalized, 10);
        if (!Number.isFinite(parsed)) {
            throw new Error(`${fieldLabel} non valido`);
        }
        return parsed;
    }

    function hideAppointmentFormError() {
        if (!appointmentFormErrorEl) {
            return;
        }
        appointmentFormErrorEl.classList.add('d-none');
        appointmentFormErrorEl.textContent = '';
    }

    function showAppointmentFormError(message) {
        if (!appointmentFormErrorEl) {
            showGlobalNotice(message, 'error');
            return;
        }
        appointmentFormErrorEl.textContent = message || "Errore durante il salvataggio dell'appuntamento.";
        appointmentFormErrorEl.classList.remove('d-none');
    }

    async function parseApiError(response, fallbackMessage) {
        const text = await response.text();
        let errorMessage = fallbackMessage || 'Operazione non riuscita';
        let errorCode = '';

        if (text) {
            try {
                const parsed = JSON.parse(text);
                if (parsed?.message) {
                    errorMessage = parsed.message;
                } else if (parsed?.error) {
                    errorMessage = parsed.error;
                }
                if (parsed?.errorCode) {
                    errorCode = String(parsed.errorCode);
                } else if (parsed?.code) {
                    errorCode = String(parsed.code);
                }
            } catch (e) {
                errorMessage = text;
            }
        }

        if (errorCode === 'TIME_SLOT_NOT_AVAILABLE') {
            errorMessage = 'Questo orario è già occupato. Scegli uno slot diverso.';
        }

        const error = new Error(errorMessage);
        error.code = errorCode;
        return error;
    }

    async function parseApiPayload(response) {
        const payload = await response.json();
        if (Object.prototype.hasOwnProperty.call(payload || {}, 'success')) {
            return payload?.data ?? null;
        }
        return payload;
    }

    function escapeHtml(value) {
        return String(value || '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#39;');
    }

    function defaultReminderTemplate() {
        return "Le ricordiamo l'appuntamento fissato per {giorno} per l'orario {ora inizio - ora fine}.";
    }

    function renderReminderMessage(template, recipient) {
        const rawTemplate = (template || defaultReminderTemplate()).trim();
        const message = rawTemplate || defaultReminderTemplate();
        return message
            .replaceAll('{nome paziente}', recipient.patientName || '')
            .replaceAll('{giorno}', recipient.dayLabel || '')
            .replaceAll('{ora inizio}', recipient.startTime || '')
            .replaceAll('{ora fine}', recipient.endTime || '')
            .replaceAll('{ora inizio - ora fine}', recipient.timeRange || '');
    }

    function renderReminderPreview(template) {
        if (!reminderPreviewListEl || !reminderPreviewEmptyEl) {
            return;
        }

        reminderPreviewListEl.innerHTML = '';
        if (!Array.isArray(reminderPreviewData) || reminderPreviewData.length === 0) {
            reminderPreviewEmptyEl.classList.remove('d-none');
            return;
        }

        reminderPreviewEmptyEl.classList.add('d-none');
        reminderPreviewData.forEach((recipient) => {
            const item = document.createElement('div');
            item.className = 'reminder-preview-item';
            const message = renderReminderMessage(template, recipient);
            item.innerHTML = `
                <div class="reminder-preview-head">
                    <strong>${escapeHtml(recipient.patientName || '-')}</strong>
                    <span>${escapeHtml(recipient.timeRange || '-')}</span>
                </div>
                <div class="reminder-preview-msg">${escapeHtml(message)}</div>
            `;
            reminderPreviewListEl.appendChild(item);
        });
    }

    async function loadReminderPreview() {
        if (!reminderDateInput || !reminderDateInput.value) {
            return;
        }
        const dateValue = reminderDateInput.value;
        const query = new URLSearchParams({
            reminderPreview: 'true',
            date: dateValue
        });
        if (reminderTemplateInput && reminderTemplateInput.value.trim()) {
            query.set('template', reminderTemplateInput.value.trim());
        }
        if (reminderAppointmentIdInput && reminderAppointmentIdInput.value.trim()) {
            query.set('appointmentId', reminderAppointmentIdInput.value.trim());
        }

        try {
            const response = await fetch(
                `${contextPath}/calendar?${query.toString()}`,
                { headers: { Accept: 'application/json' } }
            );
            if (!response.ok) {
                throw new Error('Impossibile caricare i destinatari del promemoria');
            }
            const payload = await parseApiPayload(response);
            reminderPreviewData = Array.isArray(payload?.recipients) ? payload.recipients : [];
            if (reminderTemplateInput && !reminderTemplateInput.value.trim()) {
                reminderTemplateInput.value = payload?.template || payload?.defaultTemplate || defaultReminderTemplate();
            }
            renderReminderPreview(reminderTemplateInput ? reminderTemplateInput.value : defaultReminderTemplate());
        } catch (error) {
            reminderPreviewData = [];
            renderReminderPreview(reminderTemplateInput ? reminderTemplateInput.value : defaultReminderTemplate());
            showGlobalNotice(error.message || 'Errore durante il caricamento dei promemoria', 'error');
        }
    }

    function openReminderModalForDate(dateIso) {
        if (window.fisioReminderModal) {
            window.fisioReminderModal.openForDate(dateIso);
            return;
        }
        if (!reminderModal || !reminderDateInput) {
            return;
        }
        reminderDateInput.value = dateIso;
        if (reminderAppointmentIdInput) {
            reminderAppointmentIdInput.value = '';
        }
        const selectedDate = new Date(`${dateIso}T00:00:00`);
        if (reminderDayLabelEl) {
            reminderDayLabelEl.textContent = Number.isNaN(selectedDate.getTime()) ? dateIso : toReminderDayLabel(selectedDate);
        }
        if (reminderTemplateInput) {
            reminderTemplateInput.value = '';
        }
        reminderPreviewData = [];
        renderReminderPreview(reminderTemplateInput ? reminderTemplateInput.value : defaultReminderTemplate());
        loadReminderPreview();
        reminderModal.show();
    }

    function openReminderModalForEvent(event) {
        if (window.fisioReminderModal && event && event.start) {
            window.fisioReminderModal.openForAppointment(toIsoDateValue(event.start), event.id);
            if (eventModal) {
                eventModal.hide();
            }
            return;
        }
        if (!event || !event.start || !reminderModal || !reminderDateInput) {
            return;
        }
        const dateIso = toIsoDateValue(event.start);
        reminderDateInput.value = dateIso;
        if (reminderAppointmentIdInput) {
            reminderAppointmentIdInput.value = String(event.id || '');
        }
        if (reminderDayLabelEl) {
            reminderDayLabelEl.textContent = event.title || toReminderDayLabel(event.start);
        }
        if (reminderTemplateInput) {
            reminderTemplateInput.value = '';
        }
        reminderPreviewData = [];
        renderReminderPreview(reminderTemplateInput ? reminderTemplateInput.value : defaultReminderTemplate());
        loadReminderPreview();
        if (eventModal) {
            eventModal.hide();
        }
        reminderModal.show();
    }

    function renderReminderButtons(calendarInstance) {
        if (!calendarInstance || !calendarInstance.view || calendarInstance.view.type !== 'timeGridWeek') {
            return;
        }

        const headerCells = calendarEl.querySelectorAll('.fc-timegrid .fc-col-header-cell[data-date]');
        headerCells.forEach((cell) => {
            if (cell.querySelector('.calendar-reminder-btn')) {
                return;
            }
            const dateIso = cell.getAttribute('data-date');
            const anchor = cell.querySelector('.fc-col-header-cell-cushion');
            if (!dateIso || !anchor) {
                return;
            }

            const button = document.createElement('button');
            button.type = 'button';
            button.className = 'calendar-reminder-btn';
            button.title = 'Invia promemoria del giorno';
            button.setAttribute('aria-label', `Invia promemoria per ${dateIso}`);
            button.innerHTML = `
                <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
                    <path d="M12 3a5 5 0 0 0-5 5v2.8l-1.7 2.8a1 1 0 0 0 .85 1.5h11.7a1 1 0 0 0 .85-1.5L17 10.8V8a5 5 0 0 0-5-5Zm0 18a2.5 2.5 0 0 0 2.45-2h-4.9A2.5 2.5 0 0 0 12 21Z"></path>
                </svg>
            `;
            button.addEventListener('click', (event) => {
                event.preventDefault();
                event.stopPropagation();
                openReminderModalForDate(dateIso);
            });
            anchor.appendChild(button);
        });
    }

    function hidePatientSuggestions() {
        if (!patientSuggestionsMenuEl) {
            return;
        }
        patientSuggestionsMenuEl.classList.add('d-none');
        patientSuggestionsMenuEl.innerHTML = '';
        patientSuggestionItems = [];
        highlightedSuggestionIndex = -1;
    }

    function selectPatientSuggestion(name) {
        if (!patientNameInput) {
            return;
        }
        patientNameInput.value = name || '';
        hidePatientSuggestions();
    }

    function renderPatientSuggestions(names) {
        if (!patientSuggestionsMenuEl) {
            return;
        }

        patientSuggestionsMenuEl.innerHTML = '';
        patientSuggestionItems = Array.isArray(names) ? names : [];
        highlightedSuggestionIndex = -1;

        if (!patientSuggestionItems.length) {
            patientSuggestionsMenuEl.classList.add('d-none');
            return;
        }

        patientSuggestionItems.forEach((name, index) => {
            const item = document.createElement('button');
            item.type = 'button';
            item.className = 'patient-suggestion-item';
            item.setAttribute('role', 'option');
            item.setAttribute('data-index', String(index));
            item.textContent = name;
            item.addEventListener('mousedown', (event) => {
                event.preventDefault();
                selectPatientSuggestion(name);
            });
            patientSuggestionsMenuEl.appendChild(item);
        });

        patientSuggestionsMenuEl.classList.remove('d-none');
    }

    function highlightSuggestion(index) {
        if (!patientSuggestionsMenuEl || !patientSuggestionItems.length) {
            return;
        }

        const normalizedIndex = ((index % patientSuggestionItems.length) + patientSuggestionItems.length) % patientSuggestionItems.length;
        highlightedSuggestionIndex = normalizedIndex;

        patientSuggestionsMenuEl.querySelectorAll('.patient-suggestion-item').forEach((item) => {
            item.classList.remove('active');
            if (Number(item.dataset.index) === normalizedIndex) {
                item.classList.add('active');
                item.scrollIntoView({ block: 'nearest' });
            }
        });
    }

    async function fetchPatientSuggestions(rawQuery) {
        if (isNonTreatmentEventEnabled()) {
            hidePatientSuggestions();
            return;
        }
        const query = (rawQuery || '').trim();
        if (!query || query.length < 1) {
            hidePatientSuggestions();
            return;
        }
        try {
            const response = await fetch(
                `${contextPath}/calendar?patients=true&q=${encodeURIComponent(query)}`,
                { headers: { Accept: 'application/json' } }
            );
            if (!response.ok) {
                throw new Error('Impossibile caricare suggerimenti pazienti');
            }
            const names = await response.json();
            renderPatientSuggestions(Array.isArray(names) ? names : []);
        } catch (error) {
            hidePatientSuggestions();
        }
    }

    function schedulePatientSuggestions(query) {
        if (patientSuggestionDebounce) {
            clearTimeout(patientSuggestionDebounce);
        }
        patientSuggestionDebounce = setTimeout(() => {
            fetchPatientSuggestions(query);
        }, 180);
    }

    function resolveInitialCalendarConfig() {
        const searchParams = new URLSearchParams(window.location.search);
        const supportedViews = ['timeGridDay', 'timeGridWeek', 'dayGridMonth'];
        const requestedView = searchParams.get('view');
        const requestedDate = searchParams.get('date');

        let initialView = 'timeGridWeek';
        let initialDate = null;

        if (supportedViews.includes(requestedView)) {
            initialView = requestedView;
        }

        if (requestedDate === 'today') {
            initialDate = new Date();
        } else if (/^\d{4}-\d{2}-\d{2}$/.test(requestedDate || '')) {
            const parsedDate = new Date(`${requestedDate}T00:00:00`);
            if (!Number.isNaN(parsedDate.getTime())) {
                initialDate = parsedDate;
            }
        }

        return { initialView, initialDate };
    }

    function resetAppointmentForm(startDate, endDate) {
        editingAppointmentId = null;
        if (appointmentModalTitleEl) {
            appointmentModalTitleEl.innerText = 'Nuovo appuntamento';
        }
        if (patientNameInput) {
            patientNameInput.value = '';
            patientNameInput.readOnly = false;
        }
        if (patientPhoneInput) {
            patientPhoneInput.value = '';
            patientPhoneInput.readOnly = false;
        }
        if (nonTreatmentEventInput) {
            nonTreatmentEventInput.checked = false;
        }
        updatePatientFieldUi();
        hidePatientSuggestions();
        if (allDayInput) {
            allDayInput.checked = false;
        }
        toggleTimeSelectionVisibility();
        startInput.value = toLocalDateTimeInputValue(startDate);
        endInput.value = toLocalDateTimeInputValue(endDate);
        syncVisibleTimeControlsFromDateTimeInputs();
        if (notesInput) {
            notesInput.value = '';
            notesInput.readOnly = false;
        }
    }

    function openNewAppointmentModal(prefill = {}) {
        const now = new Date();
        const rounded = ceilToQuarterHour(now);
        const end = addMinutes(rounded, DEFAULT_APPOINTMENT_DURATION_MINUTES);

        resetAppointmentForm(rounded, end);

        if (patientNameInput) {
            patientNameInput.value = prefill.patientName || '';
        }
        if (patientPhoneInput) {
            patientPhoneInput.value = prefill.patientPhone || '';
        }

        appointmentModal.show();
    }

    const { initialView, initialDate } = resolveInitialCalendarConfig();

    const calendarConfig = {
        locale: 'it',
        allDayText: 'Tutto il giorno',
        buttonText: {
            today: 'oggi',
            day: 'giorno',
            week: 'settimana',
            month: 'mese'
        },
        firstDay: 1,
        height: 'auto',
        expandRows: true,
        stickyHeaderDates: true,
        headerToolbar: {
            left: 'prev,next today',
            center: 'title',
            right: 'timeGridDay,timeGridWeek,dayGridMonth'
        },
        titleRangeSeparator: ' - ',
        initialView,
        views: {
            timeGridWeek: {
                titleFormat: { day: 'numeric', month: 'long', year: 'numeric' }
            }
        },
        slotDuration: '00:15:00',
        snapDuration: '00:15:00',
        nowIndicator: true,
        slotMinTime: '08:00:00',
        slotMaxTime: '21:00:00',
        scrollTime: '08:00:00',
        selectable: true,
        editable: false,
        displayEventTime: true,
        eventTimeFormat: {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        },
        slotLabelFormat: {
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        },
        slotLabelInterval: '01:00',
        eventContent(arg) {
            const timeText = arg.timeText || '';
            const title = arg.event.title || '';
            if (arg.view && arg.view.type === 'dayGridMonth') {
                return {
                    html: `
                        <span class="fc-event-time-inline">${timeText}</span>
                        <span class="fc-event-title-inline">${title}</span>
                    `
                };
            }
            return {
                html: `
                    <div class="fc-event-time-line">${timeText}</div>
                    <div class="fc-event-title-line">${title}</div>
                `
            };
        },
        eventDidMount(info) {
            const state = String(info.event.extendedProps?.state || '').toUpperCase();
            const isNonTreatmentEvent = Boolean(info.event.extendedProps?.nonTreatmentEvent);
            const eventEnd = info.event.end || info.event.start;
            const now = new Date();
            const isPast = eventEnd instanceof Date && !Number.isNaN(eventEnd.getTime()) && eventEnd.getTime() < now.getTime();
            const isPastCompletedTreatment = !isNonTreatmentEvent && isPast && state === 'COMPLETED';

            let background = '#eaf1fb';
            let backgroundHover = '#d5e2f6';
            let border = 'var(--calendar-event-border)';
            let text = '#1f2d3d';

            // Priority:
            // 1) generic/non-treatment events -> neutral gray
            // 2) past + completed treatment events -> green
            // 3) all other events -> existing default blue palette
            if (isNonTreatmentEvent) {
                background = '#f1f3f5';
                backgroundHover = '#e9ecef';
                border = '#c9ced6';
                text = '#4b5563';
            } else if (isPastCompletedTreatment) {
                background = '#e6f4ea';
                backgroundHover = '#d9eee1';
                border = '#8bc49a';
                text = '#1f8f47';
            }

            info.el.classList.add('calendar-event--custom-color');
            info.el.style.setProperty('--event-bg', background);
            info.el.style.setProperty('--event-bg-hover', backgroundHover);
            info.el.style.setProperty('--event-border', border);
            info.el.style.setProperty('--event-text', text);
            info.el.style.setProperty('background', background, 'important');
            info.el.style.setProperty('background-image', 'none', 'important');
            info.el.style.setProperty('border', `1px solid ${border}`, 'important');
            info.el.style.setProperty('color', text, 'important');
            info.el.style.setProperty('box-shadow', 'none', 'important');

            const eventId = Number.parseInt(String(info.event.id || ''), 10);
            const eventPatientId = Number.parseInt(String(info.event.extendedProps?.patientId || ''), 10);
            const matchesAppointment = Number.isFinite(eventId) && highlightAppointmentId !== null && eventId === highlightAppointmentId;
            const matchesPatient = Number.isFinite(eventPatientId) && highlightPatientId !== null && eventPatientId === highlightPatientId;
            if (matchesAppointment || matchesPatient) {
                info.el.style.setProperty('border', '2px solid #f59f00', 'important');
                info.el.style.setProperty('box-shadow', '0 0 0 2px rgba(245, 159, 0, .22)', 'important');
            }
        },
        datesSet() {
            applyViewClass(calendar);
            applyDesktopWeekFillMode(calendar);
            renderReminderButtons(calendar);
        },
        events: {
            url: contextPath + '/calendar',
            method: 'GET',
            extraParams: { events: 'true' }
        },
        loading(isLoading) {
            if (!window.appLoadingOverlay) {
                return;
            }
            if (isLoading) {
                window.appLoadingOverlay.show();
            } else {
                window.appLoadingOverlay.hide();
            }
        },
        select(info) {
            const startDate = ceilToQuarterHour(new Date(info.start));
            const defaultEndDate = addMinutes(startDate, DEFAULT_APPOINTMENT_DURATION_MINUTES);
            resetAppointmentForm(startDate, defaultEndDate);
            appointmentModal.show();
        },
        eventClick(info) {
            info.jsEvent.preventDefault();
            const event = info.event;
            selectedEvent = event;
            const state = (event.extendedProps.state || '').toUpperCase();
            const isCompleted = state === 'COMPLETED';
            const isAllDay = Boolean(event.allDay || event.extendedProps.allDay);
            const isNonTreatmentEvent = Boolean(event.extendedProps.nonTreatmentEvent);

            if (modalTitleEl) {
                modalTitleEl.innerText = event.title || '';
            }
            if (openPatientDetailsBtn) {
                const patientId = event.extendedProps.patientId;
                if (patientId) {
                    openPatientDetailsBtn.href = `${contextPath}/address-book?openPatientId=${encodeURIComponent(String(patientId))}`;
                    openPatientDetailsBtn.classList.remove('d-none');
                } else {
                    openPatientDetailsBtn.href = '#';
                    openPatientDetailsBtn.classList.add('d-none');
                }
            }
            if (sendSingleReminderBtn) {
                const canSendSingleReminder = !isCompleted && !isAllDay && !isNonTreatmentEvent && event.extendedProps.patientId;
                if (canSendSingleReminder) {
                    sendSingleReminderBtn.classList.remove('d-none');
                } else {
                    sendSingleReminderBtn.classList.add('d-none');
                }
            }
            if (modalNotesEl) {
                modalNotesEl.innerText = event.extendedProps.notes || 'Nessuna nota';
            }

            const eventStart = event.start;
            const eventEnd = event.end;
            if (modalTitleTimeEl) {
                if (!eventStart) {
                    modalTitleTimeEl.innerText = '';
                } else if (isAllDay) {
                    modalTitleTimeEl.innerText = `${toItalianDateLabel(eventStart)} • Tutto il giorno`;
                } else {
                    const dateLabel = toItalianDateLabel(eventStart);
                    const startTimeLabel = toItalianTimeLabel(eventStart);
                    const endTimeLabel = eventEnd ? toItalianTimeLabel(eventEnd) : '';
                    modalTitleTimeEl.innerText = endTimeLabel
                        ? `${dateLabel} • ${startTimeLabel} - ${endTimeLabel}`
                        : `${dateLabel} • ${startTimeLabel}`;
                }
            }

            if (completeAppointmentBtn) {
                completeAppointmentBtn.classList.toggle('d-none', isCompleted || isAllDay || isNonTreatmentEvent);
            }
            if (editAppointmentBtn) {
                editAppointmentBtn.classList.toggle('d-none', isCompleted);
            }
            if (deleteAppointmentBtn) {
                deleteAppointmentBtn.classList.toggle('d-none', isCompleted && !isNonTreatmentEvent);
            }
            if (eventModalStateHintEl) {
                const stateHints = [];
                if (isCompleted && !isNonTreatmentEvent) {
                    stateHints.push('Appuntamento completato: azioni non disponibili.');
                } else if (isCompleted && isNonTreatmentEvent) {
                    stateHints.push('Evento completato: cancellazione consentita.');
                }
                if (isAllDay) {
                    stateHints.push('Evento tutto il giorno: non collegato ai trattamenti.');
                }
                const hintText = stateHints.join(' ');
                eventModalStateHintEl.textContent = hintText;
                eventModalStateHintEl.classList.toggle('d-none', !hintText);
            }

            if (eventModal) {
                eventModal.show();
            }
        }
    };

    if (initialDate) {
        calendarConfig.initialDate = initialDate;
    }

    const calendar = new FullCalendar.Calendar(calendarEl, calendarConfig);

    calendar.render();
    applyViewClass(calendar);
    applyDesktopWeekFillMode(calendar);
    renderReminderButtons(calendar);
    window.addEventListener('resize', () => {
        applyDesktopWeekFillMode(calendar);
    });

    if (openModalButton) {
        openModalButton.addEventListener('click', () => {
            openNewAppointmentModal();
        });
    }

    if (editAppointmentBtn) {
        editAppointmentBtn.addEventListener('click', () => {
            if (!selectedEvent) {
                return;
            }

            const startDate = selectedEvent.start ? ceilToQuarterHour(new Date(selectedEvent.start)) : null;
            if (!startDate) {
                showGlobalNotice('Impossibile modificare questo appuntamento.', 'error');
                return;
            }
            const eventIsAllDay = Boolean(selectedEvent.allDay || selectedEvent.extendedProps.allDay);
            const isNonTreatmentEvent = Boolean(selectedEvent.extendedProps.nonTreatmentEvent);
            const normalizedStart = eventIsAllDay ? startOfDay(startDate) : startDate;
            const endDate = eventIsAllDay ? nextDay(normalizedStart) : addMinutes(normalizedStart, DEFAULT_APPOINTMENT_DURATION_MINUTES);

            editingAppointmentId = selectedEvent.id;
            if (appointmentModalTitleEl) {
                appointmentModalTitleEl.innerText = 'Modifica appuntamento';
            }
            if (patientNameInput) {
                patientNameInput.value = selectedEvent.extendedProps.patient || '';
                patientNameInput.readOnly = false;
            }
            if (patientPhoneInput) {
                patientPhoneInput.value = selectedEvent.extendedProps.patientPhone || '';
                patientPhoneInput.readOnly = false;
            }
            if (nonTreatmentEventInput) {
                nonTreatmentEventInput.checked = isNonTreatmentEvent;
            }
            hidePatientSuggestions();
            updatePatientFieldUi();
            if (notesInput) {
                notesInput.value = selectedEvent.extendedProps.notes || '';
                notesInput.readOnly = false;
            }

            if (allDayInput) {
                allDayInput.checked = eventIsAllDay;
            }
            toggleTimeSelectionVisibility();

            startInput.value = toLocalDateTimeInputValue(normalizedStart);
            endInput.value = toLocalDateTimeInputValue(endDate);
            syncVisibleTimeControlsFromDateTimeInputs();
            if (eventModal) {
                eventModal.hide();
            }
            appointmentModal.show();
        });
    }

    if (deleteAppointmentBtn) {
        deleteAppointmentBtn.addEventListener('click', () => {
            if (!selectedEvent || !confirmDeleteModal) {
                return;
            }
            confirmDeleteModal.show();
        });
    }

    if (confirmDeleteAppointmentBtn) {
        confirmDeleteAppointmentBtn.addEventListener('click', () => {
            if (!selectedEvent) {
                return;
            }

            const data = new URLSearchParams();
            data.append('action', 'cancel');
            data.append('id', String(selectedEvent.id));

            fetch(contextPath + '/calendar', {
                method: 'POST',
                body: data
            })
                .then((res) => {
                    if (!res.ok) {
                        return res.text().then((text) => {
                            let message = 'Errore durante eliminazione appuntamento';
                            try {
                                const parsed = JSON.parse(text);
                                message = parsed?.message || parsed?.error || message;
                            } catch (e) {
                                if (text) {
                                    message = text;
                                }
                            }
                            throw new Error(message);
                        });
                    }
                    return res.text();
                })
                .then(() => {
                    if (confirmDeleteModal) {
                        confirmDeleteModal.hide();
                    }
                    if (eventModal) {
                        eventModal.hide();
                    }
                    selectedEvent = null;
                    calendar.refetchEvents();
                    showGlobalNotice('Appuntamento eliminato correttamente.', 'success');
                })
                .catch((err) => showGlobalNotice(err.message || 'Errore durante eliminazione appuntamento', 'error'));
        });
    }

    if (completeAppointmentBtn) {
        completeAppointmentBtn.addEventListener('click', () => {
            if (!selectedEvent) {
                return;
            }

            const startDate = selectedEvent.start ? new Date(selectedEvent.start) : new Date();
            const endDate = selectedEvent.end ? new Date(selectedEvent.end) : startDate;
            const defaultTitle = `Trattamento da appuntamento ${toItalianDateLabel(startDate)}`;

            if (treatmentPlanTitleInput) {
                treatmentPlanTitleInput.value = defaultTitle;
            }
            if (treatmentGoalsInput) {
                treatmentGoalsInput.value = '';
            }
            if (treatmentFrequencyPerWeekInput) {
                treatmentFrequencyPerWeekInput.value = '';
            }
            if (treatmentTotalSessionsPlannedInput) {
                treatmentTotalSessionsPlannedInput.value = '1';
            }
            if (treatmentExpectedEndDateInput) {
                treatmentExpectedEndDateInput.value = toIsoDateValue(endDate);
            }
            if (treatmentPainScorePreInput) {
                treatmentPainScorePreInput.value = '';
            }
            if (treatmentPainScorePostInput) {
                treatmentPainScorePostInput.value = '';
            }
            if (treatmentSessionOutcomeInput) {
                treatmentSessionOutcomeInput.value = 'Sessione completata da appuntamento';
            }
            if (treatmentHomeExercisesInput) {
                treatmentHomeExercisesInput.value = '';
            }
            if (treatmentNotesInput) {
                treatmentNotesInput.value = selectedEvent.extendedProps.notes || '';
            }

            if (eventModal) {
                eventModal.hide();
            }
            if (completeTreatmentModal) {
                completeTreatmentModal.show();
            }
        });
    }

    if (confirmCompleteTreatmentBtn) {
        confirmCompleteTreatmentBtn.addEventListener('click', () => {
            if (!selectedEvent) {
                return;
            }

            try {
                const totalSessionsPlanned = parseOptionalInteger(
                    treatmentTotalSessionsPlannedInput ? treatmentTotalSessionsPlannedInput.value : '',
                    'Numero sedute pianificate'
                );
                if (!totalSessionsPlanned || totalSessionsPlanned < 1) {
                    throw new Error('Numero sedute pianificate deve essere almeno 1');
                }

                const frequencyPerWeek = parseOptionalInteger(
                    treatmentFrequencyPerWeekInput ? treatmentFrequencyPerWeekInput.value : '',
                    'Frequenza settimanale'
                );
                const painScorePre = parseOptionalInteger(
                    treatmentPainScorePreInput ? treatmentPainScorePreInput.value : '',
                    'Dolore pre'
                );
                const painScorePost = parseOptionalInteger(
                    treatmentPainScorePostInput ? treatmentPainScorePostInput.value : '',
                    'Dolore post'
                );

                const planTitle = normalizeOptionalText(treatmentPlanTitleInput ? treatmentPlanTitleInput.value : '');
                if (!planTitle) {
                    throw new Error('Titolo piano terapeutico obbligatorio');
                }

                const data = new URLSearchParams();
                data.append('action', 'complete');
                data.append('id', String(selectedEvent.id));
                data.append('planTitle', planTitle);
                data.append('totalSessionsPlanned', String(totalSessionsPlanned));

                const goals = normalizeOptionalText(treatmentGoalsInput ? treatmentGoalsInput.value : '');
                const expectedEndDate = normalizeOptionalText(treatmentExpectedEndDateInput ? treatmentExpectedEndDateInput.value : '');
                const sessionOutcome = normalizeOptionalText(treatmentSessionOutcomeInput ? treatmentSessionOutcomeInput.value : '');
                const homeExercises = normalizeOptionalText(treatmentHomeExercisesInput ? treatmentHomeExercisesInput.value : '');
                const notes = normalizeOptionalText(treatmentNotesInput ? treatmentNotesInput.value : '');

                if (goals) {
                    data.append('goals', goals);
                }
                if (frequencyPerWeek !== null) {
                    data.append('frequencyPerWeek', String(frequencyPerWeek));
                }
                if (expectedEndDate) {
                    data.append('expectedEndDate', expectedEndDate);
                }
                if (painScorePre !== null) {
                    data.append('painScorePre', String(painScorePre));
                }
                if (painScorePost !== null) {
                    data.append('painScorePost', String(painScorePost));
                }
                if (sessionOutcome) {
                    data.append('sessionOutcome', sessionOutcome);
                }
                if (homeExercises) {
                    data.append('homeExercises', homeExercises);
                }
                if (notes) {
                    data.append('notes', notes);
                }

                fetch(contextPath + '/calendar', {
                    method: 'POST',
                    body: data
                })
                    .then((res) => {
                        if (!res.ok) {
                            return res.text().then((text) => {
                                let message = '';
                                try {
                                    const parsed = JSON.parse(text);
                                    message = parsed?.error || '';
                                } catch (e) {
                                    message = text || '';
                                }
                                throw new Error(message || 'Errore durante completamento appuntamento');
                            });
                        }
                        return res.text();
                    })
                    .then(() => {
                        if (completeTreatmentModal) {
                            completeTreatmentModal.hide();
                        }
                        selectedEvent = null;
                        calendar.refetchEvents();
                        showGlobalNotice('Trattamento completato con successo.', 'success');
                    })
                    .catch((err) => showGlobalNotice(err.message || 'Errore durante completamento appuntamento', 'error'));
            } catch (err) {
                showGlobalNotice(err.message || 'Dati trattamento non validi', 'error');
            }
        });
    }

    if (appointmentDateInput && startTimeNativeInput) {
        appointmentDateInput.addEventListener('change', () => {
            syncDateTimeInputsFromVisibleControls(true);
        });
        startTimeNativeInput.addEventListener('change', () => {
            syncDateTimeInputsFromVisibleControls(false);
        });
    }

    if (appointmentDateInput && endTimeNativeInput) {
        endTimeNativeInput.addEventListener('change', () => {
            syncDateTimeInputsFromVisibleControls(true);
        });
    }

    if (startInput && endInput) {
        startInput.addEventListener('change', () => {
            if (!startInput.value) {
                return;
            }

            const selectedDate = new Date(startInput.value);
            const normalizedStart = allDayInput?.checked ? startOfDay(selectedDate) : ceilToQuarterHour(selectedDate);
            const normalizedEnd = allDayInput?.checked
                ? nextDay(normalizedStart)
                : addMinutes(normalizedStart, DEFAULT_APPOINTMENT_DURATION_MINUTES);

            startInput.value = toLocalDateTimeInputValue(normalizedStart);
            endInput.value = toLocalDateTimeInputValue(normalizedEnd);
            syncVisibleTimeControlsFromDateTimeInputs();
        });
    }

    if (allDayInput && startInput && endInput) {
        allDayInput.addEventListener('change', () => {
            toggleTimeSelectionVisibility();
            if (!startInput.value) {
                return;
            }

            const selectedDate = new Date(startInput.value);
            const normalizedStart = allDayInput.checked ? startOfDay(selectedDate) : ceilToQuarterHour(selectedDate);
            const normalizedEnd = allDayInput.checked
                ? nextDay(normalizedStart)
                : addMinutes(normalizedStart, DEFAULT_APPOINTMENT_DURATION_MINUTES);

            startInput.value = toLocalDateTimeInputValue(normalizedStart);
            endInput.value = toLocalDateTimeInputValue(normalizedEnd);
            syncVisibleTimeControlsFromDateTimeInputs();
        });
    }

    if (nonTreatmentEventInput) {
        nonTreatmentEventInput.addEventListener('change', () => {
            hidePatientSuggestions();
            updatePatientFieldUi();
        });
    }

    updatePatientFieldUi();
    toggleTimeSelectionVisibility();

    if (patientNameInput) {
        patientNameInput.addEventListener('input', () => {
            schedulePatientSuggestions(patientNameInput.value);
        });
        patientNameInput.addEventListener('focus', () => {
            if (patientNameInput.value && patientNameInput.value.trim().length >= 1) {
                schedulePatientSuggestions(patientNameInput.value);
            }
        });
        patientNameInput.addEventListener('keydown', (event) => {
            if (!patientSuggestionItems.length || !patientSuggestionsMenuEl || patientSuggestionsMenuEl.classList.contains('d-none')) {
                return;
            }

            if (event.key === 'ArrowDown') {
                event.preventDefault();
                highlightSuggestion(highlightedSuggestionIndex + 1);
            } else if (event.key === 'ArrowUp') {
                event.preventDefault();
                highlightSuggestion(highlightedSuggestionIndex - 1);
            } else if (event.key === 'Enter' && highlightedSuggestionIndex >= 0) {
                event.preventDefault();
                selectPatientSuggestion(patientSuggestionItems[highlightedSuggestionIndex]);
            } else if (event.key === 'Escape') {
                hidePatientSuggestions();
            }
        });
        patientNameInput.addEventListener('blur', () => {
            setTimeout(() => {
                hidePatientSuggestions();
            }, 120);
        });
    }

    document.addEventListener('click', (event) => {
        if (!patientSuggestionsMenuEl || !patientNameInput) {
            return;
        }
        const clickInsideInput = patientNameInput.contains(event.target);
        const clickInsideMenu = patientSuggestionsMenuEl.contains(event.target);
        if (!clickInsideInput && !clickInsideMenu) {
            hidePatientSuggestions();
        }
    });

    if (appointmentModalEl) {
        appointmentModalEl.addEventListener('hidden.bs.modal', () => {
            hidePatientSuggestions();
            hideAppointmentFormError();
        });
    }

    if (urlSearchParams.get('new') === '1') {
        const prefilledPatientName = (urlSearchParams.get('patientName') || '').trim();
        const prefilledPatientPhone = (urlSearchParams.get('patientPhone') || '').trim();
        window.setTimeout(() => {
            openNewAppointmentModal({
                patientName: prefilledPatientName,
                patientPhone: prefilledPatientPhone
            });
            const cleanedParams = new URLSearchParams(window.location.search);
            cleanedParams.delete('new');
            cleanedParams.delete('patientName');
            cleanedParams.delete('patientPhone');
            const cleanedQuery = cleanedParams.toString();
            const cleanedUrl = cleanedQuery
                ? `${window.location.pathname}?${cleanedQuery}`
                : window.location.pathname;
            window.history.replaceState({}, document.title, cleanedUrl);
        }, 0);
    }

    if (reminderTemplateInput && !window.fisioReminderModal) {
        reminderTemplateInput.addEventListener('input', () => {
            renderReminderPreview(reminderTemplateInput.value);
            scheduleReminderTemplateSave();
        });
    }

    if (refreshReminderPreviewBtn && !window.fisioReminderModal) {
        refreshReminderPreviewBtn.addEventListener('click', () => {
            loadReminderPreview();
        });
    }

    if (sendReminderBtn && !window.fisioReminderModal) {
        sendReminderBtn.addEventListener('click', async () => {
            const dateValue = reminderDateInput ? reminderDateInput.value : '';
            if (!dateValue) {
                showGlobalNotice('Seleziona prima un giorno dal calendario.', 'info');
                return;
            }
            const templateValue = reminderTemplateInput && reminderTemplateInput.value.trim()
                ? reminderTemplateInput.value.trim()
                : defaultReminderTemplate();
            if (!whatsAppConfigured) {
                showGlobalNotice('Servizio WhatsApp non configurato per questo account. Contattare l\'amministratore di sistema.', 'info');
                return;
            }
            try {
                const appointmentIdValue = reminderAppointmentIdInput ? reminderAppointmentIdInput.value.trim() : '';
                const payload = await executeReminderSend(dateValue, templateValue, appointmentIdValue);
                showGlobalNotice(reminderResultNotice(payload), 'success');
                if (reminderModal) {
                    reminderModal.hide();
                }
            } catch (error) {
                showGlobalNotice(error.message || 'Errore durante l\'invio dei promemoria', 'error');
            }
        });
    }

    if (sendSingleReminderBtn) {
        sendSingleReminderBtn.addEventListener('click', () => {
            if (!selectedEvent) {
                return;
            }
            openReminderModalForEvent(selectedEvent);
        });
    }

    saveButton.addEventListener('click', () => {
        hideAppointmentFormError();

        if (!startInput || !startInput.value) {
            showAppointmentFormError("Inserisci un orario di inizio valido.");
            return;
        }

        const data = new URLSearchParams();
        const isAllDay = Boolean(allDayInput?.checked);
        const selectedDate = new Date(startInput.value);
        const startDate = isAllDay ? startOfDay(selectedDate) : ceilToQuarterHour(selectedDate);
        let endDate = new Date(endInput.value);
        if (Number.isNaN(endDate.getTime()) || !endDate.getTime()) {
            endDate = isAllDay ? nextDay(startDate) : addMinutes(startDate, DEFAULT_APPOINTMENT_DURATION_MINUTES);
        }
        if (!isAllDay && endDate <= startDate) {
            showAppointmentFormError("L'orario di fine deve essere successivo all'orario di inizio.");
            return;
        }
        const normalizedStart = toLocalDateTimeInputValue(startDate);
        const normalizedEnd = toLocalDateTimeInputValue(endDate);

        startInput.value = normalizedStart;
        endInput.value = normalizedEnd;
        syncVisibleTimeControlsFromDateTimeInputs();

        if (editingAppointmentId) {
            data.append('action', 'reschedule');
            data.append('id', String(editingAppointmentId));
            data.append('patientName', patientNameInput ? patientNameInput.value.trim() : '');
            data.append('patientPhone', patientPhoneInput ? patientPhoneInput.value.trim() : '');
            data.append('notes', notesInput ? notesInput.value.trim() : '');
        } else {
            const patientName = patientNameInput?.value?.trim() || '';
            if (!patientName) {
                showAppointmentFormError("Inserisci il nome del paziente.");
                return;
            }
            data.append('action', 'create');
            data.append('patientName', patientName);
            data.append('patientPhone', patientPhoneInput ? patientPhoneInput.value.trim() : '');
            data.append('notes', notesInput ? notesInput.value.trim() : '');
        }

        data.append('start', normalizedStart);
        data.append('end', normalizedEnd);
        data.append('allDay', String(isAllDay));
        data.append('nonTreatmentEvent', String(isNonTreatmentEventEnabled()));

        fetch(contextPath + '/calendar', {
            method: 'POST',
            body: data
        })
            .then((res) => {
                if (!res.ok) {
                    return parseApiError(res, 'Errore nel salvataggio appuntamento').then((error) => {
                        throw error;
                    });
                }
                return res.text();
            })
            .then(() => {
                appointmentModal.hide();
                editingAppointmentId = null;
                if (patientNameInput) {
                    patientNameInput.readOnly = false;
                }
                if (patientPhoneInput) {
                    patientPhoneInput.readOnly = false;
                }
                if (notesInput) {
                    notesInput.readOnly = false;
                }
                calendar.refetchEvents();
            })
            .catch((err) => showAppointmentFormError(err.message));
    });

    async function executeReminderSend(dateValue, templateValue, appointmentIdValue = '') {
        const data = new URLSearchParams();
        data.append('action', 'send-reminders');
        data.append('date', dateValue);
        data.append('template', templateValue);
        if (appointmentIdValue) {
            data.append('appointmentId', appointmentIdValue);
        }

        const response = await fetch(contextPath + '/calendar', {
            method: 'POST',
            body: data
        });
        if (!response.ok) {
            throw await parseApiError(response, 'Errore durante l\'invio dei promemoria');
        }
        return parseApiPayload(response);
    }

    function scheduleReminderTemplateSave() {
        if (reminderTemplateSaveDebounce) {
            window.clearTimeout(reminderTemplateSaveDebounce);
        }
        reminderTemplateSaveDebounce = window.setTimeout(() => {
            saveReminderTemplate().catch(() => {
                // Keep editing smooth even if autosave fails.
            });
        }, 500);
    }

    async function saveReminderTemplate() {
        if (!reminderTemplateInput) {
            return null;
        }
        const templateValue = reminderTemplateInput.value.trim() || defaultReminderTemplate();
        const data = new URLSearchParams();
        data.append('action', 'save-reminder-template');
        data.append('template', templateValue);

        const response = await fetch(contextPath + '/calendar', {
            method: 'POST',
            body: data
        });
        if (!response.ok) {
            throw await parseApiError(response, 'Errore durante il salvataggio del messaggio promemoria');
        }
        return parseApiPayload(response);
    }

    function reminderResultNotice(payload) {
        const processedCount = payload?.processedCount || 0;
        const sentCount = payload?.sentCount || 0;
        const skippedCount = payload?.skippedCount || 0;
        const failedCount = payload?.failedCount || 0;
        return `Promemoria elaborati: ${processedCount}, inviati: ${sentCount}, saltati: ${skippedCount}, falliti: ${failedCount}.`;
    }
});
