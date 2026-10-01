<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Calendario • Fisio e Sports</title>

    <!-- Bootstrap -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/assets/img/logo-192.png">
    <link rel="manifest" href="<%= request.getContextPath() %>/manifest.webmanifest">
    <meta name="theme-color" content="#1a73e8">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-title" content="FisioSport">

    <!-- FullCalendar -->
    <link href="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.11/index.global.min.css" rel="stylesheet">
    <script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.11/index.global.min.js" defer></script>

    <!-- Custom CSS -->
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css?v=20260721-8">
    <script src="<%= request.getContextPath() %>/assets/js/reminder-modal.js?v=20260721-1" defer></script>
    <script src="<%= request.getContextPath() %>/assets/js/calendar.js?v=20260721-8" defer></script>
</head>

<body data-context-path="<%= request.getContextPath() %>"
      data-whatsapp-configured="${requestScope.whatsAppConfigured}"
      class="calendar-gcal-page">
<div id="appNoticeContainer" class="app-notice-container" aria-live="polite" aria-atomic="true"></div>

<!-- HEADER -->
<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell calendar-gcal-header-shell mt-4">
    <div class="page-header-row calendar-gcal-head mb-3">
        <div>
            <h2 class="page-title mb-0">Calendario</h2>
        </div>
        <div class="d-flex align-items-center gap-2">
            <a href="<%= request.getContextPath() %>/calendar/trash"
               class="btn btn-outline-secondary section-action-btn">
                Cestino
            </a>
            <button type="button"
                    class="btn btn-primary calendar-add-btn gcal-create-btn"
                    id="openAppointmentModalBtn"
                    aria-label="Nuovo appuntamento">
                <span>+</span>
                <span>Crea</span>
            </button>
        </div>
    </div>
    <div id="searchHighlightNotice" class="alert alert-warning py-2 px-3 small d-none" role="status">
        Risultati della ricerca evidenziati nel calendario.
    </div>
</div>

<div class="container-fluid calendar-gcal-wrap">
    <div class="calendar-host">
        <div class="calendar-scroll-shell">
            <div id="calendar"></div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/jsp/includes/reminderModal.jsp" %>

<!-- =========================
     MODALE DETTAGLI EVENTO
     ========================= -->
<div class="modal fade" id="eventModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-card">

            <div class="modal-header">
                <div class="event-title-block">
                    <h5 class="modal-title mb-0" id="modalTitle"></h5>
                    <span id="modalTitleTime" class="event-title-time"></span>
                </div>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>

            <div class="modal-body">
                <p><strong>Note:</strong></p>
                <p id="modalNotes" class="text-muted"></p>
                <span id="modalType" class="d-none" aria-hidden="true"></span>
            </div>

            <div class="modal-footer">
                <span id="eventModalStateHint" class="me-auto text-muted small d-none"></span>
                <button type="button" class="btn btn-outline-primary" id="sendSingleReminderBtn">Invia promemoria</button>
                <a href="#" class="btn btn-outline-secondary" id="openPatientDetailsBtn">Dettagli paziente</a>
                <button type="button" class="btn btn-success" id="completeAppointmentBtn">
                    Completa trattamento
                </button>
                <button type="button" class="btn btn-outline-primary" id="editAppointmentBtn">
                    Modifica
                </button>
                <button type="button"
                        class="btn btn-outline-danger btn-icon-only btn-trash-icon"
                        id="deleteAppointmentBtn"
                        aria-label="Elimina appuntamento"
                        title="Elimina appuntamento"></button>
            </div>

        </div>
    </div>
</div>

<!-- =========================
     MODALE COMPLETAMENTO CON DATI TRATTAMENTO
     ========================= -->
