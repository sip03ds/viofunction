package com.viohalco;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.TokenCredentialAuthProvider;
import com.microsoft.graph.models.Device;
import com.microsoft.graph.models.DeviceCategory;
import com.microsoft.graph.models.DirectoryObject;
import com.microsoft.graph.models.ManagedDevice;
import com.microsoft.graph.models.WindowsAutopilotDeploymentProfile;
import com.microsoft.graph.models.WindowsAutopilotDeviceIdentity;
import com.microsoft.graph.models.WindowsAutopilotDeviceIdentityUpdateDevicePropertiesParameterSet;
import com.microsoft.graph.requests.DeviceCategoryCollectionPage;
import com.microsoft.graph.requests.DeviceCategoryCollectionRequestBuilder;
import com.microsoft.graph.requests.DeviceCollectionPage;
import com.microsoft.graph.requests.DeviceCollectionRequestBuilder;
import com.microsoft.graph.requests.DirectoryObjectCollectionWithReferencesPage;
import com.microsoft.graph.requests.DirectoryObjectCollectionWithReferencesRequestBuilder;
import com.microsoft.graph.requests.GraphServiceClient;
import com.microsoft.graph.requests.ManagedDeviceCollectionPage;
import com.microsoft.graph.requests.ManagedDeviceCollectionRequestBuilder;
import com.microsoft.graph.requests.WindowsAutopilotDeploymentProfileCollectionPage;
import com.microsoft.graph.requests.WindowsAutopilotDeploymentProfileCollectionRequestBuilder;
import com.microsoft.graph.requests.WindowsAutopilotDeviceIdentityCollectionPage;
import com.microsoft.graph.requests.WindowsAutopilotDeviceIdentityCollectionRequestBuilder;

import okhttp3.Request;

public class GraphWrapper {
	public GraphWrapper(String tenantId,String appId,String appSecret) {
		clientSecretCredential = new ClientSecretCredentialBuilder().clientId(appId).clientSecret(appSecret).tenantId(tenantId).build();
		List<String> graphUserScopes = Arrays.asList("https://graph.microsoft.com/.default");
		tokenCredAuthProvider = new TokenCredentialAuthProvider(graphUserScopes, clientSecretCredential);
		graphClient = GraphServiceClient.builder().authenticationProvider(tokenCredAuthProvider).buildClient();
	}
	
