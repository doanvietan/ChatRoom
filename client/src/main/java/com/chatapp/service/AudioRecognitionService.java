package com.chatapp.service;

import org.vosk.Model;
import org.vosk.Recognizer;
import javax.sound.sampled.*;
import java.util.function.Consumer;

public class AudioRecognitionService {
    private Model voskModel;

    public void initModel() throws Exception {
        if (voskModel == null) {
            voskModel = new Model("model-vn");
        }
    }

    // Dùng Consumer (Callback) để trả kết quả text về cho UI
    public void startListening(Consumer<String> onResult, Consumer<Exception> onError) {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(16000, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                try (TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                     Recognizer recognizer = new Recognizer(voskModel, 16000)) {

                    line.open(format);
                    line.start();
                    byte[] buffer = new byte[4096];
                    long startTime = System.currentTimeMillis();
                    String finalResult = "";

                    while (System.currentTimeMillis() - startTime < 6000) {
                        int bytesRead = line.read(buffer, 0, buffer.length);
                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            finalResult = recognizer.getResult();
                            break;
                        }
                    }
                    String text = parseVoskResult(finalResult.isEmpty() ? recognizer.getFinalResult() : finalResult);
                    onResult.accept(text); // Trả kết quả về UI
                }
            } catch (Exception e) {
                onError.accept(e);
            }
        }).start();
    }

    private String parseVoskResult(String json) {
        if (json.contains("\"text\" : \"")) {
            return json.substring(json.indexOf("\"text\" : \"") + 10, json.lastIndexOf("\"")).trim();
        }
        return "";
    }
}