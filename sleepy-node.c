/**
	* \file
	*	Sleepy node core.
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

#include "sleepy-node.h"

/* GLOBAL VARIABLES DECLARATION */
const char* well_known = ".well-known/core";
struct sn_state_t s;

struct proxy_state_t sn_proxy_state[NUM_PROXIES];
struct sn_state_t *sn_state = &s;
int sn_status = SN_OK;

/**
* Parses a link-format string, usually a payload of a CoAP packet whose
* content-type is link-format. 
*
* @param payload
*	string containing the link-format sequence to parse
* @return a struct that contains all the link-format parsed fields
*/
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
	sn_state->response = (coap_packet_t*)response;
	PRINTF("---ret: respcode %d\n", sn_state->response->code);
}

/**
* Retrieve the proxy response for the discovery phase
* and stores the resulting base path in the proxy internal state variable (draft 5.1).
* 
* @param pkt:
*	the response packet from the proxy
* @param proxy_index:
	the index of the proxy whose state has to be changed
*/
void get_proxy_base_path(coap_packet_t* pkt, uint8_t proxy_index){
	const uint8_t* payload;
	struct link_format_t* lf;
	if(proxy_index >= NUM_PROXIES){
		PRINTF("proxy_index out of bound\n");
		return;
	}
	coap_get_payload(pkt, &payload);
	//i am supposing the payload is a c-string
	lf = parse_link_format((char*)payload);
	strcpy(sn_proxy_state[proxy_index].base_path,lf->resource[0].resource_path);
}

/**
* Retrieve the proxy response for the registration phase
* and stores the resulting container path in the proxy internal state variable (draft 5.2).
*
* @param pkt:
*	the response packet from the proxy
* @param proxy_index:
	the index of the proxy whose state has to be changed
*/
void get_proxy_resource_location(coap_packet_t* pkt, uint8_t proxy_index){
	const char* tmp;
	int len;

	if(proxy_index >= NUM_PROXIES){
		PRINTF("proxy_index out of bound\n");
		return;
	}
	
	len = coap_get_header_location_path(pkt,&tmp);
	memcpy(&(sn_proxy_state[proxy_index].res_location[1]), tmp, len);

	//add a heading '/'
	sn_proxy_state[proxy_index].res_location[0] = '/';
	//turn the received location into a string
	sn_proxy_state[proxy_index].res_location[len+1] = '\0';
	
	//PRINTF("### %s", tmp);
}

/**
* Retrieve the resource value asked with a GET request to a specific
* delegated resource uri on the proxy. Stores the value in the buffer for that
* particular sleepy-node resource.
*
* @param pkt:
*	the response packet from the proxy
* @param remote_resource_path:
*	the proxy URI of the delegated resource to retrieve 
* @param proxy_index:
	the index of the proxy containing the resource to retrieve
*/
void get_proxy_resource_value(coap_packet_t* pkt, char* remote_resource_path, uint8_t proxy_index){
	const uint8_t* payload;
	int len = coap_get_payload(pkt, &payload);

	PRINTF("Value in GET response payload: %s\n", (char*)payload);

	/* Trim out the proxy container prefix in the uri.
	*  NOTE: + 1 is used to trim out '/' from the suffix of container prefix, since
	*  the resource uri has no '/' in front of it 
	*/
	char* local_resource_path = remote_resource_path + 
		strlen(sn_proxy_state[proxy_index].res_location) + 1;
	PRINTF("GET returned for resource %s (local is %s)\n",remote_resource_path,local_resource_path);

	struct sleepy_node_resource_t* res = search_sleepy_node_resource_by_path(local_resource_path);
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

coap_packet_t request[1];

/**
* Constructs the proxy request corresponding to a discovery operation
* (draft 5.1).
*
* @param proxy_index:
*	the index of the proxy to discover
* @return the CoAP packet to send to the proxy
*/
coap_packet_t* proxy_discovery(uint8_t proxy_index){
	//static coap_packet_t request[1];

	coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);
	coap_set_header_uri_path(request, well_known);
	coap_set_header_uri_query(request, "rt=core.sp");

	return request;
}

