# Azure Durable Functions for synchronizing Device among MS Products (Azure Active Directory - Endpoint Manager - Autopilot - Defender for Endpoint). 
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

### AAD Configuration
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

### Endpoint Configuration
On Endpoint Configuration Manager we create a Scope Tag per company.
For Company A, we name it "IT Department of Company A". 

Each device is distinguished under each scope tag by **device category** attribute on Graph API for Managed Devices ([https://graph.microsoft.com/beta/deviceManagement/managedDevices](https://graph.microsoft.com/beta/deviceManagement/managedDevices))

### Autopilot Configuration
On AAD create create a dynamic group for devices per company. 

On Endpoint Configuration Manager we create an autopilot profile per company.
The profile deploys a specific naming pattern (e.g. compapc001 - company a pc 001) for each device. 

([https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities](https://graph.microsoft.com/beta/deviceManagement/windowsAutopilotDeviceIdentities))

### Defender for Endpoint Configuration

([https://api.securitycenter.microsoft.com/api/machines/](https://api.securitycenter.microsoft.com/api/machines/))