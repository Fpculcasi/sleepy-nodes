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
import org.eclipse.californium.core.server.resources.Resource;

import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_LINK_FORMAT;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Resource Delegated by a sleepy node.
 */
public class DelegatedResource extends ActiveCoapResource {

	/* store the state for the delegated resource */
	private String value;

	/*
	 * head of the subtree of resources delegated from a certain same sleepy
	 * node
	 */
	private ContainerResource container;

	/* set by timer when timer expires, checked by handlePUT */
	private boolean expired;
	/* resource lifetime updated by PUT query attribute */
	private long lifetime;

	private Timer timer;
	private ExpiredTimerTask currentTimerTask;
	private Lock l = new ReentrantLock();

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
	 * @param name
	 *            the name of the resource to be created.
	 * @param isVisible
	 *            indicate whether the newly created resource will be visible or
	 *            not from clients.
	 * @param attributes
	 *            list of attributes of the resource
	 */
	public DelegatedResource(String name, boolean isVisible,
			SNResourceAttributes attributes, ContainerResource container) {
		super(name, true, isVisible);

		this.container = container;
		expired = false;

		for (String attr : attributes.getAttributeKeySet()) {
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}

	/**
	 * handleGET() returns the state of the resource stored in 'value' variable
	 * after checking the resource has been initialized.
	 */
	@Override
	public void handleGET(CoapExchange exchange) {
		if (!isVisible()) { // if not initialized
			exchange.respond(CoAP.ResponseCode.NOT_FOUND);
		} else {
			exchange.respond(CoAP.ResponseCode.CONTENT, value);
		}
	}

	/**
	 * handlePUT() modifies the the state of the delegated resource. The first
	 * PUT request by the owner sleepy node also initializes the resource by
	 * setting it visible and observable.
	 */
	@Override
	public void handlePUT(CoapExchange exchange) {

		/*
		 * all the update operations within the handlePut method have to be
		 * performed in mutual exclusion, in order to avoid critical races
		 * between endpoint updates and timer expiration
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
			// expired = false; (??)

		} else { // the resource is not expired yet

			// update resource value
			value = payload;

			System.out.println(
					"For resource '" + getName() + "' Owner node address is "
							+ container.getSPIpAddress().getHostAddress()
							+ ", while requesting node address is "
							+ exchange.getSourceAddress().getHostAddress());

			if (container.getSPIpAddress()
					.equals(exchange.getSourceAddress())) {
				// Update from the owner sleepy node

				// get the query attributes from the request
				String query = exchange.getRequestOptions().getUriQueryString();

				/* timer restart if lifetime is specified */
				if (timer != null) {
					// there is a running timer that has to be interrupted

					timer.cancel();
					timer.purge();
					currentTimerTask.interrupt();

					if (query != null && query.matches("^lt=.*$")) {
						/*
						 * if the query contains something AND query contains
						 * lifetime attribute
						 */

						// update lifetime value
						lifetime = Long.parseLong(query.split("=")[1]);
						System.out.println("Timer of '" + getName()
								+ "' restarted" + " with the new lifetime: "
								+ lifetime);

					} else {
						System.out.println("Timer of '" + getName()
								+ "' restarted"
								+ " with the previous lifetime: " + lifetime);
						/*
						 * else is not needed as the timer is restarted with
						 * previous lifetime
						 */
					}
					timer = new Timer();
					currentTimerTask = new ExpiredTimerTask();
					timer.schedule(currentTimerTask, lifetime * 1000);

				} else {
					// no timers running

					if (query != null && query.matches("^lt=.*$")) {
						/*
						 * if the qeury contains something AND query contains
						 * lifetime attribute start a timer
						 */
						lifetime = Long.parseLong(query.split("=")[1]);
						timer = new Timer();
						currentTimerTask = new ExpiredTimerTask();
						timer.schedule(currentTimerTask, lifetime * 1000);

						System.out.println("New Timer of '" + getName()
								+ "' started" + " with lifetime: " + lifetime);
					} else {
						System.out.println("No timer for '" + getName()
								+ "' specified" + " in the request");
					}
				}

				/*
				 * get the list of "dirty" resources (updated by an endpoint
				 * different from the delegating sleepy node and not notified
				 * yet to the owner)
				 */
				response = checkChanges(container);
				if (response != null) {
					// remove last comma
					response.substring(0, response.length() - 1);
				}

				System.out.println("Here is the list of changes made to '"
						+ getName() + "' by different endpoints: " + response);

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
			} else {
				/*
				 * If the PUT request comes from an end-point different from the
				 * owner of the resource
				 */

				if (!isVisible()) {
					code = CoAP.ResponseCode.NOT_FOUND;
				} else {
					setDirty(true); // set the resource as dirty
					code = CoAP.ResponseCode.CHANGED;
				}
				System.out.println("PUT Request on '" + getName()
						+ "' from a Regular Node with address "
						+ ", answered with " + code);
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
	 * This method is used by the delegating sleepy node to retrieve
	 * informations on resources changes. The behavior resemble the handlePUT()
	 * method with the only differences: - the request MUST be generated by the
	 * owner of the resource; - no changes on delegated resources occur
	 */
	@Override
	public void handlePOST(CoapExchange exchange) {
		String response = null;
		ResponseCode code;

		// if the asker is the delegating sleepy node
		if (container.getSPIpAddress().equals(exchange.getSourceAddress())) {

			/*
			 * get the list of "dirty" resources into the subtree starting from
			 * the resource "this"
			 */
			response = checkChanges(this);
			if (response.length() != 0) {
				// remove last comma
				response.substring(0, response.length() - 1);
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
	 * Build the String listing dirty resources.
	 * 
	 * @param root
	 *            Starting point for the resource scan
	 * @return String containing the list of "dirty" resources under the
	 *         resource passed as argument
	 */
	private String checkChanges(Resource root) {
		StringBuilder buffer = new StringBuilder();
		for (Resource child : root.getChildren()) {
			// all the resource of the subtree must be ActiveCoapResource
			ActiveCoapResource c = (ActiveCoapResource) child;

			if (c.isVisible() && c.isDirty()) {
				c.setDirty(false);
				buffer.append("<").append(child.getPath())
						.append(child.getName()).append(">").append(",");
			}
			buffer.append(checkChanges(child));
		}

		return (buffer.length() == 0) ? null : buffer.toString();
	}
}
