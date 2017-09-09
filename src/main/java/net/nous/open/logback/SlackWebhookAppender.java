package net.nous.open.logback;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.*;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

import org.json.simple.JSONObject;

/**
 * Batched Logback appender for Slack through its webhook api
 */
public class SlackWebhookAppender extends AppenderBase<ILoggingEvent> {
	private String webhookUrl;
    private URL url;
    private String channel;
    private String username;
    private String iconEmoji;
    private Layout<ILoggingEvent> layout;
    private int maxLoggingEventLength = 256;
    private int maxSlackTextLength = 2048;
    private int batchingSecs = 10;
    
    private Queue<ILoggingEvent> eventBuffer = new ConcurrentLinkedQueue<ILoggingEvent>();
    private ScheduledExecutorService scheduler;
    
    @Override
    public void start() {
    	if (scheduler == null) {
    		initializeOrResetScheduler();
    	}
    	super.start();
    }

	private synchronized void initializeOrResetScheduler() {
		if (scheduler != null) {
			scheduler.shutdownNow();
		}
		scheduler = Executors.newScheduledThreadPool(1);
		scheduler.scheduleAtFixedRate(new SenderRunnable(), batchingSecs, batchingSecs, TimeUnit.SECONDS);
	}
    
    @Override
    protected void append(final ILoggingEvent evt) {
    	addEventToBuffer(evt);
    }

	private void addEventToBuffer(final ILoggingEvent evt) {
		eventBuffer.add(evt);
	}

	private void sendBufferIfItIsNotEmpty() {
		if (eventBuffer.isEmpty())
			return;
		
        StringBuffer sbuf = new StringBuffer();
        // appending events
        ILoggingEvent event = null;
    	while ((event = eventBuffer.poll()) != null && sbuf.length() <= maxSlackTextLength) {
    		sbuf.append(extractEventText(event));
    	}
    	int remaining = eventBuffer.size();
    	eventBuffer.clear();
        String slackText = trim(sbuf.toString(), maxSlackTextLength, "..\n.. and " + remaining + " more");
		sendTextToSlack(slackText);
	}
	
	private String trim(String text, int maxLen, String appendingText) {
		return text.length() <= maxLen ? text : text.substring(0, maxLen - appendingText.length()) + appendingText;
	}

	@SuppressWarnings("unchecked")
	private void sendTextToSlack(String slackText) {
		JSONObject obj = null;
		try {
            obj = new JSONObject();
            
            if (username != null) obj.put("username", username);
            if (iconEmoji != null) obj.put("icon_emoji", iconEmoji);
            if (channel != null) obj.put("channel", channel);
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

	private String extractEventText(ILoggingEvent lastEvent) {
		String text = layout.doLayout(lastEvent);
		text = trim(text, maxLoggingEventLength, "..");
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
	
	private class SenderRunnable implements Runnable {		
		public void run() {
			sendBufferIfItIsNotEmpty();
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

	public int getBatchingSecs() {
		return batchingSecs;
	}

	public void setBatchingSecs(int batchingSecs) {
		this.batchingSecs = batchingSecs;
		initializeOrResetScheduler();
	}
}