# Sleepy Nodes
## ANAWS project, Fall 2016

This file contains, in squared brackets, references to paragraphs of the following draft RFC:

**Sleepy CoAP Node** draft RFC: <https://tools.ietf.org/html/draft-zotti-core-sleepy-nodes-04>

---

### ASSUMPTIONS:
-	Resourse Directory (RD) implementation has been omitted.
-	Also security mechanisms are omitted (for istance the check on a PUT on a proxy coming from something different than a Sleepy node, trying to poisoning that resource).
-	Since as of now the multicast implementation in contiki is not reliable, Proxy discovery procedure is performed in unicast with proxy address (proxy address is known). [5.1]
-	We assume to have only one proxy in the network; even though the code is written to support the presence of multiple proxies.
-	The Proxy service is supposed to run on the same machine that is acting as gateway.

We are looking for an interfaces definition, so that the user willing to program a sleepy sensor has no worries about the protocol underlying the sinchronization between Proxy and Sleepy node; users only need to define resources and to registate to the Proxy with the specific interface (e.g. `register_to_proxy(resource, proxy_ipv6)`).

### SCENARIO:
-	For Sleepy nodes SYNCHRONIZE interface is implemented by means of some additions to the Contiki code.
-	Proxy is implemented using Californium framework.
-	The Regular node is simulated by CoAP interface of Firefox ([Copper](https://addons.mozilla.org/en-us/firefox/addon/copper-270430/?src=dp-dl-othersby) plug-in).

#### SYNCHRONIZE Interface
-	A Sleepy Node creates a resources set and registrates to the Proxy. N.B.: the Sleepy Node can registrate its own different resources (or just one) to several Proxies. [5.2, 5.4]
-	Sleepy Node cycles infinitely on a while loop in which:
     - it asks which of its delegated resource have been modified on Proxy; [5.6]
     - if the list is not empty, it asks to Proxy for the updated values (mostly configuration update requests), and it consequently updates the value on local resources; [5.6]
     - it reads sampled values from its own sensors and updates the content of the respective local resources; [5.5]
     - it sends resource values (e.g. temperature) to Proxy; [5.5]
     - fall asleep for a certain time.
-	Proxy has to instanciate a new object extending CoapResource for each delegated resource. This resource implements the needed methods for handling requests, for instance update (PUT) or read (GET), e to respond. These new resources are an artefacted copy of the true ones on Sleepy Nodes, used to store updates coming while a Sleepy Node is sleepy.

#### DELEGATE Interface
-	A Regular Node is implemented through a specific web interface able to send/receive CoAP messages (Copper).
-	A Regular Node performs discovery of Sleepy Node delegated resources on Proxy. the request is sent in unicast to the known Porxy. [6.1, unicast rather than multicast]
-	Proxies answer with a list of elements such <resource, origin sleepy node>. [6.1]
-	Regular Node requests the Proxy for a specific resource located on a praticular End Point (specifies ep attribute). [6.1]
-	If Regular Node want to "observe" a delegated resource on Proxy, Proxy accepts the request and, at the Sleepy Node update request, notifies all the observers of the occurred change. [6.2]
