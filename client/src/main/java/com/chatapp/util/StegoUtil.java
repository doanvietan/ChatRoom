package com.chatapp.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;

public class StegoUtil {

    // 1. Hàm tạo khóa AES từ mật khẩu
    private static SecretKeySpec generateKey(String password) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        byte[] key = sha.digest(password.getBytes(StandardCharsets.UTF_8));
        key = Arrays.copyOf(key, 16); // Dùng 128 bit (16 bytes)
        return new SecretKeySpec(key, "AES");
    }

    // 2. Giấu Text vào Ảnh
    public static File hideTextInImage(File sourceImage, String secretText, String password, File destImage) throws Exception {
        // Mã hóa Text bằng AES
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, generateKey(password));
        byte[] encryptedBytes = cipher.doFinal(secretText.getBytes(StandardCharsets.UTF_8));
        String base64Encrypted = Base64.getEncoder().encodeToString(encryptedBytes);

        // Cấu trúc giấu: [Độ dài chuỗi (32 bit)] + [Dữ liệu base64]
        byte[] msgBytes = base64Encrypted.getBytes();
        int msgLength = msgBytes.length;

        BufferedImage img = ImageIO.read(sourceImage);
        int width = img.getWidth();
        int height = img.getHeight();

        int byteIndex = 0;
        int bitIndex = 0;
        boolean lengthWritten = false;

        // Thuật toán LSB: duyệt qua từng pixel
        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);

                // Lấy các kênh màu (Alpha, Red, Green, Blue)
                int a = (rgb >> 24) & 0xFF;
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                // Cần 32 bit đầu để lưu chiều dài chuỗi
                for (int colorIndex = 0; colorIndex < 3; colorIndex++) {
                    int currentBit = 0;

                    if (!lengthWritten) {
                        currentBit = (msgLength >> (31 - bitIndex)) & 1;
                        bitIndex++;
                        if (bitIndex == 32) {
                            lengthWritten = true;
                            bitIndex = 0;
                        }
                    } else if (byteIndex < msgLength) {
                        currentBit = (msgBytes[byteIndex] >> (7 - bitIndex)) & 1;
                        bitIndex++;
                        if (bitIndex == 8) {
                            bitIndex = 0;
                            byteIndex++;
                        }
                    } else {
                        break outer; // Đã giấu xong
                    }

                    // Chèn bit vào bit cuối (LSB)
                    if (colorIndex == 0) r = (r & 0xFE) | currentBit;
                    if (colorIndex == 1) g = (g & 0xFE) | currentBit;
                    if (colorIndex == 2) b = (b & 0xFE) | currentBit;
                }

                // Cập nhật lại pixel
                int newRgb = (a << 24) | (r << 16) | (g << 8) | b;
                img.setRGB(x, y, newRgb);
            }
        }

        // Lưu lại thành file PNG để không bị nén mất dữ liệu (JPG sẽ làm mất LSB)
        ImageIO.write(img, "png", destImage);
        return destImage;
    }

    // 3. Trích xuất và giải mã Text từ Ảnh
    public static String extractTextFromImage(File imageFile, String password) throws Exception {
        BufferedImage img = ImageIO.read(imageFile);
        int width = img.getWidth();
        int height = img.getHeight();

        int msgLength = 0;
        int bitIndex = 0;
        boolean lengthRead = false;
        byte[] msgBytes = null;
        int byteIndex = 0;

        outer:
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                int[] colors = {r, g, b};

                for (int c : colors) {
                    int lsb = c & 1; // Lấy bit cuối

                    if (!lengthRead) {
                        msgLength = (msgLength << 1) | lsb;
                        bitIndex++;
                        if (bitIndex == 32) {
                            lengthRead = true;
                            bitIndex = 0;
                            if (msgLength <= 0 || msgLength > 5000000) return null; // Sai mật khẩu hoặc ảnh không có mật thư
                            msgBytes = new byte[msgLength];
                        }
                    } else if (byteIndex < msgLength) {
                        msgBytes[byteIndex] = (byte) ((msgBytes[byteIndex] << 1) | lsb);
                        bitIndex++;
                        if (bitIndex == 8) {
                            bitIndex = 0;
                            byteIndex++;
                        }
                    } else {
                        break outer; // Đọc xong
                    }
                }
            }
        }

        if (msgBytes == null) return null;

        // Giải mã AES
        String base64Encrypted = new String(msgBytes);
        byte[] encryptedBytes = Base64.getDecoder().decode(base64Encrypted);

        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, generateKey(password));
        byte[] decryptedBytes = cipher.doFinal(encryptedBytes);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}