# Intégration Codecov

Ce projet utilise [Codecov](https://codecov.io) pour suivre et analyser la couverture de code.

## Configuration

### 1. Rapports JaCoCo
Le projet utilise JaCoCo (déjà configuré dans le `pom.xml`) pour générer les rapports de couverture :
- Les rapports XML sont générés dans `target/site/jacoco/jacoco.xml`
- Exécution automatique lors de `mvn verify`

### 2. Configuration Codecov
Le fichier `codecov.yml` contient la configuration personnalisée :
- Objectif de couverture : 90%
- Seuil de tolérance : 1%
- Ignorer les dossiers de test et fichiers non pertinents

### 3. Workflow GitHub Actions
Le workflow `.github/workflows/ci.yml` inclut :
- Upload automatique des rapports JaCoCo vers Codecov via `codecov/codecov-action@v5`
- Exécution uniquement sur Ubuntu avec JDK 25
- Upload conditionnel (ne fait pas échouer le build si Codecov est indisponible)
