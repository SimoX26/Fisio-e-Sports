<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nuovo Utente • Admin</title>

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
        <h2 class="page-title">Nuovo utente</h2>
        <p class="page-subtitle mb-0">Crea un utente operativo del sistema</p>
    </div>

    <div class="glass-card section-card p-4">

        <c:if test="${not empty error}">
            <div class="alert alert-danger" role="alert">
                <c:out value="${error}"/>
            </div>
        </c:if>

        <form action="${pageContext.request.contextPath}/admin/create-user" method="post">
            <div class="row g-3">
                <div class="col-12 col-md-6">
                    <label class="form-label" for="username">Username</label>
                    <input type="text" class="form-control" id="username" name="username"
                           value="<c:out value='${username}'/>" required>
                </div>

                <div class="col-12 col-md-6">
                    <label class="form-label" for="password">Password</label>
                    <input type="password" class="form-control" id="password" name="password" required minlength="6">
                </div>

                <div class="col-12 col-md-6">
                    <label class="form-label" for="role">Ruolo</label>
                    <select class="form-select" id="role" name="role" required>
                        <option value="">Seleziona ruolo</option>
                        <option value="THERAPIST" <c:if test="${role == 'THERAPIST'}">selected</c:if>>Terapista</option>
                        <option value="ADMIN" <c:if test="${role == 'ADMIN'}">selected</c:if>>Admin</option>
                    </select>
                </div>

                <div class="col-12 col-md-6 d-flex align-items-end">
                    <div class="form-check mb-2">
                        <input class="form-check-input" type="checkbox" id="active" name="active"
                               <c:if test="${active}">checked</c:if>>
                        <label class="form-check-label" for="active">Utente attivo</label>
                    </div>
                </div>
            </div>

            <div class="form-actions mt-4">
                <a href="${pageContext.request.contextPath}/admin" class="btn btn-outline-secondary">Annulla</a>
                <button type="submit" class="btn btn-primary section-action-btn">Crea utente</button>
            </div>
        </form>
    </div>
</div>

</body>
</html>
