/**
	* \file
	*	Delegate resource test. 
	*	This is the user main process for the sleepy-node. It delegates
	*	some resources that should cover most of the use-cases of
	*	the sleepy-node operation.
	* \authors
	*	Francesco Paolo Culcasi	<fpculcasi@gmail.com> <br>
	*	Alessandro Martinelli	<a.martinelli1990@gmail.com> <br>
	*	Nicola Messina		<nicola.messina93@gmail.com> <br>
	*/

#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "contiki.h"
#include "contiki-net.h"
#include "net/netstack.h"

#include "dev/button-sensor.h"
#include "dev/leds.h"

#include "er-coap.h"
#include "er-coap-engine.h"
#include "rest-engine.h"

#include "sleepy-node.h"

#define AWAKE_INTERVAL 	20
#define RES_BUFFER_SIZE 20

PROCESS(sleepy_node, "Sleepy Node");
AUTOSTART_PROCESSES(&sleepy_node);

/* TEST RESOURCES */
RESOURCE(res_counter,
         "title=\"Counter\";rt=\"utility\";ct=0",
         NULL,
         NULL,
         NULL,
         NULL);
RESOURCE(res_counter_incr,
	"title=\"CounterIncrement\";rt=\"utility\";ct=0",
         NULL,
         NULL,
         NULL,
         NULL);
RESOURCE(res_dev_name,
         "title=\"SNName\";rt=\"self\";ct=0",
         NULL,
         NULL,
         NULL,
         NULL);
RESOURCE(res_button,
         "title=\"Button\";rt=\"sensor\";ct=0",
         NULL,
         NULL,
         NULL,
         NULL);

char res_counter_value[RES_BUFFER_SIZE] = "default";
char res_counter_incr_value[RES_BUFFER_SIZE] = "1";
char res_button_value[RES_BUFFER_SIZE] = "OFF";
char res_name_value[RES_BUFFER_SIZE];

struct sleepy_node_resource_t *delegated_counter;
struct sleepy_node_resource_t *delegated_counter_increment;
struct sleepy_node_resource_t *delegated_name;
struct sleepy_node_resource_t *delegated_button;
static struct etimer et;

int counter = 0;
int button_on = 0;
struct proxy_state_t* proxy;

PROCESS_THREAD(sleepy_node, ev, data)
{
	PROCESS_BEGIN();	
	set_global_address();
	coap_init_engine();
	SENSORS_ACTIVATE(button_sensor);

	/*initialize test resources*/
	rest_activate_resource(&res_counter, "vsen/counter");
	rest_activate_resource(&res_counter_incr, "vsen/counter/incr");
	rest_activate_resource(&res_button, "vsen/button");
	rest_activate_resource(&res_dev_name, "dev/n");


	/*initialize proxies ip addresses and resources*/
	uip_ipaddr_t proxy_addr;
	uip_ip6addr(&proxy_addr, 0xaaaa, 0, 0, 0, 0, 0, 0, 0x1);
	proxy = add_proxy(&proxy_addr);

	delegated_counter = initialize_sleepy_node_resource(&res_counter, 
		res_counter_value, strlen(res_counter_value)+1);
	delegated_name = initialize_sleepy_node_resource(&res_dev_name, 
		res_name_value, 0);
	delegated_counter_increment = initialize_sleepy_node_resource(&res_counter_incr, 
		res_counter_incr_value, strlen(res_counter_incr_value)+1);
	delegated_button = initialize_sleepy_node_resource(&res_button, 
		res_button_value, strlen(res_button_value));
	
	/*sets the id of this endpoint using MAC layer address*/
	set_ep_id();

	/*Send discovery*/
	PROXY_DISCOVERY(proxy);
	if(sn_status == SN_ERROR){
		PRINTF("Discovery error!\n");
		PROCESS_EXIT();
	}

	/*counter registration*/
	PROXY_RESOURCE_REGISTRATION(proxy, delegated_counter);

	/*counter increment registration and initialization (no lifetime)*/
	PROXY_RESOURCE_REGISTRATION(proxy, delegated_counter_increment);
	PROXY_RESOURCE_PUT(proxy, delegated_counter_increment);

	/*device name registration and initialization (no lifetime)*/
	PROXY_RESOURCE_REGISTRATION(proxy, delegated_name);
	PROXY_RESOURCE_PUT(proxy, delegated_name);

	/*button device registration and initialization*/
	PROXY_RESOURCE_REGISTRATION(proxy, delegated_button);
	PROXY_RESOURCE_PUT_LT(proxy, delegated_button, 100);

	etimer_set(&et, AWAKE_INTERVAL * CLOCK_SECOND);

	while(1) {
		PROCESS_YIELD();
		if(etimer_expired(&et)) {
			PRINTF("--TIMER WAKE UP!--\n");
			//NETSTACK_MAC.on();

			PRINTF("Checking for updates...\n");
			PROXY_ASK_UPDATES(proxy, "", NULL);
			if(sn_status == SN_ERROR){
				PRINTF("Error checking updates for this sleepy node\n");
			}

			//the button value could be changed! It could be needed to update the internal state
			if(strcmp(delegated_button->value,"ON")==0){
				button_on = 1;
				leds_on(LEDS_GREEN);
			} else if (strcmp(delegated_button->value,"OFF")==0){
				button_on = 0;
				leds_off(LEDS_GREEN);
			}
			
			//the counter value is uploaded to the proxy
			counter += atoi(delegated_counter_increment->value);
			sprintf(delegated_counter->value,"counter: %d",counter);
			delegated_counter->value_len = strlen(delegated_counter->value);

			PRINTF("Submitting the new counter value\n");
			PROXY_RESOURCE_PUT_LT(proxy, delegated_counter, 50);
			if(sn_status == SN_EXPIRED){
				//we decided to re-send the counter value
				PRINTF("resource %s expired; re-initializing\n", delegated_counter->resource->url);
				PROXY_RESOURCE_PUT_LT(proxy, delegated_counter, 50);
			} else if(sn_status == SN_ERROR){
				PRINTF("%s resource update error\n", delegated_counter->resource->url);
			}

			//NETSTACK_MAC.off(0);
			etimer_reset(&et);		
		}
		
		//wait until the user presses the button
		if(ev==sensors_event &&	data==&button_sensor){
			PRINTF("--BUTTON WAKE UP!--\n");
			//NETSTACK_MAC.on();
			button_on = !button_on;
			if(button_on){
				leds_on(LEDS_GREEN);
				strcpy(delegated_button->value, "ON");
				delegated_button->value_len = strlen(delegated_button->value);
			} else {
				leds_off(LEDS_GREEN);
				strcpy(delegated_button->value, "OFF");
				delegated_button->value_len = strlen(delegated_button->value);
			}
			
			PROXY_RESOURCE_PUT(proxy, delegated_button);
			if(sn_status == SN_EXPIRED){
				//we decided to re-send the button value
				PRINTF("resource %s expired; re-initializing\n", delegated_button->resource->url);
				PROXY_RESOURCE_PUT(proxy, delegated_button);
			} else if(sn_status == SN_ERROR){
				PRINTF("%s resource update error\n", delegated_button->resource->url);
			}
			//NETSTACK_MAC.off(0);
		}
			
	}

	PROCESS_END();
}
