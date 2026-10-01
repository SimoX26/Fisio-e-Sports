<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Rubrica Pazienti • Fisio e Sports</title>

    <!-- Bootstrap -->
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.2/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="icon" type="image/png" href="<%= request.getContextPath() %>/assets/img/logo.png">
    <link rel="apple-touch-icon" href="<%= request.getContextPath() %>/assets/img/logo-192.png">
    <link rel="manifest" href="<%= request.getContextPath() %>/manifest.webmanifest">
    <meta name="theme-color" content="#1a73e8">
    <meta name="mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-capable" content="yes">
    <meta name="apple-mobile-web-app-title" content="FisioSport">

   <!-- Custom CSS -->
   <link rel="stylesheet" href="<%= request.getContextPath() %>/assets/css/style.css?v=20260721-2">

</head>

<body class="app-page address-book-page">

<!-- HEADER -->
<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div class="container app-shell mt-4">

    <!-- HEADER PAGINA -->
    <div class="page-header-row mb-4">
        <div>
            <h2 class="page-title">Rubrica Pazienti</h2>
        </div>

        <a href="<%= request.getContextPath() %>/address-book/create"
           class="btn btn-primary section-action-btn">
            Nuovo paziente
        </a>
    </div>

    <!-- LISTA PAZIENTI -->
    <div class="glass-card section-card address-book-card p-4">

        <c:if test="${not empty error}">
            <div class="alert alert-warning" role="alert">
                <c:out value="${error}" />
            </div>
        </c:if>

        <c:if test="${not empty treatedDateLabel}">
            <div class="alert alert-info d-flex justify-content-between align-items-center" role="status">
                <span>Filtro attivo: pazienti con appuntamenti il <strong><c:out value="${treatedDateLabel}" /></strong></span>
                <a href="<%= request.getContextPath() %>/address-book" class="btn btn-sm btn-outline-secondary">Rimuovi filtro</a>
            </div>
        </c:if>

        <c:if test="${empty treatedDateLabel and not empty treatedMonthLabel}">
            <div class="alert alert-info d-flex justify-content-between align-items-center" role="status">
                <span>Filtro attivo: pazienti con appuntamenti in <strong><c:out value="${treatedMonthLabel}" /></strong></span>
                <a href="<%= request.getContextPath() %>/address-book" class="btn btn-sm btn-outline-secondary">Rimuovi filtro</a>
            </div>
        </c:if>

        <c:if test="${param.created == '1'}">
            <div class="alert alert-success" role="alert">
                Paziente inserito correttamente.
            </div>
        </c:if>

        <c:if test="${param.updated == '1'}">
            <div class="alert alert-success" role="alert">
                Dati paziente e anamnesi salvati correttamente.
            </div>
        </c:if>

        <%--
            Qui il controller dovrebbe settare:
            request.setAttribute("patients", List<Patient>);
        --%>

        <c:choose>
            <c:when test="${empty patients}">

            <!-- EMPTY STATE -->
            <div class="text-center py-5 empty-state">
                <h5>Nessun paziente presente</h5>
                <p class="mb-3">
                    Inizia aggiungendo il primo paziente alla rubrica
                </p>
                <a href="<%= request.getContextPath() %>/address-book/create"
                   class="btn btn-soft">
                    Aggiungi paziente
                </a>
            </div>

            </c:when>
            <c:otherwise>

            <!-- TABELLA -->
            <div class="table-responsive address-book-table-wrap">
                <table class="table table-borderless align-middle mb-0 address-book-table">
                    <thead>
                    <tr>
                        <th>
                            <c:url var="sortByNameUrl" value="/address-book">
                                <c:if test="${not empty param.q}">
                                    <c:param name="q" value="${param.q}" />
                                </c:if>
                                <c:if test="${not empty treatedMonthParam}">
                                    <c:param name="treatedMonth" value="${treatedMonthParam}" />
                                </c:if>
                                <c:if test="${not empty treatedDateParam}">
                                    <c:param name="treatedDate" value="${treatedDateParam}" />
                                </c:if>
                                <c:param name="sortName" value="${nameSort == 'asc' ? 'desc' : 'asc'}" />
                            </c:url>
                            <a class="text-decoration-none text-reset" href="${sortByNameUrl}">
                                Nome
                                <c:if test="${nameSort == 'asc'}">↑</c:if>
                                <c:if test="${nameSort == 'desc'}">↓</c:if>
                            </a>
                        </th>
                        <th>
                            <c:url var="sortByCreatedUrl" value="/address-book">
                                <c:if test="${not empty param.q}">
                                    <c:param name="q" value="${param.q}" />
                                </c:if>
                                <c:if test="${not empty treatedMonthParam}">
                                    <c:param name="treatedMonth" value="${treatedMonthParam}" />
                                </c:if>
                                <c:if test="${not empty treatedDateParam}">
                                    <c:param name="treatedDate" value="${treatedDateParam}" />
                                </c:if>
                                <c:param name="sortCreated" value="${createdSort == 'asc' ? 'desc' : 'asc'}" />
                            </c:url>
                            <a class="text-decoration-none text-reset" href="${sortByCreatedUrl}">
                                Data creazione
                                <c:if test="${createdSort == 'asc'}">↑</c:if>
                                <c:if test="${createdSort == 'desc'}">↓</c:if>
                            </a>
                        </th>
                        <th>Telefono</th>
                        <th class="text-end">Azioni</th>
                    </tr>
                    </thead>
                    <tbody>

                    <c:forEach var="patient" items="${patients}">
                        <c:url var="patientDetailsUrl" value="/address-book">
                            <c:param name="openPatientId" value="${patient.id}" />
                        </c:url>
                        <tr>
                            <td class="patient-name-cell">
                                <a href="${patientDetailsUrl}"
                                   class="patient-name-link js-edit-patient"
                                   data-no-loading-overlay="true"
                                   data-id="<c:out value='${patient.id}'/>"
                                   data-full-name="<c:out value='${patient.fullName}'/>"
                                   data-email="<c:out value='${patient.email}'/>"
                                   data-phone="<c:out value='${patient.phone}'/>">
                                    <c:out value="${patient.fullName}" />
                                </a>
                            </td>
                            <td><c:out value="${patient.createdDateLabel}" /></td>
                            <td><c:out value="${patient.phone}" /></td>
                            <td class="text-end address-book-actions">
                                <div class="address-book-action-group">
                                    <a href="${pageContext.request.contextPath}/treatment-history?patientId=${patient.id}"
                                       class="btn btn-sm btn-outline-secondary">
                                        Cronologia Trattamenti
                                    </a>
                                    <button type="button"
                                            class="btn btn-sm btn-outline-danger btn-icon-only btn-trash-icon js-delete-patient"
                                            data-id="<c:out value='${patient.id}'/>"
                                            data-name="<c:out value='${patient.fullName}'/>"
                                            data-linked-appointments="<c:out value='${patient.linkedAppointmentsCount}'/>"
                                            aria-label="Elimina paziente"
                                            title="Elimina paziente"></button>
                                </div>
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

