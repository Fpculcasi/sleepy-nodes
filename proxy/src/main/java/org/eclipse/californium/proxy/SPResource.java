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

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.coap.CoAP;
import org.eclipse.californium.core.server.resources.CoapExchange;

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

	private Proxy proxy;

	public SPResource(Proxy proxy){
		super("sp");
		
		this.proxy = proxy;
		
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
    @Override
    public void handleGET(CoapExchange exchange) {
        System.out.println("***SleepyProxyResource.handleGET called. Handled"
        		+ "	by thread" + java.lang.Thread.currentThread().toString());
        
        String attributes = "";
        Set<String> attributeSet = getAttributes().getAttributeKeySet();
        for(String temp : attributeSet){
        	attributes+=";" + temp + "=\"" + 
        			getAttributes().getAttributeValues(temp).get(0)+ "\"";
        }
        
        /*FIXME: why isn't Copper rendering the response? 
		 *		(even if the same is correctly rendered in other instances)*/
        exchange.respond(CoAP.ResponseCode.CONTENT, 
        		"<" + getPath()+getName() + ">" +attributes);
    }

	/**
	 * The handlePOST method handles POST request performed on the SP resource.
	 * It returns a '2.01 Created Location: /sp/-' response code, ...
	 */
    @Override
    public void handlePOST(CoapExchange exchange) {
    	System.out.println("***SleepyProxyResource.handlePOST called. Handled"
    		+ "	by thread" + java.lang.Thread.currentThread().toString());

    	List<String> uriQuerys = exchange.getRequestOptions().getUriQuery();
    	SNResourceAttributes queryAttributes = new SNResourceAttributes();
    	
    	for(String query: uriQuerys){
    		String keyValue[] = query.split("=",2);
        	queryAttributes.addAttribute(keyValue[0],keyValue[1]);
    	}
    	String epValue = queryAttributes.getEndPoint();
    	
    	if(epValue == null) {
    		// can't find the end point identifier
    		exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
    		return;
    	}
    	
        Map<String, DelegatedResource> EPs = proxy.getEPs();
        DelegatedResource resource = EPs.get(epValue);
        if(resource == null){ // the node has never delegated before
        	String newName = "" + proxy.newEPId();
        	resource = new DelegatedResource(newName, queryAttributes);
        	
        	
        	EPs.put(epValue, resource);
        	add(resource);
        	System.out.println("[Added] " + newName + " (visible: " + resource.isVisible() + ") "
                	+ resource.getName() + "\n-"
        			+ resource.getPath() + "\n-"
        			+ resource.getURI() + "\n"
        			+ queryAttributes );
        }
        
        /* Generate location for the new resources */
        String payload = exchange.getRequestText().trim();
        String resources[] = payload.split(",");
        for(String r: resources){
        	String fields[] = r.split(";");
        	SNResourceAttributes attributes = new SNResourceAttributes();
        	for(String attribute: fields){
        		if(!attribute.matches("^<.*>$")){ // exclude path name
        			String attr[] = attribute.split("=");
        			attributes.addAttribute(attr[0], attr[1].replace("\"",""));
        		}
        	}
        	String newName = fields[0].replace("<","").replace(">","");
        	
        	/* remove the first '/', it will be add by CoapResource setParent()
        	 * https://github.com/eclipse/californium/blob/master/
        	 * californium-core/src/main/java/org/eclipse/californium/core/
        	 * CoapResource.java#L518 */
        	newName = newName.substring(1);
        	
        	DelegatedResource newResource =
        			new DelegatedResource(newName, attributes, true/*false*/);
        	
        	resource.add(newResource);
        	System.out.println("[Added] " + newName + " (visible: "
        			+ newResource.isVisible() + ") "
                	+ newResource.getName() + "\n-"
        			+ newResource.getPath() + "\n-"
        			+ newResource.getURI() + "\n"
        			+ attributes );
        }
		exchange.respond(CoAP.ResponseCode.CREATED);
        
    }
    /*
    public static void main(String[] args){
    	SPResource sp = new SPResource();
    }
	*/
    
}
