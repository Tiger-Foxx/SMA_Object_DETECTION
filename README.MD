# README - Agent Cognitif Orienté But pour la Détection d'Objets et Estimation de Distance

## Table des Matières
1. [Contexte du Projet](#contexte-du-projet)
2. [Architecture du Système](#architecture-du-système)
3. [Fonctionnalités](#fonctionnalités)
4. [Prérequis et Installation](#prérequis-et-installation)
5. [Configuration](#configuration)
6. [Utilisation](#utilisation)
7. [Structure du Code](#structure-du-code)
8. [Détails Techniques](#détails-techniques)
9. [Captures d'Écran](#captures-décran)
10. [FAQ](#faq)
11. [Contributions](#contributions)
12. [Licence](#licence)

---

## Contexte du Projet
Ce projet a été développé dans le cadre du cours de **Systèmes Multi-Agents (SMA)** et **Projet Système Expert** à Polytechnique. L'objectif était de concevoir et implémenter un **agent cognitif orienté but** utilisant Java et JADE, avec OpenCV pour la détection d'objets et l'estimation de distance en temps réel.

L'agent cognitif se compose de deux parties principales :
1. **Agent Vision Cognitif** : Capture des images via une caméra, détecte les objets, calcule leur distance et envoie ces informations à un agent récepteur.
2. **Agent Récepteur** : Reçoit les données de l'agent vision, les affiche dans une interface graphique et génère des statistiques.

---

## Architecture du Système
Le système repose sur une architecture multi-agents avec les composants suivants :
- **MainContainer** : Conteneur principal JADE qui gère la plateforme.
- **VisionContainer** : Conteneur pour l'agent de vision cognitive.
- **ReceiverContainer** : Conteneur pour l'agent récepteur.

### Schéma de l'Architecture
```
+-------------------+       +-------------------+       +-------------------+
|  VisionContainer  | <---> |  MainContainer    | <---> | ReceiverContainer |
| (Agent Vision)    |       | (JADE Platform)   |       | (Agent Récepteur) |
+-------------------+       +-------------------+       +-------------------+
```

---

## Fonctionnalités
1. **Détection d'Objets** :
    - Utilisation de modèles pré-entraînés (YOLOv4 pour les objets, ResNet pour les visages).
    - Détection en temps réel avec ajustement du seuil de confiance.
2. **Estimation de Distance** :
    - Calcul basé sur la taille connue des objets et la longueur focale de la caméra.
3. **Communication entre Agents** :
    - Envoi des résultats de détection via des messages ACL (Agent Communication Language).
4. **Interface Graphique** :
    - Affichage des résultats, logs et statistiques.
    - Configuration des paramètres de détection.

---

## Prérequis et Installation

### Prérequis
- Java JDK 11 ou supérieur
- Maven
- OpenCV 4.5.x
- JADE (inclus dans le projet)
- Modèles de détection (YOLOv4 et ResNet)

### Installation
1. **Cloner le dépôt** :
   ```bash
   git clone https://github.com/Tiger-Foxx/SMA_Object_DETECTION.git

   cd projet-sma
   ```

2. **Installer les dépendances** :
    - Téléchargez OpenCV et configurez le chemin dans `pom.xml`.
    - Placez les modèles de détection dans le dossier `models/` :
        - `yolov4.cfg`
        - `yolov4.weights`
        - `deploy.prototxt`
        - `res10_300x300_ssd_iter_140000.caffemodel`

3. **Compiler le projet** :
   ```bash
   mvn clean install
   ```

---

## Configuration
### Fichiers de Configuration
- `MainContainer.java` : Conteneur principal JADE.
- `VisionContainer.java` : Interface pour l'agent de vision.
- `ReceiverContainer.java` : Interface pour l'agent récepteur.

### Paramètres Modifiables
- **Seuil de confiance** : Ajustable via l'interface graphique.
- **Adresse IP du MainContainer** : Saisie dans les champs dédiés.
- **Mode de détection** : Tous les objets, visages uniquement, ou objets spécifiques.

---

## Utilisation
1. **Démarrer le MainContainer** :
   ```bash
   java -cp target/projet-sma.jar MainContainer
   ```

2. **Démarrer l'Agent Vision** :
   ```bash
   java -cp target/projet-sma.jar VisionContainer
   ```
    - Saisir l'adresse IP du MainContainer (par défaut `localhost`).

3. **Démarrer l'Agent Récepteur** :
   ```bash
   java -cp target/projet-sma.jar ReceiverContainer
   ```
    - Saisir la même adresse IP que pour l'agent vision.

4. **Utiliser l'Interface** :
    - Activer/désactiver la détection.
    - Ajuster le seuil de confiance.
    - Visualiser les résultats et statistiques.

---

## Structure du Code
```
projet-sma/
├── src/
│   ├── main/java/
│   │   ├── Agents/
│   │   │   ├── AgentVisionCognitif.java
│   │   │   └── ReceiverAgent.java
│   │   ├── Containers/
│   │   │   ├── MainContainer.java
│   │   │   ├── VisionContainer.java
│   │   │   └── ReceiverContainer.java
│   │   └── Utils/
│   │       ├── DetectionData.java
│   │       └── ObjectTracker.java
├── models/
│   ├── yolov4.cfg
│   ├── yolov4.weights
│   ├── deploy.prototxt
│   └── res10_300x300_ssd_iter_140000.caffemodel
└── screens/
    ├── 0.png
    ├── 1.png
    └── ...
```

---

## Détails Techniques
### Détection d'Objets
- **YOLOv4** : Pour la détection d'objets génériques.
- **ResNet** : Pour la détection de visages.
- **Calcul de Distance** :
  \[
  \text{Distance (cm)} = \frac{\text{Taille Réelle (cm)} \times \text{Longueur Focale (pixels)}}{\text{Taille en Pixels}}
  \]

### Communication JADE
- Messages ACL entre agents.
- Utilisation du **DF (Directory Facilitator)** pour la découverte des agents.

---

## Captures d'Écran
### 1. Interface de Saisie de l'IP
![Saisie de l'IP](screens/0.png)

### 2. Interface de l'Agent Vision (Inactive)
![Vision Inactive](screens/2.png)

### 3. Interface de l'Agent Vision (Active)
![Vision Active](screens/3.png)

### 4. Logs de l'Agent Vision
![Logs Vision](screens/4.png)

### 5. Résultats dans l'Agent Récepteur
![Résultats Récepteur](screens/5.png)

---

## FAQ
### Q1 : Comment ajuster la sensibilité de détection ?
R : Utilisez le curseur dans l'interface de l'agent vision pour modifier le seuil de confiance.

### Q2 : Pourquoi la distance estimée est-elle incorrecte ?
R : Vérifiez que la taille réelle de l'objet est correctement définie dans `OBJECT_SIZES`.

### Q3 : Comment utiliser le système sur plusieurs machines ?
R : Saisissez l'IP du MainContainer dans les champs dédiés des containers Vision et Receiver.

---

## Contributions
- **Équipe** : [GROUPE 10]
- **Encadrant** : [Pr Batchakui B.]
- **Code** : Contribuez via les pull requests.


--- 

Pour toute question, contactez THE FOX  : [donfackarthur750@gmail.com]