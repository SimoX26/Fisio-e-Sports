document.addEventListener("DOMContentLoaded", () => {
  const apiBase = "http://127.0.0.1:8081";
  const loginScreen = document.getElementById("loginScreen");
  const appScreen = document.getElementById("appScreen");
  const homeScreen = document.getElementById("homeScreen");
  const calendarScreen = document.getElementById("calendarScreen");
  const patientsScreen = document.getElementById("patientsScreen");
  const treatmentsScreen = document.getElementById("treatmentsScreen");
  const trashScreen = document.getElementById("trashScreen");
  const settingsScreen = document.getElementById("settingsScreen");
  const statsScreen = document.getElementById("statsScreen");
  const loginError = document.getElementById("loginError");
  const dataStatus = document.getElementById("dataStatus");
  const patientModal = new bootstrap.Modal(document.getElementById("patientModal"));
  const appointmentModal = new bootstrap.Modal(document.getElementById("appointmentModal"));
  const eventModal = new bootstrap.Modal(document.getElementById("eventModal"));
  const completeTreatmentModal = new bootstrap.Modal(document.getElementById("completeTreatmentModal"));
  const trashConfirmModal = new bootstrap.Modal(document.getElementById("trashConfirmModal"));
  const reminderPreviewModal = new bootstrap.Modal(document.getElementById("reminderPreviewModal"));
  const logoutConfirmModal = new bootstrap.Modal(document.getElementById("logoutConfirmModal"));
  const confirmDeleteAppointmentModal = new bootstrap.Modal(document.getElementById("confirmDeleteAppointmentModal"));
  const createPatientModal = new bootstrap.Modal(document.getElementById("createPatientModal"));
  const deletePatientModal = new bootstrap.Modal(document.getElementById("confirmDeletePatientModal"));
  const mergeConfirmModal = new bootstrap.Modal(document.getElementById("confirmMergePatientModal"));
  let authorization = null;
  let calendar = null;
  let selectedAppointment = null;
  let editingAppointmentId = null;
  let convertingWaitlistId = null;
  let sessionEpoch = 0;
  let patientsRequest = 0;
  let patientsTreatedDate = null;
  let patientDetailRequest = 0;
  let mergeCandidatesRequest = 0;
  let manualLoginStarted = false;
  let appointmentSuggestionsRequest = 0;
  let treatmentsRequest = 0;
  let statsRequest = 0;
  let trashRequest = 0;
  let trashConfirmation = null;
  let reminderPreviewRequest = 0;
  let reminderEntries = [];
  let preselectedReminderId = null;
  let reminderSendEnabled = false;
  let reminderSending = false;
  let reminderSaving = false;
  let reminderLoaded = false;
  let reminderDayLabel = "";
  let reminderDefaultTemplate = "";
  let whatsAppStatusRequest = 0;
  let whatsAppStatus = null;
  let whatsAppControlBusy = false;

  function updateWhatsAppControlButtons() {
    const available = whatsAppStatus?.configured && whatsAppStatus?.controlAvailable && !whatsAppControlBusy;
    document.getElementById("startWhatsAppBtn").disabled = !available || whatsAppStatus.reachable;
    document.getElementById("stopWhatsAppBtn").disabled = !available || !whatsAppStatus.reachable;
  }

  async function loadWhatsAppStatus() {
    const request = ++whatsAppStatusRequest;
    const currentSession = sessionEpoch;
    try {
      const status = await getJson("/api/whatsapp/status");
      if (request !== whatsAppStatusRequest || currentSession !== sessionEpoch || settingsScreen.hidden) return;
      whatsAppStatus = status;
      updateWhatsAppControlButtons();
      document.getElementById("whatsAppControls").hidden = status.managementMode !== "manual";
      const badge = document.getElementById("whatsAppBadge");
      const label = !status.configured ? "Non configurato" : status.ready ? "Connesso"
        : status.reachable ? "Da autenticare" : "Non attivo";
      badge.textContent = label;
      badge.className = `badge ${status.ready ? "text-bg-success" : status.reachable ? "text-bg-warning" : "text-bg-secondary"}`;
      document.getElementById("whatsAppState").textContent = status.state || "UNKNOWN";
      document.getElementById("whatsAppMessage").textContent = !status.configured
        ? "WhatsApp non è abilitato per questo account nel backend."
        : status.managementMode === "systemd" ? status.lastError || "Servizio gestito automaticamente dal server."
        : !status.controlAvailable ? "Avvio e arresto non disponibili: configura la cartella Baileys nel backend e i permessi di scrittura."
        : status.lastError || (status.reachable ? "" : "Servizio WhatsApp non raggiungibile dal backend.");
      const panel = document.getElementById("whatsAppQrPanel");
      const image = document.getElementById("whatsAppQrImage");
      panel.hidden = !status.qrDataUrl;
      if (status.qrDataUrl && image.src !== status.qrDataUrl) image.src = status.qrDataUrl;
      if (!status.qrDataUrl) image.removeAttribute("src");
    } catch (failure) {
      if (request !== whatsAppStatusRequest || currentSession !== sessionEpoch || settingsScreen.hidden) return;
      whatsAppStatus = null;
      updateWhatsAppControlButtons();
      document.getElementById("whatsAppControls").hidden = true;
      document.getElementById("whatsAppBadge").textContent = "Non disponibile";
      document.getElementById("whatsAppState").textContent = "UNKNOWN";
      document.getElementById("whatsAppMessage").textContent = "Impossibile leggere lo stato WhatsApp dal backend.";
      document.getElementById("whatsAppQrPanel").hidden = true;
    }
  }

  async function controlWhatsApp(action) {
    if (whatsAppControlBusy || !whatsAppStatus?.configured || !whatsAppStatus?.controlAvailable) return;
    const currentSession = sessionEpoch;
    const result = document.getElementById("whatsAppControlResult");
    whatsAppControlBusy = true;
    updateWhatsAppControlButtons();
    result.classList.add("d-none");
    try {
      await apiRequest("/api/whatsapp/control", {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ action })
      });
      if (currentSession !== sessionEpoch) return;
      result.textContent = action === "start" ? "Avvio richiesto. Attendi l'aggiornamento dello stato."
        : "Arresto richiesto. Attendi l'aggiornamento dello stato.";
      result.classList.remove("d-none", "alert-danger");
      result.classList.add("alert-info");
      loadWhatsAppStatus();
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      result.textContent = failure.status === 409 ? "Controllo non disponibile: verifica cartella e permessi Baileys sul backend."
        : "Impossibile controllare il servizio WhatsApp dal backend.";
      result.classList.remove("d-none", "alert-info");
      result.classList.add("alert-danger");
    } finally {
      whatsAppControlBusy = false;
      updateWhatsAppControlButtons();
    }
  }

  function renderReminderPreview() {
    const appointments = document.getElementById("reminderPreviewAppointments");
    const messages = document.getElementById("reminderPreviewMessages");
    appointments.replaceChildren();
    messages.replaceChildren();
    document.getElementById("reminderPreviewEmpty").classList.toggle("d-none", reminderEntries.length !== 0);
    for (const entry of reminderEntries) {
      const label = document.createElement("label");
      label.className = "d-flex align-items-center gap-2 mb-2";
      const checkbox = document.createElement("input");
      checkbox.type = "checkbox";
      checkbox.className = "form-check-input";
      checkbox.checked = preselectedReminderId == null || String(entry.appointmentId) === String(preselectedReminderId);
      checkbox.addEventListener("change", renderMessages);
      const text = document.createElement("span");
      text.textContent = `${entry.timeRange} • ${entry.patientName} • ${entry.patientPhone || "numero mancante"}`;
      label.append(checkbox, text);
      appointments.appendChild(label);
      entry.checkbox = checkbox;
    }
    renderMessages();
  }

  function renderMessages() {
    const messages = document.getElementById("reminderPreviewMessages");
    messages.replaceChildren();
    const input = document.getElementById("reminderPreviewTemplate");
    const template = input.value.trim() || reminderDefaultTemplate;
    for (const entry of reminderEntries.filter(item => item.checkbox?.checked)) {
      const card = document.createElement("div");
      card.className = "border rounded p-3 mb-2";
      const name = document.createElement("strong");
      name.textContent = `${entry.patientName} • ${entry.timeRange}`;
      const body = document.createElement("div");
      body.className = "mt-2";
      body.textContent = template.split("{nome paziente}").join(entry.patientName || "")
        .split("{giorno}").join(reminderDayLabel)
        .split("{ora inizio}").join(entry.startTime)
        .split("{ora fine}").join(entry.endTime)
        .split("{ora inizio - ora fine}").join(entry.timeRange);
      card.append(name, body);
      messages.appendChild(card);
    }
    input.disabled = !reminderLoaded || reminderSaving || reminderSending;
    document.getElementById("reminderPreviewDate").disabled = reminderSaving || reminderSending;
    document.getElementById("saveReminderTemplateBtn").disabled = !reminderLoaded || reminderSaving || reminderSending;
    document.getElementById("sendRemindersBtn").disabled = reminderSaving || reminderSending || !reminderSendEnabled
      || !reminderEntries.some(item => item.checkbox?.checked);
  }

  async function loadReminderPreview() {
    const request = ++reminderPreviewRequest;
    const currentSession = sessionEpoch;
    const date = document.getElementById("reminderPreviewDate").value;
    const error = document.getElementById("reminderPreviewError");
    error.classList.add("d-none");
    document.getElementById("reminderSendResult").classList.add("d-none");
    reminderSendEnabled = false;
    reminderLoaded = false;
    reminderDayLabel = "";
    reminderDefaultTemplate = "";
    reminderEntries = [];
    renderReminderPreview();
    if (!date) return;
    try {
      const data = await getJson(`/api/reminders/preview?date=${encodeURIComponent(date)}`);
      if (request !== reminderPreviewRequest || currentSession !== sessionEpoch) return;
      reminderEntries = data.recipients;
      reminderLoaded = true;
      reminderDayLabel = data.dayLabel;
      reminderDefaultTemplate = data.defaultTemplate;
      reminderSendEnabled = data.sendEnabled;
      document.getElementById("reminderSendHint").textContent = data.sendEnabled ? ""
        : "WhatsApp non configurato per questo account nel backend.";
      document.getElementById("reminderPreviewTemplate").value = data.template;
      renderReminderPreview();
    } catch (failure) {
      if (request !== reminderPreviewRequest || currentSession !== sessionEpoch) return;
      error.textContent = "Impossibile caricare l'anteprima dal backend.";
      error.classList.remove("d-none");
    }
  }

  async function saveReminderTemplate() {
    if (!reminderLoaded || reminderSaving || reminderSending) return;
    const currentSession = sessionEpoch;
    const input = document.getElementById("reminderPreviewTemplate");
    const error = document.getElementById("reminderPreviewError");
    const result = document.getElementById("reminderSendResult");
    error.classList.add("d-none");
    result.classList.add("d-none");
    reminderSaving = true;
    renderMessages();
    try {
      const response = await apiRequest("/api/reminders/template", {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ template: input.value })
      });
      const data = await response.json();
      if (currentSession !== sessionEpoch) return;
      input.value = data.template;
      result.textContent = "Modello promemoria salvato.";
      result.classList.remove("d-none", "alert-danger");
      result.classList.add("alert-info");
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      error.textContent = "Impossibile salvare il modello nel backend.";
      error.classList.remove("d-none");
    } finally {
      reminderSaving = false;
      renderMessages();
    }
  }

  async function sendSelectedReminders() {
    if (reminderSaving || reminderSending || !reminderSendEnabled) return;
    const selected = reminderEntries.filter(item => item.checkbox?.checked);
    if (!selected.length) return;
    const date = document.getElementById("reminderPreviewDate").value;
    const currentSession = sessionEpoch;
    const error = document.getElementById("reminderPreviewError");
    const result = document.getElementById("reminderSendResult");
    error.classList.add("d-none");
    result.classList.add("d-none");
    reminderSending = true;
    renderMessages();
    try {
      const body = new URLSearchParams({ date, template: document.getElementById("reminderPreviewTemplate").value });
      for (const entry of selected) body.append("appointmentId", entry.appointmentId);
      const response = await apiRequest("/api/reminders/send", {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body
      });
      const data = await response.json();
      if (currentSession !== sessionEpoch) return;
      result.textContent = `Promemoria elaborati: ${data.processedCount}. Inviati: ${data.sentCount}. Senza numero: ${data.skippedCount}. Non riusciti: ${data.failedCount}.`;
      result.classList.remove("d-none", "alert-info", "alert-danger");
      result.classList.add(data.failedCount ? "alert-danger" : "alert-info");
      selected.forEach(entry => { if (entry.checkbox) entry.checkbox.checked = false; });
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      error.textContent = failure.status === 428 ? "WhatsApp non configurato nel backend."
        : failure.status === 404 ? "Gli appuntamenti selezionati sono cambiati. Ricarica l'anteprima."
          : "Invio non riuscito o esito sconosciuto. Verifica lo stato del gateway prima di riprovare.";
      error.classList.remove("d-none");
    } finally {
      reminderSending = false;
      renderMessages();
    }
  }

  function openReminderPreview(date, appointmentId = null) {
    preselectedReminderId = appointmentId;
    document.getElementById("reminderPreviewDate").value = date;
    document.getElementById("reminderPreviewTemplate").value = "";
    reminderPreviewModal.show();
    loadReminderPreview();
  }

  function localDateTime(date) {
    const pad = number => String(number).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  function appointmentDate(date) {
    return localDateTime(date).slice(0, 10);
  }

  function appointmentTime(date) {
    return localDateTime(date).slice(11, 16);
  }

  function showAppointmentError(message) {
    const error = document.getElementById("appointmentFormError");
    error.textContent = message;
    error.classList.remove("d-none");
  }

  function updateAppointmentForm() {
    appointmentSuggestionsRequest++;
    const generic = document.getElementById("appointmentGeneric").checked;
    const allDay = document.getElementById("appointmentAllDay").checked;
    document.getElementById("appointmentPatientLabel").textContent = generic ? "Titolo" : "Paziente";
    document.getElementById("appointmentPatientName").placeholder = generic ? "Inserisci il titolo dell'evento" : "Nome e cognome paziente";
    document.getElementById("appointmentTimeSection").classList.toggle("d-none", allDay);
    document.getElementById("appointmentStartTime").disabled = allDay;
    document.getElementById("appointmentEndTime").disabled = allDay;
    document.getElementById("appointmentPatientSuggestions").classList.add("d-none");
  }

  function openAppointmentModal(start = new Date()) {
    editingAppointmentId = null;
    convertingWaitlistId = null;
    const rounded = new Date(start);
    rounded.setMinutes(Math.ceil(rounded.getMinutes() / 15) * 15, 0, 0);
    const end = new Date(rounded.getTime() + 60 * 60000);
    document.getElementById("appointmentForm").reset();
    document.getElementById("appointmentModalTitle").textContent = "Nuovo appuntamento";
    document.getElementById("appointmentPatientPhone").disabled = false;
    document.getElementById("appointmentDate").value = appointmentDate(rounded);
    document.getElementById("appointmentStartTime").value = appointmentTime(rounded);
    document.getElementById("appointmentEndTime").value = appointmentTime(end);
    document.getElementById("appointmentFormError").classList.add("d-none");
    document.getElementById("appointmentCreated").classList.add("d-none");
    document.getElementById("homeAppointmentCreated").classList.add("d-none");
    updateAppointmentForm();
    appointmentModal.show();
  }

  function editSelectedAppointment() {
    const selected = selectedAppointment;
    if (!selected || !selected.start || selected.extendedProps.state !== "SCHEDULED") return;
    editingAppointmentId = selected.id;
    convertingWaitlistId = null;
    document.getElementById("appointmentForm").reset();
    document.getElementById("appointmentModalTitle").textContent = "Modifica appuntamento";
    document.getElementById("appointmentPatientName").value = selected.title || "";
    document.getElementById("appointmentPatientPhone").value = selected.extendedProps.patientPhone || "";
    document.getElementById("appointmentPatientPhone").disabled = true;
    document.getElementById("appointmentGeneric").checked = Boolean(selected.extendedProps.nonTreatmentEvent);
    document.getElementById("appointmentAllDay").checked = Boolean(selected.allDay);
    document.getElementById("appointmentDate").value = appointmentDate(selected.start);
    document.getElementById("appointmentStartTime").value = appointmentTime(selected.start);
    document.getElementById("appointmentEndTime").value = appointmentTime(selected.end || new Date(selected.start.getTime() + 60 * 60000));
    document.getElementById("appointmentNotes").value = selected.extendedProps.notes || "";
    document.getElementById("appointmentFormError").classList.add("d-none");
    document.getElementById("appointmentCreated").classList.add("d-none");
    updateAppointmentForm();
    document.getElementById("eventModal").addEventListener("hidden.bs.modal", () => appointmentModal.show(), { once: true });
    eventModal.hide();
  }

  async function apiRequest(path, options = {}) {
    const response = await fetch(`${apiBase}${path}`, {
      ...options,
      headers: { Authorization: authorization, ...options.headers },
      cache: "no-store"
    });
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return response;
  }

  async function getJson(path) {
    return (await apiRequest(path)).json();
  }

  function enterApp(identity) {
    sessionEpoch++;
    document.getElementById("password").value = "";
    const prefix = new Date().getHours() > 15 ? "Buonasera" : "Buongiorno";
    const displayName = identity.username.charAt(0).toLocaleUpperCase("it-IT") + identity.username.slice(1).toLocaleLowerCase("it-IT");
    document.getElementById("homeGreeting").textContent = `${prefix}, ${displayName}`;
    document.getElementById("todayLabel").textContent = new Intl.DateTimeFormat("it-IT", { weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(new Date());
    loginScreen.hidden = true;
    appScreen.hidden = false;
    document.body.classList.remove("auth-page", "d-flex", "align-items-center", "justify-content-center");
    document.getElementById("authNotice").classList.add("d-none");
    showHome();
  }

  function showAuthNotice(message) {
    const notice = document.getElementById("authNotice");
    notice.textContent = message;
    notice.classList.remove("d-none");
  }

  window.desktopBridgeReady = () => window.desktopBridge.loadToken();
  window.desktopTokenLoaded = async token => {
    if (!token || manualLoginStarted || authorization || loginScreen.hidden) return;
    authorization = `Bearer ${token}`;
    const attemptAuthorization = authorization;
    try {
      const identity = await getJson("/api/me");
      if (manualLoginStarted || loginScreen.hidden) return;
      enterApp(identity);
    } catch (failure) {
      if (manualLoginStarted || authorization !== attemptAuthorization) return;
      authorization = null;
      if (failure.status === 401) {
        window.desktopBridge.clearToken();
      } else {
        loginError.textContent = "Accesso automatico non riuscito. Controlla che il backend sia avviato.";
        loginError.classList.remove("d-none");
      }
    }
  };
  window.desktopTokenStored = saved => {
    if (!saved && !appScreen.hidden && authorization?.startsWith("Bearer ")) {
      const cleared = window.desktopBridge.clearToken();
      showAuthNotice(cleared
        ? "Accesso automatico non disponibile: il sistema non ha salvato il token protetto."
        : "Accesso automatico non disponibile: controlla l'archivio credenziali del sistema prima di riaprire l'app.");
    }
  };

  const navIds = ["homeNav", "calendarNav", "patientsNav", "treatmentsNav", "statsNav", "settingsNav"];

  function setActiveNav(activeId) {
    for (const id of navIds) {
      const button = document.getElementById(id);
      button.classList.toggle("active", id === activeId);
      if (id === activeId) button.setAttribute("aria-current", "page");
      else button.removeAttribute("aria-current");
    }
  }

  function leaveStats() {
    statsRequest++;
    statsScreen.hidden = true;
  }

  function showHome() {
    leaveStats();
    homeScreen.hidden = false;
    calendarScreen.hidden = true;
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = true;
    settingsScreen.hidden = true;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month");
    document.body.classList.remove("address-book-page");
    document.body.classList.add("app-page");
    setActiveNav("homeNav");
    document.title = "Dashboard • Fisio e Sports";
    loadTodayAgenda();
    loadWaitlist();
  }

  async function loadWaitlist() {
    const currentSession = sessionEpoch;
    const list = document.getElementById("waitlistEntries");
    list.replaceChildren();
    document.getElementById("waitlistError").classList.add("d-none");
    try {
      const entries = await getJson("/api/waitlist");
      if (currentSession !== sessionEpoch) return;
      document.getElementById("waitlistCount").textContent = `${entries.length} contatti`;
      if (entries.length === 0) {
        const empty = document.createElement("div");
        empty.className = "home-empty-state";
        empty.textContent = "Nessuna persona in attesa al momento.";
        list.appendChild(empty);
      }
      for (const entry of entries) {
        const row = document.createElement("div");
        row.className = "home-waitlist-item";
        const main = document.createElement("div");
        main.className = "home-waitlist-item__main";
        const name = document.createElement("div");
        name.className = "home-agenda-title";
        name.textContent = entry.fullName;
        const metadata = document.createElement("div");
        metadata.className = "home-waitlist-item__meta";
        const phone = document.createElement("span");
        phone.textContent = entry.phone;
        const created = document.createElement("span");
        created.textContent = `Aggiunto il ${entry.createdAtLabel}`;
        metadata.append(phone, created);
        main.append(name, metadata);
        const actions = document.createElement("div");
        actions.className = "home-waitlist-actions";
        const convert = document.createElement("button");
        convert.type = "button";
        convert.className = "btn btn-outline-primary btn-sm";
        convert.textContent = "Trasforma in appuntamento";
        convert.addEventListener("click", () => {
          openAppointmentModal();
          convertingWaitlistId = entry.id;
          document.getElementById("appointmentPatientName").value = entry.fullName;
          document.getElementById("appointmentPatientPhone").value = entry.phone || "";
        });
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-outline-danger btn-sm btn-icon-only btn-trash-icon";
        remove.setAttribute("aria-label", "Rimuovi contatto dalla lista di attesa");
        remove.title = "Rimuovi contatto dalla lista di attesa";
        remove.addEventListener("click", () => removeWaitlistEntry(entry.id));
        actions.append(convert, remove);
        row.append(main, actions);
        list.appendChild(row);
      }
    } catch (error) {
      if (currentSession !== sessionEpoch) return;
      showWaitlistError("Impossibile caricare la lista di attesa dal backend.");
    }
  }

  function showWaitlistError(message) {
    const error = document.getElementById("waitlistError");
    error.textContent = message;
    error.classList.remove("d-none");
  }

  async function removeWaitlistEntry(id) {
    const currentSession = sessionEpoch;
    try {
      await apiRequest(`/api/waitlist/${encodeURIComponent(id)}`, { method: "DELETE" });
      if (currentSession === sessionEpoch) loadWaitlist();
    } catch (error) {
      if (currentSession === sessionEpoch) showWaitlistError("Impossibile rimuovere il contatto.");
    }
  }

  async function loadTodayAgenda() {
    const currentSession = sessionEpoch;
    const start = new Date();
    start.setHours(0, 0, 0, 0);
    const end = new Date(start);
    end.setDate(end.getDate() + 1);
    const agenda = document.getElementById("todayAgenda");
    agenda.replaceChildren();
    try {
      const query = new URLSearchParams({ start: localDateTime(start), end: localDateTime(end) });
      const events = await getJson(`/api/calendar?${query}`);
      if (currentSession !== sessionEpoch) return;
      document.getElementById("appointmentsToday").textContent = events.length;
      document.getElementById("patientsToday").textContent = new Set(events.map(item => item.extendedProps.patientId).filter(id => id != null)).size;
      const now = new Date();
      document.getElementById("remindersToday").textContent = events.filter(item =>
        item.extendedProps.state !== "CANCELLED" && item.extendedProps.patientId != null
          && new Date(item.start) > now).length;
      if (events.length === 0) {
        const empty = document.createElement("div");
        empty.className = "home-empty-state";
        empty.textContent = "Nessun appuntamento pianificato per oggi.";
        agenda.appendChild(empty);
      }
      for (const event of events) {
        const row = document.createElement("div");
        row.className = "home-agenda-item";
        const time = document.createElement("div");
        time.className = "home-agenda-time";
        time.textContent = `${event.start.slice(11, 16)} - ${event.end.slice(11, 16)}`;
        const main = document.createElement("div");
        main.className = "home-agenda-main";
        const title = document.createElement("div");
        title.className = "home-agenda-title";
        title.textContent = event.title;
        const subtitle = document.createElement("div");
        subtitle.className = "home-agenda-subtitle";
        subtitle.textContent = event.extendedProps.nonTreatmentEvent ? "Evento" : "Paziente";
        main.append(title, subtitle);
        row.append(time, main);
        const state = event.extendedProps.state;
        if (state === "COMPLETED" || state === "CANCELLED") {
          const side = document.createElement("div");
          side.className = "home-agenda-side";
          const badge = document.createElement("span");
          badge.className = `home-status-badge home-status-badge--${state}`;
          badge.textContent = state === "COMPLETED" ? "COMPLETATO" : "CANCELLATO";
          side.appendChild(badge);
          row.appendChild(side);
        }
        agenda.appendChild(row);
      }
    } catch (error) {
      if (currentSession !== sessionEpoch) return;
      const message = document.createElement("div");
      message.className = "alert alert-danger";
      message.textContent = "Impossibile caricare l'agenda dal backend.";
      agenda.appendChild(message);
    }
  }

  function createCalendar() {
    return new FullCalendar.Calendar(document.getElementById("calendar"), {
      locale: "it",
      allDayText: "Tutto il giorno",
      buttonText: { today: "oggi", day: "giorno", week: "settimana", month: "mese" },
      firstDay: 1,
      height: "auto",
      expandRows: true,
      stickyHeaderDates: true,
      customButtons: {
        trash: { text: "Cestino", hint: "Apri cestino appuntamenti", click: showTrash },
        newAppointment: { text: "+ Nuovo", hint: "Nuovo appuntamento", click: () => openAppointmentModal() }
      },
      headerToolbar: { left: "prev,next today timeGridDay,timeGridWeek,dayGridMonth", center: "title", right: "trash newAppointment" },
      titleRangeSeparator: " - ",
      initialView: "timeGridWeek",
      views: { timeGridWeek: { titleFormat: { day: "numeric", month: "long", year: "numeric" } } },
      slotDuration: "00:15:00",
      snapDuration: "00:15:00",
      nowIndicator: true,
      slotMinTime: "08:00:00",
      slotMaxTime: "21:00:00",
      scrollTime: "08:00:00",
      selectable: true,
      editable: false,
      displayEventTime: true,
      eventTimeFormat: { hour: "2-digit", minute: "2-digit", hour12: false },
      slotLabelFormat: { hour: "2-digit", minute: "2-digit", hour12: false },
      slotLabelInterval: "01:00",
      async events(range, success, failure) {
        const currentSession = sessionEpoch;
        try {
          const query = new URLSearchParams({ start: range.startStr, end: range.endStr });
          const events = await getJson(`/api/calendar?${query}`);
          if (currentSession !== sessionEpoch) return;
          dataStatus.classList.add("d-none");
          success(events);
        } catch (error) {
          if (currentSession !== sessionEpoch) return;
          dataStatus.textContent = "Impossibile caricare gli appuntamenti dal backend.";
          dataStatus.classList.remove("d-none");
          failure(error);
        }
      },
      eventContent(arg) {
        const month = arg.view.type === "dayGridMonth";
        const wrapper = document.createElement(month ? "span" : "div");
        const time = document.createElement(month ? "span" : "div");
        const title = document.createElement(month ? "span" : "div");
        time.className = month ? "fc-event-time-inline" : "fc-event-time-line";
        title.className = month ? "fc-event-title-inline" : "fc-event-title-line";
        time.textContent = arg.timeText || "";
        title.textContent = arg.event.title || "";
        wrapper.append(time, title);
        return { domNodes: [wrapper] };
      },
      eventDidMount(info) {
        const generic = Boolean(info.event.extendedProps.nonTreatmentEvent);
        const completed = info.event.extendedProps.state === "COMPLETED";
        const background = generic ? "#f1f3f5" : completed ? "#e6f4ea" : "#eaf1fb";
        const border = generic ? "#c9ced6" : completed ? "#8bc49a" : "#7f9fcd";
        const foreground = generic ? "#4b5563" : completed ? "#1f8f47" : "#1f2d3d";
        info.el.classList.add("calendar-event--custom-color");
        info.el.style.setProperty("--event-bg", background);
        info.el.style.setProperty("--event-border", border);
        info.el.style.setProperty("--event-text", foreground);
        info.el.style.setProperty("background", background, "important");
        info.el.style.setProperty("border", `1px solid ${border}`, "important");
        info.el.style.setProperty("color", foreground, "important");
      },
      datesSet(info) {
        document.body.classList.toggle("calendar-view-day", info.view.type === "timeGridDay");
        document.body.classList.toggle("calendar-view-week", info.view.type === "timeGridWeek");
        document.body.classList.toggle("calendar-view-month", info.view.type === "dayGridMonth");
      },
      select(info) {
        openAppointmentModal(info.start);
      },
      eventClick(info) {
        info.jsEvent.preventDefault();
        const event = info.event;
        selectedAppointment = event;
        const completed = event.extendedProps.state === "COMPLETED";
        const allDay = Boolean(event.allDay || event.extendedProps.allDay);
        const generic = Boolean(event.extendedProps.nonTreatmentEvent);
        const patientId = event.extendedProps.patientId;
        const date = event.start?.toLocaleDateString("it-IT") || "";
        const time = value => value?.toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" }) || "";
        document.getElementById("eventModalTitle").textContent = event.title || "";
        document.getElementById("eventModalTime").textContent = allDay
          ? `${date} • Tutto il giorno`
          : `${date} • ${time(event.start)}${event.end ? ` - ${time(event.end)}` : ""}`;
        document.getElementById("eventModalNotes").textContent = event.extendedProps.notes || "Nessuna nota";
        const patientButton = document.getElementById("openPatientDetailsBtn");
        patientButton.classList.toggle("d-none", patientId == null);
        patientButton.onclick = patientId == null ? null : () => {
          document.getElementById("eventModal").addEventListener("hidden.bs.modal", () => {
            showPatients();
            showPatientDetail(patientId);
          }, { once: true });
          eventModal.hide();
        };
        document.getElementById("sendSingleReminderBtn").classList.toggle("d-none", completed || allDay || generic || patientId == null);
        document.getElementById("completeAppointmentBtn").classList.toggle("d-none",
          event.extendedProps.state !== "SCHEDULED" || allDay || generic || patientId == null
          || !event.end || event.end > new Date());
        document.getElementById("editAppointmentBtn").classList.toggle("d-none", completed);
        document.getElementById("deleteAppointmentBtn").classList.toggle("d-none", completed && !generic);
        const stateHints = [];
        if (completed) stateHints.push(generic ? "Evento completato: cancellazione consentita." : "Appuntamento completato: azioni non disponibili.");
        if (allDay) stateHints.push("Evento tutto il giorno: non collegato ai trattamenti.");
        const hint = document.getElementById("eventModalStateHint");
        hint.textContent = stateHints.join(" ");
        hint.classList.toggle("d-none", stateHints.length === 0);
        eventModal.show();
      }
    });
  }

  function showCalendar(dayView = false) {
    leaveStats();
    homeScreen.hidden = true;
    calendarScreen.hidden = false;
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = true;
    settingsScreen.hidden = true;
    document.body.classList.remove("app-page");
    document.body.classList.remove("address-book-page");
    document.body.classList.add("calendar-gcal-page");
    setActiveNav("calendarNav");
    document.title = "Calendario • Fisio e Sports";
    if (!calendar) {
      calendar = createCalendar();
      calendar.render();
    }
    if (dayView) calendar.changeView("timeGridDay");
    document.body.classList.toggle("calendar-view-day", calendar.view.type === "timeGridDay");
    document.body.classList.toggle("calendar-view-week", calendar.view.type === "timeGridWeek");
    document.body.classList.toggle("calendar-view-month", calendar.view.type === "dayGridMonth");
    calendar.updateSize();
  }

  function setPatientsFilter(date) {
    patientsTreatedDate = date;
    document.getElementById("patientsFilterNotice").classList.toggle("d-none", date == null);
    document.getElementById("patientsFilterLabel").textContent = date == null ? ""
      : `Filtro attivo: pazienti con appuntamenti il ${new Date(`${date}T00:00:00`).toLocaleDateString("it-IT")}`;
  }

  function showPatients(treatedDate = null) {
    leaveStats();
    if (treatedDate != null) document.getElementById("patientsQuery").value = "";
    setPatientsFilter(treatedDate);
    homeScreen.hidden = true;
    calendarScreen.hidden = true;
    patientsScreen.hidden = false;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = true;
    settingsScreen.hidden = true;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month");
    document.body.classList.add("app-page", "address-book-page");
    setActiveNav("patientsNav");
    document.title = "Rubrica Pazienti • Fisio e Sports";
    loadPatients();
  }

  async function showTreatments(patientId = null, patientName = "") {
    leaveStats();
    homeScreen.hidden = true;
    calendarScreen.hidden = true;
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = false;
    trashScreen.hidden = true;
    settingsScreen.hidden = true;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month", "address-book-page");
    document.body.classList.add("app-page");
    setActiveNav("treatmentsNav");
    document.getElementById("treatmentsTitle").textContent = patientId == null
      ? "Storico trattamenti" : `Cronologia trattamenti • ${patientName}`;
    document.title = "Storico trattamenti • Fisio e Sports";
    const request = ++treatmentsRequest;
    const currentSession = sessionEpoch;
    const rows = document.getElementById("treatmentsRows");
    const error = document.getElementById("treatmentsError");
    const empty = document.getElementById("treatmentsEmpty");
    rows.replaceChildren();
    error.classList.add("d-none");
    empty.classList.add("d-none");
    document.getElementById("treatmentsTableWrap").classList.add("d-none");
    try {
      const query = patientId == null ? "" : `?patientId=${encodeURIComponent(patientId)}`;
      const entries = await getJson(`/api/treatments${query}`);
      if (request !== treatmentsRequest || currentSession !== sessionEpoch) return;
      empty.classList.toggle("d-none", entries.length !== 0);
      document.getElementById("treatmentsTableWrap").classList.toggle("d-none", entries.length === 0);
      for (const entry of entries) {
        const row = document.createElement("tr");
        const score = entry.painScorePre == null && entry.painScorePost == null
          ? "-" : `${entry.painScorePre ?? "-"} / ${entry.painScorePost ?? "-"}`;
        const date = new Date(entry.sessionStart).toLocaleDateString("it-IT");
        for (const value of [date, entry.patientName, entry.planTitle, score, entry.outcome || "-", entry.state]) {
          const cell = document.createElement("td");
          cell.textContent = value;
          row.appendChild(cell);
        }
        rows.appendChild(row);
      }
    } catch (failure) {
      if (request !== treatmentsRequest || currentSession !== sessionEpoch) return;
      error.textContent = failure.status === 404 ? "Paziente non disponibile per questo terapista."
        : "Impossibile caricare lo storico trattamenti dal backend.";
      error.classList.remove("d-none");
    }
  }

  function showSettings() {
    leaveStats();
    homeScreen.hidden = true;
    calendarScreen.hidden = true;
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = true;
    settingsScreen.hidden = false;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month", "address-book-page");
    document.body.classList.add("app-page");
    setActiveNav("settingsNav");
    document.title = "Impostazioni • Fisio e Sports";
    loadWhatsAppStatus();
  }

  const operativeKpis = [
    ["appointmentsInMonth", "Appuntamenti del mese", "Appuntamenti non cancellati con paziente e inizio nel mese."],
    ["appointmentsCompleted", "Trattamenti completati", "Appuntamenti completati con fine nel mese."],
    ["appointmentsCancelled", "Appuntamenti cancellati", "Appuntamenti cancellati nel mese."],
    ["totalBookedMinutes", "Ore prenotate", "Minuti prenotati divisi per 60, arrotondati all'ora."],
    ["cancellationRate", "Tasso di cancellazione", "Appuntamenti cancellati divisi per appuntamenti del mese, in percentuale."]
  ];
  const managementKpis = [
    ["appointmentsCreated", "Nuovi appuntamenti creati", "Appuntamenti inseriti nel mese."],
    ["activePatientsMonth", "Pazienti attivi nel mese", "Pazienti distinti con almeno un appuntamento non cancellato nel mese."],
    ["newPatientsFirstAppointmentMonth", "Nuovi pazienti (primo appuntamento)", "Pazienti con primo appuntamento nel mese."],
    ["returningPatientsMonth", "Pazienti di ritorno", "Pazienti attivi meno nuovi pazienti; minimo zero."],
    ["agendaSaturationPct", "Saturazione agenda", "Minuti prenotati divisi per 160 ore mensili per terapista, in percentuale."],
    ["appointmentsPerActivePatient", "Media appuntamenti per paziente", "Appuntamenti del mese divisi per pazienti attivi."]
  ];
  const chartKpis = [
    ["appointmentsInMonth", "Appuntamenti del mese", "#7950f2"],
    ["appointmentsCompleted", "Trattamenti completati", "#1f8f47"],
    ["appointmentsCancelled", "Appuntamenti cancellati", "#c53929"],
    ["appointmentsCreated", "Nuovi appuntamenti creati", "#e67700"],
    ["newPatientsMonth", "Nuovi pazienti acquisiti", "#1a73e8"]
  ];

  function kpiMonth(row, long = false) {
    return new Date(row.year, row.month - 1, 1).toLocaleDateString("it-IT", {
      month: long ? "long" : "short", year: "numeric"
    });
  }

  function kpiValue(row, key) {
    const number = Number(row?.[key] || 0);
    if (key === "totalBookedMinutes") return Math.round(number / 60).toLocaleString("it-IT");
    if (key === "cancellationRate") {
      const total = Number(row?.appointmentsInMonth || 0);
      return total ? `${(Number(row?.appointmentsCancelled || 0) / total * 100).toFixed(1).replace(".", ",")}%` : "0%";
    }
    if (key === "agendaSaturationPct") return `${number.toLocaleString("it-IT", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
    if (key === "appointmentsPerActivePatient") return number.toLocaleString("it-IT", { minimumFractionDigits: 1, maximumFractionDigits: 1 });
    return number.toLocaleString("it-IT");
  }

  function renderKpiCards(containerId, definitions, latest) {
    const container = document.getElementById(containerId);
    container.replaceChildren();
    for (const [key, label, help] of definitions) {
      const item = document.createElement("div");
      item.className = "kpi-grid__item";
      const card = document.createElement("div");
      card.className = "glass-card section-card p-4 h-100 stats-kpi-card";
      const heading = document.createElement("div");
      heading.className = "kpi-label mb-1";
      heading.append(document.createTextNode(`${label} `));
      const info = document.createElement("button");
      info.type = "button";
      info.className = "kpi-info-btn";
      info.textContent = "i";
      info.dataset.tooltip = help;
      info.setAttribute("aria-label", `${label}. ${help}`);
      info.addEventListener("click", () => info.classList.toggle("is-open"));
      heading.appendChild(info);
      const value = document.createElement("div");
      value.className = "kpi-value";
      value.textContent = kpiValue(latest, key);
      card.append(heading, value);
      item.appendChild(card);
      container.appendChild(item);
    }
  }

  function renderKpiTable(series) {
    const body = document.getElementById("kpiTableBody");
    body.replaceChildren();
    if (!series.length) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 12;
      cell.className = "text-muted";
      cell.textContent = "Nessun dato disponibile";
      row.appendChild(cell);
      body.appendChild(row);
      return;
    }
    const columns = [
      ["appointmentsInMonth", "Appuntamenti mese"], ["appointmentsCompleted", "Trattamenti completati"],
      ["appointmentsCancelled", "Appuntamenti cancellati"], ["totalBookedMinutes", "Ore prenotate"],
      ["cancellationRate", "Tasso cancellazione"], ["appointmentsCreated", "Nuovi appuntamenti"],
      ["newPatientsFirstAppointmentMonth", "Nuovi pazienti"], ["activePatientsMonth", "Pazienti attivi"],
      ["returningPatientsMonth", "Pazienti di ritorno"], ["agendaSaturationPct", "Saturazione agenda"],
      ["appointmentsPerActivePatient", "Media app./paziente"]
    ];
    for (const snapshot of series) {
      const row = document.createElement("tr");
      const monthCell = document.createElement("td");
      monthCell.textContent = kpiMonth(snapshot);
      row.appendChild(monthCell);
      for (const [key, label] of columns) {
        const cell = document.createElement("td");
        cell.dataset.label = label;
        cell.textContent = kpiValue(snapshot, key);
        row.appendChild(cell);
      }
      body.appendChild(row);
    }
  }

  function renderKpiChart(series) {
    const container = document.getElementById("kpiTrendChart");
    const legend = document.getElementById("kpiChartLegend");
    container.replaceChildren();
    legend.replaceChildren();
    if (!series.length) return;
    const ordered = series.slice().reverse();
    const width = 840, height = 290, left = 42, top = 18, right = 16, bottom = 44;
    const plotWidth = width - left - right, plotHeight = height - top - bottom;
    const max = Math.max(1, ...ordered.flatMap(row => chartKpis.map(([key]) => Number(row[key] || 0))));
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute("viewBox", `0 0 ${width} ${height}`);
    svg.setAttribute("width", "100%");
    svg.setAttribute("height", "320");
    svg.setAttribute("role", "img");
    svg.setAttribute("aria-label", "Andamento mensile di appuntamenti, trattamenti e pazienti");
    const add = (tag, attrs, text = "") => {
      const node = document.createElementNS("http://www.w3.org/2000/svg", tag);
      for (const [name, value] of Object.entries(attrs)) node.setAttribute(name, value);
      if (text) node.textContent = text;
      svg.appendChild(node);
      return node;
    };
    for (let tick = 0; tick <= 4; tick++) {
      const y = top + plotHeight * tick / 4;
      add("line", { x1: left, x2: width - right, y1: y, y2: y, stroke: "#e3e7ec" });
      add("text", { x: left - 8, y: y + 4, "text-anchor": "end", fill: "#5f6368", "font-size": 11 },
        String(Math.round(max * (4 - tick) / 4)));
    }
    const xAt = index => left + (ordered.length === 1 ? plotWidth / 2 : plotWidth * index / (ordered.length - 1));
    ordered.forEach((row, index) => {
      if (index % Math.ceil(ordered.length / 8) === 0 || index === ordered.length - 1) {
        add("text", { x: xAt(index), y: height - 16, "text-anchor": "middle", fill: "#5f6368", "font-size": 11 }, kpiMonth(row));
      }
    });
    for (const [key, label, color] of chartKpis) {
      const points = ordered.map((row, index) => `${xAt(index)},${top + plotHeight * (1 - Number(row[key] || 0) / max)}`);
      add("polyline", { points: points.join(" "), fill: "none", stroke: color, "stroke-width": 2.2 });
      ordered.forEach((row, index) => {
        const circle = add("circle", { cx: xAt(index), cy: top + plotHeight * (1 - Number(row[key] || 0) / max), r: 3, fill: color });
        const title = document.createElementNS("http://www.w3.org/2000/svg", "title");
        title.textContent = `${kpiMonth(row)} • ${label}: ${kpiValue(row, key)}`;
        circle.appendChild(title);
      });
      const entry = document.createElement("span");
      entry.className = "d-inline-flex align-items-center gap-1";
      const marker = document.createElement("span");
      marker.style.cssText = `display:inline-block;width:10px;height:10px;border-radius:50%;background:${color}`;
      entry.append(marker, document.createTextNode(label));
      legend.appendChild(entry);
    }
    container.appendChild(svg);
  }

  function renderKpis(series) {
    const latest = series[0] || null;
    const period = latest ? kpiMonth(latest, true) : "nessun dato";
    document.getElementById("kpiPeriodOperative").textContent = period;
    document.getElementById("kpiPeriodManagement").textContent = period;
    document.getElementById("kpiEmpty").classList.toggle("d-none", series.length !== 0);
    document.getElementById("kpiComputedAt").textContent = latest?.computedAt
      ? `Ultimo calcolo: ${new Date(latest.computedAt).toLocaleString("it-IT", {
        day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"
      })}` : "";
    if (latest) {
      renderKpiCards("kpiOperativeCards", operativeKpis, latest);
      renderKpiCards("kpiManagementCards", managementKpis, latest);
    } else {
      document.getElementById("kpiOperativeCards").replaceChildren();
      document.getElementById("kpiManagementCards").replaceChildren();
    }
    renderKpiTable(series);
    renderKpiChart(series);
  }

  async function loadKpis() {
    const request = ++statsRequest;
    const currentSession = sessionEpoch;
    const error = document.getElementById("kpiError");
    error.classList.add("d-none");
    renderKpis([]);
    document.getElementById("kpiEmpty").classList.add("d-none");
    document.getElementById("kpiComputedAt").textContent = "Caricamento statistiche…";
    try {
      const query = new URLSearchParams({ scope: document.getElementById("kpiScopeSelect").value,
        months: document.getElementById("kpiMonthsSelect").value });
      const payload = await getJson(`/api/kpi?${query}`);
      if (request !== statsRequest || currentSession !== sessionEpoch || statsScreen.hidden) return;
      renderKpis(Array.isArray(payload.series) ? payload.series : []);
    } catch (failure) {
      if (request !== statsRequest || currentSession !== sessionEpoch || statsScreen.hidden) return;
      renderKpis([]);
      error.textContent = "Impossibile caricare i KPI dal backend.";
      error.classList.remove("d-none");
      document.getElementById("kpiEmpty").classList.add("d-none");
    }
  }

  function showStats() {
    homeScreen.hidden = true;
    calendarScreen.hidden = true;
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = true;
    settingsScreen.hidden = true;
    statsScreen.hidden = false;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month", "address-book-page");
    document.body.classList.add("app-page");
    setActiveNav("statsNav");
    document.title = "Dati e Statistiche • Fisio e Sports";
    loadKpis();
  }

  function showTrash() {
    leaveStats();
    homeScreen.hidden = true;
    calendarScreen.hidden = true;
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = false;
    settingsScreen.hidden = true;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month", "address-book-page");
    document.body.classList.add("app-page");
    setActiveNav("calendarNav");
    document.title = "Cestino appuntamenti • Fisio e Sports";
    loadTrash();
  }

  async function loadTrash(successMessage = "") {
    const request = ++trashRequest;
    const currentSession = sessionEpoch;
    const rows = document.getElementById("trashRows");
    const error = document.getElementById("trashError");
    const success = document.getElementById("trashSuccess");
    rows.replaceChildren();
    error.classList.add("d-none");
    success.classList.add("d-none");
    document.getElementById("trashEmpty").classList.add("d-none");
    document.getElementById("trashTableWrap").classList.add("d-none");
    try {
      const entries = await getJson("/api/calendar/trash");
      if (request !== trashRequest || currentSession !== sessionEpoch) return;
      const sort = document.getElementById("trashSort").value;
      if (sort) entries.sort((a, b) => (sort === "asc" ? 1 : -1)
        * a.patientFullName.localeCompare(b.patientFullName, "it", { sensitivity: "base" }));
      document.getElementById("trashEmpty").classList.toggle("d-none", entries.length !== 0);
      document.getElementById("trashTableWrap").classList.toggle("d-none", entries.length === 0);
      document.getElementById("emptyTrashBtn").disabled = entries.length === 0;
      for (const entry of entries) {
        const row = document.createElement("tr");
        for (const value of [entry.patientFullName, entry.startLabel, entry.endLabel, entry.notes || "-"]) {
          const cell = document.createElement("td");
          cell.textContent = value;
          row.appendChild(cell);
        }
        const actions = document.createElement("td");
        actions.className = "text-end";
        const restore = document.createElement("button");
        restore.type = "button";
        restore.className = "btn btn-sm btn-outline-primary me-2";
        restore.textContent = "Ripristina";
        restore.addEventListener("click", () => restoreTrashEntry(entry.id, restore));
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-sm btn-outline-danger btn-icon-only btn-trash-icon";
        remove.setAttribute("aria-label", "Elimina definitivamente appuntamento");
        remove.title = "Elimina definitivamente appuntamento";
        remove.addEventListener("click", () => confirmTrashDeletion(entry.id));
        actions.append(restore, remove);
        row.appendChild(actions);
        rows.appendChild(row);
      }
      if (successMessage) {
        success.textContent = successMessage;
        success.classList.remove("d-none");
      }
    } catch (failure) {
      if (request !== trashRequest || currentSession !== sessionEpoch) return;
      error.textContent = "Impossibile caricare il cestino dal backend.";
      error.classList.remove("d-none");
    }
  }

  async function restoreTrashEntry(id, button) {
    const currentSession = sessionEpoch;
    button.disabled = true;
    try {
      await apiRequest(`/api/calendar/trash/${encodeURIComponent(id)}`, { method: "PUT" });
      if (currentSession !== sessionEpoch) return;
      if (calendar) calendar.refetchEvents();
      loadTrash("Appuntamento ripristinato correttamente.");
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      const error = document.getElementById("trashError");
      error.textContent = failure.status === 409 ? "Ripristino impossibile: la fascia oraria è occupata o l'appuntamento non è più nel cestino."
        : failure.status === 404 ? "Appuntamento non disponibile per questo terapista."
          : "Impossibile ripristinare l'appuntamento.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
    }
  }

  function confirmTrashDeletion(id = null) {
    trashConfirmation = id;
    document.getElementById("trashConfirmTitle").textContent = id == null ? "Conferma svuotamento cestino" : "Conferma eliminazione";
    document.getElementById("trashConfirmText").textContent = id == null
      ? "Svuotare completamente il cestino? Questa azione è definitiva."
      : "Eliminare definitivamente questo appuntamento?";
    document.getElementById("trashConfirmError").classList.add("d-none");
    trashConfirmModal.show();
  }

  async function loadPatients() {
    const currentSession = sessionEpoch;
    const currentRequest = ++patientsRequest;
    const rows = document.getElementById("patientsRows");
    const error = document.getElementById("patientsError");
    const empty = document.getElementById("patientsEmpty");
    rows.replaceChildren();
    error.classList.add("d-none");
    empty.classList.add("d-none");
    try {
      const query = new URLSearchParams({
        q: document.getElementById("patientsQuery").value,
        sort: document.getElementById("patientsSort").value
      });
      if (patientsTreatedDate != null) query.set("treatedDate", patientsTreatedDate);
      const patients = await getJson(`/api/patients?${query}`);
      if (currentSession !== sessionEpoch || currentRequest !== patientsRequest) return;
      empty.classList.toggle("d-none", patients.length !== 0);
      for (const patient of patients) {
        const row = document.createElement("tr");
        row.className = "desktop-patient-row";
        row.tabIndex = 0;
        row.setAttribute("aria-label", `Apri scheda di ${patient.fullName}`);
        row.addEventListener("click", event => {
          if (!event.target.closest("button, a, input, select, textarea")) showPatientDetail(patient.id);
        });
        row.addEventListener("keydown", event => {
          if (event.target !== row || (event.key !== "Enter" && event.key !== " ")) return;
          event.preventDefault();
          showPatientDetail(patient.id);
        });
        const nameCell = document.createElement("td");
        const nameButton = document.createElement("button");
        nameButton.type = "button";
        nameButton.className = "btn btn-link p-0 patient-name-link text-start";
        nameButton.textContent = patient.fullName;
        nameButton.addEventListener("click", () => showPatientDetail(patient.id));
        nameCell.appendChild(nameButton);
        row.appendChild(nameCell);
        for (const value of [patient.createdDateLabel, patient.phone || ""]) {
          const cell = document.createElement("td");
          cell.textContent = value;
          row.appendChild(cell);
        }
        const actions = document.createElement("td");
        actions.className = "text-end address-book-actions";
        const history = document.createElement("button");
        history.type = "button";
        history.className = "btn btn-sm btn-outline-secondary me-2";
        history.textContent = "Cronologia Trattamenti";
        history.addEventListener("click", () => showTreatments(patient.id, patient.fullName));
        actions.appendChild(history);
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-sm btn-outline-danger btn-icon-only btn-trash-icon";
        remove.setAttribute("aria-label", "Elimina paziente");
        remove.title = "Elimina paziente";
        remove.addEventListener("click", () => openDeletePatient(patient));
        actions.appendChild(remove);
        row.appendChild(actions);
        rows.appendChild(row);
      }
    } catch (failure) {
      if (currentSession !== sessionEpoch || currentRequest !== patientsRequest) return;
      error.textContent = "Impossibile caricare la rubrica dal backend.";
      error.classList.remove("d-none");
    }
  }

  function setPatientField(name, value) {
    const field = document.getElementById("patientEditForm").elements.namedItem(name);
    if (!field) return;
    if (value === "true") value = "si";
    if (value === "false") value = "no";
    const text = value == null ? "" : String(value);
    field.value = text;
    if (field.tagName === "SELECT" && text && field.value !== text) {
      const option = document.createElement("option");
      option.value = text;
      option.textContent = text;
      option.dataset.temporary = "true";
      field.appendChild(option);
      field.value = text;
    }
  }

  function displayFreeNotes(value) {
    if (!value) return "";
    try {
      const notes = JSON.parse(value);
      if (notes && typeof notes === "object" && !Array.isArray(notes)) {
        if (typeof notes.note === "string") return notes.note;
        return Object.entries(notes).filter(([, text]) => text != null && String(text).length > 0)
          .map(([key, text]) => `${key}: ${text}`).join("\n") || value;
      }
    } catch (ignored) {
      // Le note storiche possono essere testo semplice.
    }
    return value;
  }

  function renderPatientSummary() {
    const form = document.getElementById("patientEditForm");
    const content = document.getElementById("patientSummaryContent");
    content.replaceChildren();
    content.classList.remove("text-muted");
    const groups = [
      { title: "Dati paziente", fields: [] },
      { title: "Scheda anamnesi", fields: [] }
    ];
    for (const field of Array.from(form.elements)) {
      if (!field.name || field.type === "hidden" || !field.value.trim()) continue;
      const label = field.parentElement.querySelector("label");
      if (!label) continue;
      const value = field.tagName === "SELECT"
        ? field.options[field.selectedIndex].textContent.trim()
        : field.value.trim();
      if (!value || value === "-") continue;
      const title = label.textContent.trim().replace(/\s*\(separat[ioe].*\)$/i, "");
      const group = ["fullName", "email", "phone"].includes(field.name) ? groups[0] : groups[1];
      group.fields.push({ title, value, wide: field.tagName === "TEXTAREA" || value.length > 80 });
    }
    for (const group of groups) {
      const heading = document.createElement("h6");
      heading.className = "mb-3";
      heading.textContent = group.title;
      content.appendChild(heading);
      if (group.fields.length === 0) {
        const empty = document.createElement("p");
        empty.className = "text-muted mb-4";
        empty.textContent = group.title === "Scheda anamnesi"
          ? "Nessuna anamnesi compilata. Usa Modifica per aggiungere dati."
          : "Nessun dato disponibile.";
        content.appendChild(empty);
        continue;
      }
      const row = document.createElement("div");
      row.className = "row g-3 mb-4";
      for (const item of group.fields) {
        const cell = document.createElement("div");
        cell.className = item.wide ? "col-12" : "col-12 col-md-6";
        const name = document.createElement("div");
        name.className = "small text-muted";
        name.textContent = item.title;
        const value = document.createElement("div");
        value.className = "fw-medium";
        value.style.whiteSpace = "pre-line";
        value.textContent = item.value;
        cell.append(name, value);
        row.appendChild(cell);
      }
      content.appendChild(row);
    }
  }

  function resetMergeCandidates() {
    const select = document.getElementById("mergeCandidateSelect");
    const none = document.createElement("option");
    none.value = "";
    none.textContent = "Nessuna unione (mantieni contatti separati)";
    select.replaceChildren(none);
    document.getElementById("mergeCandidatesBox").classList.add("d-none");
    document.getElementById("mergeTargetId").value = "";
  }

  async function refreshMergeCandidates() {
    const currentSession = sessionEpoch;
    const currentRequest = patientDetailRequest;
    const currentCandidatesRequest = ++mergeCandidatesRequest;
    const id = document.getElementById("editPatientId").value;
    const fullName = document.getElementById("editPatientFullName").value.trim();
    resetMergeCandidates();
    if (!id || !fullName) return;
    try {
      const query = new URLSearchParams({ fullName });
      const candidates = await getJson(`/api/patients/${encodeURIComponent(id)}/merge-candidates?${query}`);
      if (currentSession !== sessionEpoch || currentRequest !== patientDetailRequest
          || currentCandidatesRequest !== mergeCandidatesRequest) return;
      const select = document.getElementById("mergeCandidateSelect");
      for (const candidate of candidates) {
        const suffix = `${candidate.phone ? ` • ${candidate.phone}` : ""}${candidate.createdDateLabel ? ` • creato il ${candidate.createdDateLabel}` : ""}`;
        const option = document.createElement("option");
        option.value = String(candidate.id);
        option.textContent = `${candidate.fullName}${suffix}`;
        select.appendChild(option);
      }
      document.getElementById("mergeCandidatesBox").classList.toggle("d-none", candidates.length === 0);
    } catch (failure) {
      if (currentSession === sessionEpoch && currentRequest === patientDetailRequest
          && currentCandidatesRequest === mergeCandidatesRequest) resetMergeCandidates();
    }
  }

  async function showPatientDetail(id, successMessage = "") {
    const currentSession = sessionEpoch;
    const currentRequest = ++patientDetailRequest;
    const form = document.getElementById("patientEditForm");
    const saveButton = form.querySelector('button[type="submit"]');
    const summary = document.getElementById("patientSummary");
    const toggle = document.getElementById("patientEditToggle");
    const summaryError = document.getElementById("patientSummaryError");
    form.querySelectorAll('option[data-temporary="true"]').forEach(option => option.remove());
    form.reset();
    form.hidden = true;
    summary.hidden = false;
    toggle.hidden = true;
    toggle.textContent = "Modifica";
    saveButton.disabled = true;
    document.getElementById("patientEditError").classList.add("d-none");
    summaryError.classList.add("d-none");
    document.getElementById("patientSummarySuccess").classList.add("d-none");
    document.getElementById("patientSummaryContent").textContent = "Caricamento scheda...";
    document.getElementById("patientSummaryContent").classList.add("text-muted");
    document.getElementById("patientModalTitle").textContent = "Dettagli paziente";
    document.getElementById("editPatientId").value = String(id);
    resetMergeCandidates();
    patientModal.show();
    try {
      const [patient, anamnesis] = await Promise.all([
        getJson(`/api/patients/${encodeURIComponent(id)}`),
        getJson(`/api/patients/${encodeURIComponent(id)}/anamnesis`)
      ]);
      if (currentSession !== sessionEpoch || currentRequest !== patientDetailRequest) return;
      setPatientField("fullName", patient.fullName);
      setPatientField("email", patient.email);
      setPatientField("phone", patient.phone);
      for (const [key, value] of Object.entries(anamnesis)) {
        setPatientField(key, key === "freeNotesJson" ? displayFreeNotes(value) : value);
      }
      document.getElementById("patientModalTitle").textContent = patient.fullName;
      renderPatientSummary();
      toggle.hidden = false;
      if (successMessage) {
        const success = document.getElementById("patientSummarySuccess");
        success.textContent = successMessage;
        success.classList.remove("d-none");
      }
    } catch (failure) {
      if (currentSession !== sessionEpoch || currentRequest !== patientDetailRequest) return;
      document.getElementById("patientSummaryContent").replaceChildren();
      if (successMessage) {
        const success = document.getElementById("patientSummarySuccess");
        success.textContent = successMessage;
        success.classList.remove("d-none");
      }
      summaryError.textContent = successMessage
        ? "Salvataggio riuscito, ma non riesco a ricaricare la scheda."
        : failure.status === 404
          ? "Paziente non disponibile per questo terapista."
          : "Impossibile caricare la scheda dal backend.";
      summaryError.classList.remove("d-none");
    } finally {
      if (currentSession === sessionEpoch && currentRequest === patientDetailRequest) saveButton.disabled = false;
    }
  }

  async function savePatientDetails() {
    const currentSession = sessionEpoch;
    const form = document.getElementById("patientEditForm");
    const button = form.querySelector('button[type="submit"]');
    const mergeButton = document.getElementById("confirmMergePatientBtn");
    const toggle = document.getElementById("patientEditToggle");
    const error = document.getElementById("patientEditError");
    const id = document.getElementById("editPatientId").value;
    button.disabled = true;
    mergeButton.disabled = true;
    toggle.disabled = true;
    error.classList.add("d-none");
    try {
      await apiRequest(`/api/patients/${encodeURIComponent(id)}`, {
        method: "PUT",
        headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
        body: new URLSearchParams(new FormData(form)).toString()
      });
      if (currentSession !== sessionEpoch) return;
      const merged = Boolean(document.getElementById("mergeTargetId").value);
      mergeConfirmModal.hide();
      if (merged) {
        patientModal.hide();
        document.getElementById("patientsCreated").textContent = "Contatti uniti correttamente.";
        document.getElementById("patientsCreated").classList.remove("d-none");
      } else {
        document.getElementById("patientsCreated").textContent = "Dati paziente e anamnesi salvati correttamente.";
        document.getElementById("patientsCreated").classList.remove("d-none");
        await showPatientDetail(id, "Dati paziente e anamnesi salvati correttamente.");
      }
      if (currentSession !== sessionEpoch) return;
      loadPatients();
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      mergeConfirmModal.hide();
      error.textContent = failure.status === 400
        ? "Controlla i dati della scheda e riprova."
        : failure.status === 404
          ? "Paziente non disponibile per questo terapista."
          : "Impossibile salvare la scheda nel backend.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
      mergeButton.disabled = false;
      toggle.disabled = false;
    }
  }

  function openDeletePatient(patient) {
    document.getElementById("deletePatientError").classList.add("d-none");
    document.getElementById("deletePatientId").value = String(patient.id);
    document.getElementById("deletePatientName").textContent = patient.fullName;
    const count = patient.linkedAppointmentsCount || 0;
    document.getElementById("forceDeleteWithLinkedAppointments").value = count > 0 ? "1" : "0";
    document.getElementById("deletePatientPromptText").textContent = count > 0
      ? "Confermi l'eliminazione definitiva del paziente" : "Vuoi eliminare il paziente";
    document.getElementById("deleteDangerZone").classList.toggle("d-none", count === 0);
    document.getElementById("linkedAppointmentsCount").textContent = String(count);
    document.getElementById("linkedAppointmentsLabel").textContent = count === 1
      ? "appuntamento collegato" : "appuntamenti collegati";
    deletePatientModal.show();
  }

  document.getElementById("loginForm").addEventListener("submit", async event => {
    event.preventDefault();
    manualLoginStarted = true;
    const loginButton = event.target.querySelector('button[type="submit"]');
    loginButton.disabled = true;
    loginError.classList.add("d-none");
    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;
    const bytes = new TextEncoder().encode(`${username}:${password}`);
    authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`;
    try {
      const identity = await getJson("/api/me");
      let rememberUnavailable = false;
      try {
        const issued = await (await apiRequest("/api/auth/remember", { method: "POST" })).json();
        if (!/^[A-Za-z0-9_-]{43}$/.test(issued.token)) throw new Error("invalid_token");
        authorization = `Bearer ${issued.token}`;
        if (window.desktopBridge) window.desktopBridge.saveToken(issued.token);
        else rememberUnavailable = true;
      } catch (failure) {
        rememberUnavailable = true;
      }
      enterApp(identity);
      if (rememberUnavailable) showAuthNotice("Accesso automatico non disponibile; questa sessione resta attiva fino all'uscita.");
    } catch (error) {
      authorization = null;
      loginError.textContent = error.status === 401
        ? "Credenziali non valide"
        : error.status === 503
          ? "Database non disponibile. Controlla /ready e riavvia il backend locale."
          : "Backend non raggiungibile. Controlla /ready e avvialo con ./run-backend-locale.sh.";
      loginError.classList.remove("d-none");
      document.getElementById("password").value = "";
    } finally {
      loginButton.disabled = false;
    }
  });

  document.getElementById("homeNav").addEventListener("click", showHome);
  document.getElementById("calendarNav").addEventListener("click", () => showCalendar());
  document.getElementById("settingsNav").addEventListener("click", showSettings);
  document.getElementById("statsNav").addEventListener("click", showStats);
  document.getElementById("kpiScopeSelect").addEventListener("change", loadKpis);
  document.getElementById("kpiMonthsSelect").addEventListener("change", loadKpis);
  document.getElementById("refreshWhatsAppBtn").addEventListener("click", loadWhatsAppStatus);
  document.getElementById("startWhatsAppBtn").addEventListener("click", () => controlWhatsApp("start"));
  document.getElementById("stopWhatsAppBtn").addEventListener("click", () => controlWhatsApp("stop"));
  window.setInterval(() => {
    if (!appScreen.hidden && !settingsScreen.hidden) loadWhatsAppStatus();
  }, 5000);
  document.getElementById("openHomeReminderBtn").addEventListener("click", () => openReminderPreview(appointmentDate(new Date())));
  document.getElementById("homeSendRemindersButton").addEventListener("click", () => openReminderPreview(appointmentDate(new Date())));
  document.getElementById("todayRemindersButton").addEventListener("click", () => openReminderPreview(appointmentDate(new Date())));
  document.getElementById("reminderPreviewDate").addEventListener("change", () => {
    preselectedReminderId = null;
    loadReminderPreview();
  });
  document.getElementById("sendRemindersBtn").addEventListener("click", sendSelectedReminders);
  document.getElementById("saveReminderTemplateBtn").addEventListener("click", saveReminderTemplate);
  document.getElementById("reminderPreviewTemplate").addEventListener("input", renderMessages);
  document.getElementById("sendSingleReminderBtn").addEventListener("click", () => {
    if (!selectedAppointment?.start) return;
    const date = appointmentDate(selectedAppointment.start);
    const id = selectedAppointment.id;
    document.getElementById("eventModal").addEventListener("hidden.bs.modal",
      () => openReminderPreview(date, id), { once: true });
    eventModal.hide();
  });
  document.getElementById("backToCalendarBtn").addEventListener("click", () => showCalendar());
  document.getElementById("trashSort").addEventListener("change", () => loadTrash());
  document.getElementById("emptyTrashBtn").addEventListener("click", () => confirmTrashDeletion());
  document.getElementById("trashConfirmBtn").addEventListener("click", async event => {
    const id = trashConfirmation;
    const currentSession = sessionEpoch;
    const button = event.currentTarget;
    button.disabled = true;
    try {
      const response = await apiRequest(id == null ? "/api/calendar/trash"
        : `/api/calendar/trash/${encodeURIComponent(id)}`, { method: "DELETE" });
      const count = id == null ? (await response.json()).deleted : null;
      if (currentSession !== sessionEpoch) return;
      trashConfirmModal.hide();
      if (calendar) calendar.refetchEvents();
      loadTrash(id == null ? `Cestino svuotato. Appuntamenti eliminati: ${count}.`
        : "Appuntamento eliminato definitivamente.");
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      const error = document.getElementById("trashConfirmError");
      error.textContent = failure.status === 404 ? "Appuntamento non disponibile per questo terapista."
        : failure.status === 409 ? "L'appuntamento non è più nel cestino."
          : "Impossibile completare l'operazione.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("treatmentsNav").addEventListener("click", () => showTreatments());
  document.getElementById("completeAppointmentBtn").addEventListener("click", () => {
    const selected = selectedAppointment;
    if (!selected || !selected.start || selected.extendedProps.state !== "SCHEDULED"
        || selected.allDay || selected.extendedProps.nonTreatmentEvent || selected.extendedProps.patientId == null
        || !selected.end || selected.end > new Date()) return;
    const form = document.getElementById("completeTreatmentForm");
    form.reset();
    document.getElementById("treatmentPlanTitle").value = `Trattamento da appuntamento ${selected.start.toLocaleDateString("it-IT")}`;
    document.getElementById("treatmentTotalSessionsPlanned").value = "1";
    document.getElementById("treatmentExpectedEndDate").value = appointmentDate(selected.end || selected.start);
    document.getElementById("treatmentSessionOutcome").value = "Sessione completata da appuntamento";
    document.getElementById("treatmentNotes").value = selected.extendedProps.notes || "";
    document.getElementById("completeTreatmentError").classList.add("d-none");
    document.getElementById("eventModal").addEventListener("hidden.bs.modal", () => completeTreatmentModal.show(), { once: true });
    eventModal.hide();
  });
  document.getElementById("completeTreatmentForm").addEventListener("submit", async event => {
    event.preventDefault();
    if (!selectedAppointment) return;
    const currentSession = sessionEpoch;
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const error = document.getElementById("completeTreatmentError");
    const fields = new URLSearchParams();
    for (const [key, id] of Object.entries({
      planTitle: "treatmentPlanTitle", totalSessionsPlanned: "treatmentTotalSessionsPlanned",
      frequencyPerWeek: "treatmentFrequencyPerWeek", expectedEndDate: "treatmentExpectedEndDate",
      painScorePre: "treatmentPainScorePre", painScorePost: "treatmentPainScorePost",
      goals: "treatmentGoals", sessionOutcome: "treatmentSessionOutcome",
      homeExercises: "treatmentHomeExercises", notes: "treatmentNotes"
    })) fields.set(key, document.getElementById(id).value.trim());
    button.disabled = true;
    error.classList.add("d-none");
    try {
      await apiRequest(`/api/treatments/appointments/${encodeURIComponent(selectedAppointment.id)}`, {
        method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
        body: fields.toString()
      });
      if (currentSession !== sessionEpoch) return;
      completeTreatmentModal.hide();
      selectedAppointment = null;
      document.getElementById("appointmentCreated").textContent = "Trattamento completato con successo.";
      document.getElementById("appointmentCreated").classList.remove("d-none");
      calendar.refetchEvents();
      loadTodayAgenda();
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      error.textContent = failure.status === 404 ? "Appuntamento non disponibile per questo terapista."
        : failure.status === 409 ? "Questo appuntamento non può essere completato nello stato attuale."
          : failure.status === 400 ? "Verifica i dati del trattamento." : "Impossibile completare il trattamento. Riprova.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("editAppointmentBtn").addEventListener("click", editSelectedAppointment);
  document.getElementById("deleteAppointmentBtn").addEventListener("click", () => {
    if (!selectedAppointment) return;
    document.getElementById("deleteAppointmentError").classList.add("d-none");
    document.getElementById("eventModal").addEventListener("hidden.bs.modal", () => confirmDeleteAppointmentModal.show(), { once: true });
    eventModal.hide();
  });
  document.getElementById("confirmDeleteAppointmentBtn").addEventListener("click", async () => {
    if (!selectedAppointment) return;
    const currentSession = sessionEpoch;
    const button = document.getElementById("confirmDeleteAppointmentBtn");
    button.disabled = true;
    try {
      await apiRequest(`/api/calendar/${encodeURIComponent(selectedAppointment.id)}`, { method: "DELETE" });
      if (currentSession !== sessionEpoch) return;
      confirmDeleteAppointmentModal.hide();
      selectedAppointment = null;
      document.getElementById("appointmentCreated").textContent = "Appuntamento eliminato correttamente.";
      document.getElementById("appointmentCreated").classList.remove("d-none");
      calendar.refetchEvents();
      loadTodayAgenda();
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      const error = document.getElementById("deleteAppointmentError");
      error.textContent = failure.status === 404 ? "Appuntamento non disponibile per questo terapista."
        : failure.status === 409 ? "Questo appuntamento non può essere eliminato nello stato attuale."
          : "Impossibile eliminare l'appuntamento. Riprova.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("appointmentAllDay").addEventListener("change", updateAppointmentForm);
  document.getElementById("appointmentGeneric").addEventListener("change", updateAppointmentForm);
  document.getElementById("appointmentStartTime").addEventListener("change", () => {
    const date = document.getElementById("appointmentDate").value;
    const start = document.getElementById("appointmentStartTime").value;
    if (!date || !start) return;
    const end = new Date(new Date(`${date}T${start}`).getTime() + 60 * 60000);
    document.getElementById("appointmentEndTime").value = appointmentTime(end);
  });
  document.getElementById("appointmentPatientName").addEventListener("input", async event => {
    const request = ++appointmentSuggestionsRequest;
    const currentSession = sessionEpoch;
    const menu = document.getElementById("appointmentPatientSuggestions");
    const query = event.target.value.trim();
    menu.replaceChildren();
    menu.classList.add("d-none");
    if (!query || document.getElementById("appointmentGeneric").checked) return;
    try {
      const patients = await getJson(`/api/patients?q=${encodeURIComponent(query)}`);
      if (request !== appointmentSuggestionsRequest || currentSession !== sessionEpoch
          || document.getElementById("appointmentGeneric").checked) return;
      for (const patient of patients.slice(0, 8)) {
        const choice = document.createElement("button");
        choice.type = "button";
        choice.className = "patient-suggestion-item";
        choice.setAttribute("role", "option");
        choice.textContent = patient.fullName;
        choice.addEventListener("mousedown", click => {
          click.preventDefault();
          document.getElementById("appointmentPatientName").value = patient.fullName;
          menu.classList.add("d-none");
        });
        menu.appendChild(choice);
      }
      menu.classList.toggle("d-none", menu.childElementCount === 0);
    } catch (failure) {
      menu.classList.add("d-none");
    }
  });
  document.getElementById("appointmentPatientName").addEventListener("blur", () => {
    setTimeout(() => document.getElementById("appointmentPatientSuggestions").classList.add("d-none"), 120);
  });
  document.getElementById("appointmentForm").addEventListener("submit", async event => {
    event.preventDefault();
    const currentSession = sessionEpoch;
    const appointmentId = editingAppointmentId;
    const waitlistId = appointmentId == null ? convertingWaitlistId : null;
    const button = document.getElementById("saveAppointmentBtn");
    const date = document.getElementById("appointmentDate").value;
    const allDay = document.getElementById("appointmentAllDay").checked;
    const generic = document.getElementById("appointmentGeneric").checked;
    const start = allDay ? `${date}T00:00:00` : `${date}T${document.getElementById("appointmentStartTime").value}:00`;
    const nextDay = new Date(`${date}T00:00:00`);
    nextDay.setDate(nextDay.getDate() + 1);
    const end = allDay ? `${appointmentDate(nextDay)}T00:00:00` : `${date}T${document.getElementById("appointmentEndTime").value}:00`;
    if (!allDay && (new Date(start) >= new Date(end)
        || !/:(00|15|30|45):00$/.test(start) || !/:(00|15|30|45):00$/.test(end))) {
      showAppointmentError("Controlla gli orari: durata minima 15 minuti e scatti di 15 minuti.");
      return;
    }
    const body = new URLSearchParams({
      patientName: document.getElementById("appointmentPatientName").value.trim(),
      patientPhone: document.getElementById("appointmentPatientPhone").value.trim(),
      start, end, allDay: String(allDay), nonTreatmentEvent: String(generic),
      notes: document.getElementById("appointmentNotes").value.trim()
    });
    button.disabled = true;
    document.getElementById("appointmentFormError").classList.add("d-none");
    try {
      await apiRequest(appointmentId == null ? "/api/calendar" : `/api/calendar/${encodeURIComponent(appointmentId)}`, {
        method: appointmentId == null ? "POST" : "PUT",
        headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" }, body: body.toString()
      });
      if (currentSession !== sessionEpoch) return;
      appointmentModal.hide();
      editingAppointmentId = null;
      convertingWaitlistId = null;
      document.getElementById("appointmentCreated").textContent = appointmentId == null
        ? "Appuntamento salvato correttamente." : "Appuntamento modificato correttamente.";
      const homeConfirmation = document.getElementById("homeAppointmentCreated");
      homeConfirmation.classList.remove("alert-warning");
      homeConfirmation.classList.add("alert-success");
      homeConfirmation.textContent = appointmentId == null
        ? "Appuntamento salvato correttamente." : "Appuntamento modificato correttamente.";
      if (homeScreen.hidden) document.getElementById("appointmentCreated").classList.remove("d-none");
      else homeConfirmation.classList.remove("d-none");
      if (calendar) calendar.refetchEvents();
      loadTodayAgenda();
      if (waitlistId != null) {
        try {
          await apiRequest(`/api/waitlist/${encodeURIComponent(waitlistId)}`, { method: "DELETE" });
        } catch (failure) {
          if (currentSession !== sessionEpoch) return;
          homeConfirmation.classList.remove("alert-success");
          homeConfirmation.classList.add("alert-warning");
          homeConfirmation.textContent = "Appuntamento salvato, ma non è stato possibile confermare la rimozione dalla lista d'attesa. Verifica la lista prima di rimuovere il contatto manualmente.";
          homeConfirmation.classList.remove("d-none");
        }
        if (currentSession === sessionEpoch) loadWaitlist();
      }
    } catch (failure) {
      if (currentSession === sessionEpoch) showAppointmentError(failure.status === 409 ? "Fascia oraria occupata o appuntamento non più modificabile. Aggiorna il calendario e riprova."
        : failure.status === 400 && allDay && !generic ? "Per un evento tutto il giorno scegli un paziente già presente e controlla la data."
          : failure.status === 404 ? "Appuntamento non disponibile per questo terapista."
          : "Impossibile salvare l'appuntamento. Controlla i dati e riprova.");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("patientsNav").addEventListener("click", () => showPatients());
  document.getElementById("todayPatientsButton").addEventListener("click", () => showPatients(appointmentDate(new Date())));
  document.getElementById("patientsClearFilter").addEventListener("click", () => {
    setPatientsFilter(null);
    loadPatients();
  });
  document.getElementById("patientsSearchForm").addEventListener("submit", event => {
    event.preventDefault();
    setPatientsFilter(null);
    loadPatients();
  });
  document.getElementById("patientsSort").addEventListener("change", loadPatients);
  document.getElementById("editPatientFullName").addEventListener("blur", refreshMergeCandidates);
  document.getElementById("patientEditToggle").addEventListener("click", () => {
    const form = document.getElementById("patientEditForm");
    const summary = document.getElementById("patientSummary");
    const toggle = document.getElementById("patientEditToggle");
    if (form.hidden) {
      summary.hidden = true;
      form.hidden = false;
      toggle.textContent = "Annulla modifica";
      refreshMergeCandidates();
    } else {
      showPatientDetail(document.getElementById("editPatientId").value);
    }
  });
  document.getElementById("mergeCandidateSelect").addEventListener("change", event => {
    document.getElementById("mergeTargetId").value = event.target.value;
  });
  document.getElementById("patientEditForm").addEventListener("submit", event => {
    event.preventDefault();
    const targetId = document.getElementById("mergeTargetId").value;
    if (targetId) {
      document.getElementById("mergeConfirmSourceName").textContent = document.getElementById("editPatientFullName").value.trim();
      const select = document.getElementById("mergeCandidateSelect");
      document.getElementById("mergeConfirmTargetName").textContent = select.options[select.selectedIndex].textContent;
      mergeConfirmModal.show();
    } else {
      savePatientDetails();
    }
  });
  document.getElementById("confirmMergePatientBtn").addEventListener("click", savePatientDetails);
  document.getElementById("deletePatientForm").addEventListener("submit", async event => {
    event.preventDefault();
    const currentSession = sessionEpoch;
    const id = document.getElementById("deletePatientId").value;
    const force = document.getElementById("forceDeleteWithLinkedAppointments").value;
    const button = event.target.querySelector('button[type="submit"]');
    button.disabled = true;
    try {
      await apiRequest(`/api/patients/${encodeURIComponent(id)}?force=${force}`, { method: "DELETE" });
      if (currentSession !== sessionEpoch) return;
      deletePatientModal.hide();
      document.getElementById("patientsCreated").textContent = "Paziente eliminato correttamente.";
      document.getElementById("patientsCreated").classList.remove("d-none");
      loadPatients();
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      const error = document.getElementById("deletePatientError");
      error.textContent = "Impossibile eliminare il paziente. Ricarica la rubrica e riprova.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
    }
  });
  function openCreatePatient() {
    document.getElementById("createPatientError").classList.add("d-none");
    createPatientModal.show();
  }
  document.getElementById("homeCreatePatientButton").addEventListener("click", openCreatePatient);
  document.getElementById("homeCreateAppointmentButton").addEventListener("click", () => openAppointmentModal());
  document.getElementById("patientsCreateButton").addEventListener("click", openCreatePatient);
  document.getElementById("createPatientForm").addEventListener("submit", async event => {
    event.preventDefault();
    const currentSession = sessionEpoch;
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    const error = document.getElementById("createPatientError");
    button.disabled = true;
    error.classList.add("d-none");
    try {
      await apiRequest("/api/patients", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
        body: new URLSearchParams({
          fullName: document.getElementById("createPatientFullName").value,
          email: document.getElementById("createPatientEmail").value,
          phone: document.getElementById("createPatientPhone").value
        }).toString()
      });
      if (currentSession !== sessionEpoch) return;
      form.reset();
      createPatientModal.hide();
      if (patientsScreen.hidden) {
        document.getElementById("homePatientCreated").classList.remove("d-none");
      } else {
        document.getElementById("patientsQuery").value = "";
        document.getElementById("patientsSort").value = "created-desc";
        setPatientsFilter(null);
        document.getElementById("patientsCreated").classList.remove("d-none");
        loadPatients();
      }
    } catch (failure) {
      if (currentSession !== sessionEpoch) return;
      error.textContent = failure.status === 400
        ? "Controlla nome, email e telefono."
        : "Impossibile salvare il paziente nel backend.";
      error.classList.remove("d-none");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("todayAppointmentsButton").addEventListener("click", () => showCalendar(true));
  document.getElementById("openDayButton").addEventListener("click", () => showCalendar(true));
  document.getElementById("waitlistForm").addEventListener("submit", async event => {
    event.preventDefault();
    const currentSession = sessionEpoch;
    const form = event.target;
    const button = form.querySelector('button[type="submit"]');
    button.disabled = true;
    document.getElementById("waitlistError").classList.add("d-none");
    try {
      await apiRequest("/api/waitlist", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded; charset=UTF-8" },
        body: new URLSearchParams({
          fullName: document.getElementById("waitlistPatientName").value,
          phone: document.getElementById("waitlistPhone").value
        }).toString()
      });
      if (currentSession === sessionEpoch) {
        form.reset();
        loadWaitlist();
      }
    } catch (error) {
      if (currentSession === sessionEpoch) showWaitlistError("Impossibile aggiungere il contatto. Verifica nome e telefono.");
    } finally {
      button.disabled = false;
    }
  });
  const logoutConfirmForm = document.getElementById("logoutConfirmForm");
  const logoutConfirmCheck = document.getElementById("logoutConfirmCheck");
  const logoutConfirmSubmit = document.getElementById("logoutConfirmSubmit");
  document.getElementById("logoutButton").addEventListener("click", () => {
    logoutConfirmForm.reset();
    logoutConfirmSubmit.disabled = true;
    logoutConfirmModal.show();
  });
  logoutConfirmCheck.addEventListener("change", () => {
    logoutConfirmSubmit.disabled = !logoutConfirmCheck.checked;
  });
  logoutConfirmForm.addEventListener("submit", async event => {
    event.preventDefault();
    if (!logoutConfirmCheck.checked) return;
    logoutConfirmSubmit.disabled = true;
    logoutConfirmModal.hide();
    const previousAuthorization = authorization;
    const tokenCleared = window.desktopBridge ? window.desktopBridge.clearToken() : true;
    manualLoginStarted = true;
    sessionEpoch++;
    leaveStats();
    setActiveNav(null);
    document.getElementById("kpiScopeSelect").value = "me";
    document.getElementById("kpiMonthsSelect").value = "12";
    renderKpis([]);
    document.getElementById("kpiEmpty").classList.add("d-none");
    patientsRequest++;
    patientDetailRequest++;
    mergeCandidatesRequest++;
    treatmentsRequest++;
    trashRequest++;
    reminderPreviewRequest++;
    whatsAppStatusRequest++;
    whatsAppStatus = null;
    updateWhatsAppControlButtons();
    patientModal.hide();
    appointmentModal.hide();
    eventModal.hide();
    completeTreatmentModal.hide();
    trashConfirmModal.hide();
    reminderPreviewModal.hide();
    reminderEntries = [];
    trashConfirmation = null;
    confirmDeleteAppointmentModal.hide();
    selectedAppointment = null;
    editingAppointmentId = null;
    convertingWaitlistId = null;
    createPatientModal.hide();
    deletePatientModal.hide();
    mergeConfirmModal.hide();
    authorization = null;
    if (calendar) {
      calendar.destroy();
      calendar = null;
    }
    document.getElementById("todayAgenda").replaceChildren();
    document.getElementById("appointmentsToday").textContent = "–";
    document.getElementById("patientsToday").textContent = "–";
    document.getElementById("remindersToday").textContent = "–";
    document.getElementById("waitlistEntries").replaceChildren();
    document.getElementById("waitlistCount").textContent = "";
    document.getElementById("waitlistForm").reset();
    document.getElementById("waitlistError").classList.add("d-none");
    document.getElementById("homeGreeting").textContent = "";
    document.getElementById("todayLabel").textContent = "";
    document.getElementById("username").value = "";
    document.getElementById("patientsRows").replaceChildren();
    document.getElementById("patientsQuery").value = "";
    document.getElementById("patientsSort").value = "created-desc";
    setPatientsFilter(null);
    document.getElementById("patientsError").classList.add("d-none");
    document.getElementById("patientsEmpty").classList.add("d-none");
    document.getElementById("patientEditForm").reset();
    document.getElementById("patientEditError").classList.add("d-none");
    document.getElementById("deletePatientError").classList.add("d-none");
    document.getElementById("createPatientForm").reset();
    document.getElementById("createPatientError").classList.add("d-none");
    document.getElementById("homePatientCreated").classList.add("d-none");
    document.getElementById("homeAppointmentCreated").classList.add("d-none");
    document.getElementById("patientsCreated").classList.add("d-none");
    document.getElementById("authNotice").classList.add("d-none");
    loginError.classList.add("d-none");
    appScreen.hidden = true;
    loginScreen.hidden = false;
    document.body.className = "auth-page app-page d-flex align-items-center justify-content-center";
    patientsScreen.hidden = true;
    treatmentsScreen.hidden = true;
    trashScreen.hidden = true;
    settingsScreen.hidden = true;
    document.title = "Login • Fisio e Sports";
    let revoked = true;
    if (previousAuthorization?.startsWith("Bearer ")) {
      try {
        const response = await fetch(`${apiBase}/api/auth/remember`, {
          method: "DELETE", headers: { Authorization: previousAuthorization }, cache: "no-store"
        });
        revoked = response.ok;
      } catch (failure) {
        revoked = false;
      }
    }
    if (!tokenCleared || !revoked) {
      const message = "Uscita completata, ma il token automatico potrebbe essere ancora valido. Accedi e ripeti Logout quando il backend è disponibile.";
      if (loginScreen.hidden) showAuthNotice(message);
      else {
        loginError.textContent = message;
        loginError.classList.remove("d-none");
      }
    }
  });
});
