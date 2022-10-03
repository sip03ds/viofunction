# Creating different administration realms for device management among SaaS MS Products (Azure Active Directory - Endpoint Manager - Autopilot - Defender for Endpoint).
# a.k.a. Using Logic Apps and Azure Durable functions with a registered app for device category migration.
I have worked for an group organization that has many subsidiaries. 
Every subsidiary has its own IT department that desired to have full administrative permissions for performing actions on Cloud MS Products ONLY for their devices and their users.
All companies exist under the same tenant.
Let's say that we have:
- Central IT
- Company A IT
- Company B IT

## Need: 
We want to have Endpoint Management Administrators for **Central IT** , **Company A** IT Administrators managing aspects of their users (reset password/reregister MFA and change attributes) and their devices and **Company B** IT Administrators managing aspects of their users (reset password/reregister MFA and change attributes) their devices. 
We want to automate synchronization of device assignment under each *"administrative realm"* (administrative unit in AAD terms , Scope Tags in MEM terms, Deployment Profile for Autopilot, Tagging in MDE terms). 

## Assumptions: 
- We have all necessary licenses needed (Azure AD P1 , E3 , E5 Security in my case).
- Each company has its own domain name that is reflected on the UPN (user@centralit.com, user@companya.com , user@companyb.com).
- Each computer of each company follows a specific naming pattern.
- We can register applications on AAD.
- We can create and manage an Azure Key Vault.
- We can create Azure Durable Functions.
- We can create logic apps on Azure.
- AAD, MEM and MDE are already configured & integrated where applicable.
- You are aware of MS Graph API, MS Security Graph API, Managed Identities.
- You are familiar with coding (in my case I am using Java 11) and JSON .

## Scope: 
- **Azure Active Directory**
- **Microsoft Endpoint Manager**
- **Microsoft Defender for Endpoint**

## Deployment: 

Based on AAD RBAC Roles: 

