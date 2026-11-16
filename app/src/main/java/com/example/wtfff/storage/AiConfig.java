package com.example.wtfff.storage;

import android.content.Context;
import android.content.SharedPreferences;

public class AiConfig {
    public static final String PROVIDER_OPENAI = "openai";
    public static final String PROVIDER_OPENROUTER = "openrouter";
    public static final String PROVIDER_GROQ = "groq";
    public static final String PROVIDER_DEEPSEEK = "deepseek";
    public static final String PROVIDER_CUSTOM = "custom";

    private static final String PREFS = "neural_deck_ai_config";

    private final SharedPreferences prefs;
    private final KeyVault keyVault;

    public AiConfig(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.keyVault = new KeyVault(context);
    }

    public String getProviderId() {
        return prefs.getString("provider_id", PROVIDER_OPENROUTER);
    }

    public void setProviderId(String providerId) {
        prefs.edit().putString("provider_id", providerId).apply();
    }

    public String getBaseUrl() {
        String provider = getProviderId();
        return prefs.getString("base_url_" + provider, defaultBaseUrl(provider));
    }

    public void setBaseUrl(String providerId, String baseUrl) {
        prefs.edit().putString("base_url_" + providerId, baseUrl != null ? baseUrl.trim() : "").apply();
    }

    public String getModel() {
        String provider = getProviderId();
        return prefs.getString("model_" + provider, defaultModel(provider));
    }

    public void setModel(String providerId, String model) {
        prefs.edit().putString("model_" + providerId, model != null ? model.trim() : "").apply();
    }

    public float getTemperature() {
        return prefs.getFloat("temperature", 0.85f);
    }

    public void setTemperature(float temp) {
        prefs.edit().putFloat("temperature", Math.max(0.0f, Math.min(1.5f, temp))).apply();
    }

    public String getGlobalContext() {
        return prefs.getString("global_context", "");
    }

    public void setGlobalContext(String ctx) {
        prefs.edit().putString("global_context", ctx != null ? ctx.trim() : "").apply();
    }

    public String getActiveApiKey() {
        return keyVault.getApiKey(getProviderId());
    }

    public String getApiKeyForProvider(String providerId) {
        return keyVault.getApiKey(providerId);
    }

    public void saveApiKeyForProvider(String providerId, String apiKey) {
        keyVault.saveApiKey(providerId, apiKey);
    }

    public boolean isConfigured() {
        String url = getBaseUrl();
        String key = getActiveApiKey();
        // Allow localhost/custom local endpoints even without key if user explicitly entered custom URL, otherwise require key
        if (PROVIDER_CUSTOM.equals(getProviderId()) && (url.contains("127.0.0.1") || url.contains("localhost") || url.contains("10.0.2.2"))) {
            return !url.isEmpty();
        }
        return !url.isEmpty() && !key.isEmpty();
    }

    public String getProviderLabel(String providerId) {
        if (PROVIDER_OPENAI.equals(providerId)) return "OPENAI";
        if (PROVIDER_OPENROUTER.equals(providerId)) return "OPENROUTER";
        if (PROVIDER_GROQ.equals(providerId)) return "GROQ";
        if (PROVIDER_DEEPSEEK.equals(providerId)) return "DEEPSEEK";
        return "CUSTOM_URL";
    }

    public static String defaultBaseUrl(String providerId) {
        if (PROVIDER_OPENAI.equals(providerId)) return "https://api.openai.com/v1";
        if (PROVIDER_OPENROUTER.equals(providerId)) return "https://openrouter.ai/api/v1";
        if (PROVIDER_GROQ.equals(providerId)) return "https://api.groq.com/openai/v1";
        if (PROVIDER_DEEPSEEK.equals(providerId)) return "https://api.deepseek.com/v1";
        return "https://api.openai.com/v1";
    }

    public static String defaultModel(String providerId) {
        if (PROVIDER_OPENAI.equals(providerId)) return "gpt-4o-mini";
        if (PROVIDER_OPENROUTER.equals(providerId)) return "openai/gpt-4o-mini";
        if (PROVIDER_GROQ.equals(providerId)) return "llama-3.3-70b-versatile";
        if (PROVIDER_DEEPSEEK.equals(providerId)) return "deepseek-chat";
        return "gpt-4o-mini";
    }
}
