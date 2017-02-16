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
import org.eclipse.californium.core.server.resources.Resource;

/**
 * CoapTreeBuilder is a helper class for handling a subtree of
 * {@link ActiveCoapResource}. It's useful when dealing with resources having
 * path-like names. Every CoapTreeBuilder has an associated root and an
 * associated visibility policy.
 * <p>
 * An user willing to create an ActiveCoapResource with a path-like name, like
 * "/a/b/c" and make it reachable from the outside is supposed to do the
 * following:<br>
 * - to create a CoapTreeBuilder, with the the intended root and visibility,<br>
 * - to create the ActiveCoapResource (the name will be overridden,<br>
 * - to call the <tt>add()</tt> method on the CoapTreeBuilder instance,
 * specifying the new resource and its complete name.<br>
 * The <tt>add()</tt> method will create an inactive internal ActiveCoapResource
 * "a" as child of the CoapTreeBuilder root, an inactive internal
 * ActiveCoapResource "b" as child of a, will assign the name of "c" to the 
 * ActiveCoapResource passed as argument and will adds it as child of b.
 * <p>
 * If the visibility_policy ALL_INVISIBLE is used, ActiveCoapResource 'a' and
 * 'b' in the example are not visible from the outside (they are not showed in
 * .well-known/core thanks to visibility = false, while it is not possible
 * to issue any request on them since they are inactive resource.
 *  The nice thing is that from the outside it will appear as if only 
 *  one resource exists: the external resource /a/b/c".<br>
 * ALL_VISIBLE visibility policy is useful for debugging purpose, but we do not
 * exclude some user could find it useful.
 * <p>
 * A <tt>remove()</tt> method is provided, which take care of deleting the
 * request resource and all the parent of that resource that are no more
 * necessary (if any).
 * <p>
 * The CoapTreeBuilder root is built outside the CoapTreeBiulder, and outside
 * the CoapTreeBuilder must be deleted.
 *
 */
public class CoapTreeBuilder {

	// The root of the CoapTreeBuilder
	private ActiveCoapResource root;

	/*
	 * ActiveCoapResource automatically created inside the CoapTreeBuilder are
	 * provided with this visibility. This may happen both during <tt>add()</tt>
	 * (if the user do now explicitly specifies another policy) and
	 * <tt>delete()</tt> (for example, given the tree a/b/c, with a root and b,c
	 * resources explicitly created by the user, if a delete(b) is issued, b
	 * cannot be simply removed, since c would become unreachable, thus an
	 * ActiveCoapResource "b" has to be automatically created).
	 */
	private VisibilityPolicy defaultVisibility;

	/**
	 * Creates a CoapTreeBuilder with the given root and visibility policy.
	 * 
	 * @param root
	 *            the root of the CoapTreeBuilder
	 * @param visibility
	 *            the visibility policy to be used in case of automatic creation
	 *            of ActiveCoapResources
	 */
	public CoapTreeBuilder(ActiveCoapResource root,
			VisibilityPolicy visibility) {
		this.root = root;
		this.defaultVisibility = visibility;
	}

	/**
	 * Return the visibility policy associated with the CoapTreeBuilder instance
	 * 
	 * @return the visibility policy associated with the CoapTreeBuilder
	 *         instance
	 */
	public VisibilityPolicy getVisibility() {
		return defaultVisibility;
	}

	/*
	 * InfoPath is a class used internally in the add method in order to
	 * simplify the handling of the path. The add method scan one piece of the
	 * path at a time, and at each iteration consider only a single piece, thus
	 * this class store the remainingPath, the piece of path being currently
	 * examined, and offers method to 'remove' the first piece of the path from
	 * the remaining path and making it the 'current' one.
	 */
	private class InfoPath {
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
			return (remainingPath == null) ? true : false;
		}

