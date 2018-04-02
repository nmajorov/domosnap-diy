package com.domosnap.engine.connector.impl.raspberry;

/*
 * #%L
 * DomoSnapRaspberryConnector
 * %%
 * Copyright (C) 2016 - 2018 A. de Giuli
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.domosnap.engine.Log;
import com.domosnap.engine.Log.Session;
import com.domosnap.engine.connector.core.Command;
import com.domosnap.engine.connector.core.ConnectionListener;
import com.domosnap.engine.controller.light.LightStateName;
import com.domosnap.engine.controller.what.impl.OnOffState;
import com.domosnap.engine.controller.who.Who;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;

import io.reactivex.functions.Consumer;

public class RaspberryCommanderConsumer implements Consumer<Command> {

	protected final Log log = new Log(this.getClass().getSimpleName());
    private GpioController gpio;
	private List<ConnectionListener> connectionListenerList = Collections
			.synchronizedList(new ArrayList<ConnectionListener>());
    
    
	public RaspberryCommanderConsumer() {
	    
	}

	@Override
	public void accept(Command command) {
		if (command == null) {
//			log.severe(Session.Monitor, "Command unsupported (null).");
			return;
		}
			
		GpioPinDigitalOutput pin = gpio.provisionDigitalOutputPin(RaspiPin.getPinByName(command.getWhere().getTo()), command.getWhere().getFrom());
	    pin.setShutdownOptions(true, PinState.LOW);
	    
		if (Who.LIGHT.equals(command.getWho())) {
			if (LightStateName.status.name().equals(command.getWhat().getName())) {
				if(OnOffState.On().equals(command.getWhat().getValue())) {
					// turn off gpio pin #01
					pin.high();
					System.out.println("--> GPIO state should be: ON");
				} else {
					// turn off gpio pin #01
			        pin.low();
			        System.out.println("--> GPIO state should be: OFF");
				}
			}
		}
	}

	/**
	 * Open the connection. If connection is not possible it will try again
	 * later:
	 * <li>
	 * <ul>
	 * after some time for monitor
	 * </ul>
	 * <ul>
	 * at the next command for commander
	 * </ul>
	 * </li>
	 * 
	 * @return
	 */
	public boolean connect() {
		// Make connection in thread to avoid blocking the user!
		gpio = GpioFactory.getInstance();
		return true;
	}

	/**
	 * Close the client and stop the thread.<br/>
	 * No more connection will be try before method connect will be call again.
	 */
	public void disconnect() {
		closeConnection();
	}

	private void closeConnection() {
		gpio.shutdown();
		gpio = null;

	}

	public boolean isConnected() {
		if (gpio == null || gpio.isShutdown()) {
			return false;
		} else {
			return true;
		}
	}

	public void addConnectionListener(ConnectionListener connectionListener) {
		if (!connectionListenerList.contains(connectionListener)) {
			connectionListenerList.add(connectionListener);
		}
	}

	public void removeConnectionListener(ConnectionListener connectionListener) {
		connectionListenerList.remove(connectionListener);
	}


	


	private static int countConnection = 0;
	private static int countSession = 0;
	private static Map<Integer, String> mapSessionInstance = new ConcurrentHashMap<Integer, String>();
	private static Map<Integer, String> mapConnectionInstance = new ConcurrentHashMap<Integer, String>();

	public String getFormattedLog(Object object, int level, String msg) {

		String connectionNum = "Connection null";
		if (object != null) {
			if (mapConnectionInstance.containsKey(object.hashCode())) {
				connectionNum = mapConnectionInstance.get(object.hashCode());
			} else {
				connectionNum = "Connection " + pad(countConnection++, 4);
				mapConnectionInstance.put(object.hashCode(), connectionNum);
			}
		}

		String sessionNum;
		if (mapSessionInstance.containsKey(this.hashCode())) {
			sessionNum = mapSessionInstance.get(this.hashCode());
		} else {
			sessionNum = Session.Command.name() + pad(countSession++, 5);
			mapSessionInstance.put(this.hashCode(), sessionNum);
		}

		StringBuilder sb = new StringBuilder();
		sb.append("[").append(sessionNum).append("]-");
		sb.append("[").append(connectionNum).append("] : ");

		for (int i = 0; i < level; i++) {
			sb.append("   ");
		}

		sb.append(msg);
		return sb.toString();

	}

	private String pad(int i, int pad) {
		String s = "" + i;
		while (s.length() < pad) {
			s = " ".concat(s);
		}
		return s;
	}
}
