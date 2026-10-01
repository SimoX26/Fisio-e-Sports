<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dashboard • Fisio e Sports</title>

    <!-- Bootstrap -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/assets/img/logo-192.png">
    <link rel="manifest" href="<%= request.getContextPath() %>/manifest.webmanifest">
    <meta name="theme-color" content="#1a73e8">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-title" content="FisioSport">

      <!-- Custom CSS -->
       <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css?v=20260721-1">
       <script src="<%= request.getContextPath() %>/assets/js/reminder-modal.js?v=20260721-1" defer></script>
</head>

<body class="app-page"
      data-context-path="<%= request.getContextPath() %>"
      data-whatsapp-configured="${requestScope.whatsAppConfigured}">
<div id="appNoticeContainer" class="app-notice-container" aria-live="polite" aria-atomic="true"></div>

<!-- HEADER -->
<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">
    <div class="home-topbar mb-3">
        <div>
            <h1 class="home-title mb-1"><c:out value="${greetingPrefix}" />, <c:out value="${loggedUserDisplay}" /></h1>
            <div class="home-subtitle"><c:out value="${todayLabel}" /></div>
        </div>
        <div class="home-topbar-actions" aria-label="Azioni rapide">
            <a class="home-action-pill" href="<%= request.getContextPath() %>/calendar?new=1">Nuovo appuntamento</a>
            <button type="button"
                    class="home-action-pill"
                    data-bs-toggle="modal"
                    data-bs-target="#quickPatientModal">
                Nuovo paziente
            </button>
            <button type="button" class="home-action-pill" id="openHomeReminderModalBtn">Invia promemoria</button>
        </div>
    </div>

    <c:if test="${not empty error}">
        <div class="alert alert-warning mb-3" role="alert">
            <c:out value="${error}" />
        </div>
    </c:if>

    <c:if test="${not empty waitlistError}">
        <div class="alert alert-warning mb-3" role="alert">
            <c:out value="${waitlistError}" />
        </div>
    </c:if>

    <c:if test="${param.waitlistCreated == '1'}">
        <div class="alert alert-success mb-3" role="alert">
            Contatto aggiunto alla lista di attesa.
        </div>
    </c:if>

    <c:if test="${param.waitlistRemoved == '1'}">
        <div class="alert alert-success mb-3" role="alert">
            Contatto rimosso dalla lista di attesa.
        </div>
    </c:if>

    <c:if test="${param.patientCreated == '1'}">
        <div class="alert alert-success mb-3" role="alert">
            Paziente salvato correttamente.
        </div>
    </c:if>

    <div class="home-kpi-row mb-4">
        <a class="glass-card section-card home-kpi-chip text-decoration-none" href="<%= request.getContextPath() %>/calendar?view=timeGridDay&date=today">
            <span class="home-kpi-chip__label">Appuntamenti oggi</span>
            <span class="home-kpi-chip__value"><c:out value="${appointmentsToday}" /></span>
        </a>
        <a class="glass-card section-card home-kpi-chip text-decoration-none" href="<%= request.getContextPath() %>/address-book?treatedDate=${patientsTodayParam}">
            <span class="home-kpi-chip__label">Pazienti oggi</span>
            <span class="home-kpi-chip__value"><c:out value="${patientsToday}" /></span>
        </a>
        <a class="glass-card section-card home-kpi-chip text-decoration-none" href="<%= request.getContextPath() %>/calendar?view=timeGridDay&date=today">
            <span class="home-kpi-chip__label">Promemoria da inviare oggi</span>
            <span class="home-kpi-chip__value"><c:out value="${remindersToSendToday}" /></span>
        </a>
    </div>

    <div class="row g-3 align-items-start mb-4">
        <div class="col-12 col-xl-7">
            <div class="glass-card section-card home-agenda-card">
                <div class="home-section-head">
                    <h3 class="home-section-title mb-0">Agenda di oggi</h3>
                    <a class="home-link" href="<%= request.getContextPath() %>/calendar?view=timeGridDay&date=today">Apri vista giornaliera</a>
                </div>
                <c:set var="agendaRows" value="${todayAgenda}" />
                <c:choose>
                    <c:when test="${empty agendaRows}">
                        <div class="home-empty-state">
                            Nessun appuntamento pianificato per oggi.
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="home-agenda-list">
                            <c:forEach var="appointment" items="${agendaRows}">
                                <div class="home-agenda-item">
                                    <div class="home-agenda-time">
                                        <c:out value="${appointment.startTime}" /> - <c:out value="${appointment.endTime}" />
                                    </div>
                                    <div class="home-agenda-main">
                                        <c:choose>
                                            <c:when test="${not empty appointment.patientName}">
                                                <div class="home-agenda-title"><c:out value="${appointment.patientName}" /></div>
                                                <div class="home-agenda-subtitle">Paziente</div>
                                            </c:when>
                                            <c:otherwise>
                                                <div class="home-agenda-title"><c:out value="${appointment.eventTitle}" /></div>
                                                <div class="home-agenda-subtitle">Evento</div>
                                            </c:otherwise>
                                        </c:choose>
                                    </div>
                                    <c:if test="${appointment.stateClass ne 'SCHEDULED' and appointment.stateLabel ne 'PROGRAMMATO'}">
                                        <div class="home-agenda-side">
                                            <span class="home-status-badge home-status-badge--${appointment.stateClass}">
                                                <c:out value="${appointment.stateLabel}" />
                                            </span>
                                        </div>
                                    </c:if>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>

        <div class="col-12 col-xl-5">
            <div class="glass-card section-card home-waitlist-card mb-3">
                <div class="home-section-head">
                    <h3 class="home-section-title mb-0">Lista di attesa</h3>
                    <span class="home-section-meta"><c:out value="${fn:length(waitlistEntries)}" /> contatti</span>
                </div>

                <form method="post" action="<%= request.getContextPath() %>/dashboard" class="home-waitlist-form">
                    <input type="hidden" name="action" value="add-waitlist-entry">
                    <div class="row g-2">
                        <div class="col-12">
                            <label class="form-label home-form-label" for="waitlistPatientName">Nome e Cognome Paziente</label>
                            <input id="waitlistPatientName"
                                   type="text"
                                   class="form-control"
                                   name="patientName"
                                   required>
                        </div>
                        <div class="col-12">
                            <label class="form-label home-form-label" for="waitlistPhone">Telefono</label>
                            <input id="waitlistPhone" type="text" class="form-control" name="phone" required>
                        </div>
                    </div>
                    <div class="home-waitlist-form__actions">
                        <button type="submit" class="btn btn-primary btn-sm">Aggiungi alla lista</button>
                    </div>
                </form>

                <c:choose>
                    <c:when test="${empty waitlistEntries}">
                        <div class="home-empty-state">
                            Nessuna persona in attesa al momento.
                        </div>
                    </c:when>
                    <c:otherwise>
                        <div class="home-waitlist-list">
                            <c:forEach var="entry" items="${waitlistEntries}">
                                <c:url var="convertToAppointmentUrl" value="/calendar">
                                    <c:param name="new" value="1" />
                                    <c:param name="patientName" value="${entry.fullName}" />
                                    <c:param name="patientPhone" value="${entry.phone}" />
                                </c:url>
                                <div class="home-waitlist-item">
                                    <div class="home-waitlist-item__main">
                                        <div class="home-agenda-title"><c:out value="${entry.fullName}" /></div>
                                        <div class="home-waitlist-item__meta">
                                            <span><c:out value="${entry.phone}" /></span>
                                            <span>Aggiunto il <c:out value="${entry.createdAtLabel}" /></span>
                                        </div>
                                    </div>
                                    <div class="home-waitlist-actions">
                                        <a class="btn btn-outline-primary btn-sm" href="${convertToAppointmentUrl}">
                                            Trasforma in appuntamento
                                        </a>
                                        <form method="post" action="<%= request.getContextPath() %>/dashboard" class="home-waitlist-remove-form">
                                            <input type="hidden" name="action" value="remove-waitlist-entry">
                                            <input type="hidden" name="id" value="<c:out value='${entry.id}' />">
                                            <button type="submit"
                                                    class="btn btn-outline-danger btn-sm btn-icon-only btn-trash-icon"
                                                    aria-label="Rimuovi contatto dalla lista di attesa"
                                                    title="Rimuovi contatto dalla lista di attesa"></button>
                                        </form>
                                    </div>
                                </div>
                            </c:forEach>
                        </div>
                    </c:otherwise>
                </c:choose>
            </div>
        </div>
    </div>
