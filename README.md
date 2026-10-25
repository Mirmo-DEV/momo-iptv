# Momo IPTV

Application IPTV pour Fire TV Stick (Android TV), pensée pour la télécommande.

**V1** : comptes Xtream Codes et listes M3U, chaînes en direct par catégories, zapping haut/bas,
favoris (touche Menu ≡), plusieurs comptes, liste en cache (affichage instantané), reconnexion automatique.

Aucun identifiant n'est dans le code : les comptes se saisissent dans l'appli et restent sur la Fire Stick.

## Installer sur la Fire Stick

1. Sur la Fire Stick : installer l'appli **Downloader** (AFTVnews) depuis l'Appstore.
2. *Paramètres → Ma Fire TV → Options pour les développeurs → Installer des applis inconnues* → autoriser **Downloader**.
   (Si « Options pour les développeurs » n'apparaît pas : *Ma Fire TV → À propos*, appuyer 7 fois sur le nom de l'appareil.)
3. Dans Downloader, taper l'adresse :

   ```
   https://github.com/Mirmo-DEV/momo-iptv/releases/latest/download/MomoIPTV.apk
   ```

4. Installer. Pour mettre à jour plus tard : refaire la même chose, l'appli s'installe par-dessus en gardant comptes et favoris.

## Télécommande

| Écran | Touche | Action |
|---|---|---|
| Liste des chaînes | OK | Regarder |
| | Menu ≡ | Ajouter / retirer des favoris |
| | ◀ ▶ | Passer des catégories aux chaînes |
| Lecteur | ▲ ▼ | Chaîne précédente / suivante |
| | OK | Liste des chaînes par-dessus la vidéo |
| | Menu ≡ | Favori |
| | ◀ ▶ | Réafficher le bandeau d'info |
| Comptes | Menu ≡ | Modifier ou supprimer un compte |

## Compilation

L'APK est compilé automatiquement par GitHub Actions à chaque envoi sur `main`
(`.github/workflows/build.yml`) et publié dans les *Releases*.
La clé de signature est stockée dans les secrets du dépôt (`SIGNING_KEYSTORE_BASE64`,
`SIGNING_PASSWORD`, `SIGNING_ALIAS`) : elle garantit que chaque nouvelle version s'installe par-dessus l'ancienne.

## Feuille de route

- **V2** : guide TV (EPG), films et séries avec reprise de lecture
- **V3** : portail Stalker, replay