<div class="modal fade" id="completeTreatmentModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Completa trattamento</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>

            <div class="modal-body">
                <form id="completeTreatmentForm">
                    <div class="mb-3">
                        <label class="form-label" for="treatmentPlanTitle">Titolo piano terapeutico</label>
                        <input type="text" class="form-control" id="treatmentPlanTitle" required>
                    </div>

                    <div class="row g-3">
                        <div class="col-12 col-md-6">
                            <label class="form-label" for="treatmentTotalSessionsPlanned">Sedute pianificate</label>
                            <input type="number" min="1" class="form-control" id="treatmentTotalSessionsPlanned" value="1" required>
                        </div>
                        <div class="col-12 col-md-6">
                            <label class="form-label" for="treatmentFrequencyPerWeek">Frequenza settimanale</label>
                            <input type="number" min="1" class="form-control" id="treatmentFrequencyPerWeek" placeholder="Es. 2">
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-12 col-md-6">
                            <label class="form-label" for="treatmentExpectedEndDate">Fine prevista piano</label>
                            <input type="date" class="form-control" id="treatmentExpectedEndDate">
                        </div>
                        <div class="col-12 col-md-3">
                            <label class="form-label" for="treatmentPainScorePre">Dolore pre (0-10)</label>
                            <input type="number" min="0" max="10" class="form-control" id="treatmentPainScorePre">
                        </div>
                        <div class="col-12 col-md-3">
                            <label class="form-label" for="treatmentPainScorePost">Dolore post (0-10)</label>
                            <input type="number" min="0" max="10" class="form-control" id="treatmentPainScorePost">
                        </div>
                    </div>

                    <div class="mt-3">
                        <label class="form-label" for="treatmentGoals">Obiettivi trattamento</label>
                        <textarea class="form-control" id="treatmentGoals" rows="2"></textarea>
                    </div>

                    <div class="mt-3">
                        <label class="form-label" for="treatmentSessionOutcome">Esito sessione</label>
                        <textarea class="form-control" id="treatmentSessionOutcome" rows="2" placeholder="Descrivi l'esito della seduta"></textarea>
                    </div>

                    <div class="mt-3">
                        <label class="form-label" for="treatmentHomeExercises">Esercizi domiciliari</label>
                        <textarea class="form-control" id="treatmentHomeExercises" rows="2"></textarea>
                    </div>

                    <div class="mt-3">
                        <label class="form-label" for="treatmentNotes">Note trattamento</label>
                        <textarea class="form-control" id="treatmentNotes" rows="3"></textarea>
                    </div>
                </form>
            </div>

            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                <button type="button" class="btn btn-success" id="confirmCompleteTreatmentBtn">Conferma completamento</button>
            </div>
        </div>
    </div>
</div>

<!-- =========================
     MODALE CREAZIONE EVENTO
     ========================= -->
<div class="modal fade" id="appointmentModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered">
        <div class="modal-content glass-card">

            <div class="modal-header">
                <h5 class="modal-title" id="appointmentModalTitle">Nuovo appuntamento</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>

            <div class="modal-body">
                <form id="appointmentForm">
                    <div id="appointmentFormError" class="alert alert-danger d-none" role="alert"></div>

                    <div class="mb-3">
                        <div class="form-check">
                            <input class="form-check-input" type="checkbox" id="allDay">
                            <label class="form-check-label" for="allDay">
                                Tutto il giorno
                            </label>
                        </div>
                        <div class="form-check mt-2">
                            <input class="form-check-input" type="checkbox" id="nonTreatmentEvent">
                            <label class="form-check-label" for="nonTreatmentEvent">
                                Evento non collegato a trattamento
                            </label>
                        </div>
                    </div>

                    <!-- Paziente -->
                    <div class="mb-3 patient-search-wrap">
                        <label class="form-label" id="patientNameLabel">Paziente</label>
                        <input type="text"
                               class="form-control"
                               id="patientName"
                               autocomplete="off"
                               placeholder="Nome e cognome paziente"
                               required>
                        <div id="patientSuggestionsMenu" class="patient-suggestions-menu d-none" role="listbox" aria-label="Suggerimenti pazienti"></div>
                    </div>

                    <div class="mb-3">
                        <label class="form-label" for="patientPhone">Telefono</label>
                        <input type="text"
                               class="form-control"
                               id="patientPhone"
                               placeholder="Numero di telefono paziente">
                    </div>

                    <!-- Giorno e orario -->
                    <div class="mb-3">
                        <label class="form-label">Giorno</label>
                        <input type="date" class="form-control" id="appointmentDate" required>
                    </div>

                    <div class="row g-3 appointment-time-grid" id="timeSelectionSection">
                        <div class="col-12 col-md-6">
                            <label class="form-label">Orario inizio</label>
                            <input type="time" class="form-control" id="startTimeNative" step="900" required>
                            <input type="datetime-local" class="form-control d-none" id="start" step="900" required>
                        </div>
                        <div class="col-12 col-md-6">
                            <label class="form-label">Orario fine</label>
                            <input type="time" class="form-control" id="endTimeNative" step="900" required>
                            <input type="datetime-local" class="form-control d-none" id="end" step="900" required>
                        </div>
                    </div>

                    <div class="mt-3">
                        <label class="form-label">Note</label>
                        <textarea class="form-control"
                                  id="notes"
                                  rows="3"
                                  placeholder="Inserisci eventuali note sull'appuntamento"></textarea>
                    </div>

                </form>
            </div>

            <div class="modal-footer">
                <button class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                <button class="btn btn-primary" id="saveAppointmentBtn">Salva</button>
            </div>

        </div>
    </div>
</div>

<!-- =========================
     MODALE CONFERMA ELIMINAZIONE
     ========================= -->
<div class="modal fade" id="confirmDeleteModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Conferma eliminazione</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <p class="mb-0">Vuoi eliminare questo appuntamento?</p>
            </div>
            <div class="modal-footer">
                <button class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                <button class="btn btn-danger btn-icon-only btn-trash-icon"
                        id="confirmDeleteAppointmentBtn"
                        aria-label="Conferma eliminazione appuntamento"
                        title="Conferma eliminazione appuntamento"></button>
            </div>
        </div>
    </div>
</div>

</body>
</html>
