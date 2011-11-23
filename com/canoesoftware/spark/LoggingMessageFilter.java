package com.canoesoftware.spark;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.Date;
import java.util.UUID;

import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.spark.SessionManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.ui.ChatRoom;
import org.jivesoftware.spark.ui.MessageFilter;

public class LoggingMessageFilter 
	implements MessageFilter
{
	private static final int MESSAGE_THRESHOLD = 5;
	
	private final String _currentUsername;
	private File _logFile;
	private Writer _logWriter;
	private int _messageCount = 0;
	private final String _path;
	private final String _publishPath;
	private final String _room;
	
	/**
	 * Initializes a new MessageFilter for logging messages which stores log files
	 * temporarily at the specified path.
	 * 
	 * @param room the room for which messages are logged
	 * 
	 * @param path the location of temporary log files
	 * 
	 * @param publishPath the location to which log files are published
	 * 
	 * @throws IOException if unable to create a file for writing at the specified path
	 */
	public LoggingMessageFilter(String room, String path, String publishPath) throws IOException
	{
		_room = room;
		_path = path;
		_publishPath = publishPath;
		
		/* record the current user's name for logging out-going messages */
		SessionManager sessionManager = SparkManager.getSessionManager();
		_currentUsername = sessionManager.getUsername();
		
		initializeLogFile();
	}

	public void filterIncoming(ChatRoom room, Message message) {
		try {
			logMessage(room, message);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	public void filterOutgoing(ChatRoom room, Message message) {
		try {
			logMessage(room, message);
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
	
	/**
	 * Initializes a new log file at the current log path.
	 * @throws IOException if unable to create a file for writing at the current log path
	 */
	private void initializeLogFile() throws IOException {
		UUID uid = UUID.randomUUID();
		_logFile = new File(_path + "\\" + uid.toString());
		_logWriter = new FileWriter(_logFile);
	}
	
	private void logMessage(ChatRoom room, Message message) throws Exception
	{
		if (!room.getRoomname().equalsIgnoreCase(_room))
		{
			return;
		}
		
		String username;
		if (message.getFrom() == null) {
			username = _currentUsername;
		}
		else {
			/* note: for group chat messages, retrieve the username from the resource */
			if (message.getType() == Message.Type.groupchat) {
				username = StringUtils.parseResource(message.getFrom());
			}
			else {
				username = StringUtils.parseName(message.getFrom());
			}
		}
				
		try {
			_logWriter.write(message.getPacketID() + ":" + username + ":" + message.getBody() + "\r\n");
			_logWriter.flush();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		/* TODO: parameterization and response/error handling */
		URL sparkleUrl = new URL("http://localhost:2114/message/create");
		HttpURLConnection connection = (HttpURLConnection)sparkleUrl.openConnection();
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		
		OutputStream output = connection.getOutputStream();
		String charset = "UTF-8";
		String messagePost = String.format("ID=%s&Room=%s&Body=%s&From=%s&Time=%s",
				URLEncoder.encode(message.getPacketID(), charset),
				URLEncoder.encode(StringUtils.parseName(room.getRoomname()), charset),
				URLEncoder.encode(message.getBody(), charset),
				URLEncoder.encode(username, charset),
				URLEncoder.encode(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(new Date()), charset)
				);
		output.write(messagePost.getBytes(charset));
		output.close();
		
		int status = ((HttpURLConnection)connection).getResponseCode();
		
		_messageCount++;
		if (_messageCount > MESSAGE_THRESHOLD) {
			publishLog();
		}
	}
	
	private void publishLog() {
		File publishPath = new File(_publishPath);
		if (!publishPath.exists())
		{
			if (!publishPath.mkdirs()) {
				return;
			}
		}
		
		try {
			_logWriter.close();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
		if (_logFile.renameTo(new File(_publishPath + "\\" + _logFile.getName())))
		{
			_messageCount = 0;
			try {
				initializeLogFile();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}		
	}
}
