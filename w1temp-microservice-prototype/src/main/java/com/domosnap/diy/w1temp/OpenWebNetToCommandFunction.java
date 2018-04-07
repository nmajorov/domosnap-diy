package com.domosnap.diy.w1temp;

/*
 * #%L
 * w1temp-microservice-prototype
 * %%
 * Copyright (C) 2017 - 2018 A. de Giuli
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
import java.util.HashMap;
import java.util.List;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import com.domosnap.engine.Log;
import com.domosnap.engine.Log.Session;
import com.domosnap.engine.connector.core.Command;
import com.domosnap.engine.connector.core.Command.Type;
import com.domosnap.engine.connector.core.UnSupportedStateException;
import com.domosnap.engine.connector.core.UnknownControllerListener;
import com.domosnap.engine.connector.core.UnknownStateException;
import com.domosnap.engine.connector.core.UnknownStateValueException;
import com.domosnap.engine.connector.core.UnknownWhoException;
import com.domosnap.engine.connector.impl.openwebnet.conversion.core.OpenWebNetCommand;
import com.domosnap.engine.connector.impl.openwebnet.conversion.core.parser.ParseException;
import com.domosnap.engine.connector.impl.openwebnet.conversion.core.parser.TokenMgrError;
import com.domosnap.engine.controller.Controller;
import com.domosnap.engine.controller.what.State;
import com.domosnap.engine.controller.what.What;
import com.domosnap.engine.controller.where.Where;
import com.domosnap.engine.house.Label;

import io.reactivex.functions.Function;

public class OpenWebNetToCommandFunction implements Function<String, Publisher<Command>> {

	private Log log = new Log();
	
	private List<UnknownControllerListener> unknownControllerListenerList = Collections.synchronizedList(new ArrayList<UnknownControllerListener>());
	private List<Controller> controllerList = Collections.synchronizedList(new ArrayList<Controller>());
	
	
	@Override
	public Publisher<Command> apply(final String message) throws Exception {

			return new Publisher<Command>() {
				@Override
				public void subscribe(Subscriber<? super Command> arg0) {
					try {
						OpenWebNetCommand command = new OpenWebNetCommand(message);

						if (!(command.isDimensionCommand() || command.isStandardCommand())) {
							log.severe(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Message received [" + message +"] don't match with standard command or dimension command."));
							return;
						}

						boolean known = false;
						// TODO manage message on other bus
						if (command.isGeneralCommand()) {
							// We send command to all correct address
							for (int i = 11; i < 100; i++) {
								if (i % 10 != 0) { // group address (20, 30, ..) are not correct
									known |= updateController(new Where(""+i, ""+i), command, arg0);
								}
							}
						} else if (command.isGroupCommand()) {
							// We send command to group address
							synchronized (controllerList) {
								for (Controller controller : controllerList) {
									for (Label label : controller.getLabels()) {
										if (label.getId().equals(command.getGroup()) &&
												controller.getWho().equals(command.getWho())) {
											known = true;
											if (command.isStandardCommand()) {
												for(What what : command.getWhat(controller.getStateMap())) {
													Command c = new Command(command.getWho(), what, command.getWhere(), Type.ACTION, controller);
													arg0.onNext(c);
												}
											} else {
												for(What what : command.getDimension(controller.getStateMap())) {
													Command c = new Command(command.getWho(), what, command.getWhere(), Type.ACTION, controller);
													arg0.onNext(c);
												}
											}
											log.finest(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Message ["+ command.toString() + "] forwarded to controller."));
										}
									}
								}
							}
						} else if (command.isEnvironmentCommand()) {
							String environment = command.getEnvironment();
							// We send ambiance command to address
							for (int i = 1; i < 10; i++) {
								known |= updateController(new Where(environment + i, environment + i), command, arg0);
							}
						} else {
							// Command direct on a controller
							known = updateController(command.getWhere(), command, arg0);
						}

						if (!known) {
							// Detected unknown device
							log.finest(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Unknown controller detected [" +command.toString() + "]."));
							synchronized (unknownControllerListenerList) {
								for (UnknownControllerListener listener : unknownControllerListenerList) {
									try {
										if (command.isStandardCommand()) {
											listener.foundUnknownController(command.getWho(), command.getWhere(), command.getWhat(null));
										} else {
											listener.foundUnknownController(command.getWho(), command.getWhere(), command.getDimension(null));
										}
										
									}catch (Exception e) {
										log.warning(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Error with a listener when found an unknown controller on message [" + message +"]. Message dropped."));
									}
								}
								for(What what : command.getWhat(new HashMap<String, State<?>>())) {
									Command c = new Command(command.getWho(), what, command.getWhere(), Type.ACTION, null);
									log.finest(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Message ["+ command.toString() + "] transformed to command [" + c + "]." ));
									arg0.onNext(c);
								}
							}
						}
				} catch (ParseException e) {
					log.warning(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Unknown message received [" + message +"]. Message dropped."));
				} catch (UnknownStateException e) {
					log.warning(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Unknown state received [" + message +"]. Message dropped."));
				} catch (UnknownWhoException e) {
					log.warning(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Unknown who received [" + message +"]. Message dropped."));
				} catch (UnSupportedStateException e) {
					log.warning(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "UnSupportedState ["+ message + "]. Message dropped."));
				} catch (UnknownStateValueException e) {
					log.warning(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "UnknowStateValue ["+ message + "]. Message dropped."));
				} catch (TokenMgrError e) {
					log.severe(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Received unexpected message ["+ message + "]. Message dropped but probably there is a serious problem with protocol implementation. "));
					throw e;
				} catch (Exception e) {
					log.severe(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Exception occurs with message [" + message +"]. Message dropped. " +e.getMessage()));
				}
			}
		};
	} 
	
	
	public void addUnknownControllerListener(UnknownControllerListener unknownControllerListener) {
		if (!unknownControllerListenerList.contains(unknownControllerListener)) {
			unknownControllerListenerList.add(unknownControllerListener);
		}
	}
	
	public void removeUnknownControllerListener(UnknownControllerListener unknownControllerListener) {
		unknownControllerListenerList.remove(unknownControllerListener);
	}

	
	public void addKnownController(Controller controller) {
		controllerList.add(controller);
	}
	
	private boolean updateController(Where where, OpenWebNetCommand command, Subscriber<? super Command> arg0) throws UnknownStateException, UnknownWhoException, UnSupportedStateException, UnknownStateValueException {
		boolean known = false;

		// Manage what command
		synchronized (controllerList) {
			for (Controller controller : controllerList) {
				if (command.getWho().equals(controller.getWho()) && where.getFrom().equals(controller.getWhere().getFrom())) {
					known = true;
					
					if (command.isStandardCommand()) {
						for(What what : command.getWhat(controller.getStateMap())) {
							Command c = new Command(command.getWho(), what, command.getWhere(), Type.ACTION, controller);
							log.finest(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Message ["+ command.toString() + "] transformed to command [" + c + "]." ));
							arg0.onNext(c);
						}
					} else {
						for(What what : command.getDimension(controller.getStateMap())) {
							Command c = new Command(command.getWho(), what, command.getWhere(), Type.ACTION, controller);
							log.finest(this.getClass().getSimpleName(), log.getFormattedLog(Session.Monitor, Thread.currentThread().getName(), 1, "Message ["+ command.toString() + "] transformed to command [" + c + "]." ));
							arg0.onNext(c);
						}
					}

					break;
				}
			}
		}
		return known;
	}
}
