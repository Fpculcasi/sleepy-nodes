/******************************************************************************
 * @title: Delegated Resource
 * 	Every delegated resource is an object of this class
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

import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.server.resources.CoapExchange;

import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_LINK_FORMAT;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DelegatedResource represent a resource delegated by a sleepy node.
 * It offer methods<br>
 * - for retrieving the state (if any) of the resource 
 * it represent (<tt>handleGET()</tt>)<br>
 * - for initializing the delegated resource so that the state 
 * becomes meaningful (<tt>handlePUT()</tt>)<br>
 * - for updating the delegated resource value (<tt>handlePUT()</tt>), and for
 * retrieving the list of resources that has been delegated by the sleepy node
 * but have been modified by someone else (this make sense for example for
 * regular nodes writing resources representing configuration files).<br>
 * <p>
 * This last operation may be achieved with <tt>handlePUT()</tt> or 
 * <tt>handlePOST()</tt>.
 * The <tt>handlePUT()</tt> allow to updates a resources and contextually
 * retrieve the ENTIRE list of "dirty" resource, while the <tt>handlePOST()</tt>
 * does not perform contextual updates but its retrieving capabilities are
 * more powerful: the response may include only the "dirty" resources
 * having the URI of the requested delegated resource as prefix and
 * satisfying the given queries.
 * <p>
 * A sleepy node, at initialization of update time, may specify a lifetime
 * for a resource. If e.g. the lifetime is 3600, it means that the sleepy
 * node owner of the resource is supposed to send updates at least every
 * 3600 seconds in order for the resource at the proxy to be valid. If the
 * timeout expires, the <tt>CoapTreeBuilder.remove()</tt> method is called
 * on the expired resource. Concurrent update requests and lifetime 
 * expiration are supported, and handled in a thread-safe way.
 * 
 */
public class DelegatedResource extends ActiveCoapResource {

	/* stores the state for the delegated resource */
	private String value;

	/*
	 * h1ead of the subtree of resources delegated from the sleepy node
	 * who is also owner of this resource
	 */
	private ContainerResource container;

	/* set by timer when timer expires, checked by handlePUT */
	private boolean expired;
	
	/* resource lifetime updated by PUT query attribute */
	private long lifetime;

	private Timer timer;
	private ExpiredTimerTask currentTimerTask;
	
	/* Lock used in order to guarantee the safety of possible
	 * concurrent changes to the resource (lifetime, state etc)
	 */
	private Lock l = new ReentrantLock();
	