	public ArrayList<WindowsAutopilotDeploymentProfile> getAutopilotProfiles() { 
		ArrayList<WindowsAutopilotDeploymentProfile> deploymentProfiles = new ArrayList<WindowsAutopilotDeploymentProfile>();
		WindowsAutopilotDeploymentProfileCollectionPage windowsAutopilotDeploymentProfilesPage = graphClient.deviceManagement().windowsAutopilotDeploymentProfiles().buildRequest().get();
		while (windowsAutopilotDeploymentProfilesPage != null) {
			final List<WindowsAutopilotDeploymentProfile> deploymentProfilesCurrent = windowsAutopilotDeploymentProfilesPage.getCurrentPage();
			for ( WindowsAutopilotDeploymentProfile deploymentProfile : deploymentProfilesCurrent ) {
				deploymentProfiles.add(deploymentProfile);
			}
			final WindowsAutopilotDeploymentProfileCollectionRequestBuilder nextPage = windowsAutopilotDeploymentProfilesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				windowsAutopilotDeploymentProfilesPage = nextPage.buildRequest().get();
			}
		}
		deploymentProfiles.trimToSize(); 
		return deploymentProfiles;
	}
	
	public ArrayList<DirectoryObject> getRegisteredOwners(String aadObjectId) {
		ArrayList<DirectoryObject> registeredOwners = new ArrayList<DirectoryObject>();
		DirectoryObjectCollectionWithReferencesPage registeredOwnersPage = graphClient.devices(aadObjectId).registeredOwners().buildRequest().get();
		while (registeredOwnersPage != null) {
			final List<DirectoryObject> directoryObjects = registeredOwnersPage.getCurrentPage();
			for ( DirectoryObject registeredOwner : directoryObjects ) {
				registeredOwners.add(registeredOwner);
			}
			final DirectoryObjectCollectionWithReferencesRequestBuilder nextPage = registeredOwnersPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				registeredOwnersPage = nextPage.buildRequest().get();
			}
		}
		registeredOwners.trimToSize(); 
		return registeredOwners;
	}
	
	public ArrayList<Device> getAadDevices() {
		ArrayList<Device> aadDevices = new ArrayList<Device>();	
		DeviceCollectionPage devicesPage = graphClient.devices().buildRequest().get();
		while (devicesPage != null) {
			final List<Device> devices = devicesPage.getCurrentPage();
			for ( Device dev : devices ) {
				aadDevices.add(dev);
			}
			final DeviceCollectionRequestBuilder nextPage = devicesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				devicesPage = nextPage.buildRequest().get();
			}
		}
		aadDevices.trimToSize(); 
		return aadDevices;
	}
	
	public ManagedDevice getManagedDeviceById(String managedDeviceId) {
		return graphClient.deviceManagement().managedDevices(managedDeviceId).buildRequest().get();
	}
	
	public void setGroupTag(String groupTag , String windowsAutopilotDeviceIdentityId ) {
		graphClient.deviceManagement().windowsAutopilotDeviceIdentities(windowsAutopilotDeviceIdentityId).updateDeviceProperties(WindowsAutopilotDeviceIdentityUpdateDevicePropertiesParameterSet.newBuilder().withUserPrincipalName(null).withAddressableUserName(null).withGroupTag(groupTag).withDisplayName(null).withDeviceAccountUpn(null).withDeviceAccountPassword(null).withDeviceFriendlyName(null).build()).buildRequest().post();
	}
	
	public Device getAadDeviceByDeviceId(String aadDeviceId) { 
		ArrayList<Device> aadDevices = new ArrayList<Device>();	
		DeviceCollectionPage devicesPage = graphClient.devices().buildRequest().filter("DeviceId eq '"+aadDeviceId+"'").get();
		while (devicesPage != null) {
			final List<Device> devices = devicesPage.getCurrentPage();
			for ( Device dev : devices ) {
				aadDevices.add(dev);
			}
			final DeviceCollectionRequestBuilder nextPage = devicesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				devicesPage = nextPage.buildRequest().get();
			}
		}
		aadDevices.trimToSize(); 
		Device found;
		if ( aadDevices.size() > 0 && aadDevices.size() < 2 ) {
			found = aadDevices.get(0);
		}
		else {
			found = null;
		}
		return found;
	}
	
	public ArrayList<ManagedDevice> getManagedDevices() { 
		ArrayList<ManagedDevice> managedDevices = new ArrayList<ManagedDevice>();
		ManagedDeviceCollectionPage devicesPage = graphClient.deviceManagement().managedDevices().buildRequest().get();
		while (devicesPage != null) {
			final List<ManagedDevice> devices = devicesPage.getCurrentPage();
			for ( ManagedDevice dev : devices ) {
				managedDevices.add(dev);
			}
			final ManagedDeviceCollectionRequestBuilder nextPage = devicesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				devicesPage = nextPage.buildRequest().get();
			}
		}
		managedDevices.trimToSize();
		return managedDevices;
	}
	
	public ArrayList<DeviceCategory> getDeviceCategories() { 
		ArrayList<DeviceCategory> managedDeviceCategories = new ArrayList<DeviceCategory>();
		DeviceCategoryCollectionPage deviceCategoriesPage = graphClient.deviceManagement().deviceCategories().buildRequest().get();
		while (deviceCategoriesPage != null) {
			final List<DeviceCategory> deviceCategories = deviceCategoriesPage.getCurrentPage(); 
			for ( DeviceCategory dev : deviceCategories ) {
				managedDeviceCategories.add(dev);
			}
			final DeviceCategoryCollectionRequestBuilder nextPage = deviceCategoriesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				deviceCategoriesPage = nextPage.buildRequest().get();
			}
		}
		managedDeviceCategories.trimToSize(); 
		return managedDeviceCategories;
	}
	
	
	public ArrayList<DeviceCategory> getManagedDeviceCategories() { 
		ArrayList<DeviceCategory> managedDeviceCategories = new ArrayList<DeviceCategory>();
		DeviceCategoryCollectionPage deviceCategoriesPage = graphClient.deviceManagement().deviceCategories().buildRequest().get();
		while (deviceCategoriesPage != null) {
			final List<DeviceCategory> deviceCategories = deviceCategoriesPage.getCurrentPage(); 
			for ( DeviceCategory dev : deviceCategories ) {
				managedDeviceCategories.add(dev);
			}
			final DeviceCategoryCollectionRequestBuilder nextPage = deviceCategoriesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				deviceCategoriesPage = nextPage.buildRequest().get();
			}
		}
		managedDeviceCategories.trimToSize(); 
		return managedDeviceCategories;
	}
	
	public ArrayList<WindowsAutopilotDeviceIdentity> getAutopilotDevices() { 
		ArrayList<WindowsAutopilotDeviceIdentity> autopilotDevices = new ArrayList<WindowsAutopilotDeviceIdentity>();
		WindowsAutopilotDeviceIdentityCollectionPage devicesPage = graphClient.deviceManagement().windowsAutopilotDeviceIdentities().buildRequest().get();
		while (devicesPage != null) {
			final List<WindowsAutopilotDeviceIdentity> devices = devicesPage.getCurrentPage();
			for ( WindowsAutopilotDeviceIdentity dev : devices ) {
				autopilotDevices.add(dev);
			}
			final WindowsAutopilotDeviceIdentityCollectionRequestBuilder nextPage = devicesPage.getNextPage();
			if (nextPage == null) {
				break;
			}
			else {
				devicesPage = nextPage.buildRequest().get();
			}
		}
		autopilotDevices.trimToSize(); 
		return autopilotDevices;
	}
	
	public ClientSecretCredential getClientSecretCredential() {
		return clientSecretCredential;
	}

	public TokenCredentialAuthProvider getTokenCredAuthProvider() {
		return tokenCredAuthProvider;
	}

	public GraphServiceClient<Request> getGraphClient() {
		return graphClient;
	}
	
	private final ClientSecretCredential clientSecretCredential;
	private final TokenCredentialAuthProvider tokenCredAuthProvider;
	private final GraphServiceClient<Request> graphClient;
}
