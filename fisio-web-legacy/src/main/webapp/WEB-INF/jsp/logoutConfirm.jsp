<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Conferma Logout • Fisio e Sports</title>

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
<body class="auth-page app-page d-flex align-items-center justify-content-center">

<div class="container">
    <div class="row justify-content-center">
        <div class="col-md-7 col-lg-5">
            <div class="glass-card p-4 p-md-5">
                <h2 class="page-title mb-2">Conferma logout</h2>
                <p class="page-subtitle mb-4">Vuoi davvero uscire dall'applicazione?</p>

                <c:if test="${param.error == 'confirm_required'}">
                    <div class="alert alert-warning" role="alert">
                        Conferma di sicurezza richiesta prima del logout.
                    </div>
                </c:if>

                <form method="post" action="<%= request.getContextPath() %>/logout" class="form-actions d-flex justify-content-between">
                    <input type="hidden" name="confirmLogout" value="1">
                    <div class="form-check me-3">
                        <input class="form-check-input" type="checkbox" value="1" id="confirmLogoutCheck" required>
                        <label class="form-check-label" for="confirmLogoutCheck">
                            Confermo di voler terminare la sessione
                        </label>
                    </div>
                    <c:choose>
                        <c:when test="${sessionScope.userRole == 'ADMIN'}">
                            <a href="<%= request.getContextPath() %>/admin" class="btn btn-outline-secondary">Annulla</a>
                        </c:when>
                        <c:otherwise>
                            <a href="<%= request.getContextPath() %>/dashboard" class="btn btn-outline-secondary">Annulla</a>
                        </c:otherwise>
                    </c:choose>
                    <button type="submit" class="btn btn-danger">Logout sicuro</button>
                </form>
            </div>
        </div>
    </div>
</div>

<script src="<%= request.getContextPath() %>/assets/js/loading-overlay.js?v=20260513-12" defer></script>
</body>
</html>
