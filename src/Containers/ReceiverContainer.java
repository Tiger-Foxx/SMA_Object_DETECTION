package Containers;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ReceiverContainer extends JFrame {

    private ContainerController containerController;
    private JTextField ipField;
    private JButton startButton;

    public ReceiverContainer() {
        // Configuration de la fenêtre
        setTitle("Receiver Container");
        setSize(400, 150);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Panel pour l'entrée de l'adresse IP
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new FlowLayout());

        JLabel ipLabel = new JLabel("Adresse IP du CalculatorAgent:");
        ipField = new JTextField(15);
        startButton = new JButton("Démarrer ReceiverAgent");

        inputPanel.add(ipLabel);
        inputPanel.add(ipField);
        inputPanel.add(startButton);

        add(inputPanel, BorderLayout.CENTER);

        // Action du bouton "Démarrer"
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String ip = ipField.getText();
                if (!ip.isEmpty()) {
                    startReceiverAgent(ip);
                    startButton.setEnabled(false); // Désactiver le bouton après le démarrage
                } else {
                    JOptionPane.showMessageDialog(ReceiverContainer.this, "Veuillez entrer une adresse IP valide.");
                }
            }
        });

        // Afficher la fenêtre
        setVisible(true);
    }

    private void startReceiverAgent(String ip) {
        try {
            // Démarrer le container JADE
            Runtime rt = Runtime.instance();
            Profile profile = new ProfileImpl();
            profile.setParameter(Profile.MAIN_HOST, ip); // Utiliser l'adresse IP fournie
            profile.setParameter(Profile.GUI, "false"); // Pas de GUI pour le container
            containerController = rt.createAgentContainer(profile);

            // Démarrer le ReceiverAgent
            AgentController agentController = containerController.createNewAgent(
                    "ReceiverAgent",
                    "Agents.ReceiverAgent",
                    new Object[]{} // Pas d'arguments supplémentaires
            );
            agentController.start();

            System.out.println("ReceiverAgent démarré sur le container avec l'adresse IP: " + ip);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Erreur lors du démarrage du ReceiverAgent: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ReceiverContainer();
    }
}