<!-- MODALE MODIFICA PAZIENTE -->
<div class="modal fade" id="editPatientModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable">
            <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Dettagli paziente</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <form method="post" action="<%= request.getContextPath() %>/address-book">
                <div class="modal-body">
                    <input type="hidden" name="action" value="update">
                    <input type="hidden" name="id" id="editPatientId">

                    <div class="row g-3">
                        <div class="col-12">
                            <label class="form-label">Nome e Cognome</label>
                            <input type="text" class="form-control" name="fullName" id="editPatientFullName" placeholder="Nome e cognome paziente" required>
                        </div>
                        <div class="col-12 col-md-6">
                            <label class="form-label">Email</label>
                            <input type="email" class="form-control" name="email" id="editPatientEmail">
                        </div>
                        <div class="col-12 col-md-6">
                            <label class="form-label">Telefono</label>
                            <input type="text" class="form-control" name="phone" id="editPatientPhone">
                        </div>
                    </div>

                    <input type="hidden" name="mergeTargetId" id="mergeTargetId">
                    <div id="mergeCandidatesBox" class="alert alert-info mt-3 d-none" role="alert">
                        <div class="fw-semibold mb-2">Possibile unione contatti</div>
                        <p class="mb-2">Esistono contatti con lo stesso nome. Se vuoi, puoi unirli ora. Opzione facoltativa.</p>
                        <label class="form-label" for="mergeCandidateSelect">Unisci questo contatto con:</label>
                        <select id="mergeCandidateSelect" class="form-select">
                            <option value="">Nessuna unione (mantieni contatti separati)</option>
                        </select>
                    </div>

                    <hr class="my-4">
                    <h6 class="mb-3">Scheda anamnesi</h6>

                    <div class="mb-3">
                        <label class="form-label">Data anamnesi</label>
                        <input type="date" class="form-control" name="assessmentDate">
                    </div>

                    <div class="mb-3">
                        <label class="form-label">Motivo del consulto</label>
                        <textarea class="form-control" name="chiefComplaint" rows="2"></textarea>
                    </div>

                    <div class="row g-3">
                        <div class="col-md-6">
                            <label class="form-label">Localizzazione dolore</label>
                            <input type="text" class="form-control" name="painLocation">
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Tipologia dolore</label>
                            <input type="text" class="form-control" name="painQuality">
                        </div>
                        <div class="col-12">
                            <label class="form-label">Sintomi associati</label>
                            <textarea class="form-control" name="associatedSymptoms" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-4">
                            <label class="form-label">Esordio</label>
                            <select class="form-select" name="onsetType">
                                <option value="">-</option>
                                <option value="ACUTE">Acuto</option>
                                <option value="SUBACUTE">Subacuto</option>
                                <option value="CHRONIC">Cronico</option>
                            </select>
                        </div>
                        <div class="col-md-8">
                            <label class="form-label">Contesto esordio</label>
                            <input type="text" class="form-control" name="onsetContext" placeholder="Trauma, stress, casuale...">
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-4">
                            <label class="form-label">Dolore invalidante</label>
                            <select class="form-select" name="isDisabling">
                                <option value="">-</option>
                                <option value="si">Si</option>
                                <option value="no">No</option>
                            </select>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Frequenza dolore</label>
                            <select class="form-select" name="painFrequency">
                                <option value="">-</option>
                                <option value="LOW">Bassa</option>
                                <option value="MEDIUM">Media</option>
                                <option value="HIGH">Alta</option>
                            </select>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Progressione dolore</label>
                            <select class="form-select" name="painProgression">
                                <option value="">-</option>
                                <option value="CONSTANT">Costante</option>
                                <option value="WORSE">Peggiorato</option>
                                <option value="BETTER">Migliorato</option>
                            </select>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-4">
                            <label class="form-label">Con movimento</label>
                            <select class="form-select" name="painWithMovement">
                                <option value="">-</option>
                                <option value="WORSE">Peggiora</option>
                                <option value="BETTER">Migliora</option>
                                <option value="UNCHANGED">Invariato</option>
                            </select>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Con riposo</label>
                            <select class="form-select" name="painWithRest">
                                <option value="">-</option>
                                <option value="WORSE">Peggiora</option>
                                <option value="BETTER">Migliora</option>
                                <option value="UNCHANGED">Invariato</option>
                            </select>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Intensita dolore (0-10)</label>
                            <input type="number" class="form-control" name="painIntensity" min="0" max="10">
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-3">
                            <label class="form-label">Dolore notturno</label>
                            <select class="form-select" name="nightPain">
                                <option value="">-</option>
                                <option value="si">Si</option>
                                <option value="no">No</option>
                            </select>
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Dolore al risveglio</label>
                            <select class="form-select" name="morningPain">
                                <option value="">-</option>
                                <option value="si">Si</option>
                                <option value="no">No</option>
                            </select>
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Farmaci dolore</label>
                            <select class="form-select" name="usesPainMeds">
                                <option value="">-</option>
                                <option value="si">Si</option>
                                <option value="no">No</option>
                            </select>
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Efficacia farmaco</label>
                            <select class="form-select" name="painMedsEffect">
                                <option value="">-</option>
                                <option value="YES">Si</option>
                                <option value="NO">No</option>
                                <option value="PARTIAL">Poco</option>
                            </select>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-4">
                            <label class="form-label">Esami strumentali</label>
                            <textarea class="form-control" name="clinicalTests" rows="2"></textarea>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Visite specialistiche</label>
                            <textarea class="form-control" name="specialistVisits" rows="2"></textarea>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Trattamenti precedenti</label>
                            <textarea class="form-control" name="previousTreatments" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-6">
                            <label class="form-label">Patologie pregresse</label>
                            <textarea class="form-control" name="pathologyHistory" rows="2"></textarea>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Farmaci regolari</label>
                            <textarea class="form-control" name="currentRegularDrugs" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-4">
                            <label class="form-label">Interventi chirurgici</label>
                            <textarea class="form-control" name="surgeryHistory" rows="2"></textarea>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Traumi</label>
                            <textarea class="form-control" name="traumaHistory" rows="2"></textarea>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Dispositivi usati</label>
                            <textarea class="form-control" name="devicesHistory" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-3">
                            <label class="form-label">Disturbi masticazione</label>
                            <select class="form-select" name="chewingDisorders">
                                <option value="">-</option>
                                <option value="si">Si</option>
                                <option value="no">No</option>
                            </select>
                        </div>
                        <div class="col-md-4">
                            <label class="form-label">Infezioni/infiammazioni importanti</label>
                            <textarea class="form-control" name="majorInfectionsHistory" rows="2"></textarea>
                        </div>
                        <div class="col-md-5">
                            <label class="form-label">Familiarita malattie</label>
                            <textarea class="form-control" name="familyHistory" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-2">
                            <label class="form-label">Altezza (cm)</label>
                            <input type="number" step="0.01" class="form-control" name="heightCm">
                        </div>
                        <div class="col-md-2">
                            <label class="form-label">Peso (kg)</label>
                            <input type="number" step="0.01" class="form-control" name="weightKg">
                        </div>
                        <div class="col-md-2">
                            <label class="form-label">Stile vita</label>
                            <select class="form-select" name="lifestyle">
                                <option value="">-</option>
                                <option value="SPORTY">Sportivo</option>
                                <option value="SEDENTARY">Sedentario</option>
                                <option value="MIXED">Misto</option>
                            </select>
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Sport praticato</label>
                            <input type="text" class="form-control" name="sportPractice">
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Fumo/alcol/sostanze</label>
                            <input type="text" class="form-control" name="substanceUse">
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-2">
                            <label class="form-label">Qualita sonno (0-4)</label>
                            <input type="number" class="form-control" name="sleepQuality" min="0" max="4">
                        </div>
                        <div class="col-md-2">
                            <label class="form-label">Stress (0-4)</label>
                            <input type="number" class="form-control" name="stressLevel" min="0" max="4">
                        </div>
                        <div class="col-md-3">
                            <label class="form-label">Alimentazione</label>
                            <select class="form-select" name="dietQuality">
                                <option value="">-</option>
                                <option value="HEALTHY">Sana</option>
                                <option value="IMBALANCED">Squilibrata</option>
                                <option value="MIXED">Mista</option>
                            </select>
                        </div>
                        <div class="col-md-5">
                            <label class="form-label">Note ciclo/andrologiche-ginecologiche</label>
                            <input type="text" class="form-control" name="femaleCycleNotes">
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-6">
                            <label class="form-label">Condizioni patologiche (separate da virgola o su righe diverse)</label>
                            <textarea class="form-control" name="conditionsPathology" rows="2"></textarea>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Sintomi rilevanti (separati da virgola o su righe diverse)</label>
                            <textarea class="form-control" name="conditionsSymptom" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-6">
                            <label class="form-label">Familiarita (separate da virgola o su righe diverse)</label>
                            <textarea class="form-control" name="conditionsFamilyHistory" rows="2"></textarea>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Allergie/intolleranze (separate da virgola o su righe diverse)</label>
                            <textarea class="form-control" name="conditionsAllergy" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="row g-3 mt-1">
                        <div class="col-md-6">
                            <label class="form-label">Farmaci rilevanti (separati da virgola o su righe diverse)</label>
                            <textarea class="form-control" name="conditionsDrug" rows="2"></textarea>
                        </div>
                        <div class="col-md-6">
                            <label class="form-label">Revisione sistemica (separate da virgola o su righe diverse)</label>
                            <textarea class="form-control" name="conditionsSystemReview" rows="2"></textarea>
                        </div>
                    </div>

                    <div class="mb-3 mt-3">
                        <label class="form-label">Altre condizioni (separate da virgola o su righe diverse)</label>
                        <textarea class="form-control" name="conditionsOther" rows="2"></textarea>
                    </div>

                    <div class="mb-2">
                        <label class="form-label">Note libere</label>
                        <textarea class="form-control" name="freeNotesJson" rows="2" placeholder="Inserisci note testuali libere"></textarea>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                    <button type="submit" class="btn btn-primary">Salva</button>
                </div>
            </form>
        </div>
    </div>
