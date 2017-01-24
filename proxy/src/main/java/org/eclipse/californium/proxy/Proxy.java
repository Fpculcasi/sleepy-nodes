/******************************************************************************
 * @title: Proxy server
 * 	Handles requests from sleepy nodes
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

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;

/**
 * Proxy is a basic implementation of a CoapServer.
 * 
 * Into squared brackets the references to "Sleepy CoAP Nodes" draft RFC(v.4):
 * https://tools.ietf.org/html/draft-zotti-core-sleepy-nodes-04
 * 
 * When instantiated the proxy adds a "Sleepy Proxy" resource able to handle
 * discover from Sleepy Nodes [5.1], registration of delegated resources [5.2]
 * and de-registration [5.3]
 *
 */
public class Proxy extends CoapServer {
	/* EPs: maps path locations for each end point
	 * Every sleepy node has its own location, such that resources delegated
	 * in different times from the same sleepy node will be located 
	 * hierarchically under the same location (a particular resource)
	 */
	private Map<String, DelegatedResource> EPs;
	
	/* counter: accessed atomically to achieve a new identifier */
	private int counter=0;
	
	public Proxy() {
		super();
		
		EPs = new HashMap<String, DelegatedResource>();
		
		/* each proxy need a sleepy proxy resource */
		SPResource sp = new SPResource(this);
		add(sp);
	}
	
	public static void main(String[] args) {
		Proxy proxy = new Proxy();
		proxy.addEndpoint(new CoapEndpoint(new InetSocketAddress(
                "aaaa::1", 5683)));
		
		proxy.start();
	}
	
	@Override
	public void start() {
		super.start();
	}
	
	// cleaner to expose the map through a method rather than declare as public
	public Map<String, DelegatedResource> getEPs(){
		return EPs;
	}
	
	/* Synchronous because multiple threads access counter*/
	public synchronized int newEPId() {
        return counter++;
    }
}