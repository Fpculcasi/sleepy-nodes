#include "rest-engine.h"
#include <string.h>

#define MAX_DELEGATED_RESOURCES	8

struct proxy_resource_t {
	struct proxy_resource_t* next; // for LIST
	resource_t* resource;
	char *value;
	int value_len;
};

struct proxy_resource_t* initialize_proxy_resource(resource_t* resource, char* value, int value_len);
struct proxy_resource_t* search_proxy_resource_by_path(char* path);

