# Prototipo desktop

Prima prova della UI HTML/JavaScript locale ospitata in JavaFX WebView. Il
calendario usa FullCalendar 6.1.11 e Bootstrap 5.3.2 inclusi nel modulo, con
una copia del CSS della webapp. Usa soltanto dati fittizi: non richiede backend
o database e non effettua chiamate alla rete.

Con JDK 21 e Maven, dalla radice del repository:

```bash
mvn -pl fisio-desktop javafx:run
```

Controlli manuali: aprire la finestra senza Internet, verificare gli eventi
fittizi nella settimana corrente, passare tra giorno, settimana e mese,
selezionare un evento, aprire «Crea» e aggiungere un evento di prova. Provare
ridimensionamento, tastiera e DPI. Confrontare con la pagina calendario della
webapp a parità di dimensioni della finestra.

Questa prova verifica solo la resa locale della prima schermata. Le altre
schermate e i flussi completi, il login, le API, i dati reali e il pacchetto
Windows sono passi successivi. Le voci di navigazione non ancora migrate sono
solo etichette; gli eventi aggiunti spariscono alla chiusura della finestra.

Le licenze degli asset incorporati sono in `src/main/resources/desktop/vendor/`.