</div>

<%@ include file="/WEB-INF/jsp/includes/reminderModal.jsp" %>

<div class="modal fade" id="quickPatientModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Nuovo paziente</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Chiudi"></button>
            </div>
            <form action="<%= request.getContextPath() %>/address-book" method="POST">
                <div class="modal-body">
                    <input type="hidden" name="action" value="create">
                    <input type="hidden" name="id" value="0">
                    <input type="hidden" name="returnTo" value="dashboard">

                    <div class="mb-3">
                        <label class="form-label" for="quickPatientFullName">Nome e Cognome</label>
                        <input type="text"
                               name="fullName"
                               id="quickPatientFullName"
                               class="form-control"
                               required>
                    </div>

                    <div class="mb-3">
                        <label class="form-label" for="quickPatientEmail">Email</label>
                        <input type="email"
                               name="email"
                               id="quickPatientEmail"
                               class="form-control">
                    </div>

                    <div class="mb-0">
                        <label class="form-label" for="quickPatientPhone">Telefono</label>
                        <input type="text"
                               name="phone"
                               id="quickPatientPhone"
                               class="form-control">
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-outline-secondary" data-bs-dismiss="modal">Annulla</button>
                    <button type="submit" class="btn btn-primary">Salva paziente</button>
                </div>
            </form>
        </div>
    </div>
</div>

</body>
</html>
