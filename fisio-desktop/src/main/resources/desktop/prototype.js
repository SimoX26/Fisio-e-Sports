document.addEventListener("DOMContentLoaded", () => {
  const apiBase = "http://127.0.0.1:8081";
  const loginScreen = document.getElementById("loginScreen");
  const appScreen = document.getElementById("appScreen");
  const homeScreen = document.getElementById("homeScreen");
  const calendarScreen = document.getElementById("calendarScreen");
  const patientsScreen = document.getElementById("patientsScreen");
  const loginError = document.getElementById("loginError");
  const dataStatus = document.getElementById("dataStatus");
  const patientModal = new bootstrap.Modal(document.getElementById("patientModal"));
  const appointmentModal = new bootstrap.Modal(document.getElementById("appointmentModal"));
  const eventModal = new bootstrap.Modal(document.getElementById("eventModal"));
  const confirmDeleteAppointmentModal = new bootstrap.Modal(document.getElementById("confirmDeleteAppointmentModal"));
  const createPatientModal = new bootstrap.Modal(document.getElementById("createPatientModal"));
  const deletePatientModal = new bootstrap.Modal(document.getElementById("confirmDeletePatientModal"));
  const mergeConfirmModal = new bootstrap.Modal(document.getElementById("confirmMergePatientModal"));
  let authorization = null;
  let calendar = null;
  let selectedAppointment = null;
  let editingAppointmentId = null;
  let sessionEpoch = 0;
  let patientsRequest = 0;
  let patientDetailRequest = 0;
  let mergeCandidatesRequest = 0;
  let manualLoginStarted = false;
  let appointmentSuggestionsRequest = 0;

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
    updateAppointmentForm();
    appointmentModal.show();
  }

  function editSelectedAppointment() {
    const selected = selectedAppointment;
    if (!selected || !selected.start || selected.extendedProps.state !== "SCHEDULED") return;
    editingAppointmentId = selected.id;
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

  function showHome() {
    homeScreen.hidden = false;
    calendarScreen.hidden = true;
    patientsScreen.hidden = true;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month");
    document.body.classList.remove("address-book-page");
    document.body.classList.add("app-page");
    document.getElementById("homeNav").classList.add("active");
    document.getElementById("calendarNav").classList.remove("active");
    document.getElementById("patientsNav").classList.remove("active");
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
        const remove = document.createElement("button");
        remove.type = "button";
        remove.className = "btn btn-outline-danger btn-sm btn-icon-only btn-trash-icon";
        remove.setAttribute("aria-label", "Rimuovi contatto dalla lista di attesa");
        remove.title = "Rimuovi contatto dalla lista di attesa";
        remove.addEventListener("click", () => removeWaitlistEntry(entry.id));
        actions.appendChild(remove);
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
      headerToolbar: { left: "prev,next today", center: "title", right: "timeGridDay,timeGridWeek,dayGridMonth" },
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
        const completed = info.event.extendedProps.state === "COMPLETED" && (info.event.end || info.event.start) < new Date();
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
        document.getElementById("completeAppointmentBtn").classList.toggle("d-none", completed || allDay || generic);
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
    homeScreen.hidden = true;
    calendarScreen.hidden = false;
    patientsScreen.hidden = true;
    document.body.classList.remove("app-page");
    document.body.classList.remove("address-book-page");
    document.body.classList.add("calendar-gcal-page");
    document.getElementById("homeNav").classList.remove("active");
    document.getElementById("calendarNav").classList.add("active");
    document.getElementById("patientsNav").classList.remove("active");
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

  function showPatients() {
    homeScreen.hidden = true;
    calendarScreen.hidden = true;
    patientsScreen.hidden = false;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month");
    document.body.classList.add("app-page", "address-book-page");
    document.getElementById("homeNav").classList.remove("active");
    document.getElementById("calendarNav").classList.remove("active");
    document.getElementById("patientsNav").classList.add("active");
    document.title = "Rubrica Pazienti • Fisio e Sports";
    loadPatients();
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
  document.getElementById("openAppointmentModalBtn").addEventListener("click", () => openAppointmentModal());
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
      document.getElementById("appointmentCreated").textContent = appointmentId == null
        ? "Appuntamento salvato correttamente." : "Appuntamento modificato correttamente.";
      document.getElementById("appointmentCreated").classList.remove("d-none");
      calendar.refetchEvents();
      loadTodayAgenda();
    } catch (failure) {
      if (currentSession === sessionEpoch) showAppointmentError(failure.status === 409 ? "Fascia oraria occupata o appuntamento non più modificabile. Aggiorna il calendario e riprova."
        : failure.status === 400 && allDay && !generic ? "Per un evento tutto il giorno scegli un paziente già presente e controlla la data."
          : failure.status === 404 ? "Appuntamento non disponibile per questo terapista."
          : "Impossibile salvare l'appuntamento. Controlla i dati e riprova.");
    } finally {
      button.disabled = false;
    }
  });
  document.getElementById("patientsNav").addEventListener("click", showPatients);
  document.getElementById("patientsSearchForm").addEventListener("submit", event => {
    event.preventDefault();
    loadPatients();
  });
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
  document.getElementById("logoutButton").addEventListener("click", async () => {
    const previousAuthorization = authorization;
    const tokenCleared = window.desktopBridge ? window.desktopBridge.clearToken() : true;
    manualLoginStarted = true;
    sessionEpoch++;
    patientsRequest++;
    patientDetailRequest++;
    mergeCandidatesRequest++;
    patientModal.hide();
    appointmentModal.hide();
    eventModal.hide();
    confirmDeleteAppointmentModal.hide();
    selectedAppointment = null;
    editingAppointmentId = null;
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
    document.getElementById("patientsError").classList.add("d-none");
    document.getElementById("patientsEmpty").classList.add("d-none");
    document.getElementById("patientEditForm").reset();
    document.getElementById("patientEditError").classList.add("d-none");
    document.getElementById("deletePatientError").classList.add("d-none");
    document.getElementById("createPatientForm").reset();
    document.getElementById("createPatientError").classList.add("d-none");
    document.getElementById("homePatientCreated").classList.add("d-none");
    document.getElementById("patientsCreated").classList.add("d-none");
    document.getElementById("authNotice").classList.add("d-none");
    loginError.classList.add("d-none");
    appScreen.hidden = true;
    loginScreen.hidden = false;
    document.body.className = "auth-page app-page d-flex align-items-center justify-content-center";
    patientsScreen.hidden = true;
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
  document.getElementById("searchForm").addEventListener("submit", event => event.preventDefault());
});
