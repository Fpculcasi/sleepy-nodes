/**
	* \file
	*		Sleepy node example
	* \authors
	*		Francesco Paolo Culcasi	<fpculcasi@gmail.com>
	*		Alessandro Martinelli	<a.martinelli1990@gmail.com>
	*		Nicola Messina			<>
	*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "contiki.h"
#include "contiki-net.h"

#include "er-coap-engine.h"
#include "rest-engine.h"

#define DEBUG 0
#if DEBUG
#include <stdio.h>
	#define PRINTF(...) printf(__VA_ARGS__)
	#define PRINT6ADDR(addr) PRINTF("[%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x]", ((uint8_t *)addr)[0], ((uint8_t *)addr)[1], ((uint8_t *)addr)[2], ((uint8_t *)addr)[3], ((uint8_t *)addr)[4], ((uint8_t *)addr)[5], ((uint8_t *)addr)[6], ((uint8_t *)addr)[7], ((uint8_t *)addr)[8], ((uint8_t *)addr)[9], ((uint8_t *)addr)[10], ((uint8_t *)addr)[11], ((uint8_t *)addr)[12], ((uint8_t *)addr)[13], ((uint8_t *)addr)[14], ((uint8_t *)addr)[15])
	#define PRINTLLADDR(lladdr) PRINTF("[%02x:%02x:%02x:%02x:%02x:%02x]", (lladdr)->addr[0], (lladdr)->addr[1], (lladdr)->addr[2], (lladdr)->addr[3], (lladdr)->addr[4], (lladdr)->addr[5])
#else
	#define PRINTF(...)
	#define PRINT6ADDR(addr)
	#define PRINTLLADDR(addr)
#endif

/* FIXME: This server address is hard-coded for Cooja and link-local for unconnected border router. */
#define SERVER_NODE(ipaddr)	 uip_ip6addr(ipaddr, 0xfe80, 0, 0, 0, 0x0212, 0x7402, 0x0002, 0x0202)			/* cooja2 */
/* #define SERVER_NODE(ipaddr)	 uip_ip6addr(ipaddr, 0xbbbb, 0, 0, 0, 0, 0, 0, 0x1) */

#define LOCAL_PORT			UIP_HTONS(COAP_DEFAULT_PORT + 1)
#define REMOTE_PORT		 UIP_HTONS(COAP_DEFAULT_PORT)

#define TOGGLE_INTERVAL 10

PROCESS(sleepy_node, "Sleepy Node");
AUTOSTART_PROCESSES(&sleepy_node);

uip_ipaddr_t server_ipaddr;
static struct etimer et;


/* This function is will be passed to COAP_BLOCKING_REQUEST() to handle responses. */
void
client_chunk_handler(void *response){
	const uint8_t *chunk;
	int len = coap_get_payload(response, &chunk);
	printf("|%.*s", len, (char *)chunk);
}

PROCESS_THREAD(sleepy_node, ev, data)
{
	PROCESS_BEGIN();

	static coap_packet_t request[1];
	/* This way the packet can be treated as pointer as usual. */

	SERVER_NODE(&server_ipaddr);

	/* receives all CoAP messages */
	coap_init_engine();

	etimer_set(&et, TOGGLE_INTERVAL * CLOCK_SECOND);

	/* prepare request, TID is set by COAP_BLOCKING_REQUEST() */
	coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
	coap_set_header_uri_path(request, service_urls[1]);

	const char msg[] = "Toggle!";

	coap_set_payload(request, (uint8_t *)msg, sizeof(msg) - 1);

	PRINT6ADDR(&server_ipaddr);
	PRINTF(" : %u\n", UIP_HTONS(REMOTE_PORT));

	COAP_BLOCKING_REQUEST(&server_ipaddr, REMOTE_PORT, request,
														client_chunk_handler);
	printf("\n--Done--\n");
	
	while(1) {
		PROCESS_YIELD();

		if(etimer_expired(&et)) {
			printf("--Toggle timer--\n");

			// send a request to notify the end of the process 

			coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);
			coap_set_header_uri_path(request, service_urls[uri_switch]);

			printf("--Requesting %s--\n", service_urls[uri_switch]);

			PRINT6ADDR(&server_ipaddr);
			PRINTF(" : %u\n", UIP_HTONS(REMOTE_PORT));

			COAP_BLOCKING_REQUEST(&server_ipaddr, REMOTE_PORT, request,
														client_chunk_handler);

			printf("\n--Done--\n");

			etimer_reset(&et);

	}

	PROCESS_END();
}
