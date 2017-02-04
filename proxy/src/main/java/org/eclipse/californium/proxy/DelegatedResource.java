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
import org.eclipse.californium.core.coap.CoAP.Code;
import org.eclipse.californium.core.network.Exchange;
import org.eclipse.californium.core.server.resources.CoapExchange;

public class DelegatedResource extends CoapResource {
	
	private String value;
	
	/**
	 * @param name the name of the resource to be created.
	 * @param isVisible indicate whether the newly created resource will be
	 * visible or not from clients.
	 * @param attributes list of attributes of the resource
	 */
	public DelegatedResource(String name, boolean isVisible, 
			SNResourceAttributes attributes){
		super(name, isVisible);
		
		for(String attr: attributes.getAttributeKeySet()){
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}
	
	/** 
	 * Simpler constructor, resources are created as visible.
	 * @param name the name of the resource to be created.
	 * TODO: penso che questo costruttore non abbia piu' motivo di esistere
	 */
	public DelegatedResource(String name){
		this(name, true, null);
	}

	/**
	 * Method for adding the given attributes to the newly created resource
	 * or to an already existent one
	 * @param attributes the attributes to be added to the resource
	 * TODO: questa funzione ha senso solo nel nostro progetto,
	 * quindi o la togliamo da qui o, se ce la vogliamo lasciare,
	 * usiamo dei generici ResourceAttributes.
	 * Una possibilita' sarebbe metterla dentro un file
	 * utilities.java
	 */
	/*
	public void addAttributes(SNResourceAttributes attributes) {
		for(String attr: attributes.getAttributeKeySet()){
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
	}
	*/

	
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
		switch (code) {
			case GET:
				if (isVisible() == false){
					coapExchange.respond(CoAP.ResponseCode.NOT_FOUND);
				} else {
					handleGET(coapExchange);
				}
				break;
			case POST:	handlePOST(coapExchange); break;
			case PUT:	handlePUT(coapExchange); break;
			case DELETE: handleDELETE(coapExchange); break;
		}
	}
	
	/**
	 * handleGET() returns the state of the resource stored in 'value' variable
	 */
	@Override
	public void handleGET(CoapExchange exchange){
		exchange.respond(CoAP.ResponseCode.CONTENT, value);
	}
	
	/**
	 * handlePUT() modifies the the state of the delegated resource.
	 * The first PUT request also initializes the resource by setting it
	 * visible and observable.
	 */
    @Override
    public void handlePUT(CoapExchange exchange) {
    	String payload = exchange.getRequestText();
    	
    	if(value == null){ // not initialized yet
    		setObservable(true);
    		setVisible(true);
    		value = payload;
    		exchange.respond(CoAP.ResponseCode.CREATED);
    	} else {
    		value = payload;
    		exchange.respond(CoAP.ResponseCode.CHANGED);
    	}
    	
    	// TODO: handle life-time expiration
    	
    }
}
