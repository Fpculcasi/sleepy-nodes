/**
	* \file
	*		Sleepy node example
	* \authors
	*		Francesco Paolo Culcasi	<fpculcasi@gmail.com>
	*		Alessandro Martinelli	<a.martinelli1990@gmail.com>
	*		Nicola Messina		<nicola.messina93@gmail.com>
	*/

#include <stdio.h>
#include <string.h>

#include "contiki.h"
#include "contiki-net.h"

#include "er-coap.h"
#include "er-coap-engine.h"
#include "rest-engine.h"

#include "proxy-resources.h"
#include "sn-utils.h"

#define LOCAL_PORT		COAP_DEFAULT_PORT + 1
#define REMOTE_PORT		COAP_DEFAULT_PORT
#define MAX_PAYLOAD_LEN		256
#define MAX_QUERY_LEN		128
#define MAX_URI_LEN		64
#define MAX_ATTR_LEN		32

#define NUM_PROXIES		2

#define ERROR_CODE		255
#define TOGGLE_INTERVAL 5

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
 
#define ADD_PROXY(proxy_index, a0, a1, a2, a3, a4, a5, a6, a7) {	\
	if(proxy_index >= NUM_PROXIES){ \
		PRINTF("proxy_index out of bound\n"); \
	} else { \
		uip_ip6addr(&proxy_state[proxy_index].proxy_ip, a0, a1, a2, a3, a4, a5, a6, a7); \
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
char res_toggle_red_value[MAX_PAYLOAD_LEN] = "default";

/* GLOBAL VARIABLES DECLARATION */
const char* well_known = ".well-known/core";

struct proxy_state_t {
	uip_ipaddr_t proxy_ip;
	char base_path[MAX_URI_LEN];
	char res_location[MAX_URI_LEN];
} proxy_state[NUM_PROXIES];
	
struct sn_state_t {
	coap_packet_t* response;
	char* ep_id;

	/*request variables*/
	char query[MAX_QUERY_LEN];
	char uri[MAX_URI_LEN];
	char payload[MAX_PAYLOAD_LEN];
} s, *state = &s;

/* This function is will be passed to COAP_BLOCKING_REQUEST() to handle responses. */
void receiver_callback(void *response){
	state->response = (coap_packet_t*)response;
	PRINTF("---ret: respcode %d\n", state->response->code);
}

/* Parses the response from the proxy */
void set_proxy_base_path(coap_packet_t* pkt, uint8_t proxy_index){
	const uint8_t* payload;
	struct link_format_t* lf;
	if(proxy_index >= NUM_PROXIES){
		PRINTF("proxy_index out of bound\n");
		return;
	}
	coap_get_payload(pkt, &payload);
	//i am supposing the payload is a c-string
	lf = parse_link_format((char*)payload);
	strcpy(proxy_state[proxy_index].base_path,lf->resource[0].resource_path);
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

/*Set the value of a resource after having received it within a coap_packet*/
void set_proxy_resource_value(coap_packet_t* pkt, char* remote_resource_path, uint8_t proxy_index){
	const uint8_t* payload;
	int len = coap_get_payload(pkt, &payload);
	//trim out the proxy container prefix in the uri.
	char* local_resource_path = remote_resource_path + strlen(proxy_state[0].res_location);
	struct proxy_resource_t* res = search_proxy_resource_by_path(local_resource_path);

	//i use the original buffer to store the new value
	memcpy(res->value,(char*)payload,len);
	res->value_len = len;
	
	PRINTF("GET returned for resource %s (local is %s)\n",state->uri,local_resource_path);
	if(res == NULL){
		PRINTF("Something bad in updating resource after GET request\n");
	}
}
	
	
/**********************************************************************************/
/* CoAP packet construction functions:
*  Those functions are used by SN_BLOCKING_SEND to construct the
*  CoAP packet to send to the proxy 
*/

/*draft 5.1*/
coap_packet_t* proxy_discovery(uint8_t proxy_index){
	static coap_packet_t request[1];

	coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);
	coap_set_header_uri_path(request, well_known);
	coap_set_header_uri_query(request, "rt=core.sp");

	return request;
}

/*draft 5.2*/
coap_packet_t* proxy_registration(uint8_t proxy_index, resource_t* delegated_resource, char* delegated_rt){
	static coap_packet_t request[1];

	//sprintf(uri,"/%s",proxy_state->base_path);
	sprintf(state->query, "ep=%s&rt=%s",state->ep_id,delegated_rt);
	sprintf(state->payload, "</%s>;%s",delegated_resource->url,delegated_resource->attributes);

	coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
	
	coap_set_header_uri_path(request, proxy_state[proxy_index].base_path);
	coap_set_header_uri_query(request, state->query);
	coap_set_payload(request, (uint8_t *)state->payload, strlen(state->payload)+1);

	return request;
}

/*draft 5.4*/
coap_packet_t* proxy_update_resource_value(uint8_t proxy_index, struct proxy_resource_t* proxy_resource,
		uint32_t lifetime){
	static coap_packet_t request[1];
	
	sprintf(state->uri, "%s/%s", proxy_state[proxy_index].res_location, proxy_resource->resource->url);
	sprintf(state->query, "lt=%d", lifetime);

	coap_init_message(request, COAP_TYPE_CON, COAP_PUT, 0);
	
	coap_set_header_uri_path(request, state->uri);
	coap_set_header_uri_query(request, state->query);
	coap_set_payload(request, (uint8_t *)proxy_resource->value, proxy_resource->value_len);

	return request;
}

coap_packet_t* proxy_get(uint8_t proxy_index, char* proxy_resource_path){
	static coap_packet_t request[1];

	strcpy(state->uri,proxy_resource_path);

	coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);

	//sprintf(state->uri, "%s/%s", proxy_state[proxy_index].res_location, delegated_resource->url);
	coap_set_header_uri_path(request, state->uri);
	
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

struct proxy_resource_t *created_resource;
static struct etimer et;
const int p=0;

const uint8_t* payload;
struct link_format_t* lf;
int i;

PROCESS_THREAD(sleepy_node, ev, data)
{
	PROCESS_BEGIN();	
	set_global_address();
	coap_init_engine();

	/*initialize test resource*/
	rest_activate_resource(&res_toggle_red, "red_led");

	/*initialize proxies ip addresses and resources*/
	ADD_PROXY(0, 0xaaaa, 0, 0, 0, 0, 0, 0, 0x1);
	created_resource = initialize_proxy_resource(&res_toggle_red, 
		res_toggle_red_value, strlen(res_toggle_red_value)+1);

	set_ep_id();
	PRINTF("ep:%s\n",state->ep_id);

	/*Send discovery*/
	SN_BLOCKING_SEND(proxy_discovery, p);
	if(state->response->code != CONTENT_2_05 /*OK*/) {
		PRINTF("Discov. error\n");
		PROCESS_EXIT();
	}
	set_proxy_base_path(state->response, p);
	PRINTF("proxy disc bp: %s\n",proxy_state[p].base_path);

	/*Send registration*/
	SN_BLOCKING_SEND(proxy_registration, p, &res_toggle_red, "sensors");
	if(state->response->code != CREATED_2_01 /*CREATED*/) {
		PRINTF("Registr. error\n");
		PROCESS_EXIT();
	}
	set_proxy_resource_location(state->response, p);
	PRINTF("proxy reg location: %s\n",proxy_state[p].res_location);

	/*provo ad aggiungere un valore per la risorsa led sul proxy 0 (non ha molto senso per ora)*/
	SN_BLOCKING_SEND(proxy_update_resource_value, p, created_resource, 3600);
	if(state->response->code == CONTENT_2_05){
		//a list of modified resources is returned
		coap_get_payload(state->response, &payload);
		//i am supposing the payload is a c-string
		lf = parse_link_format((char*)payload);
		for(i=0; i<lf->res_num; i++){
			//send get requests for each resource in the payload 
			SN_BLOCKING_SEND(proxy_get, p, lf->resource[i].resource_path);
			set_proxy_resource_value(state->response, lf->resource[i].resource_path, p);
		}
	} else {
		PRINTF("Something wrong with resource initialization\n");
	}

	etimer_set(&et, TOGGLE_INTERVAL * CLOCK_SECOND);

	while(1) {
		PROCESS_YIELD();
		if(etimer_expired(&et)) {
			PRINTF("--Toggle timer--\n");
			SN_BLOCKING_SEND(proxy_update_resource_value, p, created_resource, 3600);
			if(state->response->code == CONTENT_2_05){
				//a list of modified resources is returned
				coap_get_payload(state->response, &payload);
				//i am supposing the payload is a c-string
				lf = parse_link_format((char*)payload);
				for(i=0; i<lf->res_num; i++){
					//send get requests for each resource in the payload 
					SN_BLOCKING_SEND(proxy_get, p, lf->resource[i].resource_path);
					set_proxy_resource_value(state->response, lf->resource[i].resource_path, p);
				}
			} else if (state->response->code == NOT_FOUND_4_04){
				//i must repeat the registration of this resource, since it expired
				PRINTF("resource %s expired; resending registration\n", created_resource->resource->url);

				/*Send registration*/
				SN_BLOCKING_SEND(proxy_registration, p, &res_toggle_red, "sensors");
				if(state->response->code != CREATED_2_01 /*CREATED*/) {
					PRINTF("Registr. error\n");
					PROCESS_EXIT();
				}
				set_proxy_resource_location(state->response, p);
				PRINTF("proxy reg location: %s\n",proxy_state[p].res_location);
			} else {
				PRINTF("Something wrong with resource initialization\n");
			}

			etimer_reset(&et);		
		}
	}

	PROCESS_END();
}
