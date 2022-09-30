# Using a Logic App with Azure Durable Functions for automating Device Synchronization among SaaS MS Products (Azure Active Directory - Endpoint Manager - Autopilot - Defender for Endpoint).
I have worked for an group organization that has many subsidiaries. 
Every subsidiary has its own IT department that should have a different degree of freedom for performing actions on Cloud MS Products. 
All companies exist under the same tenant.
Let's say that we have:
- Central IT
- Company A IT
- Company B IT

## Need: 
We want to have Endpoint Management Administrators for **Central IT** , **Company A** IT Administrators managing their devices and **Company B** IT Administrators managing their devices. 
We want to automate synchronization of device assignment under each *"administrative realm"* (administrative unit in AAD terms , Scope Tags in MEM terms, Deployment Profile for Autopilot, Tagging in MDE terms). 

## Assumptions: 
- We have all necessary licenses needed (Azure AD P1 , E3 , E5 Security in my case).
- Each company has its own domain name that is reflected on the UPN e.g. user@centralit.com, user@companya.com , user@companyb.com.
- Each computer of each company follows a specific naming pattern.
- We can register applications on AAD.
- We can create and manage an Azure Key Vault.
- We can create Azure Durable Functions.
- We can create logic apps on Azure.
- InTune, MDE and AAD are already configured & integrated.
- You are aware of MS Graph API, MS Security Graph API, Managed Identities.
- You are familiar with coding and JSON (in my case I am using Java 11 and JSON).

## Scope: 
- **Azure Active Directory**
- **Microsoft Endpoint Manager**
- **Microsoft Defender for Endpoint**

## Deployment: 

Based on AAD RBAC Roles: 

![AAD RBAC](https://learn.microsoft.com/en-us/azure/active-directory/roles/media/concept-understand-roles/role-overlap-diagram.png)

We will create different Groups of Users that will be shared among the administration patterns of each product.
We will need to rely **specific attributes** for managing users and devices and link them.

We need to utilize the exact same attribute value on every device or user entity on every SaaS platform to highlight **who** is supporting the device/user.
In this manner we will link conceptually every device across every SaaS platform
We will reuse the same String for each company in every application. 
For Company A, we will have `IT Department of Company A`.

### Azure Active Directory Configuration (AAD)
AAD is the basis of our implementation. 
On AAD we will create different security groups that will be reused on MEM and MDE. 

Initially, using our example we will create 3 administrative units per company
For Company A:
1. `IT Department of Company A - Devices` - *Dynamic Membership*
2. `IT Department of Company A - Users` - *Dynamic Membership*
3. `IT Department of Company A - Groups` - *Assigned Membership*

The first two (1) & (2) administrative units would have dynamic membership rules.
Obviously, the 1st one contains devices and the 2nd one contains users. 
The last one (3) is assigned.

For the *devices* dynamic administrative unit, the dynamic rule will contain every device supported by each IT Department.
For Company A, the rule is:

```
device.deviceCategory -eq "IT Department of Company A"
```

For the *users* dynamic administrative unit, the dynamic rule will contain every user supported by each IT Department.
For Company A, the rule can be :


```
user.employeeOrgData -eq "IT Department of Company A"
```

For users you can also use `CompanyName` attribute. In my humble opinion it has more operational overhead, because, but considering future use, IT Department of Company A may support more than one Company. 
In this case you have to go back and update every dynamic administrative unit or group with the new Company Name, hence my suggestion to use an other distinct attribute.
Considering also hybrid environments, we may have extension attributes being synchronized and populated from on-premise Active Directory where we already populate this attribute and we may use that one in AAD. 

Then we create a group with Administrators per company. For Company A, we name it `IT Department of Company A` and it contains all administrators of Company A.
We can use any type of group (Dynamic , Privileged Access Group, in case you want to use Privileged Identity Management). E.g. for dynamic you can leverage on `user.CompanyName`, `user.employeeOrgData` and `user.department`.

For the `IT Department of Company A - Devices` administrative unit created, we provide the following RBAC roles on the group `IT Department of Company A`:
- Cloud device administrator 
- Printer administrator

For the `IT Department of Company A - Users` administrative unit created, we provide the following RBAC roles on the group `IT Department of Company A`:
- User administrator
- Authentication administrator
- Helpdesk administrator
- License administrator
- Password administrator

For the `IT Department of Company A - Groups` administrative unit created, we provide the following RBAC roles on the group `IT Department of Company A`:
- Groups administrator

We follow the same pattern for each Company to distinguish users and devices administrative units.

The achievement so far is that each IT Department of each company has the ability to manage their users and devices in AAD. 
They can reset passwords or troubleshoot MFA, they assign licenses on their users, delete AAD devices under their authority. 

For our automation project later, we will use a few AAD Device attributes ([https://graph.microsoft.com/beta/devices](https://graph.microsoft.com/beta/devices)). 
The ones we will focus on are:
- **{id}** is a unique GGUID Id for distinguishing the object in AAD.
- **{deviceId}** is a unique GGUID Id for linking the object with other entities in Graph API.
- **{displayName}** is a string and usually it's the hostname of the device (without the domain name).
- **{physicalIds}** is a list of strings with physical hardware characteristics of the device.
    - **{[ZTDID]}** is part of the physical id list (Zero Touch Device Id), containing the autopilot device id.
    - **{[OrderId]}** is part of the physical id list - containing a user defined string.

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

"id": "8ac4e717-f429-4091-854c-6a1b60a90912",   --> [ZTDID]@AAD
"azureActiveDirectoryDeviceId": "b81c8159-2b09-40ad-915c-fc726886bad2",
"azureAdDeviceId": "b81c8159-2b09-40ad-915c-fc726886bad2",
"groupTag": "CLOUD_STEELMET",
"managedDeviceId": "00000000-0000-0000-0000-000000000000", 
"serialNumber": "0000-0003-4209-7652-2425-8798-38",
 
### Microsoft Defender for Endpoint Configuration (MDE)

Security Graph API for MDE([https://api.securitycenter.microsoft.com/api/machines/](https://api.securitycenter.microsoft.com/api/machines/))

"id": "0002a344412985ce8629d2ea979f98d57449b243",
"computerDnsName": "stlpmavrakis.corp.vionet.gr",
"aadDeviceId": "888d2c94-b23c-4b8c-b25b-e878aad0b6e2", AADDeviceID (not object Id)
"machineTags": ["IT Department of STEELMET"],

## Linking the devices among AAD, MEM , Autopilot and MDE

Using the attributes for each entity we attempt to make the links displayed on the following figure:
![Entity Links](https://github.com/sip03ds/viofunction/blob/81005d0db697b858f2cbe2527a3209d937bf56c2/src/main/resources/images/Graph_Api_Links.png)

The **white** links display out of the box connections used by the products.
The **yellow** links display the conceptual link we will try to create using strings for distinguishing each company. 

## Automated Linking

### Logic App

### Registering an App

### Securing access to the app using Keyvault

### Durable Functions to overcome Logic Limitations or Long running transactions using Async HTTP API

### Email notifications or MS Teams message posting