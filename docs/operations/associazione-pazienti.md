# Associazione dei pazienti ai terapisti

## Regola

Ogni scheda in `patients` ha un solo `therapist_id`. Il terapista autenticato
vede e modifica soltanto le proprie schede. L'ID del terapista arriva dalla
sessione server, mai dal form. Rubrica, ricerca globale, suggerimenti del
calendario, anamnesi e storico usano filtri sul proprietario. Unione ed
eliminazione verificano il proprietario di entrambe le schede coinvolte.

L'accesso amministrativo alla ricerca clinica globale non restituisce più
schede paziente. Le statistiche aggregate restano distinte dall'accesso ai
dati nominativi.

## Dati storici

La migrazione `2026-10-01_patient_therapist_ownership.sql` aggiunge la colonna,
ricava i terapisti dai riferimenti di appuntamenti, anamnesi, piani e sedute,
e duplica la scheda se i riferimenti indicano terapisti diversi. Ogni gruppo
di riferimenti viene poi collegato alla copia del proprio terapista; le
condizioni seguono l'anamnesi, il cui ID non cambia. Le schede senza riferimenti
vengono assegnate allo username configurato all'inizio dello script, attualmente
`marco`. Prima di eseguire lo script verificare che sia ancora la scelta voluta
e che l'account sia attivo.

Lo script interrompe la migrazione se trova sedute collegate a un piano o a
un appuntamento con terapista o paziente incoerente. `ALTER TABLE` in MySQL
produce commit impliciti: una migrazione interrotta può lasciare la nuova
colonna senza completare l'assegnazione. Conservare il backup e annotare il
punto d'interruzione prima di qualsiasi nuovo tentativo.

## Sequenza operativa

1. Fare un backup verificato del database e provare la procedura su una copia
   isolata. Non eseguire `db.sql` sul database esistente.
2. Fermare le scritture della webapp e degli altri client.
3. Ricontare schede senza riferimenti, schede con più terapisti e anomalie nei
   legami tra sedute, piani e appuntamenti. Confermare il terapista di fallback.
4. Applicare lo script SQL dalla stessa sessione `mysql`. Lo script non usa
   procedure memorizzate.
5. Verificare che ogni paziente abbia `therapist_id` e che appuntamenti,
   anamnesi, piani e sedute con `patient_id` abbiano lo stesso terapista della
   scheda. Verificare inoltre i conteggi prima e dopo, tenendo conto delle
   eventuali copie aggiunte.
6. Pubblicare il WAR con i filtri per proprietario e fare le prove con due
   account prima di riaprire le scritture.

La migrazione non viene applicata automaticamente dal codice né dalla build.
Nel database di test configurato è già stata completata il 1 ottobre 2026;
non va eseguita una seconda volta lì. Il WAR aggiornato va ancora pubblicato.
