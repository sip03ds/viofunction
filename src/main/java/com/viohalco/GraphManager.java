package com.viohalco;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import com.google.gson.JsonPrimitive;
import com.microsoft.graph.models.Device;
import com.microsoft.graph.models.DeviceCategory;
import com.microsoft.graph.models.ManagedDevice;
import com.microsoft.graph.models.WindowsAutopilotDeviceIdentity;
import com.microsoft.graph.requests.DeviceCategoryRequestBuilder;
import com.nimbusds.oauth2.sdk.util.StringUtils;

public class GraphManager {
	public GraphManager(String tenantId,String appId,String appSecret) {
		setGraphWrapper( new GraphWrapper(tenantId, appId, appSecret) );
	}	
	public String setDeviceCategoryToMdeDevices(Logger log) { 
		log.info("Getting all MDE Devices...");
		String response = null;
		ArrayList<JSONObject> mdeDevicesToHandleManually = null;
		
		ArrayList<JSONObject> mdeDevices = getGraphWrapper().getMdeDevices();
		if (mdeDevices != null) {
			log.info(""+mdeDevices.size());
			log.info("Extracting MDE Devices that are registered on AAD and do not have tags");
			ArrayList<JSONObject> mdeDevicesJsonUntagged = getUntaggedAadMdeDevices(mdeDevices);
			log.info(""+mdeDevicesJsonUntagged.size());
			mdeDevicesToHandleManually = setTagsToMdeDevices(mdeDevicesJsonUntagged,log);
		}
		if ( mdeDevicesToHandleManually.size() > 0 ) {
			response = getResponseForMdeDevicesToHandleManually(mdeDevicesToHandleManually);
		}		
		else {
			response = "{ \"manuallyConfiguredMdeAadDevices\": 0 }";
		}
		return response;
	}
	
	private String getResponseForMdeDevicesToHandleManually(ArrayList<JSONObject> mdeDevicesToHandleManually) {
		 StringBuilder sb = new StringBuilder(" { ");
		 sb.append(" { \"manuallyConfiguredMdeAadDevicesLength\": "+mdeDevicesToHandleManually.size()+" , ");
		 sb.append(" \"manuallyConfiguredMdeAadDevices\": [ ");
		 for (JSONObject mdeDeviceToHandleManually :  mdeDevicesToHandleManually) {
         	sb.append(mdeDeviceToHandleManually.toString());
         	sb.append(",");
         }
		 sb.delete(sb.lastIndexOf(","),sb.lastIndexOf(",")+1);
		 sb.append(" ] } ");
		 return sb.toString();
	}
	
	private ArrayList<JSONObject> setTagsToMdeDevices(ArrayList<JSONObject> mdeDevicesJsonUntagged,Logger log) {
		ArrayList<JSONObject> mdeDevicesToHandleManually = new ArrayList<JSONObject>();
		for ( JSONObject mdeDevice : mdeDevicesJsonUntagged ) {			
			mdeDevicesToHandleManually = setMdeTagFromAadDevice( mdeDevicesToHandleManually , mdeDevice  , log );
		}
		return mdeDevicesToHandleManually;
	}
	
	private ArrayList<JSONObject> setMdeTagFromAadDevice(ArrayList<JSONObject> mdeDevicesToHandleManually , JSONObject mdeDevice  , Logger log ) {
		String aadDeviceId = null;
		try { 
			aadDeviceId = mdeDevice.getString("aadDeviceId");
			if (!StringUtils.isBlank(aadDeviceId)) {
				Device aadDevice = getGraphWrapper().getAadDeviceByDeviceId(aadDeviceId);
				if ( aadDevice != null ) {
					log.info("Processing: "+aadDevice.displayName);
					mdeDevicesToHandleManually = processAadDeviceCategoryForMdeDevice(mdeDevicesToHandleManually , mdeDevice, aadDevice , log );
				}
				else {
					log.info("Graph API did not return an AAD Device...");
					mdeDevicesToHandleManually.add(mdeDevice);
				}
			}
			else {
				log.info("AAD device ID was not found...");
				mdeDevicesToHandleManually.add(mdeDevice);
			}
		}
		catch (org.json.JSONException e) {
			log.info("AAD device was not found...");
			mdeDevicesToHandleManually.add(mdeDevice);
		}
		return mdeDevicesToHandleManually;
	}
	
