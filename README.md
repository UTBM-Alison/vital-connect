# VitalConnect Java
[![CI](https://github.com/UTBM-Alison/vital-connect/actions/workflows/ci.yml/badge.svg)](https://github.com/UTBM-Alison/vital-connect/actions/workflows/ci.yml)

Une implémentation Java autonome pour recevoir, traiter et afficher en temps réel les données VitalRecorder via Socket.IO.

Ce projet expose un serveur Socket.IO, décompresse et nettoie les trames reçues, les transforme en objets de domaine structurés, puis les diffuse vers des sorties (console, etc.). Il inclut une suite de tests unitaires et une CI GitHub Actions prête à l’emploi.

---

## Sommaire
- [Fonctionnalités](#fonctionnalités)
- [Architecture & pipeline](#architecture--pipeline)
- [Prérequis](#prérequis)
- [Installation](#installation)
- [Exécution](#exécution)
- [Configuration](#configuration)
- [Tests](#tests)
- [Intégration Continue (CI)](#intégration-continue-ci)
- [Dépannage](#dépannage)
- [Contribuer](#contribuer)
- [Licence](#licence)

---

## Fonctionnalités
- Serveur Socket.IO pour recevoir les données VitalRecorder.
- Décompression robuste des payloads (zlib, indicateur Socket.IO binaire v4, données non compressées renvoyées telles quelles).
- Nettoyage JSON (suppression des caractères de contrôle, remplacement NaN/Infinity par null, normalisation décimale virgule→point).
- Transformation des données en structures prêtes à l’affichage (formatage numérique indépendant de la locale, stats waveform min/max/moyenne).
- Sortie console compacte ou verbeuse, avec couleurs ANSI optionnelles.
- Orchestration claire entrée→processeur→sorties, statistiques (uptime, rooms/tracks traités, etc.).
- Suite de tests JUnit 5 + Mockito + AssertJ.

---

## Architecture & pipeline
- Entrée: `vitalconnect.input.SocketIOServerInput`
  - Héberge le serveur Socket.IO (port configurable), reçoit les trames brutes.
  - Utilise `VitalDataDecompressor` pour décompresser si nécessaire.
  - Utilise `VitalDataProcessor` pour parser le JSON en `VitalData`.
  - Utilise `VitalDataTransformer` pour produire `ProcessedData`/`ProcessedTrack`.
- Coeur: `vitalconnect.core.VitalProcessor`
  - Reçoit des `ProcessedData` de l’entrée et les diffuse vers une ou plusieurs sorties.
  - Maintient l’état (running), la dernière donnée reçue et des statistiques (avec uptime basé sur System.nanoTime pour précision).
- Sorties: `vitalconnect.output.*`
  - `ConsoleVitalOutput`: affiche les données au format compact ou verbeux, en couleurs optionnellement.

Entrée → Décompression → Nettoyage/Parsing → Transformation → Sorties

---

## Prérequis
- Java JDK 25 (compilation et exécution)
- Maven 3.9+

Vérification rapide:
```cmd
java -version
mvn -v
```

---

## Installation
Récupérez les dépendances et compilez le projet:
```cmd
mvn clean package -DskipTests
```
Le packaging produit un JAR exécutable (shaded) dans `target/`. Le nom exact dépend de la configuration Maven (par défaut, suffixe `-shaded`).

Listez les artefacts:
```cmd
dir target
```

---

## Exécution
Exécutez l’application principale `vitalconnect.VitalConnectApplication` à partir du JAR:
```cmd
java -jar target\vital-connect-java-1.0.0-shaded.jar
```

Arguments CLI (tous optionnels, dans l’ordre):
1. host (string, défaut: `127.0.0.1`)
2. port (int, défaut: `3000`)
3. verbose (boolean, défaut: `false`)
4. colorized (boolean, défaut: `true`)

Exemples:
```cmd
:: Démarrage avec défauts (127.0.0.1:3000, compact, couleurs)
java -jar target\vital-connect-java-1.0.0-shaded.jar

:: Démarrage verbeux sans couleurs sur le port 5000
java -jar target\vital-connect-java-1.0.0-shaded.jar 127.0.0.1 5000 true false
```

Pendant l’exécution, vous pouvez arrêter l’application avec Ctrl+C (ou appeler `shutdown()` via le hook d’arrêt). Des statistiques finales sont affichées.

Note: configurez VitalRecorder pour pointer vers l’IP/port du serveur (par défaut 127.0.0.1:3000).

---

## Configuration
- Journalisation: `src/main/resources/logback.xml`
  - Par défaut, écrit sur la console et peut écrire dans `logs/vitalconnect.log` (selon la configuration fournie).
- Console:
  - Mode `verbose` ou `compact` selon la création de `ConsoleVitalOutput` (via les args CLI ou code).
  - Couleurs ANSI activables/désactivables.
- Décompression: `VitalDataDecompressor` détecte automatiquement les entêtes zlib (`0x78`) et l’indicateur binaire Socket.IO v4 (`0x04 0x78`), sinon renvoie les données brutes telles quelles.
- Nettoyage/Parsing: `VitalDataProcessor` remplace toutes variantes de `NaN`/`Infinity` (+/−, casse insensible) par `null` et normalise `123,456` → `123.456` dans objets/tableaux.
- Transformation: `VitalDataTransformer` formate les flottants avec `Locale.US` (`72.500`, `1.000..5.000`, etc.).

---

## Tests
Exécuter toute la suite:
```cmd
mvn test
```
Cibler un test:
```cmd
mvn -Dtest=VitalProcessorTest test
mvn -Dtest=VitalDataTransformerTest#testTransformWaveformData test
```

Rapports Surefire:
- Générés dans `target/surefire-reports/`.
- En CI, ils sont publiés comme artefacts téléchargeables.

Compatibilité Mockito/JDK 25:
- Le `pom.xml` configure Surefire avec:
  - `-Djdk.attach.allowAttachSelf=true`
  - `--add-opens java.base/java.lang=ALL-UNNAMED`
- Ces options permettent l’agent ByteBuddy et les mocks inline/MockedConstruction nécessaires sous JDK 25.

---

## Intégration Continue (CI)
Un workflow GitHub Actions est fourni: `.github/workflows/ci.yml`.
- Déclencheurs: push/PR sur `main` et `master`, exécution manuelle.
- Matrix: `ubuntu-latest` et `windows-latest`, JDK `25`.
- Étapes: checkout, setup-java (cache Maven), `mvn test`, upload des rapports Surefire.

Badge de statut (remplacez `<owner>`/`<repo>` si vous le souhaitez dans votre README):
```markdown
[![CI](https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg)](https://github.com/<owner>/<repo>/actions/workflows/ci.yml)
```

---

## Dépannage
- Le JAR ne démarre pas / classe principale introuvable
  - Assurez-vous d’avoir exécuté `mvn clean package` et d’utiliser le JAR `-shaded`.
- Les tests Mockito échouent sous JDK récent
  - Gardez les options Surefire du `pom.xml` (attach + add-opens) ou exécutez avec ces flags.
- Formatage décimal incorrect (virgule au lieu de point)
  - Le formatage est forcé en `Locale.US` dans le transformeur; vérifiez que vous utilisez bien le pipeline standard.
- Les données Socket.IO ne se décompressent pas
  - `VitalDataDecompressor` renvoie les données brutes si non zlib; vérifiez la source de données et l’indicateur binaire v4.

Logs
- Consultez `logs/vitalconnect.log` (si activé dans `logback.xml`) et la console.
- En cas d’échec de l’écriture console, `ConsoleVitalOutput` journalise un message d’erreur sur `System.err`.

---

## Contribuer
Les contributions sont les bienvenues !
1. Créez une branche à partir de `main`.
2. Ajoutez des tests pour toute modification fonctionnelle.
3. Veillez au passage de `mvn test` sur Windows et Linux.
4. Ouvrez une Pull Request détaillant les changements.

---

## Licence
Aucune licence explicite n’est fournie pour l’instant. Si vous souhaitez publier sous une licence open-source (MIT/Apache-2.0, etc.), ajoutez un fichier `LICENSE` et mettez à jour cette section.
