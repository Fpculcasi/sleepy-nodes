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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.network.CoapEndpoint;

/**
 * Proxy is a basic implementation of a CoapServer. Proxy extends CoapServer and
 * implements everything needed to works as proxy, i.e. the definition of a
 * resource with rt=core.ps, working as a base path for every resource delegated
 * on this proxy, and the definition of a map with the purpose of storing the
 * base path for each delegating node.
 * 
 * Into squared brackets the references to "Sleepy CoAP Nodes" draft RFC(v.4):
 * https://tools.ietf.org/html/draft-zotti-core-sleepy-nodes-04
 * 
 * When instantiated the proxy adds a "Sleepy Proxy" resource able to handle -
 * discover from Sleepy Nodes [5.1] - registration of delegated resources [5.2]
 *
 */
public class Proxy extends CoapServer {
	/*
	 * Every sleepy node has a reserved space in the proxy, represented by a
	 * resource, which is the base path for the resources delegated by the
	 * sleepy node. All the resources delegated (possibly at different times) by
	 * the sleepy node to the proxy will be stored in that space, organized in a
	 * hierarchical way.
	 * 
	 * EPs is a map linking each end point to the resource representing the
	 * corresponding base path. We need to implement it as a CurrentHashMap in
	 * order to have stronger guarantees in a multi-threaded environment (e.g.
	 * for discovery to work properly).
	 */
	private ConcurrentHashMap<String, ContainerResource> EPs;

	// Counter: accessed atomically to obtain a new identifier
	private int counter = 0;

	public Proxy() {
		super();

		EPs = new ConcurrentHashMap<String, ContainerResource>();

		/*
		 * Each proxy use a sp (sleepy proxy) resource, representing the base
		 * path starting from which all the delegated resources will be stored.
		 */
		SPResource sp = new SPResource(this);
		add(sp);

		// setMessageDeliverer(new ProxyMessageDeliverer(sp));
	}

	public static void main(String[] args) {
		Proxy proxy = new Proxy();
		/*
		 * An endpoint is used by the server to expose resources to clients. I-s
		 * bound to a particular IP address and port
		 */
		proxy.addEndpoint(
				new CoapEndpoint(new InetSocketAddress("aaaa::1", 5683)));

		proxy.start();
	}

	@Override
	public void start() {
		super.start();
	}

	/**
	 * easier to expose the map through a method rather than declaring it as
	 * public
	 * 
	 * @return the map of base paths
	 */
	public Map<String, ContainerResource> getEPs() {
		return EPs;
	}

	/* Synchronous because multiple threads access counter */
	public synchronized int newEPId() {
		return counter++;
	}
}