</div>

<!-- MODALE CONFERMA ELIMINAZIONE -->
<div class="modal fade" id="confirmDeletePatientModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Conferma eliminazione</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <form method="post" action="<%= request.getContextPath() %>/address-book">
                <div class="modal-body">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="id" id="deletePatientId">
                    <input type="hidden" name="forceDeleteWithLinkedAppointments" id="forceDeleteWithLinkedAppointments" value="0">
                    <p class="mb-0">
                        <span id="deletePatientPromptText">Vuoi eliminare il paziente</span>
                        <strong id="deletePatientName"></strong>?
                    </p>
                    <div id="deleteDangerZone" class="alert alert-warning mt-3 d-none mb-0" role="alert">
                        Questo paziente ha <strong id="linkedAppointmentsCount"></strong>
                        <span id="linkedAppointmentsLabel">appuntamenti collegati</span>.
                        Eliminandolo, il paziente verrà rimosso definitivamente dal sistema.
                        Gli appuntamenti e lo storico trattamenti resteranno disponibili, ma non saranno più associati a questo paziente.
                        Vuoi continuare?
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                    <button type="submit"
                            class="btn btn-danger btn-icon-only btn-trash-icon"
                            aria-label="Conferma eliminazione paziente"
                            title="Conferma eliminazione paziente"></button>
                </div>
            </form>
        </div>
    </div>
