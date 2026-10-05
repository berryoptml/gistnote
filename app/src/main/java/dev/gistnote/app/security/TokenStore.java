package dev.gistnote.app.security;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class TokenStore {
    private static final String KEY_ALIAS = "gistnote.github.token.v1";
    private static final String PREFS = "secure_credentials";
    private static final String TOKEN = "github_token_ciphertext";
    private static final int TAG_BITS = 128;
    private final SharedPreferences preferences;

    public TokenStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasToken() {
        return preferences.contains(TOKEN) && !getToken().isEmpty();
    }

    public void saveToken(String value) throws Exception {
        String token = value == null ? "" : value.trim();
        if (token.isEmpty()) {
            clear();
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] ciphertext = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        byte[] iv = cipher.getIV();
        ByteBuffer packed = ByteBuffer.allocate(4 + iv.length + ciphertext.length);
        packed.putInt(iv.length).put(iv).put(ciphertext);
        preferences.edit().putString(TOKEN,
                Base64.encodeToString(packed.array(), Base64.NO_WRAP)).apply();
    }

    public String getToken() {
        String encoded = preferences.getString(TOKEN, "");
        if (encoded == null || encoded.isEmpty()) return "";
        try {
            ByteBuffer packed = ByteBuffer.wrap(Base64.decode(encoded, Base64.NO_WRAP));
            int ivLength = packed.getInt();
            if (ivLength < 12 || ivLength > 32 || packed.remaining() <= ivLength) return "";
            byte[] iv = new byte[ivLength];
            packed.get(iv);
            byte[] ciphertext = new byte[packed.remaining()];
            packed.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static final String LOGIN = "github_login";

    public void setUsername(String username) {
        preferences.edit().putString(LOGIN, username == null ? "" : username.trim()).apply();
    }

    public String getUsername() {
        return preferences.getString(LOGIN, "");
    }

    public void clear() {
        preferences.edit().remove(TOKEN).remove(LOGIN).apply();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return ((KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null)).getSecretKey();
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}

