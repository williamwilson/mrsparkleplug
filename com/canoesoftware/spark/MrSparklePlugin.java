package com.canoesoftware.spark;

import java.net.URL;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.jivesoftware.spark.ChatManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.plugin.Plugin;
import org.jivesoftware.spark.ui.MessageFilter;

public class MrSparklePlugin implements Plugin {
	private static final Logger logger = Logger.getLogger(MrSparklePlugin.class);
	
	public boolean canShutDown() {
		return true;
	}

	public void initialize() {
		logger.info("Initializing MrSparkle sparkplug.");
		
		logger.debug("Loading properties from mrsparkle.properties");
		Properties properties = new Properties();
		try {
			ClassLoader loader = this.getClass().getClassLoader();
			logger.debug(String.format("Trying to find 'mrsparkle.properties' using classloader %s.", loader));
			URL propertiesUrl = loader.getResource("mrsparkle.properties");
			if (propertiesUrl == null)
			{
				loader = ClassLoader.getSystemClassLoader();
				logger.debug(String.format("Trying to find 'mrsparkle.properties' using classloader %s.", loader));
				propertiesUrl = loader.getResource("mrsparkle.properties");
				
				if (propertiesUrl == null)
				{
					logger.debug("Trying to find 'mrsparkle.properties' using ClassLoader.getSystemResource().");
					propertiesUrl = ClassLoader.getSystemResource("mrsparkle.properties");
				}
			}
			
			if (propertiesUrl == null)
			{
				logger.fatal("Failed to locate 'mrsparkle.properties'.  No messages will be logged.");
				return;
			}
			else
			{
				logger.debug(String.format("Using URL '%s' for configuration.", propertiesUrl.toString()));
			}
			
			properties.load(propertiesUrl.openStream());
		}
		catch (Exception e)
		{
			logger.error(e);
			logger.debug("Failed to load properties.  No messages will be logged.");
			return;
		}
		
		/* initialize and register the logging message filter */
		ChatManager chatManager = SparkManager.getChatManager();
		MessageFilter messageFilter = new LoggingMessageFilter(properties.getProperty("mrsparkle.room"), properties.getProperty("mrsparkle.url"));
		chatManager.addMessageFilter(messageFilter);
	}

	public void shutdown() {	
		logger.info("Shutting down MrSparkle sparkplug.");		
	}

	public void uninstall() {	
		logger.info("Uninstalling MrSparkle sparkplug.");		
	}

}
