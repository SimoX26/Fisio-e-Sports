# Inventario funzionale e parità dei client

Riferimento: commit `a412e3d`, analisi statica del 28 settembre 2026. Documento collegato a [01-analisi-migrazione.md](01-analisi-migrazione.md).

Le funzionalità elencate sono ricavate da routing e sorgenti. La verifica visuale e operativa sarà eseguita sulla baseline di test. Per ogni voce, Desktop e Android dovranno registrare esito del collaudo e differenze intenzionali prima della rimozione della webapp.

## 1. Navigazione e schermate

| ID | Schermata / route attuale | Elementi e azioni da mantenere | Origine principale |
|---|---|---|---|
| UI-01 | Accesso `/login` | Username, password, errore credenziali, destinazione per ruolo, accesso persistente | `LoginPageServlet`, `login.jsp` |
| UI-02 | Richiesta accesso `/register` | Nome, cognome, email, username, password; invio richiesta e feedback | `RegisterPageServlet`, `register.jsp` |
| UI-03 | Uscita `/logout` | Conferma esplicita, revoca token corrente, chiusura sessione | `LogoutServlet`, `logoutConfirm.jsp` |
| UI-04 | Home terapista `/dashboard` | Saluto/data, agenda odierna, contatori, azioni rapide, pazienti giorno/mese, ore settimana, lista d'attesa, promemoria | `DashboardServlet`, `dashboard.jsp` |
| UI-05 | Calendario `/calendar` | Giorno/settimana/mese, navigazione data, nuovo appuntamento, dettaglio/modifica/cancellazione, completamento, promemoria, accesso al paziente e al cestino | `CalendarServlet`, `calendar.jsp`, `calendar.js` |
| UI-06 | Cestino `/calendar/trash` | Elenco cancellati, ordinamento paziente, ripristino, eliminazione definitiva, svuotamento e conferme | `CalendarTrashServlet`, `calendarTrash.jsp` |
| UI-07 | Rubrica `/address-book` | Ricerca, ordinamento nome/data, filtro pazienti del giorno/mese, nome/data creazione/telefono, dettagli, cronologia, eliminazione | `AddressBookServlet`, `addressBook.jsp` |
| UI-08 | Nuovo paziente `/address-book/create` e dialog home | Nome completo, email, telefono; salvataggio, annullamento e ritorno al contesto | `AddressBookCreatePageServlet`, `addressBookCreate.jsp`, dialog in `dashboard.jsp` |
| UI-09 | Scheda paziente, dialog rubrica | Dati anagrafici, anamnesi completa, condizioni, note; ultima scheda disponibile; unione contatti con conferma | `AddressBookServlet`, dialog in `addressBook.jsp` |
| UI-10 | Storico `/treatment-history` | Elenco del terapista o di un paziente; data, paziente, piano, dolore pre/post, esito, stato; vuoto/errori | `TreatmentHistoryServlet`, `treatmenthistory.jsp` |
| UI-11 | Statistiche `/dashboard/insights` | Ambito personale/globale, 6/12/24 mesi, schede, informazioni sulle formule, grafico e tabella | `DashboardKpiServlet`, `dashboardInsights.jsp` |
| UI-12 | Ricerca `/search`, `/admin/search` | Query, risultati per pazienti/appuntamenti/trattamenti, navigazione verso risultato ed evidenziazione calendario | `GlobalSearchServlet`, `searchResults.jsp` |
| UI-13 | Promemoria `/promemoria` e dialog condiviso | Data, appuntamento/destinatari, numero, anteprima, template, selezione e invio, esiti parziali | `ReminderServlet`, `CalendarServlet`, `promemoria.jsp`, `reminderModal.jsp`, `reminder-modal.js` |
| UI-14 | Impostazioni `/settings/*` | Stato WhatsApp, avvio/arresto, QR, aggiornamento stato e messaggi | `SettingsServlet`, `settings.jsp`, `settings.js` |
| UI-15 | Home ADMIN `/admin` | Conteggio richieste pendenti e navigazione amministrativa | `AdminDashboardServlet`, `adminDashboard.jsp` |
| UI-16 | Richieste ADMIN `/admin/access-requests` | Pendenti/recenti, dati richiedente, approvazione/rifiuto, stato ed esito operazione | `AdminAccessRequestsServlet`, `AdminReviewAccessRequestServlet`, `accessRequests.jsp` |

