# Intruder Proto

Prototype Android natif pour valider progressivement les possibilités et les limites d'Android.

## Environnement

- Kotlin
- Jetpack Compose
- Android Gradle Plugin 9.3.1
- Gradle 9.5.0
- JDK 17
- Minimum Android : API 29 (Android 10)

## Développement

Le code peut être modifié directement dans le navigateur avec [github.dev](https://github.dev/lucentis/intruder-proto).

Aucun Android Studio ni environnement local n'est nécessaire pour modifier le code.

## Compilation APK

GitHub Actions compile automatiquement l'application à chaque push sur `main`, à chaque pull request et sur lancement manuel du workflow.

Pour récupérer l'APK :

1. Ouvrir l'onglet **Actions** du dépôt.
2. Sélectionner le dernier workflow **Build Android APK**.
3. Télécharger l'artefact **intruder-proto-debug-apk**.
4. Extraire le fichier `app-debug.apk` et l'installer sur le téléphone Android.

L'APK généré est une version de debug destinée aux tests, pas une version de publication.

## Objectif du prototype

Tester séparément les comportements Android nécessaires au projet avant de construire l'application complète : cycle de vie, verrouillage de l'interface, permissions et exécution en arrière-plan.
