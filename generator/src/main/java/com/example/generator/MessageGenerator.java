package com.example.generator;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Gera mensagens (images) e publica no exchange "images" com routing keys "face" e "team".
 * Taxa: ~5 msg/s (200ms).
 */
public class MessageGenerator {
    private static final String EXCHANGE = "images";
    private static final Gson gson = new Gson();
    private static final Random rnd = new Random();

    public static void main(String[] args) throws Exception {
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

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        // envia a cada 200ms -> 5 mensagens/segundo
        scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean isFace = rnd.nextBoolean();
                String routingKey = isFace ? "face" : "team";

                byte[] imageBytes;
                if (isFace) {
                    // gera face (aleatoriamente happy/sad)
                    boolean happy = rnd.nextBoolean();
                    imageBytes = renderFaceImage(happy);
                } else {
                    // gera brasão (aleatoriamente 3 times)
                    int team = rnd.nextInt(3); // 0=RED,1=BLUE,2=GREEN
                    imageBytes = renderTeamImage(team);
                }

                String base64Image = Base64.getEncoder().encodeToString(imageBytes);

                MessagePayload p = new MessagePayload(UUID.randomUUID().toString(), routingKey, Instant.now().toString(), base64Image);
                String json = gson.toJson(p);

                channel.basicPublish(EXCHANGE, routingKey, null, json.getBytes());
                System.out.println("Published -> key: " + routingKey + " id: " + p.id);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    static byte[] renderFaceImage(boolean happy) throws Exception {
        int w = 64, h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        // background: happy -> yellow bright, sad -> dark gray
        if (happy) {
            g.setColor(new Color(255, 220, 80));
        } else {
            g.setColor(new Color(80, 80, 90));
        }
        g.fillRect(0, 0, w, h);

        // draw a simple face circle
        g.setColor(new Color(255, 230, 150));
        g.fillOval(6, 6, 52, 52);

        // eyes
        g.setColor(Color.BLACK);
        g.fillOval(20, 24, 6, 6);
        g.fillOval(38, 24, 6, 6);

        // mouth: arc (smile or frown)
        g.setStroke(new BasicStroke(3));
        if (happy) {
            g.drawArc(20, 30, 24, 16, 180, 180); // smile
        } else {
            g.drawArc(20, 36, 24, 16, 0, 180); // sad
        }

        g.dispose();
        return toPNGBytes(img);
    }

    static byte[] renderTeamImage(int team) throws Exception {
        int w = 64, h = 64;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();

        // background neutral
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);

        // big stripe with team color and simple crest text
        Color c;
        String label;
        switch (team) {
            case 0: c = Color.RED; label = "RED"; break;
            case 1: c = Color.BLUE; label = "BLUE"; break;
            default: c = Color.GREEN; label = "GREEN"; break;
        }
        g.setColor(c);
        g.fillRect(8, 8, w-16, h-16);

        // small emblem circle
        g.setColor(Color.YELLOW);
        g.fillOval(22, 18, 20, 20);

        // optional mini-label
        g.setColor(Color.BLACK);
        g.setFont(new Font("Arial", Font.BOLD, 10));
        g.drawString(label, 22, 55);

        g.dispose();
        return toPNGBytes(img);
    }

    static byte[] toPNGBytes(BufferedImage img) throws Exception {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        }
    }

    static class MessagePayload {
        String id;
        String type;
        String timestamp;
        String image; // base64

        MessagePayload(String id, String type, String timestamp, String image) {
            this.id = id;
            this.type = type;
            this.timestamp = timestamp;
            this.image = image;
        }
    }
}