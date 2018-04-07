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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.domosnap.engine.Log;
import com.domosnap.engine.connector.ControllerService;
import com.domosnap.engine.connector.core.Command;
import com.domosnap.engine.connector.core.ConnectionListener;
import com.domosnap.engine.connector.core.ScanListener;
import com.domosnap.engine.connector.core.UnknownControllerListener;
import com.domosnap.engine.controller.Controller;
import com.domosnap.engine.controller.light.LightStateName;
import com.domosnap.engine.controller.where.Where;

import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.functions.Predicate;
import io.reactivex.processors.FlowableProcessor;
import io.reactivex.processors.PublishProcessor;

public class RaspberryControllerService implements ControllerService {

	private Log log = new Log();

	private RaspberryMonitorPublisher monitor;
	private Flowable<Command> monitorStream;
	
	private RaspberryCommanderConsumer commander;
	private PublishProcessor<Command> commanderStream;
	
	private Map<Class<?>, Map<Where, Controller>> controllerList = new ConcurrentHashMap<Class<?>, Map<Where,Controller>>();
//	private Map<Where, Controllerstream> controllers = new ConcurrentHashMap<Where,Controllerstream>();
	

	public RaspberryControllerService() {

	}

	@Override
	public <T extends Controller> List<T> createControllers(
			Class<T> clazz, Where... wheres) {
		List<T> list = new ArrayList<T>();
		for (Where where : wheres) {
			list.add(createController(clazz, where));
		}
		return list;
	}


	@Override
	public <T extends Controller> T createController(
			Class<T> clazz, Where where) {
		
		synchronized (controllerList) {
			Map<Where, Controller> map = controllerList.get(clazz);
			if (map == null) {
				map = new HashMap<Where, Controller>();
				controllerList.put(clazz,map);
			}

			@SuppressWarnings("unchecked")
			T controller = (T) map.get(where);
			
			if (controller == null) {
			
				try {
					controller = (T) clazz.newInstance();
					controller.addMonitor(getRaspberryMonitor());
					controller.addCommander(getRaspberryCommand());
					controller.setWhere(where);
					map.put(where, controller);
				} catch (InstantiationException e) {
					log.severe(this.getClass().getSimpleName(), "Error when instanciate the controller [" + where + "]. " + e.getMessage());
				} catch (IllegalAccessException e) {
					log.severe(this.getClass().getSimpleName(), "Error when instanciate the controller [" + where + "]. " + e.getMessage());
				}
			}
			return controller;
		}
	}
	
	private Flowable<Command> getRaspberryMonitor() {
		if (monitorStream == null) {
			monitor = new RaspberryMonitorPublisher();
			monitorStream = FlowableProcessor.create(this.monitor, BackpressureStrategy.DROP).share();
			log.finest(this.getClass().getSimpleName(), monitor.getFormattedLog(null, 0, "Monitor created."));
		}

		return monitorStream;
	}
	
	private PublishProcessor<Command> getRaspberryCommand() {
		if (commanderStream == null) {
			commanderStream = PublishProcessor.create();
			
			this.commander = new RaspberryCommanderConsumer();
			commanderStream.filter(new Predicate<Command>() {
				@Override
				public boolean test(Command command) throws Exception {
					// Only on status.... => since only it get all status
					return LightStateName.status.name().equals(command.getWhat().getName());
				}
			}).subscribe(this.commander);	
			
//			log.finest(this.getClass().getSimpleName(), commander.getFormattedLog(null, 0, "Commander created."));
		}
		return commanderStream;
	}

	
	@Override
	public void disconnect() {
		if (monitor != null) {
			monitor.disconnect();
		}

		if ( commander != null) {
//			commander.disconnect();
		}
	}

	/**
	 * Return true if all commander are connected (else false)
	 * @return true if all commander are connected (else false)
	 */
	@Override
	public boolean isConnected() {
		return commander.isConnected() && monitor.isConnected();
	}

	

	@Override
	public void addCommanderConnectionListener(ConnectionListener listener) {
		commander.addConnectionListener(listener);
	}

	@Override
	public void removeCommanderConnectionListener(ConnectionListener listener) {
		commander.removeConnectionListener(listener);
	}
	
	@Override
	public void addMonitorConnectionListener(ConnectionListener listener) {
		monitor.addConnectionListener(listener);
		
	}

	@Override
	public void removeMonitorConnectionListener(ConnectionListener listener) {
		monitor.removeConnectionListener(listener);
		
	}

	@Override
	public void addUnknowControllerListener(UnknownControllerListener arg0) {
//		TODO monitor.addUnknownControllerListener(arg0);
	}
	
	@Override
	public void removeUnknowControllerListener(UnknownControllerListener arg0) {
//		TODO this.getHueMonitor().removeUnknownControllerListener(arg0);
	}
	
	@Override
	public void connect() {
		if (!commander.isConnected()) {
			commander.connect();
		}
		if (!monitor.isConnected()) {
			monitor.connect();
		}
	}


	@Override
	public void scan(ScanListener listener) {
		// TODO ici scanner les gpio! et voir si on a un retour
		
	}
}