La pagina pubblica `index.jsp` è il punto di ingresso web: il desktop potrà aprire login o home secondo lo stato della sessione. Il menu condiviso contiene brand, ricerca, voci per ruolo, impostazioni e logout. Completano l'esperienza gli indicatori di caricamento, messaggi di successo/errore, conferme e gestione del ritorno dopo il salvataggio.

Fonti: [Servlet](../../fisio-web-legacy/src/main/java/it/SimoSW/controller/graphic), [JSP](../../fisio-web-legacy/src/main/webapp/WEB-INF/jsp), [JavaScript](../../fisio-web-legacy/src/main/webapp/assets/js).

## 2. Calendario e completamento

Il [calendario attuale](../../fisio-web-legacy/src/main/webapp/assets/js/calendar.js) apre normalmente la settimana, con lunedì come primo giorno; supporta anche giorno e mese. Mostra la fascia 08:00–21:00, scatti da 15 minuti e durata iniziale di 60 minuti. L'opzione FullCalendar `editable` è `false`: trascinamento e ridimensionamento degli eventi non sono funzioni operative da dare per già esistenti.

Elementi da riprodurre:

- Nuovo/modifica: paziente con suggerimenti, telefono nel flusso di creazione, data e orari, note, opzioni tutto il giorno ed evento non collegato a trattamento.
- Selezione da agenda, apertura tramite pulsante e apertura dalla home; navigazione alla data/elemento individuato dalla ricerca.
- Dettaglio: titolo, intervallo, note, scheda paziente, promemoria, modifica, cancellazione, completamento e messaggi relativi allo stato.
- Colori: eventi generici grigi; trattamenti completati nel passato verdi; altri eventi blu; risultati di ricerca evidenziati.
- Eventi tutto il giorno con estremi a mezzanotte e durata minima di un giorno; se associati a un paziente, il flusso richiede un paziente esistente.
- Controllo sovrapposizioni per terapista e messaggio di fascia occupata.

Il dialog di completamento contiene tutti questi campi:

| Gruppo | Campi |
|---|---|
| Piano | Titolo, obiettivi, sedute pianificate, frequenza settimanale, fine prevista |
| Seduta | Dolore prima/dopo 0–10, esito, esercizi domiciliari, note |
| Azioni | Conferma e annullamento; errori sul form |

La UI nasconde il completamento per eventi generici, tutto il giorno e già completati. Questa regola deve essere verificata anche dal servizio, indipendentemente dai pulsanti disponibili.

Il completamento oggi crea un nuovo piano e una seduta completata: con una seduta prevista il piano nasce completato, con più sedute nasce attivo. Non è presente nel flusso UI censito una scelta per collegare quel completamento a un piano già esistente. Una gestione completa di cicli di trattamento sarebbe un'estensione da valutare separatamente.

## 3. Scheda paziente: copertura dei campi

Fonte: [dialog di dettaglio paziente](../../fisio-web-legacy/src/main/webapp/WEB-INF/jsp/therapist/addressBook.jsp) e [parsing della Servlet](../../fisio-web-legacy/src/main/java/it/SimoSW/controller/graphic/AddressBookServlet.java).

| Sezione | Campi da conservare |
|---|---|
| Anagrafica | Nome completo, email, telefono; suggerimento contatto destinazione per unione |
| Valutazione | Data anamnesi, motivo del consulto, localizzazione e tipologia del dolore, sintomi associati |
| Esordio | Tipo e contesto |
| Caratteristiche del dolore | Invalidante, frequenza, progressione, risposta a movimento/riposo, intensità 0–10, notturno, al risveglio |
| Farmaci per il dolore | Uso ed efficacia |
| Accertamenti | Esami strumentali, visite specialistiche, trattamenti precedenti |
| Storia clinica | Patologie pregresse, farmaci regolari, interventi, traumi, dispositivi, disturbi masticazione, infezioni/infiammazioni, familiarità |
| Stile di vita | Altezza, peso, stile di vita, sport, fumo/alcol/sostanze, sonno 0–4, stress 0–4, alimentazione, note ciclo/andrologiche-ginecologiche |
| Condizioni strutturate | Patologie, sintomi, familiarità, allergie, farmaci, revisione sistemica, altre condizioni |
| Note | Note libere, memorizzate in `free_notes_json` |

