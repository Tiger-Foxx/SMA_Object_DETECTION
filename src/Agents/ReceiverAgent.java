package Agents;

import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;

import javax.swing.*;
import java.awt.*;

public class ReceiverAgent extends Agent {

    private JFrame frame;
    private JLabel distanceLabel;

    @Override
    protected void setup() {
        System.out.println("ReceiverAgent démarré: " + getLocalName());

        // Initialiser l'interface utilisateur
        initUI();

        // Ajouter un comportement pour recevoir les messages
        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                ACLMessage msg = receive();
                if (msg != null) {
                    // Traiter le message reçu
                    String content = msg.getContent();
                    if (content.startsWith("DISTANCE:")) {
                        String distance = content.substring("DISTANCE:".length());
                        updateDistanceDisplay(distance);
                    }
                } else {
                    block();
                }
            }
        });
    }

    private void initUI() {
        // Créer l'interface utilisateur
        frame = new JFrame("ReceiverAgent - Affichage de la distance");
        frame.setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Label pour afficher la distance
        distanceLabel = new JLabel("En attente de données...", SwingConstants.CENTER);
        distanceLabel.setFont(new Font("Arial", Font.BOLD, 24));
        frame.add(distanceLabel, BorderLayout.CENTER);

        // Configurer et afficher la fenêtre
        frame.setSize(400, 200);
        frame.setVisible(true);
    }

    private void updateDistanceDisplay(String distance) {
        // Mettre à jour l'affichage de la distance
        SwingUtilities.invokeLater(() -> {
            distanceLabel.setText("L'objet se trouve à " + distance + " cm");
        });
    }

    @Override
    protected void takeDown() {
        System.out.println("ReceiverAgent terminé");

        // Fermer l'interface utilisateur
        if (frame != null) {
            frame.dispose();
        }
    }
}