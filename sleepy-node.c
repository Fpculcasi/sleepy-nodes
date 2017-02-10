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
#define MAX_PAYLOAD_LEN		192
#define MAX_QUERY_LEN		64
#define MAX_URI_LEN		64
#define MAX_ATTR_LEN		32

#define NUM_PROXIES		2
#define MAX_LINK_FORMAT_RESOURCES 10

#define ERROR_CODE		255
#define TOGGLE_INTERVAL 	15

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

struct link_format_resource_t {
	char* resource_path;
	char* rtt;
	char* iff;
};

struct link_format_t {
	struct link_format_resource_t resource[MAX_LINK_FORMAT_RESOURCES];
	uint8_t res_num;
};

struct link_format_t* parse_link_format(char* payload){
	//points to an entire row	
	char* row;
	//points to the components of a row (path + options)
	char* row_component;

	//utility pointers for strtok_r
	char* a;
	char* b;

	uint8_t res_num = 0;
	static struct link_format_t lf_container[1];
	static char lf_buf[MAX_PAYLOAD_LEN];

	strcpy(lf_buf, payload);
	memset(lf_container->resource, 0, sizeof(struct link_format_resource_t) * MAX_LINK_FORMAT_RESOURCES);

	row = strtok_r(lf_buf, ",",&a);
	while(row != NULL) {
		row_component = strtok_r(row, ";", &b);
		while(row_component != NULL){
			if(row_component[0] == '<'){
				//found the resource path, i must trim out the angular parentesis
				row_component[strlen(row_component)-1] = '\0';
				lf_container->resource[res_num].resource_path = row_component + 1;
			} else {
				//found an option, i must trim out the double quotes
				row_component[strlen(row_component)-1] = '\0';
				if(strncmp(row_component,"rt",2) == 0){
					lf_container->resource[res_num].rtt = row_component + 4;
				} else if (strncmp(row_component,"if",2) == 0){
					lf_container->resource[res_num].iff = row_component + 4;
				}
			}
			row_component = strtok_r(NULL, ";", &b);
		}

		row = strtok_r(NULL, ",",&a);
		res_num++;
	}
	lf_container->res_num = res_num;

	PRINTF("### link-format debug: carrying %d resources\n",res_num);

	return lf_container;
}

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
	memcpy(&(proxy_state[proxy_index].res_location[1]), tmp, len);

	//add a heading '/'
	proxy_state[proxy_index].res_location[0] = '/';
	//turn the received location into a string
	proxy_state[proxy_index].res_location[len+1] = '\0';
	
	//PRINTF("### %s", tmp);
}

/*Set the value of a resource after having received it within a coap_packet*/
void set_proxy_resource_value(coap_packet_t* pkt, char* remote_resource_path, uint8_t proxy_index){
	const uint8_t* payload;
	int len = coap_get_payload(pkt, &payload);

	PRINTF("Value in GET response payload: %s\n", (char*)payload);

	/* Trim out the proxy container prefix in the uri.
	*  NOTE: + 1 is used to trim out '/' from the suffix of container prefix, since
	*  the resource uri has no '/' in front of it 
	*/
	char* local_resource_path = remote_resource_path + 
		strlen(proxy_state[proxy_index].res_location) + 1;
	PRINTF("GET returned for resource %s (local is %s)\n",remote_resource_path,local_resource_path);

	struct proxy_resource_t* res = search_proxy_resource_by_path(local_resource_path);
	if(res == NULL){
		PRINTF("Something bad in updating resource after GET request\n");
	}
	//i use the original buffer to store the new value
	memcpy(res->value,(char*)payload,len);

	/* WARN: assuming the payload contains strings, that's non true in general
	*  The buffer is manually terminated with '\0'
	*/
	res->value[len] = '\0';

	res->value_len = len;
	PRINTF("So, the new memorized value for %s is %s (length: %d)\n",
						res->resource->url, res->value, res->value_len);
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
coap_packet_t* proxy_registration(uint8_t proxy_index, struct proxy_resource_t* delegated_resource){
	static coap_packet_t request[1];
	struct link_format_t* lf;
	char* delegated_rt;

	//I get the rt field from the link-format string in the standard coap resource
	lf = parse_link_format((char*)delegated_resource->resource->attributes);
	delegated_rt = lf[0].resource->rtt;
	if(delegated_rt==NULL){
		sprintf(state->query, "ep=%s",state->ep_id);
	} else {
		sprintf(state->query, "ep=%s&rt=%s",state->ep_id,delegated_rt);
	}
	
	sprintf(state->payload, "</%s>;%s",delegated_resource->resource->url,delegated_resource->resource->attributes);

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

#define PROXY_DISCOVERY(proxy_id){ \
	SN_BLOCKING_SEND(proxy_discovery, proxy_id); \
	if(state->response->code != CONTENT_2_05) { \
		PRINTF("Discov. error\n"); \
	} \
	set_proxy_base_path(state->response, proxy_id); \
	PRINTF("proxy disc bp: %s\n",proxy_state[proxy_id].base_path); \
}

#define PROXY_RESOURCE_REGISTRATION(proxy_id, delegate_resource){ \
	SN_BLOCKING_SEND(proxy_registration, proxy_id, delegate_resource); \
	if(state->response->code != CREATED_2_01) { \
		PRINTF("Registr. error\n"); \
	} \
	set_proxy_resource_location(state->response, proxy_id); \
	PRINTF("proxy reg location: %s\n",proxy_state[proxy_id].res_location); \
}

