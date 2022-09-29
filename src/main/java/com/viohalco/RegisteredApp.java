package com.viohalco;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.logging.Logger;

public class RegisteredApp {
	public RegisteredApp(Token token,String tenantId ,String appId, String appSecret) {
		setToken(token);
		setTenantId(tenantId);
		setAppId(appId);
		setAppSecret(appSecret);
	}
	
	public String addTagToMdeDevice(String mdeDeviceId, String tag , Logger log) {
		HttpResponse<String> response = null;
		try {
			getToken().createAccess(getTenantId(), getAppId(), getAppSecret(), "https://api.securitycenter.microsoft.com");
			final String oAuthUri = "https://api.securitycenter.microsoft.com/api/machines/"+mdeDeviceId+"/tags";
	        String inputJson = "{ \"Value\":\""+tag+"\", \"Action\":\"Add\" }";	        
	        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(oAuthUri)).header("Authorization", "Bearer "+getToken().getAccess()).POST(HttpRequest.BodyPublishers.ofString(inputJson)).build();
			response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build().send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			log.info("IO Exception: "+e.toString());
		} catch (InterruptedException e) {
			log.info("InterruptedException: "+e.toString());
		}
        return response.body();
	}
	
	
	public ArrayList<JSONObject> getData( String resourceAppIdUri, String url, String method , String body, boolean cachedResult, ArrayList<String> previousResponse ) throws IOException, InterruptedException {
		ArrayList<JSONObject> objects = new ArrayList<JSONObject>();
		switch (url) {
		case "https://graph.microsoft.com/beta/devices":
		case "https://graph.microsoft.com/beta/deviceManagement/managedDevices":
		case "https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities":
		case "https://api.securitycenter.microsoft.com/api/machines":
			ArrayList<String> responses = callApi(resourceAppIdUri, url, method , body, cachedResult, previousResponse);
			for ( String response : responses ) {
	        	JSONObject res = new JSONObject(response);
	        	if ( res.has("value")) {
	        		JSONArray values = res.getJSONArray("value");
	        		 for (int i = 0 ; i < values.length(); i++) {
	        			 JSONObject obj = values.getJSONObject(i);
	        			 objects.add(obj);
	        		 }	
	        	}
			}
			break;
		}
		return objects;
	}
	
	public ArrayList<String> callApi(String resourceAppIdUri, String url, String method , String body, boolean cachedResult, ArrayList<String> previousResponse) throws IOException, InterruptedException { 		
		getToken().createAccess(getTenantId(), getAppId(), getAppSecret(), resourceAppIdUri);
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Authorization", "Bearer "+getToken().getAccess()).GET().build();
        HttpRequest.newBuilder().uri(URI.create(url)).header("Content-Type", "application/json").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if ( response.statusCode() == 200 ) {
        	if ( previousResponse == null ) {
        		previousResponse = new ArrayList<String>();
        	}
    		previousResponse.add(response.body());
    		previousResponse.trimToSize();
        	JSONObject jsonResponse = new JSONObject(response.body());
        	if ( jsonResponse.has("@odata.nextLink")) {
        		String nextLink = jsonResponse.getString("@odata.nextLink");
        		callApi(resourceAppIdUri,nextLink,null,null,true,previousResponse);
        		return previousResponse ;
        	}
        	else {
                return previousResponse;		
        	}
		}
        else {
        	return previousResponse;
        }
	}
	
	public Token getToken() {
		return token;
	}
	public void setToken(Token token) {
		this.token = token;
	}
	public String getAppId() {
		return appId;
	}
	public void setAppId(String appId) {
		this.appId = appId; 
	}
	public String getAppSecret() {
		return appSecret;
	}
	public void setAppSecret(String appSecret) {
		this.appSecret = appSecret;
	}
	public String getTenantId() {
		return tenantId;
	}
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}
	private Token token;
	private String appId;
	private String appSecret;
	private String tenantId;
	private static final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build();
}