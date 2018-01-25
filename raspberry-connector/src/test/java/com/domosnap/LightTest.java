package com.domosnap;

/*
 * #%L
 * DomoSnapRaspberry
 * %%
 * Copyright (C) 2011 - 2017 A. de Giuli
 * %%
 * This file is part of HomeSnap done by Arnaud de Giuli (arnaud.degiuli(at)free.fr)
 *     helped by Olivier Driesbach (olivier.driesbach(at)gmail.com).
 * 
 *     HomeSnap is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     HomeSnap is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with HomeSnap. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import org.junit.Assert;

import com.domosnap.engine.connector.ControllerService;
import com.domosnap.engine.connector.impl.raspberry.RaspberryControllerService;
import com.domosnap.engine.controller.Controller;
import com.domosnap.engine.controller.ControllerChangeListener;
import com.domosnap.engine.controller.light.Light;
import com.domosnap.engine.controller.what.What;
import com.domosnap.engine.controller.what.impl.OnOffState;
import com.domosnap.engine.controller.where.Where;

public class LightTest {

	private static ControllerService s = new RaspberryControllerService();
	private static Object lock = new Object();
	private static Object lock2 = new Object();
	
	public static void main(String[] args) {
		
		final Light light = s.createController(Light.class, new Where("12","12"));
		
		// Listener will make us availabe to wait response from server
		light.addControllerChangeListener(new ControllerChangeListener() {

			@Override
			public void onStateChangeError(Controller controller,
					What oldStatus, What newStatus) {
				synchronized (lock) {
					// When response from server is here we unlock the thread
					System.out.println("Unlock...");
					lock.notify();
				}
			}
			
			@Override
			public void onStateChange(Controller controller,
					What oldStatus, What newStatus) {
				synchronized (lock) {
					// When response from server is here we unlock the thread
					System.out.println("Unlock...");
					lock.notify();
				}
			}
		});

		// First we just wait 1 second to be sure the controller is initialize 
		try {
			synchronized (lock2) {
				
				lock2.wait(2000);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		// By default simulation server send back a ON status (light 12 only). If value == null, it is a bug or just server have not enough time (1 second) to respond
		Assert.assertNotNull(light.getStatus());
		Assert.assertEquals(OnOffState.On().getValue() , light.getStatus().getValue());

		// Now set the value to OFF
		light.setStatus(OnOffState.Off());
		// Wait the response from the server
		try {
			synchronized (lock) {
				System.out.println("Wait...");	
				lock.wait();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		// Check that after the server response now the status is OFF
		Assert.assertNotNull(light.getStatus());
		Assert.assertEquals(OnOffState.Off().getValue() , light.getStatus().getValue());

		// Switch on again
		light.setStatus(OnOffState.On());
		try {
			synchronized (lock) {
				System.out.println("Wait...");
				lock.wait();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		
		Assert.assertNotNull(light.getStatus());
		Assert.assertEquals(OnOffState.On().getValue() , light.getStatus().getValue());
		
		System.out.println("Finish...");

	}
}
