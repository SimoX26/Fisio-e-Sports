<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Ricerca globale • Fisio e Sports</title>

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
    <div class="mb-4">
        <h2 class="page-title">Ricerca globale</h2>
        <p class="page-subtitle mb-0">
            Risultati per: <strong><c:out value="${query}" /></strong>
        </p>
    </div>

    <c:if test="${not empty error}">
        <div class="alert alert-warning" role="alert">
            <c:out value="${error}" />
        </div>
    </c:if>

    <div class="glass-card section-card p-4">
        <c:choose>
            <c:when test="${empty query}">
                <p class="mb-0 text-muted">Inserisci una parola chiave nella barra di ricerca in alto.</p>
            </c:when>
            <c:when test="${empty results}">
                <p class="mb-0 text-muted">Nessun risultato trovato.</p>
            </c:when>
            <c:otherwise>
                <div class="table-responsive">
                    <table class="table align-middle mb-0">
                        <thead>
                        <tr>
                            <th>Tipo</th>
                            <th>Titolo</th>
                            <th>Dettaglio</th>
                            <th class="text-end">Apri</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach var="result" items="${results}">
                            <tr>
                                <td><c:out value="${result.type}" /></td>
                                <td><c:out value="${result.title}" /></td>
                                <td><c:out value="${result.subtitle}" /></td>
                                <td class="text-end">
                                    <a class="btn btn-sm btn-outline-primary"
                                       href="${pageContext.request.contextPath}${result.link}">
                                        Vai
                                    </a>
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
