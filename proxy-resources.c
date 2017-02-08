#include "proxy-resources.h"

MEMB(resources_buf, struct proxy_resource_t, MAX_DELEGATED_RESOURCES);
LIST(resources_list);

/*void clear_proxy_resource_memory(){
	memset(p_resource, 0, sizeof(struct proxy_resource_t) * MAX_DELEGATED_RESOURCES);
}*/

struct proxy_resource_t* initialize_proxy_resource(resource_t* resource, char* value, int value_len){
	struct proxy_resource_t* res = search_proxy_resource_by_path((char*)resource->url);
	if(res!=NULL){
		return res;
	}
	//else, it is not present, must be added
	res = memb_alloc(&resources_buf);
	if(res == NULL){
		return NULL;
	}

	//i add it to the list
	res->resource = resource;
	res->value = value;
	res->value_len = value_len;
	list_add(resources_list,res);

	return res;
}

struct proxy_resource_t* search_proxy_resource_by_path(char* path){
	struct proxy_resource_t* res;
	for(res = (struct proxy_resource_t *)list_head(resources_list);
		res!=0 && strcmp(res->resource->url, path)!=0;
      		res = res->next);
	return res;
}
			



