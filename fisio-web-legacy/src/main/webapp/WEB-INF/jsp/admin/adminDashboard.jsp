<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Admin Dashboard</title>

    <!-- Bootstrap -->
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
        <h2 class="page-title">Dashboard amministrativa</h2>
        <p class="page-subtitle mb-0">Gestione richieste di accesso e approvazioni</p>
    </div>

    <!-- =========================
         SEZIONE GESTIONE UTENTI
         ========================= -->
    <div class="glass-card section-card p-4">

        <h5 class="mb-3">Richieste di accesso</h5>

        <p class="page-subtitle">
            Utenti in attesa di approvazione: <strong>${pendingCount}</strong>
        </p>

        <a href="${pageContext.request.contextPath}/admin/access-requests"
           class="btn btn-primary section-action-btn">
            Gestisci richieste
        </a>

    </div>

</div>

</body>
</html>
