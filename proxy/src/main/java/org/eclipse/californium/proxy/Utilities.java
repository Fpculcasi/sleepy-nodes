package org.eclipse.californium.proxy;

import java.util.List;

import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.server.resources.Resource;

/*
 * This class contains methods used in different classes that
 * do not rely on features of a specific class.
 */
public class Utilities {
	/*
	 * Private method called internally by checkChanges
	 */
	private static String recursiveCheckChanges(Resource root,
			List<String> queries) {
		StringBuilder buffer = new StringBuilder();
		for (Resource child : root.getChildren()) {
			// all the resource of the subtree must be ActiveCoapResource
			ActiveCoapResource c = (ActiveCoapResource) child;

			if (c.isVisible() && c.isDirty()
					&& LinkFormat.matches(c, queries)) {
				c.setDirty(false);
				buffer.append("<").append(child.getPath())
						.append(child.getName()).append(">").append(",");
			}
			buffer.append(recursiveCheckChanges(child, queries));
		}
		return buffer.toString();
	}

	/**
	 * Build the String listing dirty resources.
	 * 
	 * @param root
	 *            Starting point for the resource scan.
	 * @param queries
	 * 			  List of queries sent by the user in order to filtering results.
	 * @return String containing the list of 'dirty' resources which have as
	 * 			prefix the URI of the resource passed as argument.
	 */
	public static String checkChanges(Resource root, List<String> queries) {
		String s = recursiveCheckChanges(root, queries);
		if (s.length() != 0) {
			// remove last comma
			return s.substring(0, s.length() - 1);
		} else {
			return null;
		}
	}
}
