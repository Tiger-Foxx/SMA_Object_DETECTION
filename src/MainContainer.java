import jade.wrapper.AgentContainer;
import jade.core.Profile;
import jade.core.Runtime;
import jade.util.ExtendedProperties;
import jade.util.leap.Properties;
import jade.core.ProfileImpl;
import jade.wrapper.ControllerException;

public class MainContainer {
    public static void main(String[] args) {
        Runtime runtime = Runtime.instance();

        Properties properties = new ExtendedProperties();
        properties.setProperty(Profile.GUI, "true");
        Profile profile = new ProfileImpl(properties);
        AgentContainer mainContainer = runtime.createMainContainer(profile);
        try {
            mainContainer.start();

        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }
    }
}