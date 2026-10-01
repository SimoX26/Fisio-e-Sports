<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Invia promemoria - Fisio e Sports</title>

    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css?v=20260721-1">
</head>
<body class="app-page">

<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">
    <div class="page-header-row mb-3">
        <div>
            <h1 class="page-title mb-0">Invia promemoria</h1>
        </div>
        <a class="btn btn-outline-secondary" href="<%= request.getContextPath() %>/calendar?view=timeGridDay&date=${selectedDate}">Calendario</a>
    </div>

    <c:if test="${not empty error}">
        <div class="alert alert-warning mb-3" role="alert">
            <c:out value="${error}" />
        </div>
    </c:if>
    <c:if test="${not empty success}">
        <div class="alert alert-success mb-3" role="alert">
            <c:out value="${success}" />
        </div>
    </c:if>
    <c:if test="${not whatsAppConfigured}">
        <div class="alert alert-warning mb-3" role="alert">
            Servizio WhatsApp non configurato per questo account. Contattare l'amministratore di sistema.
        </div>
    </c:if>

    <div class="glass-card section-card promemoria-card">
        <c:if test="${not appointmentLocked}">
            <form method="get" action="<%= request.getContextPath() %>/promemoria" class="promemoria-date-form">
                <div>
                    <label class="form-label" for="date">Giorno appuntamento</label>
                    <input class="form-control" type="date" id="date" name="date" value="<c:out value='${selectedDate}' />">
                </div>
                <div>
                    <button type="submit" class="btn btn-outline-primary">Mostra appuntamenti</button>
                </div>
            </form>
        </c:if>

        <c:if test="${appointmentLocked}">
            <div class="promemoria-selected">
                <div>
                    <span class="promemoria-selected__label">Appuntamento</span>
                    <strong><c:out value="${selectedAppointment.label}" /></strong>
                </div>
            </div>
        </c:if>

        <form method="post" action="<%= request.getContextPath() %>/promemoria" class="app-form-grid">
            <input type="hidden" name="date" value="<c:out value='${selectedDate}' />">

            <c:choose>
                <c:when test="${appointmentLocked}">
                    <input type="hidden" name="appointmentId" value="<c:out value='${selectedAppointmentId}' />">
                </c:when>
                <c:otherwise>
            <div>
                <label class="form-label" for="appointmentId">Appuntamento</label>
                <select class="form-select" id="appointmentId" name="appointmentId" required>
                    <option value="">Seleziona appuntamento</option>
                    <c:forEach var="appointment" items="${appointments}">
                        <option value="<c:out value='${appointment.id}' />"
                                <c:if test="${not appointment.sendable}">disabled</c:if>
                                <c:if test="${appointment.id == selectedAppointmentId}">selected</c:if>>
                            <c:out value="${appointment.label}" />
                        </option>
                    </c:forEach>
                </select>
                <c:if test="${empty appointments}">
                    <div class="form-text">Nessun appuntamento programmato con paziente per il giorno selezionato.</div>
                </c:if>
            </div>
                </c:otherwise>
            </c:choose>

            <div>
                <label class="form-label" for="template">Messaggio</label>
                <textarea class="form-control promemoria-message" id="template" name="template" rows="5"><c:out value="${template}" /></textarea>
            </div>

            <div class="app-form-actions">
                <button type="submit" class="btn btn-primary" <c:if test="${empty appointments or not whatsAppConfigured}">disabled</c:if>>
                    Invia promemoria
                </button>
            </div>
        </form>
    </div>
</div>

</body>
</html>
