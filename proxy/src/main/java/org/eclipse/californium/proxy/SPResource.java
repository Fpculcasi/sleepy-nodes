package org.eclipse.californium.proxy;

import java.util.Set;
import java.util.TreeSet;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;
import static org.eclipse.californium.core.coap.MediaTypeRegistry.APPLICATION_JSON;

public class SPResource extends CoapResource {
	public SPResource(){
		super("/sp");
		
		getAttributes().setTitle("Sleepy Proxy Resource");
		getAttributes().addAttribute("rt", "core.sp");
		
		setObservable(false);
	}
	
    @Override
    public void handleGET(CoapExchange exchange) {
        System.out.println("SleepyProxyResource.handleGet called");
        String attributes = "";
        Set<String> attributeSet = getAttributes().getAttributeKeySet();
        for(String temp : attributeSet){
        	attributes+=";" + temp + "=\"" + 
        			getAttributes().getAttributeValues(temp).get(0)+ "\"";
        } 
        exchange.respond(CoAP.ResponseCode.CONTENT, 
        		"<" + getName() + ">" + attributes, APPLICATION_JSON);

    }
    /*
    public static void main(String[] args){
    	SPResource sp = new SPResource();
    }
	*/
    
}
