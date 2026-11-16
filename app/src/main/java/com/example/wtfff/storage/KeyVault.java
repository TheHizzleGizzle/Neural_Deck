package com.example.wtfff.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyVault {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "neural_deck_byok_master_key_v1";
    private static final String PREFS_NAME = "neural_deck_vault_prefs";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private final SharedPreferences prefs;

    public KeyVault(Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveApiKey(String providerId, String apiKey) {
        String slot = "enc_key_" + providerId;
        if (apiKey == null || apiKey.trim().isEmpty()) {
            prefs.edit().remove(slot).apply();
            return;
        }
        try {
            SecretKey secretKey = getOrCreateSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            byte[] iv = cipher.getIV();
            byte[] ciphertext = cipher.doFinal(apiKey.trim().getBytes(StandardCharsets.UTF_8));

            String payload = Base64.encodeToString(iv, Base64.NO_WRAP) + ":" +
                    Base64.encodeToString(ciphertext, Base64.NO_WRAP);
            prefs.edit().putString(slot, payload).apply();
        } catch (Exception e) {
            // Fallback obfuscated Base64 if hardware keystore unavailable in preview/sandbox
            String fallback = "B64:" + Base64.encodeToString(apiKey.trim().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            prefs.edit().putString(slot, fallback).apply();
        }
    }

    public String getApiKey(String providerId) {
        String slot = "enc_key_" + providerId;
        String payload = prefs.getString(slot, null);
        if (payload == null || payload.isEmpty()) {
            return "";
        }
        try {
            if (payload.startsWith("B64:")) {
                byte[] decoded = Base64.decode(payload.substring(4), Base64.NO_WRAP);
                return new String(decoded, StandardCharsets.UTF_8);
            }
            String[] parts = payload.split(":", 2);
            if (parts.length != 2) return "";
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ciphertext = Base64.decode(parts[1], Base64.NO_WRAP);

            SecretKey secretKey = getOrCreateSecretKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(128, iv));
            byte[] plain = cipher.doFinal(ciphertext);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public boolean hasApiKey(String providerId) {
        return !getApiKey(providerId).trim().isEmpty();
    }

    private SecretKey getOrCreateSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }
        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        keyGenerator.init(spec);
        return keyGenerator.generateKey();
    }
}
