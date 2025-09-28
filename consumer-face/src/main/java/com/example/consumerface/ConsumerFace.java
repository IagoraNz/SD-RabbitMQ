package com.example.consumerface;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeoutException;

import smile.classification.KNN;

/**
 * Consumer para mensagens face -> classifica happy/sad usando Smile KNN.
 * Treina em dados sintéticos gerados localmente (mesma lógica de geração de faces).
 * Processamento propositalmente mais lento para enfileirar mensagens.
 */
public class ConsumerFace {
    private static final String EXCHANGE = "images";
    private static final String QUEUE = "queue_face";
    private static final Gson gson = new Gson();

    // modelo KNN
    private static KNN<double[]> knnModel;

    public static void main(String[] args) throws Exception {
        // treina modelo
        trainModel();

        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        String user = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
        String pass = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(user);
        factory.setPassword(pass);

        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();
        channel.exchangeDeclare(EXCHANGE, "topic", true);

        // cria fila e bind
        channel.queueDeclare(QUEUE, true, false, false, null);
        channel.queueBind(QUEUE, EXCHANGE, "face");

        System.out.println("ConsumerFace waiting for messages...");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String body = new String(delivery.getBody());
                MessagePayload payload = gson.fromJson(body, MessagePayload.class);
                BufferedImage img = decodeImage(payload.image);
                double[] features = extractFeatures(img);

                int pred = knnModel.predict(features); // 1 = happy, 0 = sad
                String label = pred == 1 ? "HAPPY" : "SAD";

                // simulate slow processing (longer than generator interval)
                Thread.sleep(1000);

                System.out.println("[Face] id=" + payload.id + " -> predicted: " + label);
                // ack
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                e.printStackTrace();
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        channel.basicConsume(QUEUE, false, deliverCallback, consumerTag -> {});
    }

    static void trainModel() {
        // gerar dataset sintético (mesma lógica de face do generator)
        List<double[]> X = new ArrayList<>();
        List<Integer> Y = new ArrayList<>();
        int N = 400;
        Random rnd = new Random(12345);
        for (int i = 0; i < N; i++) {
            boolean happy = rnd.nextBoolean();
            BufferedImage img = makeFaceImage(happy);
            double[] f = extractFeatures(img);
            X.add(f);
            Y.add(happy ? 1 : 0);
        }
        double[][] xArr = X.toArray(new double[0][]);
        int[] yArr = Y.stream().mapToInt(i->i).toArray();
        knnModel = KNN.fit(xArr, yArr, 3);
        System.out.println("Face model trained (KNN).");
    }

    // --- Image utilities (same rules as generator but local) ---
    static BufferedImage makeFaceImage(boolean happy) {
        int w = 64, h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        if (happy) g.setColor(new Color(255, 220, 80));
        else g.setColor(new Color(80, 80, 90));
        g.fillRect(0,0,w,h);

        g.setColor(new Color(255, 230, 150));
        g.fillOval(6,6,52,52);

        g.setColor(Color.BLACK);
        g.fillOval(20,24,6,6);
        g.fillOval(38,24,6,6);

        g.setStroke(new BasicStroke(3));
        if (happy) g.drawArc(20,30,24,16,180,180);
        else g.drawArc(20,36,24,16,0,180);

        g.dispose();
        return img;
    }

    static BufferedImage decodeImage(String base64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64);
        try (ByteArrayInputStream bais = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(bais);
        }
    }

    static double[] extractFeatures(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        double sumR = 0, sumG = 0, sumB = 0;
        double sumUpper = 0, countUpper = 0, sumLower = 0, countLower = 0;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                double bright = (r + g + b) / 3.0;
                sumR += r; sumG += g; sumB += b;
                if (y < h/2) { sumUpper += bright; countUpper++; }
                else { sumLower += bright; countLower++; }
            }
        }
        double pixelCount = w * h;
        double avgBrightness = (sumR + sumG + sumB) / (3.0 * pixelCount) / 255.0; // 0..1
        double upperAvg = (countUpper==0)?0: (sumUpper / countUpper) / 255.0;
        double lowerAvg = (countLower==0)?0: (sumLower / countLower) / 255.0;
        double lowerUpperDiff = lowerAvg - upperAvg; // positive if lower is brighter
        double redRatio = (sumR) / (sumR + sumG + sumB + 1e-9);

        return new double[] { avgBrightness, lowerUpperDiff, redRatio };
    }

    static class MessagePayload {
        String id;
        String type;
        String timestamp;
        String image;
    }
}