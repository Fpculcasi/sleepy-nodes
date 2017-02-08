/******************************************************************************
 * @title: ActiveCoapResource
 * 	The base resource used in CoapTreeBuilder
 * 
 * @authors:
 * 	- Francesco Paolo Culcasi	<fpculcasi@gmail.com>
 * 	- Alessandro Martinelli		<a.martinelli1990@gmail.com>
 * 	- Nicola Messina			<nicola.messina93@gmail.com>
 * 
 * @for: Advanced topics in Network Architectures and Wireless Systems
 * 	UNIPI (2016/2017)
 *
 *****************************************************************************/

package org.eclipse.californium.proxy;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;

/**
 * ActiveCoapResource defines a resource that can be marked as active/inactive.
 * A resource is "active" if it has been delegated, in opposition to inactive
 * resources generated in order to delegated resources to be reachable from the
 * outside.
 * The resource is marked as dirty when it's accessed, i.e. after an update
 * operation.
 */
public class ActiveCoapResource extends CoapResource {
	private boolean active; // false if internal node
	private boolean dirty; 	// true if the resource has been updated since
							// either the last PUT request from sleepy node or
							// the last call to the modification check interface

	/** 
	 * Like the base constructor but the resource is assumed to be visible
	 * @param name resource name
	 * @param active flag used to set the activity/inactivity
	 */
	public ActiveCoapResource(String name, boolean active) {
		this(name, active, true);
	}
	
	/**
	 * Base constructor.
	 * @param name resource name
	 * @param active flag used to set the activity/inactivity
	 * @param visible flag used to set the visibility of the resource
	 */
	public ActiveCoapResource(String name, boolean active, boolean visible) {
		super(name, visible);
		this.active = active;
		dirty = false;
	}

	/**
	 * Returns the "active" flag
	 */
	public boolean isActive() {
		return active;
	}
	
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/**
	 * Returns the "dirty" flag
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * handleRequest() method has been overridden in order to handle the
	 * presence of inactive resources. Those resources are used internally
	 * in the resource tree with the aims of make the leaves of the tree
	 * reachable, but since they were not explicitly created, the are not
	 * reachable from the outside.
	 * @param exchange the exchange with the request
	 */
	@Override
	public void handleRequest(final Exchange exchange) {
		CoapExchange coapExchange = new CoapExchange(exchange, this);
		if (isActive()){
			Code code = coapExchange.getRequestCode();
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
