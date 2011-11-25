package com.canoesoftware.spark;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.spark.SessionManager;
import org.jivesoftware.spark.SparkManager;
import org.jivesoftware.spark.ui.ChatRoom;
import org.jivesoftware.spark.ui.MessageFilter;

public class LoggingMessageFilter 
	implements MessageFilter
{
	/**
	 * Represents a failed message and associated context.
	 */
	private class FailedMessage
	{
		private final Date _date;
		private final Message _message;
		private final ChatRoom _room;
		private final String _username;
		
		/**
		 * Initializes a new failed message with the specified context.
		 * @param room the room in which the message was received
		 * @param username the name of the user who sent the message
		 * @param message the message
		 * @param date the date and time the message was processed
		 */
		public FailedMessage(ChatRoom room, String username, Message message, Date date)
		{
			_room = room;
			_username = username;
			_message = message;
			_date = date;
		}
		
		/**
		 * Gets the date and time of the failed message.
		 * @return the date and time the message was first processed
		 */
		public Date getDate() { return _date; }
		
		/**
		 * Gets the failed message.
		 * @return the message which failed to post
		 */
		public Message getMessage() { return _message; }
		
		/**
		 * Gets the chatroom in which the message was received.
		 * @return the chatroom in which the message was received
		 */
		public ChatRoom getRoom() { return _room; }
		
		/**
		 * Gets the name of the user who sent the message.
		 * @return the name of the user who sent the message
		 */
		public String getUsername() { return _username; }
	}
	
	private static final int MAX_BUFFER = 50;
	private static final Logger logger = Logger.getLogger(LoggingMessageFilter.class);
	
	private final String _currentUsername;
	private final List _failedMessages = new ArrayList();
	private Date _lastMessageTime;
	private final String _logUrl;
	private final String _room;
	
	/**
	 * Initializes a new MessageFilter for logging messages to a URL via an HTTP post.
	 * 
	 * @param room the room for which messages are logged
	 * @param logUrl the URL to which messages are posted
	 * 
	 */
	public LoggingMessageFilter(String room, String logUrl)
	{
		logger.info(String.format("Initializing logging message filter. room: '%s' url: '%s'", new Object[] {room, logUrl}));
		_room = room;
		_logUrl = logUrl;
		
		/* record the current user's name for logging out-going messages */
		SessionManager sessionManager = SparkManager.getSessionManager();
		_currentUsername = sessionManager.getUsername();
		logger.debug(String.format("Current user: '%s'", new Object[] {_currentUsername}));
	}

	public void filterIncoming(ChatRoom room, Message message) {
		logger.debug(String.format("Incoming message received.  room: '%s'\r\nmessage.id: '%s'\r\nmessage.from: '%s'\r\nmessage.body: '%s'", new Object[] {room.getRoomname(), message.getPacketID(), message.getFrom(), message.getBody()}));
		try {
			logMessage(room, message);
		}
		catch (Exception e)
		{
			logger.error(e);
			e.printStackTrace();
		}
	}

	public void filterOutgoing(ChatRoom room, Message message) {
		logger.debug(String.format("Outgoing message.  room: '%s'\r\nmessage.id: '%s'\r\nmessage.from: '%s'\r\nmessage.body: '%s'", new Object[] {room.getRoomname(), message.getPacketID(), message.getFrom(), message.getBody()}));
		try {
			logMessage(room, message);
		}
		catch (Exception e)
		{
			logger.error(e);
			e.printStackTrace();
		}
	}
	
	private void logMessage(ChatRoom room, Message message) throws Exception
	{
		if (message.getPacketID() == null)
		{
			logger.debug("Message with null ID ignored.");
			return;
		}
		
		if (!room.getRoomname().equalsIgnoreCase(_room))
		{
			logger.debug("Room match failed, ignoring.");
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
		logger.debug(String.format("From resolved to: '%s'", new Object[] {username}));
		int status = 0;
		
		/* note: in order to avoid multiple messages being sent with the same time
		 * (and thus losing their ordering) add some time if the current message has
		 * the same time (this mostly happens when joining a room)
		 */
		Date date = new Date();
		if (_lastMessageTime != null && _lastMessageTime.compareTo(date) >= 0)
		{
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(_lastMessageTime);
			calendar.add(Calendar.MILLISECOND, 10);
			date = calendar.getTime();
		}
		_lastMessageTime = date;
		
		try {
			status = postMessage(room, message, username, date);
		}
		catch (Exception e) {
			logger.error(e);
			logger.debug("Post failed.  Buffering message for retry.");
			bufferFailedMessage(room, message, username, date);
			return;
		}
		logger.debug(String.format("Post response: '%s'", new Object[] { new Integer(status)}));
		
		if (status != 200) {
			logger.debug("Post failed.  Buffering message for retry.");
			bufferFailedMessage(room, message, username, date);
		}
		else {
			while (_failedMessages.size() > 0) {
				logger.debug("Attempting post for buffered failed message.");
				FailedMessage failedMessage = (FailedMessage) _failedMessages.get(0);

				try {
					status = postMessage(failedMessage.getRoom(), failedMessage.getMessage(), failedMessage.getUsername(), failedMessage.getDate());
				}
				catch (Exception e) {
					logger.error(e);
				}
				
				logger.debug(String.format("Post response: '%s'", new Object[] { new Integer(status)}));
				if (status != 200) {
					logger.debug("Post failed.  Aborting re-posts.");
					break;
				}
				_failedMessages.remove(0);
			}
		}
	}

	private void bufferFailedMessage(ChatRoom room, Message message,
			String username, Date date) {
		_failedMessages.add(new FailedMessage(room, username, message, date));
		if (_failedMessages.size() >= MAX_BUFFER) {
			_failedMessages.remove(0);
		}
	}

	private int postMessage(ChatRoom room, Message message, String username, Date date)
			throws MalformedURLException, IOException,
			UnsupportedEncodingException {
		URL mrsparkleUrl = new URL(_logUrl);
		HttpURLConnection connection = (HttpURLConnection)mrsparkleUrl.openConnection();
		connection.setDoOutput(true);
		connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		
		OutputStream output = connection.getOutputStream();
		String charset = "UTF-8";
		String messagePost = String.format("ID=%s&Room=%s&Body=%s&From=%s&Time=%s",
				new Object[] {URLEncoder.encode(message.getPacketID(), charset),
				URLEncoder.encode(StringUtils.parseName(room.getRoomname()), charset),
				URLEncoder.encode(message.getBody(), charset),
				URLEncoder.encode(username, charset),
				URLEncoder.encode(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(date), charset)}
				);
		output.write(messagePost.getBytes(charset));
		output.close();

		logger.debug(String.format("Posting to: '%s'\r\n%s", new Object[] {mrsparkleUrl.toString(), messagePost}));
		int status = ((HttpURLConnection)connection).getResponseCode();
		return status;
	}
}