	private ArrayList<JSONObject> processAadDeviceCategoryForMdeDevice(ArrayList<JSONObject> mdeDevicesToHandleManually , JSONObject mdeDevice, Device aadDevice , Logger log ) {
		try {
			String deviceCategory = aadDevice.deviceCategory;
			if ( !StringUtils.isBlank(deviceCategory ) ) {
				log.info("Trying to set Device Category: "+deviceCategory+" to device: "+aadDevice.displayName+" mdeid: "+mdeDevice.getString("id"));
				String response = getGraphWrapper().addTag( mdeDevice.getString("id") , deviceCategory , log);
				log.info("response: "+response);	
			}
			else {
				// we need to seach from AAD device 
				log.info("Device Category was not found on AAD device: "+aadDevice.displayName);
				List<String> physicalIds = aadDevice.physicalIds;
				boolean addedTag = false;
				for ( int i = 0 ; i < physicalIds.size() ; i++ ) {
					String physicalId = physicalIds.get(i);
					if ( !StringUtils.isBlank(physicalId) ) {
						if ( physicalId.contains("[OrderId]:")) {
							int length = physicalId.length();
							int index = physicalId.indexOf(":");
							String groupTag = physicalId.substring(index+1,length);
							log.info("extracted groupTag: "+groupTag);
							String deviceCategoryFromGroupTag = getDeviceCategory(groupTag.trim());
							if ( !StringUtils.isBlank(deviceCategoryFromGroupTag) ) {
								log.info("Setting tag to: "  + mdeDevice.getString("id") +" tag: "+deviceCategoryFromGroupTag+" device: "+aadDevice.displayName);
								String response = getGraphWrapper().addTag(mdeDevice.getString("id"), deviceCategoryFromGroupTag, log);
								log.info("RESPONSE: "+response);
								addedTag = true;
							}
						}
					}
				}	
				if ( !addedTag ) {
					mdeDevicesToHandleManually.add(mdeDevice);
				}
			}
		}
		catch (	NullPointerException e ) {
			log.info("The device "+aadDevice.displayName+" does not have device category");
			mdeDevicesToHandleManually.add(mdeDevice);
		}	
		return mdeDevicesToHandleManually;
	}
	
	
	private ArrayList<JSONObject> getUntaggedAadMdeDevices(ArrayList<JSONObject> mdeDevicesJson) {
		ArrayList<JSONObject> untaggedMdeDevicesJson = new ArrayList<JSONObject>();
		for ( JSONObject mdeDevice : mdeDevicesJson ) {
			boolean isUntagged = false;
			String aadDeviceId = null;
			JSONArray tags = null;
			try { 
				aadDeviceId = mdeDevice.getString("aadDeviceId");
			}
			catch (org.json.JSONException e) {
				//e.printStackTrace();
			}
			if (!StringUtils.isBlank(aadDeviceId)) {
				tags = mdeDevice.getJSONArray("machineTags");
				if ( tags.length() <= 0 ) {
					isUntagged = true;
				}
				else {
					isUntagged = !tagsAreValid(tags);	
				}
			}
			if ( isUntagged ) {
				untaggedMdeDevicesJson.add(mdeDevice);
			}
		}
		return untaggedMdeDevicesJson;
	}
	
	private static boolean tagsAreValid(JSONArray tags) {
		boolean tagsAreValid = false;
		for (int i = 0 ; i < tags.length() ; i++) {
			String tag = tags.getString(i);
			tagsAreValid = tagIsValid(tag);
			if ( tagsAreValid ) {
				break ;
			}
		}
		return tagsAreValid;
	}
	
	private static boolean tagIsValid(String tag) {
		boolean isValid = false;
		switch (tag) {
		case "IT Department of BRIDGNORTH ALUMINIUM LTD":
			isValid = true;
			break;
		case "IT Department of CABLEL":
			isValid = true;
			break;
		case "IT Department of ELVAL":
			isValid = true;
			break;
		case "IT Department of ETEM BG":
			isValid = true;
			break;
		case "IT Department of ETEM GR":
			isValid = true;
			break;
		case "IT Department of HALCOR":
			isValid = true;
			break;
		case "IT Department of ICME":
			isValid = true;
			break;
		case "IT Department of SIDENOR":
			isValid = true;
			break;
		case "IT Department of SOFIA MED":
			isValid = true;
			break;
		case "IT Department of STEELMET":
			isValid = true;
			break;
		case "IT Department of SIDMA":
			isValid = true;
			break;
		case "IT Department of TEKA":
			isValid = true;
			break;
		case "IT Department of VIOMAL":
			isValid = true;
			break;
		case "IT Department of CPW":
			isValid = true;
			break;
		case "IT Department of CENERGY":
			isValid = true;
			break;
		case "IT Department of METALIGN GR":
			isValid = true;
			break;
		case "IT Department of TEPROMKC":
			isValid = true;
			break;
		}
		return isValid;
	}
	
