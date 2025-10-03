package com.example.consumerteam;

import com.google.gson.Gson;
import com.rabbitmq.client.*;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

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
    private static String[] LABEL_NAMES = {"RED","BLUE","GREEN"}; // default para sintético
    private static String modelTag = "SYN"; // DATASET ou SYN
    private static boolean verbose = false;

    public static void main(String[] args) throws Exception {
        // REMOVER chamada antiga trainModel();
        String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
        String user = System.getenv().getOrDefault("RABBITMQ_USER", "guest");
        String pass = System.getenv().getOrDefault("RABBITMQ_PASS", "guest");
        String datasetDir = System.getenv().getOrDefault("TEAM_DATASET_DIR", "./archive");
        verbose = "1".equals(System.getenv().getOrDefault("TEAM_VERBOSE","0"));
        boolean forceSynthetic = "1".equals(System.getenv().getOrDefault("TEAM_FORCE_SYNTHETIC","0"));

        trainModel(datasetDir, forceSynthetic);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);
        factory.setUsername(user);
        factory.setPassword(pass);

        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();
        channel.exchangeDeclare(EXCHANGE, "topic", true);

        channel.queueDeclare(QUEUE, true, false, false, null);
        channel.queueBind(QUEUE, EXCHANGE, "team");

        System.out.println("ConsumerTeam waiting for messages... (model=" + modelTag + ")");

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            try {
                String body = new String(delivery.getBody());
                MessagePayload payload = gson.fromJson(body, MessagePayload.class);
                BufferedImage img = decodeImage(payload.image);
                double[] features = extractFeatures(img);

                int pred = knnModel.predict(features);
                String team = LABEL_NAMES[pred];

                // slow processing
                Thread.sleep(1200);

                System.out.println("[Team][" + modelTag + "] id=" + payload.id + " -> predicted: " + team +
                        (verbose ? (" features=" + Arrays.toString(features)) : ""));
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                e.printStackTrace();
                channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
        };

        channel.basicConsume(QUEUE, false, deliverCallback, consumerTag -> {});
    }

    // Novo método unificado
    static void trainModel(String datasetDir, boolean forceSynthetic) {
        if (!forceSynthetic) {
            try {
                Optional<Dataset> dsOpt = loadDataset(datasetDir);
                if (dsOpt.isPresent()) {
                    Dataset ds = dsOpt.get();
                    LABEL_NAMES = new String[]{"CORINTHIANS","FLAMENGO","PALMEIRAS"};
                    knnModel = KNN.fit(ds.xTrain, ds.yTrain, 3);
                    modelTag = "DATASET";
                    // avaliação
                    int[][] cm = new int[3][3]; // pred x real
                    int correct = 0;
                    for (int i = 0; i < ds.xTest.length; i++) {
                        int yTrue = ds.yTest[i];
                        int yPred = knnModel.predict(ds.xTest[i]);
                        if (yPred == yTrue) correct++;
                        cm[yPred][yTrue]++;
                    }
                    double acc = ds.xTest.length == 0 ? 0.0 : correct / (double) ds.xTest.length;
                    System.out.println("Dataset carregado de: " + datasetDir);
                    System.out.println("Treino: COR=" + ds.trainCounts[0] + " FLA=" + ds.trainCounts[1] + " PAL=" + ds.trainCounts[2]);
                    System.out.println("Teste : COR=" + ds.testCounts[0] + " FLA=" + ds.testCounts[1] + " PAL=" + ds.testCounts[2]);
                    System.out.println(String.format("Acurácia teste=%.3f", acc));
                    System.out.println("Matriz de confusão (pred x real) ordem [COR,FLA,PAL]:");
                    for (int p = 0; p < 3; p++) {
                        System.out.println(" pred=" + LABEL_NAMES[p] + " -> [" + cm[p][0] + " " + cm[p][1] + " " + cm[p][2] + "]");
                    }
                    if (verbose && ds.sampleFeature != null) {
                        System.out.println("Exemplo feature primeira imagem treino: " + Arrays.toString(ds.sampleFeature));
                    }
                    return;
                } else {
                    System.out.println("Dataset inválido ou vazio em " + datasetDir + " -> fallback sintético.");
                }
            } catch (Exception e) {
                System.out.println("Falha dataset: " + e.getMessage() + " -> fallback sintético.");
            }
        } else {
            System.out.println("TEAM_FORCE_SYNTHETIC=1 -> ignorando dataset.");
        }
        // Fallback sintético (código original adaptado)
        List<double[]> X = new ArrayList<>();
        List<Integer> Y = new ArrayList<>();
        int N = 450;
        Random rnd = new Random(123);
        for (int i = 0; i < N; i++) {
            int team = rnd.nextInt(3); // 0=RED 1=BLUE 2=GREEN
            BufferedImage img = makeTeamImage(team);
            double[] f = extractFeatures(img);
            X.add(f);
            Y.add(team);
        }
        double[][] xArr = X.toArray(new double[0][]);
        int[] yArr = Y.stream().mapToInt(i->i).toArray();
        knnModel = KNN.fit(xArr, yArr, 3);
        LABEL_NAMES = new String[]{"RED","BLUE","GREEN"};
        modelTag = "SYN";
        System.out.println("Team model trained (synthetic). Samples=" + N);
    }

    // Carrega dataset: base/treino/<classe>, base/teste/<classe>
    static Optional<Dataset> loadDataset(String baseDir) throws IOException {
        Path base = Paths.get(baseDir);
        Path trainDir = base.resolve("treino");
        Path testDir  = base.resolve("teste");
        if (!Files.isDirectory(trainDir) || !Files.isDirectory(testDir)) return Optional.empty();

        String[] classDirs = {"corinthians","flamengo","palmeiras"}; // ordem fixa -> labels 0,1,2
        List<double[]> xTrainList = new ArrayList<>();
        List<Integer> yTrainList = new ArrayList<>();
        List<double[]> xTestList  = new ArrayList<>();
        List<Integer> yTestList  = new ArrayList<>();
        int[] trainCounts = new int[3];
        int[] testCounts  = new int[3];

        for (int label = 0; label < classDirs.length; label++) {
            Path tDir = trainDir.resolve(classDirs[label]);
            Path vDir = testDir.resolve(classDirs[label]);
            loadImagesInto(tDir, label, xTrainList, yTrainList, trainCounts);
            loadImagesInto(vDir, label, xTestList, yTestList, testCounts);
        }

        if (xTrainList.isEmpty()) return Optional.empty();

        double[][] xTrain = xTrainList.toArray(new double[0][]);
        int[] yTrain = yTrainList.stream().mapToInt(i->i).toArray();
        double[][] xTest = xTestList.toArray(new double[0][]);
        int[] yTest = yTestList.stream().mapToInt(i->i).toArray();

        Dataset ds = new Dataset(xTrain, yTrain, xTest, yTest);
        ds.trainCounts = trainCounts;
        ds.testCounts = testCounts;
        ds.sampleFeature = xTrain[0];
        return Optional.of(ds);
    }

    static void loadImagesInto(Path dir, int label, List<double[]> X, List<Integer> Y, int[] counters) throws IOException {
        if (!Files.isDirectory(dir)) return;
        try (Stream<Path> st = Files.walk(dir)) {
            for (Path p : st.filter(Files::isRegularFile)
                    .filter(pp -> {
                        String n = pp.getFileName().toString().toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                    }).collect(Collectors.toList())) {
                try {
                    BufferedImage img = ImageIO.read(p.toFile());
                    if (img == null) continue;
                    double[] f = extractFeatures(img);
                    X.add(f);
                    Y.add(label);
                    counters[label]++;
                } catch (Exception ignore) {}
            }
        }
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

    static class Dataset {
        double[][] xTrain; int[] yTrain;
        double[][] xTest;  int[] yTest;
        int[] trainCounts; int[] testCounts;
        double[] sampleFeature;
        Dataset(double[][] xt, int[] yt, double[][] xv, int[] yv) {
            this.xTrain = xt; this.yTrain = yt; this.xTest = xv; this.yTest = yv;
        }
    }

    static class MessagePayload {
        String id;
        String type;
        String timestamp;
        String image;
    }
}