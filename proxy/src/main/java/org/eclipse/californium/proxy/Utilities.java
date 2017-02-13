package org.eclipse.californium.proxy;

import org.eclipse.californium.core.server.resources.Resource;

public class Utilities {
	/**
	 * Build the String listing dirty resources.
	 * 
	 * @param root
	 *            Starting point for the resource scan
	 * @return String containing the list of "dirty" resources under the
	 *         resource passed as argument
	 */
	private static String recursiveCheckChanges(Resource root) {
		StringBuilder buffer = new StringBuilder();
		for (Resource child : root.getChildren()) {
			// all the resource of the subtree must be ActiveCoapResource
			ActiveCoapResource c = (ActiveCoapResource) child;

			if (c.isVisible() && c.isDirty()) {
				c.setDirty(false);
				buffer.append("<").append(child.getPath())
						.append(child.getName()).append(">").append(",");
			}
			buffer.append(recursiveCheckChanges(child));
		}

		return buffer.toString();
	}

	public static String checkChanges(Resource root) {
		String s = recursiveCheckChanges(root);
		if (s.length() != 0) {
			// remove last comma
			return s.substring(0, s.length() - 1);
		} else {
			return null;
		}
	}
}
