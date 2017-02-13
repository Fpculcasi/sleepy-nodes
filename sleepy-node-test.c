/**
	* \file
	*		Delegate resource test
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

#include "sleepy-node.h"

#define TOGGLE_INTERVAL 	15

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

	/*Send discovery*/
	PROXY_DISCOVERY(0);
	if(sn_status == SN_ERROR){
		PRINTF("Discovery error!\n");
		PROCESS_EXIT();
	}

	/*Send registration*/
	PROXY_RESOURCE_REGISTRATION(0, delegated_counter);
	if(sn_status == SN_ERROR){
		PRINTF("%s registration error!\n", delegated_counter->resource->url);
		PROCESS_EXIT();
	}

	PROXY_RESOURCE_PUT(0, delegated_counter, 10);
	if(sn_status == SN_ERROR){
		PRINTF("%s initialization error!\n",delegated_counter->resource->url);
		PROCESS_EXIT();
	}

	etimer_set(&et, TOGGLE_INTERVAL * CLOCK_SECOND);

	while(1) {
		PROCESS_YIELD();
		if(etimer_expired(&et)) {
			PRINTF("--WAKE UP!--\n");

			PRINTF("Checking for updates...\n");
			PROXY_ASK_UPDATES(0, "");
			if(sn_status == SN_ERROR){
				PRINTF("Error checking updates for this sleepy node\n");
			}
			
			//The resource is updated (its value is read from the sensor)
			counter++;
			sprintf(delegated_counter->value,"counter: %d",counter);
			delegated_counter->value_len = strlen(delegated_counter->value);

			PRINTF("Submitting the new sensor value\n");
			PROXY_RESOURCE_PUT(0, delegated_counter, 10);
			if(sn_status == SN_EXPIRED){
				//The programmer chooses to re-send the value
				PRINTF("resource %s expired; re-initializing\n", delegated_counter->resource->url);
				PROXY_RESOURCE_PUT(0, delegated_counter, 10);
			} else if(sn_status == SN_ERROR){
				PRINTF("%s resource update error\n", delegated_counter->resource->url);
			}

			etimer_reset(&et);		
		}
	}

	PROCESS_END();
}
