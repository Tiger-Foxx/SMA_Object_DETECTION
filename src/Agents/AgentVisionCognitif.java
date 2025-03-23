package Agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.dnn.Dnn;
import org.opencv.dnn.Net;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class AgentVisionCognitif extends Agent {

    // Constantes et paramètres
    private static final int FOCAL_LENGTH = 615; // Longueur focale (pixels) pour webcam standard
    private static final int FRAME_WIDTH = 640;
    private static final int FRAME_HEIGHT = 480;
    private static final String WINDOW_TITLE = "Agent Cognitif - Vision Intelligente";
    private static final String[] COCO_NAMES = { "personne", "vélo", "voiture", "moto", "avion", "bus", "train",
            "camion", "bateau", "feu de circulation", "bouche d'incendie", "panneau stop", "parcomètre", "banc",
            "oiseau", "chat", "chien", "cheval", "mouton", "vache", "éléphant", "ours", "zèbre", "girafe",
            "sac à dos", "parapluie", "sac à main", "cravate", "valise", "frisbee", "skis", "snowboard",
            "ballon de sport", "cerf-volant", "batte de baseball", "gant de baseball", "skateboard", "planche de surf",
            "raquette de tennis", "bouteille", "verre à vin", "tasse", "fourchette", "couteau", "cuillère",
            "bol", "banane", "pomme", "sandwich", "orange", "brocoli", "carotte", "hot-dog", "pizza", "donut", "gâteau",
            "chaise", "canapé", "plante en pot", "lit", "table à manger", "toilettes", "téléviseur", "ordinateur portable",
            "souris", "télécommande", "clavier", "téléphone mobile", "micro-ondes", "four", "grille-pain", "évier",
            "réfrigérateur", "livre", "horloge", "vase", "ciseaux", "ours en peluche", "sèche-cheveux", "brosse à dents" };

    // Tailles moyennes des objets en cm (pour le calcul de distance)
    private static final Map<String, Double> OBJECT_SIZES = new HashMap<String, Double>() {{
        put("personne", 45.0); // Largeur moyenne des épaules
        put("visage", 16.0);   // Largeur moyenne d'un visage
        put("voiture", 180.0); // Largeur moyenne d'une voiture
        put("chat", 30.0);     // Longueur moyenne d'un chat
        put("chien", 40.0);    // Longueur moyenne d'un chien
        put("bouteille", 8.0); // Largeur moyenne d'une bouteille
        put("ordinateur portable", 35.0); // Largeur moyenne d'un laptop
        put("téléphone mobile", 7.0);     // Largeur moyenne d'un smartphone
    }};

    // Capteurs et modèles
    private VideoCapture camera;
    private Net faceDetector;
    private Net objectDetector;
    private boolean modelsLoaded = false;

    // État et contrôle
    private AtomicBoolean cameraActive = new AtomicBoolean(false);
    private boolean sendMessages = true;
    private int selectedMode = 0; // 0: Tous, 1: Visages, 2: Objets
    private Map<String, ObjectTracker> trackedObjects = new HashMap<>();
    private long lastMessageTime = 0;
    private static final long MESSAGE_THRESHOLD_MS = 500; // Envoyer au max 2 messages par seconde

    // Interface utilisateur
    private JFrame frame;
    private JPanel controlPanel;
    private JLabel cameraFeed;
    private JTextArea logArea;
    private JComboBox<String> modeSelector;
    private JComboBox<String> agentSelector;
    private JToggleButton cameraToggle;
    private JCheckBox messageToggle;
    private JButton screenshotButton;
    private JProgressBar confidenceThresholdSlider;
    private JLabel thresholdValueLabel;
    private JLabel statusLabel;
    private float confidenceThreshold = 0.5f;
    private List<AID> receiverAgents = new ArrayList<>();

    @Override
    protected void setup() {
        System.out.println("Agent cognitif de vision démarré: " + getLocalName());

        // Initialiser les modèles DNN et capteurs
        initModels();

        // Créer l'interface utilisateur moderne
        SwingUtilities.invokeLater(this::createModernUI);

        // Ajouter le comportement de vision principal
        addBehaviour(new VisionProcessingBehaviour(this, 50)); // 20 FPS

        // Rechercher d'autres agents dans le conteneur
        discoverReceiverAgents();
        // Ajouter ce comportement dans la méthode setup()
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    String content = msg.getContent();
                    String sender = msg.getSender().getLocalName();

                    // Traiter les différents types de messages
                    if (content.equals("PING_RESPONSE")) {
                        logMessage("✓ Connecté à l'agent " + sender);
                    } else if (content.startsWith("ACK:")) {
                        // Message de confirmation de réception
                        int count = Integer.parseInt(content.substring(4));
                        logMessage("✓ Agent " + sender + " a reçu " + count + " détections");
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void initModels() {
        try {
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

            // Initialiser la caméra avec résolution spécifique
            camera = new VideoCapture(0);
            if (!camera.isOpened()) {
                System.err.println("⚠️ Erreur: Impossible d'accéder à la caméra!");
                return;
            }

            camera.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_WIDTH, FRAME_WIDTH);
            camera.set(org.opencv.videoio.Videoio.CAP_PROP_FRAME_HEIGHT, FRAME_HEIGHT);

            // Charger le modèle de détection de visage (DNN)
            String faceProtoPath = "models/deploy.prototxt";
            String faceModelPath = "models/res10_300x300_ssd_iter_140000.caffemodel";

            // Charger le modèle de détection d'objets YOLOv4
            String objectProtoPath = "models/yolov4.cfg";
            String objectModelPath = "models/yolov4.weights";

            // Vérifier l'existence des fichiers modèles
            if (new File(faceProtoPath).exists() && new File(faceModelPath).exists()
                    && new File(objectProtoPath).exists() && new File(objectModelPath).exists()) {

                // Charger le modèle de détection de visage
                faceDetector = Dnn.readNetFromCaffe(faceProtoPath, faceModelPath);

                // Charger le modèle de détection d'objets
                objectDetector = Dnn.readNetFromDarknet(objectProtoPath, objectModelPath);

                // Configurer pour utiliser GPU si disponible
                objectDetector.setPreferableBackend(Dnn.DNN_BACKEND_OPENCV);
                objectDetector.setPreferableTarget(Dnn.DNN_TARGET_OPENCL);

                modelsLoaded = true;
                System.out.println("✅ Modèles DNN chargés avec succès");
            } else {
                System.err.println("⚠️ Les fichiers de modèle n'existent pas!");
                String currentDir = new File(".").getAbsolutePath();
                System.err.println("Répertoire actuel: " + currentDir);
                System.err.println("Veuillez placer les modèles dans: " + currentDir + "/models/");
            }

        } catch (Exception e) {
            System.err.println("⚠️ Erreur lors de l'initialisation des modèles: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createModernUI() {
        // Configuration du look and feel moderne
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            // Appliquer des styles supplémentaires
            UIManager.put("Button.arc", 15);
            UIManager.put("Component.arc", 15);
            UIManager.put("ProgressBar.arc", 15);
            UIManager.put("TextComponent.arc", 15);
        } catch (Exception e) {
            System.err.println("Impossible de définir le look and feel: " + e.getMessage());
        }

        // Créer la fenêtre principale avec un design moderne
        frame = new JFrame(WINDOW_TITLE);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doDelete(); // Arrêter proprement l'agent
            }
        });

        // Utiliser BorderLayout avec des marges plus grandes
        frame.setLayout(new BorderLayout(15, 15));

        // Panel principal avec marge et couleur de fond plus moderne
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(248, 250, 252));

        // Créer le panneau vidéo avec une bordure élégante et arrondissement
        cameraFeed = new JLabel();
        cameraFeed.setPreferredSize(new Dimension(FRAME_WIDTH, FRAME_HEIGHT));
        cameraFeed.setBackground(new Color(30, 41, 59));
        cameraFeed.setOpaque(true);
        cameraFeed.setHorizontalAlignment(SwingConstants.CENTER);
        cameraFeed.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(2, 2, 2, 2)));

        // Panneau de flux vidéo au centre avec ombre
        JPanel videoPanel = new JPanel(new BorderLayout());
        videoPanel.add(cameraFeed, BorderLayout.CENTER);
        videoPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        videoPanel.setOpaque(false);

        // Créer le panneau de contrôle avec un design moderne
        createControlPanel();

        // Créer le panneau de journalisation amélioré
        createEnhancedLogPanel();

        // Ajouter les panneaux au panneau principal
        mainPanel.add(videoPanel, BorderLayout.CENTER);
        mainPanel.add(controlPanel, BorderLayout.EAST);

        // Statut et barre de progression moderne
        JPanel statusPanel = new JPanel(new BorderLayout(10, 0));
        statusPanel.setOpaque(false);

        statusLabel = new JLabel("Système prêt", SwingConstants.LEFT);
        statusLabel.setForeground(new Color(37, 99, 235));
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusPanel.add(statusLabel, BorderLayout.WEST);

        // Ajouter un indicateur d'état avec animation
        JProgressBar activityIndicator = new JProgressBar();
        activityIndicator.setIndeterminate(true);
        activityIndicator.setVisible(false);
        activityIndicator.setPreferredSize(new Dimension(80, 12));
        statusPanel.add(activityIndicator, BorderLayout.EAST);

        mainPanel.add(statusPanel, BorderLayout.SOUTH);

        // Ajouter le panneau principal à la fenêtre
        frame.add(mainPanel, BorderLayout.CENTER);

        // Configurer et afficher la fenêtre
        frame.pack();
        frame.setLocationRelativeTo(null); // Centrer sur l'écran
        frame.setVisible(true);

        // Initialiser l'état des boutons
        updateControlState();
    }
    private void createEnhancedLogPanel() {
        // Zone de journal améliorée avec plus d'espace et meilleure lisibilité
        logArea = new JTextArea(8, 40); // Hauteur doublée
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(248, 250, 252));
        logArea.setForeground(new Color(30, 41, 59));
        logArea.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        // Ajouter un DefaultCaret pour l'auto-scroll
        DefaultCaret caret = (DefaultCaret) logArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        // Créer un défilement avec des barres modernes
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240), 1));
        scrollPane.setPreferredSize(new Dimension(FRAME_WIDTH, 150)); // Plus de hauteur

        // Ajouter des boutons de contrôle pour le log
        JPanel logControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logControlPanel.setOpaque(false);

        JButton clearLogButton = new JButton("Effacer");
        clearLogButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clearLogButton.addActionListener(e -> logArea.setText(""));

        JButton saveLogButton = new JButton("Enregistrer");
        saveLogButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        saveLogButton.addActionListener(e -> saveLogToFile());

        logControlPanel.add(clearLogButton);
        logControlPanel.add(saveLogButton);

        // Panel contenant le scroll et les boutons
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(scrollPane, BorderLayout.CENTER);
        logPanel.add(logControlPanel, BorderLayout.SOUTH);

        // Ajouter le panneau au bas de la fenêtre
        frame.add(logPanel, BorderLayout.SOUTH);
    }

    private void saveLogToFile() {
        try {
            // Créer un répertoire logs s'il n'existe pas
            File dir = new File("logs");
            if (!dir.exists()) {
                dir.mkdir();
            }

            // Générer un nom de fichier avec horodatage
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
            String timestamp = sdf.format(new Date());
            String filename = "logs/vision_log_" + timestamp + ".txt";

            // Enregistrer le log
            java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileWriter(filename));
            writer.write(logArea.getText());
            writer.close();

            logMessage("✓ Journal enregistré: " + filename);
        } catch (Exception ex) {
            logMessage("⚠️ Erreur lors de l'enregistrement du journal: " + ex.getMessage());
        }
    }

    private void createControlPanel() {
        // Panneau de contrôle avec un GridBagLayout pour un alignement précis
        controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        controlPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));
        controlPanel.setBackground(new Color(245, 245, 250));
        controlPanel.setPreferredSize(new Dimension(250, FRAME_HEIGHT));

        // Titre du panneau de contrôle
        JLabel titleLabel = new JLabel("Contrôle & Configuration");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 14));
        titleLabel.setForeground(new Color(50, 50, 80));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));
        controlPanel.add(titleLabel);

        // Contrôle de la caméra
        cameraToggle = new JToggleButton("Démarrer Caméra");
        cameraToggle.setFont(new Font("Arial", Font.BOLD, 12));
        cameraToggle.setFocusPainted(false);
        cameraToggle.setAlignmentX(Component.CENTER_ALIGNMENT);
        cameraToggle.addActionListener(e -> toggleCamera());
        customizeButton(cameraToggle);
        addComponentWithMargin(controlPanel, cameraToggle, 0, 8, 0, 15);

        // Mode de détection
        JLabel modeLabel = new JLabel("Mode de détection:");
        modeLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        modeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addComponentWithMargin(controlPanel, modeLabel, 0, 0, 0, 5);

        String[] modes = {"Tous les objets", "Visages seulement", "Objets COCO"};
        modeSelector = new JComboBox<>(modes);
        modeSelector.setFont(new Font("Arial", Font.PLAIN, 12));
        modeSelector.setAlignmentX(Component.LEFT_ALIGNMENT);
        modeSelector.addActionListener(e -> selectedMode = modeSelector.getSelectedIndex());
        addComponentWithMargin(controlPanel, modeSelector, 0, 0, 0, 15);

        // Seuil de confiance
        JLabel thresholdLabel = new JLabel("Seuil de confiance:");
        thresholdLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        thresholdLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addComponentWithMargin(controlPanel, thresholdLabel, 0, 0, 0, 5);

        JPanel thresholdPanel = new JPanel(new BorderLayout(5, 0));
        thresholdPanel.setOpaque(false);
        thresholdPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));

        confidenceThresholdSlider = new JProgressBar(0, 100);
        confidenceThresholdSlider.setValue((int)(confidenceThreshold * 100));
        confidenceThresholdSlider.setStringPainted(false);
        confidenceThresholdSlider.setForeground(new Color(30, 120, 80));
        thresholdPanel.add(confidenceThresholdSlider, BorderLayout.CENTER);

        thresholdValueLabel = new JLabel(String.format("%.2f", confidenceThreshold));
        thresholdValueLabel.setFont(new Font("Arial", Font.BOLD, 12));
        thresholdValueLabel.setPreferredSize(new Dimension(35, 20));
        thresholdPanel.add(thresholdValueLabel, BorderLayout.EAST);

        // Ajouter un écouteur pour mettre à jour le seuil de confiance
        confidenceThresholdSlider.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent evt) {
                updateConfidenceThreshold(evt);
            }
            @Override
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                updateConfidenceThreshold(evt);
            }
        });
        confidenceThresholdSlider.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseDragged(java.awt.event.MouseEvent evt) {
                updateConfidenceThreshold(evt);
            }
        });

        addComponentWithMargin(controlPanel, thresholdPanel, 0, 0, 0, 15);

        // Sélecteur d'agent récepteur
        JLabel agentLabel = new JLabel("Agent récepteur:");
        agentLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        agentLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addComponentWithMargin(controlPanel, agentLabel, 0, 0, 0, 5);

        String[] agentNames = {"Tous les agents", "Agent1", "Agent2"};
        agentSelector = new JComboBox<>(agentNames);
        agentSelector.setFont(new Font("Arial", Font.PLAIN, 12));
        agentSelector.setAlignmentX(Component.LEFT_ALIGNMENT);
        addComponentWithMargin(controlPanel, agentSelector, 0, 0, 0, 15);

        // Activation des messages
        messageToggle = new JCheckBox("Envoi des messages");
        messageToggle.setFont(new Font("Arial", Font.PLAIN, 12));
        messageToggle.setSelected(sendMessages);
        messageToggle.setOpaque(false);
        messageToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
        messageToggle.addActionListener(e -> sendMessages = messageToggle.isSelected());
        addComponentWithMargin(controlPanel, messageToggle, 0, 0, 0, 15);

        // Bouton de capture d'écran
        screenshotButton = new JButton("Capture d'écran");
        screenshotButton.setFont(new Font("Arial", Font.BOLD, 12));
        screenshotButton.setFocusPainted(false);
        screenshotButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        screenshotButton.addActionListener(this::takeScreenshot);
        customizeButton(screenshotButton);
        addComponentWithMargin(controlPanel, screenshotButton, 0, 8, 0, 0);

        // Ajouter un espace flexible
        controlPanel.add(Box.createVerticalGlue());

        // Informations sur l'agent
        JLabel infoLabel = new JLabel("Agent: " + getLocalName());
        infoLabel.setFont(new Font("Arial", Font.ITALIC, 11));
        infoLabel.setForeground(Color.GRAY);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        controlPanel.add(infoLabel);
    }

    private void createLogPanel() {
        // Zone de journal pour afficher les événements
        logArea = new JTextArea(5, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(245, 245, 245));
        logArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        // Ajouter le panneau au bas de la fenêtre
        frame.add(scrollPane, BorderLayout.SOUTH);
    }

    private void customizeButton(AbstractButton button) {
        button.setBackground(new Color(60, 120, 200));
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setMaximumSize(new Dimension(Short.MAX_VALUE, 35));
    }

    private void addComponentWithMargin(JPanel panel, JComponent component, int top, int left, int bottom, int right) {
        component.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
        panel.add(component);
    }

    private void updateConfidenceThreshold(java.awt.event.MouseEvent evt) {
        JProgressBar slider = (JProgressBar) evt.getSource();
        int width = slider.getWidth();
        int mouseX = evt.getX();

        // Calculer la nouvelle valeur en fonction de la position de la souris
        float newValue = (float) mouseX / width;
        if (newValue < 0) newValue = 0;
        if (newValue > 1) newValue = 1;

        // Mettre à jour le seuil de confiance
        confidenceThreshold = newValue;
        confidenceThresholdSlider.setValue((int)(confidenceThreshold * 100));
        thresholdValueLabel.setText(String.format("%.2f", confidenceThreshold));
    }

    private void toggleCamera() {
        if (cameraActive.get()) {
            // Arrêter la caméra
            cameraActive.set(false);
            cameraToggle.setText("Démarrer Caméra");
            cameraToggle.setBackground(new Color(60, 120, 200));
            logMessage("Caméra arrêtée");
        } else {
            // Démarrer la caméra si possible
            if (camera != null && camera.isOpened()) {
                cameraActive.set(true);
                cameraToggle.setText("Arrêter Caméra");
                cameraToggle.setBackground(new Color(200, 60, 60));
                logMessage("Caméra démarrée");
            } else {
                logMessage("⚠️ Erreur: Impossible d'accéder à la caméra!");
            }
        }

        updateControlState();
    }

    private void updateControlState() {
        boolean isActive = cameraActive.get();

        // Mettre à jour l'état des contrôles
        modeSelector.setEnabled(isActive);
        agentSelector.setEnabled(isActive && sendMessages);
        messageToggle.setEnabled(isActive);
        screenshotButton.setEnabled(isActive);
    }

    private void takeScreenshot(ActionEvent e) {
        if (!cameraActive.get()) return;

        try {
            // Obtenir le contenu actuel de l'étiquette cameraFeed
            Icon icon = cameraFeed.getIcon();
            if (icon instanceof ImageIcon) {
                BufferedImage image = (BufferedImage) ((ImageIcon) icon).getImage();

                // Créer un répertoire screenshots s'il n'existe pas
                File dir = new File("screenshots");
                if (!dir.exists()) {
                    dir.mkdir();
                }

                // Générer un nom de fichier avec un horodatage
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
                String timestamp = sdf.format(new Date());
                String filename = "screenshots/capture_" + timestamp + ".png";

                // Enregistrer l'image
                File outputFile = new File(filename);
                javax.imageio.ImageIO.write(image, "png", outputFile);

                logMessage("✓ Capture d'écran enregistrée: " + filename);
            }
        } catch (Exception ex) {
            logMessage("⚠️ Erreur lors de la capture d'écran: " + ex.getMessage());
        }
    }

    private void logMessage(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String logEntry = "[" + timestamp + "] " + message + "\n";

        SwingUtilities.invokeLater(() -> {
            logArea.append(logEntry);
            // Défilement automatique vers le bas
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void discoverReceiverAgents() {
        // Utiliser le Directory Facilitator de JADE pour trouver les agents récepteurs
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("receiver-agent");
        template.addServices(sd);

        try {
            DFAgentDescription[] results = DFService.search(this, template);
            receiverAgents.clear();

            for (DFAgentDescription result : results) {
                AID agent = result.getName();
                receiverAgents.add(agent);
            }

            // Si aucun agent n'est trouvé, rechercher sur le réseau
            if (receiverAgents.isEmpty()) {
                searchRemoteAgents();
            }

            // Mettre à jour le sélecteur d'agents
            SwingUtilities.invokeLater(() -> {
                agentSelector.removeAllItems();
                agentSelector.addItem("Tous les agents");
                for (AID agent : receiverAgents) {
                    agentSelector.addItem(agent.getLocalName() + "@" + agent.getName().split("@")[1]);
                }
            });

            logMessage("✓ " + receiverAgents.size() + " agents récepteurs trouvés");
        } catch (FIPAException e) {
            logMessage("⚠️ Erreur lors de la recherche d'agents: " + e.getMessage());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void searchRemoteAgents() {
        // Rechercher spécifiquement l'agent ReceiverAgent sur le réseau
        AID remoteAgent = new AID("ReceiverAgent", AID.ISLOCALNAME);
        receiverAgents.add(remoteAgent);

        // Ajouter un comportement pour vérifier la connectivité
        addBehaviour(new OneShotBehaviour() {
            @Override
            public void action() {
                ACLMessage pingMsg = new ACLMessage(ACLMessage.QUERY_IF);
                pingMsg.addReceiver(remoteAgent);
                pingMsg.setContent("PING");
                send(pingMsg);
                logMessage("🔍 Recherche de l'agent distant: " + remoteAgent.getLocalName());
            }
        });
    }

    // Classe interne pour le suivi des objets
    private static class ObjectTracker {
        String type;
        Rect bounds;
        double distance;
        double confidence;
        long lastSeenTimestamp;

        public ObjectTracker(String type, Rect bounds, double distance, double confidence) {
            this.type = type;
            this.bounds = bounds;
            this.distance = distance;
            this.confidence = confidence;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        public void update(Rect bounds, double distance, double confidence) {
            this.bounds = bounds;
            this.distance = distance;
            this.confidence = confidence;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }
    }

    // Comportement principal de traitement de vision
    private class VisionProcessingBehaviour extends TickerBehaviour {

        public VisionProcessingBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            // Ignorer si la caméra n'est pas active ou si les modèles ne sont pas chargés
            if (!cameraActive.get() || !modelsLoaded) return;

            try {
                // Capturer une image de la caméra
                Mat frame = new Mat();
                if (!camera.read(frame) || frame.empty()) {
                    return;
                }

                // Créer une copie pour l'affichage
                Mat displayFrame = frame.clone();

                // En fonction du mode sélectionné, effectuer la détection appropriée
                List<DetectionResult> detections = new ArrayList<>();

                if (selectedMode == 0 || selectedMode == 1) {
                    // Détecter les visages
                    List<DetectionResult> faceDetections = detectFaces(frame, displayFrame);
                    detections.addAll(faceDetections);
                }

                if (selectedMode == 0 || selectedMode == 2) {
                    // Détecter les objets
                    List<DetectionResult> objectDetections = detectObjects(frame, displayFrame);
                    detections.addAll(objectDetections);
                }

                // Mettre à jour le suivi des objets
                updateObjectTracking(detections);

                // Informer les autres agents si nécessaire
                if (sendMessages) {
                    sendDetectionMessages(detections);
                }

                // Ajouter des informations sur l'image
                addInfoOverlay(displayFrame, detections);

                // Mettre à jour l'interface utilisateur
                updateUI(displayFrame);

            } catch (Exception e) {
                System.err.println("Erreur dans le traitement de vision: " + e.getMessage());
                e.printStackTrace();
            }
        }

        private List<DetectionResult> detectFaces(Mat frame, Mat displayFrame) {
            List<DetectionResult> results = new ArrayList<>();

            try {
                // Préparer l'image pour la détection de visage
                Mat blob = Dnn.blobFromImage(frame, 1.0, new Size(300, 300),
                        new Scalar(104.0, 177.0, 123.0), false, false);

                // Passer l'image au réseau de neurones
                faceDetector.setInput(blob);
                Mat detections = faceDetector.forward();

                // Analyser les résultats de détection
                int cols = frame.cols();
                int rows = frame.rows();

                // Méthode corrigée pour accéder aux détections
                // Vérifier le format réel des détections
                int numDetections = (int) detections.total();
                float[] data = new float[(int) (detections.total() * detections.channels())];
                detections.get(0, 0, data);

                // Le format typique pour ce modèle est [1, 1, N, 7] où N est le nombre de détections
                // et chaque détection contient: [image_id, label, confidence, x_min, y_min, x_max, y_max]
                for (int i = 0; i < numDetections; i++) {
                    int offset = i * 7;

                    // Vérifier si l'indice est valide
                    if (offset + 6 < data.length) {
                        float confidence = data[offset + 2];

                        if (confidence > confidenceThreshold) {
                            int x1 = (int) (data[offset + 3] * cols);
                            int y1 = (int) (data[offset + 4] * rows);
                            int x2 = (int) (data[offset + 5] * cols);
                            int y2 = (int) (data[offset + 6] * rows);

                            // Créer un rectangle pour le visage
                            Rect faceRect = new Rect(x1, y1, x2 - x1, y2 - y1);

                            // Calculer la distance basée sur la taille du visage
                            double distance = calculateDistance("visage", faceRect.width);

                            // Dessiner le rectangle du visage
                            Imgproc.rectangle(displayFrame, faceRect, new Scalar(0, 255, 0), 2);

                            // Ajouter le résultat à la liste
                            DetectionResult result = new DetectionResult("visage", faceRect, distance, confidence);
                            results.add(result);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur dans la détection de visage: " + e.getMessage());
                e.printStackTrace(); // Ajout de la trace de la pile pour déboguer
            }

            return results;
        }

        private List<DetectionResult> detectObjects(Mat frame, Mat displayFrame) {
            List<DetectionResult> results = new ArrayList<>();

            try {
                // Préparer l'image pour la détection d'objets
                // Préparer l'image pour la détection d'objets
                Mat blob = Dnn.blobFromImage(frame, 1.0/255.0,
                        new Size(416, 416), new Scalar(0, 0, 0), true, false);

                // Passer l'image au réseau de neurones
                objectDetector.setInput(blob);

                // Obtenir les couches de sortie
                List<String> outLayerNames = getOutputLayerNames(objectDetector);
                List<Mat> result = new ArrayList<>();
                objectDetector.forward(result, outLayerNames);

                // Dimensions de l'image originale
                int frameHeight = frame.height();
                int frameWidth = frame.width();

                // Analyser les détections
                for (Mat level : result) {
                    for (int i = 0; i < level.rows(); ++i) {
                        Mat row = level.row(i);
                        Mat scores = row.colRange(5, level.cols());
                        Core.MinMaxLocResult mm = Core.minMaxLoc(scores);
                        int classId = (int) mm.maxLoc.x;
                        double confidence = mm.maxVal;

                        if (confidence > confidenceThreshold) {
                            // Obtenir les coordonnées de la boîte englobante
                            int centerX = (int) (row.get(0, 0)[0] * frameWidth);
                            int centerY = (int) (row.get(0, 1)[0] * frameHeight);
                            int width = (int) (row.get(0, 2)[0] * frameWidth);
                            int height = (int) (row.get(0, 3)[0] * frameHeight);
                            int x = centerX - width / 2;
                            int y = centerY - height / 2;

                            // Créer un rectangle pour l'objet
                            Rect objectRect = new Rect(x, y, width, height);

                            // Obtenir le type d'objet
                            String objectType = COCO_NAMES[classId];

                            // Calculer la distance estimée
                            double distance = calculateDistance(objectType, objectRect.width);

                            // Dessiner le rectangle de l'objet avec une couleur basée sur le type
                            Scalar color = getClassColor(classId);
                            Imgproc.rectangle(displayFrame, objectRect, color, 2);

                            // Ajouter le résultat à la liste
                            DetectionResult result2 = new DetectionResult(objectType, objectRect, distance, confidence);
                            results.add(result2);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("Erreur dans la détection d'objets: " + e.getMessage());
                e.printStackTrace();
            }

            return results;
        }

        private List<String> getOutputLayerNames(Net net) {
            List<String> names = new ArrayList<>();

            // Obtenir les indices des couches de sortie
            MatOfInt outLayers = net.getUnconnectedOutLayers();

            // Obtenir les noms de toutes les couches
            List<String> layersNames = net.getLayerNames();

            // Sélectionner les noms des couches de sortie
            for (int i = 0; i < outLayers.total(); ++i) {
                names.add(layersNames.get((int) outLayers.get(i, 0)[0] - 1));
            }

            return names;
        }

        private Scalar getClassColor(int classId) {
            // Générer une couleur unique basée sur l'ID de classe
            Random random = new Random(classId * 100);
            int r = random.nextInt(256);
            int g = random.nextInt(256);
            int b = random.nextInt(256);
            return new Scalar(r, g, b);
        }

        private double calculateDistance(String objectType, int pixelWidth) {
            // Obtenir la taille réelle de l'objet en cm
            double realSize = OBJECT_SIZES.getOrDefault(objectType, 30.0); // Taille par défaut: 30cm

            // Calculer la distance en utilisant la formule: distance = (taille réelle * focale) / taille en pixels
            return (realSize * FOCAL_LENGTH) / pixelWidth;
        }

        private void updateObjectTracking(List<DetectionResult> detections) {
            // Mettre à jour le timestamp actuel
            long currentTime = System.currentTimeMillis();

            // Marquer tous les objets suivis comme non vus
            Set<String> seenObjectKeys = new HashSet<>();

            // Mettre à jour les objets détectés
            for (DetectionResult detection : detections) {
                String key = detection.type + "_" + detection.bounds.x + "_" + detection.bounds.y;
                seenObjectKeys.add(key);

                if (trackedObjects.containsKey(key)) {
                    // Mettre à jour l'objet existant
                    trackedObjects.get(key).update(detection.bounds, detection.distance, detection.confidence);
                } else {
                    // Ajouter un nouvel objet
                    trackedObjects.put(key, new ObjectTracker(
                            detection.type, detection.bounds, detection.distance, detection.confidence));
                }
            }

            // Supprimer les objets qui n'ont pas été vus depuis 2 secondes
            trackedObjects.entrySet().removeIf(entry ->
                    !seenObjectKeys.contains(entry.getKey()) &&
                            currentTime - entry.getValue().lastSeenTimestamp > 2000);
        }

        private void sendDetectionMessages(List<DetectionResult> detections) {
            long currentTime = System.currentTimeMillis();

            // Limiter la fréquence d'envoi des messages
            if (currentTime - lastMessageTime < MESSAGE_THRESHOLD_MS) {
                return;
            }

            lastMessageTime = currentTime;

            // Sélectionner les agents destinataires
            List<AID> recipients = new ArrayList<>();
            int selectedIndex = agentSelector.getSelectedIndex();

            if (selectedIndex == 0) {
                // "Tous les agents" sélectionné
                recipients.addAll(receiverAgents);
            } else if (selectedIndex > 0 && selectedIndex <= receiverAgents.size()) {
                // Agent spécifique sélectionné
                recipients.add(receiverAgents.get(selectedIndex - 1));
            }

            if (recipients.isEmpty()) {
                // Tenter de redécouvrir les agents si la liste est vide
                discoverReceiverAgents();
                return;
            }

            // Créer et envoyer un message JSON contenant toutes les détections
            try {
                // Construire un JSON avec toutes les détections
                StringBuilder jsonBuilder = new StringBuilder("{\"detections\":[");
                boolean first = true;

                for (DetectionResult detection : detections) {
                    if (!first) {
                        jsonBuilder.append(",");
                    }
                    jsonBuilder.append(String.format(
                            "{\"type\":\"%s\",\"distance\":%.2f,\"confidence\":%.2f,\"x\":%d,\"y\":%d,\"width\":%d,\"height\":%d}",
                            detection.type, detection.distance, detection.confidence,
                            detection.bounds.x, detection.bounds.y, detection.bounds.width, detection.bounds.height
                    ));
                    first = false;
                }

                jsonBuilder.append("],\"timestamp\":").append(System.currentTimeMillis()).append("}");

                // Créer et envoyer le message
                ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
                for (AID recipient : recipients) {
                    msg.addReceiver(recipient);
                }

                msg.setContent(jsonBuilder.toString());
                msg.setOntology("vision-detection");
                send(msg);

                // Journaliser l'envoi (uniquement occasionnellement)
                if (Math.random() < 0.1) {
                    int detectionCount = detections.size();
                    String recipientInfo = selectedIndex == 0 ?
                            "tous les agents (" + recipients.size() + ")" :
                            recipients.get(0).getLocalName();

                    logMessage("📤 Envoi de " + detectionCount + " détections à " + recipientInfo);
                }
            } catch (Exception e) {
                logMessage("⚠️ Erreur lors de l'envoi des messages: " + e.getMessage());
            }
        }

        private void addInfoOverlay(Mat frame, List<DetectionResult> detections) {
            // Ajouter un compteur d'objets en haut à gauche
            String countText = "Objets détectés: " + detections.size();
            Imgproc.putText(frame, countText, new Point(10, 25),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.7, new Scalar(0, 165, 255), 2);

            // Ajouter le mode actuel
            String modeText = "Mode: " + modeSelector.getSelectedItem();
            Imgproc.putText(frame, modeText, new Point(10, frame.rows() - 10),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(0, 165, 255), 1);

            // Ajouter un timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            Imgproc.putText(frame, timestamp, new Point(frame.cols() - 200, 25),
                    Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 1);

            // Pour chaque détection, ajouter des infos
            for (DetectionResult detection : detections) {
                // Dessiner le nom de l'objet et la distance
                String label = String.format("%s: %.1f cm (%.1f%%)",
                        detection.type, detection.distance, detection.confidence * 100);

                // Position du texte au-dessus du rectangle
                Point textPosition = new Point(detection.bounds.x, detection.bounds.y - 10);

                // Fond semi-transparent pour le texte
                Rect textRect = new Rect(
                        detection.bounds.x - 2,
                        detection.bounds.y - 30,
                        label.length() * 8,
                        25
                );

                // Dessiner le fond avec transparence
                Mat overlay = new Mat(frame.size(), frame.type(), new Scalar(0, 0, 0, 0.7));
                Imgproc.rectangle(overlay, textRect, new Scalar(40, 40, 40), -1);
                Core.addWeighted(overlay, 0.4, frame, 1.0, 0.0, frame);

                // Dessiner le texte
                Imgproc.putText(frame, label, textPosition,
                        Imgproc.FONT_HERSHEY_SIMPLEX, 0.5, new Scalar(255, 255, 255), 1);
            }
        }

        private void updateUI(Mat frame) {
            try {
                // Convertir Mat en BufferedImage pour l'affichage Swing
                BufferedImage image = matToBufferedImage(frame);

                // Mettre à jour l'interface utilisateur
                SwingUtilities.invokeLater(() -> {
                    if (image != null) {
                        cameraFeed.setIcon(new ImageIcon(image));

                        // Mettre à jour le statut
                        statusLabel.setText("Système actif - " + modeSelector.getSelectedItem());
                    }
                });
            } catch (Exception e) {
                System.err.println("Erreur lors de la mise à jour de l'interface: " + e.getMessage());
            }
        }

        private BufferedImage matToBufferedImage(Mat mat) {
            // Convertir l'image Mat en BufferedImage
            int type = BufferedImage.TYPE_BYTE_GRAY;
            if (mat.channels() > 1) {
                type = BufferedImage.TYPE_3BYTE_BGR;
            }

            int bufferSize = mat.channels() * mat.cols() * mat.rows();
            byte[] buffer = new byte[bufferSize];
            mat.get(0, 0, buffer);

            BufferedImage image = new BufferedImage(mat.cols(), mat.rows(), type);
            final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(buffer, 0, targetPixels, 0, buffer.length);

            return image;
        }
    }

    // Classe pour stocker les résultats de détection
    private static class DetectionResult {
        String type;
        Rect bounds;
        double distance;
        double confidence;

        public DetectionResult(String type, Rect bounds, double distance, double confidence) {
            this.type = type;
            this.bounds = bounds;
            this.distance = distance;
            this.confidence = confidence;
        }
    }

    @Override
    protected void takeDown() {
        System.out.println("Agent cognitif de vision terminé: " + getLocalName());

        // Arrêter la caméra
        if (camera != null && camera.isOpened()) {
            camera.release();
        }

        // Fermer l'interface utilisateur
        if (frame != null) {
            frame.dispose();
        }
    }
}