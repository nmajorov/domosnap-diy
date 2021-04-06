package com.domosnap.diy.w1temp;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;

import com.domosnap.core.consumer.eventToKafkaConsumer.EventToKafkaConsumer;

/*
 * #%L
 * w1temp-microservice-prototype
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

import com.domosnap.engine.Log;
import com.domosnap.engine.Log.Session;
import com.domosnap.engine.adapter.impl.openwebnet.OpenWebCommanderConsumer;
import com.domosnap.engine.controller.Command;
import com.domosnap.engine.controller.Command.Type;
import com.domosnap.engine.controller.heating.HeatingZone;
import com.domosnap.engine.controller.temperature.TemperatureSensorStateName;
import com.domosnap.engine.controller.what.What;
import com.domosnap.engine.controller.what.impl.DoubleState;
import com.domosnap.engine.controller.where.Where;
import com.domosnap.engine.event.EventFactory;
import com.pi4j.component.temperature.TemperatureSensor;
import com.pi4j.io.w1.W1Master;
import com.pi4j.temperature.TemperatureScale;

import io.vertx.core.AbstractVerticle;

public class W1TempMicroservice extends AbstractVerticle {

	
	private Log log = new Log(W1TempMicroservice.class.getSimpleName());
	private OpenWebCommanderConsumer openWebCommanderConsumer;
	private long timerID;
	private W1Master w1Master;
	private static boolean verbose = "true".equals(System.getProperty("w1Temp.debug", "false"));
	
	// https://www.maximintegrated.com/en/products/ibutton/software/1wire/1wire_api.cfm
	// http://hirt.se/blog/?p=493
	// https://jahislove314.wordpress.com/2014/07/16/installation-dun-capteur-de-temperature-1-wire-ds18b20-sur-raspberry-partie-1/
	
	@Override
	public void start() {
		log.debug = config().getBoolean("log.enable");
		
		String url = config().getString("gateway.url", "localhost");
		int port = config().getInteger("gateway.port", 1234);
		int password = config().getInteger("gateway.password", 12345);
		
		String kafkaIp = config().getString("kafka.ip");
		if (kafkaIp != null) {
			Map<String, Object> props = new HashMap<String, Object>();
			props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "PLAINTEXT://" + kafkaIp + ":9092");
			props.put(ProducerConfig.ACKS_CONFIG, "all");
			props.put(ProducerConfig.RETRIES_CONFIG, "0");
			props.put(ProducerConfig.BATCH_SIZE_CONFIG, "100");
			props.put(ProducerConfig.LINGER_MS_CONFIG, "1");
			props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "33554432");
			props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
					"org.apache.kafka.common.serialization.StringSerializer");
			props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
					"org.apache.kafka.common.serialization.StringSerializer");
			
			EventFactory.addConsumer(new EventToKafkaConsumer(props));
		}
		
		
		openWebCommanderConsumer = new OpenWebCommanderConsumer(url, port, password);
		openWebCommanderConsumer.connect();
		w1Master = new W1Master();

		timerID = vertx.setPeriodic(300000, id -> { // all 5 min
			
			try {
				if (verbose) System.out.println(w1Master);

		        for (TemperatureSensor device : w1Master.getDevices(TemperatureSensor.class)) {
		        	
		            log.finest(Session.Device, 
		            		String.format("%-20s %3.1f°C %3.1f°F\n", device.getName(), device.getTemperature(TemperatureScale.CELSIUS),
		                    device.getTemperature(TemperatureScale.FARENHEIT))
		            );
		            String where = config().getJsonObject("mapping").getString(device.getName().replaceAll("\n", ""));
		            log.finest(Session.Device,"[".concat(device.getName().replaceAll("\n", "")).concat("]"));
		            String command = String.format("*#4*%s*0*%04.0f##", where, device.getTemperature(TemperatureScale.CELSIUS)*10);
		            log.finest(Session.Device,command);
		            openWebCommanderConsumer.accept(command);
		            command = "*4*202*" + where + "##";
		            log.finest(Session.Device,command);
		            openWebCommanderConsumer.accept(command);
		            
		            // Temp
		            DoubleState whatState = new DoubleState(device.getTemperature(TemperatureScale.CELSIUS));
		            EventFactory.SendEvent(Session.Command, new Command(HeatingZone.class, new What(TemperatureSensorStateName.value.name(), whatState), new Where("45"), Type.COMMAND, null));

		        }
			} catch (Exception e) {
				System.out.println(e.getMessage());
			}
					
			});
		System.out.println("W1Temp Started");
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		vertx.cancelTimer(timerID);
		w1Master = null;
		openWebCommanderConsumer.disconnect();
		System.out.println("W1Temp Stopped");
	}

//	
//	
//	[10:02:03:0834] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#1*04*17*08*2017##
//	[10:02:03:0844] - OpenWebNetCommand              - Invalid command [*#13**#1*04*17*08*2017##].
//	[10:02:03:0844] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#1*04*17*08*2017##]. Message dropped.
//	[10:02:03:0853] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#0*22*00*00*001##
//	[10:02:03:0853] - OpenWebNetCommand              - Invalid command [*#13**#0*22*00*00*001##].
//	[10:02:03:0854] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#0*22*00*00*001##]. Message dropped.
//	[10:02:03:0854] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*1*0*0284##
//	[10:02:03:0861] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*1*0*0284##].
//	[10:02:03:0866] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [1] : what [measure_temperature] : value [28.4]
//
//	[10:02:03:0866] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*1##
//	[10:02:03:0867] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*1##].
//	[10:02:03:0867] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [1] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:03:0868] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*2*0*0272##
//	[10:02:03:0868] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*2*0*0272##].
//	[10:02:03:0869] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [2] : what [measure_temperature] : value [27.2]
//
//	[10:02:03:0869] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*2##
//	[10:02:03:0870] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*2##].
//	[10:02:03:0870] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [2] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:03:0870] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*3*0*0277##
//	[10:02:03:0871] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*3*0*0277##].
//	[10:02:03:0871] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [3] : what [measure_temperature] : value [27.7]
//
//	[10:02:03:0872] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*303*3##
//	[10:02:03:0872] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*303*3##].
//	[10:02:03:0872] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [3] : what [status] : value [OFF]
//
//	[10:02:03:0873] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*4*0*0283##
//	[10:02:03:0873] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*4*0*0283##].
//	[10:02:03:0874] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [4] : what [measure_temperature] : value [28.3]
//
//	[10:02:03:0874] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*4##
//	[10:02:03:0875] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*4##].
//	[10:02:03:0875] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [4] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:03:0875] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*5*0*0268##
//	[10:02:03:0876] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*5*0*0268##].
//	[10:02:03:0876] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [5] : what [measure_temperature] : value [26.8]
//
//	[10:02:03:0877] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*5##
//	[10:02:03:0877] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*5##].
//	[10:02:03:0878] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [5] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:03:0878] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*6*0*0250##
//	[10:02:03:0879] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*6*0*0250##].
//	[10:02:03:0879] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [6] : what [measure_temperature] : value [25.0]
//
//	[10:02:03:0880] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*6##
//	[10:02:03:0880] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*6##].
//	[10:02:03:0880] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [6] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:03:0881] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*7*0*0262##
//	[10:02:03:0881] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*7*0*0262##].
//	[10:02:03:0881] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [7] : what [measure_temperature] : value [26.2]
//
//	[10:02:03:0882] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*7##
//	[10:02:03:0882] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*7##].
//	[10:02:03:0882] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [7] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:04:0386] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*8*0*0263##
//	[10:02:04:0388] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*8*0*0263##].
//	[10:02:04:0388] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [8] : what [measure_temperature] : value [26.3]
//
//	[10:02:04:0389] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*8##
//	[10:02:04:0390] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*8##].
//	[10:02:04:0391] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [8] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:02:04:0961] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*9*0*0233##
//	[10:02:04:0962] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*9*0*0233##].
//	[10:02:04:0963] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [9] : what [measure_temperature] : value [23.3]
//
//	[10:02:04:0964] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*9##
//	[10:02:04:0965] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*9##].
//	[10:02:04:0966] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [9] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:12:00:0920] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#1*04*17*08*2017##
//	[10:12:00:0921] - OpenWebNetCommand              - Invalid command [*#13**#1*04*17*08*2017##].
//	[10:12:00:0922] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#1*04*17*08*2017##]. Message dropped.
//	[10:12:00:0926] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#0*22*10*00*001##
//	[10:12:00:0927] - OpenWebNetCommand              - Invalid command [*#13**#0*22*10*00*001##].
//	[10:12:00:0928] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#0*22*10*00*001##]. Message dropped.
//	[10:17:00:0749] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*1*0*0283##
//	[10:17:00:0750] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*1*0*0283##].
//	[10:17:00:0750] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [1] : what [measure_temperature] : value [28.3]
//
//	[10:17:00:0755] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*1##
//	[10:17:00:0755] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*1##].
//	[10:17:00:0755] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [1] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:01:0337] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*2*0*0270##
//	[10:17:01:0338] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*2*0*0270##].
//	[10:17:01:0338] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [2] : what [measure_temperature] : value [27.0]
//
//	[10:17:01:0346] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*2##
//	[10:17:01:0346] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*2##].
//	[10:17:01:0347] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [2] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:01:0727] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*3*0*0276##
//	[10:17:01:0728] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*3*0*0276##].
//	[10:17:01:0729] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [3] : what [measure_temperature] : value [27.6]
//
//	[10:17:01:0730] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*303*3##
//	[10:17:01:0731] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*303*3##].
//	[10:17:01:0732] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [3] : what [status] : value [OFF]
//
//	[10:17:02:0321] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*4*0*0281##
//	[10:17:02:0322] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*4*0*0281##].
//	[10:17:02:0323] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [4] : what [measure_temperature] : value [28.1]
//
//	[10:17:02:0324] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*4##
//	[10:17:02:0325] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*4##].
//	[10:17:02:0326] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [4] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:02:0905] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*5*0*0266##
//	[10:17:02:0906] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*5*0*0266##].
//	[10:17:02:0906] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [5] : what [measure_temperature] : value [26.6]
//
//	[10:17:02:0909] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*5##
//	[10:17:02:0909] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*5##].
//	[10:17:02:0909] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [5] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:03:0143] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*6*0*0249##
//	[10:17:03:0144] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*6*0*0249##].
//	[10:17:03:0145] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [6] : what [measure_temperature] : value [24.9]
//
//	[10:17:03:0187] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*6##
//	[10:17:03:0188] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*6##].
//	[10:17:03:0188] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [6] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:03:0892] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*7*0*0260##
//	[10:17:03:0893] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*7*0*0260##].
//	[10:17:03:0893] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [7] : what [measure_temperature] : value [26.0]
//
//	[10:17:03:0896] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*7##
//	[10:17:03:0897] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*7##].
//	[10:17:03:0897] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [7] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:04:0478] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*8*0*0260##
//	[10:17:04:0479] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*8*0*0260##].
//	[10:17:04:0480] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [8] : what [measure_temperature] : value [26.0]
//
//	[10:17:04:0481] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*8##
//	[10:17:04:0482] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*8##].
//	[10:17:04:0483] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [8] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:17:05:0056] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*9*0*0229##
//	[10:17:05:0057] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*9*0*0229##].
//	[10:17:05:0058] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [9] : what [measure_temperature] : value [22.9]
//
//	[10:17:05:0060] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*9##
//	[10:17:05:0061] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*9##].
//	[10:17:05:0061] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [9] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:22:01:0165] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#1*04*17*08*2017##
//	[10:22:01:0166] - OpenWebNetCommand              - Invalid command [*#13**#1*04*17*08*2017##].
//	[10:22:01:0166] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#1*04*17*08*2017##]. Message dropped.
//	[10:22:01:0170] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#0*22*20*00*001##
//	[10:22:01:0171] - OpenWebNetCommand              - Invalid command [*#13**#0*22*20*00*001##].
//	[10:22:01:0172] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#0*22*20*00*001##]. Message dropped.
//	[10:32:00:0801] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#1*04*17*08*2017##
//	[10:32:00:0802] - OpenWebNetCommand              - Invalid command [*#13**#1*04*17*08*2017##].
//	[10:32:00:0803] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#1*04*17*08*2017##]. Message dropped.
//	[10:32:00:0818] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#13**#0*22*30*00*001##
//	[10:32:00:0819] - OpenWebNetCommand              - Invalid command [*#13**#0*22*30*00*001##].
//	[10:32:00:0819] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown message received [*#13**#0*22*30*00*001##]. Message dropped.
//	[10:32:01:0700] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*1*0*0282##
//	[10:32:01:0701] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*1*0*0282##].
//	[10:32:01:0701] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [1] : what [measure_temperature] : value [28.2]
//
//	[10:32:01:0702] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*1##
//	[10:32:01:0703] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*1##].
//	[10:32:01:0703] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [1] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:02:0195] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*2*0*0268##
//	[10:32:02:0196] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*2*0*0268##].
//	[10:32:02:0197] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [2] : what [measure_temperature] : value [26.8]
//
//	[10:32:02:0203] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*2##
//	[10:32:02:0204] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*2##].
//	[10:32:02:0205] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [2] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:02:0786] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*3*0*0274##
//	[10:32:02:0787] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*3*0*0274##].
//	[10:32:02:0788] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [3] : what [measure_temperature] : value [27.4]
//
//	[10:32:02:0788] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*303*3##
//	[10:32:02:0789] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*303*3##].
//	[10:32:02:0790] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [3] : what [status] : value [OFF]
//
//	[10:32:03:0230] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*4*0*0280##
//	[10:32:03:0231] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*4*0*0280##].
//	[10:32:03:0232] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [4] : what [measure_temperature] : value [28.0]
//
//	[10:32:03:0233] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*4##
//	[10:32:03:0234] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*4##].
//	[10:32:03:0235] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [4] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:03:0616] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*5*0*0263##
//	[10:32:03:0617] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*5*0*0263##].
//	[10:32:03:0618] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [5] : what [measure_temperature] : value [26.3]
//
//	[10:32:03:0666] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*5##
//	[10:32:03:0667] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*5##].
//	[10:32:03:0667] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [5] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:04:0139] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*6*0*0244##
//	[10:32:04:0139] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*6*0*0244##].
//	[10:32:04:0140] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [6] : what [measure_temperature] : value [24.4]
//
//	[10:32:04:0191] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*6##
//	[10:32:04:0192] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*6##].
//	[10:32:04:0193] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [6] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:04:0765] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*7*0*0259##
//	[10:32:04:0767] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*7*0*0259##].
//	[10:32:04:0768] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [7] : what [measure_temperature] : value [25.9]
//
//	[10:32:04:0768] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*7##
//	[10:32:04:0769] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*7##].
//	[10:32:04:0770] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [7] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:05:0213] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*8*0*0259##
//	[10:32:05:0214] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*8*0*0259##].
//	[10:32:05:0215] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [8] : what [measure_temperature] : value [25.9]
//
//	[10:32:05:0260] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*8##
//	[10:32:05:0261] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*8##].
//	[10:32:05:0262] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [8] : what [status] : value [THERMAL_PROTECTION]
//
//	[10:32:05:0789] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *#4*9*0*0230##
//	[10:32:05:0790] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*#4*9*0*0230##].
//	[10:32:05:0791] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [9] : what [measure_temperature] : value [23.0]
//
//	[10:32:05:0792] - OpenWebMonitorPublisher        - [Monitor    0]-[Socket    0] :    Read from MONITOR server: *4*202*9##
//	[10:32:05:0794] -                                - [Monitor     ]-[pool-1-thread-1] :    Unknown controller detected [*4*202*9##].
//	[10:32:05:0794] - com.domosnap.microservice.engine.recorder.http.RecorderMicroservice$1 - Who [HEATING_ADJUSTMENT] : Where [9] : what [status] : value [THERMAL_PROTECTION]
//

	
}
