# sleepy-nodes
##ANAWS project, Fall 2016

**Sleepy node draft RFC**: <https://tools.ietf.org/html/draft-zotti-core-sleepy-nodes-04>
Nel testo sono presenti, tra parentesi quadre, i referimenti ai relativi paragrafi del draft RFC.

---

###ASSUNZIONI:
-	Tralasciamo l'implementazione del Resourse Directory (RD).
-	Tralasciamo i meccanismi di sicurezza (ad esempio accertarsi che una PUT fatta su un proxy provenga effettivamente da uno sleepy node e non sia stata fatta invece da un qualsiasi altro nodo, che magari vuole avvelenare il valore di quella risorsa su quel proxy).
-	Non avendo a disposizione il multicast in contiki, il discovery del proxy da parte degli sleepy node e dei regular node viene fatto in unicast usando l'indirizzo del proxy (stiamo quindi supponendo di conoscere l'indirizzo del proxy). [5.1]
-	Supponiamo, almeno inizialmente, di avere unsolo proxy nella rete;  il codice verrà comunque pensato per poter supportare la presenza di più proxy.
-	Il proxy è situato sulla stessa macchina che sta funzionando da gateway.
Cerchiamo di sviluppare il codice sottoforma di interfaccia, in modo tale che l'utente che voglia programmare un sensore non si debba preoccupare del protocollo che sta alla base della sincronizzazione tra proxy e sleepy node; per l'utente sarà sufficiente definire delle risorse e registrarle al proxy tramite un'apposita interfaccia (esempio: register_to_proxy(risorsa, proxy_ipv6)).

###SCENARIO:
-	Per gli sleepy node, l'interfaccia SYNCHRONIZE viene implementata come aggiunta al codice di Contiki.
-	Il proxy viene implementato tramite il framework Californium.
-	Il regular node viene simulato tramite interfaccia CoAP di firefox.

####Interfaccia SYNCHRONIZE
-	Predisponiamo uno sleepy node che crea un set di risorse e si registra con il proxy delegandogliele. NOTA: lo sleepy node può registrare risorse diverse (o anche la stessa) su proxy diversi. [5.2, 5.4]
-	Lo sleepy node entra in un ciclo che esegue le seguenti operazioni:
  -	chiede quali risorse da lui delegate sono state modificate sul proxy; [5.6]
  -	se la lista non è vuota, richiede al proxy l'invio dei valori aggiornati (per la maggior parte questi aggiornamenti saranno richieste di modifica di configurazione), e aggiorna conseguentemente il valore delle risorse locali; [5.6]
  -	legge i valori campionati dai suoi sensori ed aggiorna conseguentemente il valore delle relative risorse locali; [5.5]
  -	invia il valore delle risorse (esempio: temperatura) al proxy; [5.5]
  -	si addormenta per un certo lasso di tempo.
-	Il proxy deve istanziare un nuovo oggetto di una classe che estende CoapResource per ogni risorsa delegata. La classe avrà i metodi necessari a gestire le richieste, che potranno essere di update (PUT) o di lettura (GET), e a rispondere. Queste risorse sono tuttavia diverse da quelle vere, sono solo delle risorse "tampone" che servono per memorizzare gli aggiornamenti che avvengono mentre lo sleepy node è sleepy.

####Intefaccia DELEGATE
-	Il regular node è simulato tramite apposita interfaccia web per inviare/ricevere messaggi CoAP (plugin di firefox).
-	Il regular node esegue il discovery delle risorse delegate dagli sleepy node sui proxy. La richiesta è fatta in unicast verso un proxy noto. [6.1, con unicast invece che multicast]
-	Il proxy risponderà con un elenco di elementi del tipo <risorsa, sleepy node su cui la risorsa è situata>. [6.1]
-	Il regular node farà la richiesta al proxy per una specifica risorsa situata su un particolare endpoint. [6.1]
-	Se il regular node fa observe su  una risorsa delegata su un proxy, il proxy accetterà tale richiesta e, nel momento in cui lo sleepy node aggiornerà la sua risorsa sul proxy, questo notificherà tutti gli osservatori della modifica effettuata. [6.2]
