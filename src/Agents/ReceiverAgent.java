package Agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.AffineTransform;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ReceiverAgent extends Agent {

    // Constantes UI
    private static final String WINDOW_TITLE = "Agent Récepteur - Analyse d'Objets";
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final Color PRIMARY_COLOR = new Color(60, 141, 188);
    private static final Color SECONDARY_COLOR = new Color(245, 245, 245);
    private static final Color TEXT_COLOR = new Color(51, 51, 51);
    private static final Color ACCENT_COLOR = new Color(243, 156, 18);

    // Interface utilisateur
    private JFrame frame;
    private JPanel mainPanel;
    private JPanel objectPanel;
    private JPanel statsPanel;
    private JTextArea logArea;
    private JScrollPane logScrollPane;
    private JComboBox<String> objectFilterCombo;
    private JPanel objectCardsPanel;
    private Map<String, JPanel> objectCardMap = new HashMap<>();

    // Données
    private Map<String, DetectionData> detectionDataMap = new HashMap<>();
    private List<String> detectionLog = new ArrayList<>();
    private int totalDetections = 0;
    private Set<String> uniqueObjectTypes = new TreeSet<>();

    // Graphiques
    private SimpleBarChart distanceChart;
    private SimplePieChart objectTypeChart;

    @Override
    protected void setup() {
        System.out.println("ReceiverAgent démarré: " + getLocalName());

        // Initialiser l'interface utilisateur moderne
        SwingUtilities.invokeLater(this::createModernUI);
        registerInDF();

        // Ajouter un comportement pour recevoir les messages
        addBehaviour(new DetectionMessageReceiver());
    }

    private void registerInDF() {
        try {
            DFAgentDescription dfd = new DFAgentDescription();
            dfd.setName(getAID());

            ServiceDescription sd = new ServiceDescription();
            sd.setType("detection-receiver");
            sd.setName("detection-service");
            dfd.addServices(sd);

            DFService.register(this, dfd);
            System.out.println("Agent " + getLocalName() + " enregistré dans le DF comme 'detection-receiver'");
        } catch (FIPAException e) {
            System.err.println("Erreur lors de l'enregistrement au DF: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createModernUI() {
        try {
            // Utiliser un look and feel moderne
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                System.out.print("Look and feel dispo : "+info.getName());
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }

            // Personnaliser le Nimbus look and feel
            UIManager.put("nimbusBase", PRIMARY_COLOR);
            UIManager.put("nimbusBlueGrey", SECONDARY_COLOR);
            UIManager.put("control", new Color(250, 250, 250));
        } catch (Exception e) {
            System.err.println("Impossible de définir le look and feel: " + e.getMessage());
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ex) {
                // Ignorer
            }
        }

        // Créer la fenêtre principale
        frame = new JFrame(WINDOW_TITLE);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                doDelete(); // Terminer proprement l'agent
            }
        });

        // Panel principal avec marge
        mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(SECONDARY_COLOR);

        // Créer le panneau du haut avec filtre
        JPanel topPanel = createTopPanel();

        // Créer le panneau d'objets
        objectPanel = createObjectPanel();

        // Créer le panneau de statistiques
        statsPanel = createStatsPanel();

        // Créer le panneau de journal
        JPanel logPanel = createLogPanel();

        // Organiser les panneaux avec un layout plus moderne
        JSplitPane centerSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                objectPanel, statsPanel);
        centerSplitPane.setResizeWeight(0.5);
        centerSplitPane.setDividerLocation(400);
        centerSplitPane.setBorder(null);
        centerSplitPane.setDividerSize(5);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(centerSplitPane, BorderLayout.CENTER);

        // Ajouter les panneaux à la fenêtre
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setLocationRelativeTo(null); // Centrer sur l'écran
        frame.setVisible(true);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(15, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        // Label du titre avec un style plus moderne
        JLabel titleLabel = new JLabel("Système de Détection d'Objets - Monitoring");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(PRIMARY_COLOR);
        panel.add(titleLabel, BorderLayout.WEST);

        // Compteur de détections
        JPanel statsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        statsPanel.setOpaque(false);

        JLabel detectionCountLabel = new JLabel("Total détections: 0");
        detectionCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        detectionCountLabel.setForeground(ACCENT_COLOR);

        // Panel pour le filtre avec un look moderne
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        filterPanel.setOpaque(false);

        JLabel filterLabel = new JLabel("Filtrer par type:");
        filterLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        filterLabel.setForeground(TEXT_COLOR);

        objectFilterCombo = new JComboBox<>(new String[]{"Tous les objets"});
        objectFilterCombo.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        objectFilterCombo.setBackground(Color.WHITE);
        objectFilterCombo.addActionListener(e -> filterObjects());

        filterPanel.add(filterLabel);
        filterPanel.add(Box.createHorizontalStrut(10));
        filterPanel.add(objectFilterCombo);

        statsPanel.add(detectionCountLabel);
        statsPanel.add(Box.createHorizontalStrut(20));
        statsPanel.add(filterPanel);

        panel.add(statsPanel, BorderLayout.EAST);

        // Ajouter une ligne de séparation
        JSeparator separator = new JSeparator();
        separator.setForeground(new Color(220, 220, 220));
        panel.add(separator, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createObjectPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                ),
                "Objets Détectés",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                PRIMARY_COLOR
        ));
        panel.setBackground(Color.WHITE);

        // Panneau défilant pour les cartes d'objets avec un layout en grille
        objectCardsPanel = new JPanel();
        objectCardsPanel.setLayout(new WrapLayout(FlowLayout.LEFT, 10, 10));
        objectCardsPanel.setBackground(Color.WHITE);

        JScrollPane scrollPane = new JScrollPane(objectCardsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        scrollPane.setBackground(Color.WHITE);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 0, 15));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(220, 220, 220), 1),
                        BorderFactory.createEmptyBorder(10, 10, 10, 10)
                ),
                "Statistiques & Analyses",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                PRIMARY_COLOR
        ));
        panel.setBackground(Color.WHITE);

        // Panel pour le graphique des distances avec un titre moderne
        JPanel distanceChartPanel = new JPanel(new BorderLayout(0, 5));
        distanceChartPanel.setBackground(Color.WHITE);
        distanceChartPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(240, 240, 240)),
                BorderFactory.createEmptyBorder(5, 5, 10, 5)
        ));

        JLabel distanceChartTitle = new JLabel("Distances des objets (cm)");
        distanceChartTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        distanceChartTitle.setForeground(PRIMARY_COLOR);
        distanceChartPanel.add(distanceChartTitle, BorderLayout.NORTH);

        distanceChart = new SimpleBarChart();
        distanceChartPanel.add(distanceChart, BorderLayout.CENTER);

        // Panel pour le graphique des types d'objets
        JPanel typeChartPanel = new JPanel(new BorderLayout(0, 5));
        typeChartPanel.setBackground(Color.WHITE);
        typeChartPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel typeChartTitle = new JLabel("Répartition des types d'objets");
        typeChartTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        typeChartTitle.setForeground(PRIMARY_COLOR);
        typeChartPanel.add(typeChartTitle, BorderLayout.NORTH);

        objectTypeChart = new SimplePieChart();
        typeChartPanel.add(objectTypeChart, BorderLayout.CENTER);

        panel.add(distanceChartPanel);
        panel.add(typeChartPanel);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(15, 0, 0, 0)
        ));
        panel.setOpaque(false);

        // Titre et boutons en haut
        JPanel topLogPanel = new JPanel(new BorderLayout());
        topLogPanel.setOpaque(false);

        JLabel logLabel = new JLabel("Journal des Détections");
        logLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logLabel.setForeground(PRIMARY_COLOR);

        // Boutons avec un style moderne
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonsPanel.setOpaque(false);

        JButton clearButton = createStyledButton("Effacer le journal", new Color(217, 83, 79));
        clearButton.addActionListener(e -> {
            logArea.setText("");
            detectionLog.clear();
        });

        JButton exportButton = createStyledButton("Exporter le journal", new Color(92, 184, 92));
        exportButton.addActionListener(e -> {
            // Fonctionnalité d'exportation à implémenter
            JOptionPane.showMessageDialog(frame, "Fonctionnalité d'exportation à implémenter",
                    "Information", JOptionPane.INFORMATION_MESSAGE);
        });

        buttonsPanel.add(exportButton);
        buttonsPanel.add(clearButton);

        topLogPanel.add(logLabel, BorderLayout.WEST);
        topLogPanel.add(buttonsPanel, BorderLayout.EAST);

        // Zone de texte pour le journal avec un style moderne
        logArea = new JTextArea(6, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(250, 250, 250));
        logArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        logArea.setForeground(TEXT_COLOR);

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220), 1));

        panel.add(topLogPanel, BorderLayout.NORTH);
        panel.add(logScrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JButton createStyledButton(String text, Color bgColor) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setBackground(bgColor);
        button.setForeground(Color.WHITE);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createEmptyBorder(8, 15, 8, 15));

        // Effet de survol
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor.darker());
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(bgColor);
            }
        });

        return button;
    }

    private void updateObjectCards() {
        SwingUtilities.invokeLater(() -> {
            // Vider le panneau de cartes
            objectCardsPanel.removeAll();

            // Obtenir le filtre actuel
            String filter = (String) objectFilterCombo.getSelectedItem();
            if (filter == null) filter = "Tous les objets";

            // Ajouter une carte pour chaque détection
            for (Map.Entry<String, DetectionData> entry : detectionDataMap.entrySet()) {
                DetectionData data = entry.getValue();

                // Appliquer le filtre
                if (!filter.equals("Tous les objets") && !data.type.equals(filter)) {
                    continue;
                }

                // Créer ou mettre à jour la carte d'objet
                JPanel card = objectCardMap.computeIfAbsent(entry.getKey(), k -> createObjectCard(data));
                updateObjectCard(card, data);
                objectCardsPanel.add(card);
            }

            // Mettre à jour l'interface
            objectCardsPanel.revalidate();
            objectCardsPanel.repaint();
        });
    }

    private JPanel createObjectCard(DetectionData data) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setPreferredSize(new Dimension(180, 160));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        // Icon panel - use a circular background with a more modern look
        JPanel iconPanel = new JPanel(new BorderLayout());
        iconPanel.setOpaque(false);
        iconPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel iconCircle = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Calculate the circle dimensions
                int diameter = Math.min(getWidth(), getHeight()) - 2;
                int x = (getWidth() - diameter) / 2;
                int y = (getHeight() - diameter) / 2;

                // Draw the circle with the object's color
                Color color = getColorForObjectType(data.type);
                g2d.setColor(color);
                g2d.fillOval(x, y, diameter, diameter);

                // Draw the object type's first character
                g2d.setColor(Color.WHITE);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 18));
                String initial = data.type.substring(0, 1).toUpperCase();
                FontMetrics fm = g2d.getFontMetrics();
                int textX = (getWidth() - fm.stringWidth(initial)) / 2;
                int textY = ((getHeight() - fm.getHeight()) / 2) + fm.getAscent();
                g2d.drawString(initial, textX, textY);
            }
        };
        iconCircle.setPreferredSize(new Dimension(48, 48));
        iconCircle.setOpaque(false);

        iconPanel.add(iconCircle, BorderLayout.CENTER);

        // Informations sur l'objet au centre avec un style moderne
        JPanel infoPanel = new JPanel(new GridLayout(3, 1, 0, 4));
        infoPanel.setOpaque(false);

        JLabel typeLabel = new JLabel(data.type);
        typeLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        typeLabel.setForeground(TEXT_COLOR);

        JLabel distanceLabel = new JLabel("Distance: " + String.format("%.1f", data.distance) + " cm");
        distanceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        distanceLabel.setForeground(TEXT_COLOR);

        // Création d'une barre de confiance moderne
        JPanel confidencePanel = new JPanel(new BorderLayout(5, 0));
        confidencePanel.setOpaque(false);

        JLabel confidenceLabel = new JLabel("Confiance: ");
        confidenceLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        confidenceLabel.setForeground(TEXT_COLOR);

        JProgressBar confidenceBar = new JProgressBar(0, 100);
        confidenceBar.setValue((int)(data.confidence * 100));
        confidenceBar.setStringPainted(true);
        confidenceBar.setString(String.format("%.0f%%", data.confidence * 100));
        confidenceBar.setForeground(getConfidenceColor(data.confidence));
        confidenceBar.setBackground(new Color(240, 240, 240));

        confidencePanel.add(confidenceLabel, BorderLayout.WEST);
        confidencePanel.add(confidenceBar, BorderLayout.CENTER);

        infoPanel.add(typeLabel);
        infoPanel.add(distanceLabel);
        infoPanel.add(confidencePanel);

        // Timestamp en bas avec un style moderne
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        JLabel timeLabel = new JLabel(sdf.format(data.timestamp));
        timeLabel.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        timeLabel.setForeground(Color.GRAY);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        card.add(iconPanel, BorderLayout.NORTH);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(timeLabel, BorderLayout.SOUTH);

        // Ajouter un effet de survol
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                card.setBackground(new Color(245, 245, 250));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(PRIMARY_COLOR, 1),
                        BorderFactory.createEmptyBorder(8, 8, 8, 8)
                ));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                card.setBackground(Color.WHITE);
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(230, 230, 230), 1),
                        BorderFactory.createEmptyBorder(8, 8, 8, 8)
                ));
            }
        });

        return card;
    }

    private Color getConfidenceColor(double confidence) {
        if (confidence >= 0.8) {
            return new Color(92, 184, 92); // Green
        } else if (confidence >= 0.5) {
            return new Color(240, 173, 78); // Yellow
        } else {
            return new Color(217, 83, 79); // Red
        }
    }

    private void updateObjectCard(JPanel card, DetectionData data) {
        // Mise à jour des informations de la carte
        try {
            if (card.getComponentCount() >= 3) {
                // Mettre à jour le panneau d'informations
                JPanel infoPanel = (JPanel) card.getComponent(1);
                if (infoPanel.getComponentCount() >= 3) {
                    JLabel typeLabel = (JLabel) infoPanel.getComponent(0);
                    JLabel distanceLabel = (JLabel) infoPanel.getComponent(1);
                    JPanel confidencePanel = (JPanel) infoPanel.getComponent(2);

                    if (confidencePanel.getComponentCount() >= 2) {
                        JProgressBar confidenceBar = (JProgressBar) confidencePanel.getComponent(1);

                        typeLabel.setText(data.type);
                        distanceLabel.setText("Distance: " + String.format("%.1f", data.distance) + " cm");
                        confidenceBar.setValue((int)(data.confidence * 100));
                        confidenceBar.setString(String.format("%.0f%%", data.confidence * 100));
                        confidenceBar.setForeground(getConfidenceColor(data.confidence));
                    }
                }

                // Mettre à jour le timestamp
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                JLabel timeLabel = (JLabel) card.getComponent(2);
                timeLabel.setText(sdf.format(data.timestamp));
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la mise à jour de la carte: " + e.getMessage());
        }
    }

    private void filterObjects() {
        updateObjectCards();
    }

    private void updateObjectTypes() {
        SwingUtilities.invokeLater(() -> {
            String currentSelection = (String) objectFilterCombo.getSelectedItem();

            objectFilterCombo.removeAllItems();
            objectFilterCombo.addItem("Tous les objets");

            for (String type : uniqueObjectTypes) {
                objectFilterCombo.addItem(type);
            }

            // Restaurer la sélection précédente si possible
            if (currentSelection != null) {
                objectFilterCombo.setSelectedItem(currentSelection);
            }
        });
    }

    private void updateStatistics() {
        SwingUtilities.invokeLater(() -> {
            // Mise à jour du compteur de détections
            Component topPanel = mainPanel.getComponent(0);
            if (topPanel instanceof JPanel) {
                Component statsPanel = ((JPanel) topPanel).getComponent(1);
                if (statsPanel instanceof JPanel) {
                    Component countLabel = ((JPanel) statsPanel).getComponent(0);
                    if (countLabel instanceof JLabel) {
                        ((JLabel) countLabel).setText("Total détections: " + totalDetections);
                    }
                }
            }

            // Mettre à jour le graphique des distances
            Map<String, Double> distanceData = new HashMap<>();
            for (DetectionData data : detectionDataMap.values()) {
                distanceData.put(data.type, data.distance);
            }
            distanceChart.updateData(distanceData);

            // Mettre à jour le graphique des types d'objets
            Map<String, Integer> typeCountMap = new HashMap<>();
            for (DetectionData data : detectionDataMap.values()) {
                typeCountMap.put(data.type, typeCountMap.getOrDefault(data.type, 0) + 1);
            }
            objectTypeChart.updateData(typeCountMap);
        });
    }

    private Color getColorForObjectType(String type) {
        // Générer une couleur cohérente basée sur le type d'objet
        int hash = type.hashCode();
        Random random = new Random(hash);

        return new Color(
                100 + random.nextInt(100),  // R: 100-200
                100 + random.nextInt(100),  // G: 100-200
                100 + random.nextInt(100)   // B: 100-200
        );
    }

    // Classe pour stocker les données de détection
    private static class DetectionData {
        String type;
        double distance;
        double confidence;
        Date timestamp;

        public DetectionData(String type, double distance, double confidence) {
            this.type = type;
            this.distance = distance;
            this.confidence = confidence;
            this.timestamp = new Date();
        }
    }

    // Comportement pour recevoir les messages de détection
    // Comportement pour recevoir les messages de détection
    private class DetectionMessageReceiver extends CyclicBehaviour {
        @Override
        public void action() {
            // Recevoir les messages
            ACLMessage msg = myAgent.receive();
            if (msg != null) {
                try {
                    // Traiter le message si c'est une détection
                    if (msg.getPerformative() == ACLMessage.INFORM) {
                        String content = msg.getContent();
                        processDetectionMessage(content, msg.getSender().getLocalName());
                    }
                } catch (Exception e) {
                    System.err.println("Erreur lors du traitement du message: " + e.getMessage());
                    e.printStackTrace();
                }
            } else {
                block(); // Bloquer jusqu'à réception d'un nouveau message
            }
        }
    }

    private void processDetectionMessage(String content, String sender) {
        try {
            // Format reçu: DETECTION:type:distance:confidence
            System.out.println("Message reçu: " + content);

            // Diviser sur les deux-points au lieu du point-virgule
            String[] parts = content.split(":");

            if (parts.length >= 4 && parts[0].equals("DETECTION")) {
                String objectType = parts[1].trim();

                // Remplacer la virgule par un point pour les nombres décimaux
                double distance = Double.parseDouble(parts[2].trim().replace(',', '.'));
                double confidence = Double.parseDouble(parts[3].trim().replace(',', '.'));

                // Créer un ID unique pour cette détection
                String objectId = objectType + "-" + Math.abs(content.hashCode() % 1000);

                // Stocker ou mettre à jour les données de détection
                DetectionData data = new DetectionData(objectType, distance, confidence);
                detectionDataMap.put(objectId, data);

                // Ajouter le type d'objet à l'ensemble des types uniques
                uniqueObjectTypes.add(objectType);

                // Mettre à jour le compteur de détections
                totalDetections++;

                // Ajouter au journal
                SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
                String logEntry = String.format("[%s] %s a détecté %s à %.1f cm (confiance: %.1f%%)",
                        sdf.format(new Date()), sender, objectType, distance, confidence * 100);
                detectionLog.add(logEntry);

                // Mettre à jour l'interface utilisateur
                updateUI(logEntry);

                // Débogage
                System.out.println("Log ajouté: " + logEntry);
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du message de détection: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void updateUI(String logEntry) {
        SwingUtilities.invokeLater(() -> {
            // Mettre à jour les cartes d'objets
            updateObjectCards();

            // Mettre à jour les types d'objets dans le filtre
            updateObjectTypes();

            // Mettre à jour les statistiques
            updateStatistics();

            // Ajouter l'entrée au journal
            logArea.append(logEntry + "\n");

            // Défiler automatiquement vers le bas
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    // Classes internes pour les graphiques

    // Classe pour le FlowLayout avec retour à la ligne
    public static class WrapLayout extends FlowLayout {
        private Dimension preferredLayoutSize;

        public WrapLayout() {
            super();
        }

        public WrapLayout(int align) {
            super(align);
        }

        public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();

                if (targetWidth == 0)
                    targetWidth = Integer.MAX_VALUE;

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);

                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                        if (rowWidth + d.width > maxWidth) {
                            dim.width = Math.max(dim.width, rowWidth);
                            dim.height += rowHeight + vgap;
                            rowWidth = 0;
                            rowHeight = 0;
                        }

                        if (rowWidth != 0) {
                            rowWidth += hgap;
                        }

                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }

                dim.width = Math.max(dim.width, rowWidth);
                dim.height += rowHeight + insets.top + insets.bottom;

                preferredLayoutSize = dim;
                return new Dimension(dim);
            }
        }
    }

    // Graphique à barres simple
    private class SimpleBarChart extends JPanel {
        private Map<String, Double> data = new HashMap<>();
        private final Color[] COLORS = {
                new Color(70, 130, 180),   // Bleu acier
                new Color(255, 99, 71),    // Rouge tomate
                new Color(50, 205, 50),    // Vert lime
                new Color(255, 165, 0),    // Orange
                new Color(128, 0, 128),    // Violet
                new Color(30, 144, 255),   // Bleu dodger
                new Color(255, 215, 0),    // Or
                new Color(220, 20, 60)     // Rouge cramoisi
        };

        public SimpleBarChart() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        public void updateData(Map<String, Double> newData) {
            this.data = new HashMap<>(newData);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth() - 40;
            int height = getHeight() - 60;
            int bottom = getHeight() - 30;
            int maxValue = 0;

            // Trouver la valeur maximale
            for (Double value : data.values()) {
                maxValue = Math.max(maxValue, (int)Math.ceil(value));
            }

            // Ajuster si maxValue est trop petit
            maxValue = Math.max(maxValue, 100);

            // Dessiner l'axe Y
            g2d.setColor(new Color(220, 220, 220));
            g2d.drawLine(30, 20, 30, bottom);

            // Dessiner l'axe X
            g2d.drawLine(30, bottom, width + 30, bottom);

            // Dessiner les graduations et les valeurs sur l'axe Y
            g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            FontMetrics fm = g2d.getFontMetrics();

            for (int i = 0; i <= 5; i++) {
                int value = maxValue * i / 5;
                int y = bottom - (height * i / 5);

                g2d.setColor(new Color(200, 200, 200));
                g2d.drawLine(28, y, width + 30, y);

                g2d.setColor(new Color(100, 100, 100));
                String valueStr = String.valueOf(value);
                int valueWidth = fm.stringWidth(valueStr);
                g2d.drawString(valueStr, 25 - valueWidth, y + 4);
            }

            // S'il n'y a pas de données, afficher un message
            if (data.isEmpty()) {
                g2d.setColor(new Color(150, 150, 150));
                g2d.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                String noData = "Aucune donnée disponible";
                int noDataWidth = g2d.getFontMetrics().stringWidth(noData);
                g2d.drawString(noData, (getWidth() - noDataWidth) / 2, getHeight() / 2);
                return;
            }

            // Dessiner les barres
            int barWidth = Math.min(50, (width - 40) / Math.max(1, data.size()));
            int gap = 10;
            int x = 40;

            int i = 0;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                String key = entry.getKey();
                double value = entry.getValue();

                // Dessiner la barre avec un dégradé
                int barHeight = (int)((value / maxValue) * height);
                int y = bottom - barHeight;

                Color barColor = COLORS[i % COLORS.length];
                GradientPaint gradient = new GradientPaint(
                        x, y, barColor,
                        x + barWidth, y, barColor.brighter());
                g2d.setPaint(gradient);
                g2d.fillRoundRect(x, y, barWidth, barHeight, 6, 6);

                // Bordure de la barre
                g2d.setColor(barColor.darker());
                g2d.drawRoundRect(x, y, barWidth, barHeight, 6, 6);

                // Dessiner la valeur au-dessus de la barre
                g2d.setColor(TEXT_COLOR);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 11));
                fm = g2d.getFontMetrics();
                String valueStr = String.format("%.1f", value);
                int valueWidth = fm.stringWidth(valueStr);
                g2d.drawString(valueStr, x + (barWidth - valueWidth) / 2, y - 5);

                // Dessiner le label sous la barre
                g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                fm = g2d.getFontMetrics();
                String shortKey = key.length() > 6 ? key.substring(0, 6) + "..." : key;
                int labelWidth = fm.stringWidth(shortKey);

                // Rotation du texte pour les étiquettes longues
                AffineTransform old = g2d.getTransform();
                g2d.rotate(-Math.PI / 4, x + barWidth / 2, bottom + 5);
                g2d.drawString(shortKey, x + barWidth / 2 - labelWidth / 2, bottom + 15);
                g2d.setTransform(old);

                x += barWidth + gap;
                i++;
            }
        }
    }

    // Graphique circulaire simple
    private class SimplePieChart extends JPanel {
        private Map<String, Integer> data = new HashMap<>();
        private final Color[] COLORS = {
                new Color(70, 130, 180),   // Bleu acier
                new Color(255, 99, 71),    // Rouge tomate
                new Color(50, 205, 50),    // Vert lime
                new Color(255, 165, 0),    // Orange
                new Color(128, 0, 128),    // Violet
                new Color(30, 144, 255),   // Bleu dodger
                new Color(255, 215, 0),    // Or
                new Color(220, 20, 60)     // Rouge cramoisi
        };

        public SimplePieChart() {
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }

        public void updateData(Map<String, Integer> newData) {
            this.data = new HashMap<>(newData);
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();

            // Calculer le total
            int total = 0;
            for (int value : data.values()) {
                total += value;
            }

            if (total > 0) {
                // Calculer le centre et le rayon
                int centerX = width / 2;
                int centerY = height / 2;
                int radius = Math.min(width, height) / 3;

                // Dessiner les sections du camembert
                int startAngle = 0;
                int i = 0;

                // Créer une liste triée par valeur descendante
                List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(data.entrySet());
                sortedEntries.sort((e1, e2) -> e2.getValue().compareTo(e1.getValue()));

                for (Map.Entry<String, Integer> entry : sortedEntries) {
                    String key = entry.getKey();
                    int value = entry.getValue();

                    // Calculer l'angle de la section
                    int arcAngle = (int) Math.round(360.0 * value / total);

                    // Dessiner la section avec un dégradé radial
                    Color color = COLORS[i % COLORS.length];
                    RadialGradientPaint gradient = new RadialGradientPaint(
                            centerX, centerY, radius,
                            new float[]{0.0f, 1.0f},
                            new Color[]{color.brighter(), color}
                    );
                    g2d.setPaint(gradient);
                    g2d.fillArc(centerX - radius, centerY - radius, 2 * radius, 2 * radius, startAngle, arcAngle);

                    // Bordure de la section
                    g2d.setColor(color.darker());
                    g2d.drawArc(centerX - radius, centerY - radius, 2 * radius, 2 * radius, startAngle, arcAngle);

                    // Calculer la position pour le label (à mi-chemin de la section)
                    double middleAngle = Math.toRadians(startAngle + arcAngle / 2);
                    int labelRadius = radius + 20;
                    int labelX = (int) (centerX + labelRadius * Math.cos(middleAngle));
                    int labelY = (int) (centerY + labelRadius * Math.sin(middleAngle));

                    // Dessiner la ligne de connexion
                    g2d.setColor(new Color(200, 200, 200));
                    int innerX = (int) (centerX + radius * 0.8 * Math.cos(middleAngle));
                    int innerY = (int) (centerY + radius * 0.8 * Math.sin(middleAngle));
                    g2d.drawLine(innerX, innerY, labelX, labelY);

                    // Dessiner le label
                    g2d.setFont(new Font("Segoe UI", Font.BOLD, 11));
                    g2d.setColor(TEXT_COLOR);
                    String label = key;
                    FontMetrics fm = g2d.getFontMetrics();
                    int labelWidth = fm.stringWidth(label);

                    // Ajuster la position du texte selon le quadrant
                    if (labelX < centerX) {
                        labelX -= labelWidth + 5;
                    } else {
                        labelX += 5;
                    }

                    g2d.drawString(label, labelX, labelY);

                    // Dessiner le pourcentage
                    g2d.setFont(new Font("Segoe UI", Font.PLAIN, 10));
                    String percent = String.format("%.1f%%", (100.0 * value / total));

                    if (labelX < centerX) {
                        labelX -= 0; // Déjà ajusté pour le label
                    } else {
                        labelX = labelX;
                    }

                    g2d.drawString(percent, labelX, labelY + 15);

                    startAngle += arcAngle;
                    i++;
                }

                // Dessiner un cercle au centre pour un effet 3D
                g2d.setColor(Color.WHITE);
                g2d.fillOval(centerX - radius/3, centerY - radius/3, 2*radius/3, 2*radius/3);
                g2d.setColor(new Color(240, 240, 240));
                g2d.drawOval(centerX - radius/3, centerY - radius/3, 2*radius/3, 2*radius/3);

                // Afficher le total au centre
                g2d.setColor(TEXT_COLOR);
                g2d.setFont(new Font("Segoe UI", Font.BOLD, 14));
                String totalStr = String.valueOf(total);
                FontMetrics fm = g2d.getFontMetrics();
                int totalWidth = fm.stringWidth(totalStr);
                g2d.drawString(totalStr, centerX - totalWidth / 2, centerY + fm.getAscent() / 2);
            } else {
                // Aucune donnée
                g2d.setColor(new Color(150, 150, 150));
                g2d.setFont(new Font("Segoe UI", Font.ITALIC, 12));
                String noData = "Aucune donnée disponible";
                FontMetrics fm = g2d.getFontMetrics();
                int noDataWidth = fm.stringWidth(noData);
                g2d.drawString(noData, (width - noDataWidth) / 2, height / 2);
            }
        }
    }

    @Override
    protected void takeDown() {
        // Désenregistrement du DF
        try {
            DFService.deregister(this);
        } catch (FIPAException e) {
            e.printStackTrace();
        }

        System.out.println("ReceiverAgent terminé: " + getLocalName());
        if (frame != null) {
            frame.dispose();
        }
    }
}