	public ArrayList<WindowsAutopilotDeviceIdentity> assignProfilesToAutopilotDevices(Logger log, long startTime) {
		ArrayList<WindowsAutopilotDeviceIdentity> autopilotDevicesToManuallyConfigure = new ArrayList<WindowsAutopilotDeviceIdentity>();
		log.info("Getting all Autopilot Devices...");
		ArrayList<WindowsAutopilotDeviceIdentity> autopilotDevices = getGraphWrapper().getAutopilotDevices();
		log.info("Got all Autopilot Devices: "+autopilotDevices.size());
		int counter = 1; 		
		ArrayList<ManagedDevice> managedDevices = getGraphWrapper().getManagedDevices();
		
		for ( WindowsAutopilotDeviceIdentity autopilotDevice : autopilotDevices ) {
			// Sometimes it throws an exception here - we need to investigate !!
			try {
				autopilotDevice = getGraphWrapper().getGraphClient().deviceManagement().windowsAutopilotDeviceIdentities(autopilotDevice.id).buildRequest().expand("deploymentProfile,intendedDeploymentProfile").get();
				if ( autopilotDevice.deploymentProfile == null ) {
					WindowsAutopilotDeviceIdentity toManuallyConfigure = tryToSetDeploymentProfile(autopilotDevice , managedDevices, log);
					if ( toManuallyConfigure != null ) {
						autopilotDevicesToManuallyConfigure.add(toManuallyConfigure);
					}
				}
				else {
					// Try to check the Group Tag
					if ( StringUtils.isBlank(autopilotDevice.groupTag) ) {
						String groupTag = getGroupTagFromAssignmentProfile(autopilotDevice.deploymentProfile.displayName);
						if ( groupTag != null ) { 
							log.info("we need to set the following: "+groupTag+" device: "+autopilotDevice.serialNumber+" has profile: "+autopilotDevice.deploymentProfile.displayName);
							try { 
								getGraphWrapper().setGroupTag(groupTag, autopilotDevice.id);
								log.info("Set Group Tag: "+groupTag+" to device: "+autopilotDevice.serialNumber);
							}
							catch (Exception e) {
								log.info("we couldn't set the group tag: "+groupTag+ " device: "+autopilotDevice.serialNumber);
							}
						}
					}
					//else {
					//	log.info("The group tag found!!! device: "+autopilotDevice.serialNumber);
					//	log.info("|"+autopilotDevice.groupTag+"|");
					//}
				}
				
				long now = System.nanoTime();
				long duration = now - startTime;
				long convert = TimeUnit.SECONDS.convert(duration, TimeUnit.NANOSECONDS);
				log.info("EXEC TIME: "+convert+" DeviceNo: "+counter+"/"+autopilotDevices.size()+" serial: "+autopilotDevice.serialNumber);
				counter++;

			}
			catch (Exception e) {
				log.info("Exception thown when trying to find autopilot profiles and their deployment profiles");
			}
		}
		autopilotDevicesToManuallyConfigure.trimToSize();
		if ( autopilotDevicesToManuallyConfigure.size() > 0) {
			return autopilotDevicesToManuallyConfigure;
		}
		else {
			return null;
		}
	}
	public String getJsonDataFromWindowsAutopilotDeviceIdentity(WindowsAutopilotDeviceIdentity autopilotDevice) {
		String json = null;
		if ( autopilotDevice != null) {
			StringBuilder sb = new StringBuilder(" { ");
			sb.append(" \"SerialNumber\": ");
			sb.append("\""+autopilotDevice.serialNumber+"\",");
			if ( !autopilotDevice.managedDeviceId.equalsIgnoreCase("00000000-0000-0000-0000-000000000000") ) {
				ManagedDevice managedDevice = getGraphWrapper().getManagedDeviceById(autopilotDevice.managedDeviceId);
				sb.append(" \"ManagedDeviceName\": ");
				sb.append("\""+managedDevice.deviceName+"\",");
			}
			if ( !StringUtils.isBlank(autopilotDevice.azureAdDeviceId) ) {				
				Device aad = getGraphWrapper().getAadDeviceByDeviceId(autopilotDevice.azureAdDeviceId);
				try { 
					sb.append(" \"AadDeviceName\": ");
					sb.append("\""+aad.displayName+"\",");
				}
				catch (NullPointerException e) {
					sb.append(" \"AadDeviceId\": ");
					sb.append("\""+autopilotDevice.azureAdDeviceId+"\",");
				}
				
			}
			sb.lastIndexOf(",");
			sb.delete(sb.lastIndexOf(","),sb.lastIndexOf(",")+1);
			sb.append(" \n } ");
			json = sb.toString();
		}
		return json;
	}
	
	private WindowsAutopilotDeviceIdentity tryToSetDeploymentProfile(WindowsAutopilotDeviceIdentity autopilotDevice , ArrayList<ManagedDevice> managedDevices,  Logger log) {
		WindowsAutopilotDeviceIdentity toManuallyConfigure = null;
		if ( !fixDeploymentProfileWithManagedDevice(autopilotDevice , log) ) {
			if ( !fixDeploymentProfileWithAadDevice(autopilotDevice , log) ) {
				if ( !fixDeploymentProfileByMatchingSerial(autopilotDevice , managedDevices ,  log) ) {										
					if ( !deleteAutopilotDeviceWithoutOrderIdentifier(autopilotDevice , log) ) {
						toManuallyConfigure = autopilotDevice;
					}
				}
			}
		}
		return toManuallyConfigure;
	}
	
	private boolean deleteAutopilotDeviceWithoutOrderIdentifier(WindowsAutopilotDeviceIdentity autopilotDevice , Logger log)  {
		boolean fixed = false;
		if (StringUtils.isBlank(autopilotDevice.purchaseOrderIdentifier) ) {
			// delete the device	
			deleteAction(autopilotDevice, log);
			fixed = true;
		}
		return fixed;
	}
	
	
	private boolean fixDeploymentProfileWithManagedDevice(WindowsAutopilotDeviceIdentity autopilotDevice , Logger log) {
		boolean fixed = false;
		if ( !StringUtils.isBlank(autopilotDevice.managedDeviceId) ) {
			if ( !autopilotDevice.managedDeviceId.equalsIgnoreCase("00000000-0000-0000-0000-000000000000") ) {
				// get the device category from the managed device 
				log.info("managedDeviceId: "+autopilotDevice.managedDeviceId);
				try { 
					ManagedDevice managedDevice = getGraphWrapper().getManagedDeviceById(autopilotDevice.managedDeviceId);						
					if ( !StringUtils.isBlank(managedDevice.deviceCategoryDisplayName)) {
						fixed = configureGroupTag(autopilotDevice,managedDevice.deviceCategoryDisplayName,log);
					}
				}
				catch (Exception e) {
					fixed = false;
				}
			}
		}
		return fixed;
	}
	
	private boolean configureGroupTag(WindowsAutopilotDeviceIdentity autopilotDevice,String deviceCategory,Logger log) { 
		boolean configured = false;
		String groupTag = getGroupTag(deviceCategory);
		if ( groupTag != null) {
			log.info("GroupTag to SET: "+groupTag);
			log.info("id: "+autopilotDevice.id);
			log.info("serial: "+autopilotDevice.serialNumber);
			try { 
				getGraphWrapper().setGroupTag(groupTag, autopilotDevice.id);
				configured = true;
				log.info("GroupTag "+groupTag+" was set on device: "+autopilotDevice.serialNumber);
			}
			catch (Exception e) {
				configured = false;
				log.info("Attempt to set "+groupTag+" FAILED!");
			}
		}
		return configured;
	}
	
