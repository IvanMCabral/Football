package com.footballmanager.infrastructure.world.canary;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime-only inputs for the non-public World V2 canary runner. */
@ConfigurationProperties(prefix = "world.v2.canary")
public class WorldV2CanaryProperties {

    private boolean enabled;
    private String ownerId;
    private String expectedOwnerHash;
    private String expectedSourceSha;
    private String expectedSemanticPlanSha;
    private String expectedCanonicalFingerprint;
    private Long maxCurrentStorageBytes;
    private Long quotaBytes;
    private Long requiredHeadroomBytes;
    private Long retainedCushionBytes;
    private String mode = "VALIDATE_ONLY";
    private String confirm;
    private String databaseId;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getExpectedOwnerHash() { return expectedOwnerHash; }
    public void setExpectedOwnerHash(String expectedOwnerHash) { this.expectedOwnerHash = expectedOwnerHash; }
    public String getExpectedSourceSha() { return expectedSourceSha; }
    public void setExpectedSourceSha(String expectedSourceSha) { this.expectedSourceSha = expectedSourceSha; }
    public String getExpectedSemanticPlanSha() { return expectedSemanticPlanSha; }
    public void setExpectedSemanticPlanSha(String expectedSemanticPlanSha) { this.expectedSemanticPlanSha = expectedSemanticPlanSha; }
    public String getExpectedCanonicalFingerprint() { return expectedCanonicalFingerprint; }
    public void setExpectedCanonicalFingerprint(String expectedCanonicalFingerprint) { this.expectedCanonicalFingerprint = expectedCanonicalFingerprint; }
    public Long getMaxCurrentStorageBytes() { return maxCurrentStorageBytes; }
    public void setMaxCurrentStorageBytes(Long maxCurrentStorageBytes) { this.maxCurrentStorageBytes = maxCurrentStorageBytes; }
    public Long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(Long quotaBytes) { this.quotaBytes = quotaBytes; }
    public Long getRequiredHeadroomBytes() { return requiredHeadroomBytes; }
    public void setRequiredHeadroomBytes(Long requiredHeadroomBytes) { this.requiredHeadroomBytes = requiredHeadroomBytes; }
    public Long getRetainedCushionBytes() { return retainedCushionBytes; }
    public void setRetainedCushionBytes(Long retainedCushionBytes) { this.retainedCushionBytes = retainedCushionBytes; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getConfirm() { return confirm; }
    public void setConfirm(String confirm) { this.confirm = confirm; }
    public String getDatabaseId() { return databaseId; }
    public void setDatabaseId(String databaseId) { this.databaseId = databaseId; }
}
