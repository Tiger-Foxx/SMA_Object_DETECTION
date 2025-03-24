package Containers;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.logging.Handler;
import java.util.logging.ConsoleHandler;
import java.util.logging.SimpleFormatter;

public class ReceiverContainer extends JFrame {
    private static final Logger LOGGER = Logger.getLogger(ReceiverContainer.class.getName());
    private JTextArea logArea;
    private JTextField mainHostField;
    private JButton startButton;
    private AgentContainer agentContainer;


    public ReceiverContainer() {
        // Configuration du logger
        configureLogger();

        // Configuration de la fenêtre
        setTitle("Receiver Container");
        setSize(600, 200);
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel pour l'entrée de l'adresse IP
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new FlowLayout());

        JLabel hostLabel = new JLabel("Adresse du conteneur principal:");
        mainHostField = new JTextField("localhost", 15);
        startButton = new JButton("Démarrer ReceiverAgent");

        inputPanel.add(hostLabel);
        inputPanel.add(mainHostField);
        inputPanel.add(startButton);

        add(inputPanel, BorderLayout.NORTH);

        // Zone de logs
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setPreferredSize(new Dimension(580, 200));
        add(scrollPane, BorderLayout.CENTER);

        // Action du bouton "Démarrer"
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String host = mainHostField.getText();
                if (!host.isEmpty()) {
                    startReceiverAgent(host);
                    startButton.setEnabled(false);
                } else {
                    JOptionPane.showMessageDialog(ReceiverContainer.this,
                            "Veuillez entrer une adresse valide.");
                }
            }
        });

        // Afficher la fenêtre
        setVisible(true);
    }

    private void configureLogger() {
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        LOGGER.addHandler(consoleHandler);
        LOGGER.setLevel(Level.ALL);
    }

    private void startReceiverAgent(String mainHost) {
        try {
            // Démarrer le container JADE
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl(false); // false car ce n'est pas un conteneur principal
            profile.setParameter(Profile.MAIN_HOST, mainHost);
            profile.setParameter(Profile.MAIN_PORT, "1099"); // Port par défaut de JADE

            log("Connexion au conteneur principal sur " + mainHost + "...");
            agentContainer = rt.createAgentContainer(profile);
            log("Conteneur créé avec succès");

            // Démarrer le ReceiverAgent
            AgentController agentController = agentContainer.createNewAgent(
                    "ReceiverAgent",
                    "Agents.ReceiverAgent",
                    new Object[]{}
            );
            agentController.start();
            log("ReceiverAgent démarré avec succès");
        } catch (Exception e) {
            log("ERREUR: " + e.getMessage());
            e.printStackTrace();
            startButton.setEnabled(true);
        }
    }

    private void log(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            // Autoscroll
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
        LOGGER.info(message);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ReceiverContainer());
    }
}