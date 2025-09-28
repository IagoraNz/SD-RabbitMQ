package com.example.consumerteam;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.List;

import smile.classification.KNN;

/**
 * Consumer de mensagens 'team' -> identifica time (RED/BLUE/GREEN).
 * Treina um KNN em imagens sintéticas com cores dominantes.
 */
public class ConsumerTeam {
    private static final String EXCHANGE = "images";
    private static final String QUEUE = "queue_team";
    private static final Gson gson = new Gson();

    private static KNN<double[]> knnModel;
    private static final String[] TEAM_NAMES = {"RED","BLUE","GREEN"};

    public static void main(String[] args) throws Exception {
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

        channel.queueDeclare(QUEUE, true, false, false, null);
        channel.queueBind(QUEUE, EXCHANGE, "team");

        System.out.println("ConsumerTeam waiting for messages...");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String body = new String(delivery.getBody());
                MessagePayload payload = gson.fromJson(body, MessagePayload.class);
                BufferedImage img = decodeImage(payload.image);
                double[] features = extractFeatures(img);

                int pred = knnModel.predict(features); // returns 0..2
                String team = TEAM_NAMES[pred];

                // slow processing
                Thread.sleep(1200);

                System.out.println("[Team] id=" + payload.id + " -> predicted team: " + team);
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                e.printStackTrace();
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        channel.basicConsume(QUEUE, false, deliverCallback, consumerTag -> {});
    }

    static void trainModel() {
        List<double[]> X = new ArrayList<>();
        List<Integer> Y = new ArrayList<>();
        int N = 450;
        Random rnd = new Random(123);
        for (int i = 0; i < N; i++) {
            int team = rnd.nextInt(3);
            BufferedImage img = makeTeamImage(team);
            double[] f = extractFeatures(img);
            X.add(f);
            Y.add(team);
        }
        double[][] xArr = X.toArray(new double[0][]);
        int[] yArr = Y.stream().mapToInt(i->i).toArray();
        knnModel = KNN.fit(xArr, yArr, 3);
        System.out.println("Team model trained (KNN).");
    }

    // generate synthetic team image (same colors as generator)
    static BufferedImage makeTeamImage(int team) {
        int w = 64, h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        Color c;
        switch (team) {
            case 0: c = Color.RED; break;
            case 1: c = Color.BLUE; break;
            default: c = Color.GREEN; break;
        }
        g.setColor(c);
        g.fillRect(8,8,w-16,h-16);

        g.setColor(Color.YELLOW);
        g.fillOval(22,18,20,20);

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
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xff;
                int g = (rgb >> 8) & 0xff;
                int b = rgb & 0xff;
                sumR += r;
                sumG += g;
                sumB += b;
            }
        }
        double total = sumR + sumG + sumB + 1e-9;
        double redRatio = sumR / total;
        double greenRatio = sumG / total;
        double blueRatio = sumB / total;
        double avgBrightness = (sumR + sumG + sumB) / (3.0 * w * h) / 255.0;
        return new double[] { redRatio, greenRatio, blueRatio, avgBrightness };
    }

    static class MessagePayload {
        String id;
        String type;
        String timestamp;
        String image;
    }
}