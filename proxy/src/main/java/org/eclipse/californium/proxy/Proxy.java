/******************************************************************************
 * Proxy server
 * 
 * Handles requests from sleepy nodes
 *
 *****************************************************************************/

package org.eclipse.californium.proxy;

import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

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
	
	public Proxy() {
		super();
		
		SPResource sp = new SPResource();
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
}