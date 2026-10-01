<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Nuovo Paziente • Fisio e Sports</title>

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

<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">

    <!-- HEADER -->
    <div class="mb-4">
        <h2 class="page-title">Nuovo paziente</h2>
    </div>

    <!-- FORM -->
    <div class="glass-card section-card p-4">

        <form action="<%= request.getContextPath() %>/address-book"
              method="POST">

            <!-- ACTION -->
            <input type="hidden" name="action" value="create"/>

            <!-- ID (temporaneamente, perché la servlet lo richiede) -->
            <input type="hidden" name="id" value="0"/>

            <!-- NOME E COGNOME -->
            <div class="mb-3">
                <label class="form-label">Nome e Cognome</label>
                <input type="text"
                       name="fullName"
                       class="form-control"
                       placeholder="Nome e cognome paziente"
                       required>
            </div>

            <!-- EMAIL -->
            <div class="mb-3">
                <label class="form-label">Email</label>
                <input type="email"
                       name="email"
                       class="form-control">
            </div>

            <!-- TELEFONO -->
            <div class="mb-3">
                <label class="form-label">Telefono</label>
                <input type="text"
                       name="phone"
                       class="form-control">
            </div>

            <!-- AZIONI -->
            <div class="form-actions mt-4">
                <a href="<%= request.getContextPath() %>/address-book"
                   class="btn btn-outline-secondary">
                    Annulla
                </a>

                <button type="submit"
                        class="btn btn-primary">
                    Salva paziente
                </button>
            </div>

        </form>

    </div>

</div>

<script>
(function () {
    const guardKey = 'addressBookCreateSubmitted';
    const isCreateFormBackNavigation = function () {
        const navEntries = performance.getEntriesByType && performance.getEntriesByType('navigation');
        if (navEntries && navEntries.length > 0 && navEntries[0].type === 'back_forward') {
            return true;
        }

        return false;
    };

    window.addEventListener('pageshow', function (event) {
        if (!sessionStorage.getItem(guardKey)) {
            return;
        }

        if (!event.persisted && !isCreateFormBackNavigation()) {
            return;
        }

        sessionStorage.removeItem(guardKey);
        window.location.replace('<%= request.getContextPath() %>/address-book');
    });
})();
</script>

</body>
</html>
