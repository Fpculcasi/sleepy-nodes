/**
	* \file
	*		Sleepy node methods interfaces
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

#define NUM_PROXIES		2
#define MAX_LINK_FORMAT_RESOURCES 10

/* sleepy-node error codes */
#define SN_OK		0
#define SN_ERROR	1
#define SN_EXPIRED	2

#define SN_BLOCKING_SEND(pkt_build_function, proxy_index, ...){ \
	coap_packet_t* send_pkt; \
	if(proxy_index >= NUM_PROXIES){ \
		PRINTF("proxy_index out of bound\n"); \
		sn_status = SN_ERROR; \
	} else { \
		send_pkt = pkt_build_function(proxy_index, ##__VA_ARGS__); \
		PRINTF("+++sent: %s?%s\n",send_pkt->uri_path,send_pkt->uri_query); \
		COAP_BLOCKING_REQUEST(&(sn_proxy_state[proxy_index].proxy_ip), \
			UIP_HTONS(REMOTE_PORT), send_pkt, receiver_callback); \
	} \
}
 
#define ADD_PROXY(proxy_index, a0, a1, a2, a3, a4, a5, a6, a7) {	\
	if(proxy_index >= NUM_PROXIES){ \
		PRINTF("proxy_index out of bound\n"); \
	} else { \
		uip_ip6addr(&sn_proxy_state[proxy_index].proxy_ip, a0, a1, a2, a3, a4, a5, a6, a7); \
	} \
}	

struct proxy_state_t {
	uip_ipaddr_t proxy_ip;
	char base_path[MAX_URI_LEN];
	char res_location[MAX_URI_LEN];
};
	
struct sn_state_t {
	coap_packet_t* response;
	char* ep_id;

	/*request variables*/
	char query[MAX_QUERY_LEN];
	char uri[MAX_URI_LEN];
	char payload[MAX_PAYLOAD_LEN];
};

struct link_format_resource_t {
	char* resource_path;
	char* rtt;
	char* iff;
};

struct link_format_t {
	struct link_format_resource_t resource[MAX_LINK_FORMAT_RESOURCES];
	uint8_t res_num;
};

/* Methods declaration */
struct link_format_t* parse_link_format(char* payload);
void get_proxy_base_path(coap_packet_t* pkt, uint8_t proxy_index);
void get_proxy_resource_location(coap_packet_t* pkt, uint8_t proxy_index);
void get_proxy_resource_value(coap_packet_t* pkt, char* remote_resource_path, uint8_t proxy_index);

void set_ep_id();
void receiver_callback(void *response);

/* CoAP packets building functions */
coap_packet_t* proxy_discovery(uint8_t proxy_index);
coap_packet_t* proxy_registration(uint8_t proxy_index, struct proxy_resource_t* delegated_resource);
coap_packet_t* proxy_update_resource_value(uint8_t proxy_index, struct proxy_resource_t* proxy_resource,
		uint32_t lifetime);
coap_packet_t* proxy_get(uint8_t proxy_index, char* proxy_resource_path);
coap_packet_t* proxy_check_updates(uint8_t proxy_index, char* local_path_prefix);

/* EXTERN VARIABLES DECLARATION*/
extern struct proxy_state_t sn_proxy_state[];
extern struct sn_state_t *sn_state;
extern int sn_status;	//for storing sleepy-node error codes

#define PROXY_DISCOVERY(proxy_id){ \
	SN_BLOCKING_SEND(proxy_discovery, proxy_id); \
	if(sn_state->response->code == CONTENT_2_05) { \
		get_proxy_base_path(sn_state->response, proxy_id); \
		PRINTF("proxy disc bp: %s\n",sn_proxy_state[proxy_id].base_path); \
		sn_status = SN_OK; \
	} else { \
		sn_status = SN_ERROR; \
	} \
}

#define PROXY_RESOURCE_REGISTRATION(proxy_id, delegate_resource){ \
	SN_BLOCKING_SEND(proxy_registration, proxy_id, delegate_resource); \
	if(sn_state->response->code == CREATED_2_01) { \
		get_proxy_resource_location(sn_state->response, proxy_id); \
		PRINTF("proxy reg location: %s\n",sn_proxy_state[proxy_id].res_location); \
		sn_status = SN_OK; \
	} else { \
		sn_status = SN_ERROR; \
	} \
}

#define PROXY_GET(proxy_id, resource_path){ \
	SN_BLOCKING_SEND(proxy_get, proxy_id, resource_path); \
	get_proxy_resource_value(sn_state->response, resource_path, proxy_id); \
}

#define CHECK_GET_UPDATES(proxy_id, link_format_payload){ \
	static const uint8_t* __payload; \
	static struct link_format_t* __lf; \
	static int __i; \
	int __len; \
	/* a list of modified resources is returned */ \
	__len = coap_get_payload(link_format_payload, &__payload); \
	if(__len != 0){ \
		__lf = parse_link_format((char*)__payload); \
		PRINTF("Getting updates for %d resources\n",__lf->res_num); \
		for(__i=0; __i<__lf->res_num; __i++){ \
			/* send get requests for each resource in the payload */ \
			PROXY_GET(proxy_id, __lf->resource[__i].resource_path); \
		} \
	} \
}

/* initializes or update resource */
#define PROXY_RESOURCE_PUT(proxy_id, delegated_resource, lifetime){ \
	sn_status = SN_OK; \
	SN_BLOCKING_SEND(proxy_update_resource_value, proxy_id, delegated_resource, lifetime); \
	if(sn_state->response->code == CONTENT_2_05 || sn_state->response->code == CHANGED_2_04){ \
		/* check for updates from my delegated resources, and get their updated value */ \
		CHECK_GET_UPDATES(proxy_id, sn_state->response); \
	} else if (sn_state->response->code == NOT_FOUND_4_04){ \
		/* i must repeat the registration of this resource, since it expired */ \
		PROXY_RESOURCE_REGISTRATION(proxy_id, delegated_resource); \
		sn_status = SN_EXPIRED; \
	} else if (sn_state->response->code == CREATED_2_01){ \
		PRINTF("%s initialization ok\n", delegated_resource->resource->url); \
	} else { \
		sn_status = SN_ERROR; \
	} \
}

#define PROXY_ASK_UPDATES(proxy_id, local_path_prefix){ \
	sn_status = SN_OK; \
	SN_BLOCKING_SEND(proxy_check_updates, proxy_id, local_path_prefix); \
	if(sn_state->response->code == CHANGED_2_04){ \
		CHECK_GET_UPDATES(proxy_id, sn_state->response); \
	} else if(sn_state->response->code == VALID_2_03){ \
		PRINTF("Checking changes: no updates\n"); \
	} else { \
		sn_status = SN_ERROR; \
	} \
}