	private boolean fixDeploymentProfileByMatchingSerial(WindowsAutopilotDeviceIdentity autopilotDevice , ArrayList<ManagedDevice> managedDevices , Logger log) {
		boolean fixed = false;
		if ( !StringUtils.isBlank(autopilotDevice.serialNumber) ) {
			if ( managedDevices !=null && managedDevices.size() > 0 ) {
				for ( ManagedDevice managedDevice : managedDevices ) {
					if ( !StringUtils.isBlank(managedDevice.serialNumber) && !StringUtils.isBlank(managedDevice.deviceCategoryDisplayName) ) {
						if ( managedDevice.serialNumber.equalsIgnoreCase(autopilotDevice.serialNumber)) {
							log.info("We found a match: "+managedDevice.serialNumber+" device category: "+managedDevice.deviceCategoryDisplayName);
							fixed = configureGroupTag(autopilotDevice,managedDevice.deviceCategoryDisplayName,log);
							log.info("Group tag set for: "+managedDevice.serialNumber);
						}
					}
				}
			}
		}
		return fixed; 
	}
	
	private boolean fixDeploymentProfileWithAadDevice(WindowsAutopilotDeviceIdentity autopilotDevice , Logger log) {
		boolean fixed = false;
		if ( !StringUtils.isBlank(autopilotDevice.azureAdDeviceId) ) {
			try { 
				Device aadDevice = getGraphWrapper().getAadDeviceByDeviceId(autopilotDevice.azureAdDeviceId);
				if ( !StringUtils.isBlank(aadDevice.deviceCategory) ) {
					fixed = configureGroupTag(autopilotDevice,aadDevice.deviceCategory,log);
				}
			}
			catch (Exception e) {
				fixed = false;
			}
		}
		return fixed;
	}

	private String getGroupTagFromAssignmentProfile(String assignmentProfile) {
		String groupTag = null;
		switch (assignmentProfile) {
		case "BRIDGNORTH_ALUMINIUM_LTD_AUTOPILOT_Cloud":
			groupTag = "CLOUD_BRIDGNORTH_ALUMINIUM_LTD";
			break;
		case "CABLEL_AUTOPILOT_Cloud":
			groupTag = "CLOUD_CABLEL";
			break;
		case "CENERGY_AUTOPILOT_Cloud":
			groupTag = "CLOUD_CENERGY";
			break;
		case "CPW_AUTOPILOT_Cloud":
			groupTag = "CLOUD_CPW";
			break;
		case "ELVAL_AUTOPILOT_Cloud":
			groupTag = "CLOUD_ELVAL";
			break;
		case "ETEM_BG_AUTOPILOT_Cloud":
			groupTag = "CLOUD_ETEM_BG";
			break;
		case "ETEM_GR_AUTOPILOT_Cloud":
			groupTag = "CLOUD_ETEM_GR";
			break;
		case "HALCOR_AUTOPILOT_Cloud":
			groupTag = "CLOUD_HALCOR";
			break;
		case "ICME_AUTOPILOT_Cloud":
			groupTag = "CLOUD_ICME";
			break;
		case "METALIGN_GR_AUTOPILOT_Cloud":
			groupTag = "CLOUD_METALIGN_GR";
			break;
		case "SIDENOR_AUTOPILOT_Cloud":
			groupTag = "CLOUD_SIDENOR";
			break;
		case "SIDMA_AUTOPILOT_Cloud":
			groupTag = "CLOUD_SIDMA";
			break;
		case "SOFIAMED_AUTOPILOT_Cloud":
			groupTag = "CLOUD_SOFIA_MED";
			break;
		case "STEELMET_AUTOPILOT_Cloud":
			groupTag = "CLOUD_STEELMET";
			break;
		case "TEKA_AUTOPILOT_Cloud":
			groupTag = "CLOUD_TEKA";
			break;
		case "TEPROMKC_AUTOPILOT_Cloud":
			groupTag = "CLOUD_TEPROMKC";
			break;
		case "VIOMAL_AUTOPILOT_Cloud":
			groupTag = "CLOUD_VIOMAL";
			break;
		}
		return groupTag;
	}
	private String getDeviceCategory(String groupTag) {
		String deviceCategory = null;
		switch(groupTag) {
		case "CLOUD_BRIDGNORTH_ALUMINIUM_LTD":
			deviceCategory = "IT Department of BRIDGNORTH ALUMINIUM LTD";
			break;
		case "CLOUD_CABLEL":
			deviceCategory = "IT Department of CABLEL";
			break;
		case "CLOUD_ELVAL":
			deviceCategory = "IT Department of ELVAL";
			break;
		case "CLOUD_ETEM_BG":
			deviceCategory = "IT Department of ETEM BG";
			break;
		case "CLOUD_ETEM_GR":
			deviceCategory = "IT Department of ETEM GR";
			break;
		case "CLOUD_HALCOR":
			deviceCategory = "IT Department of HALCOR";
			break;
		case "CLOUD_ICME":
			deviceCategory = "IT Department of ICME";
			break;
		case "CLOUD_SIDENOR":
			deviceCategory = "IT Department of SIDENOR";
			break;
		case "CLOUD_SOFIA_MED":
			deviceCategory = "IT Department of SOFIA MED";
			break;
		case "CLOUD_STEELMET":
			deviceCategory = "IT Department of STEELMET";
			break;
		case "CLOUD_SIDMA":
			deviceCategory = "IT Department of SIDMA";
			break;
		case "CLOUD_TEKA":
			deviceCategory = "IT Department of TEKA";
			break;
		case "CLOUD_VIOMAL":
			deviceCategory = "IT Department of VIOMAL";
			break;
		case "CLOUD_CPW":
			deviceCategory = "IT Department of CPW";
			break;
		case "CLOUD_CENERGY":
			deviceCategory = "IT Department of CENERGY";
			break;
		case "CLOUD_METALIGN_GR":
			deviceCategory = "IT Department of METALIGN GR";
			break;
		case "CLOUD_TEPROMKC":
			deviceCategory = "IT Department of TEPROMKC";
			break;
		}
		return deviceCategory;
	}
	
