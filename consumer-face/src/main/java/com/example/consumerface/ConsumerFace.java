package com.example.consumerface;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private static String modelTag = "SYN";          // DATASET ou SYN
    private static boolean usingDataset = false;
    private static boolean verbose = false;

    public static void main(String[] args) throws Exception {
        // REMOVIDO: trainModel(); -> agora dependemos da variável de ambiente
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        String user = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
        String pass = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");
        String datasetDir = System.getenv().getOrDefault("DATASET_DIR", "./archive");
        verbose = "1".equals(System.getenv().getOrDefault("FACE_VERBOSE","0"));
        boolean forceSynthetic = "1".equals(System.getenv().getOrDefault("FACE_FORCE_SYNTHETIC","0"));
        // treina modelo (dataset real ou fallback)
        trainModel(datasetDir, forceSynthetic);

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

        System.out.println("ConsumerFace waiting for messages... (model=" + modelTag + ")");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String body = new String(delivery.getBody());
                MessagePayload payload = gson.fromJson(body, MessagePayload.class);
                BufferedImage img = decodeImage(payload.image);
                double[] features = extractFeatures(img);

                int pred = knnModel.predict(features);
                String label = pred == 1 ? "HAPPY" : "SAD";

                // simulate slow processing (longer than generator interval)
                Thread.sleep(1000);

                System.out.println("[Face][" + modelTag + "] id=" + payload.id + " -> predicted: " + label +
                        (verbose ? (" features=" + Arrays.toString(features)) : ""));
                // ack
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                e.printStackTrace();
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        channel.basicConsume(QUEUE, false, deliverCallback, consumerTag -> {});
    }

    // Substitui a antiga trainModel()
    static void trainModel(String datasetDir, boolean forceSynthetic) {
        if (!forceSynthetic) {
            try {
                Optional<TrainTestData> dsOpt = loadLocalDataset(datasetDir);
                if (dsOpt.isPresent()) {
                    TrainTestData ds = dsOpt.get();
                    knnModel = KNN.fit(ds.xTrain, ds.yTrain, 3);
                    usingDataset = true;
                    modelTag = "DATASET";
                    // avaliação + matriz confusão
                    int correct = 0;
                    int[][] cm = new int[2][2]; // [pred][real] 0=angry 1=happy
                    for (int i = 0; i < ds.xTest.length; i++) {
                        int yTrue = ds.yTest[i];
                        int yPred = knnModel.predict(ds.xTest[i]);
                        if (yPred == yTrue) correct++;
                        cm[yPred][yTrue]++;
                    }
                    double acc = ds.xTest.length == 0 ? 0.0 : correct / (double) ds.xTest.length;
                    System.out.println("Dataset carregado de: " + datasetDir);
                    System.out.println("Imagens: happy=" + ds.happyCount + " angry=" + ds.angryCount);
                    System.out.println("Split: train=" + ds.xTrain.length + " test=" + ds.xTest.length);
                    System.out.println(String.format("Acurácia teste=%.3f", acc));
                    System.out.println("Matriz de confusão (pred x real) [angry,happy]:");
                    System.out.println(" pred=angry -> [" + cm[0][0] + " " + cm[0][1] + "]");
                    System.out.println(" pred=happy -> [" + cm[1][0] + " " + cm[1][1] + "]");
                    if (verbose && ds.sampleFeature != null) {
                        System.out.println("Exemplo de feature (primeira imagem): " + Arrays.toString(ds.sampleFeature));
                    }
                    return;
                } else {
                    System.out.println("Dataset não encontrado/vazio em " + datasetDir + " -> fallback sintético.");
                }
            } catch (Exception e) {
                System.out.println("Falha ao usar dataset real: " + e.getMessage() + " -> fallback sintético.");
            }
        } else {
            System.out.println("FACE_FORCE_SYNTHETIC=1 -> ignorando dataset e usando sintético.");
        }
        // Fallback sintético (código original adaptado)
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
        modelTag = "SYN";
        usingDataset = false;
        System.out.println("Face model trained (synthetic fallback). Samples=" + N);
    }

    // Carrega dataset local (happy=1, angry=0). Retorna Optional vazio se não existir.
    static Optional<TrainTestData> loadLocalDataset(String baseDir) throws IOException {
        Path base = Paths.get(baseDir);
        Path happyDir = base.resolve("happy");
        Path angryDir = base.resolve("angry");
        if (!Files.isDirectory(happyDir) || !Files.isDirectory(angryDir)) {
            return Optional.empty();
        }

        List<LabeledFeature> samples = new ArrayList<>();
        int[] counters = new int[2];
        loadImagesFromDir(happyDir, 1, samples, counters);
        loadImagesFromDir(angryDir, 0, samples, counters);

        if (samples.isEmpty()) return Optional.empty();

        // embaralha
        Collections.shuffle(samples, new Random(42));

        int split = (int) Math.round(samples.size() * 0.8);
        List<LabeledFeature> train = samples.subList(0, split);
        List<LabeledFeature> test = samples.subList(split, samples.size());

        double[][] xTrain = train.stream().map(lf -> lf.features).toArray(double[][]::new);
        int[] yTrain = train.stream().mapToInt(lf -> lf.label).toArray();
        double[][] xTest = test.stream().map(lf -> lf.features).toArray(double[][]::new);
        int[] yTest = test.stream().mapToInt(lf -> lf.label).toArray();

        TrainTestData ttd = new TrainTestData(xTrain, yTrain, xTest, yTest);
        ttd.happyCount = counters[1];
        ttd.angryCount = counters[0];
        ttd.sampleFeature = samples.get(0).features;
        return Optional.of(ttd);
    }

    // === (RESTAURADO) método original de carregamento sem contadores ===
    static void loadImagesFromDir(Path dir, int label, List<LabeledFeature> out) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> st = Files.walk(dir)) {
            for (Path p : st.filter(Files::isRegularFile)
                    .filter(pp -> {
                        String n = pp.getFileName().toString().toLowerCase();
                        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png");
                    }).collect(Collectors.toList())) {
                try {
                    BufferedImage img = ImageIO.read(p.toFile());
                    if (img == null) continue;
                    double[] f = extractFeatures(img);
                    out.add(new LabeledFeature(f, label));
                } catch (Exception ignore) {}
            }
        }
    }

    // === Sobrecarga com contadores corrigida ===
    static void loadImagesFromDir(Path dir, int label, List<LabeledFeature> out, int[] counters) throws IOException {
        int before = out.size();
        loadImagesFromDir(dir, label, out); // usa método base
        counters[label] += (out.size() - before);
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

    static class LabeledFeature {
        double[] features;
        int label;
        LabeledFeature(double[] f, int l) { this.features = f; this.label = l; }
    }

    static class TrainTestData {
        double[][] xTrain; int[] yTrain;
        double[][] xTest; int[] yTest;
        int happyCount; int angryCount;
        double[] sampleFeature;
        TrainTestData(double[][] xT, int[] yT, double[][] xV, int[] yV) {
            this.xTrain = xT; this.yTrain = yT; this.xTest = xV; this.yTest = yV;
        }
    }

    static class MessagePayload {
        String id;
        String type;
        String timestamp;
        String image;
    }
}