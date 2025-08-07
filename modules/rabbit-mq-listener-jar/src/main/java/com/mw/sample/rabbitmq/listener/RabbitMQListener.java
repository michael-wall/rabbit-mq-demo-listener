package com.mw.sample.rabbitmq.listener;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONObject;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RabbitMQListener{
	
	@Value("${RABBIT_MQ_DEMO_QUEUE_NAME}")
	private String rabbitMqDemoQueueName;
	
    @Value("${RABBIT_MQ_PROCESSED_QUEUE_NAME}")
    private String rabbitMqProcessedQueueName;
    
    @Value("${RABBIT_MQ_ERROR_QUEUE_NAME}")
    private String rabbitMqErrorQueueName;

    @Value("${LIFERAY_BASE_URL}")
	private String liferayBaseUrl;
	
	@Value("${LISTENER_OAUTH_CLIENT_ID}")
	private String oAuthClientId;
	
	@Value("${LISTENER_OAUTH_CLIENT_SECRET}")
	private String oAuthClientSecret;
	
    private final RabbitTemplate rabbitTemplate;

    public RabbitMQListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "${RABBIT_MQ_DEMO_QUEUE_NAME}")
    public void receiveMessage(String message) {
    	
    	try {
			Thread.sleep(15000);
		} catch (InterruptedException e) {}
    	
    	_log.info("############################# RabbitMQ MESSAGE RECEIVED #############################");
    	
    	_log.info("Queue: " + rabbitMqDemoQueueName + ", " + message);
    	_log.info("Message: " + message);
    	
    	boolean processed = false;
    	
    	try {
	    	String oauthToken = getOAuthAccessToken();
	    	
	    	_log.info("oauthToken: " + oauthToken);
	    	
	    	if (oauthToken != null) {
				JSONObject jsonObject = new JSONObject(message);
				if (jsonObject.has("objectEntryDTORabbitTest")) {
					JSONObject jsonObjectEntry = jsonObject.getJSONObject("objectEntryDTORabbitTest");
		
					if (jsonObjectEntry != null && jsonObjectEntry.has("id") && jsonObjectEntry.has("properties")) {
						long id = jsonObjectEntry.getLong("id");
						
						JSONObject jsonObjectProperties = jsonObjectEntry.getJSONObject("properties");
						
						if (jsonObjectProperties.has("input")) {
							String input = jsonObjectProperties.getString("input");
							
							_log.info("id: " + id + ", input: " + input);
							
							 int statusCode = patch(id, input, oauthToken);
							 
							 if (statusCode == 200) { //TODO check which...
								 _log.info("Message processed as expected, moving to : " + rabbitMqProcessedQueueName);
								 
								 rabbitTemplate.send(rabbitMqProcessedQueueName, new Message(message.getBytes()));
								 
								 processed = true;						 
							 }							
						}
					}
				}
	    	}
    	} catch (Exception e) {
    		_log.error(e);
    	}
    	
    	if (!processed) {
    		_log.info("Message not processed as expected, moving to : " + rabbitMqErrorQueueName);
    		
    		rabbitTemplate.send(rabbitMqErrorQueueName, new Message(message.getBytes()));
    	}
    }
    
    private int patch(long id, String input, String oauthToken) {
    	
    	String endpoint = liferayBaseUrl + "/o/c/rabbittests/" + id;
        String patchData = "{ \"output\": \"" + input + " " + UUID.randomUUID().toString() +  "\" }";

    	try {
	    	HttpClient client = HttpClient.newHttpClient();
	    	HttpRequest request = HttpRequest.newBuilder()
	    		    .uri(URI.create(endpoint))
	    		    .method("PATCH", HttpRequest.BodyPublishers.ofString(patchData))
	    		    .header("Authorization", "Bearer " + oauthToken)
	    		    .header("Content-Type", "application/json")
	    		    .header("Accept", "application/json")
	    		    .build();

    		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    		
    		_log.info("Response: " + response);
    		
    		return response.statusCode();

    	} catch (Exception e) {
    		_log.error(e);
    	}
    	
    	return -1;
    }
    
    private String getOAuthAccessToken() {
    	
    	String endpoint = liferayBaseUrl + "/o/oauth2/token";
    	String postData = "grant_type=client_credentials";
        String auth = oAuthClientId + ":" + oAuthClientSecret;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    	
    	try {
	    	HttpClient client = HttpClient.newHttpClient();
	    	HttpRequest request = HttpRequest.newBuilder()
	    		    .uri(URI.create(endpoint))
	    		    .method("POST", HttpRequest.BodyPublishers.ofString(postData))
	    		    .header("Authorization", "Basic " + encodedAuth)
	    		    .header("Content-Type", "application/x-www-form-urlencoded")
	    		    .build();

    		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
    		
    		_log.info("Response: " + response.body());
    		
    		JSONObject jsonObject = new JSONObject(response.body());
    		
    		if (jsonObject.has("access_token")) {
    			return jsonObject.getString("access_token");
    		}
    	} catch (Exception e) {
    		_log.error(e);
    	}

        return null;
    }    
    
	private static final Log _log = LogFactory.getLog(RabbitMQListener.class);
}