Le note testuali sono attualmente racchiuse in JSON con proprietà `note`; un JSON già valido viene conservato. Va prevista compatibilità con i contenuti preesistenti senza esporre necessariamente JSON all'utente.

Ogni salvataggio con dati anamnestici inserisce una nuova anamnesi; il caricamento mostra la più recente. Non è un aggiornamento in-place della stessa riga. La parità deve includere questo comportamento e il mantenimento delle condizioni associate alla corretta versione.

La conferma di eliminazione cambia quando esistono appuntamenti collegati. L'unione contatti richiede conferma e sposta lo storico sull'ID destinazione. Le conseguenze sui dati sono descritte nell'analisi architetturale.

## 4. Dashboard, lista d'attesa e statistiche

La lista d'attesa è per terapista, contiene nome completo e telefono e non richiede un record paziente. Conservare aggiunta, elenco e rimozione, evitando di trasformarla implicitamente in creazione paziente.

La home calcola riepiloghi direttamente in Java nella Servlet; la pagina statistiche usa `/dashboard/kpi`. Devono essere estratti due casi d'uso distinti con definizioni coerenti, preservando le differenze intenzionali tra giornata, settimana e mese.

| Statistiche operative | Statistiche gestionali |
|---|---|
| Appuntamenti del mese | Nuovi appuntamenti creati |
| Trattamenti completati | Pazienti attivi |
| Appuntamenti cancellati | Nuovi pazienti al primo appuntamento |
| Ore prenotate | Pazienti di ritorno |
| Tasso di cancellazione | Saturazione agenda |
| | Media appuntamenti per paziente |

