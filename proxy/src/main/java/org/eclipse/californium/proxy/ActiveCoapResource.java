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
 * ActiveCoapResource adds to CoapResource the concepts of activeness and
 * dirtiness. An active resource is a resource that, in the context of sleepy
 * nodes delegating resources to proxies, has been delegated to a proxy, instead
 * a resource is said to be inactive if it has not been delegated and it exists
 * for the only purpose of making a child resource reachable. Inactive resources
 * are not reachable from regular nodes and thus are also called "internal
 * resources".
 * 
 * The dirtiness concept is intended to be used for keeping trace if a resource
 * has been modified or not. For example, in the context of sleepy nodes
 * delegating resources to proxies, a delegated resource is marked as dirty when
 * it is modified by a node excepts its owner.
 */
public class ActiveCoapResource extends CoapResource {

	/*
	 * Indicates whether the resource is active or inactive (internal). An
	 * internal resource is not reachable from the outside and created with the
	 * only purpose of making a child resource reachable from the outside.
	 */
	private boolean active;

	/*
	 * Indicates whether a resource has been modified or not. In the context of
	 * sleepy nodes delegating resources to proxies, a delegated resource is
	 * marked as dirty when it is modified by a node excepts its owner.
	 */
	private boolean dirty;

	/**
	 * Constructs a new resource with the specified name and active/inactive
	 * state. The resource is assumed to be visible.
	 * 
	 * @param name
	 *            resource name
	 * @param active
	 *            flag used to set the active/inactive state
	 */
	public ActiveCoapResource(String name, boolean active) {
		this(name, active, true);
	}

	/**
	 * Constructs a new resource with the specified name, active/inactive state
	 * and visible/invisible state.
	 * 
	 * @param name
	 *            resource name
	 * @param active
	 *            flag used to set the active/inactive state
	 * @param visible
	 *            flag used to set the visibility of the resource
	 */
	public ActiveCoapResource(String name, boolean active, boolean visible) {
		super(name, visible);
		this.active = active;
		dirty = false;
	}

	/**
	 * Returns the value of the "active" flag
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * Set this resource with the specified dirtiness state
	 * 
	 * @param dirty
	 *            true if dirty
	 */
	public void setDirty(boolean dirty) {
		this.dirty = dirty;
	}

	/**
	 * Returns the values of the "dirty" flag
	 */
	public boolean isDirty() {
		return dirty;
	}

	/**
	 * handleRequest() method has been overridden in order to handle the
	 * presence of inactive resources. Those resources are used internally in
	 * the resource tree in order to make the initialized resources - that is,
	 * in general, the most external ones in the tree - reachable,
	 * but since their creation was not explicitly requested,
	 * the are not reachable from the outside.
	 * 
	 * @param exchange
	 *            an object storing useful information regarding the request,
	 *            accessible with user-friendly API
	 */
	@Override
	public void handleRequest(final Exchange exchange) {
		CoapExchange coapExchange = new CoapExchange(exchange, this);
		if (isActive()) {
			// If the resource is active, handlers are called.
			Code code = coapExchange.getRequestCode();
			switch (code) {
			case GET:
				handleGET(coapExchange);
				break;
			case POST:
				handlePOST(coapExchange);
				break;
			case PUT:
				handlePUT(coapExchange);
				break;
			case DELETE:
				handleDELETE(coapExchange);
				break;
			}
		} else {
			/* If the resource is inactive, it should not be reachable
			 * from the outside.
			 */
			coapExchange.respond(CoAP.ResponseCode.NOT_FOUND);
		}
	}
}
