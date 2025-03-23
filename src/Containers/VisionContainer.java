package Containers;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.ControllerException;

public class VisionContainer {

    public static void main(String[] args) {
        try {
            jade.core.Runtime runtime = Runtime.instance();
            Profile profile = new ProfileImpl(false);
            profile.setParameter(Profile.MAIN_HOST, "localhost");

            AgentContainer agentContainer = runtime.createAgentContainer(profile);
            AgentController agentController = agentContainer.createNewAgent("Calculator", "Agents.AgentVisionCognitif", new Object[0]);
            agentController.start();
        } catch (ControllerException e) {
            throw new RuntimeException(e);
        }
    }
}