		public void removeFirst() {
			// removes the first '/', it will be add by CoapResource
			// setParent(),
			// see reference [1]
			remainingPath = remainingPath.substring(1);
			// is resourceName a simple name or a path?
			int separatorIndex = remainingPath.indexOf('/');

			if (separatorIndex > 0) {
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

	/*
	 * Method that check the correctness of the parameter. In case problems are
	 * detected 'false' is returned.
	 */
	private boolean parametersAreValid(String path) {

		if (path.matches("^./*$")) {
			return false;
		}
		return true;
	}

	/**
	 * Builds a subtree of resources resembling the given path, with the passed
	 * newResource as last resource of this path, using the CoapTreeBuilder
	 * VisibilityPolicy, and adds it as child of the root. The resource
	 * representing the final piece of the path must be built by the user and
	 * passed as argument to this function, that will take care of adding it as
	 * child of the resource representing the penultimate piece of the path.
	 * 
	 * @param path
	 *            The path containing the name of the resources to be created
	 *            or, if already existing, traversed.
	 * @param root
	 *            The resource starting from which the subtree will be built. If
	 *            you would like the subtree to start from the root of the
	 *            CoapServer, use
	 *            org.eclipse.californium.core.CoapServer.getRoot() as root.
	 */
	public boolean add(ActiveCoapResource newResource, String path) {
		return add(newResource, path, defaultVisibility);
	}

	/**
	 * Builds a subtree of resources resembling the given path, with the passed
	 * newResource as last resource of this path, using the given
	 * VisibilityPolicy, and adds it as child of the root. The resource
	 * representing the final piece of the path must be built by the user and
	 * passed as argument to this function, that will take care of adding it as
	 * child of the resource representing the penultimate piece of the path.
	 * 
	 * @param path
	 *            The path containing the name of the resources to be created
	 *            or, if already existing, traversed.
	 * @param root
	 *            The resource starting from which the subtree will be built. If
	 *            you would like the subtree to start from the root of the
	 *            CoapServer, use
	 *            org.eclipse.californium.core.CoapServer.getRoot() as root.
	 * @param vPolicy
	 *            The visibility policy. Possible values are:
	 *            VisibilityPolicy.ALL_VISIBLE or
	 *            VisibilityPolicy.ALL_INVISIBLE. If a CoapResource has to be
	 *            created, it will be created with the visibility specified by
	 *            this policy.
	 * @return true in case of correct creation, false otherwise.
	 */
	public synchronized boolean add(ActiveCoapResource newResource, String path,
			VisibilityPolicy vPolicy) {
		if (parametersAreValid(path) == false) {
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
		while (true) {
			iPath.removeFirst();
			System.out.println("[CoapTreeBuilder.add]: "
					+ "current is " + iPath.current 
					+ ", remainingPath is " + iPath.remainingPath);
			if (iPath.pathParsingFinished()) {
				// The user, during newResource creation, should have
				// set all its fields, like visibility, so the only
				// fields still to be set is name
				newResource.setName(iPath.current);
				// In order to avoid breaking the subtree, if a resource
				// with the same name of the resource is being created,
				// its children have to be moved to the new resource
				Resource toDelete = (currentFather.getChild(iPath.current));
				if (toDelete != null) {
					for (Resource child : toDelete.getChildren()) {
						newResource.add(child);
					}
					currentFather.delete(toDelete);
				}
				currentFather.add(newResource);
				return true;
			} else {
				// Does a resource with name resourceName already exists as
				// child of the Resource we are considering?
				Resource child = currentFather.getChild(iPath.getCurrent());

				if (child != null) {
					if (!(child instanceof CoapResource)) {
						System.err.println("Resource " + child.getName()
								+ " is not a CoapResource: its visibility"
								+ " will not be affected");
					} else {
						// The resource already exists. We may need to
						// change his visibility according to the policy
						handleExistingResource((CoapResource) child, vPolicy);
					}
					currentFather = child;
				} else {
					// The considered resource has to be created
					currentFather = handleResourceCreation(iPath.getCurrent(),
							currentFather, vPolicy);
				}
			}
		}
	}

	// Handle already existing resources along the path
	private void handleExistingResource(CoapResource child,
			VisibilityPolicy vPolicy) {
		System.out.println("traversing intermediate resource" + " "
				+ child.getName() + ", visibility " + child.isVisible()
				+ " and son of " + child.getParent().getName());
		// The intermediate resource was already there
		switch (vPolicy) {
		case ALL_VISIBLE:
			if (child.isVisible() == false) {
				child.setVisible(true);
			}
			break;
		case ALL_INVISIBLE:
			// nothing has to be done
			break;
		}
	}

	// Handle the creation of new ActiveCoapResources along the path
	private ActiveCoapResource handleResourceCreation(String resourceName,
			Resource father, VisibilityPolicy vPolicy) {
		// newResource is used to store the newly created resource
		ActiveCoapResource newResource = null;
		// Creation of an intermediate resource
		switch (vPolicy) {
		case ALL_VISIBLE:
			newResource = new ActiveCoapResource(resourceName, false, true);
			break;
		case ALL_INVISIBLE:
			newResource = new ActiveCoapResource(resourceName, false, false);
			break;
		}
		father.add(newResource);
		System.out.println("Created intermediate resource" + " " + resourceName
				+ ", visibility " + newResource.isVisible() + " and son of "
				+ father.getName());
		return newResource;
	}

	/**
	 * The remove method removes the passed child if possible. If the resource
	 * has some child, it is not deleted (if inactive) or is deleted and
	 * replaced with an inactive resource (if active). If the passed resource is
	 * deleted, and if his its parent is nor an active resource nor the root of
	 * the tree, the method is recursively called on the father resource.
	 * 
	 * @param child
	 *            The resource to be removed
	 */
	public synchronized void remove(ActiveCoapResource child) {
		if (child == null) {
			return;
		}
		// The root is created outside the CoapTreeBuilder, and outside
		// the CoapTreeBuilder must be deleted
		if (child.equals(root)) {
			return;
		}

		Resource parent = child.getParent();
		ActiveCoapResource activeParent;

		if (!child.isActive()) {
			// The resource is an inactive resource
			if (child.getChildren().isEmpty()) {
				/*
				 * If the resource is an inactive resource and it has no child,
				 * thus it can to be removed
				 */
				parent.delete(child);
				/*
				 * The method is called recursively on the parent only as
				 * long as it is a inactive resource.
				 * Even though the CoapTreeBuilder should be used with
				 * ActiveCoapResource only, it is not impossible for an user
				 * to manually adding Resource that are not ActiveCoapResource 
				 * to it. To avoid exceptions, we cannot simply perform
				 * ActiveCoapResource parent = (ActiveCoapResource)child.getParent(),
				 * we must first to the following check:
				 */
				if (parent instanceof ActiveCoapResource){
					activeParent = (ActiveCoapResource)parent;
					if (activeParent.isActive() == false) {
						// the parent is an inactive resource
						remove(activeParent);
					}
				}
			} else {
				/*
				 * If the resource is an inactive resource and it has some
				 * children, it must not be removed
				 */
				return;
			}
		} else {
			// The resource is an active resource
			if (child.getChildren().isEmpty()) {
				/*
				 * The resource is an active resource with no children, thus we
				 * can delete it and, iff the father is an inactive resource
				 */
				parent.delete(child);
				/*
				 * The method is called recursively on the parent only as
				 * long as it is a inactive resource.
				 * Even though the CoapTreeBuilder should be used with
				 * ActiveCoapResource only, it is not impossible for an user
				 * to manually adding Resource that are not ActiveCoapResource 
				 * to it. To avoid exceptions, we cannot simply perform
				 * ActiveCoapResource parent = (ActiveCoapResource)child.getParent(),
				 * we must first to the following check:
				 */
				if (parent instanceof ActiveCoapResource){
					activeParent = (ActiveCoapResource)parent;
					if (activeParent.isActive() == false) {
						// the parent is an inactive resource
						remove(activeParent);
					}
				}
			} else {
				/*
				 * The resource is an active resource with children, thus before
				 * deleting it we have to assign his children to a new resource.
				 * Then, we have to remove this resource from his parent and
				 * replace it with the newly created resource.
				 */
				boolean visibility = 
						(defaultVisibility == VisibilityPolicy.ALL_VISIBLE)
						? true : false;

				ActiveCoapResource newInactiveResource = new ActiveCoapResource(
						child.getName(), false, visibility);

				for (Resource son : child.getChildren()) {
					newInactiveResource.add(son);
				}

				parent.delete(child);
				parent.add(newInactiveResource);

			}
		}
	}
}
