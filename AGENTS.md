# Regole di lavoro per Fisio e Sports

## Ambito e metodo

- Leggi il codice e la guida pertinente in `docs/README.md` prima di modificarli.
- Fai il più piccolo passo completo e verificabile. Non aggiungere refactor,
  rinominazioni, redesign, dipendenze o file estranei alla richiesta.
- Conserva il comportamento esistente, salvo modifica esplicitamente richiesta.
- Per scelte funzionali o sui dati con più esiti plausibili, fai una domanda
  mirata. Continua solo il lavoro indipendente dalla risposta.
- Non procedere a una fase successiva quando l'utente vuole prima provarla
  manualmente; indica in modo concreto che cosa deve testare.

## Uso efficiente di tempo e token

- Cerca prima i file rilevanti con `rg`; leggi solo le parti necessarie.
- Evita ricognizioni ripetute, output lunghi, diff o file completi non richiesti.
- Riusa le decisioni e le verifiche già documentate; non rifare controlli
  sufficienti senza una ragione concreta.
- Se una scelta richiede molta esplorazione o può allargare il lavoro, chiedi
  prima una breve precisazione. Raggruppa le domande correlate.
- Comunica aggiornamenti brevi; la risposta finale deve essere sintetica.

## Architettura e dati

- Rispetta le dipendenze tra `fisio-domain`, `fisio-application`,
  `fisio-persistence-mysql`, `fisio-web-legacy` e `fisio-backend`, descritte in
  `docs/architecture/03-struttura-componenti.md`.
- I client non accedono direttamente a MySQL. Le regole e i permessi stanno
  nei servizi condivisi; l'identità del terapista arriva dalla sessione o dal
  contesto autenticato, mai da un ID inviato dal client.
- Ogni paziente appartiene a un terapista: nessuna lettura o scrittura clinica
  deve aggirare quel vincolo. Verifica i riferimenti incrociati quando tocchi
  pazienti, appuntamenti, anamnesi o trattamenti.
- Non versionare credenziali, dump o configurazioni locali. Non usare `db.sql`
  su database con dati da conservare. Prima di cambiare lo schema, leggi la
  guida in `docs/operations/associazione-pazienti.md` e verifica lo stato reale.

## Verifica e documentazione

- Esegui i controlli pertinenti disponibili e dichiara solo quelli eseguiti.
- Aggiorna le guide versionate quando cambiano struttura, comportamento o
  procedure; mantieni il README generale e breve.
- Alla fine indica soltanto: 1) cosa è cambiato; 2) file modificati;
  3) verifiche eseguite; 4) limiti e prossimo controllo manuale, se serve.