Il grafico usa anche `newPatientsMonth` (creazione dell'anagrafica), distinto dai nuovi pazienti al primo appuntamento. L'etichetta «trattamenti completati» della metrica operativa conta appuntamenti completati; esiste separatamente la metrica delle sedute completate. L'API supporta fino a 36 mesi, anche se il selettore UI offre 6/12/24.

Confrontare le nuove UI con fixture numeriche e con la [guida KPI](../../KPI_STATISTICHE_GUIDA.md), distinguendo dati live e snapshot. Non cambiare formule durante il semplice trasferimento della grafica; le correzioni identificate nell'analisi devono avere un test dedicato.

## 5. Promemoria e impostazioni

Conservare i segnaposto `{nome paziente}`, `{giorno}`, `{ora inizio}`, `{ora fine}` e `{ora inizio - ora fine}`, il modello per terapista, l'anteprima e gli esiti inviati/saltati/falliti.

Esistono due flussi: pagina dedicata per singolo appuntamento e dialog calendario/home con selezione di destinatari. Entrambi devono usare un servizio comune. La UI nasconde alcuni invii per eventi tutto il giorno, ma i filtri server dei promemoria non applicano uniformemente questa esclusione: la regola va chiarita e testata.

Impostazioni mostra stato, QR e avvio/arresto del gateway. Nel sistema attuale agisce sulla stessa sessione WhatsApp centrale, anche quando la pagina è aperta da dispositivi diversi. Nella futura UI «arresta servizio» non deve essere interpretato come arresto di un processo locale al telefono o al PC.

## 6. Funzioni nel codice che non equivalgono a schermate operative

| Elemento | Stato osservato | Trattamento nella migrazione |
|---|---|---|
| `/admin/new-user` | Redirige a richieste accesso | Conservare il flusso di approvazione; non riproporre automaticamente il vecchio form |
| `insertNewUser.jsp` e `UserController.createUser` | Pagina legacy con action `/admin/create-user`, route non trovata; nessun chiamante operativo di `createUser` individuato | Riattivazione solo come decisione di prodotto |
| Attivazione/disattivazione/archiviazione paziente | Azioni server presenti; pulsanti non emersi nella rubrica attuale | Documentare e decidere se esporle nei nuovi client |
| Pianificazione/avvio/completamento/cancellazione sedute come funzioni separate | Metodi nel `TreatmentController`, senza chiamanti nel percorso UI censito | Non includere implicitamente un nuovo gestionale dei piani nel requisito di parità |
| Dettagli JSON `calendar?details=true` | Ramo e metodo commentati | Non trattarlo come API disponibile |
| Storico chiamato “multi-sessione” | Nome/commenti suggeriscono un filtro, ma la query corrente seleziona sedute IN_PROGRESS/COMPLETED senza filtro sul numero previsto | Preservare il risultato della query, correggendo eventuali testi incoerenti in un intervento esplicito |
| Fotocamera/selettore file Android | Funzionalità generica della WebView | Non prova l'esistenza di un sistema allegati clinici; non sono emersi upload clinici attivi nelle schermate censite |

## 7. Contratti HTTP esistenti da mantenere durante la convivenza

| Endpoint | Uso attuale |
|---|---|
| GET `/calendar?events=true` | Eventi per intervallo |
| GET `/calendar?patients=true` | Suggerimenti pazienti |
| GET `/calendar?reminderPreview=true` | Anteprima destinatari e messaggi |
| POST `/calendar` | `create`, `reschedule`, `cancel`, `complete`, `send-reminders`, `save-reminder-template` |
| GET `/address-book?action=anamnesis-details` | Ultima anamnesi e condizioni |
| GET `/address-book?action=merge-candidates` | Possibili contatti destinazione |
| POST `/address-book` | Creazione, aggiornamento, eliminazione e cambi stato; unione e anamnesi nel ramo update |
| POST `/dashboard` | Aggiunta/rimozione lista d'attesa |
| POST `/calendar/trash` | Ripristino, eliminazione, svuotamento |
| GET `/dashboard/kpi` | Serie personale/globale e periodo |
| GET `/settings/baileys-status` | Stato JSON del gateway |
| GET `/settings/whatsapp-qr` | Pagina HTML del QR |
| POST `/settings/*` | Azioni avvio/arresto servizio |
| POST `/promemoria` | Invio singolo e salvataggio modello |
| POST `/admin/access-requests/review` | Approvazione/rifiuto |
| POST `/login`, `/register`, `/logout` | Accesso, richiesta, uscita |

Questi endpoint mescolano form, HTML, JSON e redirect; non sono ancora un'API uniforme per client nativi. Gli endpoint futuri potranno avere nomi diversi, ma dovranno coprire tutte le operazioni censite. Durante la convivenza gli endpoint legacy rimangono necessari al wrapper Android attuale.

## 8. Parità grafica e comportamento trasversale

Il [tema attuale](../../fisio-web-legacy/src/main/webapp/assets/css/style.css) fornisce riferimenti concreti: sfondo `#f5f7fa`, testo `#1f2d3d`, primario `#0d6efd`, superfici bianche, bordi arrotondati generalmente di 12 px e font con Segoe UI come prima scelta. Riutilizzare logo e palette, verificando il rendering alla risoluzione e al DPI effettivi.

Per ogni schermata verificare:

1. Campi, etichette, valori ammessi, valori iniziali e pulsanti presenti.
2. Stessi dati per lo stesso account e filtri; messaggi quando non esistono risultati.
3. Permessi applicati dal backend, anche invocando direttamente le API.
4. Caricamento, errore rete, sessione scaduta e conservazione dei valori del form in caso di errore.
5. Conferme per cancellazione/unione e prevenzione dei doppi invii.
6. Aggiornamento dopo salvataggio e rilevazione di modifiche effettuate da un altro dispositivo.
7. Desktop: ordine di tabulazione, tastiera, ridimensionamento e DPI; Android: touch, indietro e rotazione.
8. Testo, colori, gerarchia visuale e layout rispetto a schermate di riferimento acquisite su dati sintetici.

La scelta JavaFX puro o UI web locale va fatta prima di promettere equivalenza grafica esatta. Gli adattamenti mobili già esistenti sono parte dell'inventario da collaudare, non un motivo per rimuovere funzioni.

## 9. Criterio di completamento

Una funzione è migrata quando il servizio condiviso è coperto dalle verifiche pertinenti, la UI esegue il flusso sul DB di test, un secondo client vede il risultato, errori e permessi sono corretti e le differenze volute sono registrate.

La webapp è rimovibile quando tutte le voci UI-01–UI-16 sono coperte dai client previsti, le funzioni legacy da mantenere sono state decise, Android non carica più le JSP e KPI/WhatsApp continuano a funzionare senza aprire il desktop.
