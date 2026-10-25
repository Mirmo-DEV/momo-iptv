# Momo IPTV

Application IPTV pour Fire TV Stick (Android TV), pensée pour la télécommande.

**V1** : comptes Xtream Codes, listes M3U et portails Stalker (MAC), chaînes en direct par catégories, zapping haut/bas,
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

4. Installer.

**Mises à jour** : au démarrage, l'appli vérifie s'il existe une nouvelle version et propose de l'installer
(pop-up « Mise à jour disponible »). Comptes et favoris sont conservés. Bouton « Rechercher une mise à jour »
dans l'écran Comptes pour vérifier à la main. La première fois, Fire OS demande d'autoriser Momo IPTV
dans *Options pour les développeurs → Installer des applis inconnues*.

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

- **V2** : guide TV (en cours / ensuite), films et séries avec reprise de lecture — disponible (sauf guide TV pour les listes M3U)
- **V3** : replay (le portail Stalker est déjà disponible)
