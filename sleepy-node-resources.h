/**
	* \file
	*	Sleepy node resources interface.
	*	Wrapper for standard CoAP resources in order to add value buffers
	*	for the resources and a LIST functionality in order to organize all
	*	the sleepy-node resource in a list of delegated resources.
	*	
	* \authors
	*	Francesco Paolo Culcasi	<fpculcasi@gmail.com> <br>
	*	Alessandro Martinelli	<a.martinelli1990@gmail.com> <br>
	*	Nicola Messina		<nicola.messina93@gmail.com> <br>
	*/

#include "rest-engine.h"
#include <string.h>

#define MAX_DELEGATED_RESOURCES	8

/**
 * Data structure representing a sleepy-node resource
*/
struct sleepy_node_resource_t {
	/*@{*/
	struct sleepy_node_resource_t* next; 	/**< pointer to the next resource for LIST */
	resource_t* resource;		/**< pointer to the standard CoAP resource */
	char *value;			/**< pointer the the buffer that stores the resource value */
	int value_len;			/**< the length of the value field */
	/*@}*/
};

struct sleepy_node_resource_t* initialize_sleepy_node_resource(resource_t* resource, char* value, int value_len);
struct sleepy_node_resource_t* search_sleepy_node_resource_by_path(char* path);

