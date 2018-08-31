package net.nous.open.slack;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;

import org.json.simple.*;
import org.slf4j.*;

/** Simple class for sending text to a particular slack channel or user, apparently from a particular
 * user, with a particular icon
 */
public class SlackPoster {
	private static final Logger LOGGER = LoggerFactory.getLogger(SlackPoster.class);

	private final String urlString;
	private final ExecutorService executor = Executors.newSingleThreadExecutor();
 	private String icon;
	private String user;

	public SlackPoster(String urlString) {
		this.urlString = urlString;
	}
	     
	public void setUsername(String user) {
		this.user = user;
	}

	public void setIcon(String icon) {
		this.icon = icon;
	}

	/** Post the given text to the given channel/user, using the default user and icon
	 * @param text The message
	 * @param to <tt>#channel</tt> or <tt>@username</tt>
	 */
	public void post(String text, String to) {
		post(text, to, this.user, this.icon);
	}
	
	/** Posts using the supplied options
	 * @param text The message
	 * @param to <tt>#channel</tt> or <tt>@username</tt>
	 * @param username who the message appears to have been sent by
	 * @param iconOrEmoji icon or emoji...
	 */
	public void post(final String text, final String to, final String username, final String iconOrEmoji) {
		final JSONObject obj = buildSimpleTextJson(text, to, username, iconOrEmoji);
		doPostAsync(obj);		
	}
	
	@SuppressWarnings("unchecked")
	public void postWithColor(String text, String to, String colorCode) {
		final JSONObject obj = buildSimpleTextJson(null, to, this.user, this.icon);
		
		// add color and text as a attachment
		// more => https://api.slack.com/docs/formatting
		JSONObject attachment = new JSONObject(); 
		attachment.put("color", colorCode);
		JSONObject attachmentField = new JSONObject();
		attachmentField.put("value", text);
		attachment.put("fields", asJSONArray(attachmentField));		
		obj.put("attachments", asJSONArray(attachment));
	
		doPostAsync(obj);
	}

	@SuppressWarnings("unchecked")
	private JSONArray asJSONArray(JSONObject obj) {
		JSONArray result = new JSONArray();
		result.add(obj);
		return result;
	}

	@SuppressWarnings("unchecked")	
	private JSONObject buildSimpleTextJson(String text, String to,
			String username, String iconOrEmoji) {
		final JSONObject obj = new JSONObject();
        if (username != null) {
        	obj.put("username", username);
        }
        if (iconOrEmoji != null) {
        	obj.put("icon_emoji", iconOrEmoji);
        }
        if (to != null) {
        	obj.put("channel", to);
        }
        obj.put("text", text);
		return obj;
	}
	
	private void doPostAsync(final JSONObject obj) {
		// run it in a executor so it wont block
		executor.execute(new Runnable() {
			public void run() {
				doPostSync(obj);
			}
		});
	}

	private void doPostSync(final JSONObject obj) {
		try {
            final byte[] bytes = obj.toJSONString().getBytes("UTF-8");
            URL url = new URL(urlString);
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
        } catch (UnknownHostException ex) {
        	LOGGER.warn("Cannot reach slack, are you offline? " + obj);
        } catch (Exception ex) {
        	LOGGER.warn("Unable to post to slack " + obj, ex);
        }
	}
}
