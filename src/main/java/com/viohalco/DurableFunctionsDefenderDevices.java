package com.viohalco;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import com.microsoft.azure.functions.ExecutionContext;
import com.microsoft.azure.functions.HttpMethod;
import com.microsoft.azure.functions.HttpRequestMessage;
import com.microsoft.azure.functions.HttpResponseMessage;
import com.microsoft.azure.functions.annotation.AuthorizationLevel;
import com.microsoft.azure.functions.annotation.FunctionName;
import com.microsoft.azure.functions.annotation.HttpTrigger;
import com.microsoft.durabletask.DurableTaskClient;
import com.microsoft.durabletask.OrchestrationRunner;
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger;
import com.microsoft.durabletask.azurefunctions.DurableClientContext;
import com.microsoft.durabletask.azurefunctions.DurableClientInput;
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger;



public class DurableFunctionsDefenderDevices {
	 @FunctionName("StartDefenderDeviceSynchronization")
	    public HttpResponseMessage startDefenderDeviceSynchronization(@HttpTrigger(name = "req", methods = {HttpMethod.POST},authLevel = AuthorizationLevel.ANONYMOUS) HttpRequestMessage<Optional<String>> req,@DurableClientInput(name = "durableContext") DurableClientContext durableContext,final ExecutionContext context) {
	        context.getLogger().info("Calling Trigger Function");
	        final String query = req.getQueryParameters().get("keyVaultName");
	        final String keyvault = req.getBody().orElse(query);
	        DurableTaskClient client = durableContext.getClient();
	        String instanceId = client.scheduleNewOrchestrationInstance("DefenderDeviceSynchronization", keyvault);
	        context.getLogger().info("Created new Java orchestration with instance ID = " + instanceId);
	        return durableContext.createCheckStatusResponse(req, instanceId);
	    }

	    /**
	     * This is the orchestrator function, which can schedule activity functions, create durable timers,
	     * or wait for external events in a way that's completely fault-tolerant. The OrchestrationRunner.loadAndRun()
	     * static method is used to take the function input and execute the orchestrator logic.
	     */
	    @FunctionName("DefenderDeviceSynchronization")
	    public String defenderDeviceSynchronizationOrchestrator(@DurableOrchestrationTrigger(name = "runtimeState") String runtimeState,ExecutionContext functionContext) {
	        return OrchestrationRunner.loadAndRun(runtimeState, ctx -> {
	            String keyvault = ctx.getInput(String.class);
	            String result = "";
	            Logger log = functionContext.getLogger();
	            log.info("Started Orchestrator Function");
	            log.info("keyvault name:");
	            log.info(keyvault);
	            result += ctx.callActivity("SynchronizeDefenderDevices", keyvault, String.class).await() ;
	            return result;
	        });
	    }

	    /**
	     * This is the activity function that gets invoked by the orchestrator function.
	     */
	    @FunctionName("SynchronizeDefenderDevices")
	    public String synchronizeDefenderDevices(@DurableActivityTrigger(name = "name") String name,ExecutionContext functionContext) {
	        long startTime = System.nanoTime();
	        KeyVault keyVault = new KeyVault(name);
	        Logger log = functionContext.getLogger();
	        log.info("Started Activity Function");
	        
	        GraphManager api = new GraphManager( keyVault.getSecretValue("tenantId"), keyVault.getSecretValue("InTuneApp"), keyVault.getSecretValue("InTuneAppSecret") );
	        String response = api.setDeviceCategoryToMdeDevices(log); 
	        
	        log.info("RESPONDING:");
	        log.info(response);
	        
	        long endTime   = System.nanoTime();
	        long totalTime = endTime - startTime;
	        long convertSeconds = TimeUnit.SECONDS.convert(totalTime, TimeUnit.NANOSECONDS);
	        long convertMinutes = TimeUnit.MINUTES.convert(totalTime, TimeUnit.NANOSECONDS);
	        if ( convertSeconds > 60) {
	        	log.info("Execution Time in Minutes: "+convertMinutes);
	        }
	        else {
	        	log.info("Execution Time in Seconds: "+convertSeconds);
	        }
	        
	        return String.format("%s", response);
	    }
}
