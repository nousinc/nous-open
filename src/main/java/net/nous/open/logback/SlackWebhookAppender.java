package net.nous.open.logback;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.*;
import java.io.*;
import java.net.*;
import java.util.*;

import org.json.simple.JSONObject;

/**
 * Batched Logback appender for Slack through its webhook api
 */
public class SlackWebhookAppender extends AppenderBase<ILoggingEvent> {
	private static final String BATCHING_WARNING_MESSAGE = "[Slack Appender] The current and the susquent messages will be batched to prevent exceeding slack message limit";
	private static final String BATCHING_REMINDER_MESSAGE = "[Slack Appender] Following message(s) are batched\n";
	private static final int MAX_EVENT_BUFFER_SIZE = 20;
	private String webhookUrl;
    private URL url;
    private String channel;
    private String username;
    private String iconEmoji;
    private Layout<ILoggingEvent> layout;
    private int maxLoggingEventLength = 256;
    private int maxSlackTextLength = 1024;
    private boolean asynchronousSending = true; // async by default
    
    private long lastSendEventMillis = -1;
    private int batchingTimeFrameMills = 2000; // batch message into two seconds window
    private List<ILoggingEvent> eventBuffer = new ArrayList<ILoggingEvent>(MAX_EVENT_BUFFER_SIZE);

    @Override
    protected void append(final ILoggingEvent evt) {
    	addEventToBuffer(evt);
		long now = System.currentTimeMillis();
    	if (now - lastSendEventMillis < batchingTimeFrameMills) {
    		// send the warning message when it batch the first item
    		if (eventBuffer.size() == 1) {
    	        if (asynchronousSending) {
    	            // perform actual sending asynchronously
    	            context.getExecutorService().execute(new SenderRunnable(BATCHING_WARNING_MESSAGE));
    	          } else {
    	            // synchronous sending
    	            sendTextToSlack(BATCHING_WARNING_MESSAGE);
    	          }
    		}
    		return;
    	}
    	lastSendEventMillis = now;
    	
    	List<ILoggingEvent> cloneEvents = new ArrayList<ILoggingEvent>(eventBuffer);
    	eventBuffer.clear();
    	
        if (asynchronousSending) {
            // perform actual sending asynchronously
            context.getExecutorService().execute(new SenderRunnable(cloneEvents, evt));
          } else {
            // synchronous sending
            send(cloneEvents, evt);
          }
    }

	private void addEventToBuffer(final ILoggingEvent evt) {
		if (eventBuffer.size() < MAX_EVENT_BUFFER_SIZE)
	    	eventBuffer.add(evt);
	}
    
    private void fillBuffer(List<ILoggingEvent> events, StringBuffer sbuf) {
        for (ILoggingEvent event : events) {
          sbuf.append(extractEventText(event));
        }
      }

	private void send(List<ILoggingEvent> events, ILoggingEvent lastEvent) {
        StringBuffer sbuf = new StringBuffer();
        // add batching message if applicable
        if (eventsAreBatched(events, lastEvent)) { 
        	sbuf.append(BATCHING_REMINDER_MESSAGE);
        }
        // appending events
        fillBuffer(events, sbuf);
        String slackText = sbuf.length() <= maxSlackTextLength ? sbuf.toString() : sbuf.substring(0, maxSlackTextLength - 6) + "\n..\n..";
		
		sendTextToSlack(slackText);
	}

	public void sendTextToSlack(String slackText) {
		JSONObject obj = null;
		try {
            obj = new JSONObject();
            
            if (username != null) {
            	obj.put("username", username);
            }
            if (iconEmoji != null) {
            	obj.put("icon_emoji", iconEmoji);
            }
            if (channel != null) {
            	obj.put("channel", channel);
            }
            obj.put("text", slackText);
            
            final byte[] bytes = obj.toJSONString().getBytes("UTF-8");
            final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setFixedLengthStreamingMode(bytes.length);
            conn.setRequestProperty("Content-Type", "application/json");

            final OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.flush();
            os.close();

            if (conn.getResponseCode() != 200) {
            	throw new IOException("Bad response code: HTTP/1.0 " + conn.getResponseCode());
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            addError("Error to post json object to Slack.com (" + channel + "): " + obj, ex);
        }
	}

	private boolean eventsAreBatched(List<ILoggingEvent> events, ILoggingEvent lastEvent) {
		return events.size() > 1;
	}

	private String extractEventText(ILoggingEvent lastEvent) {
		String text = layout.doLayout(lastEvent);
		text = text.length() <= maxLoggingEventLength ? text : text.substring(0, maxLoggingEventLength-2) + "..";
		return text;
	}

    public String getChannel() {
        return channel;
    }

    public void setChannel(final String channel) {
        this.channel = channel;
    }

    public Layout<ILoggingEvent> getLayout() {
        return layout;
    }

    public void setLayout(final Layout<ILoggingEvent> layout) {
        this.layout = layout;
    }
    
	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getIconEmoji() {
		return iconEmoji;
	}

	public void setIconEmoji(String iconEmoji) {
		this.iconEmoji = iconEmoji;
	}
	
	public String getWebhookUrl() {
		return webhookUrl;
	}
	
	/**
	 * Set your own slack webhock URL here.
	 * For example: https://mycompany.slack.com/services/hooks/incoming-webhook?token=MY_SLACK_APPENDER_TOKEN
	 */
	public void setWebhookUrl(String webhookUrl) throws MalformedURLException {
		this.webhookUrl = webhookUrl;
		this.url = new URL(webhookUrl);
	}

	public boolean isAsynchronousSending() {
		return asynchronousSending;
	}

	public void setAsynchronousSending(boolean asynchronousSending) {
		this.asynchronousSending = asynchronousSending;
	}
	
	private class SenderRunnable implements Runnable {
		ILoggingEvent evt;
		List<ILoggingEvent> cloneEvents;
		String text;
		
		SenderRunnable(String text) {
			this.text = text;
		}
		
		SenderRunnable(List<ILoggingEvent> cloneEvents, ILoggingEvent evt) {
			this.cloneEvents = cloneEvents;
			this.evt = evt;
		}
		
		public void run() {
			if (text != null) {
				sendTextToSlack(text);
			}
			else {
				send(cloneEvents, evt);
			}
		}
	}

	public int getMaxLoggingEventLength() {
		return maxLoggingEventLength;
	}

	public void setMaxLoggingEventLength(int maxLoggingEventLength) {
		this.maxLoggingEventLength = maxLoggingEventLength;
	}

	public int getMaxSlackTextLength() {
		return maxSlackTextLength;
	}

	public void setMaxSlackTextLength(int maxSlackTextLength) {
		this.maxSlackTextLength = maxSlackTextLength;
	}

	public int getBatchingTimeFrameMills() {
		return batchingTimeFrameMills;
	}

	public void setBatchingTimeFrameMills(int batchingTimeFrameMills) {
		this.batchingTimeFrameMills = batchingTimeFrameMills;
	}
}