/******************************************************************************
 * @title: Sleepy Proxy Resource
 * 	The base path of the resources in the proxy
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

import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_LINK_FORMAT;

/**
 * This class implements the SP (Sleepy Proxy) resource of the proxy. This
 * resource is the root of the hierarchy in which resources will be stored.
 * 
 * The 'name' is "sp"; the only required attribute is 'rt',
 * resource type. A client performing the discovery of the proxy will filter on
 * rt=core.sp. The SP resource doesn't need to be observable, since its state
 * never changes.
 */
public class SPResource extends CoapResource {

	private Proxy proxy;

	/**
	 * Constructs the SP resource for a particular proxy.
	 * 
	 * @param proxy
	 * 		The proxy object on which the SP resource is constructed
	 */
	public SPResource(Proxy proxy) {
		super("sp");

		this.proxy = proxy;

		getAttributes().setTitle("Sleepy Proxy Resource");
		getAttributes().addAttribute("rt", "core.sp");

		setObservable(false);
	}

	/**
	 * The handleGET method handles GET request performed on the SP resource. If
	 * successful, it returns a '2.05 Content' response code, along with 
	 * a payload including the name and the list of the attribute-value pairs 
	 * of the resource's attributes, in link-format content.
	 * 
	 * @param exchange
	 * 		The exchange object that handles requests/responses
	 */
	@Override
	public void handleGET(CoapExchange exchange) {
		System.out.println("***SleepyProxyResource.handleGET called. Handled"
				+ "	by thread" + java.lang.Thread.currentThread().toString());

		String attributes = "";
		Set<String> attributeSet = getAttributes().getAttributeKeySet();
		for (String temp : attributeSet) {
			attributes += ";" + temp + "=\""
					+ getAttributes().getAttributeValues(temp).get(0) + "\"";
		}

		exchange.respond(CoAP.ResponseCode.CONTENT,
				"<" + getPath() + getName() + ">" + attributes,
				APPLICATION_LINK_FORMAT);
	}

	/**
	 * The handlePOST method handles POST requests performed on the SP resource.
	 * These requests correspond to the resource registration process by a 
	 * certain sleepy node.
	 * If successful, it returns a '2.01 Created Location: /sp/x' response code,
	 * where x is a local identifier for the registering sleepy node.
	 * 
	 * @param exchange
	 * 		The exchange object that handles requests/responses
	 */
	@Override
	public void handlePOST(CoapExchange exchange) {
		System.out.println("***SleepyProxyResource.handlePOST called. Handled"
				+ "	by thread" + java.lang.Thread.currentThread().toString());

		// We retrieve queries contained in the URI
		List<String> uriQueries = exchange.getRequestOptions().getUriQuery();
		SNResourceAttributes queryAttributes = new SNResourceAttributes();

		// We fill a attribute-value maps with the value found in the query
		for (String query : uriQueries) {
			String keyValue[] = query.split("=", 2);
			queryAttributes.addAttribute(keyValue[0], keyValue[1]);
		}
		String epValue = queryAttributes.getEndPoint();

		if (epValue == null) {
			// the endpoint value was not specified in the query
			exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
			return;
		}

		/*
		 * The endpoint was specified. We try to understand if this endpoint
		 * already registered with this proxy. If that is the case, in the
		 * following map there will be a corresponding DelegatedResource
		 */
		ContainerResource containerResource = getContainer(proxy.getEPs(),
				epValue, queryAttributes, exchange.getSourceAddress());

		// Create the delegated resources, but do not initialize them
		createResources(exchange.getRequestText().trim(), containerResource);

		// I add the "Location" option to the answer,
		// set with the URI of the resource container
		exchange.setLocationPath(containerResource.getURI());
		exchange.respond(CoAP.ResponseCode.CREATED);
	}

	/**
	 * Retrieve the container resource for a specific sleepy node (based on
	 * end-point value). If not existing yet create a new container.
	 * 
	 * @param EPs
	 *            Map of containers
	 * @param ep
	 *            End-point value
	 * @param queryAttributes
	 *            Attributes if the (hypothetical) new container
	 * @param address
	 *            Address of the delegating node
	 * @return The proper container
	 */
	private ContainerResource getContainer(Map<String, ContainerResource> EPs,
			String ep, SNResourceAttributes queryAttributes,
			InetAddress address) {
		ContainerResource containerResource = EPs.get(ep);

		if (containerResource == null) { // the node has never delegated before
			String newContainerId = "" + proxy.newEPId();

			queryAttributes.addContentType(APPLICATION_LINK_FORMAT);
			containerResource = new ContainerResource(newContainerId,
					queryAttributes, address);

			// Add the new <endPoint, locationRerouce> pair to the map
			EPs.put(ep, containerResource);
			// Add the newly created resource as child of his container resource
			add(containerResource);

			System.out.println("[Added] " + newContainerId + " (visible: "
					+ containerResource.isVisible() + ") "
					+ containerResource.getName() + "\n-"
					+ containerResource.getPath() + "\n-"
					+ containerResource.getURI() + "\n" + queryAttributes);
		}
		return containerResource;
	}

	/**
	 * Used to delegate the resources passed in a POST request payload.
	 * 
	 * @param payload
	 *            Payload of the request. It has to be parsed to collect the
	 *            different resources.
	 * @param containerResource
	 *            Base resource of the specific sleepy node.
	 */
	private void createResources(String payload,
			ContainerResource containerResource) {
		// Fills a string array with the delegated resources
		String resources[] = payload.split(",");
		for (String r : resources) {
			String fields[] = r.split(";");

			SNResourceAttributes attributes = new SNResourceAttributes();
			for (String attribute : fields) {
				if (attribute.compareTo(fields[0]) != 0) { // exclude path name
					String attr[] = attribute.split("=");
					attributes.addAttribute(attr[0], attr[1].replace("\"", ""));
				}
			}
			String cleanedPath = fields[0].replace("<", "").replace(">", "");

			DelegatedResource newResource = new DelegatedResource(null, false,
					attributes, containerResource);

			containerResource.getCoapTreeBuilder().add(newResource, cleanedPath,
					VisibilityPolicy.ALL_INVISIBLE);
		}
	}
}
