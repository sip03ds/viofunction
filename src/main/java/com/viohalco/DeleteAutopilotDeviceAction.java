package com.viohalco;

public class DeleteAutopilotDeviceAction {
	public DeleteAutopilotDeviceAction(String managedDeviceName,String managedDeviceId,boolean deletedManagedDevice,String aadDeviceName,String aadDeviceId,boolean deletedAadDevice,String autopilotSerial,String autopilotId,boolean deletedAutopilotDevice) {
		setManagedDeviceName(managedDeviceName);
		setManagedDeviceId(managedDeviceId);
		setDeletedManagedDevice(deletedManagedDevice);
		setAadDeviceName(aadDeviceName);
		setAadDeviceId(aadDeviceId);
		setDeletedAadDevice(deletedAadDevice);
		setAutopilotSerial(autopilotSerial);
		setAutopilotId(autopilotId);
		setDeletedAutopilotDevice(deletedAutopilotDevice);
	}
	public DeleteAutopilotDeviceAction() {
		
	}
	public String toJson() { 
		if ( !deletedManagedDevice || !deletedAadDevice || !deletedAutopilotDevice ) { 
			StringBuilder sb = new StringBuilder(" { ");	
			if ( !deletedManagedDevice  ) {
				sb.append("\n");
				sb.append(" \"ManagedDeviceName\": ");
				sb.append("\""+managedDeviceName+"\",");
				sb.append(" \"ManagedDeviceId\": ");
				sb.append("\""+managedDeviceId+"\",");
			}
			if ( !deletedAadDevice ) {
				sb.append("\n");
				sb.append("\"AADDeviceName\": ");
				sb.append("\""+aadDeviceName+"\",");
				sb.append("\"AADDeviceObjectId\": ");
				sb.append("\""+aadDeviceId+"\",");
			}
			if ( !deletedAutopilotDevice ) {
				sb.append("\n");
				sb.append("\"AutopilotSerial\": ");
				sb.append("\""+autopilotSerial+"\",");
			}
			sb.lastIndexOf(",");
			sb.delete(sb.lastIndexOf(","),sb.lastIndexOf(","));
			sb.append(" \n } ");
			return sb.toString();
		}
		else {
			return null;
		}
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder(" [DeleteAutopilotDeviceAction] ");
		if ( !deletedManagedDevice  ) {
			sb.append("\n ManagedDeviceName: ");
			sb.append(managedDeviceName);
		}
		if ( !deletedAadDevice ) {
			sb.append("\n AADDeviceName: ");
			sb.append(aadDeviceName);
		}
		if ( !deletedAutopilotDevice ) {
			sb.append("\n AutopilotSerial: ");
			sb.append(autopilotSerial);
		}
		return sb.toString();
	}
	
	public String getManagedDeviceName() {
		return managedDeviceName;
	}
	public void setManagedDeviceName(String managedDeviceName) {
		this.managedDeviceName = managedDeviceName;
	}
	public String getManagedDeviceId() {
		return managedDeviceId;
	}
	public void setManagedDeviceId(String managedDeviceId) {
		this.managedDeviceId = managedDeviceId;
	}
	public boolean isDeletedManagedDevice() {
		return deletedManagedDevice;
	}
	public void setDeletedManagedDevice(boolean deletedManagedDevice) {
		this.deletedManagedDevice = deletedManagedDevice;
	}
	public String getAadDeviceName() {
		return aadDeviceName;
	}
	public void setAadDeviceName(String aadDeviceName) {
		this.aadDeviceName = aadDeviceName;
	}
	public String getAadDeviceId() {
		return aadDeviceId;
	}
	public void setAadDeviceId(String aadDeviceId) {
		this.aadDeviceId = aadDeviceId;
	}
	public boolean isDeletedAadDevice() {
		return deletedAadDevice;
	}
	public void setDeletedAadDevice(boolean deletedAadDevice) {
		this.deletedAadDevice = deletedAadDevice;
	}
	public String getAutopilotSerial() {
		return autopilotSerial;
	}
	public void setAutopilotSerial(String autopilotSerial) {
		this.autopilotSerial = autopilotSerial;
	}
	public String getAutopilotId() {
		return autopilotId;
	}
	public void setAutopilotId(String autopilotId) {
		this.autopilotId = autopilotId;
	}
	public boolean isDeletedAutopilotDevice() {
		return deletedAutopilotDevice;
	}
	public void setDeletedAutopilotDevice(boolean deletedAutopilotDevice) {
		this.deletedAutopilotDevice = deletedAutopilotDevice;
	}

	private String managedDeviceName;
	private String managedDeviceId;
	private boolean deletedManagedDevice;

	private String aadDeviceName;
	private String aadDeviceId;
	private boolean deletedAadDevice;

	private String autopilotSerial;
	private String autopilotId;
	private boolean deletedAutopilotDevice;
}