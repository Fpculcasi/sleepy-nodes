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


#define SERVER_NODE(ipaddr)	uip_ip6addr(ipaddr, 0xaaaa, 0, 0, 0, 0, 0, 0, 0x1)

#define LOCAL_PORT		COAP_DEFAULT_PORT + 1
#define REMOTE_PORT		COAP_DEFAULT_PORT
#define MAX_PAYLOAD_LEN		256
#define MAX_QUERY_LEN		128
#define MAX_URI_LEN		64
#define MAX_ATTR_LEN		32


#define TOGGLE_INTERVAL 3

#define SN_BLOCKING_SEND(pkt_build_function, ipaddr, ...){ \
	coap_packet_t* send_pkt; \
	send_pkt = pkt_build_function(__VA_ARGS__); \
	PRINTF("+++sent: %s?%s\n",send_pkt->uri_path,send_pkt->uri_query); \
	COAP_BLOCKING_REQUEST(ipaddr, UIP_HTONS(REMOTE_PORT), send_pkt, receiver_callback); \
}
 

PROCESS(sleepy_node, "Sleepy Node");
AUTOSTART_PROCESSES(&sleepy_node);

/* TEST RESOURCE */
RESOURCE(res_toggle_red,
         "title=\"Red LED\";rt=\"Control\"",
         NULL,
         NULL,
         NULL,
         NULL);


const char* well_known = ".well-known/core";
uip_ipaddr_t server_ipaddr;
static struct etimer et;
struct proxy_infos_t {
	char base_path[MAX_URI_LEN];
	char res_location[MAX_URI_LEN];
} p, *proxy_infos = &p;
	
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

	/*printf("IPv6 addresses: ");
	for(i = 0; i < UIP_DS6_ADDR_NB; i++) {
		state = uip_ds6_if.addr_list[i].state;
		if(uip_ds6_if.addr_list[i].isused &&
			(state == ADDR_TENTATIVE || state == ADDR_PREFERRED)) {
			uip_debug_ipaddr_print(&uip_ds6_if.addr_list[i].ipaddr);
			printf("\n");
		}
	}*/
}

/* This function is will be passed to COAP_BLOCKING_REQUEST() to handle responses. */
void receiver_callback(void *response){
	state->response = (coap_packet_t*)response;
	PRINTF("---ret: respcode %d\n", state->response->code);
}

void set_proxy_base_path(coap_packet_t* pkt){
	const uint8_t* payload;
	char* token;
	const char delim1[2] = ";";
	coap_get_payload(pkt, &payload);
	token = strtok((char*)payload, delim1);
	while( token != NULL ) {
		if(token[0] == '<'){
			//here I found the 9base path of the proxy
			//I trim the angular parenthesis
			token[strlen(token)-1] = '\0';
			token = token+1;
			strcpy(proxy_infos->base_path,token);
		} else {
			//TODO: get the other parameters
			//proxy_infos = NULL;
		}

		token = strtok(NULL, delim1);
	}
}

void set_proxy_resource_location(coap_packet_t* pkt){
	coap_get_header_location_path(pkt,(const char**)&(proxy_infos->res_location));
}

coap_packet_t* proxy_discovery(){
	static coap_packet_t request[1];

	coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);
	coap_set_header_uri_path(request, well_known);
	coap_set_header_uri_query(request, "rt=core.sp");

	return request;
}

coap_packet_t* proxy_registration(resource_t* delegated_resource, char* delegated_rt){
	static coap_packet_t request[1];
	static char query[MAX_QUERY_LEN];
	static char uri[MAX_URI_LEN];
	static char payload[MAX_PAYLOAD_LEN];

	sprintf(uri,"%s%s",well_known,proxy_infos->base_path);
	sprintf(query, "ep=%s&rt=%s",state->ep_id,delegated_rt);
	sprintf(payload, "</%s>;%s",delegated_resource->url,delegated_resource->attributes);

	coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
	
	coap_set_header_uri_path(request, uri);
	coap_set_header_uri_query(request, query);
	coap_set_payload(request, (uint8_t *)payload, strlen(payload)+1);

	return request;
}

void set_ep_id(){
	static char buf[17];
	sprintf(buf, "%02x%02x%02x%02x%02x%02x%02x%02x", 
		((uint8_t *)&uip_lladdr)[0], ((uint8_t *)&uip_lladdr)[1],
		((uint8_t *)&uip_lladdr)[2], ((uint8_t *)&uip_lladdr)[3],
		((uint8_t *)&uip_lladdr)[4], ((uint8_t *)&uip_lladdr)[5],
		((uint8_t *)&uip_lladdr)[6], ((uint8_t *)&uip_lladdr)[7]);
	state->ep_id = buf;
}

PROCESS_THREAD(sleepy_node, ev, data)
{
	PROCESS_BEGIN();	
	SERVER_NODE(&server_ipaddr);
	set_global_address();
	coap_init_engine();

	/*initialize test resource*/
	rest_activate_resource(&res_toggle_red, "red_led");

	set_ep_id();
	PRINTF("ep:%s\n",state->ep_id);

	etimer_set(&et, TOGGLE_INTERVAL * CLOCK_SECOND);

	/*Send discovery*/
	SN_BLOCKING_SEND(proxy_discovery, &server_ipaddr);
	if(state->response->code != CONTENT_2_05 /*OK*/) {
		PRINTF("Discov. error\n");
		PROCESS_EXIT();
	}
	set_proxy_base_path(state->response);
	PRINTF("proxy disc bp: %s\n",proxy_infos->base_path);

	/*Send registration*/
	SN_BLOCKING_SEND(proxy_registration, &server_ipaddr, &res_toggle_red, "sensors");
	if(state->response->code != CREATED_2_01 /*CREATED*/) {
		PRINTF("Registr. error\n");
		PROCESS_EXIT();
	}
	set_proxy_resource_location(state->response);
	PRINTF("proxy reg location: %s\n",proxy_infos->res_location);

	/*while(1) {

		PROCESS_YIELD();
		if(etimer_expired(&et)) {
			//Query the proxy for notifications
			//Perform some operations
		}
	}*/

	PROCESS_END();
}
