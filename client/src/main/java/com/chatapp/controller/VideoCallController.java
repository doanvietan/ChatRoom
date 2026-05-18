package com.chatapp.controller;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;

import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.bytedeco.javacv.OpenCVFrameGrabber;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoCallController {

    @FXML private ImageView localVideoView;
    @FXML private ImageView remoteVideoView;
    @FXML private Label statusLabel;

    private DatagramSocket udpSocket;
    private volatile boolean isCalling = false;
    private int roomId;
    private int myUserId;

    // Đảm bảo IP này là IP LAN của máy chạy Server (Ví dụ: 172.16.246.102 hoặc 192.168.1.x)
    private String serverHost = "localhost";
    private int serverPort = 200;
    private OpenCVFrameGrabber grabber;

    public void setCallInfo(int roomId, int myUserId) {
        this.roomId = roomId;
        this.myUserId = myUserId;
        Platform.runLater(() -> statusLabel.setText("Room: " + roomId));
        startCall();
    }

    public void startCall() {
        if (isCalling) return;
        isCalling = true;
        try {
            udpSocket = new DatagramSocket();

            // 1. Gửi gói tin ĐĂNG KÝ (REGISTER) để Server biết mình là ai
            new Thread(() -> {
                try {
                    // Server của bạn nhận lệnh: "REGISTER:RoomID:UserID:..."
                    String joinMsg = "REGISTER:" + roomId + ":" + myUserId + ":JOIN";
                    byte[] data = joinMsg.getBytes();
                    InetAddress address = InetAddress.getByName(serverHost);
                    DatagramPacket packet = new DatagramPacket(data, data.length, address, serverPort);

                    // Gửi 5 lần để chắc chắn Server nhận được
                    for (int i = 0; i < 5; i++) {
                        if (!isCalling) break;
                        udpSocket.send(packet);
                        Thread.sleep(100);
                    }
                    System.out.println("Đã gửi lệnh REGISTER đến server");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();

            // 2. Khởi chạy 2 luồng CHÍNH (Chỉ start 1 lần duy nhất ở đây)
            new Thread(this::sendVideoLoop, "VideoSender").start();
            new Thread(this::receiveVideoLoop, "VideoReceiver").start();

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> statusLabel.setText("Lỗi Socket!"));
        }
    }

    // --- SENDER LOOP ---
    private void sendVideoLoop() {
        try {
            grabber = new OpenCVFrameGrabber(0);
            grabber.setImageWidth(320);
            grabber.setImageHeight(240);
            grabber.start();

            Java2DFrameConverter converter = new Java2DFrameConverter();

            while (isCalling) {
                Frame frame = grabber.grab();
                if (frame != null) {
                    BufferedImage image = converter.convert(frame);
                    if (image != null) {
                        // Hiển thị Local
                        Image fxImage = SwingFXUtils.toFXImage(image, null);
                        Platform.runLater(() -> {
                            if (localVideoView != null) {
                                localVideoView.setScaleX(-1);
                                localVideoView.setImage(fxImage);
                            }
                        });

                        // Gửi đi
                        sendFrameOverNetwork(image);
                    }
                }
                Thread.sleep(33);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendFrameOverNetwork(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Header đúng với Server: "VIDEO:RoomID:UserID:"
            String header = "VIDEO:" + roomId + ":" + myUserId + ":";
            byte[] headerBytes = header.getBytes();

            byte[] packetData = new byte[headerBytes.length + imageBytes.length];
            System.arraycopy(headerBytes, 0, packetData, 0, headerBytes.length);
            System.arraycopy(imageBytes, 0, packetData, headerBytes.length, imageBytes.length);

            if (packetData.length < 60000) {
                DatagramPacket packet = new DatagramPacket(
                        packetData, packetData.length,
                        InetAddress.getByName(serverHost), serverPort
                );
                udpSocket.send(packet);
            }
        } catch (Exception e) {}
    }

    // --- RECEIVER LOOP ---
    private void receiveVideoLoop() {
        byte[] buffer = new byte[60000];
        System.out.println("Bắt đầu lắng nghe video...");

        while (isCalling) {
            try {
                if (udpSocket == null || udpSocket.isClosed()) break;

                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet); // Code sẽ dừng ở đây chờ tin đến

                // Xử lý dữ liệu
                String msg = new String(packet.getData(), 0, Math.min(packet.getLength(), 50));

                if (msg.startsWith("VIDEO:")) {
                    int bodyStart = findBodyStart(packet.getData());
                    if (bodyStart != -1) {
                        int len = packet.getLength() - bodyStart;
                        byte[] imgData = new byte[len];
                        System.arraycopy(packet.getData(), bodyStart, imgData, 0, len);

                        ByteArrayInputStream bais = new ByteArrayInputStream(imgData);
                        BufferedImage img = ImageIO.read(bais);

                        if (img != null) {
                            Image fxImage = SwingFXUtils.toFXImage(img, null);
                            Platform.runLater(() -> {
                                if (remoteVideoView != null) {
                                    remoteVideoView.setImage(fxImage);
                                    statusLabel.setText("Đã kết nối");
                                }
                            });
                        }
                    }
                }
            } catch (Exception e) {
                // e.printStackTrace();
            }
        }
    }

    private int findBodyStart(byte[] data) {
        int colons = 0;
        for (int i = 0; i < 50; i++) {
            if (data[i] == ':') colons++;
            if (colons == 3) return i + 1;
        }
        return -1;
    }

    @FXML
    public void endCall() {
        isCalling = false;
        try {
            if (grabber != null) {
                grabber.stop();
                grabber.release();
            }
            if (udpSocket != null && !udpSocket.isClosed()) {
                udpSocket.close();
            }
        } catch (Exception e) {}

        Platform.runLater(() -> {
            if (localVideoView != null && localVideoView.getScene() != null) {
                Stage stage = (Stage) localVideoView.getScene().getWindow();
                stage.close();
            }
        });
    }
}