	private String getGroupTag(String deviceCategory) {
		String groupTag = null;
		switch (deviceCategory) {
		case "IT Department of BRIDGNORTH ALUMINIUM LTD":
			groupTag = "CLOUD_BRIDGNORTH_ALUMINIUM_LTD";
			break;
		case "IT Department of CABLEL":
			groupTag = "CLOUD_CABLEL";
			break;
		case "IT Department of ELVAL":
			groupTag = "CLOUD_ELVAL";
			break;
		case "IT Department of ETEM BG":
			groupTag = "CLOUD_ETEM_BG";
			break;
		case "IT Department of ETEM GR":
			groupTag = "CLOUD_ETEM_GR";
			break;
		case "IT Department of HALCOR":
			groupTag = "CLOUD_HALCOR";
			break;
		case "IT Department of ICME":
			groupTag = "CLOUD_ICME";
			break;
		case "IT Department of SIDENOR":
			groupTag = "CLOUD_SIDENOR";
			break;
		case "IT Department of SOFIA MED":
			groupTag = "CLOUD_SOFIA_MED";
			break;
		case "IT Department of STEELMET":
			groupTag = "CLOUD_STEELMET";
			break;
		case "IT Department of SIDMA":
			groupTag = "CLOUD_SIDMA";
			break;
		case "IT Department of TEKA":
			groupTag = "CLOUD_TEKA";
			break;
		case "IT Department of VIOMAL":
			groupTag = "CLOUD_VIOMAL";
			break;
		case "IT Department of CPW":
			groupTag = "CLOUD_CPW";
			break;
		case "IT Department of CENERGY":
			groupTag = "CLOUD_CENERGY";
			break;
		case "IT Department of METALIGN GR":
			groupTag = "CLOUD_METALIGN_GR";
			break;
		case "IT Department of TEPROMKC":
			groupTag = "CLOUD_TEPROMKC";
			break;
		}
		return groupTag;
	}
	
	private DeleteAutopilotDeviceAction deleteAction(WindowsAutopilotDeviceIdentity autopilotDevice,Logger log) {
		DeleteAutopilotDeviceAction action = new DeleteAutopilotDeviceAction();
		if ( deleteAssociatedManagedDevice(autopilotDevice) ) {
			log.info("Managed Device: "+autopilotDevice.managedDeviceId+" will be deleted");
			String managedDeviceName = null;
			try {
				ManagedDevice device = getGraphWrapper().getGraphClient().deviceManagement().managedDevices(autopilotDevice.managedDeviceId).buildRequest().get();
				managedDeviceName = device.deviceName;
				log.info("Managed Device: "+device.deviceName);
				getGraphWrapper().getGraphClient().deviceManagement().managedDevices(device.id).buildRequest().delete();
				action.setManagedDeviceId(device.id);
				action.setManagedDeviceName(managedDeviceName);
				action.setDeletedManagedDevice(true);
				log.info("Managed Device: "+autopilotDevice.managedDeviceId+" was deleted");
			}
			catch (Exception e) {
				action.setManagedDeviceId(autopilotDevice.managedDeviceId);
				if ( managedDeviceName != null) {
					action.setManagedDeviceName(managedDeviceName);
				}
				else {
					action.setManagedDeviceName(null);
				}
				action.setDeletedManagedDevice(false);
				log.info("Managed Device: "+autopilotDevice.managedDeviceId+" WAS NOT DELETED!!!!");
			}
		}
		else {
			action.setManagedDeviceId(null);
			action.setManagedDeviceName(null);
			action.setDeletedManagedDevice(true);
		}
		// Delete Autopilot Device
		log.info("Autopilot Device: "+autopilotDevice.serialNumber+" will be deleted");
		log.info("Autopilot Device: "+autopilotDevice.deviceFriendlyName+" will be deleted");
		log.info("Autopilot Device: "+autopilotDevice.displayName+" will be deleted");
		try {
			getGraphWrapper().getGraphClient().deviceManagement().windowsAutopilotDeviceIdentities(autopilotDevice.id).buildRequest().delete();	
			action.setAutopilotId(autopilotDevice.id);
			action.setAutopilotSerial(autopilotDevice.serialNumber);
			action.setDeletedManagedDevice(true);
			log.info("Autopilot Device: "+autopilotDevice.serialNumber+" was deleted");
		}
		catch ( Exception e) {
			action.setAutopilotId(autopilotDevice.id);
			action.setAutopilotSerial(autopilotDevice.serialNumber);
			action.setDeletedManagedDevice(false);
			log.info("Managed Device: "+autopilotDevice.serialNumber+" WAS NOT DELETED!!!!");
		}
		// Delete Azure AD Device
		if ( deleteAssociatedAadDevice(autopilotDevice) ) {
			log.info("AAD Device: "+autopilotDevice.azureAdDeviceId+" will be deleted");
			String aadDeviceName = null;
			try {
				Device device = getGraphWrapper().getGraphClient().devices(autopilotDevice.azureAdDeviceId).buildRequest().get();
				aadDeviceName = device.displayName;
				log.info("AAD Device: "+aadDeviceName);
				getGraphWrapper().getGraphClient().devices(device.id).buildRequest().delete();
				action.setAadDeviceId(device.id);
				action.setAadDeviceName(aadDeviceName);
				action.setDeletedAadDevice(true);
			}
			catch (Exception e) {
				log.info("AAD Device: "+autopilotDevice.azureAdDeviceId+" WAS NOT DELETED!!!!");
				action.setAadDeviceId(autopilotDevice.azureAdDeviceId);
				if ( aadDeviceName != null ) {
					action.setAadDeviceName(aadDeviceName);
				}
				else {
					action.setAadDeviceName(null);	
				}
				action.setDeletedAadDevice(false);
			}
		}
		else {
			action.setAadDeviceId(null);
			action.setAadDeviceName(null);
			action.setDeletedAadDevice(true);
		}

		return action;
	}
	
	
	public ArrayList<DeleteAutopilotDeviceAction> deleteAutopilotDevicesWithoutSerial(Logger log) {
		log.info("Getting all Autopilot Devices...");
		ArrayList<WindowsAutopilotDeviceIdentity> autopilotDevices = getGraphWrapper().getAutopilotDevices();
		log.info("Got all Autopilot Devices: "+autopilotDevices.size());
		ArrayList<DeleteAutopilotDeviceAction> actions = new ArrayList<DeleteAutopilotDeviceAction>(); 
		for ( WindowsAutopilotDeviceIdentity autopilotDevice : autopilotDevices ) {
			if ( deleteAutopilotDevice(autopilotDevice) ) {
				DeleteAutopilotDeviceAction action = deleteAction(autopilotDevice,log);
				if ( action != null ) {
					actions.add(action);
				}
			}
		}
		actions.trimToSize();
		if ( actions.size() > 0 ) {
			return actions;
		}
		else {
			return null;
		}
	}
	
