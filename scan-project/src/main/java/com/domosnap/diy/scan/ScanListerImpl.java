package com.domosnap.diy.scan;

/*
 * #%L
 * scan-project
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

import java.text.MessageFormat;
import java.util.Map;

import com.domosnap.engine.Log;
import com.domosnap.engine.Log.Session;
import com.domosnap.engine.adapter.discovery.device.ScanListener;
import com.domosnap.engine.controller.Controller;
import com.domosnap.engine.controller.what.State;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

public class ScanListerImpl implements ScanListener {

	private String protocole;
	private Log log = new Log(this.getClass().getSimpleName());
	private WebClient client;
	
	public ScanListerImpl(Vertx vertx, String protocole) {
		this.protocole = protocole;
		 
		client = WebClient.create(vertx);
	}
	
	@Override
	public void foundController(Controller controller) {
		log.fine(Session.Device, MessageFormat.format("Device who[{0}], Where[{1}] found!", controller.getClass().getName(), controller.getWhere().getUri()));
		HttpRequest<Buffer> request =  client.post("192.168.1.61", "/smartdevice/devices");
		request.bearerTokenAuthentication("eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJuYW1lIjoiT2xpdmllciBEcmllc2JhY2giLCJwaWN0dXJlIjoiaHR0cHM6Ly9saDMuZ29vZ2xldXNlcmNvbnRlbnQuY29tLy1CSVJmT3J5dThVYy9BQUFBQUFBQUFBSS9BQUFBQUFBQUFBQS9BQ0hpM3JlSUVVd21VSldqbTNoWmlFRXZCNjlJb2N1OHN3L3M5Ni1jL3Bob3RvLmpwZyIsImlzcyI6Imh0dHBzOi8vc2VjdXJldG9rZW4uZ29vZ2xlLmNvbS9jdXJhbnRpLW12cCIsImF1ZCI6ImN1cmFudGktbXZwIiwiYXV0aF90aW1lIjoxNTYyMTUyMzU2LCJ1c2VyX2lkIjoibjVNSEcyVEx1cVliemM4QXJoNkx3VkxmaVp1MiIsInN1YiI6Im41TUhHMlRMdXFZYnpjOEFyaDZMd1ZMZmladTIiLCJpYXQiOjE1NjIxNTIzNTYsImVtYWlsIjoib2xpdmllci5kcmllc2JhY2hAY3VyYW50aS5jb20iLCJlbWFpbF92ZXJpZmllZCI6dHJ1ZSwiZmlyZWJhc2UiOnsiaWRlbnRpdGllcyI6eyJnb29nbGUuY29tIjpbIjExNTI3NDUyODg1NjQ2NDY0NDA1MiJdLCJlbWFpbCI6WyJvbGl2aWVyLmRyaWVzYmFjaEBjdXJhbnRpLmNvbSJdfSwic2lnbl9pbl9wcm92aWRlciI6Imdvb2dsZS5jb20ifX0.OLgwhtHzjhVmJV9U_lVCWkyPlRem5tyK8BIdRw2y7nDQzAkoh9sj2O_7uND7D6qSSgW9FQyEpXSdHfXE-YVYO6yHR_IJAt7ATawVtl4krVuKevDGOC6aVHncbfvYENvs-ADCWKYQ6-teEwC99S7_1iwcD5okuH4BQ2aOQe4XW7-7qVdhDHw0Q3BzPe9wlEoVqS1vebmxXBW-Rgt4Z-1LcMzYKVm5YM2Uiw-vibxAFRD1QbM1pm-3uQYlFrpHy5ksiuh4YoaIeZKiJKZNOEOzJ-XmdtvYwIWmtjZ5gI2iol75kQNJ00A2aF7H2pN90v6DTTxvaAT67-Xoc8stYx1XUA");
		request.putHeader("Content-Type", "application/json");
		request.putHeader("idPartner", "1");
		
		String smartdevice = "{\"uri\":\"" + controller.getWhere() + "\",\"type\":\"Light\"," //+ controller.getClass().getName() + "\","
				+ "\"active\":true, \"measures\": [";
		
		Map<String, State<?>> stateMap = controller.getStateMap();
		
		boolean first = true;
		for (String stateKey : controller.getStateList()) {

			if (first) {
				first = false;
			} else {
				smartdevice = smartdevice.concat(",");
			}

			State<?> state = stateMap.get(stateKey);
			if (state != null) {
				smartdevice = smartdevice.concat("{\"key\":\"" + stateKey + "\",\"type\":\"" + state.getClass().getName() + "\", \"description\": \"description...\"}");
			} else {
				smartdevice = smartdevice.concat("{\"key\":\"" + stateKey + "\",\"type\":\"unknown\", \"description\": \"description...\"}");
			}
		}
		
		smartdevice = smartdevice.concat("]}"); // TODO apply a parse on the getWhere!!!
		JsonObject jo = new JsonObject(smartdevice);
		System.out.println(jo.toString());
		request.sendJson(jo, response -> System.out.println(response.result().body()));
		
	}

	@Override
	public void progess(int percent) {
		log.fine(Session.Device, MessageFormat.format("Protocole {0} scan progress [{1}%].", protocole, percent));
	}

	@Override
	public void scanFinished() {
		log.fine(Session.Device, MessageFormat.format("Protocole {0} scan finish.", protocole));
	}
}
