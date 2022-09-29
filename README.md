# Using a Logic App with Azure Durable Functions for automating Device Synchronization among MS Products (Azure Active Directory - Endpoint Manager - Autopilot - Defender for Endpoint).
I have worked for an group organization that has many subsidiaries. 
Every subsidiary has its own IT department that should have a different degree of freedom for performing actions on Cloud MS Products. 
All companies exist under the same tenant.
Let's say that we have:
- Central IT
- Company A IT
- Company B IT

## Need: 
We want to have Endpoint Management Administrators for **Central IT** , **Company A** IT Administrators managing their devices and **Company B** IT Administrators managing their devices. 
We want to automate synchronization of device assignment and user under each "administrative realm" (administrative unit in AAD terms , Scope Tags in MEM terms, Deployment Profile for Autopilot, Tagging in MDE terms). 

## Assumptions: 
- Each company has its own domain name that is reflected on the UPN e.g. user@centralit.com, user@companya.com , user@companyb.com
- Each computer of each company follows a specific naming pattern 
- We have all necessary licenses (Azure AD P1 , E3 , E5 Security in my case) 
- We can register applications on AAD
- We can create and manage an Azure Key Vault
- We can create Azure Durable Functions 
- We can create logic apps on Azure
- InTune, MDE and AAD are already configured & integrated
- You are aware of MS Graph API, MS Security Graph API, Managed Identities
- You are familiar with coding and JSON (in my case I am using Java 11 and JSON)

## Scope: 
- **Azure Active Directory**
- **Microsoft Endpoint Manager**
- **Microsoft Defender for Endpoint**

## Deployment: 

Based on AAD RBAC Roles: 

![AAD RBAC](https://learn.microsoft.com/en-us/azure/active-directory/roles/media/concept-understand-roles/role-overlap-diagram.png)

We will create different Groups of Users that will be shared among the administration patterns of each product.
We will leverage on **specific attributes** for managing users and devices and link them. 

### Azure Active Directory Configuration (AAD)
AAD is the basis of our implementation we will create 3 administrative units per company
For Company A:
1. "IT Department of Company A - Devices" - *Dynamic Membership*
2. "IT Department of Company A - Users" - *Dynamic Membership*
3. "IT Department of Company A - Groups" - *Assigned Membership*

The first two (1) & (2) administrative units would have dynamic membership rules.
Obviously, the 1st one contains devices and the 2nd one contains users. 
The last one (3) is assigned.

More over we then create a group per company. For Company A, we name it "IT Department of Company A" and it contains all administrators of Company A.
This group can be Dynamic , Privileged Access Group (if you want to use Privileged Identity Management). 

Each device is distinguished under each administrative unit by **device category** attribute on Graph API for AAD ([https://graph.microsoft.com/beta/devices](https://graph.microsoft.com/beta/devices))

 "AzureObjectId"/"id": "001a0a77-50c7-42a1-97b4-349066ad56e5",
 "deviceCategory": "IT Department of VIOMAL",
 "AzureDeviceId"/"deviceId": "abea4fb9-9a9a-47f6-8627-7da71d37a2c0", 
"displayName": "VMLVTDWRH",
            "physicalIds": [
                "[OrderId]:CLOUD_ICME",
                "[ZTDID]:fda09c69-5181-49f2-b7e3-bda6c17e3355",
                "[HWID]:h:6896142841360412"
            ],

### Microsoft Endpoint Configuration (MEM)
On Endpoint Configuration Manager we create a Scope Tag per company.
For Company A, we name it "IT Department of Company A". 

Each device is distinguished under each scope tag by **device category** attribute on Graph API for Managed Devices ([https://graph.microsoft.com/beta/deviceManagement/managedDevices](https://graph.microsoft.com/beta/deviceManagement/managedDevices))

"id": "ac753f45-dc86-421b-8fe8-cde09e8a498a",
"deviceName": "CBLPRD01",
"deviceCategoryDisplayName": "IT Department of CABLEL",
"userPrincipalName": "cblprduser@hellenic-cables.com",
"serialNumber": "CZC7037VX6",
"azureActiveDirectoryDeviceId": "ddf37b4e-f68b-4add-a164-881b7bbe6504",
"azureADDeviceId": "ddf37b4e-f68b-4add-a164-881b7bbe6504",

### Autopilot Configuration
On AAD create create a dynamic group for devices per company. 

On Endpoint Configuration Manager we create an autopilot profile per company.
The profile deploys a specific naming pattern (e.g. compapc001 - company a pc 001) for each device. 

Graph API for Autopilot Device([https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities](https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities))

Each device is distinguished under each scope tag by **Serial** and 

"azureActiveDirectoryDeviceId": "b81c8159-2b09-40ad-915c-fc726886bad2",
"azureAdDeviceId": "b81c8159-2b09-40ad-915c-fc726886bad2",
"managedDeviceId": "00000000-0000-0000-0000-000000000000", 
"groupTag": "CLOUD_STEELMET",
"id": "8ac4e717-f429-4091-854c-6a1b60a90912",   --> [ZTDID]@AAD
"serialNumber": "0000-0003-4209-7652-2425-8798-38",
 
### Microsoft Defender for Endpoint Configuration (MDE)

Security Graph API for MDE([https://api.securitycenter.microsoft.com/api/machines/](https://api.securitycenter.microsoft.com/api/machines/))

"id": "0002a344412985ce8629d2ea979f98d57449b243",
"computerDnsName": "stlpmavrakis.corp.vionet.gr",
"aadDeviceId": "888d2c94-b23c-4b8c-b25b-e878aad0b6e2", AADDeviceID (not object Id)
"machineTags": [
				"IT Department of STEELMET"
			],

## Linking the devices among AAD, MEM , Autopilot and MDE

## Automating Linking

### Logic App

### Registering an App

### Securing access to the app using Keyvault

### Durable Functions to overcome Logic Limitations or Long running transactions using Async HTTP API

### Email notifications or MS Teams message posting