	private boolean deleteAssociatedAadDevice(WindowsAutopilotDeviceIdentity autopilotDevice) {
		boolean deleteAssociatedAadDevice = false;
		if ( !StringUtils.isBlank(autopilotDevice.azureAdDeviceId) ) {
			deleteAssociatedAadDevice = true;
		}
		return deleteAssociatedAadDevice;
	}
	
	private boolean deleteAssociatedManagedDevice(WindowsAutopilotDeviceIdentity autopilotDevice) {
		boolean deleteAssociatedManagedDevice = false;
		if ( !StringUtils.isBlank(autopilotDevice.managedDeviceId) ) {
			if ( !autopilotDevice.managedDeviceId.equalsIgnoreCase("00000000-0000-0000-0000-000000000000") ) { 
				deleteAssociatedManagedDevice = true;
			}
		}
		return deleteAssociatedManagedDevice;
	}
	
	private boolean deleteAutopilotDevice(WindowsAutopilotDeviceIdentity autopilotDevice) {
		boolean deleteAutopilotDevice = false;
		if ( StringUtils.isBlank(autopilotDevice.serialNumber) ) {
			deleteAutopilotDevice = true;
		}
		else {
			String oneOrMoreSpaces = "\\s++";
			Pattern regex = Pattern.compile(oneOrMoreSpaces);
			Matcher regexMatcher = regex.matcher(autopilotDevice.serialNumber);
			if ( regexMatcher.find() ) {
				deleteAutopilotDevice = true;
			}
			else {
				if ( autopilotDevice.serialNumber.equalsIgnoreCase("empty") ) {
					deleteAutopilotDevice = true;
				}
			}
		}
		return deleteAutopilotDevice;
	}
	
	public ArrayList<ManagedDevice> setDeviceCategoryToManagedDevices(Logger log) { 
		ArrayList<ManagedDevice> managedDevicesToManuallyConfigure = new ArrayList<ManagedDevice>();
		log.info("Getting all Managed Devices...");
		ArrayList<ManagedDevice> managedDevices = getGraphWrapper().getManagedDevices();
		log.info("Got all Managed Devices: "+managedDevices.size());
		for ( ManagedDevice managedDevice : managedDevices ) {
			String deviceCategory = null;
			boolean configured = true;
			if ( StringUtils.isBlank(managedDevice.deviceCategoryDisplayName) ) {
				deviceCategory = searchDeviceCategorForManagedDevice(managedDevice,log);
				configured = configureDeviceCategory(deviceCategory,managedDevice,log);
			}
			else {
				if ( !managedDevice.deviceCategoryDisplayName.startsWith("IT Department of ") ) {
					deviceCategory = searchDeviceCategorForManagedDevice(managedDevice,log);
					configured = configureDeviceCategory(deviceCategory,managedDevice,log);
				}
			}
			if ( !configured ) {
				managedDevicesToManuallyConfigure.add(managedDevice);
			}
		}
		managedDevicesToManuallyConfigure.trimToSize();
		if ( managedDevicesToManuallyConfigure.size() > 0 ) {
			return managedDevicesToManuallyConfigure;
		}
		else {
			return null;
		}
	}
	private boolean configureDeviceCategory(String deviceCategoryName,ManagedDevice managedDevice,Logger log) {
		boolean configured = false;
		if ( deviceCategoryName != null) {
			if ( deviceCategoryName.startsWith("IT Department of ")) {
				log.info("Search for the ID of "+deviceCategoryName);
				String deviceCategoryId = searchManagedDeviceCategoryId(deviceCategoryName);
				log.info("Found ID: "+deviceCategoryId );
				DeviceCategory deviceCategory = new DeviceCategory();
				deviceCategory.additionalDataManager().put("@odata.id", new JsonPrimitive("https://graph.microsoft.com/beta/deviceManagement/deviceCategories/"+deviceCategoryId));
				try {
					new DeviceCategoryRequestBuilder("https://graph.microsoft.com/beta/deviceManagement/managedDevices/"+managedDevice.id+"/deviceCategory/$ref", getGraphWrapper().getGraphClient(),null).buildRequest().put(deviceCategory);		
					configured = true;
				}
				catch (Exception e) {
					configured = false;
					log.info(e.toString());
				}
			}
		}
		return configured;
	}
	private String searchManagedDeviceCategoryId(String deviceCategoryName) { 
		String managedDeviceCategoryId = null;
		ArrayList<DeviceCategory> managedDeviceCategories = null;
		try {
			managedDeviceCategories = getGraphWrapper().getDeviceCategories();
			found:
			for ( DeviceCategory deviceCategory : managedDeviceCategories ) {
				if ( !StringUtils.isBlank(deviceCategory.displayName) && deviceCategory.displayName.equals(deviceCategoryName) ) {
					managedDeviceCategoryId = deviceCategory.id;
					break found;
				}
			}
		}
		catch (Exception e) {
			managedDeviceCategoryId = null;
		}
		return managedDeviceCategoryId;
	}
	