	/**
	 * Instantiates a new DelegatedResource with the given name, visibility,
	 * attributes and the given ContainerResource as container.
	 * 
	 * @param name
	 *            the name of the resource to be created.
	 * @param isVisible
	 *            indicate whether the newly created resource will be visible or
	 *            not from clients.
	 * @param attributes
	 *            list of attributes of the resource
	 * @param container
	 * 			  the ContainerResource containing all the resources 
	 * 			  delegated by the sleepy node which is also the owner
	 * 			  of this resource. 
	 */
	public DelegatedResource(String name, boolean isVisible,
			SNResourceAttributes attributes, ContainerResource container) {
		super(name, true, isVisible);

		this.container = container;
		
		// initially, the resource is not expired
		expired = false;
		
		// initially, the lifetime is not set
		lifetime = -1;

		// association of the given attributes to the DelegatedResource
		for (String attr : attributes.getAttributeKeySet()) {
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}	

	/**
	 * TimerTask that implement a interrupt mechanism. As soon as timer expires
	 * the delegated resource has to be de-registered unless a PUT request
	 * arrives and interrupt the task.
	 */
	private class ExpiredTimerTask extends TimerTask {
		private AtomicBoolean interrupt = new AtomicBoolean(false);

		public void interrupt() {
			interrupt.set(true);
		}

		@Override
		public void run() {
			l.lock();
			if (interrupt.get()) {
				/*
				 * The following sequence of events happened: the resource
				 * timer expired, thus the task started, but in the middle of 
				 * this process a put request arrived at the resource from
				 * his owner and set the interrupt flag of the ExpiredTimerTask.
				 * Thus, the ExpiredTimerTask has to be canceled.
				 */
				l.unlock();
				return;
			}
			expired = true;
			System.out.println("timer expired for resource "
					+ DelegatedResource.this.getName());
			container.getCoapTreeBuilder().remove(DelegatedResource.this);
			l.unlock();
		}
	}



	/**
	 * Returns the state of the resource stored in 'value' variable
	 * 
	 * @param exchange
	 * 				Structure maintaining informations about the request
	 */
	@Override
	public void handleGET(CoapExchange exchange) {
		if (!isVisible()) {
			/*
			 * The resource is active but it has not been initialized yet. Thus,
			 * depending of whether the request came from the owner of the
			 * resource or from another node, the answer will respectively be
			 * METHOD_NOT_ALLOWED or NOT_FOUND.
			 */
			if (container.getSPIpAddress()
					.equals(exchange.getSourceAddress())) {
				exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
			} else {
				exchange.respond(CoAP.ResponseCode.NOT_FOUND);
			}
		} else {
			/*
			 * The resource is active and visibile, this it is inizialized. Its
			 * state can be returned.
			 */
			exchange.respond(CoAP.ResponseCode.CONTENT, value);
		}
	}

	/**
	 * handlePUT() modifies the the state of the delegated resource if it has
	 * been initialized. The first PUT request issued the owner sleepy node 
	 * perform this initialization, setting it visible and observable.
	 * A PUT request executed by the owner of the resource with the purpose
	 * of updating the resource also has the effect of retrieving the list
	 * of "dirty" resources, i.e. the list of resource delegated by the
	 * sleepy node but modified by other nodes (e.g. configuration files).
	 * 
	 * @param exchange
	 * 				Structure maintaining informations about the request
	 */
	@Override
	public void handlePUT(CoapExchange exchange) {

		/*
		 * all the update operations within the handlePut method have to be
		 * performed in mutual exclusion, in order to avoid critical races
		 * between endpoint updates and timer expiration.
		 * A PUT executed by the owner of the resource 
		 */
		l.lock();

		String payload = exchange.getRequestText();
		String response = null;
		ResponseCode code;

		if (expired) {
			System.out.println("Resource '" + getName() + "' expired.");
			System.out.printf(
					"If you have seen this, you can play superenalotto");
			// resource is expired thus it has been removed from the tree

			code = CoAP.ResponseCode.NOT_FOUND;

		} else { // the resource is not expired yet

			// update resource value
			value = payload;

			if (container.getSPIpAddress()
					.equals(exchange.getSourceAddress())) {
				// Update from the owner sleepy node

				// get the query attributes from the request
				String query = exchange.getRequestOptions().getUriQueryString();
				if (query != null && query.matches("^lt=.*$")) {
					/*
					 * if the qeury contains something AND query contains
					 * lifetime attribute
					 */
					long lf = Long.parseLong(query.split("=")[1]);
					if (lf < 0) {
						code = CoAP.ResponseCode.BAD_REQUEST;
						exchange.respond(code);
						l.unlock();
						return;
					}
					lifetime = lf;
					System.out.println(
							getName() + ": New lifetime = " + lifetime);
				}

				/* timer restart if lifetime is specified */
				if (timer != null) {
					// there is a running timer that has to be interrupted
					timer.cancel();
					timer.purge();
					currentTimerTask.interrupt();
					System.out.println(
							getName() + ": Previous timer interrupted");

					timer = new Timer();
					currentTimerTask = new ExpiredTimerTask();
					timer.schedule(currentTimerTask, lifetime * 1000);
					System.out.println(getName() + ": Timer started ("
							+ lifetime + "s)");

				} else { // no timers running
					if (lifetime >= 0) { // new lifetime specified
						timer = new Timer();
						currentTimerTask = new ExpiredTimerTask();
						timer.schedule(currentTimerTask, lifetime * 1000);

						System.out.println(getName() + ": First timer started");
					}
				}

				/*
				 * get the list of "dirty" resources (updated by an endpoint
				 * different from the delegating sleepy node and not notified
				 * yet to the owner)
				 */
				response = Utilities.checkChanges(container, null);

				System.out.println("List of changes made to '" + getName()
						+ "' by different endpoints: " + response);

				if (!isVisible()) { // not initialized yet
					System.out.println("Resource '" + getName()
							+ "' has been initialized");
					// set as visible (reachable) and observable
					setObservable(true);
					setVisible(true);
					code = CoAP.ResponseCode.CREATED;

				} else {
					System.out.println(
							"Resource '" + getName() + "' has been modified");
					code = CoAP.ResponseCode.CHANGED;
				}
			} else {/*
					 * If the PUT request comes from an end-point different from
					 * the owner of the resource timers is not affected
					 */
				if (!isVisible()) {
					code = CoAP.ResponseCode.NOT_FOUND;
					value = "";
				} else {
					setDirty(true); // set the resource as dirty
					code = CoAP.ResponseCode.CHANGED;
				}
				System.out.println("PUT Request on '" + getName()
						+ "' from a Regular Node, answered with " + code);
			}

			// notify all the observing node the resource has been updated
			changed();
		}

		l.unlock();

		// build a response
		if (response == null) {
			exchange.respond(code);
		} else {
			exchange.respond(code, response, APPLICATION_LINK_FORMAT);
		}
	}

	/**
	 * handlePOST is used by the delegating sleepy node to retrieve the list of
	 * resources it has delegated which have been modified by someone else (like
	 * a regular node for configuring purpose). 
	 * The behavior resembles the <tt>handlePUT()</tt> method with some difference:<br>
	 * - the result include only the resources having as prefix the URI of
	 * this resource;<br>
	 * - the result use - if any - the queries specified in the request in
	 * order to filter the result;<br>
	 * - no changes to delegated resources occur.
	 * 
	 * @param exchange
	 * 			Structure maintaining informations about the request
	 */
	@Override
	public void handlePOST(CoapExchange exchange) {
		if (!isVisible()) {
			/*
			 * The resource is active but it has not been initialized yet. Thus,
			 * depending of whether the request came from the owner of the
			 * resource or from another node, the answer will respectively be
			 * METHOD_NOT_ALLOWED or NOT_FOUND.
			 */
			if (container.getSPIpAddress()
					.equals(exchange.getSourceAddress())) {
				exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
			} else {
				exchange.respond(CoAP.ResponseCode.NOT_FOUND);
			}
		}

		String response = null;
		ResponseCode code;

		// if the asker is the delegating sleepy node
		if (container.getSPIpAddress().equals(exchange.getSourceAddress())) {

			/*
			 * get the list of "dirty" resources into the subtree which have as
			 * prefix the URI of "this" resource. In our implementation, this
			 * means we are considering the resources son of this resource.
			 */
			List<String> queries = exchange.getRequestOptions().getUriQuery();
			response = Utilities.checkChanges(this, queries);
			if (response != null) {
				code = ResponseCode.CHANGED;
			} else {
				code = ResponseCode.VALID;
			}

		} else {
			code = ResponseCode.METHOD_NOT_ALLOWED;
		}

		// build a response
		if (response == null) {
			exchange.respond(code);
		} else {
			exchange.respond(code, response, APPLICATION_LINK_FORMAT);
		}
	}

	/**
	 * Method not implemented, resource deletion is handled by means of
	 * lifetime. This method has been overridden with the only purpose of
	 * returning NOT_FOUND to a regular node issuing a delete request to a
	 * resource delegated but not initialized yet.
	 */
	@Override
	public void handleDELETE(CoapExchange exchange) {
		if (!isVisible()) {
			/*
			 * The resource is active but it has not been initialized yet. Thus,
			 * depending of whether the request came from the owner of the
			 * resource or from another node, the answer will respectively be
			 * METHOD_NOT_ALLOWED or NOT_FOUND.
			 */
			if (container.getSPIpAddress()
					.equals(exchange.getSourceAddress())) {
				exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
			} else {
				exchange.respond(CoAP.ResponseCode.NOT_FOUND);
			}
		} else {
			/*
			 * The resource is initialized. This method is not implemented since
			 * resource deletion is manager by means of lifetime
			 */
			exchange.respond(CoAP.ResponseCode.METHOD_NOT_ALLOWED);
		}
	}

}
