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
 * 
 * TODO: the method handlePOST should include some method,
 * instead of having all that bloat of code
 * TODO: container resources (location resources) like sp/0 
 * should have content type=40, link-format
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
    	
    	/* TODO: Prima di andare avanti, sarebbe buono controllare
    	 * anche che la richiesta sia ben formata: ad esempio
    	 * che al primo posto ci sia il path, che dentro il path
    	 * non ci siano doppi '/' e non ci siano spazi etc.
    	 */
    	
    	/* The endpoint was specified. We try to understand if this
    	 * endpoint already registered with this proxy. If that is the case,
    	 * in the following map there will be a corresponding DelegatedResource
    	 */
        Map<String, ContainerResource> EPs = proxy.getEPs();
        ContainerResource containerResource = EPs.get(epValue);
        
        if(containerResource == null){ // the node has never delegated before
        	// TODO: ma sara' safe fare una cosa del genere? non 
        	// sarebbe meglio mettere un bel .toString?
        	// Anche newName non mi piace molto, sarebbe meglio, chesso',
        	// sleepyNodeLocalId o sleepyNodeContainerId
        	String newName = "" + proxy.newEPId();
        	containerResource = new ContainerResource(newName, queryAttributes);
        	
        	// Add the new <endPoint, locationRerouce> pair to the map
        	EPs.put(epValue, containerResource);
        	// Add the newly created resource as child of his container resource
        	add(containerResource);

            System.out.println("[Added] " + newName + " (visible: "
              + containerResource.isVisible() + ") " + containerResource.getName()
              + "\n-" + containerResource.getPath()
              + "\n-" + containerResource.getURI()
              + "\n" + queryAttributes );
        }
        
        // Creates the delegated resources, but do not initialize them
        String payload = exchange.getRequestText().trim();
        // Fills a string array with the delegated resources
        String resources[] = payload.split(",");
        for(String r: resources){
        	String fields[] = r.split(";");
        	SNResourceAttributes attributes = new SNResourceAttributes();
        	for(String attribute: fields){
        		// TODO: possibile che non ci sia un modo piu' semplice
        		// per dire "salta il primo elemento"?
        		// ah ecco, ho visto che si puo' sostituire con
        		// if (attribute = fields[0]) continue.
        		// penso sia anche piu' veloce da controllare
        		// tanto anche nel seguito stiamo assumendo che 
        		// il path stia nella prima posizione dell'array
        		if(!attribute.matches("^<.*>$")){ // exclude path name
        			String attr[] = attribute.split("=");
        			attributes.addAttribute(attr[0], attr[1].replace("\"",""));
        		}
        	}
        	String cleanedPath = fields[0].replace("<","").replace(">","");
        	
        	DelegatedResource newResource =	new DelegatedResource(
        			null, true, attributes);
        	
        	// TODO: visibility has to be set to false when the PUT is ready

        	containerResource.getCoapTreeBuilder().add(
        			newResource, 
        			cleanedPath, 
        			VisibilityPolicy.ALL_INVISIBLE);
        	
        }
        // I add the "Location" option to the answer, 
        // set with the URI of the resource container
        exchange.setLocationPath(containerResource.getURI());
		exchange.respond(CoAP.ResponseCode.CREATED);
        
    }
}
