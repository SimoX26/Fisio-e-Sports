# Struttura dei componenti

## Moduli Java

| Modulo | Contenuto | Dipende da |
|---|---|---|
| `fisio-domain` | Entità, enum ed eccezioni | Solo JDK |
| `fisio-application` | Casi d'uso, contratti DAO e hashing password | `fisio-domain`, jBCrypt |
| `fisio-persistence-mysql` | DAO JDBC, pool e lettura configurazione | `fisio-application`, MySQL Connector/J, HikariCP |
| `fisio-web-legacy` | Servlet, JSP, bootstrap, KPI e integrazione WhatsApp | `fisio-persistence-mysql` e dipendenze web |
| `fisio-backend` | Processo HTTP autonomo, `/health` e `/ready` | JDK e MySQL Connector/J |

Il `pom.xml` alla radice è l'aggregatore Maven. La webapp produce ancora
`Fisio-e-Sports.war`; il nome del contesto Tomcat non cambia. Le classi
mantengono per ora i package Java originali per evitare rinominazioni estranee
alla separazione. Il servizio backend non usa ancora i casi d'uso dei moduli
condivisi: la composizione avverrà insieme alle prime API autenticate.

`KpiSnapshotController` rimane in `fisio-web-legacy` perché esegue ancora SQL
diretto tramite `ConnectionFactory`. Per spostarlo in `fisio-application` occorre
prima estrarre le sue query in un contratto di lettura. Il bootstrap e lo
scheduler sono ancora legati al ciclo di vita Tomcat. La webapp e il backend
autonomo non vanno avviati come due scheduler KPI indipendenti.

## Flusso attuale

```mermaid
flowchart LR
  Browser --> Web[Webapp legacy]
  Web --> Application[Servizi applicativi]
  Application --> Domain[Dominio]
  Web --> Persistence[DAO MySQL]
  Persistence --> Application
  Persistence --> DB[(MySQL)]
  Backend[Backend autonomo] --> DB
  Web --> Baileys[Gateway WhatsApp]
```

Android usa ancora la webapp tramite WebView. Il backend autonomo espone solo
controlli di salute e disponibilità del database; non gestisce sessioni o dati
clinici. Le future UI desktop e Android dovranno usare API autenticate, senza
connessione diretta a MySQL.
