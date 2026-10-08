# Guida alle statistiche KPI

La pagina legacy `/dashboard/insights` legge `/dashboard/kpi`. La schermata
desktop **Dati e Statistiche** legge `GET /api/kpi?scope=me&months=12` dal
backend: mostra schede, grafico e dettaglio mensile, anche per l'ambito globale.
L'API espone gli snapshot salvati, le metriche derivate e `computedAt`. Il
backend calcola gli snapshot all'avvio e poi ogni 24 ore dalla prima esecuzione
alle 02:30 locali.

## Ambito e periodo

L'interfaccia permette di selezionare:

- `me`: dati del terapista autenticato;
- `global`: dati aggregati di tutti i terapisti;
- una finestra di 6, 12 o 24 mesi.

L'endpoint accetta il parametro `months` fino a un massimo di 36.

## Aggiornamento dei dati

I valori principali sono salvati nella tabella `kpi_monthly_snapshot`. All'avvio del backend e poi ogni 24 ore lo scheduler aggiorna il mese corrente e quello precedente, sia per l'ambito globale sia per ogni terapista attivo. La prima esecuzione programmata è alle 02:30 locali; il cambio d'ora può spostare le esecuzioni successive. La webapp legge gli snapshot, ma non li aggiorna più.

Alcuni indicatori gestionali vengono calcolati durante la lettura degli snapshot. I mesi più vecchi possono quindi essere assenti se non sono mai stati salvati nella tabella.

## Indicatori operativi

### Appuntamenti del mese

Appuntamenti non cancellati, associati a un paziente e con data di inizio nel mese.

### Trattamenti completati

Appuntamenti in stato `COMPLETED` con data di fine nel mese.

### Appuntamenti cancellati

Appuntamenti in stato `CANCELLED` la cui data di cancellazione ricade nel mese.

### Ore prenotate

Somma della durata degli appuntamenti non cancellati e associati a un paziente, con data di inizio nel mese:

```text
ore prenotate = totalBookedMinutes / 60
```

### Tasso di cancellazione

Rapporto mostrato dall'interfaccia tra cancellazioni e appuntamenti del mese:

```text
tasso di cancellazione = appuntamenti cancellati / appuntamenti del mese * 100
```

Se il denominatore è zero, il valore mostrato è `0%`.

## Indicatori gestionali

### Nuovi appuntamenti creati

Appuntamenti il cui campo `created_at` ricade nel mese, indipendentemente dalla data fissata per la prestazione.

### Nuovi pazienti

Pazienti il cui primo appuntamento non cancellato ricade nel mese selezionato. Nell'ambito `me` vengono considerate solo le prestazioni del terapista autenticato.

### Pazienti attivi

Numero distinto di pazienti con almeno un appuntamento non cancellato nel mese.

### Pazienti di ritorno

```text
pazienti di ritorno = pazienti attivi - nuovi pazienti
```

Il risultato non scende mai sotto zero.

### Saturazione agenda

```text
saturazione = minuti prenotati / minuti disponibili * 100
```

La capacità applicativa è fissata a 160 ore mensili per terapista. Nell'ambito globale viene moltiplicata per il numero di terapisti attivi.

### Media appuntamenti per paziente

```text
media = appuntamenti del mese / pazienti attivi
```

Se non ci sono pazienti attivi, il valore mostrato è `0,0`.

## Grafico e tabella

Il grafico mostra l'andamento mensile di:

- appuntamenti del mese;
- trattamenti completati;
- appuntamenti cancellati;
- nuovi appuntamenti creati;
- pazienti creati nel mese (`newPatientsMonth`).

La tabella riporta, per ciascun mese disponibile, tutti gli indicatori mostrati nelle schede.

## Risoluzione dei problemi

- Se tutti i valori sono a zero, controlla i log di avvio dello scheduler e la connessione al database.
- Se mancano alcuni mesi, verifica la presenza dei relativi record in `kpi_monthly_snapshot`.
- Per un database aggiornato da una versione precedente, verifica che sia stata applicata la migrazione `2026-05-13_kpi_monthly_snapshot.sql`.
