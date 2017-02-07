#include "proxy-resources.h"

struct proxy_resource_t {
	resource_t* resource;
	char *value;
	int value_len;
};

struct proxy_resource_t p_resource[MAX_DELEGATED_RESOURCES];

void clear_proxy_resource_memory(){
	memset(p_resource, 0, sizeof(struct proxy_resource_t) * MAX_DELEGATED_RESOURCES);
}

int initialize_proxy_resource(resource_t* resource, char* value, int value_len){
	int i;
	//i find the first empty slot to insert the new resource
	for(i=0;i<MAX_DELEGATED_RESOURCES && p_resource[i].resource!=NULL;i++);
	if(i==MAX_DELEGATED_RESOURCES){
		return -1;
	}
	p_resource[i].resource = resource;
	p_resource[i].value = value;
	p_resource[i].value_len = value_len;
	return 1;
}

int update_proxy_resource_by_path(char* path, char* value, int value_len){
	int i;
	for(i=0; i<MAX_DELEGATED_RESOURCES; i++){
		//check only non-null slots
		if(p_resource[i].resource!=NULL && 
				strcmp(p_resource[i].resource->url, path)==0){
			//copy the received value inside the original buffer
			memcpy(p_resource[i].value, value, value_len);
			p_resource[i].value_len = value_len;
			return 1;
		}
	}
	return -1;
}
			