	private String searchDeviceCategorForManagedDevice(ManagedDevice managedDevice,Logger log) {
		String deviceCategory = null;
		log.info("Found Managed Device without Device Category: "+managedDevice.managedDeviceName);
		// check aad device category
		if ( !StringUtils.isBlank(managedDevice.azureActiveDirectoryDeviceId) && deviceCategory == null ) {
			log.info("Managed Device - associated Azure AD device: "+managedDevice.azureADDeviceId);
			deviceCategory = getDeviceCategoryFromAadDevice(managedDevice.azureADDeviceId);
			log.info("AAD Device Category: "+deviceCategory);
		}
		// check owner domain name
		if ( !StringUtils.isBlank(managedDevice.userPrincipalName) && deviceCategory == null ) {
			log.info("Managed Device - associated userPrincipalName: "+managedDevice.userPrincipalName);
			if ( managedDevice.userPrincipalName.contains("@") ) {
				deviceCategory = getDeviceCategoryFromDomainName((managedDevice.userPrincipalName.substring(managedDevice.userPrincipalName.indexOf("@")+1)).toLowerCase());
				log.info("User Principal Domain Device Category: "+deviceCategory);
			}
		}
		// check hostname - in case the managedDeviceName is the same as deviceName
		if ( !StringUtils.isBlank(managedDevice.managedDeviceName) && deviceCategory == null ) {
			log.info("Managed Device - hostname for finding Category: "+managedDevice.managedDeviceName);
			if ( managedDevice.managedDeviceName.length() > 3 ) {
				deviceCategory = getDeviceCategoryFromHostName(managedDevice.managedDeviceName.substring(0,3).toUpperCase());
				log.info("Hostname extracted Device Category: "+deviceCategory);
			}
		}
		// check hostname - in case the managedDeviceName is NOT the same as deviceName
		if ( !StringUtils.isBlank(managedDevice.deviceName) && deviceCategory == null ) {
			log.info("Managed Device - hostname for finding Category: "+managedDevice.deviceName);
			if ( managedDevice.deviceName.length() > 3 ) {
				deviceCategory = getDeviceCategoryFromHostName(managedDevice.deviceName.substring(0,3).toUpperCase());
				log.info("Hostname extracted Device Category: "+deviceCategory);
			}
		}
		return deviceCategory;
	}
	private String getDeviceCategoryFromHostName(String hostname) {
		String deviceCategory = null;
		switch ( hostname ) {
		case "BRG":
			deviceCategory  = "IT Department of BRIDGNORTH ALUMINIUM LTD";
			break;
		case "CBL":
		case "CPW":
		case "ELE":
		case "FLG":
		case "HCG":
		case "HCP":
		case "HCT":
			deviceCategory = "IT Department of CABLEL";
			break;
		case "ELV": 
			deviceCategory = "IT Department of ELVAL";
			break;
		case "EGD":
		case "EGL":
		case "EMO":
			deviceCategory = "IT Department of ETEM BG";
			break;
		case "ETG":
			deviceCategory = "IT Department of ETEM GR";
			break;
		case "ELK":
		case "FTC":
		case "HLC":
			deviceCategory = "IT Department of HALCOR";
			break;
		case "ICM":
		case "ICR":
			deviceCategory = "IT Department of ICME";
			break;
		case "MLG":
		case "MTL":
			deviceCategory = "IT Department of METALIGN GR";
			break;
		case "AEB":
		case "AEG":
		case "AEI":
		case "ANG":
		case "ANM":
		case "DIO":
		case "DOJ":
		case "DSN":
		case "ERL":
		case "ETL":
		case "IBS":
		case "INB":
		case "INS":
		case "PRK":
		case "SGC":
		case "SGG":
		case "SID":
		case "STO":
		case "SVL":
			deviceCategory = "IT Department of SIDENOR";
			break;
		case "SFB":
		case "SFM":
			deviceCategory = "IT Department of SOFIA MED";
			break;
		case "BM1":
		case "BM2":
		case "BRS":
		case "BSM":
		case "EIA":
		case "ERG":
		case "FMN":
		case "KFM":
		case "KLV":
		case "KVL":
		case "MGC":
		case "MTG":
		case "NVL":
		case "RNL":
		case "STG":
		case "STL":
		case "TKA":
		case "VTR":
			deviceCategory = "IT Department of STEELMET";
			break;
		case "MKC":
		case "TPD":
		case "TPM":
			deviceCategory = "IT Department of TEPROMKC";
			break;
		case "VML":
			deviceCategory = "IT Department of VIOMAL";
			break;
		}
		return deviceCategory;
	}
	private String getDeviceCategoryFromAadDevice(String aadObjectId) { 
		String deviceCategory = null;
		try {
			Device device = getGraphWrapper().getGraphClient().devices(aadObjectId).buildRequest().get();
			if ( !device.deviceCategory.isEmpty() && !device.deviceCategory.isBlank() ) {
				if ( device.deviceCategory.startsWith("IT Department of ")) {
					deviceCategory = device.deviceCategory;
				}
			}
		}
		catch (Exception e) {
			deviceCategory = null;
		}
		return deviceCategory;
	}	

