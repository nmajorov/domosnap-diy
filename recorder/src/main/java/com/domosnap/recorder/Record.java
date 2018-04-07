package com.domosnap.recorder;

/*
 * #%L
 * DomoSnapRecorder
 * %%
 * Copyright (C) 2017 - 2018 A. de Giuli
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

import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;


public class Record {
	
	private String PROBE_ID_KEY= "probeID";
	private String VALUE_KEY= "value";
	private String DATETIME_KEY= "dateTime";
	private String TYPE_KEY= "type";
	
	private String probeId;
	private String value;
	private Timestamp date;
	private String type;
	public String getProbeId() {
		return probeId;
	}
	public void setProbeId(String probeId) {
		this.probeId = probeId;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public Timestamp getDate() {
		return date;
	}
	public void setDate(Timestamp date) {
		this.date = date;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	
	public JsonObject toJson() {
		JsonObject oJson = new JsonObject();
		SimpleDateFormat sp = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.sss");

		oJson.add(PROBE_ID_KEY,  new JsonPrimitive(probeId));
		oJson.add(VALUE_KEY, new JsonPrimitive(value));
		oJson.add(DATETIME_KEY, new JsonPrimitive(sp.format(new Date(getDate().getTime()))));
		oJson.add(TYPE_KEY, new JsonPrimitive(type));
		
		return oJson;
	}
	
	public void fromJson(JsonObject value) {
		
		SimpleDateFormat sp = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss.sss");
		
		setProbeId(value.get(PROBE_ID_KEY).getAsString());
		setValue(value.get(VALUE_KEY).getAsString());
		try {
			setDate(new Timestamp(sp.parse(value.get(DATETIME_KEY).getAsString()).getTime()));
		}  catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		setType(value.get(TYPE_KEY).getAsString());
	}


}
