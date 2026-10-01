<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>

<!DOCTYPE html>
<html lang="it">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Dati e Statistiche • Fisio e Sports</title>

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
<body class="app-page" data-context-path="<%= request.getContextPath() %>">

<%@ include file="/WEB-INF/jsp/includes/header.jsp" %>

<div id="appNoticeContainer" class="app-notice-container" aria-live="polite" aria-atomic="true"></div>

<div class="container app-shell mt-4">
    <div class="page-header-row mb-4">
        <div>
            <h2 class="page-title mb-0">Dati e Statistiche</h2>
        </div>
        <div class="d-flex gap-2">
            <select id="kpiScopeSelect" class="form-select form-select-sm">
                <option value="me">I miei dati</option>
                <option value="global">Dati globali</option>
            </select>
            <select id="kpiMonthsSelect" class="form-select form-select-sm">
                <option value="6">Ultimi 6 mesi</option>
                <option value="12" selected>Ultimi 12 mesi</option>
                <option value="24">Ultimi 24 mesi</option>
            </select>
        </div>
    </div>

    <div class="kpi-section mb-4">
        <div class="kpi-section-head mb-3">
            <h5 class="mb-1">Dati operativi - <span id="kpiSectionPeriodOperative">mese corrente</span></h5>
            <p class="kpi-section-note mb-0">Cosa e successo nel mese sugli appuntamenti pianificati (basati su data evento).</p>
        </div>
        <div class="kpi-grid kpi-grid--operativi stats-kpi-grid">
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Appuntamenti del mese <button type="button" class="kpi-info-btn" data-kpi="appointments_month" aria-label="Info Appuntamenti del mese">i</button></div>
                    <div class="kpi-value" id="kpiAppointmentsMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Trattamenti completati <button type="button" class="kpi-info-btn" data-kpi="completed_treatments" aria-label="Info Trattamenti completati">i</button></div>
                    <div class="kpi-value" id="kpiCompletedMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Appuntamenti cancellati <button type="button" class="kpi-info-btn" data-kpi="cancelled_appointments" aria-label="Info Appuntamenti cancellati">i</button></div>
                    <div class="kpi-value" id="kpiCancelledMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Ore prenotate <button type="button" class="kpi-info-btn" data-kpi="booked_hours" aria-label="Info Ore prenotate">i</button></div>
                    <div class="kpi-value" id="kpiBookedHoursMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Tasso di cancellazione <button type="button" class="kpi-info-btn" data-kpi="cancellation_rate" aria-label="Info Tasso di cancellazione">i</button></div>
                    <div class="kpi-value" id="kpiCancellationRate">0%</div>
                </div>
            </div>
        </div>
    </div>

    <div class="kpi-section mb-4">
        <div class="kpi-section-head mb-3">
            <h5 class="mb-1">Dati gestionali - <span id="kpiSectionPeriodManagement">mese corrente</span></h5>
            <p class="kpi-section-note mb-0">Metriche di crescita gestionale basate su creazione dei record nel mese.</p>
        </div>
        <div class="kpi-grid kpi-grid--gestionali stats-kpi-grid stats-kpi-grid--gestionali">
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Nuovi appuntamenti creati <button type="button" class="kpi-info-btn" data-kpi="new_created_appointments" aria-label="Info Nuovi appuntamenti creati">i</button></div>
                    <div class="kpi-value" id="kpiCreatedMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Pazienti attivi nel mese <button type="button" class="kpi-info-btn" data-kpi="active_patients_month" aria-label="Info Pazienti attivi nel mese">i</button></div>
                    <div class="kpi-value" id="kpiActivePatientsMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Nuovi pazienti (primo appuntamento) <button type="button" class="kpi-info-btn" data-kpi="new_patients_first_visit" aria-label="Info Nuovi pazienti">i</button></div>
                    <div class="kpi-value" id="kpiNewPatientsFirstMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Pazienti di ritorno <button type="button" class="kpi-info-btn" data-kpi="returning_patients" aria-label="Info Pazienti di ritorno">i</button></div>
                    <div class="kpi-value" id="kpiReturningPatientsMonth">0</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Saturazione agenda <button type="button" class="kpi-info-btn" data-kpi="agenda_saturation" aria-label="Info Saturazione agenda">i</button></div>
                    <div class="kpi-value" id="kpiAgendaSaturation">0%</div>
                </div>
            </div>
            <div class="kpi-grid__item">
                <div class="glass-card section-card p-4 h-100 stats-kpi-card">
                    <div class="kpi-label mb-1">Media appuntamenti per paziente <button type="button" class="kpi-info-btn" data-kpi="appointments_per_patient" aria-label="Info Media appuntamenti per paziente">i</button></div>
                    <div class="kpi-value" id="kpiAppointmentsPerPatient">0,0</div>
                </div>
            </div>
        </div>
    </div>

    <div class="glass-card section-card p-4 mb-4">
        <h5 class="mb-1">Grafico mensile</h5>
        <div class="kpi-chart-wrap">
            <canvas id="kpiTrendChart"></canvas>
        </div>
    </div>

    <div class="glass-card section-card p-4">
        <h5 class="mb-3">Dettaglio mensile</h5>
        <div class="table-responsive">
            <table class="table table-borderless align-middle mb-0">
                <thead>
                <tr class="kpi-table-groups">
                    <th rowspan="2">Mese</th>
                    <th colspan="5">Dati operativi</th>
                    <th colspan="6">Dati gestionali</th>
                </tr>
                <tr>
                    <th>Appuntamenti mese</th>
                    <th>Trattamenti completati</th>
                    <th>Appuntamenti cancellati</th>
                    <th>Ore prenotate</th>
                    <th>Tasso cancellazione</th>
                    <th>Nuovi appuntamenti</th>
                    <th>Nuovi pazienti</th>
                    <th>Pazienti attivi</th>
                    <th>Pazienti di ritorno</th>
                    <th>Saturazione agenda</th>
                    <th>Media app./paziente</th>
                </tr>
                </thead>
                <tbody id="kpiTableBody">
                <tr><td colspan="12" class="text-muted">Nessun dato disponibile</td></tr>
                </tbody>
            </table>
        </div>
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js"></script>
<script>
document.addEventListener('DOMContentLoaded', function () {
    const KPI_HELP = {
        appointments_month: {
            title: 'Appuntamenti del mese',
            description: 'Numero totale di appuntamenti pianificati nel mese selezionato.',
            formula: 'Totale appuntamenti nel mese'
        },
        completed_treatments: {
            title: 'Trattamenti completati',
            description: 'Numero di appuntamenti/trattamenti conclusi nel mese selezionato.',
            formula: 'Totale appuntamenti completati nel mese'
        },
        cancelled_appointments: {
            title: 'Appuntamenti cancellati',
            description: 'Numero di appuntamenti annullati nel mese selezionato.',
            formula: 'Totale appuntamenti cancellati nel mese'
        },
        booked_hours: {
            title: 'Ore prenotate',
            description: 'Totale delle ore prenotate negli appuntamenti confermati del mese.',
            formula: 'Somma delle durate degli appuntamenti del mese'
        },
        cancellation_rate: {
            title: 'Tasso di cancellazione',
            description: 'Percentuale di appuntamenti cancellati rispetto agli appuntamenti del mese.',
            formula: 'Appuntamenti cancellati ÷ appuntamenti totali × 100',
            details: 'Se il denominatore è 0, il valore mostrato è 0%.'
        },
        new_created_appointments: {
            title: 'Nuovi appuntamenti creati',
            description: 'Numero di appuntamenti inseriti nel sistema durante il mese selezionato.',
            formula: 'Totale appuntamenti creati nel mese'
        },
        active_patients_month: {
            title: 'Pazienti attivi nel mese',
            description: 'Pazienti che hanno avuto almeno un appuntamento valido nel mese selezionato.',
            formula: 'Totale pazienti con almeno un appuntamento nel mese'
        },
        new_patients_first_visit: {
            title: 'Nuovi pazienti',
            description: 'Pazienti che hanno effettuato il primo appuntamento nel periodo selezionato.',
            formula: 'Totale pazienti al primo appuntamento nel mese'
        },
        returning_patients: {
            title: 'Pazienti di ritorno',
            description: 'Pazienti già presenti con appuntamenti precedenti e almeno un appuntamento nel mese.',
            formula: 'Pazienti attivi nel mese - nuovi pazienti del mese'
        },
        agenda_saturation: {
            title: 'Saturazione agenda',
            description: 'Percentuale di ore disponibili occupate da appuntamenti prenotati.',
            formula: 'Ore prenotate ÷ ore disponibili × 100',
            details: 'Ore disponibili calcolate con capacità mensile configurata nel sistema.'
        },
        appointments_per_patient: {
            title: 'Media appuntamenti per paziente',
            description: 'Numero medio di appuntamenti per ogni paziente attivo nel mese.',
            formula: 'Appuntamenti del mese ÷ pazienti attivi del mese',
            details: 'Se i pazienti attivi sono 0, il valore mostrato è 0,0.'
        }
    };
    const contextPath = document.body.dataset.contextPath || '';
    const scopeSelect = document.getElementById('kpiScopeSelect');
    const monthsSelect = document.getElementById('kpiMonthsSelect');
    const tableBody = document.getElementById('kpiTableBody');
    const kpiAppointmentsMonth = document.getElementById('kpiAppointmentsMonth');
    const kpiCompletedMonth = document.getElementById('kpiCompletedMonth');
    const kpiCancelledMonth = document.getElementById('kpiCancelledMonth');
    const kpiBookedHoursMonth = document.getElementById('kpiBookedHoursMonth');
    const kpiCancellationRate = document.getElementById('kpiCancellationRate');
    const kpiCreatedMonth = document.getElementById('kpiCreatedMonth');
    const kpiActivePatientsMonth = document.getElementById('kpiActivePatientsMonth');
    const kpiNewPatientsFirstMonth = document.getElementById('kpiNewPatientsFirstMonth');
    const kpiReturningPatientsMonth = document.getElementById('kpiReturningPatientsMonth');
    const kpiAgendaSaturation = document.getElementById('kpiAgendaSaturation');
    const kpiAppointmentsPerPatient = document.getElementById('kpiAppointmentsPerPatient');
    const kpiSectionPeriodOperative = document.getElementById('kpiSectionPeriodOperative');
    const kpiSectionPeriodManagement = document.getElementById('kpiSectionPeriodManagement');
    const chartCanvas = document.getElementById('kpiTrendChart');
    let trendChart = null;

    function applyKpiHelp() {
        document.querySelectorAll('.kpi-info-btn').forEach(function (button) {
            const key = button.dataset.kpi;
            const item = KPI_HELP[key];
            if (!item) return;
            const parts = [item.title, item.description, 'Formula: ' + item.formula];
            if (item.details) parts.push(item.details);
            const text = parts.join('\n');
            button.setAttribute('data-tooltip', text);
            button.setAttribute('aria-label', item.title + '. ' + item.description + ' Formula: ' + item.formula + (item.details ? ' ' + item.details : ''));
            button.removeAttribute('title');
        });
    }

    function initKpiTooltipInteractions() {
        const buttons = Array.from(document.querySelectorAll('.kpi-info-btn'));
        if (!buttons.length) return;

        function closeAll(except) {
            buttons.forEach(function (btn) {
                if (except && btn === except) return;
                btn.classList.remove('is-open');
                btn.setAttribute('aria-expanded', 'false');
            });
        }

        buttons.forEach(function (button) {
            button.setAttribute('aria-expanded', 'false');

            button.addEventListener('click', function (event) {
                event.preventDefault();
                event.stopPropagation();
                const willOpen = !button.classList.contains('is-open');
                closeAll(button);
                if (willOpen) {
                    button.classList.add('is-open');
                    button.setAttribute('aria-expanded', 'true');
                } else {
                    button.classList.remove('is-open');
                    button.setAttribute('aria-expanded', 'false');
                }
            });

            button.addEventListener('keydown', function (event) {
                if (event.key === 'Escape') {
                    button.classList.remove('is-open');
                    button.setAttribute('aria-expanded', 'false');
                    button.blur();
                }
            });
        });

        document.addEventListener('click', function () { closeAll(null); });
        document.addEventListener('touchstart', function (event) {
            const target = event.target;
            if (!(target instanceof Element) || !target.closest('.kpi-info-btn')) {
                closeAll(null);
            }
        }, { passive: true });
    }

    function showNotice(message, variant) {
        const container = document.getElementById('appNoticeContainer');
        if (!container) return;
        const box = document.createElement('div');
        box.className = 'app-notice app-notice--' + (variant || 'info');
        box.innerHTML = '<div class="app-notice__body"></div><button type="button" class="app-notice__close" aria-label="Chiudi">&times;</button>';
        box.querySelector('.app-notice__body').textContent = message;
        box.querySelector('.app-notice__close').addEventListener('click', function () { box.remove(); });
        container.appendChild(box);
        requestAnimationFrame(function () { box.classList.add('app-notice--visible'); });
        setTimeout(function () { box.remove(); }, 4500);
    }

    function monthLabel(year, month) {
        const date = new Date(year, month - 1, 1);
        return date.toLocaleDateString('it-IT', { month: 'short', year: 'numeric' });
    }

    function monthLabelLong(year, month) {
        const date = new Date(year, month - 1, 1);
        return date.toLocaleDateString('it-IT', { month: 'long', year: 'numeric' });
    }

    function formatNumber(value) {
        return Number(value || 0).toLocaleString('it-IT');
    }

    function formatPercent(num, den) {
        if (!den) return '0%';
        return ((num / den) * 100).toFixed(1).replace('.', ',') + '%';
    }

    function formatHoursFromMinutes(minutes) {
        const value = Number(minutes || 0) / 60;
        return Math.round(value).toLocaleString('it-IT');
    }

    function formatFixed(value, digits) {
        return Number(value || 0).toLocaleString('it-IT', {
            minimumFractionDigits: digits,
            maximumFractionDigits: digits
        });
    }

    function updateCards(series) {
        const latest = series[0];
        if (!latest) {
            kpiAppointmentsMonth.textContent = '0';
            kpiCompletedMonth.textContent = '0';
            kpiCancelledMonth.textContent = '0';
            kpiActivePatientsMonth.textContent = '0';
            kpiNewPatientsFirstMonth.textContent = '0';
            kpiReturningPatientsMonth.textContent = '0';
            kpiAgendaSaturation.textContent = '0%';
            kpiAppointmentsPerPatient.textContent = '0,0';
            kpiBookedHoursMonth.textContent = '0';
            kpiCancellationRate.textContent = '0%';
            kpiCreatedMonth.textContent = '0';
            if (kpiSectionPeriodOperative) kpiSectionPeriodOperative.textContent = 'mese corrente';
            if (kpiSectionPeriodManagement) kpiSectionPeriodManagement.textContent = 'mese corrente';
            return;
        }
        const referenceMonth = monthLabelLong(latest.year, latest.month);
        if (kpiSectionPeriodOperative) kpiSectionPeriodOperative.textContent = referenceMonth;
        if (kpiSectionPeriodManagement) kpiSectionPeriodManagement.textContent = referenceMonth;
        kpiAppointmentsMonth.textContent = formatNumber(latest.appointmentsInMonth);
        kpiCompletedMonth.textContent = formatNumber(latest.appointmentsCompleted);
        kpiCancelledMonth.textContent = formatNumber(latest.appointmentsCancelled);
        kpiActivePatientsMonth.textContent = formatNumber(latest.activePatientsMonth);
        kpiNewPatientsFirstMonth.textContent = formatNumber(latest.newPatientsFirstAppointmentMonth);
        kpiReturningPatientsMonth.textContent = formatNumber(latest.returningPatientsMonth);
        kpiAgendaSaturation.textContent = formatFixed(latest.agendaSaturationPct, 1) + '%';
        kpiAppointmentsPerPatient.textContent = formatFixed(latest.appointmentsPerActivePatient, 1);
        kpiBookedHoursMonth.textContent = formatHoursFromMinutes(latest.totalBookedMinutes);
        kpiCancellationRate.textContent = formatPercent(latest.appointmentsCancelled, latest.appointmentsInMonth);
        kpiCreatedMonth.textContent = formatNumber(latest.appointmentsCreated);
    }

    function updateTable(series) {
        if (!series.length) {
            tableBody.innerHTML = '<tr><td colspan="12" class="text-muted">Nessun dato disponibile</td></tr>';
            return;
        }
        tableBody.innerHTML = series.map(function (row) {
            return '<tr>'
                + '<td>' + monthLabel(row.year, row.month) + '</td>'
                + '<td>' + formatNumber(row.appointmentsInMonth) + '</td>'
                + '<td>' + formatNumber(row.appointmentsCompleted) + '</td>'
                + '<td>' + formatNumber(row.appointmentsCancelled) + '</td>'
                + '<td>' + formatHoursFromMinutes(row.totalBookedMinutes) + '</td>'
                + '<td>' + formatPercent(row.appointmentsCancelled, row.appointmentsInMonth) + '</td>'
                + '<td>' + formatNumber(row.appointmentsCreated) + '</td>'
                + '<td>' + formatNumber(row.newPatientsFirstAppointmentMonth) + '</td>'
                + '<td>' + formatNumber(row.activePatientsMonth) + '</td>'
                + '<td>' + formatNumber(row.returningPatientsMonth) + '</td>'
                + '<td>' + formatFixed(row.agendaSaturationPct, 1) + '%</td>'
                + '<td>' + formatFixed(row.appointmentsPerActivePatient, 1) + '</td>'
                + '</tr>';
        }).join('');
    }

    function updateChart(series) {
        const isMobile = window.matchMedia('(max-width: 576px)').matches;
        const ordered = series.slice().reverse();
        const labels = ordered.map(function (r) { return monthLabel(r.year, r.month); });
        const appointmentsMonth = ordered.map(function (r) { return r.appointmentsInMonth || 0; });
        const completed = ordered.map(function (r) { return r.appointmentsCompleted || 0; });
        const cancelled = ordered.map(function (r) { return r.appointmentsCancelled || 0; });
        const created = ordered.map(function (r) { return r.appointmentsCreated || 0; });
        const newPatients = ordered.map(function (r) { return r.newPatientsMonth || 0; });

        if (trendChart) trendChart.destroy();
        trendChart = new Chart(chartCanvas, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    { label: 'Appuntamenti del mese', data: appointmentsMonth, borderColor: '#7950f2', backgroundColor: 'rgba(121,80,242,.10)', tension: .28, fill: false, borderWidth: 2.2, pointRadius: isMobile ? 1.3 : 2.2, pointHoverRadius: 4, yAxisID: 'y' },
                    { label: 'Trattamenti completati', data: completed, borderColor: '#1f8f47', backgroundColor: 'rgba(31,143,71,.10)', tension: .28, fill: false, borderWidth: 2.2, pointRadius: isMobile ? 1.3 : 2.2, pointHoverRadius: 4, yAxisID: 'y' },
                    { label: 'Appuntamenti cancellati', data: cancelled, borderColor: '#c53929', backgroundColor: 'rgba(197,57,41,.10)', tension: .28, fill: false, borderWidth: 2.2, pointRadius: isMobile ? 1.3 : 2.2, pointHoverRadius: 4, yAxisID: 'y' },
                    { label: 'Nuovi appuntamenti creati', data: created, borderColor: '#e67700', backgroundColor: 'rgba(230,119,0,.10)', tension: .28, fill: false, borderWidth: 2.2, pointRadius: isMobile ? 1.3 : 2.2, pointHoverRadius: 4, yAxisID: 'y' },
                    { label: 'Nuovi pazienti acquisiti', data: newPatients, borderColor: '#1a73e8', backgroundColor: 'rgba(26,115,232,.10)', tension: .28, fill: false, borderWidth: 2.2, pointRadius: isMobile ? 1.3 : 2.2, pointHoverRadius: 4, yAxisID: 'y' }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: {
                        position: isMobile ? 'bottom' : 'top',
                        align: isMobile ? 'center' : 'start',
                        labels: {
                            usePointStyle: true,
                            boxWidth: 8,
                            boxHeight: 8,
                            padding: isMobile ? 12 : 16,
                            font: { size: isMobile ? 11 : 12, weight: '600' }
                        }
                    },
                    tooltip: {
                        backgroundColor: 'rgba(24,28,34,.94)',
                        titleFont: { size: 12, weight: '700' },
                        bodyFont: { size: 12 },
                        padding: 10,
                        displayColors: true,
                        callbacks: {
                            title: function (items) {
                                return items[0] ? items[0].label : '';
                            },
                            label: function (context) {
                                const value = formatNumber(context.parsed.y);
                                return context.dataset.label + ': ' + value;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        ticks: {
                            maxRotation: 0,
                            autoSkip: true,
                            maxTicksLimit: isMobile ? 6 : 12,
                            color: '#5f6368',
                            font: { size: isMobile ? 10 : 11 }
                        },
                        grid: { display: false }
                    },
                    y: {
                        beginAtZero: true,
                        ticks: {
                            precision: 0,
                            color: '#5f6368',
                            font: { size: isMobile ? 10 : 11 }
                        },
                        grid: { color: 'rgba(60,64,67,.12)' }
                    }
                }
            }
        });
    }

    async function loadKpis() {
        const scope = scopeSelect.value || 'me';
        const months = monthsSelect.value || '12';
        const url = contextPath + '/dashboard/kpi?scope=' + encodeURIComponent(scope) + '&months=' + encodeURIComponent(months);
        try {
            const response = await fetch(url, { headers: { Accept: 'application/json' } });
            if (!response.ok) throw new Error('Errore caricamento KPI');
            const payload = await response.json();
            const series = Array.isArray(payload.series) ? payload.series : [];
            updateCards(series);
            updateTable(series);
            updateChart(series);
        } catch (error) {
            updateCards([]);
            updateTable([]);
            if (trendChart) {
                trendChart.destroy();
                trendChart = null;
            }
            showNotice('Impossibile caricare i KPI in questo momento.', 'error');
        }
    }

    scopeSelect.addEventListener('change', loadKpis);
    monthsSelect.addEventListener('change', loadKpis);
    applyKpiHelp();
    initKpiTooltipInteractions();
    loadKpis();
});
</script>
</body>
</html>
