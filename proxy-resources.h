#include "rest-engine.h"
#include <string.h>

#define MAX_DELEGATED_RESOURCES	8

void clear_proxy_resource_memory();
int initialize_proxy_resource(resource_t* resource, char* value, int value_len);
int update_proxy_resource_by_path(char* path, char* value, int value_len);

