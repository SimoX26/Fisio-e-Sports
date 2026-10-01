document.addEventListener("DOMContentLoaded", () => {
  const apiBase = "http://127.0.0.1:8081";
  const loginScreen = document.getElementById("loginScreen");
  const appScreen = document.getElementById("appScreen");
  const homeScreen = document.getElementById("homeScreen");
  const calendarScreen = document.getElementById("calendarScreen");
  const loginError = document.getElementById("loginError");
  const dataStatus = document.getElementById("dataStatus");
  let authorization = null;
  let calendar = null;
  let sessionEpoch = 0;

  function localDateTime(date) {
    const pad = number => String(number).padStart(2, "0");
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
  }

  async function getJson(path) {
    const response = await fetch(`${apiBase}${path}`, {
      headers: { Authorization: authorization }, cache: "no-store"
    });
    if (!response.ok) {
      const error = new Error(`HTTP ${response.status}`);
      error.status = response.status;
      throw error;
    }
    return response.json();
  }

  function showHome() {
    homeScreen.hidden = false;
    calendarScreen.hidden = true;
    document.body.classList.remove("calendar-gcal-page", "calendar-view-day", "calendar-view-week", "calendar-view-month");
    document.body.classList.add("app-page");
    document.getElementById("homeNav").classList.add("active");
    document.getElementById("calendarNav").classList.remove("active");
    loadTodayAgenda();
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
    document.body.classList.remove("app-page");
    document.body.classList.add("calendar-gcal-page");
    document.getElementById("homeNav").classList.remove("active");
    document.getElementById("calendarNav").classList.add("active");
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
  document.getElementById("todayAppointmentsButton").addEventListener("click", () => showCalendar(true));
  document.getElementById("openDayButton").addEventListener("click", () => showCalendar(true));
  document.getElementById("logoutButton").addEventListener("click", () => {
    sessionEpoch++;
    authorization = null;
    if (calendar) {
      calendar.destroy();
      calendar = null;
    }
    document.getElementById("todayAgenda").replaceChildren();
    document.getElementById("appointmentsToday").textContent = "–";
    document.getElementById("patientsToday").textContent = "–";
    document.getElementById("homeGreeting").textContent = "";
    document.getElementById("todayLabel").textContent = "";
    document.getElementById("username").value = "";
    appScreen.hidden = true;
    loginScreen.hidden = false;
    document.body.className = "auth-page app-page d-flex align-items-center justify-content-center";
    document.title = "Login • Fisio e Sports";
  });
  document.getElementById("searchForm").addEventListener("submit", event => event.preventDefault());
});
