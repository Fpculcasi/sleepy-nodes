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

/**
 * REFERENCES:
 * [1] https://github.com/eclipse/californium/blob/master/californium-core/src/main/java/org/eclipse/californium/core/CoapResource.java#L518
 * 
 * 
 */

package org.eclipse.californium.proxy;

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
 * In the constructor:
 * The 'name' is "sp"; the only required attribute is 'rt', resource type.
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
	/* TODO: it's a shame, ma temo che vada tolto questo metodo, no? */
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
        		"<" + getPath()+getName() + ">" +attributes, 
        		APPLICATION_LINK_FORMAT);
    }

	/**
	 * The handlePOST method handles POST request performed on the SP resource.
	 * It returns a '2.01 Created Location: /sp/-' response code, ...
	 */
    /* TODO: per quanto riguarda qui sopra: il reponse code e' soltanto 2.01 Created;
     * inoltre, per chiarezza, al posto di /sp/- metterei /sp/x 
     * 
     */
    @Override
    public void handlePOST(CoapExchange exchange) {
    	System.out.println("***SleepyProxyResource.handlePOST called. Handled"
    		+ "	by thread" + java.lang.Thread.currentThread().toString());

    	// We retrieve queries contained in the URI
    	// TODO: ho cambiato uriQuerys con uriQueries
    	List<String> uriQueries = exchange.getRequestOptions().getUriQuery();
    	SNResourceAttributes queryAttributes = new SNResourceAttributes();
    	
    	// We fill a attribute-value maps with the value found in the query
    	for(String query: uriQueries){
    		String keyValue[] = query.split("=",2);
        	queryAttributes.addAttribute(keyValue[0],keyValue[1]);
    	}
    	String epValue = queryAttributes.getEndPoint();
    	
    	if(epValue == null) {
    		// the endpoint value was not specified in the query
    		exchange.respond(CoAP.ResponseCode.BAD_REQUEST);
    		return;
    	}
    	
    	/* The endpoint was specified. We try to understand if this
    	 * endpoint already registered with this proxy. If that is the case,
    	 * in the following map there will be a corresponding DelegatedResource
    	 */
        Map<String, LocationResource> EPs = proxy.getEPs();
        LocationResource locationResource = EPs.get(epValue);
        
        if(locationResource == null){ // the node has never delegated before
        	// TODO: ma sara' safe fare una cosa del genere? non 
        	// sarebbe meglio mettere un bel .toString?
        	// Anche newName non mi piace molto, sarebbe meglio, chesso',
        	// sleepyNodeLocalId o sleepyNodeContainerId
        	String newName = "" + proxy.newEPId();
        	locationResource = new LocationResource(newName, queryAttributes);
        	
        	EPs.put(epValue, locationResource);
        	// Add the newly created resource as child of *this* (SP) resource
        	add(locationResource);

            System.out.println("[Added] " + newName + " (visible: "
              + locationResource.isVisible() + ") " + locationResource.getName()
              + "\n-" + locationResource.getPath()
              + "\n-" + locationResource.getURI()
              + "\n" + queryAttributes );
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
        	
        	/* remove the first '/', it will be add by CoapResource setParent(),
        	 * see [1] */
        	newName = newName.substring(1);
        	
        	DelegatedResource newResource =
        			new DelegatedResource(newName, attributes, true/*false*/);
        	
        	// I add the newly created resources to the resource container
        	locationResource.add(newResource);

            System.out.println("[Added] " + newName + " (visible: "
              + newResource.isVisible() + ") " + newResource.getName()
              + "\n-" + newResource.getPath()
              + "\n-" + newResource.getURI()
              + "\n" + attributes );
        }
        
        /* I add the "Location" option to the answer, 
         * set with the URI of the resource container
         */
        exchange.setLocationPath(locationResource.getURI());
		exchange.respond(CoAP.ResponseCode.CREATED);
        
    }
    /*
    public static void main(String[] args){
    	SPResource sp = new SPResource();
    }
	*/
    
}
