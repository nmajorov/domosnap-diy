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

import java.text.MessageFormat;

import com.domosnap.engine.Log;
import com.domosnap.engine.Log.Session;
import com.domosnap.engine.adapter.core.ScanListener;
import com.domosnap.engine.controller.Controller;

public class ScanListerImpl implements ScanListener {

	private String protocole;
	private Log log = new Log(this.getClass().getSimpleName());
	
	public ScanListerImpl(String protocole) {
		this.protocole = protocole;
	}
	
	@Override
	public void foundController(Controller controller) {
		log.fine(Session.Device, MessageFormat.format("Device who[{0}], Where[{1}] found!", controller.getClass().getName(), controller.getWhere().getUri()));
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
