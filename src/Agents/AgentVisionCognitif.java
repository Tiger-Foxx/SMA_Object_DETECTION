package Agents;
import jade.core.Agent;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;

import org.opencv.core.*;
import org.opencv.core.Point;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;
import org.opencv.objdetect.Objdetect;
import org.opencv.videoio.VideoCapture;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;

public class AgentVisionCognitif extends Agent {

    // Paramètres de l'agent
    private static final int FOCAL_LENGTH = 615; // Longueur focale approximative pour webcam standard (en pixels)
    private static final int AVERAGE_FACE_WIDTH = 16; // Largeur moyenne d'un visage humain (en cm)

    // Capteurs
    private VideoCapture camera;
    private CascadeClassifier faceDetector;

    // État interne
    private boolean cameraActive = false;
    private double lastDistance = 0;

    // Interface utilisateur
    private JFrame frame;
    private JLabel cameraFeed;
    private JLabel distanceLabel;

    @Override
    protected void setup() {
        System.out.println("Agent cognitif de vision démarré: " + getLocalName());

        // Initialiser OpenCV
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        // Initialiser les capteurs (caméra et détecteur de visage)
        initSensors();

        // Initialiser l'interface utilisateur
        initUI();

        // Ajouter le comportement principal de l'agent
        addBehaviour(new VisionBehaviour(this, 100)); // Mise à jour toutes les 100ms
    }

    private void initSensors() {
        // Initialiser la caméra (0 = webcam par défaut)
        camera = new VideoCapture(0);
        if (!camera.isOpened()) {
            System.err.println("Erreur: Impossible d'accéder à la caméra!");
            doDelete(); // Terminer l'agent si la caméra n'est pas disponible
            return;
        }

        // Initialiser le détecteur de visage avec le classifieur en cascade Haar
        String classifierPath = "haarcascade_frontalface_default.xml";
        faceDetector = new CascadeClassifier(classifierPath);
        if (faceDetector.empty()) {
            System.err.println("Erreur: Impossible de charger le classifieur!");
            doDelete();
            return;
        }

        cameraActive = true;
    }

    private void initUI() {
        // Créer l'interface utilisateur
        frame = new JFrame("Agent Cognitif - Vision");
        frame.setLayout(new BorderLayout());
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Panel pour le flux vidéo
        cameraFeed = new JLabel();
        frame.add(cameraFeed, BorderLayout.CENTER);

        // Panel pour afficher la distance
        distanceLabel = new JLabel("Distance: ? cm");
        distanceLabel.setFont(new Font("Arial", Font.BOLD, 18));
        frame.add(distanceLabel, BorderLayout.SOUTH);

        // Configurer et afficher la fenêtre
        frame.setSize(640, 520);
        frame.setVisible(true);
    }

    // Classe interne pour le comportement principal de vision
    private class VisionBehaviour extends TickerBehaviour {

        public VisionBehaviour(Agent a, long period) {
            super(a, period);
        }

        @Override
        protected void onTick() {
            if (!cameraActive) return;

            // Capteur: Capturer une image de la caméra
            Mat frame = new Mat();
            camera.read(frame);
            if (frame.empty()) return;

            // Traitement: Convertir en niveaux de gris pour la détection
            Mat grayFrame = new Mat();
            Imgproc.cvtColor(frame, grayFrame, Imgproc.COLOR_BGR2GRAY);
            Imgproc.equalizeHist(grayFrame, grayFrame);

            // Règle de décision: Détecter les visages
            MatOfRect faces = new MatOfRect();
            faceDetector.detectMultiScale(
                    grayFrame,
                    faces,
                    1.1,
                    3,
                    0 | Objdetect.CASCADE_SCALE_IMAGE,
                    new Size(30, 30)
            );

            // Action: Calculer et afficher la distance pour chaque visage détecté
            Rect[] facesArray = faces.toArray();
            if (facesArray.length > 0) {
                // Prendre le plus grand visage (supposé être le plus proche)
                Rect largestFace = findLargestFace(facesArray);

                // Calculer la distance basée sur la taille apparente du visage
                double distance = calculateDistance(largestFace.width);
                lastDistance = distance;

                // Mettre à jour l'affichage
                distanceLabel.setText(String.format("Distance: %.2f cm", distance));

                // Dessiner un rectangle autour du visage
                Imgproc.rectangle(
                        frame,
                        new Point(largestFace.x, largestFace.y),
                        new Point(largestFace.x + largestFace.width, largestFace.y + largestFace.height),
                        new Scalar(0, 255, 0),
                        3
                );

                // Ajouter la distance sur l'image
                Imgproc.putText(
                        frame,
                        String.format("%.2f cm", distance),
                        new Point(largestFace.x, largestFace.y - 10),
                        Imgproc.FONT_HERSHEY_SIMPLEX,
                        0.7,
                        new Scalar(0, 255, 0),
                        2
                );

                // Communiquer la distance (exemple d'interaction multi-agent)
                if (Math.abs(lastDistance - distance) > 10) { // Seuil de changement significatif
                    informOtherAgents(distance);
                }
            } else {
                distanceLabel.setText("Aucun visage détecté");
            }

            // Afficher l'image dans l'interface
            updateCameraFeed(frame);
        }

        private Rect findLargestFace(Rect[] faces) {
            Rect largest = faces[0];
            for (Rect face : faces) {
                if (face.area() > largest.area()) {
                    largest = face;
                }
            }
            return largest;
        }

        private double calculateDistance(double faceWidthPixels) {
            // Formule: distance = (largeur réelle * distance focale) / largeur en pixels
            return (AVERAGE_FACE_WIDTH * FOCAL_LENGTH) / faceWidthPixels;
        }

        private void updateCameraFeed(Mat frame) {
            // Convertir Mat en BufferedImage pour l'affichage Swing
            BufferedImage image = matToBufferedImage(frame);
            if (image != null) {
                ImageIcon icon = new ImageIcon(image);
                cameraFeed.setIcon(icon);
                cameraFeed.repaint(); // Mettre à jour l'affichage de l'image
            }
        }

        private BufferedImage matToBufferedImage(Mat mat) {
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

        private void informOtherAgents(double distance) {
            // Exemple d'envoi de message à d'autres agents
            ACLMessage msg = new ACLMessage(ACLMessage.INFORM);
            msg.addReceiver(getAID()); // À remplacer par les AID des agents destinataires
            msg.setContent("DISTANCE:" + distance);
            send(msg);
        }
    }

    @Override
    protected void takeDown() {
        System.out.println("Agent cognitif de vision terminé");

        // Libérer les ressources
        if (camera != null && camera.isOpened()) {
            camera.release();
        }

        if (frame != null) {
            frame.dispose();
        }
    }
}