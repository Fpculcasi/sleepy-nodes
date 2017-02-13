/******************************************************************************
 * @title: Visibility Policy
 * 	The policy of visibility for each resource under the container resource.
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

/**
 * The policy of visibility for each resource under the container resource.
 */
public enum VisibilityPolicy {
	// Used as argument in subtreeBuilder
	ALL_VISIBLE,	// all the resources created, both the intermediate
					// resources in the path and the new resource, are
					// created as visible from clients.

	ALL_INVISIBLE	// all the resources created, both the intermediate
					// resources in the path and the new resource, are
					// created as invisible. Useful when you have e.g.
					// a proxy that mantains the states of some resources
					// delegated by some sleepy nodes. In such a
					// situation, usually you have a two-step delegation:
					// a first phase where the sleepy node, e.g. with a POST,
					// request the proxy to create some resources. However,
					// they are not visible to clients until the sleepy
					// node, with another request (e.g. PUT), initialize them.
}
