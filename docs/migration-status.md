# Stato della migrazione

Aggiornato: 8 ottobre 2026. Questo registro descrive il codice del repository;
non attesta un deploy in produzione.

| Passo | Stato | Verifica o lavoro rimasto |
|---|---|---|
| Analisi e inventario | Completato | Documenti `01` e `02` in `architecture/` |
| Backend avviabile | Completato; script di deploy remoto del solo JAR preparato | Collaudare `fisio-backend.service` e `/ready` sul server con configurazione DB remota |
| Separazione Maven | Implementata, da collaudare in Tomcat | Build aggregata e contenuto WAR verificati |
| Associazione paziente-terapista | Migrazione applicata al DB di test configurato; codice pronto | Deploy WAR e prova manuale con due utenti |
| API autenticate | Identità, calendario con completamento, cestino, modello e invio promemoria, stato e QR WhatsApp; controllo manuale opzionale, gateway systemd remoto preparato; trattamenti, lista d'attesa, scheda paziente e lettura snapshot KPI personali | Collaudare gateway systemd e invio; migrare aggiornamento KPI; HTTPS prima del collegamento da altri PC |
| Client desktop | Si apre sul login anche senza backend; accesso automatico con archivio protetto; home con lista d'attesa trasformabile in appuntamento, tre contatori, azioni rapide, filtro Pazienti oggi e stati agenda; ricerca nella rubrica, calendario, promemoria, Impostazioni WhatsApp, storico trattamenti e scheda paziente. Barra di ricerca globale rimossa su richiesta | Verificare home, rubrica e isolamento fra terapisti; URL remoto e HTTPS restano da fare |
| Client Android autonomo | Da fare | Sostituzione della WebView |
| Dismissione webapp | Da fare | Solo dopo parità funzionale e collaudo |

La regola scelta per i pazienti è: ciascuna scheda appartiene a un terapista;
solo quel terapista può leggerla e modificarla. Eventuali schede condivise
vanno duplicate per terapista conservando i relativi riferimenti clinici. La
verifica in sola lettura sul database configurato ha trovato 137 schede: 125
associate a un solo terapista, nessuna a più terapisti e 12 senza collegamenti.
Le 12 schede senza collegamenti saranno assegnate a `marco`, identificato come
terapista attivo al momento della verifica. Questi numeri vanno ricontrollati
immediatamente prima della migrazione.

La migrazione SQL è stata provata su un MariaDB temporaneo con dati sintetici:
una scheda condivisa è stata duplicata, i riferimenti sono rimasti coerenti e
la scheda senza legami è stata assegnata a `marco`. Nel database di test
configurato, la prima esecuzione si è interrotta dopo l'aggiunta della colonna
perché il server MariaDB non riusciva a creare una procedura SQL (`mysql.proc`
non aggiornato). L'assegnazione è stata completata senza procedure: 137 schede
con proprietario, nessuna senza e nessuna discrepanza tra il proprietario delle
schede e quello di appuntamenti, anamnesi, piani o sedute. Lo script versionato
è stato sostituito con una versione senza procedure, riprovata sul MariaDB
temporaneo. Il WAR aggiornato non è ancora stato pubblicato in Tomcat.
