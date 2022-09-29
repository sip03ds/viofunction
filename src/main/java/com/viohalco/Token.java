package com.viohalco;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

public class Token {
	public Token(String access) {
		setAccess(access);
	}
	public Token() { 
		
	}
	public void createAccess(String tenantId , String appId , String appSecret , String resourceAppIdUri) throws IOException, InterruptedException {
		final String oAuthUri = "https://login.microsoftonline.com/" + tenantId + "/oauth2/token";
		
        Map<Object, Object> data = new HashMap<>();
        data.put("resource", resourceAppIdUri);
        data.put("client_id", appId);
        data.put("client_secret", appSecret);
        data.put("grant_type", "client_credentials");
        data.put("ts", System.currentTimeMillis());		
		
        HttpRequest request = HttpRequest.newBuilder().POST(buildFormDataFromMap(data)).uri(URI.create(oAuthUri)).setHeader("Accept", "application/json").build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        String[] responseNameValues = response.body().split(",");
        String value;
        
        for ( String nameValue : responseNameValues ) {
        	if (nameValue.startsWith("\"access_token\":")) {
        		int firstIndex = nameValue.indexOf(":");
        		int lastIndex = nameValue.lastIndexOf("\"");
        		value = nameValue.substring(firstIndex+2,lastIndex);
        		setAccess(value);
        	}
        }
	}	
	public void setAccess(String access) {
		this.access = access; 
	}
	public String getAccess() { 
		return this.access;
	}
	private static HttpRequest.BodyPublisher buildFormDataFromMap(Map<Object, Object> data) {
        var builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }
    private static final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
	private String access; 
}