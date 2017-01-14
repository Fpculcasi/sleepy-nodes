/*******************************************************************************
 * Copyright (c) 2014 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 ******************************************************************************/
package org.eclipse.californium.proxy;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;


public class GETClient {

	/*
	 * Application entry point.
	 * 
	 */	
	public static void main(String args[]) {

		URI uri = null;

		try{
			uri = new URI("coap://[aaaa::212:7402:2:202]:5683/hello");
			//uri = new URI("coap://[aaaa::c30c:0:0:3]:5683/hello");
		} catch (Exception e) {
 			System.err.println("Caught Exception: " + e.getMessage());
		}	 
	
        	CoapClient client = new CoapClient(uri);

		CoapResponse response = client.get();
			
		if (response!=null) {
				
			System.out.println(response.getCode());
			System.out.println(response.getOptions());
			System.out.println(response.getResponseText());
				
			System.out.println("\nADVANCED\n");
			// access advanced API with access to more details through .advanced()
			System.out.println(Utils.prettyPrint(response));
				
		} else {
			System.out.println("No response received.");
		}
	}

}
