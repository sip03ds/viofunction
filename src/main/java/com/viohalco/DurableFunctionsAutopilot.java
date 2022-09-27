package com.viohalco;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;

import com.microsoft.azure.functions.*;
import com.microsoft.azure.functions.annotation.*;
import com.microsoft.durabletask.*;
import com.microsoft.durabletask.azurefunctions.*;
import java.util.logging.Logger;

import com.microsoft.graph.models.WindowsAutopilotDeviceIdentity;

public class DurableFunctionsAutopilot {
    // https://viohalcofunctions-20220907075420754.azurewebsites.net/StartAutopilotProfileSynchronization
    // https://viohalcofunctions-20220907075420754.scm.azurewebsites.net

    /**
     * This HTTP-triggered function starts the orchestration.
     */
    @FunctionName("StartAutopilotProfileSynchronization")
    public HttpResponseMessage startAutopilotProfileSynchronization(@HttpTrigger(name = "req", methods = {HttpMethod.POST},authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> req,@DurableClientInput(name = "durableContext") DurableClientContext durableContext,final ExecutionContext context) {
        context.getLogger().info("Calling Trigger Function");
        final String query = req.getQueryParameters().get("keyVaultName");
        final String keyvault = req.getBody().orElse(query);
        DurableTaskClient client = durableContext.getClient();


        //final String guid = "8441e2cc-cff3-4a76-ac7e-18872b7349fb";
        //OrchestrationMetadata metadata = client.getInstanceMetadata(guid,false);
        //if (metadata.isRunning()) {
        //    return req.createResponseBuilder(HttpStatus.CONFLICT).body("An instance with ID: "+guid+" already exists.").build();
        //}

        String instanceId = client.scheduleNewOrchestrationInstance("AutopilotProfileSynchronization", keyvault);
        context.getLogger().info("Created new Java orchestration with instance ID = " + instanceId);
        return durableContext.createCheckStatusResponse(req, instanceId);

    }

    /**
     * This is the orchestrator function, which can schedule activity functions, create durable timers,
     * or wait for external events in a way that's completely fault-tolerant. The OrchestrationRunner.loadAndRun()
     * static method is used to take the function input and execute the orchestrator logic.
     */
    @FunctionName("AutopilotProfileSynchronization")
    public String autopilotProfileSynchronizationOrchestrator(@DurableOrchestrationTrigger(name = "runtimeState") String runtimeState,ExecutionContext functionContext) {

        return OrchestrationRunner.loadAndRun(runtimeState, ctx -> {

            String keyvault = ctx.getInput(String.class);

            String result = "";
            Logger log = functionContext.getLogger();
            log.info("Started Orchestrator Function");
            log.info("keyvault name:");
            log.info(keyvault);
            
            result += ctx.callActivity("SynchronizeAutopilotProfiles", keyvault, String.class).await() + ", ";
            return result;
        });
    }

    /**
     * This is the activity function that gets invoked by the orchestrator function.
     */
    @FunctionName("SynchronizeAutopilotProfiles")
    public String synchronizeAutopilotProfiles(@DurableActivityTrigger(name = "name") String name,ExecutionContext functionContext) {
        KeyVault keyVault = new KeyVault(name);

        Logger log = functionContext.getLogger();
        log.info("Started Activity Function");
        
        GraphManager api = new GraphManager( keyVault.getSecretValue("tenantId"), keyVault.getSecretValue("InTuneApp"), keyVault.getSecretValue("InTuneAppSecret") );
        long startTime = System.nanoTime();
        ArrayList<WindowsAutopilotDeviceIdentity> unassignedAutopilotProfiles = api.assignProfilesToAutopilotDevices(log,startTime);
        String response;
        if ( unassignedAutopilotProfiles != null ) {
            log.info("Devices that need manual actions: "+unassignedAutopilotProfiles.size());
            StringBuilder sb = new StringBuilder(" { ");
            sb.append(" \"unassignedAutoplotProfilesLength\": "+unassignedAutopilotProfiles.size()+" , ");
            sb.append(" \"unassignedAutoplotProfiles\": [ ");
            for (WindowsAutopilotDeviceIdentity autopilotProfile :  unassignedAutopilotProfiles) {
            	sb.append(api.getJsonDataFromWindowsAutopilotDeviceIdentity(autopilotProfile));
            	sb.append(",");
            }
			sb.lastIndexOf(",");
			sb.delete(sb.lastIndexOf(","),sb.lastIndexOf(",")+1);
			sb.append(" ] ");
			sb.append(" } ");	
			response = sb.toString();
			log.info("Reponse Built: "+response);
        }
        else {
        	response = "{ \"unassignedAutopilotProfilesLength\": 0 }";
        }
        long endTime   = System.nanoTime();
        long totalTime = endTime - startTime;
        long convertSeconds = TimeUnit.SECONDS.convert(totalTime, TimeUnit.NANOSECONDS);
        long convertMinutes = TimeUnit.MINUTES.convert(totalTime,TimeUnit.NANOSECONDS);
        if ( convertSeconds > 60) {
        	log.info("Execution Time in Minutes: "+convertMinutes);
        }
        else {
        	log.info("Execution Time in Seconds: "+convertSeconds);
        }
        return String.format("%s", response);
    }
}