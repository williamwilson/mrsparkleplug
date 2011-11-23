package com.canoesoftware.spark;

import java.io.IOException;

import org.jivesoftware.spark.ChatManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.plugin.Plugin;
import org.jivesoftware.spark.ui.MessageFilter;

public class MrSparklePlugin implements Plugin {
	
	public boolean canShutDown() {
		return true;
	}

	public void initialize() {
		
		/* initialize and register the logging message filter */
		ChatManager chatManager = SparkManager.getChatManager();
		MessageFilter messageFilter;
		try {
			messageFilter = new LoggingMessageFilter("test@conference.hydrogen", "d:\\temp", "d:\\temp\\published");
			chatManager.addMessageFilter(messageFilter);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void shutdown() {		
	}

	public void uninstall() {		
	}

}
