package ir.hamrah.agent;

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

/** Device-only secret storage. No plaintext credential is written to preferences. */
public final class SecretStore {
    private static final Object LOCK = new Object();
    private static final String KEY_ALIAS = "ir.hamrah.agent.device-secrets.v1";
    private static final String API_SLOT = "openai_api_key";
    private final SharedPreferences preferences;

    public SecretStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences("hamrah_secrets", Context.MODE_PRIVATE);
    }

    public String getApiKey() {
        return getSecret(API_SLOT);
    }

    public void setApiKey(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (!trimmed.isEmpty() && (trimmed.length() < 16 || trimmed.length() > 1024
                || containsControlCharacter(trimmed))) {
            throw new IllegalArgumentException("کلید API معتبر نیست؛ کلید را بدون فاصله یا خط جدید وارد کن.");
        }
        putSecret(API_SLOT, trimmed);
    }

    /** Removes only the AI credential; unrelated mail credentials remain connected. */
    public void clear() {
        removeSecret(API_SLOT);
    }

    public String getSecret(String name) {
        validateName(name);
        synchronized (LOCK) {
            String encrypted = preferences.getString(name + ".ciphertext", null);
            if (encrypted == null) return "";
            String encodedIv = preferences.getString(name + ".iv", null);
            try {
                if (encodedIv == null) throw new IllegalStateException();
                SecretKey key = existingKey();
                if (key == null) throw new IllegalStateException();
                byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
                if (iv.length != 12) throw new IllegalStateException();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
                cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
                byte[] plaintext = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
                return new String(plaintext, StandardCharsets.UTF_8);
            } catch (Exception failure) {
                // Do not attach causes: callers may accidentally log exception details.
                throw new IllegalStateException("اطلاعات اتصال در این گوشی قابل بازیابی نیست؛ آن را حذف و دوباره وارد کن.");
            }
        }
    }

    public void putSecret(String name, String value) {
        validateName(name);
        if (value == null || value.isEmpty()) {
            removeSecret(name);
            return;
        }
        if (value.length() > 8192) throw new IllegalArgumentException("اطلاعات اتصال بیش از حد طولانی است.");
        synchronized (LOCK) {
            try {
                SecretKey key = existingKey();
                if (key == null) key = createKey();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.ENCRYPT_MODE, key);
                cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
                String encrypted = Base64.encodeToString(
                        cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
                String iv = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP);
                if (!preferences.edit().putString(name + ".ciphertext", encrypted)
                        .putString(name + ".iv", iv).commit()) {
                    throw new IllegalStateException();
                }
            } catch (Exception failure) {
                throw new IllegalStateException("ذخیرهٔ امن اطلاعات اتصال روی این گوشی انجام نشد.");
            }
        }
    }

    public void removeSecret(String name) {
        validateName(name);
        synchronized (LOCK) {
            if (!preferences.edit().remove(name + ".ciphertext").remove(name + ".iv").commit()) {
                throw new IllegalStateException("حذف اطلاعات اتصال انجام نشد؛ دوباره تلاش کن.");
            }
        }
    }

    private static SecretKey existingKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return (SecretKey) store.getKey(KEY_ALIAS, null);
    }

    private static SecretKey createKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }

    private static void validateName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_.-]{1,64}")) {
            throw new IllegalArgumentException("نام اطلاعات اتصال معتبر نیست.");
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i)) || Character.isISOControl(value.charAt(i))) return true;
        }
        return false;
    }
}
