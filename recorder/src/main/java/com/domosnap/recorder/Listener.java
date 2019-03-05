package com.domosnap.recorder;

/*
 * #%L
 * DomoSnapRecorder
 * %%
 * Copyright (C) 2017 - 2019 A. de Giuli
 * %%
 * This file is part of HomeSnap done by A. de Giuli (arnaud.degiuli(at)free.fr).
 * 
 *     MyDomo is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     MyDomo is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with MyDomo.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import java.text.MessageFormat;

import com.domosnap.engine.adapter.core.Command;
import com.domosnap.engine.adapter.core.UnknownControllerListener;
import com.domosnap.engine.adapter.impl.openwebnet.OpenWebNetControllerAdapter;
import com.domosnap.engine.controller.what.What;

public class Listener {


	public static void main(String[] args) {
		OpenWebNetControllerAdapter monitor = new OpenWebNetControllerAdapter("openwebnet://12345@192.168.1.35:20000");
		monitor.addUnknowControllerListener(new UnknownControllerListener() {
			@Override
			public void foundUnknownController(Command command) {
				try {
					for (What what : command.getWhatList()) {
						String whereStr = command.getWhere() != null ? command.getWhere().getPath() : "null";
						String whatStr = what == null ? "null": what.getName() == null ? "null" : what.getName();
						String valueStr = what == null ? "null": what.getValue() == null ? "null" : what.getValue().toString();
						
						System.out.println(MessageFormat.format("Who [{0}] : Where [{1}] : what [{2}] : value [{3}]\n", command.getWho(), whereStr, whatStr, valueStr));
					}
				} catch (Exception e) {
					e.printStackTrace();
				}				
			}
		});
		monitor.connect();
	}
}