#define PROXY_GET(proxy_id, resource_path){ \
	SN_BLOCKING_SEND(proxy_get, proxy_id, resource_path); \
	set_proxy_resource_value(state->response, resource_path, proxy_id); \
}

/* initializes or update resource */
#define PROXY_RESOURCE_PUT(proxy_id, delegated_resource, lifetime, expired){ \
	static const uint8_t* __payload; \
	static struct link_format_t* __lf; \
	static int __i; \
	int __len; \
	*expired = 0; \
	SN_BLOCKING_SEND(proxy_update_resource_value, proxy_id, delegated_resource, lifetime); \
	if(state->response->code == CONTENT_2_05 || state->response->code == CHANGED_2_04){ \
		/* a list of modified resources is returned */ \
		__len = coap_get_payload(state->response, &__payload); \
		if(__len != 0){ \
			__lf = parse_link_format((char*)__payload); \
			for(__i=0; __i<__lf->res_num; __i++){ \
				/* send get requests for each resource in the payload */ \
				PROXY_GET(proxy_id, __lf->resource[__i].resource_path); \
			} \
		} \
	} else if (state->response->code == NOT_FOUND_4_04){ \
		/* i must repeat the registration of this resource, since it expired */ \
		PRINTF("resource %s expired; resending registration\n", delegated_resource->resource->url); \
		PROXY_RESOURCE_REGISTRATION(proxy_id, delegated_resource); \
		*expired = 1; \
	} else if (state->response->code == CREATED_2_01){ \
		PRINTF("%s initialization ok\n", delegated_resource->resource->url); \
	} else { \
		PRINTF("Something wrong with %s initialization\n", delegated_resource->resource->url); \
	} \
}

PROCESS(sleepy_node, "Sleepy Node");
AUTOSTART_PROCESSES(&sleepy_node);

/* TEST RESOURCE */
RESOURCE(res_counter,
         "title=\"Counter\";rt=\"utility\";ct=0",
         NULL,
         NULL,
         NULL,
         NULL);
char res_counter_value[MAX_PAYLOAD_LEN] = "default";
struct proxy_resource_t *delegated_counter;
static struct etimer et;

int counter = 0;
int expired;

PROCESS_THREAD(sleepy_node, ev, data)
{
	PROCESS_BEGIN();	
	set_global_address();
	coap_init_engine();

	/*initialize test resource*/
	rest_activate_resource(&res_counter, "counter");

	/*initialize proxies ip addresses and resources*/
	ADD_PROXY(0, 0xaaaa, 0, 0, 0, 0, 0, 0, 0x1);
	delegated_counter = initialize_proxy_resource(&res_counter, 
		res_counter_value, strlen(res_counter_value)+1);

	set_ep_id();
	PRINTF("ep:%s\n",state->ep_id);

	/*Send discovery*/
	PROXY_DISCOVERY(0);

	/*Send registration*/
	PROXY_RESOURCE_REGISTRATION(0, delegated_counter);

	PROXY_RESOURCE_PUT(0, delegated_counter, 10, &expired);

	etimer_set(&et, TOGGLE_INTERVAL * CLOCK_SECOND);

	while(1) {
		PROCESS_YIELD();
		if(etimer_expired(&et)) {
			PRINTF("--WAKE UP!--\n");
			
			/*TODO: ask for updates using POST*/
			
			//The resource is updated (its value is read from the sensor)
			counter++;
			sprintf(delegated_counter->value,"counter: %d",counter);
			delegated_counter->value_len = strlen(delegated_counter->value);

			PROXY_RESOURCE_PUT(0, delegated_counter, 10, &expired);
			if(expired){
				//The programmer chooses to re-send the value
				PROXY_RESOURCE_PUT(0, delegated_counter, 10, &expired);
			}

			etimer_reset(&et);		
		}
	}

	PROCESS_END();
}
