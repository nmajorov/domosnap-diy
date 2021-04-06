package com.domosnap.diy.uv;

import com.domosnap.core.adapter.i2c.I2CControllerService;

/*
 * #%L
 * uv-scanner
 * %%
 * Copyright (C) 2017 - 2021 A. de Giuli
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

import com.domosnap.engine.controller.Controller;
import com.domosnap.engine.controller.ControllerChangeListener;
import com.domosnap.engine.controller.uv.UvSensor;
import com.domosnap.engine.controller.what.What;
import com.domosnap.engine.controller.what.impl.IntegerState;
import com.domosnap.engine.controller.where.Where;
import com.domosnap.engine.event.EventFactory;
import com.domosnap.engine.event.EventToConsoleConsumer;

import io.vertx.core.AbstractVerticle;

public class UVScannerMicroservice extends AbstractVerticle {
	
	
//	private static int LOW = 0;
	private static int MODERATE = 2241;
	private static int HIGH = 4482;
	private static int VERY_HIGH = 5976;
	private static int EXTREME = 8217;
	
	
	@Override
	public void start() {

//		Log.debug = false;
//		Log.error = false;
//		Log.fine = false;
//		Log.finest = false;
//		Log.info = false;
//		Log.logCommand = false;
//		Log.logMonitor = false;
//		Log.warning = false;
		
		

		EventFactory.addConsumer(new EventToConsoleConsumer());

		I2CControllerService i2c = new I2CControllerService(null);
		i2c.connect();
		UvSensor sensor = i2c.createController(UvSensor.class, new Where("i2c://localhost/veml6070/38"));
		sensor.addControllerChangeListener(new ControllerChangeListener() {
			
			@Override
			public void onStateChangeError(Controller controller, What oldStatus, What newStatus) {
			}
			
			@Override
			public void onStateChange(Controller controller, What oldStatus, What newStatus) {
				
				int uv = ((IntegerState) newStatus.getValue()).getIntValue();
				
				if (uv < MODERATE) {
					// LOW
					System.out.println("Low risk (" + uv + ").");
				} else if (uv < HIGH) {
					// MODERATE
					System.out.println("Moderate risk (" + uv + ").");
				} else if (uv < VERY_HIGH) {
					// HIGH
					System.out.println("High risk (" + uv + ").");
				} else if (uv < EXTREME) {
					// VERY HIGH
					System.out.println("Very high risk (" + uv + ").");
				} else {
					// EXTREME
					System.out.println("Extreme risk (" + uv + ").");
				}
			}
		});
		System.out.println("UV Service Started");
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		System.out.println("Prove Service Stopped");
	}

	public static void main(String[] args) {
		io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
		vertx.deployVerticle(UVScannerMicroservice.class.getName());
	}
}
