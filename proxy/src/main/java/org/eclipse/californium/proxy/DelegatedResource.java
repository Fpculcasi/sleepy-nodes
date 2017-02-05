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

public class DelegatedResource extends ActiveCoapResource {
	
	private String value;
	private ContainerResource container;
	
	/**
	 * @param name the name of the resource to be created.
	 * @param isVisible indicate whether the newly created resource will be
	 * visible or not from clients.
	 * @param attributes list of attributes of the resource
	 */
	public DelegatedResource(String name, boolean isVisible, 
			SNResourceAttributes attributes, ContainerResource container) {
		super(name, true, isVisible);
		
		this.container = container;
		
		for(String attr: attributes.getAttributeKeySet()){
			getAttributes().addAttribute(attr,
					attributes.getAttributeValues(attr).get(0));
		}
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
	 * handleGET() returns the state of the resource stored in 'value' variable
	 * after checking the resource has been initialized
	 */
	@Override
	public void handleGET(CoapExchange exchange){
		if(!isVisible()) { // if not initialized
			exchange.respond(CoAP.ResponseCode.NOT_FOUND);
		} else {
			exchange.respond(CoAP.ResponseCode.CONTENT, value);
		}
	}
	
	/**
	 * handlePUT() modifies the the state of the delegated resource.
	 * The first PUT request also initializes the resource by setting it
	 * visible and observable.
	 */
    @Override
    public void handlePUT(CoapExchange exchange) {
    	String payload = exchange.getRequestText();
    	String response = null;
		ResponseCode code;

		value = payload; // update resource value
		
    	if(container.getSPIpAddress().equals(exchange.getSourceAddress())) {
    		// Update from the owner sleepy node
    		
        	response = checkChanges(container); // check all the resources
        	response.substring(0, response.length()-1); //remove last comma
        	
        	if(!isVisible()){ // not initialized yet
        		setObservable(true);
        		setVisible(true);
        		code = CoAP.ResponseCode.CREATED;
        	} else {
        		code = CoAP.ResponseCode.CHANGED;
        	}
    	} else { // Any other EP
    		if(!isVisible()){
    			code = CoAP.ResponseCode.NOT_FOUND;
    		}else{
	    		setDirty(true); // set the resource ad dirty
	    		code = CoAP.ResponseCode.CHANGED;
    		}
    	}
    	
    	// build a response
    	if(response == null){
    		exchange.respond(code);
    	} else {
    		exchange.respond(code, response, APPLICATION_LINK_FORMAT);
    	}
    	
    	
    	// TODO: handle life-time expiration
    	
    }
    
    public String checkChanges(Resource root) {
		StringBuilder buffer = new StringBuilder();
		for (Resource child: root.getChildren()) {
			ActiveCoapResource c = (ActiveCoapResource) child;
			if(c.isVisible() && c.isDirty()) {
				c.setDirty(false);
				buffer.append("<")
						.append(child.getPath())
						.append(child.getName())
						.append(">")
						.append(",");
			}
			buffer.append(checkChanges(child));
		}
		
		return buffer.toString();
	}
}
