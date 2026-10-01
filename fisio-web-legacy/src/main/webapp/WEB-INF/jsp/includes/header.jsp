<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<nav class="navbar navbar-expand-lg px-3 px-lg-5 app-navbar">
    <div class="container-fluid">
        <c:choose>
            <c:when test="${sessionScope.userRole == 'ADMIN'}">
                <c:set var="searchAction" value="/admin/search" />
                <c:set var="homePath" value="/admin" />
            </c:when>
            <c:otherwise>
                <c:set var="searchAction" value="/search" />
                <c:set var="homePath" value="/dashboard" />
            </c:otherwise>
        </c:choose>

        <!-- BRAND -->
        <a class="navbar-brand fw-bold"
           href="${pageContext.request.contextPath}${homePath}">
            <span class="brand-dot"></span>
            FISIO E SPORTS
        </a>

        <!-- TOGGLER -->
        <button class="navbar-toggler"
                type="button"
                data-bs-toggle="collapse"
                data-bs-target="#navbarNav"
                aria-controls="navbarNav"
                aria-expanded="false"
                aria-label="Apri menu di navigazione">
            <span class="navbar-toggler-label">MENU</span>
            <span class="navbar-toggler-icon"></span>
        </button>

        <!-- LINKS -->
        <div class="collapse navbar-collapse app-navbar-collapse" id="navbarNav">
            <form class="d-flex mt-3 mt-lg-0 ms-lg-3 me-lg-3 app-global-search"
                  method="get"
                  action="${pageContext.request.contextPath}${searchAction}">
                <input class="form-control form-control-sm app-global-search-input"
                       type="search"
                       name="q"
                       value="<c:out value='${param.q}'/>"
                       placeholder="Cerca nel sistema..."
                       aria-label="Ricerca globale">
                <button class="btn btn-outline-secondary btn-sm ms-2 app-global-search-btn" type="submit">Cerca</button>
            </form>

            <ul class="navbar-nav ms-auto align-items-lg-center app-navbar-links">

                <c:choose>
                    <c:when test="${sessionScope.userRole == 'ADMIN'}">
                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/admin">
                                Dashboard Admin
                            </a>
                        </li>

                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/admin/access-requests">
                                Richieste Accesso
                            </a>
                        </li>
                    </c:when>

                    <c:otherwise>
                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/dashboard">
                                Home
                            </a>
                        </li>

                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/calendar">
                                Calendario
                            </a>
                        </li>

                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/address-book">
                                Rubrica
                            </a>
                        </li>

                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/treatment-history">
                                Storico Trattamenti
                            </a>
                        </li>

                        <li class="nav-item">
                            <a class="nav-link"
                               href="<%= request.getContextPath() %>/dashboard/insights">
                                Dati e Statistiche
                            </a>
                        </li>
                    </c:otherwise>
                </c:choose>

                <li class="nav-item d-none d-lg-block mx-2">
                    <span class="nav-link disabled nav-separator">|</span>
                </li>

                <li class="nav-item nav-item-mobile-divider">
                    <a class="nav-link"
                       href="<%= request.getContextPath() %>/settings">
                        Impostazioni
                    </a>
                </li>

                <li class="nav-item">
                    <a class="nav-link logout-link"
                       href="<%= request.getContextPath() %>/logout">
                        Logout
                    </a>
                </li>

            </ul>
        </div>

    </div>
</nav>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/js/bootstrap.bundle.min.js" defer></script>
<script src="<%= request.getContextPath() %>/assets/js/loading-overlay.js?v=20260513-13" defer></script>
<script src="<%= request.getContextPath() %>/assets/js/modal-scroll-lock.js?v=20260721-1" defer></script>
