#include "sn-utils.h"
#include "net/ip/uip.h"
#include "net/ipv6/uip-ds6.h"

void set_global_address(void){
	uip_ipaddr_t ipaddr;
	int i;
	uint8_t state;

	uip_ip6addr(&ipaddr, 0xaaaa, 0, 0, 0, 0, 0, 0, 0);
	uip_ds6_set_addr_iid(&ipaddr, &uip_lladdr);
	uip_ds6_addr_add(&ipaddr, 0, ADDR_AUTOCONF);

	PRINTF("IPv6 addresses: ");
	for(i = 0; i < UIP_DS6_ADDR_NB; i++) {
		state = uip_ds6_if.addr_list[i].state;
		if(uip_ds6_if.addr_list[i].isused &&
			(state == ADDR_TENTATIVE || state == ADDR_PREFERRED)) {
			PRINT6ADDR(&uip_ds6_if.addr_list[i].ipaddr);
			PRINTF("\n");
		}
	}
}

struct link_format_t* parse_link_format(char* payload){
	//points to an entire row	
	char* row;
	//points to the components of a row (path + options)
	char* row_component;
	uint8_t res_num = 0;
	static struct link_format_t lf_container[1];

	memset(lf_container->resource, 0, sizeof(struct link_format_resource_t) * MAX_LINK_FORMAT_RESOURCES);

	row = strtok(payload, ",");
	while(row != NULL) {
		row_component = strtok(row, ";");
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
			row_component = strtok(NULL, ";");
		}

		row = strtok(NULL, ",");
		res_num++;
	}
	lf_container->res_num = res_num;

	PRINTF("### lf debug: %d resources\n",res_num);
	PRINTF("### payload was %s: 1st respath: %s\n", payload, lf_container->resource[0].resource_path);

	return lf_container;
}
