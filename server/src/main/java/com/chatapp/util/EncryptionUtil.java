package com.chatapp.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class EncryptionUtil {
    private static final String ALGORITHM = "AES";
    private static final String SECRET_KEY = "ChatappSecretKey";

    // Thêm một cờ nhận diện để biết tin nhắn nào đã được mã hóa
    private static final String PREFIX = "ENC:";

    public static String encrypt(String data) {
        if (data == null) return null;

        try {
            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);

            byte[] encryptedBytes = cipher.doFinal(data.getBytes("UTF-8"));
            String base64String = Base64.getEncoder().encodeToString(encryptedBytes);

            // Gắn tiền tố ENC: vào trước chuỗi Base64 rồi mới lưu DB
            return PREFIX + base64String;

        } catch (Exception e) {
            System.err.println("Lỗi mã hóa tin nhắn: " + e.getMessage());
            return data;
        }
    }

    public static String decrypt(String encryptedData) {
        // Kiểm tra xem tin nhắn có tiền tố mã hóa hay không
        if (encryptedData == null || !encryptedData.startsWith(PREFIX)) {
            // Nếu KHÔNG có chữ ENC: ở đầu -> Đây là tin nhắn cũ (plain text)
            // Ta trả về nguyên gốc, KHÔNG cố gắng giải mã nữa để tránh lỗi.
            return encryptedData;
        }

        try {
            // Cắt bỏ 4 ký tự "ENC:" ở đầu để lấy ra chuỗi Base64 thực sự
            String actualBase64 = encryptedData.substring(PREFIX.length());

            SecretKeySpec secretKey = new SecretKeySpec(SECRET_KEY.getBytes(), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);

            byte[] decodedBytes = Base64.getDecoder().decode(actualBase64);
            byte[] decryptedBytes = cipher.doFinal(decodedBytes);

            return new String(decryptedBytes, "UTF-8");

        } catch (Exception e) {
            System.err.println("Lỗi giải mã tin nhắn: " + e.getMessage());
            return encryptedData; // Trả về chuỗi lỗi nếu có trục trặc
        }
    }
}