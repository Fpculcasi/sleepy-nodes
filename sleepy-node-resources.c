/**
	* \file
	*	Sleepy node resources.
	*	Wrapper for standard CoAP resources in order to add value buffers
	*	for the resources and a LIST functionality in order to organize all
	*	the sleepy-node resource in a list of delegated resources.
	*	
	* \authors
	*	Francesco Paolo Culcasi	<fpculcasi@gmail.com> <br>
	*	Alessandro Martinelli	<a.martinelli1990@gmail.com> <br>
	*	Nicola Messina		<nicola.messina93@gmail.com> <br>
	*/

#include "sleepy-node-resources.h"

MEMB(resources_buf, struct sleepy_node_resource_t, MAX_DELEGATED_RESOURCES);
LIST(resources_list);

/*void clear_proxy_resource_memory(){
	memset(p_resource, 0, sizeof(struct sleepy_node_resource_t) * MAX_DELEGATED_RESOURCES);
}*/

/**
* Initializes a sleepy-node resource with a standard CoAP resource
* plus a buffer that contains the resource value.
*
* @param resource:
*	a standard CoAP resource
* @param value:
*	pointer to a user-defined buffer for storing the resource value
* @param value_len:
*	the length of the value
* @return a pointer to the new allocated resource
*/
struct sleepy_node_resource_t* initialize_sleepy_node_resource(resource_t* resource, char* value, int value_len){
	struct sleepy_node_resource_t* res = search_sleepy_node_resource_by_path((char*)resource->url);
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

/**
* Retrieves a sleepy-node resource through its URI
*
* @param path:
*	the URI of the resource to retrieve
* @return the pointer to the found resource, or NULL if
*	the resource is not found
*/
struct sleepy_node_resource_t* search_sleepy_node_resource_by_path(char* path){
	struct sleepy_node_resource_t* res = NULL;
	for(res = (struct sleepy_node_resource_t *)list_head(resources_list);
		res!=0 && strcmp(res->resource->url, path)!=0;
      		res = res->next);
	return res;
}
			



