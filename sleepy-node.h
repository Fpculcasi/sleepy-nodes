/**
	* \file
	*	Sleepy node core interface.
	*	Main functionalities for sleepy-node / proxy interaction.
	*	
	* \authors
	*	Francesco Paolo Culcasi	<fpculcasi@gmail.com> <br>
	*	Alessandro Martinelli	<a.martinelli1990@gmail.com> <br>
	*	Nicola Messina		<nicola.messina93@gmail.com> <br>
	*/

#include <stdio.h>
#include <string.h>

#include "contiki.h"
#include "contiki-net.h"

#include "er-coap.h"
#include "er-coap-engine.h"
#include "rest-engine.h"

#include "sleepy-node-resources.h"
#include "sn-utils.h"

#define LOCAL_PORT		COAP_DEFAULT_PORT + 1
#define REMOTE_PORT		COAP_DEFAULT_PORT
#define MAX_PAYLOAD_LEN		192
#define MAX_QUERY_LEN		64
#define MAX_URI_LEN		64

#define NUM_PROXIES		2
#define MAX_LINK_FORMAT_RESOURCES 10

/* sleepy-node error codes */
/** Sleepy-node status code for "transaction completed without errors".
 */
#define SN_OK		0

/** Sleepy-node status code for "transaction completed with errors"
 * (e.g.: connection failure, server errors...).
*/
#define SN_ERROR	1

/** Sleepy-node status code for "delegated resource expired";
 * usually returned by a PUT operation.
 */
#define SN_EXPIRED	2

#define SN_BLOCKING_SEND(pkt_build_function, proxy_index, ...){ \
	static coap_packet_t* send_pkt; \
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

/**
* Adds a proxy to the internal proxy state variable, so that
* this proxy can be used to store delegated resources.
*
* @param proxy_index:
*	the index through which this proxy is identified in
*	the sleepy node is must be a value between 0 and 
*	NUM_PROXIES
* @param a0..a7:
*	the ipv6 address of the proxy
*/
#define ADD_PROXY(proxy_index, a0, a1, a2, a3, a4, a5, a6, a7) {	\
	if(proxy_index >= NUM_PROXIES){ \
		PRINTF("proxy_index out of bound\n"); \
	} else { \
		uip_ip6addr(&sn_proxy_state[proxy_index].proxy_ip, a0, a1, a2, a3, a4, a5, a6, a7); \
	} \
}	

/**
 * Data structure mantaining proxy informations 
 */
struct proxy_state_t {
	/*@{*/
	uip_ipaddr_t proxy_ip;		/**< the ipv6 address of the proxy */
	char base_path[MAX_URI_LEN];	/**< the proxy base path URI (e.g.: /sp) */
	char res_location[MAX_URI_LEN]; /**< the proxy container URI for this sleepy-node (e.g.: /sp/0) */
	/*@}*/
};
	
/**
 * Data structure mantaining some state for this sleepy-node
 */
struct sn_state_t {
	/*@{*/
	coap_packet_t* response;	/**< the response CoAP packet from the proxy */
	char* ep_id;			/**< the endpoint identifier of this node */

	/*request variables*/
	char query[MAX_QUERY_LEN];
	char uri[MAX_URI_LEN];
	char payload[MAX_PAYLOAD_LEN];
	/*@}*/
};

/**
 * Data structure for mantaining parsed link-format rows.
 * As of now, only the URI, the 'rt' and the 'if' attributes are supported
*/ 
struct link_format_resource_t {
	/*@{*/
	char* resource_path;	/**< the URI of the link-format resource */
	char* rtt;		/**< the value for 'rt' attribute */
	char* iff;		/**< the value for 'if' attribute */
	/*@}*/
};

/**
 * Data structure for mantaining parsed link format entire payloads,
 * made up of at a maximum of MAX_LINK_FORMAT_RESOURCES resources.
*/
struct link_format_t {
	/*@{*/
	struct link_format_resource_t resource[MAX_LINK_FORMAT_RESOURCES];	/**< an array of link-format resources */
	uint8_t res_num;							/**< how many parsed resources */
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
coap_packet_t* proxy_registration(uint8_t proxy_index, struct sleepy_node_resource_t* delegated_resource);
coap_packet_t* proxy_update_resource_value(uint8_t proxy_index, struct sleepy_node_resource_t* proxy_resource,
		int lifetime);
coap_packet_t* proxy_get(uint8_t proxy_index, char* proxy_resource_path);
coap_packet_t* proxy_check_updates(uint8_t proxy_index, char* local_path_prefix, char* query);

/* EXTERN VARIABLES DECLARATION*/
extern struct proxy_state_t sn_proxy_state[];
extern struct sn_state_t *sn_state;
extern int sn_status;	//for storing sleepy-node error codes

/**
* Performs a proxy discovery operation. It blocks until
* the response is returned (draft 5.1).
*
* @param proxy_id:
*	the index of the proxy to discover
*/
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

/**
* Performs the registration of a sleepy-node resource to
* a given proxy. It blocks until the response is returned
* (draft 5.2).
*
* @param proxy_id:
*	the index of the proxy to discover
* @delegate_resource:
*	pointer to the resource to be registered
*/
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

/**
* Ask for the value for a certain resource and waits
* for the proxy response.
*/
#define PROXY_GET(proxy_id, resource_path){ \
	SN_BLOCKING_SEND(proxy_get, proxy_id, resource_path); \
	get_proxy_resource_value(sn_state->response, resource_path, proxy_id); \
}

/**
* Get the updated value for the resources listed in
* link-format content.
*/
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

/**
* Initializes or updates an already registered resource 
* on the proxy (draft 5.4).
*
* @param proxy_id:
*	the index of the proxy where the resource has been registered
* @param delegated_resource:
*	pointer to the resource to be initialized
*/
#define PROXY_RESOURCE_PUT(proxy_id, delegated_resource) \
	PROXY_RESOURCE_PUT_LT(proxy_id, delegated_resource, -1)

/**
* Initializes or updates an already registered resource on the proxy,
* specifying a lifetime for that resource (draft 5.4, 5.5).
*
* @param proxy_id:
*	the index of the proxy where the resource has been registered
* @param delegated_resource:
*	pointer to the resource to be initialized
* @param lifetime:
	positive integer value specifying the resource lifetime in seconds
*/
#define PROXY_RESOURCE_PUT_LT(proxy_id, delegated_resource, lifetime){ \
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

/**
* Explicitly asks the proxy for updates on delegated resources,
* specifying some filter option (draft 5.6).
*
* @param proxy_id:
*	the index of the proxy where the resources are registered
* @param local_path_prefix:
*	URI prefix for the resources to be retrieved;
*	an empty string means: all the delegated resources (the base
*	path is the URI of the proxy container for this sleepy-node
* @param query:
*	a user-defined URI-query to make the proxy filter out the response
*	a NULL query means: all the delegated resource (no filters)
*/
#define PROXY_ASK_UPDATES(proxy_id, local_path_prefix, query){ \
	sn_status = SN_OK; \
	SN_BLOCKING_SEND(proxy_check_updates, proxy_id, local_path_prefix, query); \
	if(sn_state->response->code == CHANGED_2_04){ \
		CHECK_GET_UPDATES(proxy_id, sn_state->response); \
	} else if(sn_state->response->code == VALID_2_03){ \
		PRINTF("Checking changes: no updates\n"); \
	} else { \
		sn_status = SN_ERROR; \
	} \
}