![AAD RBAC](https://learn.microsoft.com/en-us/azure/active-directory/roles/media/concept-understand-roles/role-overlap-diagram.png)

We will create different Groups of Users that will be shared among the administration patterns of each product.
We will need to rely on **specific attributes** for managing users and devices and linking them.

We need to utilize the exact same attribute value on every device or user entity on every SaaS platform to highlight **who** is supporting the device/user.
In this manner we will link conceptually every device across every SaaS platform.
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
The last one (3) is assigned and used to manage groups.

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

For users you can also use `CompanyName` attribute. In my humble opinion it has more operational overhead, since considering future use, IT Department of Company A may support more than one Company. 
In this case you have to go back and update every dynamic administrative unit and dynamic rule in relevant AAD dynamic group with the new Company Name, hence my suggestion to use an other distinct attribute.
Considering also hybrid environments, we may have extension attributes being synchronized and populated from on-premise Active Directory where we already populate this attribute and we may use that one in AAD. 

Then we create a group with Administrators per company. For Company A, we name it `IT Department of Company A` and it contains all administrators of Company A.
We can use any type of group (Dynamic , Privileged Access Group, in case you want to use Privileged Identity Management). For dynamic you can leverage on `user.CompanyName`, `user.employeeOrgData` and `user.department`.

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
1. `IT Department of Central IT`
2. `IT Department of Company A`
3. `IT Department of Company B`

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

Later, we will automate the process of filling in device category by using some Managed Device attributes ([https://graph.microsoft.com/beta/deviceManagement/managedDevices](https://graph.microsoft.com/beta/deviceManagement/managedDevices)).
The ones we will focus on are:
- **{id}** is a unique GUID Id for distinguishing the object in MEM.
- **{deviceName}** is a string and usually it's the hostname of the device (without domain name).
- **{userPrincipalName}** is a string usually its the UPN of the primary user of the device. It contains the domain name of the company and we can use it to find the company supporting the device
- **{serialNumber}** is a string specifying the serial number of the device.
- **{azureActiveDirectoryDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 
- **{azureADDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 

### Autopilot Configuration
In order to separate devices for autopilot we will create separate AAD groups and autopilot profiles for each company. 
We will create different dynamic device groups per autopilot profile for each company. Each group will associate a different autopilot profile to the devices for the company.
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
   - Contain any OOBE configuration we wish BUT we need to apply a different naming pattern for distinguishing the profile of the device for Company A. We can use `compacloud%RAND:4%` (Company A Cloud followed by 4 random numbers. Do not forget that computer names must be up to 15 alphanumerics)
   - Deploy Autopilot profile to the group `Supported autopilot devices cloud of IT Department of Company A`

2. We will create a profile named `Company A Hybrid` , the dynamic rule will contain every autopilot device we wish to deploy cloud profile supported by each IT Department.
   - Covert all existing targeted devices to Autopilot.
   - Contain any OOBE configuration we wish BUT we need to apply a different naming pattern for distinguishing the profile of the device for Company A. We can use `compabybrid%RAND:4%` (Company A Hybrid followed by 4 random numbers. Do not forget that computer names must be up to 15 alphanumerics)
   - Deploy Autopilot profile to the group `Supported autopilot devices hybrid of IT Department of Company A`

Now if we navigate to the list of autopilot devices we can set Group Tag of a device to `CLOUD_COMPANY_A` or `HYBRID_COMPANY_A` and the autopilot profile will be assigned to the autopilot device.
You can use these groups to deploy applications, push configuration profiles and achieve high level of autodeployment for new devices.

We have achieved to automate profile deployment to autopilot devices. For every new device that you buy you may provide the right **Group Tag** to the vendor on your order and the device can be handed to the user straight away from the factory.
As soon as the computer boots, the right profile and any predefined applications will be deployed. 

For existing devices we will have to migrate them on the right profile based on their computer name or serial. On our migration journey we will use some Autopilot Device attributes([https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities](https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities)).
The ones we will focus on are:
- **{id}** is a unique GGUID Id for distinguishing the object in MEM.
- **{groupTag}** is a string specifying the autopilot profile for deployment.
- **{serialNumber}** is a string specifying the serial number of the device.
- **{azureActiveDirectoryDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 
- **{azureAdDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 
 
### Microsoft Defender for Endpoint Configuration (MDE)
Separating device management in MDE differs than MEM or AAD. 
1. You create Roles by setting the permissions for the users and assign the role to an AAD Group. 
2. You create Device Groups based on rules (similar to the dynamic groups of AAD) and you assign one or more user AAD Groups that can manage these Device Groups.

In case you want to customize device assignment to Device Groups in MDE, MDE offers the flexibility of tags. Tags are free text strings that you can define and assign them on devices. 
For our example we start by creating 3 custom roles, one for each company.
For Company A:
- `IT Department of Company A` and we set all the permissions we want *IT Department of Company A* to manage and we assign the Role to AAD Group `IT Department of Company A`.

We follow the same process for every company. 
Then we have to create the Device Groups on MDE. We create one device Group per company.
For Company A:
- `Supported Devices of Company A`
   - the rule we set is that `Tag equals IT Department of Company A` .
   - the users that have access on the Device Group is `IT Department of Company A`.

We have achieved to create a different administration realm for each company on MDE. 
Every device that is enrolled to MDE should get the appropriate tag in order to be added on the right administration realm.

Now we need to set the tag on every device we already have on MDE. In order to migrate we will use Security Graph API for MDE([https://api.securitycenter.microsoft.com/api/machines/](https://api.securitycenter.microsoft.com/api/machines/))
We will utilize the following attributes to achieve migration:
- **{id}** is a unique GGUID Id for distinguishing the object in MDE.
- **{computerDnsName}** is a string and usually it's the hostname of the device (including domain name).
- **{machineTags}** is a list of strings used to filter the device in MDE. 
- **{aadDeviceId}** is a unique GGUID Id for distinguishing the object in AAD. 

## Linking the devices among AAD, MEM , Autopilot and MDE
So far we have configured all products and prepared them to accept different administration realms for each company.
The next step is to establish a process that migrates any existing devices under the right administration realm and checks for any discrepancies for new devices.
This process should run on a regular basis and try to sync the right attributes of device entities among all platforms.

Check Graph API and Security Graph API I have come across the attributes that we need to use. 
Using the attributes for each entity we attempt to make the links displayed on the following figure:
![Entity Links](https://github.com/sip03ds/viofunction/blob/81005d0db697b858f2cbe2527a3209d937bf56c2/src/main/resources/images/Graph_Api_Links.png)

The **white** links display out of the box connections used by the products.
The **yellow** links display the conceptual link we will try to create using strings for distinguishing each company. 

## Automated Migration
You can create powershell scripts that run on a regular basis on a task scheduler, but in the cloud era, setting up redundant servers and maintaining them is a **waste of time**.
I wanted a solution that has minimal next to zero maintenance, minimal cost that runs secure and does the job; and in case anything goes wrong to let me know, so I can fix it.
To achieve the goal I decided to deploy different cloud technologies (and as I found out during the process, I also had to come across their limitations).

The whole process is wrapped around a Logic App that runs once per day on a consumption plan and costs ~10 Euros per month.
Logic App is using an M365 account to send emails and notifications to MS Teams. Add ~6 Euros on the monthly bill for the M365 account.
The Logic App calls a few Azure Durable Functions that make HTTP Async Calls (We have some long running transactions that take more than 10 minutes hence the need of Elastic Premium SKU). 
The cost of Durable Functions run on Elastic Premium Tier App Service Plan and cost around ~200 Euros per month.
Durable functions make calls to Graph API and Security Graph API and I have registered an app on AAD for this purpose. This comes free with AAD license.
Last but not least, I am using an Azure Key Vault to hide the app secret that Azure Functions are using.
The cost of Key Vault is less than a Euro per month. All deployments take place in West Europe.
As the time of writing (20/9/2022) the total monthly cost in West Europe is **~220 Euros**.
I doubt if you can find a consistent administrator doing the migration job and checks and a server costing that much on a monthly basis.

How do we achieve migration? 
High level the process is the following: 
1. The logic app runs once per day.
2. During the run, Azure Functions help us make calls to Graph API via the Registered App.
3. Functions get all the device entities (AAD,MEM managed device, MEM autopilot and MDE) from every platform using Java 11 API.
4. Functions try to match the entities attributes as displayed on the diagram.
5. In case there are missing attributes functions try to update the attributes based on certain rules (using the hostname , or the domain name).
6. For devices that cannot be updated based on the info gathered, we create a JSON list that is returned to the logic App. 
7. Logic app parses the response and prepares an email for each device along with the action required (update device category on MEM device).
   As we may have a large amount of devices, we may hit Exchange Online limitations, so the logic app has timers to send emails respecting Exchange Online limitations.
   In case there is an error (e.g. an HTTP call is not made), the Logic app will notify me by posting a message to MS Teams.

### Logic App
We start by defining the recursive occurrence for running the app. Preferably you can run it on night where there is minimal API traffic on your tenant. 
Then we define a few variables that hold the URLs of the functions, the KeyVault name that we will use, a few counters, the email address where emails will be sent and any ([email limits that Exchange Online](https://learn.microsoft.com/en-us/office365/servicedescriptions/exchange-online-service-description/exchange-online-limits)) is posing. Then after initializing the variables we make HTTP calls using Azure Durable functions (Async HTTP calls). We get as response the status URL, and we query the status URL (get HTTP 202 response) until we the transaction is complete (get HTTP 200 response). In case there is an error on HTTP calls, the application will inform us on MS Teams by posting a message with the HTTP request and the HTTP response.
Then we get the output from HTTP status and we parse the JSON response. Based on the response we receive, the application is sending an email for each item parsed on JSON response. The email can be forwarded with a predefined format on a ticketing system that can create a ticket to administrators notifying on the manual actions required.

### Registering an App
Azure Durable functions need to make calls to Graph API in order get data from SaaS Applications. We will register an app that will act as an intermediate between our functions and Graph API and Security Graph API providing relevant permissions.
We register the application and create a API client secret that will store on the Keyvault we will create. 
Then we provide the relevant API permissions on the application for interacting with Graph API:
- AdministrativeUnit.Read.All, type: Application
- AdministrativeUnit.ReadWrite.All, type: Application, Microsoft Graph
- CloudPC.Read.All, type: Application, Microsoft Graph
- CloudPC.ReadWrite.All, type: Application, Microsoft Graph
- Device.Read.All, type: Application, Microsoft Graph
- Device.ReadWrite.All, type: Application, Microsoft Graph
- DeviceManagementApps.Read.All, type: Application, Microsoft Graph
- DeviceManagementApps.ReadWrite.All, type: Application, Microsoft Graph
- DeviceManagementConfiguration.Read.All, type: Application, Microsoft Graph
- DeviceManagementConfiguration.ReadWrite.All, type: Application, Microsoft Graph
- DeviceManagementManagedDevices.PrivilegedOperations.All, type: Application, Microsoft Graph
- DeviceManagementManagedDevices.Read.All, type: Application, Microsoft Graph
- DeviceManagementManagedDevices.ReadWrite.All, type: Delegated, Microsoft Graph
- DeviceManagementManagedDevices.ReadWrite.All, type: Application, Microsoft Graph
- DeviceManagementRBAC.Read.All, type: Application, Microsoft Graph
- DeviceManagementRBAC.ReadWrite.All, type: Application, Microsoft Graph
- DeviceManagementServiceConfig.Read.All, type: Application, Microsoft Graph
- DeviceManagementServiceConfig.ReadWrite.All, type: Application, Microsoft Graph
- Directory.AccessAsUser.All, type: Delegated, Microsoft Graph
- Directory.Read.All, type: Application, Microsoft Graph
- Directory.ReadWrite.All, type: Application, Microsoft Graph
- Directory.Write.Restricted, type: Application, Microsoft Graph
- ThreatAssessment.Read.All, type: Application, Microsoft Graph
- ThreatHunting.Read.All, type: Application, Microsoft Graph
- ThreatIndicators.Read.All, type: Application, Microsoft Graph
- ThreatIndicators.ReadWrite.OwnedBy, type: Application, Microsoft Graph
- ThreatSubmission.Read.All, type: Application, Microsoft Graph
- ThreatSubmission.ReadWrite.All, type: Application, Microsoft Graph
- ThreatSubmissionPolicy.ReadWrite.All, type: Application, Microsoft Graph
- User.Export.All, type: Application, Microsoft Graph
- User.Invite.All, type: Application, Microsoft Graph
- User.ManageIdentities.All, type: Application, Microsoft Graph
- User.Read, type: Delegated, Microsoft Graph
- User.Read.All, type: Application, Microsoft Graph
- User.ReadWrite.All, type: Application, Microsoft Graph
- AdvancedQuery.Read.All, type: Application, WindowsDefenderATP
- Alert.Read.All, type: Application, WindowsDefenderATP
- Alert.ReadWrite.All, type: Application, WindowsDefenderATP
- Event.Write, type: Application, WindowsDefenderATP
- File.Read.All, type: Application, WindowsDefenderATP
- IntegrationConfiguration.ReadWrite, type: Application, WindowsDefenderATP
- Ip.Read.All, type: Application, WindowsDefenderATP
- Library.Manage, type: Application, WindowsDefenderATP
- Machine.CollectForensics, type: Application, WindowsDefenderATP
- Machine.Isolate, type: Application, WindowsDefenderATP
- Machine.LiveResponse, type: Application, WindowsDefenderATP
- Machine.Offboard, type: Application, WindowsDefenderATP
- Machine.Read.All, type: Application, WindowsDefenderATP
- Machine.ReadWrite.All, type: Application, WindowsDefenderATP
- Machine.RestrictExecution, type: Application, WindowsDefenderATP
- Machine.Scan, type: Application, WindowsDefenderATP
- Machine.StopAndQuarantine, type: Application, WindowsDefenderATP
- RemediationTasks.Read.All, type: Application, WindowsDefenderATP
- Score.Read.All, type: Application, WindowsDefenderATP
- SecurityBaselinesAssessment.Read.All, type: Application, WindowsDefenderATP
- SecurityConfiguration.Read.All, type: Application, WindowsDefenderATP
- SecurityRecommendation.Read.All, type: Application, WindowsDefenderATP
- Software.Read.All, type: Application, WindowsDefenderATP
- Ti.Read.All, type: Application, WindowsDefenderATP
- Ti.ReadWrite, type: Application, WindowsDefenderATP
- Ti.ReadWrite.All, type: Application, WindowsDefenderATP
- Url.Read.All, type: Application, WindowsDefenderATP
- User.Read.All, type: Application, WindowsDefenderATP
- Vulnerability.Read.All, type: Application, WindowsDefenderATP

The calls that we make to Graph API are listed below:
- [List AAD Devices](https://learn.microsoft.com/en-us/graph/api/device-list?view=graph-rest-1.0&tabs=http)
- [Get AAD Device by AAD Object Id](https://learn.microsoft.com/en-us/graph/api/device-get?view=graph-rest-1.0&tabs=http)
- [List AAD Devices](https://learn.microsoft.com/en-us/graph/api/device-list?view=graph-rest-1.0&tabs=http), use filter to parse results for device ID
- [List Managed Devices](https://learn.microsoft.com/en-us/graph/api/intune-devices-manageddevice-list?view=graph-rest-1.0)
- [Get Managed Device by Managed Device Id](https://learn.microsoft.com/en-us/graph/api/intune-devices-manageddevice-get?view=graph-rest-1.0)
- [Update Managed Device properties](https://learn.microsoft.com/en-us/graph/api/intune-devices-manageddevice-update?view=graph-rest-1.0)
- [List Device Categories](https://learn.microsoft.com/en-us/graph/api/intune-onboarding-devicecategory-list?view=graph-rest-1.0)
- [List Autopilot Device Identities](https://learn.microsoft.com/en-us/graph/api/intune-enrollment-windowsautopilotdeviceidentity-list?view=graph-rest-1.0)
- [Get Autopilot Device Identity by Id](https://learn.microsoft.com/en-us/graph/api/intune-enrollment-windowsautopilotdeviceidentity-get?view=graph-rest-1.0)
- [Update Autopilot Device Identity Properties](https://learn.microsoft.com/en-us/graph/api/intune-enrollment-windowsautopilotdeviceidentity-updatedeviceproperties?view=graph-rest-1.0)

Since there are no libraries for making calls to Security Graph, I am managing the access token to the application and make direct HTTP calls to Security Graph API. 
The APIs used are:
- [List MDE Machines](https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/get-machines?view=o365-worldwide)
- [Add Tags](https://learn.microsoft.com/en-us/microsoft-365/security/defender-endpoint/add-or-remove-machine-tags?view=o365-worldwide)

### Securing access to the app using Keyvault



### Durable Functions to overcome Logic Limitations or Long running transactions using Async HTTP API
Before starting we need to setup the right permissions.
After creating the function, we need to assign IAM permissions on the Keyvault we just created to allow the functions to read secrets on the Keyvault.
Durable functions allow us to overcome synchronous HTTP call limitations. 
On every environment we might have unpredictable number of devices. This means that the data structures we create may become very very large. 
Parsing these data structures may require large amount of processing time that exceeds synchronous HTTP call limitations. More over, these calls may exceed the [10 minute limitation](https://build5nines.com/azure-functions-extend-execution-timeout-past-5-minutes/) used on the [consumption app plan](https://www.koskila.net/how-to-upgrade-your-azure-function-app-plan-when-you-originally-selected-consumption/) for Azure Functions.
That's why we have to switch to app service plan and to tweak `host.json`, to include:

```
  "functiontimeout": "00:59:59"
```

Our Azure durable functions make [asynchronous HTTP calls](https://learn.microsoft.com/en-us/azure/azure-functions/durable/durable-functions-overview?tabs=csharp#async-http)
 
![Azure durable function async HTTP](https://learn.microsoft.com/en-us/azure/azure-functions/durable/media/durable-functions-concepts/async-http-api.png)

My code is not very sophisticated, I am using the [JAVA example](https://learn.microsoft.com/en-us/azure/azure-functions/durable/quickstart-java) provided by Microsoft.
My changes on the Azure Function Durable provided, is that I pass arguments from the HTTP triggered function to the orchestrator function and finally to the activity function and that I am using a logger to send info to the function's log. 
The activity function embodies all logic required for syncing device categories and makes the calls to Graph API and all checks.
After implementing all the logic required, all response are created in JSON and returned back on the status URL response.

### Email notifications or MS Teams message posting
The logic app parses any JSON responses and sends emails to helpdesk for any manual actions that may be required. We are using an M365 account for creating the response and posting messages to MS Teams.