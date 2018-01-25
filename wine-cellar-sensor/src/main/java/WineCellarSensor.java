

/*
 * #%L
 * event-connector-rest
 * %%
 * Copyright (C) 2017 A. de Giuli
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;

import com.domosnap.engine.connector.impl.i2c.I2CControllerService;
import com.domosnap.engine.controller.humidity.HumiditySensor;
import com.domosnap.engine.controller.pressure.PressureSensor;
import com.domosnap.engine.controller.temperature.TemperatureSensor;
import com.domosnap.engine.controller.what.impl.DoubleState;
import com.domosnap.engine.controller.what.impl.PercentageState;
import com.domosnap.engine.controller.where.Where;
import com.pi4j.component.lcd.LCDTextAlignment;
import com.pi4j.component.lcd.impl.I2CLcdDisplay;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class WineCellarSensor extends AbstractVerticle {

	
	private Logger logger = LoggerFactory.getLogger(this.getClass().getName());
	
	
	@Override
	public void start() {
		
		try {
			
			I2CControllerService cs = new I2CControllerService();
			TemperatureSensor ts = cs.createController(TemperatureSensor.class, new Where("77", "77"));
			HumiditySensor hs = cs.createController(HumiditySensor.class, new Where("77", "77"));
			PressureSensor ps = cs.createController(PressureSensor.class, new Where("77", "77"));
			
			
			I2CLcdDisplay led = new I2CLcdDisplay(
					4,  //     * @param rows
			        20, //     * @param columns
			        1,  //     * @param i2cBus
			        39, //     * @param i2cAddress
			        3,  //     * @param backlightBit
			        0,  //     * @param rsBit
			        1,  //     * @param rwBit
			        2,  //     * @param eBit
			        7,  //     * @param d7
			        6,  //     * @param d6
			        5,  //     * @param d5
			        4   //     * @param d4
			        );
			
			
			while (true) {
//"Dimanche 12 Novembre"
//"     12:50:55       "        
//"  [10.5°] [62.22%]  "
//"  Pressure: 2000    "
		        led.writeln(0, getFormattedDate(new Date()), LCDTextAlignment.ALIGN_CENTER);
		        led.writeln(1, getFormattedTime(new Date()), LCDTextAlignment.ALIGN_CENTER);
		        led.writeln(2, getFormattedInfo(ts.getTemperature(), hs.getHumidity()), LCDTextAlignment.ALIGN_CENTER);
		        led.writeln(3, getFormattedPressure(ps.getPressure()), LCDTextAlignment.ALIGN_CENTER);
		        Thread.sleep(1000);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
	private String getFormattedDate(Date date) {
		return new SimpleDateFormat("EEEE dd MMMMM").format(date);
	}
	
	private String getFormattedTime(Date date) {
		return new SimpleDateFormat("HH:mm:ss").format(date);
	}
	
	private String getFormattedInfo(DoubleState temp, PercentageState humidity) {
		StringBuilder sb = new StringBuilder();
		Formatter f = new Formatter(sb);
		f.format("[%.1f°C] [%.2f%%]", temp.getDoubleValue(), humidity.getValue());
		f.close();
		return sb.toString();
	}
	
	private String getFormattedPressure(DoubleState press) {
		StringBuilder sb = new StringBuilder();
		Formatter f = new Formatter(sb);
		f.format("[%.4fPa]", press.getValue());
		f.close();
		return sb.toString();
	}
	
	public static void main(String[] args) {
		io.vertx.core.Vertx vertx = io.vertx.core.Vertx.vertx();
		vertx.deployVerticle(WineCellarSensor.class.getName());
		
	}


}
