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
 * requests with a list of his descendant resources, i.e. links. A proxy, every
 * time a new sleepy node delegates some resources to it, creates a new
 * ContainerResource, associated to the new sleepy node, acting as root of the
 * subtree used to store the copies of the sleepy node's delegated resources.
 * Thus, the ContainerResource name will be part of the prefix of each
 * resource's complete URI.
 */
public class ContainerResource extends ActiveCoapResource {

	// The CoapTreeBuilder instance associated with this ContainerResource
	private CoapTreeBuilder coapTreeBuilder;

	// The IP address of the sleepy node associated with this ContainerResource
	private InetAddress snIpAddress;

	/**
	 * Instantiates a new ContainerResource with the specified name, attributes,
	 * and sleepy node's address.
	 *
	 * @param name
	 *            the name
	 * @param attributes
	 *            the attributes of this resource
	 * @param snIPAddress
	 *            the address of the sleepy node associated with this
	 *            ContainerResource
	 */
	public ContainerResource(String name, SNResourceAttributes attributes,
			InetAddress spIpAddress) {
		super(name, true, true);

		this.snIpAddress = spIpAddress;
		this.coapTreeBuilder = new CoapTreeBuilder(this,
				VisibilityPolicy.ALL_INVISIBLE);

		for (String attr : attributes.getAttributeKeySet()) {
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}	
	
	/**
	 * Get the CoapTreeBuilder instance associated with this ContainerResource.
	 * 
	 * @return the CoapTreeBuilder instance
	 */
	public CoapTreeBuilder getCoapTreeBuilder() {
		return coapTreeBuilder;
	}
	
	/**
	 * Get the spIpAddress field, representing the IP address of the sleepy node
	 * associated with this ContainerResource instance.
	 * 
	 * @return
	 * 		the snIpAddress
	 */
	public InetAddress getSPIpAddress() {
		return snIpAddress;
	}

	/**
	 * Responds with the list of resources in its subtree, i.e. its descendants.
	 * Its subtree contain the copy of the resources delegated by the sleepy
	 * node associated with this ContainerResource (if any). The response is in
	 * application/link-format.
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
	 * handlePOST is used by the delegating sleepy node to retrieve the list of
	 * resources it has delegated which have been modified by someone else (like
	 * a regular node for configuring purpose). 
	 * The result use - if any - the queries specified in the request in
	 * order to filter the result.
	 * This method may only be called by the sleepy node associated with this
	 * ContainerResource
	 */
	@Override
	public void handlePOST(CoapExchange exchange) {
		String response = null;
		ResponseCode code;

		if (getSPIpAddress().equals(exchange.getSourceAddress())) {
			/*
			 * The request comes from the delegating sleepy node. The list of
			 * "dirty" resources located in the subtree starting from the
			 * resource "this" must be returned.
			 */
			List<String> queries = exchange.getRequestOptions().getUriQuery();
			response = Utilities.checkChanges(this, queries);
			if (response != null) {
				// At least one resource has been modified: response code CHANGED
				code = ResponseCode.CHANGED;
			} else {
				// No resource has been modified: response code VALID
				code = ResponseCode.VALID;
			}

		} else {
			/*
			 * The request comes from a node different from the owner of this
			 * resource, and thus must not be allowed.
			 */
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
	protected String discoverTree(Resource root, List<String> queries) {
		StringBuilder buffer = new StringBuilder();
		for (Resource child : root.getChildren()) {
			LinkFormat.serializeTree(child, queries, buffer);
		}

		// remove last comma ',' of the buffer
		if (buffer.length() > 1)
			buffer.delete(buffer.length() - 1, buffer.length());
		return buffer.toString();
	}

}
