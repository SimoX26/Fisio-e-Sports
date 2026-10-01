<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><c:out value="${not empty historyTitle ? historyTitle : 'Storico trattamenti'}" /> • Fisio e Sports</title>

    <!-- Bootstrap -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css"
          rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/assets/img/logo-192.png">
    <link rel="manifest" href="<%= request.getContextPath() %>/manifest.webmanifest">
    <meta name="theme-color" content="#1a73e8">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-title" content="FisioSport">

      <!-- Custom CSS -->
       <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css?v=20260721-1">
</head>

<body class="app-page">

<!-- HEADER -->
<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">

    <!-- HEADER PAGINA -->
    <div class="page-header-row mb-4">
        <div>
            <h2 class="page-title">
                <c:out value="${not empty historyTitle ? historyTitle : 'Storico trattamenti'}" />
            </h2>
        </div>
    </div>

    <!-- LISTA TRATTAMENTI -->
    <div class="glass-card section-card p-4">

        <%--
            Il controller dovrebbe settare:
            request.setAttribute("sessions", List<TreatmentSession>);
        --%>

        <c:choose>
            <c:when test="${empty sessions}">

            <!-- EMPTY STATE -->
            <div class="text-center py-5 empty-state">
                <h5>Nessun trattamento registrato</h5>
                <p class="mb-3">
                    Nessuna sessione avviata trovata per piani multi-trattamento.
                </p>
            </div>

            </c:when>
            <c:otherwise>

            <!-- TABELLA -->
            <div class="table-responsive">
                <table class="table table-borderless align-middle mb-0">
                    <thead>
                    <tr>
                        <th>Data</th>
                        <th>Paziente</th>
                        <th>Piano terapeutico</th>
                        <th>Dolore pre/post</th>
                        <th>Esito</th>
                        <th>Stato</th>
                    </tr>
                    </thead>
                    <tbody>

                    <c:forEach var="session" items="${sessions}">
                        <tr>
                            <td><c:out value="${session.sessionDateLabel}" /></td>
                            <td><c:out value="${session.patientName}" /></td>
                            <td><c:out value="${session.planTitle}" /></td>
                            <td>
                                <c:choose>
                                    <c:when test="${empty session.painScorePre and empty session.painScorePost}">
                                        -
                                    </c:when>
                                    <c:otherwise>
                                        <c:out value="${session.painScorePre}" /> / <c:out value="${session.painScorePost}" />
                                    </c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <c:choose>
                                    <c:when test="${empty session.outcome}">-</c:when>
                                    <c:otherwise><c:out value="${session.outcome}" /></c:otherwise>
                                </c:choose>
                            </td>
                            <td>
                                <span class="badge-state state-completed">
                                    <c:out value="${session.stateLabel}" />
                                </span>
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

</body>
</html>
