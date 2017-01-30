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

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

public class DelegatedResource extends CoapResource {
	
	public DelegatedResource(String name, SNResourceAttributes attributes, boolean visible){
		
		super(name, visible);
		
		for(String attr: attributes.getAttributeKeySet()){
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}
	
	public DelegatedResource(String name, SNResourceAttributes attributes){
		this(name, attributes, true);
	}
	
	@Override
	public void handleGET(CoapExchange exchange){
		exchange.respond(CoAP.ResponseCode.CONTENT, "Hello, McFly");
	}
}
