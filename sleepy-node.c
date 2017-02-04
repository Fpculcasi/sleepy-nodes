/**
	* \file
	*		Sleepy node example
	* \authors
	*		Francesco Paolo Culcasi	<fpculcasi@gmail.com>
	*		Alessandro Martinelli	<a.martinelli1990@gmail.com>
	*		Nicola Messina		<nicola.messina93@gmail.com>
	*/

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "net/ip/uip.h"
#include "net/ipv6/uip-ds6.h"
//#include "net/ip/uip-debug.h"

#include "contiki.h"
#include "contiki-net.h"

#include "er-coap.h"
#include "er-coap-engine.h"
#include "rest-engine.h"

#define DEBUG 1
#if DEBUG
	#define PRINTF(...) printf(__VA_ARGS__)
	#define PRINT6ADDR(addr) PRINTF("[%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x:%02x%02x]", ((uint8_t *)addr)[0], ((uint8_t *)addr)[1], ((uint8_t *)addr)[2], ((uint8_t *)addr)[3], ((uint8_t *)addr)[4], ((uint8_t *)addr)[5], ((uint8_t *)addr)[6], ((uint8_t *)addr)[7], ((uint8_t *)addr)[8], ((uint8_t *)addr)[9], ((uint8_t *)addr)[10], ((uint8_t *)addr)[11], ((uint8_t *)addr)[12], ((uint8_t *)addr)[13], ((uint8_t *)addr)[14], ((uint8_t *)addr)[15])
	#define PRINTLLADDR(lladdr) PRINTF("[%02x:%02x:%02x:%02x:%02x:%02x]", (lladdr)->addr[0], (lladdr)->addr[1], (lladdr)->addr[2], (lladdr)->addr[3], (lladdr)->addr[4], (lladdr)->addr[5])
#else
	#define PRINTF(...)
	#define PRINT6ADDR(addr)
	#define PRINTLLADDR(addr)
#endif	

#define LOCAL_PORT		COAP_DEFAULT_PORT + 1
#define REMOTE_PORT		COAP_DEFAULT_PORT
#define MAX_PAYLOAD_LEN		256
#define MAX_QUERY_LEN		128
#define MAX_URI_LEN		64
#define MAX_ATTR_LEN		32

#define NUM_PROXIES		2

#define ERROR_CODE		255
#define TOGGLE_INTERVAL 3

#define SN_BLOCKING_SEND(pkt_build_function, proxy_index, ...){ \
	coap_packet_t* send_pkt; \
	if(proxy_index >= NUM_PROXIES){ \
		PRINTF("proxy_index out of bound\n"); \
		state->response->code = ERROR_CODE; \
	} else { \
		send_pkt = pkt_build_function(proxy_index, ##__VA_ARGS__); \
		PRINTF("+++sent: %s?%s\n",send_pkt->uri_path,send_pkt->uri_query); \
		COAP_BLOCKING_REQUEST(&(proxy_state[proxy_index].proxy_ip), \
			UIP_HTONS(REMOTE_PORT), send_pkt, receiver_callback); \
	} \
}
 

PROCESS(sleepy_node, "Sleepy Node");
AUTOSTART_PROCESSES(&sleepy_node);

/* TEST RESOURCE */
RESOURCE(res_toggle_red,
         "title=\"Red LED\";rt=\"Control\";ct=0",
         NULL,
         NULL,
         NULL,
         NULL);

/* GLOBAL VARIABLES DECLARATION */
const char* well_known = ".well-known/core";
static struct etimer et;
struct proxy_state_t {
	uip_ipaddr_t proxy_ip;
	char base_path[MAX_URI_LEN];
	char res_location[MAX_URI_LEN];
} proxy_state[NUM_PROXIES];
	
struct sn_state_t {
	coap_packet_t* response;
	char* ep_id;
} s, *state = &s;

static void
set_global_address(void)
{
	uip_ipaddr_t ipaddr;
	int i;
	uint8_t state;

	uip_ip6addr(&ipaddr, 0xaaaa, 0, 0, 0, 0, 0, 0, 0);
	uip_ds6_set_addr_iid(&ipaddr, &uip_lladdr);
	uip_ds6_addr_add(&ipaddr, 0, ADDR_AUTOCONF);

	printf("IPv6 addresses: ");
	for(i = 0; i < UIP_DS6_ADDR_NB; i++) {
		state = uip_ds6_if.addr_list[i].state;
		if(uip_ds6_if.addr_list[i].isused &&
			(state == ADDR_TENTATIVE || state == ADDR_PREFERRED)) {
			uip_debug_ipaddr_print(&uip_ds6_if.addr_list[i].ipaddr);
			printf("\n");
		}
	}
}

/* This function is will be passed to COAP_BLOCKING_REQUEST() to handle responses. */
void receiver_callback(void *response){
	state->response = (coap_packet_t*)response;
	PRINTF("---ret: respcode %d\n", state->response->code);
}

