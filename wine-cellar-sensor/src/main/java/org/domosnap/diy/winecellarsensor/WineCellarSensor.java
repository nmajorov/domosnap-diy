package org.domosnap.diy.winecellarsensor;


import java.io.PrintWriter;
import java.io.StringWriter;

/*
 * #%L
 * DomoSnapWineCellar
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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.producer.ProducerConfig;

import com.domosnap.core.adapter.i2c.I2CControllerService;
import com.domosnap.core.consumer.eventToKafkaConsumer.EventToKafkaConsumer;
import com.domosnap.engine.controller.humidity.HumiditySensor;
import com.domosnap.engine.controller.pressure.PressureSensor;
import com.domosnap.engine.controller.temperature.TemperatureSensor;
import com.domosnap.engine.controller.what.impl.DoubleState;
import com.domosnap.engine.controller.what.impl.PercentageState;
import com.domosnap.engine.controller.where.Where;
import com.domosnap.engine.event.EventFactory;
import com.pi4j.component.lcd.LCDTextAlignment;
import com.pi4j.component.lcd.impl.I2CLcdDisplay;

import io.vertx.core.AbstractVerticle;
import io.vertx.ext.mail.MailClient;
import io.vertx.ext.mail.MailConfig;
import io.vertx.ext.mail.MailMessage;
import io.vertx.ext.mail.StartTLSOptions;


public class WineCellarSensor extends AbstractVerticle {

	private long timerID;
	
	@Override
	public void start() {
			
		vertx.executeBlocking(future -> {
			try {
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
				
				I2CControllerService cs = new I2CControllerService(null);
				cs.connect();
				int adress = 0x77;
				TemperatureSensor ts = cs.createController(TemperatureSensor.class, new Where("bme/" + adress));
				HumiditySensor hs = cs.createController(HumiditySensor.class, new Where("bme/" + adress));
				PressureSensor ps = cs.createController(PressureSensor.class, new Where("bme/" + adress));
				
				
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
				
				timerID = vertx.setPeriodic(1000, id -> {
						//"Dimanche 12 Novembre"
						//"     12:50:55       "        
						//"  [10.5°] [62.22%]  "
						//"  Pressure: 2000    "
				        led.writeln(0, getFormattedDate(new Date()), LCDTextAlignment.ALIGN_CENTER);
				        led.writeln(1, getFormattedTime(new Date()), LCDTextAlignment.ALIGN_CENTER);
				        led.writeln(2, getFormattedInfo(ts.getTemperature(), hs.getHumidity()), LCDTextAlignment.ALIGN_CENTER);
				        led.writeln(3, getFormattedPressure(ps.getPressure()), LCDTextAlignment.ALIGN_CENTER);
				});
			} catch (Exception | Error e) {
				
				String from = config().getString("mail.from");
				String to = config().getString("mail.to");
				String hostname = config().getString("mail.hostname");
				Integer port = config().getInteger("mail.port");
				String tls = config().getString("mail.tls");
				String user = config().getString("mail.user");
				String password = config().getString("mail.password");
				
				if (from != null && to != null && hostname != null &&
					port != null && tls != null && user != null &&
					password != null) {
					System.out.println("Send mail");
					MailConfig config = new MailConfig();
					config.setHostname(hostname);
					config.setPort(port);
					if (StartTLSOptions.REQUIRED.equals(StartTLSOptions.valueOf(tls))) {
						config.setStarttls(StartTLSOptions.REQUIRED);					
					} else if (StartTLSOptions.OPTIONAL.equals(StartTLSOptions.valueOf(tls))) {
						config.setStarttls(StartTLSOptions.OPTIONAL);
					} else {
						config.setStarttls(StartTLSOptions.DISABLED);					
					}
					config.setUsername(user);
					config.setPassword(password);
					MailClient mailClient = MailClient.createNonShared(vertx, config);
		
					StringWriter sw = new StringWriter();
					PrintWriter pw = new PrintWriter(sw);
					e.printStackTrace(pw);
					
					MailMessage message = new MailMessage();
					message.setFrom(from);
					message.setTo(to);
					message.setSubject("An error occurs with WineCellarSensor: " + e.getMessage());
					message.setText(sw.toString());
					
					mailClient.sendMail(message, result -> {
						  if (result.succeeded()) {
						    System.out.println(result.result());
						  } else {
						    result.cause().printStackTrace();
						  }
						});
					;
					System.out.println("Mail with error send to " + to);
				}
				else {
					System.out.println("No mail send.");
					e.printStackTrace();
				}
				future.complete(e);
			}
		}, res -> {
			System.out.println("An error occurs with WineCellarSensor");
			vertx.close();
		});
	}

	@Override
	public void stop() throws Exception {
		super.stop();
		vertx.cancelTimer(timerID);
	}
	
	private String getFormattedDate(Date date) {
		return new SimpleDateFormat("EEEE dd MMMMM").format(date);
	}
	
	private String getFormattedTime(Date date) {
		return new SimpleDateFormat("HH:mm:ss").format(date);
	}
	
	private String getFormattedInfo(DoubleState temp, PercentageState humidity) {
		if (temp == null || humidity == null)
			return "No value";
		StringBuilder sb = new StringBuilder();
		Formatter f = new Formatter(sb);
		f.format("[%.1f C] [%.2f%%]", temp.getDoubleValue(), humidity.getValue());
		f.close();
		return sb.toString();
	}
	
	private String getFormattedPressure(DoubleState press) {
		if (press == null)
			return "No value";
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
