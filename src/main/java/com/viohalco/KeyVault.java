package com.viohalco;

import com.azure.identity.DefaultAzureCredentialBuilder;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;

public class KeyVault {
	
	public KeyVault(String keyVaultName) {
		setKeyVaultName(keyVaultName);
		setKeyVaultUri("https://" + getKeyVaultName() + ".vault.azure.net");
	}
	
	public String getSecretValue(String secretName) {
		SecretClient secretClient = new SecretClientBuilder().vaultUrl(getKeyVaultUri()).credential(new DefaultAzureCredentialBuilder().build()).buildClient();
		KeyVaultSecret retrievedSecret = secretClient.getSecret(secretName);
		return retrievedSecret.getValue();
	}
	
	public String getKeyVaultName() {
		return keyVaultName;
	}
	
	public void setKeyVaultName(String keyVaultName) {
		this.keyVaultName = keyVaultName;
	}
	
	public String getKeyVaultUri() {
		return keyVaultUri;
	}
	
	public void setKeyVaultUri(String keyVaultUri) {
		this.keyVaultUri = keyVaultUri;
	}
	private String keyVaultName;
	private String keyVaultUri;
}