</div>

<div class="modal fade" id="confirmMergePatientModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Conferma unione contatti</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal"></button>
            </div>
            <div class="modal-body">
                <p class="mb-2">Stai per unire il contatto:</p>
                <p class="mb-2"><strong id="mergeConfirmSourceName">-</strong></p>
                <p class="mb-2">nel contatto:</p>
                <p class="mb-2"><strong id="mergeConfirmTargetName">-</strong></p>
                <p class="mb-0 text-muted">Questa operazione e irreversibile.</p>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Annulla</button>
                <button type="button" class="btn btn-danger" id="confirmMergePatientBtn">Conferma unione</button>
            </div>
        </div>
    </div>
</div>

<c:if test="${param.created == '1'}">
<script>
sessionStorage.setItem('addressBookCreateSubmitted', '1');
</script>
</c:if>

<script>
(function () {
    const lockKey = 'lockBackAddressBook';
    const params = new URLSearchParams(window.location.search);

    if (params.get('lockBack') === '1') {
        sessionStorage.setItem(lockKey, '1');
        params.delete('lockBack');
        const cleanUrl = window.location.pathname
            + (params.toString() ? '?' + params.toString() : '')
            + window.location.hash;
        history.replaceState(null, '', cleanUrl);
    }

    if (sessionStorage.getItem(lockKey) !== '1') {
        return;
    }

    history.pushState(null, '', window.location.href);
    window.addEventListener('popstate', function () {
        history.pushState(null, '', window.location.href);
    });
})();
</script>