	private String getDeviceCategoryFromDomainName(String domainName) {
		String deviceCategory = null;
		switch (domainName) {
		case "bridgnorthaluminium.co.uk":
			deviceCategory = "IT Department of BRIDGNORTH ALUMINIUM LTD";
		case "cenergyholdings.com":
		case "cpw.gr":
		case "hellenic-cables.com":
			deviceCategory = "IT Department of CABLEL";
			break;
		case "anoxal.gr":
		case "elval.com":
		case "elval-colour.com":
		case "elvalhalcor.com":
		case "symetal.gr":
			deviceCategory = "IT Department of ELVAL";
			break;
		case "etemgestamp.com":
			deviceCategory = "IT Department of ETEM BG";
			break;
		case "etem.com":
			deviceCategory = "IT Department of ETEM GR";
			break;
		case "cablelwires.com":
		case "elkeme.gr":
		case "epirusmetalworks.com":
		case "halcor.com":
			deviceCategory = "IT Department of HALCOR";
			break;
		case "metalign.eu":
			deviceCategory = "IT Department of METALIGN GR";
			break;
		case "aeiforos.bg":
		case "aeiforos.gr":
		case "anamet.gr":
		case "dio-pernik.bg":
		case "dojransteel.com":
		case "erlikon.gr":
		case "inosbalkan.com":
		case "novometal.mk":
		case "sidenor.gr":
		case "sideral.vionet.gr":
		case "sovel.gr":
		case "stomana.bg":
			deviceCategory = "IT Department of SIDENOR";
			break;
		case "sofiamed.com":
			deviceCategory = "IT Department of SOFIA MED";
			break;
		case "alurame.com":
		case "base-metal.com.tr":
		case "copperalliance.gr":
		case "ergosteel.gr":
		case "fomentos.com":
		case "genecos.fr":
		case "hellenicproduction.org":
		case "internationaltrade.com":
		case "marewest.gr":
		case "metalagencies.com":
		case "msvf.gr":
		case "noval-property.com":
		case "reynolds-cuivre.fr":
		case "riverwest.gr":
		case "steelmet.gr":
		case "steelmet.ro":
		case "steelmetpropertyservices.com":
		case "vienersa.gr":
		case "viexalsa.gr":
		case "viohalco.com":
		case "vithoulkascompass.com":
		case "vitruvit.gr":
			deviceCategory = "IT Department of STEELMET";
			break;
		case "teka.vionet.gr":
			deviceCategory = "IT Department of TEKA";
			break;
		case "tepromkc.com":
			deviceCategory = "IT Department of TEPROMKC";
			break;
		case "viomal.com":
			deviceCategory = "IT Department of VIOMAL";
			break;
		}
		return deviceCategory;
	}
		
	public GraphWrapper getGraphWrapper() {
		return graphWrapper;
	}

	public void setGraphWrapper(GraphWrapper graphWrapper) {
		this.graphWrapper = graphWrapper;
	}
	
	public String managedDevicesToJson(ArrayList<ManagedDevice> managedDevices) {
		managedDevices.trimToSize();
    	int size = managedDevices.size();
    	String jsonArray = "";
    	for ( int i = 0 ; i <= size-1 ; i++ ) {
    		if ( i == size-1 ) {
         		jsonArray += "{ \"name\":  \""+managedDevices.get(i).deviceName+"\" }";
         	}
         	else {
         		jsonArray += "{ \"name\":  \""+managedDevices.get(i).deviceName+"\" },\n";
         		
         	}
        }
    	return "{ \n \"managedDevicesToManuallyConfigureLength\": "+size+", \n  \n \"managedDevicesToManuallyConfigure\": [ \n"+jsonArray+" \n ] \n }";
    }
	
	public String deleteAutopilotActionsToJson(ArrayList<DeleteAutopilotDeviceAction> actions) {
		actions.trimToSize();
    	int size = actions.size();
    	String jsonArray = "";
    	for ( int i = 0 ; i <= size-1 ; i++ ) {
    		if ( i == size-1 ) {
         		jsonArray += actions.get(i).toJson();
         	}
         	else {
         		jsonArray += actions.get(i).toJson()+",";
         		
         	}
        }
    	return "{ \n \"deletedDevicesThatRequireAttentionLength\": "+size+", \n  \n \"deletedDevicesThatRequireAttention\": [ \n"+jsonArray+" \n ] \n }";
    }
	public ArrayList<DeleteAutopilotDeviceAction> removeDeletedDevices(ArrayList<DeleteAutopilotDeviceAction> actions) {
		ArrayList<DeleteAutopilotDeviceAction> autopilotDevicesThatNeedAttention = new ArrayList<DeleteAutopilotDeviceAction>();
		for ( DeleteAutopilotDeviceAction action : actions ) {
			if ( !action.isDeletedAadDevice() || !action.isDeletedAutopilotDevice() || !action.isDeletedManagedDevice()   ) {
				autopilotDevicesThatNeedAttention.add(action);
			}
		}
		autopilotDevicesThatNeedAttention.trimToSize();
		if ( autopilotDevicesThatNeedAttention.size() > 0 ) {
			return autopilotDevicesThatNeedAttention;
		}
		else {
			return null;
		}
	}
	
	private GraphWrapper graphWrapper;
}