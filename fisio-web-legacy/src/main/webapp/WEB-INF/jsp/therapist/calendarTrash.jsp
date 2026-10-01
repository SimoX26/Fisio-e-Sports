<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Cestino Appuntamenti • Fisio e Sports</title>

    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/assets/img/logo-192.png">
    <link rel="manifest" href="<%= request.getContextPath() %>/manifest.webmanifest">
    <meta name="theme-color" content="#1a73e8">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-title" content="FisioSport">
    <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css?v=20260721-1">
</head>
<body class="app-page">

<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">
    <div class="page-header-row mb-3">
        <div>
            <h2 class="page-title mb-0">Cestino appuntamenti</h2>
            <p class="page-subtitle mb-0">Eliminazione automatica dopo 30 giorni.</p>
        </div>
        <div class="d-flex gap-2">
            <button type="button"
                    class="btn btn-outline-danger section-action-btn"
                    data-bs-toggle="modal"
                    data-bs-target="#confirmEmptyTrashModal">
                Svuota cestino
            </button>
            <a href="<%= request.getContextPath() %>/calendar" class="btn btn-outline-secondary section-action-btn">Torna al calendario</a>
        </div>
    </div>

    <div class="glass-card section-card p-4">
        <c:if test="${not empty message}">
            <div class="alert alert-success" role="alert">
                <c:out value="${message}" />
            </div>
        </c:if>
        <c:if test="${not empty error}">
            <div class="alert alert-warning" role="alert">
                <c:out value="${error}" />
            </div>
        </c:if>

        <c:choose>
            <c:when test="${empty cancelledAppointments}">
                <div class="empty-state py-5 text-center">
                    <h5>Nessun appuntamento nel cestino</h5>
                    <p class="mb-0">Gli appuntamenti cancellati compariranno qui.</p>
                </div>
            </c:when>
            <c:otherwise>
                <div class="table-responsive">
                    <table class="table table-borderless align-middle mb-0">
                        <thead>
                        <tr>
                            <th>
                                <c:url var="sortByPatientUrl" value="/calendar/trash">
                                    <c:param name="sortPatient" value="${patientSort == 'asc' ? 'desc' : 'asc'}" />
                                </c:url>
                                <a class="text-decoration-none text-reset" href="${sortByPatientUrl}">
                                    Paziente
                                    <c:if test="${patientSort == 'asc'}">↑</c:if>
                                    <c:if test="${patientSort == 'desc'}">↓</c:if>
                                </a>
                            </th>
                            <th>Inizio</th>
                            <th>Fine</th>
                            <th>Note</th>
                            <th class="text-end">Azioni</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="row" items="${cancelledAppointments}">
                            <tr>
                                <td><strong><c:out value="${row.patientFullName}" /></strong></td>
                                <td><c:out value="${row.startLabel}" /></td>
                                <td><c:out value="${row.endLabel}" /></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${empty row.notes}">
                                            <span class="text-muted">-</span>
                                        </c:when>
                                        <c:otherwise>
                                            <c:out value="${row.notes}" />
                                        </c:otherwise>
                                    </c:choose>
                                </td>
                                <td class="text-end">
                                    <form method="post" action="<%= request.getContextPath() %>/calendar/trash" class="d-inline">
                                        <input type="hidden" name="action" value="restore">
                                        <input type="hidden" name="id" value="<c:out value='${row.id}'/>">
                                        <button type="submit" class="btn btn-sm btn-outline-primary">Ripristina</button>
                                    </form>
                                    <form method="post" action="<%= request.getContextPath() %>/calendar/trash" class="d-inline ms-1">
                                        <input type="hidden" name="action" value="delete">
                                        <input type="hidden" name="id" value="<c:out value='${row.id}'/>">
                                        <button type="submit"
                                                class="btn btn-sm btn-outline-danger btn-icon-only btn-trash-icon"
                                                onclick="return confirm('Eliminare definitivamente questo appuntamento?');"
                                                aria-label="Elimina definitivamente appuntamento"
                                                title="Elimina definitivamente appuntamento"></button>
                                    </form>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </div>
</div>

<div class="modal fade" id="confirmEmptyTrashModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Conferma svuotamento cestino</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <p class="mb-0">Svuotare completamente il cestino? Questa azione e definitiva.</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                <form method="post" action="<%= request.getContextPath() %>/calendar/trash" class="d-inline">
                    <input type="hidden" name="action" value="empty-trash">
                    <button type="submit" class="btn btn-danger">Svuota cestino</button>
                </form>
            </div>
        </div>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>