<script>
document.addEventListener('DOMContentLoaded', function () {
    const contextPath = '<%= request.getContextPath() %>';
    const editModalEl = document.getElementById('editPatientModal');
    const deleteModalEl = document.getElementById('confirmDeletePatientModal');
    const mergeConfirmModalEl = document.getElementById('confirmMergePatientModal');
    const editModal = editModalEl ? new bootstrap.Modal(editModalEl) : null;
    const deleteModal = deleteModalEl ? new bootstrap.Modal(deleteModalEl) : null;
    const mergeConfirmModal = mergeConfirmModalEl ? new bootstrap.Modal(mergeConfirmModalEl) : null;
    const editForm = editModalEl ? editModalEl.querySelector('form') : null;

    const editId = document.getElementById('editPatientId');
    const editFullName = document.getElementById('editPatientFullName');
    const editEmail = document.getElementById('editPatientEmail');
    const editPhone = document.getElementById('editPatientPhone');
    const mergeCandidatesBox = document.getElementById('mergeCandidatesBox');
    const mergeCandidateSelect = document.getElementById('mergeCandidateSelect');
    const mergeTargetIdInput = document.getElementById('mergeTargetId');
    const mergeConfirmSourceName = document.getElementById('mergeConfirmSourceName');
    const mergeConfirmTargetName = document.getElementById('mergeConfirmTargetName');
    const confirmMergePatientBtn = document.getElementById('confirmMergePatientBtn');
    let mergeSubmitPending = false;

    const deleteId = document.getElementById('deletePatientId');
    const deleteName = document.getElementById('deletePatientName');
    const deletePatientPromptText = document.getElementById('deletePatientPromptText');
    const forceDeleteWithLinkedAppointments = document.getElementById('forceDeleteWithLinkedAppointments');
    const deleteDangerZone = document.getElementById('deleteDangerZone');
    const linkedAppointmentsCount = document.getElementById('linkedAppointmentsCount');
    const linkedAppointmentsLabel = document.getElementById('linkedAppointmentsLabel');

    function setFormFieldValue(name, value) {
        if (!editForm) return;
        const field = editForm.elements.namedItem(name);
        if (!field) return;
        field.value = value == null ? '' : String(value);
    }

    function resetMergeCandidatesUi() {
        if (mergeCandidatesBox) {
            mergeCandidatesBox.classList.add('d-none');
        }
        if (mergeTargetIdInput) {
            mergeTargetIdInput.value = '';
        }
        if (mergeCandidateSelect) {
            mergeCandidateSelect.innerHTML = '<option value="">Nessuna unione (mantieni contatti separati)</option>';
            mergeCandidateSelect.value = '';
        }
    }

    async function refreshMergeCandidates() {
        if (!editId || !mergeCandidatesBox || !mergeCandidateSelect) {
            return;
        }
        const id = (editId.value || '').trim();
        const fullName = (editFullName?.value || '').trim();

        if (!id || !fullName) {
            resetMergeCandidatesUi();
            return;
        }

        const url = new URL(contextPath + '/address-book', window.location.origin);
        url.searchParams.set('action', 'merge-candidates');
        url.searchParams.set('id', id);
        url.searchParams.set('fullName', fullName);

        try {
            const response = await fetch(url.toString(), { headers: { 'Accept': 'application/json' } });
            if (!response.ok) {
                throw new Error('Impossibile caricare candidati merge');
            }
            const candidates = await response.json();
            mergeCandidateSelect.innerHTML = '<option value="">Nessuna unione (mantieni contatti separati)</option>';
            if (!Array.isArray(candidates) || candidates.length === 0) {
                mergeCandidatesBox.classList.add('d-none');
                if (mergeTargetIdInput) {
                    mergeTargetIdInput.value = '';
                }
                return;
            }
            candidates.forEach(function (candidate) {
                const option = document.createElement('option');
                option.value = String(candidate.id || '');
                const phone = candidate.phone ? ' • ' + candidate.phone : '';
                const created = candidate.createdDate ? ' • creato il ' + candidate.createdDate : '';
                option.textContent = (candidate.fullName || 'Contatto') + phone + created;
                mergeCandidateSelect.appendChild(option);
            });
            mergeCandidatesBox.classList.remove('d-none');
        } catch (error) {
            resetMergeCandidatesUi();
        }
    }

    async function openPatientDetails(button) {
        if (!editModal || !button) return;
        if (editForm) {
            editForm.reset();
        }
        resetMergeCandidatesUi();
        editId.value = button.dataset.id || '';
        editFullName.value = button.dataset.fullName || '';
        editEmail.value = button.dataset.email || '';
        editPhone.value = button.dataset.phone || '';

        const patientId = button.dataset.id || '';
        if (patientId) {
            try {
                const response = await fetch(
                    contextPath + '/address-book?action=anamnesis-details&id=' + encodeURIComponent(patientId),
                    { headers: { 'Accept': 'application/json' } }
                );
                if (!response.ok) {
                    throw new Error('Impossibile caricare i dettagli anamnesi');
                }
                const payload = await response.json();
                const anamnesis = payload && payload.anamnesis ? payload.anamnesis : {};
                Object.keys(anamnesis).forEach(function (key) {
                    setFormFieldValue(key, anamnesis[key]);
                });
            } catch (error) {
                alert('Impossibile caricare i dati anamnestici salvati.');
            }
        }
        await refreshMergeCandidates();
        editModal.show();
    }

    document.querySelectorAll('.js-edit-patient').forEach(function (button) {
        button.addEventListener('click', async function (event) {
            event.preventDefault();
            await openPatientDetails(this);
        });

        const row = button.closest('tr');
        if (row) {
            row.addEventListener('click', async function (event) {
                if (event.target.closest('a, button, input, select, textarea')) {
                    return;
                }
                await openPatientDetails(button);
            });
        }
    });

    const openPatientId = new URLSearchParams(window.location.search).get('openPatientId');
    if (openPatientId) {
        const targetButton = Array.from(document.querySelectorAll('.js-edit-patient'))
            .find(function (button) {
                return (button.dataset.id || '') === openPatientId;
            });
        if (targetButton) {
            openPatientDetails(targetButton);
        }
    }

    document.querySelectorAll('.js-delete-patient').forEach(function (button) {
        button.addEventListener('click', function () {
            if (!deleteModal) return;
            deleteId.value = this.dataset.id || '';
            deleteName.textContent = this.dataset.name || 'questo paziente';
            const linkedCount = Number.parseInt(this.dataset.linkedAppointments || '0', 10);
            const hasLinkedAppointments = Number.isFinite(linkedCount) && linkedCount > 0;
            if (forceDeleteWithLinkedAppointments) {
                forceDeleteWithLinkedAppointments.value = hasLinkedAppointments ? '1' : '0';
            }
            if (deletePatientPromptText) {
                deletePatientPromptText.textContent = hasLinkedAppointments
                    ? "Confermi l'eliminazione definitiva del paziente"
                    : 'Vuoi eliminare il paziente';
            }
            if (deleteDangerZone && linkedAppointmentsCount) {
                deleteDangerZone.classList.toggle('d-none', !hasLinkedAppointments);
                linkedAppointmentsCount.textContent = String(hasLinkedAppointments ? linkedCount : 0);
            }
            if (linkedAppointmentsLabel) {
                linkedAppointmentsLabel.textContent = linkedCount === 1 ? 'appuntamento collegato' : 'appuntamenti collegati';
            }
            deleteModal.show();
        });
    });

    if (mergeCandidateSelect && mergeTargetIdInput) {
        mergeCandidateSelect.addEventListener('change', function () {
            mergeTargetIdInput.value = this.value || '';
        });
    }

    if (editForm && mergeCandidateSelect) {
        editForm.addEventListener('submit', function (event) {
            if (mergeSubmitPending) {
                mergeSubmitPending = false;
                return;
            }
            const selectedTargetId = (mergeCandidateSelect.value || '').trim();
            if (!selectedTargetId) {
                return;
            }
            event.preventDefault();
            if (window.appLoadingOverlay && typeof window.appLoadingOverlay.hide === 'function') {
                window.appLoadingOverlay.hide();
            }
            const selectedOption = mergeCandidateSelect.options[mergeCandidateSelect.selectedIndex];
            const sourceName = (editFullName?.value || '').trim() || 'contatto corrente';
            const targetLabel = (selectedOption?.textContent || 'contatto selezionato').trim();
            if (mergeConfirmSourceName) {
                mergeConfirmSourceName.textContent = sourceName;
            }
            if (mergeConfirmTargetName) {
                mergeConfirmTargetName.textContent = targetLabel;
            }
            if (mergeConfirmModal) {
                mergeConfirmModal.show();
            }
        });
    }

    if (confirmMergePatientBtn && editForm) {
        confirmMergePatientBtn.addEventListener('click', function () {
            mergeSubmitPending = true;
            if (mergeConfirmModal) {
                mergeConfirmModal.hide();
            }
            editForm.requestSubmit();
        });
    }

    [editFullName].forEach(function (field) {
        if (!field) return;
        field.addEventListener('blur', function () {
            refreshMergeCandidates();
        });
    });
});
</script>

</body>
</html>
