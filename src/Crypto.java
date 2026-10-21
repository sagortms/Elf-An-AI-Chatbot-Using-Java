import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class Crypto {
    private static final int ITERATIONS = 10000;
    private static final SecureRandom random = new SecureRandom();

    public static String newSalt() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    public static String hash(String secret, String salt) {
        return Base64.getEncoder().encodeToString(pbkdf2(secret, Base64.getDecoder().decode(salt), 256));
    }

    public static byte[] deriveKey(String secret, String salt) {
        return pbkdf2(secret, Base64.getDecoder().decode(salt), 128);
    }

    private static byte[] pbkdf2(String secret, byte[] salt, int bits) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), salt, ITERATIONS, bits);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            try {
                // pbkdf2 is always there on java 8, this is just a fallback
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                md.update(salt);
                byte[] d = md.digest(secret.getBytes("UTF-8"));
                return Arrays.copyOf(d, bits / 8);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static String encrypt(String text, byte[] key) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            byte[] enc = c.doFinal(text.getBytes("UTF-8"));
            // iv goes in front of the encrypted bytes
            byte[] all = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(enc, 0, all, iv.length, enc.length);
            return Base64.getEncoder().encodeToString(all);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    public static String decrypt(String data, byte[] key) {
        try {
            byte[] all = Base64.getDecoder().decode(data);
            byte[] iv = Arrays.copyOfRange(all, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(all, 12, all.length - 12), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }
}
