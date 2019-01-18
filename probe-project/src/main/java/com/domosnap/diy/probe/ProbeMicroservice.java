package com.domosnap.diy.probe;

/*
 * #%L
 * probe-project
 * %%
 * Copyright (C) 2017 - 2019 A. de Giuli
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

import com.domosnap.engine.Log;
import com.domosnap.engine.adapter.i2c.I2CControllerAdapter;
import com.domosnap.engine.adapter.onewire.OneWireControllerAdapter;
import com.domosnap.engine.event.EventFactory;
import com.domosnap.engine.event.EventToConsoleConsumer;

import io.vertx.core.AbstractVerticle;

public class ProbeMicroservice extends AbstractVerticle {
	
	@Override
	public void start() {

		Log.debug = false;
		Log.error = false;
		Log.fine = false;
		Log.finest = false;
		Log.info = false;
		Log.logCommand = false;
		Log.logMonitor = false;
		Log.warning = false;
		
		EventFactory.addConsumer(new EventToConsoleConsumer());

		OneWireControllerAdapter owa = new OneWireControllerAdapter();
		owa.connect();
		owa.scan(new ScanListerImpl("OneWire"));
		
		I2CControllerAdapter i2c = new I2CControllerAdapter();
		i2c.connect();
		i2c.scan(new ScanListerImpl("I2C"));
		
		System.out.println("Probe Service Started");
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		System.out.println("Prove Service Stopped");
	}

	public static void main(String[] args) {
		io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
		vertx.deployVerticle(ProbeMicroservice.class.getName());
	}
}