/* Parses the response from the proxy */
void set_proxy_base_path(coap_packet_t* pkt, uint8_t proxy_index){
	const uint8_t* payload;
	char* token;
	const char delim1[2] = ";";
	if(proxy_index >= NUM_PROXIES){
		PRINTF("proxy_index out of bound\n");
		return;
	}
	coap_get_payload(pkt, &payload);
	token = strtok((char*)payload, delim1);
	while( token != NULL ) {
		if(token[0] == '<'){
			/* Here I found the base path of the proxy
			*  I trim the angular parenthesis
			*/
			token[strlen(token)-1] = '\0';
			token = token+1;
			strcpy(proxy_state[proxy_index].base_path,token);
		} else {
			//TODO: get the other parameters
			//proxy_state = NULL;
		}

		token = strtok(NULL, delim1);
	}
}

/* Set the location on the proxy where the delegated resource has been stored */
void set_proxy_resource_location(coap_packet_t* pkt, uint8_t proxy_index){
	const char* tmp;
	int len;

	if(proxy_index >= NUM_PROXIES){
		PRINTF("proxy_index out of bound\n");
		return;
	}
	
	len = coap_get_header_location_path(pkt,&tmp);
	memcpy(&(proxy_state[proxy_index].res_location[0]), tmp, len);

	//turn the received location into a string
	proxy_state[proxy_index].res_location[len] = '\0';
	
	//PRINTF("### %s", tmp);
}

/**********************************************************************************/
/* CoAP packet construction functions:
*  Those functions are used by SN_BLOCKING_SEND to construct the
*  CoAP packet to send to the proxy 
*/

coap_packet_t* proxy_discovery(uint8_t proxy_index){
	static coap_packet_t request[1];

	coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);
	coap_set_header_uri_path(request, well_known);
	coap_set_header_uri_query(request, "rt=core.sp");

	return request;
}

coap_packet_t* proxy_registration(uint8_t proxy_index, resource_t* delegated_resource, char* delegated_rt){
	static coap_packet_t request[1];
	static char query[MAX_QUERY_LEN];
	//static char uri[MAX_URI_LEN];
	static char payload[MAX_PAYLOAD_LEN];

	//sprintf(uri,"/%s",proxy_state->base_path);
	sprintf(query, "ep=%s&rt=%s",state->ep_id,delegated_rt);
	sprintf(payload, "</%s>;%s",delegated_resource->url,delegated_resource->attributes);

	coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
	
	coap_set_header_uri_path(request, proxy_state[proxy_index].base_path);
	coap_set_header_uri_query(request, query);
	coap_set_payload(request, (uint8_t *)payload, strlen(payload)+1);

	return request;
}
/*********************************************************************************/

/* Set this sleepy node ep field*/
void set_ep_id(){
	static char buf[17];
	sprintf(buf, "%02x%02x%02x%02x%02x%02x%02x%02x", 
		((uint8_t *)&uip_lladdr)[0], ((uint8_t *)&uip_lladdr)[1],
		((uint8_t *)&uip_lladdr)[2], ((uint8_t *)&uip_lladdr)[3],
		((uint8_t *)&uip_lladdr)[4], ((uint8_t *)&uip_lladdr)[5],
		((uint8_t *)&uip_lladdr)[6], ((uint8_t *)&uip_lladdr)[7]);
	state->ep_id = buf;
}
int p=0;

PROCESS_THREAD(sleepy_node, ev, data)
{
	PROCESS_BEGIN();	
	set_global_address();
	coap_init_engine();

	/*initialize proxies ip addresses*/
	uip_ip6addr(&proxy_state[0].proxy_ip, 0xaaaa, 0, 0, 0, 0, 0, 0, 0x1);
	uip_ip6addr(&proxy_state[1].proxy_ip, 0xaaaa, 0, 0, 0, 0, 0, 0, 0x1);

	/*initialize test resource*/
	rest_activate_resource(&res_toggle_red, "red_led");

	set_ep_id();
	PRINTF("ep:%s\n",state->ep_id);

	//etimer_set(&et, TOGGLE_INTERVAL * CLOCK_SECOND);

	/*for testing purposes, I register the same resource on all the proxies*/
	for(p=0;p<NUM_PROXIES;p++){
		/*Send discovery*/
		SN_BLOCKING_SEND(proxy_discovery, p);
		if(state->response->code != CONTENT_2_05 /*OK*/) {
			PRINTF("Discov. error\n");
			PROCESS_EXIT();
		}
		set_proxy_base_path(state->response, p);
		printf("proxy disc bp: %s\n",proxy_state[p].base_path);

		/*Send registration*/
		SN_BLOCKING_SEND(proxy_registration, p, &res_toggle_red, "sensors");
		if(state->response->code != CREATED_2_01 /*CREATED*/) {
			PRINTF("Registr. error\n");
			PROCESS_EXIT();
		}
		set_proxy_resource_location(state->response, p);
		printf("proxy reg location: %s\n",proxy_state[p].res_location);
	}


	/*while(1) {

		PROCESS_YIELD();
		if(etimer_expired(&et)) {
			//Query the proxy for notifications
			//Perform some operations
		}
	}*/

	PROCESS_END();
}
