package com.domosnap.recorder;

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
