package org.eclipse.californium.proxy;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;

public class ActiveCoapResource extends CoapResource {
	private boolean active; // false if internal node
	private boolean dirty; // true if the resource has been updated since either the last PUT request from sleepy node or the last call to the modification check interface

	public ActiveCoapResource(String name, boolean active) {
		this(name, active, true);
	}
	
	public ActiveCoapResource(String name, boolean active, boolean visible) {
		super(name, visible);
		this.active = active;
		dirty = false;
	}

	public boolean isActive() {
		return active;
	}
	
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}
	
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * handleRequest() method has been overridden in order to handle the
	 * presence of phantom resources. Those resources are used internally
	 * in the resource tree with the aims of make the leaves of the tree
	 * reachable, but since they were not explicitly created, the are not
	 * reachable from the outside.
	 * @param exchange the exchange with the request
	 */
	@Override
	public void handleRequest(final Exchange exchange) {
		CoapExchange coapExchange = new CoapExchange(exchange, this);
		Code code = coapExchange.getRequestCode();
		if (isActive()){
			switch (code) {
				case GET:	 handleGET(coapExchange); 	 break;
				case POST:	 handlePOST(coapExchange);	 break;
				case PUT:	 handlePUT(coapExchange);	 break;
				case DELETE: handleDELETE(coapExchange); break;
			}
		} else {
			coapExchange.respond(CoAP.ResponseCode.NOT_FOUND);
		}
	}
}
