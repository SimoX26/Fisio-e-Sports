# Backup del database remoto

`backup-db.sh` esegue `mariadb-dump` (o `mysqldump`) sul server via SSH,
comprime il flusso sul computer locale e salva un file `.sql.gz` nella
directory `backups/`, esclusa da Git. Non crea una copia temporanea sul server.
Il dump include database, tabelle, routine, eventi e trigger.

## Esecuzione

Servono `ssh` e `gzip` in locale e un client dump sul server. Lo script usa
come default il server `31.70.74.92:22`, l'utente SSH `root` e il database
`fisio_e_sport`. Sul server esegue il dump con l'utente DB `root` tramite
socket locale, senza password DB. Per SSH usa una chiave, la password richiesta
dal client SSH oppure `DEPLOY_SSH_PASSWORD` con `sshpass`.

Avvia dalla radice del progetto:

```bash
./backup-db.sh
```

`./backup-db.sh --help` elenca le opzioni per host, porta, utenti, nome DB e
directory di output.
Lo script indica quando si connette via SSH, quando il dump è in corso e quando
inizia la verifica locale. Se SSH richiede una password, inserirla al prompt
SSH. La connessione iniziale scade dopo 10 secondi; se il dump è molto grande,
la fase di trasferimento può richiedere più tempo.

Se compare `Connection closed by ... port 22` oppure `Connection timed out
during banner exchange`, il dump non è ancora partito: controllare l'accesso
SSH con `ssh -v root@31.70.74.92 true`. Se manca anche il banner SSH,
verificare dalla console del server lo stato di `ssh`/`sshd` e i relativi log.

## Verifica e conservazione

Lo script pubblica il file finale solo quando il dump remoto termina senza
errori, la compressione è valida e il SQL contiene `CREATE DATABASE` e
`USE` per il database richiesto. In caso di errore elimina il file parziale.
Il file finale contiene dati clinici: conservarlo in un luogo protetto e non
aggiungerlo al repository.

Il dump include `DROP DATABASE` per consentire il ripristino su una nuova
istanza. Un'importazione su un database esistente può eliminarne i dati:
provare il ripristino su un'istanza separata prima di usarlo operativamente.

## Backup per la versione locale Windows

Se il backup precede l'associazione delle schede paziente ai terapisti,
prepara un nuovo archivio dalla radice del progetto:

```bash
python3 prepare-mysql-backup.py backups/NOME_BACKUP.sql.gz
```

Lo script lascia intatto l'originale e crea `NOME_BACKUP.mysql-ready.sql.gz`
nella stessa directory. Rimuove la direttiva iniziale specifica del client
MariaDB e accoda la migrazione versionata che assegna le schede senza
riferimenti al terapista attivo `marco`; per le altre schede usa i terapisti
dei riferimenti clinici. Se lo schema contiene già `patients.therapist_id`,
lo script si interrompe. Selezionare il nuovo archivio durante l'installazione
Windows. Il ripristino va fatto su una nuova istanza MySQL: il dump contiene
`DROP DATABASE`. Conservare anche il nuovo archivio come dato clinico protetto.
