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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.domosnap.engine.Log;
import com.domosnap.engine.Log.Session;
import com.domosnap.engine.connector.core.Command;
import com.domosnap.engine.connector.core.Command.Type;
import com.domosnap.engine.connector.core.ConnectionListener;
import com.domosnap.engine.controller.light.LightStateName;
import com.domosnap.engine.controller.what.What;
import com.domosnap.engine.controller.what.impl.OnOffState;
import com.domosnap.engine.controller.where.Where;
import com.domosnap.engine.controller.who.Who;
import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinDigitalStateChangeEvent;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;

import io.reactivex.FlowableEmitter;
import io.reactivex.FlowableOnSubscribe;

public class RaspberryMonitorPublisher implements FlowableOnSubscribe<Command> {

	private List<FlowableEmitter<Command>> suscriberList = Collections.synchronizedList(new ArrayList<FlowableEmitter<Command>>());
	private GpioPinDigitalInput led;
    private GpioController gpio;
	protected final Log log = new Log(this.getClass().getSimpleName());

	public RaspberryMonitorPublisher() {
		connect();
	}


	void onMessageReceipt(GpioPinDigitalStateChangeEvent event) {
		try {
			for (FlowableEmitter<Command> suscriber : suscriberList) {
				Command command;
				if (event.getState().isHigh()) {
					command = new Command(Who.LIGHT, new What(LightStateName.status.name(), OnOffState.On()), new Where("GPIO_01", "GPIO_01"), Type.ACTION, null);
				} else {
					command = new Command(Who.LIGHT, new What(LightStateName.status.name(), OnOffState.Off()), new Where("GPIO_01", "GPIO_01"), Type.ACTION, null);
				}
				
				suscriber.onNext(command);
			}
		} catch (Exception e) {
			log.severe(Session.Command, "Exception occurs with message ["
					+ String.valueOf(event) + "]. Message dropped. " + e.getMessage());
		}
	}

	private class MonitorHandler implements Runnable {

		@Override
		public void run() {
			System.out.println("<--Pi4J--> GPIO Listen Example ... started.");

	        // create gpio controller = ici prendre les controllers que l'on a déclarer => pas de unknow supporté ici!

	        // provision gpio pin #02 as an input pin with its internal pull down resistor enabled
	        led = gpio.provisionDigitalInputPin(RaspiPin.GPIO_01, PinPullResistance.PULL_DOWN);

	        // set shutdown state for this input pin
	        led.setShutdownOptions(true);

	        // create and register gpio pin listener
	        led.addListener(new GpioPinListenerDigital() {
	            @Override
	            public void handleGpioPinDigitalStateChangeEvent(GpioPinDigitalStateChangeEvent event) {
	                // display pin state on console
	                System.out.println(" --> GPIO PIN STATE CHANGE: " + event.getPin() + " = " + event.getState());
	                synchronized (lock) { // mutex on the main thread: only one connection or send message at the same time!
	                onMessageReceipt(event);
	                }
	            }

	        });

	        System.out.println(" ... complete the GPIO #02 circuit and see the listener feedback here in the console.");
			
			
			
			
			 
			 
			// keep program running until user aborts (CTRL-C)
		        while(forceConnection) {
		            try {
						Thread.sleep(500);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		        }
		}
	}

	@Override
	public void subscribe(FlowableEmitter<Command> e) throws Exception {
		suscriberList.add(e);
	}
	
	
	
	protected final Object lock = new Object();
	protected final Object lockPool = new Object();
	protected ExecutorService pool = null;
	protected boolean forceConnection = true;
	private final int poolSize = 5;
	private List<ConnectionListener> connectionListenerList = Collections
			.synchronizedList(new ArrayList<ConnectionListener>());
	private Log.Session session;



	
	public boolean connect() {
		forceConnection = true;
		getExecutorService().execute(new MonitorHandler());
		return isConnected();
	}

	/**
	 * Close the client and stop the thread.<br/>
	 * No more connection will be try before method connect will be call again.
	 */
	public void disconnect() {
		forceConnection = false;
		closeConnection();
	}

	private void closeConnection() {
		gpio.shutdown();
		gpio = null;
		led = null;
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

	protected ExecutorService getExecutorService() {
		synchronized (lockPool) {
			if (pool == null) {
				pool = Executors.newFixedThreadPool(poolSize);
			}
		}

		return pool;
	}

	
//
//		private void callOpenWebConnectionListenerConnect(ConnectionStatusEnum connection) {
//			synchronized (connectionListenerList) {
//				for (ConnectionListener connectionListener : connectionListenerList) {
//					try {
//						connectionListener.onConnect(connection);
//					} catch (Exception e) {
//						log.severe(this.getClass().getSimpleName(), getFormattedLog(client, 1,
//								"ConnectionListener raise an error [" + e.getMessage() + "]"));
//					}
//				}
//			}
//		}
	

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
			sessionNum = session.name() + pad(countSession++, 5);
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
