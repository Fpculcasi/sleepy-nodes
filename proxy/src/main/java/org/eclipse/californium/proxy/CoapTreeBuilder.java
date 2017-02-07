/******************************************************************************
 * @title: CoapTreeBuilder
 * 	Used to build the tree of resources to reach a resource with "path-like"
 *  name (e.g. "dev/mfg")
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
import org.eclipse.californium.core.server.resources.Resource;

public class CoapTreeBuilder {
	
	protected CoapResource root;
	
	// defaultVisibility is the visibility applied to a deleted/de-registered
	// (by life-time expiration) resource
	private VisibilityPolicy defaultVisibility;
	
	
	public CoapTreeBuilder(CoapResource root, VisibilityPolicy visibility){
		this.root = root;
		this.defaultVisibility = visibility;
	}
	
	public VisibilityPolicy getVisibility() {
		return defaultVisibility;
	}
	
	protected class InfoPath{
		// Path that will be traversed
		String remainingPath;
		// resourceName is going to store, iteration after iteration,
		// the part of the path taken into account in that single iteration
		String current;
		public InfoPath(String Path) {
			remainingPath = Path;
		}
		public String getCurrent() {
			return current;
		}
		public boolean pathParsingFinished() {
			return (remainingPath == null)? true : false;
		}
		public void removeFirst(){			
			// removes the first '/', it will be add by CoapResource setParent(),
	    	// see reference [1]
			remainingPath = remainingPath.substring(1);
			// is resourceName a simple name or a path?
			int separatorIndex = remainingPath.indexOf('/');
			
			if (separatorIndex > 0){
				// resourceName contains a path. We only need the name
				current = remainingPath.substring(0, separatorIndex);
				// remaining_path takes the remaining part of the path
				remainingPath = remainingPath.substring(separatorIndex);
			} else {
				// remainingPath contains only the last part of the path now,
				// i.e. it's only a simple name now. 
				current = remainingPath;
				remainingPath = null;
			}
		}
	}
		
	
	private boolean parametersAreValid(String path){
		// Method that check the correctness of the parameter. In
		// case of problem, null must be returned
		if (path.charAt(0) != '/'){
			//TODO: idem se ci sono due '/' di seguito. 
			// bisogna vedere cosa accadrebbe
			return false;
		}
		return true;
	}
	
	
	/**
	 * Builds a subtree of resources from the given path, basing on the
	 * given VisibilityPolicy, and adds it as child of the given root. 
	 * The last part of the path is not added instead, 
	 * and it is left to the user to create a last resource,
	 * using the name (i.e. the last name contained in the path) 
	 * returned by subtreeBuilder, and to adds it as child of the resource
	 * returned by subtreeBuilder, i.e. the resource representing
	 * the penultimate part of the path.  
	 * 
	 * @param path The path containing the name of the resources to be created
	 * or traversed.
	 * @param root The resource starting from which the subtree will be built. 
	 * If you would like the subtree to start from the root of the CoapResver,
	 * use org.eclipse.californium.core.CoapServer.getRoot() as root.
	 * @param vPolicy The visibility policy. Possible values are:
	 * VisibilityPolicy.ALL_VISIBLE or VisibilityPolicy.ALL_INVISIBLE.
	 * If a CoapResource has to be created, it will be created with 
	 * the visibility specified by the policy. 
	 * 
	 * @return a class containing the resource representing the
	 * penultimate part of the path, i.e. the father of the last
	 * resource, that the user is expected to add, and the name
	 * of such last resource, extracted from the path.
	 */
	public synchronized boolean add(CoapResource newResource, String path, 
			VisibilityPolicy vPolicy){
		if (parametersAreValid(path) == false){
			return false;
		}
		// In each iteration, currentFather store the father of the resource
		// it is going to be created or traversed in that iteration. 
		// Initially it coincides with the root of the subtree 
		Resource currentFather = root;
		// Class used to memorize remaining path and name of
		// the resource that is currently being traversed or created. 
		// It also consider if the algorithm is considering the last part
		// of the path
		InfoPath iPath = new InfoPath(path);
		while(true){
			iPath.removeFirst();
			if (iPath.pathParsingFinished()){
				// The user, during newResource creation, should have
				// set all its fields, like visibility, so the only
				// fields still to be set is name
				newResource.setName(iPath.current);
				// In order to avoid breaking the subtree, if a resource
				// with the same name of the resource is being created,
				// its children have to be moved to the new resource
				Resource toDelete = (currentFather.getChild(iPath.current));
				if (toDelete != null){
					for(Resource child : toDelete.getChildren()){
						newResource.add(child);
					}
				}
				currentFather.add(newResource);
				return true;
			} else {
				// Does a resource with name resourceName already exists as
				// child of the Resource we are considering?
				Resource child = currentFather.getChild(iPath.getCurrent());
				
				if (child != null){
					if (!(child instanceof CoapResource)){
						System.err.println("Resource " + child.getName()
									+ " is not a CoapResource: its visibility"
									+ " will not be affected");
					} else {
						// The resource already exists. We may need to
						// change his visibility according to the policy
						handleExistingResource((CoapResource)child, vPolicy);
					}
					currentFather = child;
				} else {
					// The considered resource has to be created
					currentFather = handleResourceCreation(
							iPath.getCurrent(), currentFather, vPolicy);
				}
			}	
		}
	}
	
	/* If not specified choice the default visibility */
	public boolean add(CoapResource newResource, String path){
		return add(newResource, path, defaultVisibility);
	}
	
	protected void handleExistingResource(CoapResource child,
			VisibilityPolicy vPolicy) {
			System.out.println("traversing intermediate resource" 
					+ " " + child.getName() + ", visibility " + child.isVisible() 
					+ " and son of " + child.getParent().getName());
			// The intermediate resource was already there
			switch (vPolicy){
				case ALL_VISIBLE:
						if(child.isVisible() == false){
							child.setVisible(true);
						}
					break;
				case ALL_INVISIBLE:
					// nothing has to be done
					break;
			}		
	}
	
	protected Resource handleResourceCreation(
			String resourceName, Resource father, VisibilityPolicy vPolicy) {
		// newResource is used to store the newly created resource
		Resource newResource = null;
		// Creation of an intermediate resource
		switch (vPolicy){
			case ALL_VISIBLE:
				newResource = new ActiveCoapResource(resourceName, false, true);
				break;
			case ALL_INVISIBLE:					
				newResource = new ActiveCoapResource(resourceName, false, false){
					@Override
					public void handleGET(CoapExchange exchange){
						exchange.respond(CoAP.ResponseCode.NOT_FOUND);
					}
				};
				break;
		}
		father.add(newResource);
		System.out.println("Created intermediate resource" 
				+ " " + resourceName + ", visibility " + newResource.isVisible()
				+ " and son of " + father.getName());	
		return newResource;
	}
}
