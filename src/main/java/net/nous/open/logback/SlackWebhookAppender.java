package net.nous.open.logback;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.*;

import java.io.*;
import java.net.*;
import org.json.simple.JSONObject;

/**
 * Logback appender for Slack through its webhook api
 */
public class SlackWebhookAppender extends AppenderBase<ILoggingEvent> {
    private String webhookUrl;
    private URL url;
    private String channel;
    private String username;
    private String iconEmoji;
    private Layout<ILoggingEvent> layout;
    private int maxTextLength = 256;
    private boolean asynchronousSending = true; // async by default

    @Override
    protected void append(final ILoggingEvent evt) {
        if (asynchronousSending) {
            // perform actual sending asynchronously
            context.getExecutorService().execute(new SenderRunnable(evt));
          } else {
            // synchronous sending
            send(evt);
          }
    }

	private void send(final ILoggingEvent evt) {
		try {
            JSONObject obj = new JSONObject();
            
            if (username != null) {
            	obj.put("username", username);
            }
            if (iconEmoji != null) {
            	obj.put("icon_emoji", iconEmoji);
            }
            if (channel != null) {
            	obj.put("channel", channel);
            }
            String text = layout.doLayout(evt);       
            obj.put("text", text.length() <= maxTextLength ? text : text.substring(0, maxTextLength-2) + "..");
            
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
            addError("Error to post log to Slack.com (" + channel + "): " + evt, ex);
        }
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

	public int getMaxTextLength() {
		return maxTextLength;
	}

	public void setMaxTextLength(int maxTextLength) {
		this.maxTextLength = maxTextLength;
	}
	
	private class SenderRunnable implements Runnable {
		ILoggingEvent evt;
		SenderRunnable(ILoggingEvent evt) {
			this.evt = evt;
		}
		
		public void run() {
			send(evt);
		}
	}
}