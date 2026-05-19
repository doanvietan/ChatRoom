package com.chatapp.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EncryptionUtil {
    private static final String ALGORITHM = "AES";

    // Khóa bảo mật 16 byte (128-bit).
    // LƯU Ý: Trong thực tế, bạn nên lấy key này từ file Config (vd: biến môi trường) thay vì hardcode.
    private static final String SECRET_KEY = "ChatappSecretKey";

    public static String encrypt(String data) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(encryptedBytes);
        } catch (Exception e) {
            System.err.println("Lỗi mã hóa tin nhắn: " + e.getMessage());
            return data; // Fallback trả về nguyên gốc nếu lỗi
        }
    }

    public static String decrypt(String encryptedData) {
        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decodedBytes = Base64.getDecoder().decode(encryptedData);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);
            return new String(decryptedBytes, "UTF-8");
        } catch (Exception e) {
            System.err.println("Lỗi giải mã tin nhắn: " + e.getMessage());
            return encryptedData; // Fallback trả về chuỗi mã hóa nếu không giải mã được
        }
    }
}