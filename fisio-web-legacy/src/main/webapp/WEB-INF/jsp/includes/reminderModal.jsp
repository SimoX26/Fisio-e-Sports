<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<div class="modal fade" id="reminderModal" tabindex="-1" aria-hidden="true">
    <div class="modal-dialog modal-lg modal-dialog-centered modal-dialog-scrollable">
        <div class="modal-content glass-card">
            <div class="modal-header">
                <h5 class="modal-title">Invia promemoria</h5>
                <button type="button" class="btn-close" data-bs-dismiss="modal" id="closeReminderModalBtn"></button>
            </div>
            <div class="modal-body">
                <div class="reminder-modal-grid">
                    <div class="reminder-modal-panel reminder-modal-panel--date">
                        <label class="form-label" for="reminderDate">Giorno</label>
                        <input type="date" class="form-control" id="reminderDate">
                    </div>

                    <div class="reminder-modal-panel reminder-modal-panel--appointments">
                        <div class="reminder-modal-head">
                            <label class="form-label mb-0">Appuntamenti</label>
                            <button type="button" class="btn btn-sm btn-outline-secondary" id="selectAllReminderAppointmentsBtn">
                                Seleziona tutti
                            </button>
                        </div>
                        <div id="reminderAppointmentList" class="reminder-appointment-list"></div>
                        <div id="reminderAppointmentEmpty" class="alert alert-light border mb-0 d-none">
                            Nessun appuntamento disponibile.
                        </div>
                    </div>

                    <div class="reminder-modal-panel reminder-modal-panel--message">
                        <div class="reminder-modal-head">
                            <label class="form-label mb-0" for="reminderTemplate">Messaggio</label>
                            <span class="reminder-modal-placeholders">
                                {nome paziente}, {giorno}, {ora inizio}, {ora fine}, {ora inizio - ora fine}
                            </span>
                        </div>
                        <textarea class="form-control" id="reminderTemplate" rows="2"></textarea>
                    </div>

                    <div class="reminder-modal-panel reminder-modal-panel--preview">
                        <div class="reminder-modal-head">
                            <label class="form-label mb-0">Anteprima</label>
                            <button type="button" class="btn btn-sm btn-outline-secondary" id="refreshReminderPreviewBtn">
                                Aggiorna anteprima
                            </button>
                        </div>
                        <div id="reminderPreviewEmpty" class="alert alert-light border mb-0 d-none">
                            Seleziona almeno un appuntamento.
                        </div>
                        <div id="reminderPreviewList" class="reminder-preview-list"></div>
                    </div>
                </div>

                <c:if test="${not requestScope.whatsAppConfigured}">
                    <div class="alert alert-warning py-2 small mt-3 mb-0" role="alert">
                        Servizio WhatsApp non configurato per questo account. Contattare l'amministratore di sistema.
                    </div>
                </c:if>
            </div>
            <div class="modal-footer">
                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal" id="cancelReminderModalBtn">Annulla</button>
                <button type="button" class="btn btn-primary" id="sendReminderBtn">Invia promemoria</button>
            </div>
        </div>
    </div>
</div>
