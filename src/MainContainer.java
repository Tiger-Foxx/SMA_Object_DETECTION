import jade.wrapper.AgentContainer;
import jade.core.Profile;
import jade.core.Runtime;
import jade.util.ExtendedProperties;
import jade.util.leap.Properties;
import jade.core.ProfileImpl;
import jade.wrapper.ControllerException;

public class MainContainer {
    public static void main(String[] args) {
        try {
            // Création du conteneur principal
            Runtime runtime = Runtime.instance();
            ProfileImpl profile = new ProfileImpl(true); // true signifie que c'est un conteneur principal
            profile.setParameter(Profile.GUI, "true"); // Active l'interface graphique de JADE
            AgentContainer mainContainer = runtime.createMainContainer(profile);
            System.out.println("Conteneur principal démarré avec succès");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}