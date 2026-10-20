# Mahjora

Application Android (Kotlin, Jetpack Compose) de suivi de parties de Mahjong compétition (règles « Vent d'Est »).

## Architecture

Trois « agents », chacun défini par une interface et branché dans `AppContainer` :

| Agent | Interface | Implémentation | Rôle |
|---|---|---|---|
| Score | `agents/scoring/ScoringAgent` | `StandardScoringAgent` | Décompose la main, détecte les 82 combinaisons, applique les exclusions, ajoute les fleurs, calcule les paiements (`Payments`), le différentiel de points (`TargetPoints`) et la rotation des places (`SeatRotation`). |
| Caméra | `agents/vision/TileDetectionAgent` | `ModelTileDetectionAgent` | Photo → tuiles (NMS, tri en lignes, fleurs séparées). Le réseau de neurones se branche via `TileInferenceBackend` ; sans modèle (`NoModelBackend`) l'écran de scan se désactive. Classes du modèle = `Tile.index` (0-41). |
| Stockage | `agents/storage/StorageAgent` | `SqliteStorageAgent` | SQLite local (`SQLiteOpenHelper`) : joueurs, parties, mains, scores par joueur, combinaisons, main saisie en JSON. |

Écrans (`ui/`) : accueil, nouvelle partie, partie (scores + historique), saisie d'une main (palette de tuiles, figures déclarées, score en direct), scan caméra, liste des combinaisons.

## Développement

```
./gradlew :app:testDebugUnitTest   # tests du score, de la détection et du JSON
./gradlew :app:assembleDebug
```

Renseigner `sdk.dir` dans `local.properties` (ignoré par git).

## À valider / à faire

- Table d'exclusions entre combinaisons (`ComboDefinition.excludes`) : non imprimée sur les mémos, à vérifier avec le règlement officiel.
- Modèle de reconnaissance des tuiles (ex. détecteur TFLite 42 classes) à entraîner et brancher.