/**
* Constructs the proxy request corresponding to a registration operation
* for a given sleepy-proxy resource that has to be delegated
* (draft 5.2).
*
* @param proxy_index:
*	the index of the proxy where the resource has to be registered
* @param delegated_resource:
*	pointer to the sleepy-node resource that has to be registered
* @return the CoAP packet to send to the proxy
*/
coap_packet_t* proxy_registration(uint8_t proxy_index, struct sleepy_node_resource_t* delegated_resource){
	//static coap_packet_t request[1];
	struct link_format_t* lf;
	char* delegated_rt;

	//I get the rt field from the link-format string in the standard coap resource
	lf = parse_link_format((char*)delegated_resource->resource->attributes);
	delegated_rt = lf[0].resource->rtt;
	if(delegated_rt==NULL){
		sprintf(sn_state->query, "ep=%s",sn_state->ep_id);
	} else {
		sprintf(sn_state->query, "ep=%s&rt=%s",sn_state->ep_id,delegated_rt);
	}
	
	sprintf(sn_state->payload, "</%s>;%s",delegated_resource->resource->url,delegated_resource->resource->attributes);

	coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
	
	coap_set_header_uri_path(request, sn_proxy_state[proxy_index].base_path);
	coap_set_header_uri_query(request, sn_state->query);
	coap_set_payload(request, (uint8_t *)sn_state->payload, strlen(sn_state->payload)+1);

	return request;
}

/**
* Constructs a proxy request to initialize an already delegated resource
* (draft 5.4).
*
* @param proxy_index:
*	the index of the proxy where the resource has been registered
* @param proxy_resource:
*	pointer to the sleepy-node resource whose value must be updated
*	on the proxy
* @return the CoAP packet to send to the proxy
*/
coap_packet_t* proxy_update_resource_value(uint8_t proxy_index, struct sleepy_node_resource_t* proxy_resource,
		int lifetime){
	//static coap_packet_t request[1];
	
	sprintf(sn_state->uri, "%s/%s", sn_proxy_state[proxy_index].res_location, proxy_resource->resource->url);

	coap_init_message(request, COAP_TYPE_CON, COAP_PUT, 0);
	
	if(lifetime>=0){
		sprintf(sn_state->query, "lt=%d", lifetime);
		coap_set_header_uri_query(request, sn_state->query);
	}
	coap_set_header_uri_path(request, sn_state->uri);
	coap_set_payload(request, (uint8_t *)proxy_resource->value, proxy_resource->value_len);

	return request;
}

/**
* Constructs a proxy request to ask the proxy the value for
* a particular resource (draft 5.6).
*
* @param proxy_index:
*	the index of the proxy where the resource is located
* @param proxy_resource_path:
*	URI of the proxy resource whose value must be retrieved
* @return the CoAP packet to send to the proxy
*/
coap_packet_t* proxy_get(uint8_t proxy_index, char* proxy_resource_path){
	//static coap_packet_t request[1];

	strcpy(sn_state->uri,proxy_resource_path);

	coap_init_message(request, COAP_TYPE_CON, COAP_GET, 0);

	//sprintf(state->uri, "%s/%s", sn_proxy_state[proxy_index].res_location, delegated_resource->url);
	coap_set_header_uri_path(request, sn_state->uri);
	
	return request;
}

/**
* Constructs a proxy request to ask the proxy the updates for
* an already delegated resource (draft 5.6).
*
* @param proxy_index:
*	the index of the proxy where the resource has been registered
* @param local_path_prefix:
*	URI prefix for the resources to be retrieved
* @param query:
*	a user-defined URI-query to filter out the proxy response
* @return the CoAP packet to send to the proxy
*/

coap_packet_t* proxy_check_updates(uint8_t proxy_index, char* local_path_prefix, char* query){
	//static coap_packet_t request[1];

	sprintf(sn_state->uri,"%s/%s", sn_proxy_state[proxy_index].res_location, local_path_prefix);
	
	coap_init_message(request, COAP_TYPE_CON, COAP_POST, 0);
	if(query!=NULL){
		strcpy(sn_state->query, query);
		coap_set_header_uri_query(sn_state->query, sn_state->query);
	}

	coap_set_header_uri_path(request, sn_state->uri);

	return request;
}
	
/*********************************************************************************/

/**
* Initializes the ep (endpoint identifier) of the sleepy-node
* using the 64bit link layer address.
*/
void set_ep_id(){
	static char buf[17];
	sprintf(buf, "%02x%02x%02x%02x%02x%02x%02x%02x", 
		((uint8_t *)&uip_lladdr)[0], ((uint8_t *)&uip_lladdr)[1],
		((uint8_t *)&uip_lladdr)[2], ((uint8_t *)&uip_lladdr)[3],
		((uint8_t *)&uip_lladdr)[4], ((uint8_t *)&uip_lladdr)[5],
		((uint8_t *)&uip_lladdr)[6], ((uint8_t *)&uip_lladdr)[7]);
	sn_state->ep_id = buf;
	PRINTF("ep:%s\n",sn_state->ep_id);
}
