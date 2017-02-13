/******************************************************************************
 * @title: Container Resource
 * 	The resource storing the delegated resources of a given endpoint
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

import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_LINK_FORMAT;

import java.net.InetAddress;
import java.util.List;
import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.server.resources.Resource;

/**
 * The ContainerResource implements CoAP's discovery service. It responds to GET
 * requests with a list of his child resources, i.e. links.
 */
public class ContainerResource extends ActiveCoapResource {

	private CoapTreeBuilder coapTreeBuilder;
	private InetAddress spIpAddress;

	public CoapTreeBuilder getCoapTreeBuilder() {
		return coapTreeBuilder;
	}

	/**
	 * Instantiates a new discovery resource with the specified name.
	 *
	 * @param name
	 *            the name
	 * @param attributes
	 *            the attributes of this resource
	 */
	public ContainerResource(String name, SNResourceAttributes attributes,
			InetAddress spIpAddress) {
		super(name, true, true);

		this.spIpAddress = spIpAddress;
		this.coapTreeBuilder = new CoapTreeBuilder(this,
				VisibilityPolicy.ALL_INVISIBLE);

		for (String attr : attributes.getAttributeKeySet()) {
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}

	/**
	 * Responds with a list of his child resources, i.e. links.
	 * 
	 * @param exchange
	 *            the exchange
	 */
	@Override
	public void handleGET(CoapExchange exchange) {
		String tree = discoverTree((Resource) this,
				exchange.getRequestOptions().getUriQuery());
		exchange.respond(ResponseCode.CONTENT, tree,
				MediaTypeRegistry.APPLICATION_LINK_FORMAT);
	}

	/**
	 * Builds up the list of resources of the specified root resource. Queries
	 * serve as filter and might prevent undesired resources from appearing on
	 * the list.
	 * 
	 * @param root
	 *            the root resource of the server
	 * @param queries
	 *            the queries
	 * @return the list of resources as string
	 */

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
		if (getSPIpAddress().equals(exchange.getSourceAddress())) {

			/*
			 * get the list of "dirty" resources into the subtree starting from
			 * the resource "this"
			 */
			response = Utilities.checkChanges(this);
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

	public String discoverTree(Resource root, List<String> queries) {
		StringBuilder buffer = new StringBuilder();
		for (Resource child : root.getChildren()) {
			LinkFormat.serializeTree(child, queries, buffer);
		}

		// remove last comma ',' of the buffer
		if (buffer.length() > 1)
			buffer.delete(buffer.length() - 1, buffer.length());

		return buffer.toString();
	}

	public InetAddress getSPIpAddress() {
		return spIpAddress;
	}
}
