all: sleepy-node-test

CONTIKI=/home/user/contiki
PROJECT_SOURCEFILES = sleepy-node.c sleepy-node-resources.c sn-utils.c

# linker optimizations
SMALL=1

# REST Engine shall use Erbium CoAP implementation
APPS += er-coap
APPS += rest-engine

# for some platforms
UIP_CONF_IPV6=1

# IPv6 make config disappeared completely
CFLAGS += -DUIP_CONF_IPV6=1
CFLAGS += -DPROJECT_CONF_H=\"project-conf.h\"
#disabling TCP on CoAP node
CFLAGS += -DUIP_CONF_TCP=0

include $(CONTIKI)/Makefile.include
