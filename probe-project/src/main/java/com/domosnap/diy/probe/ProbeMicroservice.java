package com.domosnap.diy.probe;

/*
 * #%L
 * probe-project
 * %%
 * Copyright (C) 2017 - 2020 A. de Giuli
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
import com.domosnap.engine.adapter.impl.openwebnet.OpenWebNetDiscoveryService;
import com.domosnap.engine.event.EventFactory;
import com.domosnap.engine.event.EventToFileConsumer;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.HealthChecks;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;

public class ProbeMicroservice extends AbstractVerticle {
	
	private static final String FILE_PATH_KEY = "file.path";
	private static final String LOG_ENABLE = "log.enable";
	
	@Override
	public void start() {

		Log.getConfig().put("all", config().getValue(LOG_ENABLE, "false"));
		
		String path = config().getString(FILE_PATH_KEY, "/opt/probe/logs");
		
		EventFactory.addConsumer(new EventToFileConsumer(path));
		
//		MqttClientOptions opt = new MqttClientOptions();
//		opt.setMaxInflightQueue(100);
//		EventFactory.addConsumer(new EventToMqttConsumer("env-5291014.hidora.com", 11112, opt, getVertx(), null, true));

		OpenWebNetDiscoveryService own = new OpenWebNetDiscoveryService("scs://12345@192.168.1.35:20000");
		
		vertx.executeBlocking(future -> {
			own.connect();
			own.scan(new ScanListerImpl("scs"));
		}, null);
		
		
//		OneWireDiscoveryDeviceService owa = new OneWireDiscoveryDeviceService();
//		owa.connect();
//		owa.scan(new ScanListerImpl("OneWire"));
//		
//		I2CDiscoveryDeviceService i2c = new I2CDiscoveryDeviceService(null);
//		i2c.connect();
//		i2c.scan(new ScanListerImpl("I2C"));
//		
		
		// Create healthcheck
		HealthChecks hc = HealthChecks.create(vertx);

		// Register with a timeout. The check fails if it does not complete in time.
		// The timeout is given in ms.
		hc.register("ownCheckHealth", 200, future -> {
			future.complete(Status.OK());
		});
		Router router = Router.router(vertx);
		// Register the health check handler
		router.get("/health*").handler(HealthCheckHandler.createWithHealthChecks(hc));
		vertx.createHttpServer().requestHandler(router).listen(8080, ar -> {
			System.out.println("Health started on port " + ar.result().actualPort());
		});
		
		System.out.println("Probe Service Started");
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		System.out.println("Prove Service Stopped");
	}

	public static void main(String[] args) {
//		io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
		Vertx vertx = Vertx.vertx();
		vertx.deployVerticle(ProbeMicroservice.class.getName());
	}
}
