<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ page import="java.util.List" %>
<%@ page import="it.SimoSW.model.AccessRequest" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Richieste Accesso • Admin</title>

    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/assets/img/logo-192.png">
    <link rel="manifest" href="<%= request.getContextPath() %>/manifest.webmanifest">
    <meta name="theme-color" content="#1a73e8">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-title" content="FisioSport">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/assets/css/style.css?v=20260721-1">
</head>
<body class="app-page">

<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">
    <div class="mb-4">
        <h2 class="page-title">Richieste di accesso</h2>
        <p class="page-subtitle mb-0">Approva o rifiuta le richieste in ingresso</p>
    </div>

    <c:if test="${param.success == 'approved'}">
        <div class="alert alert-success" role="alert">
            Richiesta approvata e account creato.
        </div>
    </c:if>

    <c:if test="${param.success == 'rejected'}">
        <div class="alert alert-warning" role="alert">
            Richiesta rifiutata.
        </div>
    </c:if>

    <c:if test="${not empty param.error}">
        <div class="alert alert-danger" role="alert">
            <c:out value="${param.error}" />
        </div>
    </c:if>

    <div class="glass-card section-card p-4 mb-4">
        <h5 class="mb-3">In attesa</h5>
        <c:choose>
            <c:when test="${empty pendingRequests}">
                <p class="text-muted mb-0">Nessuna richiesta in attesa.</p>
            </c:when>
            <c:otherwise>
                <div class="table-responsive">
                    <table class="table align-middle">
                        <thead>
                        <tr>
                            <th>Utente</th>
                            <th>Contatti</th>
                            <th>Ruolo richiesto</th>
                            <th>Data richiesta</th>
                            <th class="text-end">Azioni</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${pendingRequests}" var="req">
                            <tr>
                                <td>
                                    <strong><c:out value="${req.firstName}" /> <c:out value="${req.lastName}" /></strong><br>
                                    <span class="text-muted">@<c:out value="${req.username}" /></span>
                                </td>
                                <td>
                                    <c:out value="${req.email}" />
                                </td>
                                <td>
                                    <c:out value="${req.requestedRole}" />
                                </td>
                                <td>
                                    <c:out value="${req.createdAt}" />
                                </td>
                                <td class="text-end">
                                    <form method="post" action="${pageContext.request.contextPath}/admin/access-requests/review" class="d-inline">
                                        <input type="hidden" name="requestId" value="${req.id}">
                                        <input type="hidden" name="action" value="approve">
                                        <button type="submit" class="btn btn-sm btn-success">Approva</button>
                                    </form>
                                    <form method="post" action="${pageContext.request.contextPath}/admin/access-requests/review" class="d-inline ms-1">
                                        <input type="hidden" name="requestId" value="${req.id}">
                                        <input type="hidden" name="action" value="reject">
                                        <button type="submit" class="btn btn-sm btn-outline-danger">Rifiuta</button>
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

    <div class="glass-card section-card p-4">
        <h5 class="mb-3">Storico recente richieste</h5>
        <c:choose>
            <c:when test="${empty recentRequests}">
                <p class="text-muted mb-0">Nessuna richiesta registrata.</p>
            </c:when>
            <c:otherwise>
                <div class="table-responsive">
                    <table class="table align-middle">
                        <thead>
                        <tr>
                            <th>Username</th>
                            <th>Email</th>
                            <th>Stato</th>
                            <th>Creata il</th>
                            <th>Revisionata il</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${recentRequests}" var="req">
                            <tr>
                                <td><c:out value="${req.username}" /></td>
                                <td><c:out value="${req.email}" /></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${req.status == 'PENDING'}">In attesa</c:when>
                                        <c:when test="${req.status == 'APPROVED'}">Approvata</c:when>
                                        <c:when test="${req.status == 'REJECTED'}">Rifiutata</c:when>
                                        <c:otherwise><c:out value="${req.status}" /></c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${req.createdAt}" /></td>
                                <td><c:out value="${req.reviewedAt}" /></td>
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
