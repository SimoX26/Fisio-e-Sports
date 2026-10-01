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
  const createPatientModal = new bootstrap.Modal(document.getElementById("createPatientModal"));
  const deletePatientModal = new bootstrap.Modal(document.getElementById("confirmDeletePatientModal"));
  const mergeConfirmModal = new bootstrap.Modal(document.getElementById("confirmMergePatientModal"));
  let authorization = null;
  let calendar = null;
  let sessionEpoch = 0;
  let patientsRequest = 0;
  let patientDetailRequest = 0;
  let mergeCandidatesRequest = 0;

  function localDateTime(date) {
    const pad = number => String(number).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
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
    const eventModal = new bootstrap.Modal(document.getElementById("eventModal"));
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
      selectable: false,
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
      eventClick(info) {
        document.getElementById("eventModalTitle").textContent = info.event.title;
        document.getElementById("eventModalTime").textContent = info.event.start.toLocaleString("it-IT") + " – " + info.event.end.toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" });
        document.getElementById("eventModalNotes").textContent = info.event.extendedProps.notes || "Nessuna nota";
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
    const loginButton = event.target.querySelector('button[type="submit"]');
    loginButton.disabled = true;
    loginError.classList.add("d-none");
    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;
    const bytes = new TextEncoder().encode(`${username}:${password}`);
    authorization = `Basic ${btoa(String.fromCharCode(...bytes))}`;
    try {
      const identity = await getJson("/api/me");
      sessionEpoch++;
      document.getElementById("password").value = "";
      const prefix = new Date().getHours() > 15 ? "Buonasera" : "Buongiorno";
      const displayName = identity.username.charAt(0).toLocaleUpperCase("it-IT") + identity.username.slice(1).toLocaleLowerCase("it-IT");
      document.getElementById("homeGreeting").textContent = `${prefix}, ${displayName}`;
      document.getElementById("todayLabel").textContent = new Intl.DateTimeFormat("it-IT", { weekday: "long", day: "numeric", month: "long", year: "numeric" }).format(new Date());
      loginScreen.hidden = true;
      appScreen.hidden = false;
      document.body.classList.remove("auth-page", "d-flex", "align-items-center", "justify-content-center");
      document.title = "Dashboard • Fisio e Sports";
      showHome();
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
  document.getElementById("logoutButton").addEventListener("click", () => {
    sessionEpoch++;
    patientsRequest++;
    patientDetailRequest++;
    mergeCandidatesRequest++;
    patientModal.hide();
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
    appScreen.hidden = true;
    loginScreen.hidden = false;
    document.body.className = "auth-page app-page d-flex align-items-center justify-content-center";
    patientsScreen.hidden = true;
    document.title = "Login • Fisio e Sports";
  });
  document.getElementById("searchForm").addEventListener("submit", event => event.preventDefault());
});
