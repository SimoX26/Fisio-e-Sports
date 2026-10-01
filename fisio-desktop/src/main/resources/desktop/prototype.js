document.addEventListener("DOMContentLoaded", () => {
  const calendarElement = document.getElementById("calendar");
  if (!window.FullCalendar || !window.bootstrap || !calendarElement) {
    document.body.insertAdjacentHTML("afterbegin", '<p role="alert">Impossibile caricare il calendario locale.</p>');
    return;
  }

  const monday = new Date();
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
  const dateFor = day => {
    const date = new Date(monday);
    date.setDate(monday.getDate() + day);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}-${String(date.getDate()).padStart(2, "0")}`;
  };
  const events = [
    { title: "Giulia Rossi", start: `${dateFor(0)}T09:00:00`, end: `${dateFor(0)}T10:00:00`, extendedProps: { notes: "Controllo iniziale" } },
    { title: "Luca Bianchi", start: `${dateFor(2)}T11:30:00`, end: `${dateFor(2)}T12:30:00`, extendedProps: { notes: "Seduta di controllo" } },
    { title: "Sara Verdi", start: `${dateFor(4)}T15:00:00`, end: `${dateFor(4)}T16:00:00`, extendedProps: { notes: "Prima visita" } }
  ];

  const appointmentModal = new bootstrap.Modal(document.getElementById("appointmentModal"));
  const eventModal = new bootstrap.Modal(document.getElementById("eventModal"));
  const calendar = new FullCalendar.Calendar(calendarElement, {
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
    events,
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
      const background = "#eaf1fb";
      const border = "var(--calendar-event-border)";
      info.el.classList.add("calendar-event--custom-color");
      info.el.style.setProperty("--event-bg", background);
      info.el.style.setProperty("--event-border", border);
      info.el.style.setProperty("--event-text", "#1f2d3d");
      info.el.style.setProperty("background", background, "important");
      info.el.style.setProperty("border", `1px solid ${border}`, "important");
      info.el.style.setProperty("color", "#1f2d3d", "important");
    },
    datesSet(info) {
      document.body.classList.toggle("calendar-view-day", info.view.type === "timeGridDay");
      document.body.classList.toggle("calendar-view-week", info.view.type === "timeGridWeek");
      document.body.classList.toggle("calendar-view-month", info.view.type === "dayGridMonth");
    },
    select(info) {
      document.getElementById("appointmentDate").value = info.startStr.slice(0, 10);
      document.getElementById("startTime").value = info.allDay ? "09:00" : info.startStr.slice(11, 16);
      document.getElementById("endTime").value = info.allDay ? "10:00" : info.endStr.slice(11, 16);
      appointmentModal.show();
    },
    eventClick(info) {
      document.getElementById("eventModalTitle").textContent = info.event.title;
      document.getElementById("eventModalTime").textContent = info.event.start.toLocaleString("it-IT") + " – " + info.event.end.toLocaleTimeString("it-IT", { hour: "2-digit", minute: "2-digit" });
      document.getElementById("eventModalNotes").textContent = info.event.extendedProps.notes || "Nessuna nota";
      eventModal.show();
    }
  });
  calendar.render();

  document.getElementById("openAppointmentModalBtn").addEventListener("click", () => {
    document.getElementById("appointmentDate").value = dateFor((new Date().getDay() + 6) % 7);
    document.getElementById("startTime").value = "09:00";
    document.getElementById("endTime").value = "10:00";
    appointmentModal.show();
  });
  document.getElementById("appointmentForm").addEventListener("submit", event => {
    event.preventDefault();
    const date = document.getElementById("appointmentDate").value;
    const start = `${date}T${document.getElementById("startTime").value}:00`;
    const end = `${date}T${document.getElementById("endTime").value}:00`;
    const endInput = document.getElementById("endTime");
    if (end <= start) {
      endInput.setCustomValidity("L'orario finale deve essere successivo all'inizio");
      endInput.reportValidity();
      return;
    }
    endInput.setCustomValidity("");
    calendar.addEvent({ title: document.getElementById("patientName").value.trim(), start, end, extendedProps: { notes: document.getElementById("notes").value } });
    appointmentModal.hide();
    event.target.reset();
  });
  document.getElementById("endTime").addEventListener("input", event => event.target.setCustomValidity(""));
  document.getElementById("searchForm").addEventListener("submit", event => event.preventDefault());
});
