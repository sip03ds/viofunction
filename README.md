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
To distinguish administrative authorities we will utilize administrative units.

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
For Company A, the rule can be:


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

The ability is there but data has not been migrated. In order to start managing devices, we have to populate the right attribute on users and users based on specific rules.
We will automate the migration process by using some AAD Device attributes ([https://graph.microsoft.com/beta/devices](https://graph.microsoft.com/beta/devices)). 
The ones we will focus on are:
- **{id}** is a unique GUID Id for distinguishing the object in AAD.
- **{deviceId}** is a unique GUID Id for linking the object with other entities in Graph API.
- **{displayName}** is a string and usually it's the hostname of the device (without domain name).
- **{physicalIds}** is a list of strings with physical hardware characteristics of the device.
    - **{[ZTDID]}** is part of the physical id list (Zero Touch Device Id), containing the autopilot device id.
    - **{[OrderId]}** is part of the physical id list - containing a user defined string.

### Microsoft Endpoint Configuration (MEM)
In order to separate users and devices for application deployment, profile assignment we will create separate AAD groups for each company. 
We create 3 different dynamic groups per company, two for the devices and one for the users. 
For Company A:
1. `Supported devices of IT Department of Company A` - *Dynamic Membership*
2. `Supported users of IT Department of Company A` - *Dynamic Membership*

For the `Supported devices of IT Department of Company A` security group, the dynamic rule will contain every device supported by each IT Department.
For Company A, the rule is:

```
device.deviceCategory -eq "IT Department of Company A"
```

For the `Supported users of IT Department of Company A` security group, the dynamic rule will contain every user supported by each IT Department.
For Company A, the rule is:

```
user.employeeOrgData -eq "IT Department of Company A"
```

We follow the same pattern for each Company to distinguish users and devices administrative units.
For devices, we can further distinguish devices by using different groups per criteria (you can use dynamic rules that contain OS, OS build number, Vendor).

Then we have to create device categories on Endpoint Manager.
We navigate under devices and we configure each Device Category per Company:
1. IT Department of Central IT
2. IT Department of Company A
3. IT Department of Company B

The next step is separating administration realms.
We will utilize scope tags. Scope tags determine which objects administrators are able to administer.
1. We create a scope tag for each Company. 
    - `IT Department of Company A`.
    Scope tags included groups will contain the dynamic group of devices `Supported devices of IT Department of Company A`.
2. We create a custom role for each Company assigning all necessary permissions you desire the IT Departments to configure
3. We assign the custom role to `IT Department of Company A` group, we include `Supported devices of IT Department of Company A` and `Supported users of IT Department of Company A` and set the scope tag to `IT Department of Company A`

The achievement is that on top of distinguished administration on AAD, each company's IT Department can manage their devices on Endpoint Manager.
IT departments can now deploy applications, push configuration profiles for devices based on the permissions set. 
We can also use the groups of Devices for deploying applications

When a new devices are enrolled on Endpoint Manager (not autopilot device); and company portal is pushed on the device users will have to select the appropriate device category. 
Administrators can override user selection by changing the device category attribute. 

Later, we will automate the process of filling in device cateogry by using some Managed Device attributes ([https://graph.microsoft.com/beta/deviceManagement/managedDevices](https://graph.microsoft.com/beta/deviceManagement/managedDevices)).
The ones we will focus on are:
- **{id}** is a unique GUID Id for distinguishing the object in Endpoint Manager.
- **{deviceName}** is a string and usually it's the hostname of the device (without domain name).
- **{userPrincipalName}** is a string usually its the UPN of the primary user of the device. It contains the domain name of the company and we can use it to find the company supporting the device
- **{serialNumber}** is a string specifying the serial number of the device.
- **{azureActiveDirectoryDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 
- **{azureADDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 

### Autopilot Configuration
In order to separate devices for autopilot we will create separate AAD groups and autopilot profiles for each company. 
We will create different dynamic groups per autopilot profile for each company. Each group will associate a different autopilot profile to the devices for the company.
For demonstration purposes, suppose that we want 2 autopilot enrollment profiles per company, a hybrid and a cloud one. 
Initially, we will create 2 different AAD groups per company for our example for distinguishing devices for each profile. 
Then we will create the autopilot enrollment profiles. 

1. We will create a group `Supported autopilot devices cloud of IT Department of Company A` dynamic administrative unit, the dynamic rule will contain every autopilot device we wish to deploy cloud profile supported by each IT Department.
For Company A, the rule is:

```
(device.devicePhysicalIDs -any _ -contains "[ZTDID]") and (device.devicePhysicalIDs -any _ -contains "[OrderID]:CLOUD_COMPANY_A")
```

2. We will create a group `Supported autopilot devices hybrid of IT Department of Company A` dynamic administrative unit, the dynamic rule will contain every autopilot device we wish to deploy hybrid profile supported by each IT Department.
For Company A, the rule is:

```
(device.devicePhysicalIDs -any _ -contains "[ZTDID]") and (device.devicePhysicalIDs -any _ -contains "[OrderID]:HYBRID_COMPANY_A")
```

We follow the same pattern for every company.
We get back on Endpoint Manager Administration and we update the scope tags with the groups created as well as scope assignment with the groups we just created. 

Under devices and windows enrollment we enable automatic enrollment so every Windows device is turned into an autopilot device.
Moreover, following our example we create 2 different windows autopilot profiles for each company. 

1. We will create a profile named `Company A Cloud` , the profile must:
   - Covert all existing targeted devices to Autopilot.
   - Contain any OOBE configuration we wish BUT we need to apply a different naming pattern for distinguishing the profile for Company A. We can use `compacloud%RAND:4%` (Company A Cloud followed by 4 random numbers. Do not forget that computer names must be up to 15 alphanumerics)
   - Deploy Autopilot profile to the group `Supported autopilot devices cloud of IT Department of Company A`

2. We will create a profile named `Company A Hybrid` , the dynamic rule will contain every autopilot device we wish to deploy cloud profile supported by each IT Department.
   - Covert all existing targeted devices to Autopilot.
   - Contain any OOBE configuration we wish BUT we need to apply a different naming pattern for distinguishing the profile for Company A. We can use `compabybrid%RAND:4%` (Company A Hybrid followed by 4 random numbers. Do not forget that computer names must be up to 15 alphanumerics)
   - Deploy Autopilot profile to the group `Supported autopilot devices hybrid of IT Department of Company A`

Now if we navigate to the list of autopilot devices we can set Group Tag of a device to `CLOUD_COMPANY_A` or `HYBRID_COMPANY_A` and the autopilot profile will be assigned to the autopilot device.
You can use these groups to deploy applications, push configuration profiles and achieve high level of autodeployment for new devices.

We have achieved to automate profile deployment to autopilot devices. For every new device that you buy you may provide the right **Group Tag** to the vendor on your order and the device can be handed to the user straight away from the factory.
As soon as the computer boots, the right profile and any predefined applications will be deployed. 

For existing devices we will have to migrate them on the right profile based on their computer name or serial. On our migration journey we will use some Autopilot Device attributes([https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities](https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities)).
The ones we will focus on are:
- **{id}** is a unique GGUID Id for distinguishing the object in Endpoint Manager.
- **{groupTag}** is a string specifying the autopilot profile for deployment.
- **{serialNumber}** is a string specifying the serial number of the device.
- **{azureActiveDirectoryDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 
- **{azureAdDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 
 
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