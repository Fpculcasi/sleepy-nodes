/*********************************************
 * 
 * Sleepy Proxy Resource
 * 
 * The base path of the resources in the proxy
 * 
 *********************************************/

package org.eclipse.californium.proxy;

import java.util.Set;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;

/**
 * This class implements the SP (Sleepy Proxy) resource of the proxy. This
 * resource is the root of the hierarchy in which resources will be stored.
 * 
 * In the constructor:
 * The 'name' is "/sp"; the only required attribute is 'rt', resource type.
 * A client performing the discovery of the proxy will filter on
 * rt=core.sp.
 * The SP resource doesn't need to be observable, since its state never
 * changes.
 */
public class SPResource extends CoapResource {
	public SPResource(){
		super("/sp", true);
		
		getAttributes().setTitle("Sleepy Proxy Resource");
		getAttributes().addAttribute("rt", "core.sp");
		
		setObservable(false);
	}
	
	/**
	 * The handleGET method handles GET request performed on the SP resource.
	 * It returns a '2.05 Content' response code, along with a
	 * payload including the name and the list of the attribute-value pairs 
	 * of the resource's attributes.
	 */
	/*
    @Override
    public void handleGET(CoapExchange exchange) {
        System.out.println("SleepyProxyResource.handleGet called. Handled"
        		+ "	by thread" + java.lang.Thread.currentThread().toString());
        
        String attributes = "";
        Set<String> attributeSet = getAttributes().getAttributeKeySet();
        for(String temp : attributeSet){
        	attributes+=";" + temp + "=\"" + 
        			getAttributes().getAttributeValues(temp).get(0)+ "\"";
        } 
        
        exchange.respond(CoAP.ResponseCode.CONTENT, 
        		"<" + getName() + ">" + attributes, APPLICATION_JSON);
    }
    */
    
    /*
    public static void main(String[] args){
    	SPResource sp = new SPResource();
    }
	*/
    
}
