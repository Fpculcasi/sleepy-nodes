/******************************************************************************
 * @title: Sleepy node resource attribute
 * 	It's ResourceAttribute extended to set and retrieve the end point value
 * 	See: https://github.com/eclipse/californium/blob/master/californium-core/
 * 		src/main/java/org/eclipse/californium/core/server/resources/
 * 		ResourceAttributes.java
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

import java.util.Set;

import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.server.resources.ResourceAttributes;

public class SNResourceAttributes extends ResourceAttributes {

	public SNResourceAttributes() {
		super();
	}

	/**
	 * Gets the end point.
	 *
	 * @return the end point
	 */
	public String getEndPoint() {
		if (containsAttribute(LinkFormat.END_POINT)) {
			return getAttributeValues(LinkFormat.END_POINT).get(0);
		} else {
			return null;
		}
	}

	/**
	 * Sets the end point.
	 *
	 * @param endPoint
	 *            the end point
	 */
	public void setEndPoint(String endPoint) {
		setAttribute(LinkFormat.END_POINT, endPoint);
	}

	/**
	 * Generate a string containing the attribute pairs key-value
	 */
	public String toString() {
		String buff = "";
		Set<String> keys = getAttributeKeySet();
		for (String key : keys) {
			buff += key + "= " + getAttributeValues(key).get(0) + "\n";
		}
		return buff;
	}

}