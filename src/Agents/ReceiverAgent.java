package Agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;

public class ReceiverAgent extends Agent {

    // Constantes UI
    private static final String WINDOW_TITLE = "Agent Récepteur - Analyse d'Objets";
    private static final int WINDOW_WIDTH = 600;
    private static final int WINDOW_HEIGHT = 500;

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

        // Ajouter un comportement pour recevoir les messages
        addBehaviour(new DetectionMessageReceiver());
    }

    private void createModernUI() {
        try {
            // Utiliser le look and feel du système
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("Impossible de définir le look and feel: " + e.getMessage());
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
        mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(new EmptyBorder(15, 15, 15, 15));
        mainPanel.setBackground(new Color(240, 240, 245));

        // Créer le panneau du haut avec filtre
        JPanel topPanel = createTopPanel();

        // Créer le panneau d'objets
        objectPanel = createObjectPanel();

        // Créer le panneau de statistiques
        statsPanel = createStatsPanel();

        // Créer le panneau de journal
        JPanel logPanel = createLogPanel();

        // Organiser les panneaux
        JSplitPane mainSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                objectPanel, statsPanel);
        mainSplitPane.setResizeWeight(0.5);
        mainSplitPane.setDividerLocation(220);

        // Ajouter les panneaux à la fenêtre
        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(mainSplitPane, BorderLayout.CENTER);
        mainPanel.add(logPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setSize(WINDOW_WIDTH, WINDOW_HEIGHT);
        frame.setLocationRelativeTo(null); // Centrer sur l'écran
        frame.setVisible(true);
    }

    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 0));
        panel.setOpaque(false);

        // Label du titre
        JLabel titleLabel = new JLabel("Réception et Analyse des Détections");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setForeground(new Color(50, 50, 80));
        panel.add(titleLabel, BorderLayout.WEST);

        // Panel pour le filtre
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        filterPanel.setOpaque(false);

        JLabel filterLabel = new JLabel("Filtrer par type:");
        filterLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        objectFilterCombo = new JComboBox<>(new String[]{"Tous les objets"});
        objectFilterCombo.setFont(new Font("Arial", Font.PLAIN, 12));
        objectFilterCombo.addActionListener(e -> filterObjects());

        filterPanel.add(filterLabel);
        filterPanel.add(objectFilterCombo);

        panel.add(filterPanel, BorderLayout.EAST);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        return panel;
    }

    private JPanel createObjectPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "Objets Détectés",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12),
                new Color(60, 60, 80)
        ));
        panel.setBackground(new Color(250, 250, 252));

        // Panneau défilant pour les cartes d'objets
        objectCardsPanel = new JPanel();
        objectCardsPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        objectCardsPanel.setBackground(new Color(250, 250, 252));

        JScrollPane scrollPane = new JScrollPane(objectCardsPanel);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200), 1),
                "Statistiques",
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Arial", Font.BOLD, 12),
                new Color(60, 60, 80)
        ));
        panel.setBackground(new Color(250, 250, 252));

        // Panel pour le graphique des distances
        JPanel distanceChartPanel = new JPanel(new BorderLayout());
        distanceChartPanel.setBackground(Color.WHITE);
        distanceChartPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        distanceChart = new SimpleBarChart("Distances des objets (cm)");
        distanceChartPanel.add(distanceChart, BorderLayout.CENTER);

        // Panel pour le graphique des types d'objets
        JPanel typeChartPanel = new JPanel(new BorderLayout());
        typeChartPanel.setBackground(Color.WHITE);
        typeChartPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        objectTypeChart = new SimplePieChart("Répartition des types d'objets");
        typeChartPanel.add(objectTypeChart, BorderLayout.CENTER);

        panel.add(distanceChartPanel);
        panel.add(typeChartPanel);

        return panel;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        panel.setOpaque(false);

        // Label du titre
        JLabel logLabel = new JLabel("Journal des Détections");
        logLabel.setFont(new Font("Arial", Font.BOLD, 12));
        logLabel.setForeground(new Color(60, 60, 80));
        panel.add(logLabel, BorderLayout.NORTH);

        // Zone de texte pour le journal
        logArea = new JTextArea(5, 40);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(new Color(245, 245, 245));

        logScrollPane = new JScrollPane(logArea);
        logScrollPane.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200), 1));

        panel.add(logScrollPane, BorderLayout.CENTER);

        // Bouton pour effacer le journal
        JButton clearButton = new JButton("Effacer le journal");
        clearButton.setFont(new Font("Arial", Font.PLAIN, 12));
        clearButton.addActionListener(e -> {
            logArea.setText("");
            detectionLog.clear();
        });

        panel.add(clearButton, BorderLayout.EAST);

        return panel;
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
        card.setPreferredSize(new Dimension(170, 130));
        card.setBackground(new Color(240, 240, 250));
        card.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 220), 1));

        // Icône de l'objet en haut
        JLabel iconLabel = new JLabel(getIconForObjectType(data.type));
        iconLabel.setHorizontalAlignment(SwingConstants.CENTER);
        iconLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));

        // Informations sur l'objet au centre
        JPanel infoPanel = new JPanel(new GridLayout(3, 1));
        infoPanel.setOpaque(false);
        infoPanel.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));

        JLabel typeLabel = new JLabel(data.type);
        typeLabel.setFont(new Font("Arial", Font.BOLD, 13));
        typeLabel.setForeground(new Color(50, 50, 100));

        JLabel distanceLabel = new JLabel("Distance: " + String.format("%.1f", data.distance) + " cm");
        distanceLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        JLabel confidenceLabel = new JLabel("Confiance: " + String.format("%.1f", data.confidence * 100) + "%");
        confidenceLabel.setFont(new Font("Arial", Font.PLAIN, 12));

        infoPanel.add(typeLabel);
        infoPanel.add(distanceLabel);
        infoPanel.add(confidenceLabel);

        // Timestamp en bas
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        JLabel timeLabel = new JLabel(sdf.format(data.timestamp));
        timeLabel.setFont(new Font("Arial", Font.ITALIC, 10));
        timeLabel.setForeground(Color.GRAY);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        timeLabel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

        card.add(iconLabel, BorderLayout.NORTH);
        card.add(infoPanel, BorderLayout.CENTER);
        card.add(timeLabel, BorderLayout.SOUTH);

        return card;
    }

    private void updateObjectCard(JPanel card, DetectionData data) {
        // Mettre à jour les informations sur la carte
        if (card.getComponentCount() >= 3) {
            // Mettre à jour les labels d'information
            JPanel infoPanel = (JPanel) card.getComponent(1);
            if (infoPanel.getComponentCount() >= 3) {
                JLabel typeLabel = (JLabel) infoPanel.getComponent(0);
                JLabel distanceLabel = (JLabel) infoPanel.getComponent(1);
                JLabel confidenceLabel = (JLabel) infoPanel.getComponent(2);

                typeLabel.setText(data.type);
                distanceLabel.setText("Distance: " + String.format("%.1f", data.distance) + " cm");
                confidenceLabel.setText("Confiance: " + String.format("%.1f", data.confidence * 100) + "%");
            }

            // Mettre à jour le timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
            JLabel timeLabel = (JLabel) card.getComponent(2);
            timeLabel.setText(sdf.format(data.timestamp));
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

    private Icon getIconForObjectType(String type) {
        // Créer des icônes personnalisées en fonction du type d'objet
        int size = 32;
        ImageIcon icon = new ImageIcon("ico.png");

        Graphics g = icon.getImage().getGraphics();

        // Couleur de fond basée sur le type
        Color color = getColorForObjectType(type);
        g.setColor(color);
        g.fillOval(0, 0, size, size);

        // Bordure
        g.setColor(color.darker());
        g.drawOval(0, 0, size - 1, size - 1);

        // Initiale du type d'objet
        g.setColor(Color.WHITE);
        g.setFont(new Font("Arial", Font.BOLD, 16));
        String initial = type.substring(0, 1).toUpperCase();
        FontMetrics fm = g.getFontMetrics();
        int x = (size - fm.stringWidth(initial)) / 2;
        int y = ((size - fm.getHeight()) / 2) + fm.getAscent();
        g.drawString(initial, x, y);

        g.dispose();
        return icon;
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
            // Format attendu: type;distance;confidence
            String[] parts = content.split(";");
            if (parts.length >= 3) {
                String objectType = parts[0].trim();
                double distance = Double.parseDouble(parts[1].trim());
                double confidence = Double.parseDouble(parts[2].trim());

                // Créer un ID unique pour cette détection (type + hashcode)
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
            }
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement du message de détection: " + e.getMessage());
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

    // Graphique à barres simple
    private class SimpleBarChart extends JPanel {
        private String title;
        private Map<String, Double> data = new HashMap<>();
        private final Color[] COLORS = {
                new Color(70, 130, 180),   // Bleu acier
                new Color(255, 99, 71),    // Rouge tomate
                new Color(50, 205, 50),    // Vert lime
                new Color(255, 165, 0),    // Orange
                new Color(128, 0, 128)     // Violet
        };

        public SimpleBarChart(String title) {
            this.title = title;
            setBackground(Color.WHITE);
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

            int width = getWidth();
            int height = getHeight();
            int barWidth = width / (data.size() + 1);
            int maxValue = 0;

            // Trouver la valeur maximale
            for (Double value : data.values()) {
                maxValue = Math.max(maxValue, value.intValue());
            }

            // Ajuster si maxValue est trop petit
            maxValue = Math.max(maxValue, 100);

            // Dessiner le titre
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics metrics = g2d.getFontMetrics();
            int titleWidth = metrics.stringWidth(title);
            g2d.drawString(title, (width - titleWidth) / 2, 20);

            // Dessiner les barres
            int i = 0;
            for (Map.Entry<String, Double> entry : data.entrySet()) {
                String key = entry.getKey();
                double value = entry.getValue();

                int x = i * barWidth + barWidth / 2;
                int barHeight = (int)((value / maxValue) * (height - 60));

                // Dessiner la barre
                g2d.setColor(COLORS[i % COLORS.length]);
                g2d.fillRect(x, height - 30 - barHeight, barWidth - 10, barHeight);

                // Dessiner la valeur
                g2d.setColor(Color.BLACK);
                String valueStr = String.format("%.1f", value);
                int valueWidth = metrics.stringWidth(valueStr);
                g2d.drawString(valueStr, x + (barWidth - 10 - valueWidth) / 2, height - 35 - barHeight);

                // Dessiner le label
                String shortKey = key.length() > 8 ? key.substring(0, 8) + "..." : key;
                int labelWidth = metrics.stringWidth(shortKey);
                g2d.drawString(shortKey, x + (barWidth - 10 - labelWidth) / 2, height - 10);

                i++;
            }
        }
    }

    // Graphique circulaire simple
    private class SimplePieChart extends JPanel {
        private String title;
        private Map<String, Integer> data = new HashMap<>();
        private final Color[] COLORS = {
                new Color(70, 130, 180),   // Bleu acier
                new Color(255, 99, 71),    // Rouge tomate
                new Color(50, 205, 50),    // Vert lime
                new Color(255, 165, 0),    // Orange
                new Color(128, 0, 128),    // Violet
                new Color(255, 215, 0),    // Or
                new Color(65, 105, 225),   // Bleu royal
                new Color(139, 69, 19)     // Brun
        };

        public SimplePieChart(String title) {
            this.title = title;
            setBackground(Color.WHITE);
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

            // Dessiner le titre
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            FontMetrics metrics = g2d.getFontMetrics();
            int titleWidth = metrics.stringWidth(title);
            g2d.drawString(title, (width - titleWidth) / 2, 20);

            // Calculer le total
            int total = 0;
            for (int value : data.values()) {
                total += value;
            }

            if (total > 0) {
                // Calculer le centre et le rayon
                int centerX = width / 2;
                int centerY = height / 2 + 10;
                int radius = Math.min(width, height) / 3;

                // Dessiner les sections du camembert
                int startAngle = 0;
                int i = 0;

                for (Map.Entry<String, Integer> entry : data.entrySet()) {
                    String key = entry.getKey();
                    int value = entry.getValue();

                    // Calculer l'angle de la section
                    int arcAngle = (int) Math.round(360.0 * value / total);

                    // Dessiner la section
                    g2d.setColor(COLORS[i % COLORS.length]);
                    g2d.fillArc(centerX - radius, centerY - radius, 2 * radius, 2 * radius, startAngle, arcAngle);

                    // Calculer la position de la légende
                    double middleAngle = Math.toRadians(startAngle + arcAngle / 2);
                    int legendX = (int) (centerX + (radius + 20) * Math.cos(middleAngle));
                    int legendY = (int) (centerY + (radius + 20) * Math.sin(middleAngle));

                    // Ajuster la position pour la lisibilité
                    if (legendX < centerX) {
                        legendX -= metrics.stringWidth(key) + 10;
                    }
                    if (legendY < centerY) {
                        legendY += metrics.getHeight() / 2;
                    } else {
                        legendY += metrics.getHeight();
                    }

                    // Dessiner la légende
                    g2d.setColor(Color.BLACK);
                    String label = key + " (" + value + ")";
                    g2d.drawString(label, legendX, legendY);

                    startAngle += arcAngle;
                    i++;
                }
            } else {
                // Aucune donnée
                g2d.setColor(Color.GRAY);
                g2d.setFont(new Font("Arial", Font.ITALIC, 12));
                String noData = "Aucune donnée disponible";
                int noDataWidth = metrics.stringWidth(noData);
                g2d.drawString(noData, (width - noDataWidth) / 2, height / 2);
            }
        }
    }

    @Override
    protected void takeDown() {
        System.out.println("ReceiverAgent terminé: " + getLocalName());
        if (frame != null) {
            frame.dispose();
        }
    }
}