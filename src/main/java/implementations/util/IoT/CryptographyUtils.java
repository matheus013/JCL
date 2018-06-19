package implementations.util.IoT;

import java.io.UnsupportedEncodingException;
import java.security.*;
import java.util.Arrays;

import javax.crypto.*;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class CryptographyUtils {

    private static String clusterPassword;

    private static byte[] cryptDuplicate(byte[] msg, byte[] iv, int mode) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, UnsupportedEncodingException, BadPaddingException, IllegalBlockSizeException {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(clusterPassword.getBytes("UTF-8"));
        key = Arrays.copyOf(key, 16);
        SecretKeySpec skc = new SecretKeySpec(key, "AES");
        Cipher c = Cipher.getInstance("AES/CBC/PKCS5PADDING");
        c.init(mode, skc, new IvParameterSpec(iv));
        byte[] original = c.doFinal(msg);
        return original;
    }

    public static byte[] crypt(byte[] msg, byte[] iv) {
        try {
            return cryptDuplicate(msg, iv, Cipher.ENCRYPT_MODE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] decrypt(byte[] cryped, byte[] iv) {
        try {
            return cryptDuplicate(cryped, iv, Cipher.DECRYPT_MODE);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] generateIV() {
        byte iv[] = new byte[16];
        try {
            SecureRandom randomSecureRandom = SecureRandom.getInstance("SHA1PRNG");
            randomSecureRandom.nextBytes(iv);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return iv;
    }

    public static byte[] generateRegitrationKey(byte[] msg, byte[] iv) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] key = sha.digest(clusterPassword.getBytes("UTF-8"));
            key = Arrays.copyOfRange(key, 16, 32);
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec macKey = new SecretKeySpec(key, "HmacSHA256");
            mac.init(macKey);
            mac.update(iv);
            mac.update(msg);
            return mac.doFinal();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String getClusterPassword() {
        return clusterPassword;
    }

    public static void setClusterPassword(String clusterPassword) {
        CryptographyUtils.clusterPassword = clusterPassword;
    }
}
