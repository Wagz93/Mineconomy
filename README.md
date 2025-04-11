# 🏦 Mineconomy - Système Économique par Équipe pour Minecraft

[![Version](https://img.shields.io/badge/Version-1.0.0-blue?style=for-the-badge)](https://github.com/your-repo/Mineconomy)
[![API](https://img.shields.io/badge/API-1.20.x-green?style=for-the-badge)](https://papermc.io/)
[![Dépendances](https://img.shields.io/badge/Dépendances-Minefest,_PlaceholderAPI-orange?style=for-the-badge)](https://www.spigotmc.org/resources/placeholderapi.6245/)
[![Discord](https://img.shields.io/badge/Discord-Support-7289DA?style=for-the-badge&logo=discord)](https://discord.gg/TjgJmMzsKu) <!-- Optionnel: Ajoutez votre lien Discord -->

**Mineconomy est un plugin d'économie avancé conçu spécifiquement pour les serveurs Minecraft basés sur des équipes (comme ceux utilisant le plugin Minefest). Il centralise l'économie autour des équipes plutôt que des joueurs individuels, favorisant la collaboration et la compétition stratégique.**

---

## ✨ Fonctionnalités Principales

*   **🏦 Banques d'Équipe Multiples :** Gérez plusieurs types de banques avec des devises distinctes pour chaque équipe.
*   **🛒 Shop Principal Configurable :** Un marché permanent où les équipes vendent des ressources.
*   **📈 Prix Dynamiques :** Les prix des items peuvent s'ajuster automatiquement en fonction de l'offre et de la demande globale.
*   **📅 Shop Journalier Rotatif :** Des offres spéciales quotidiennes avec des prix potentiellement plus élevés et des limites de vente par équipe.
*   **📊 Suivi des Contributions :** Chaque vente est attribuée au joueur, permettant de suivre les meilleurs contributeurs de chaque équipe.
*   **🏆 Classements d'Équipes :** Suivez la richesse et le classement des équipes pour chaque type de banque.
*   **🏷️ Intégration PlaceholderAPI :** Affichez des informations économiques partout (scoreboard, chat, holograms...).
*   **🧩 API Développeur Robuste :** Intégrez facilement Mineconomy à vos propres plugins.
*   **⚙️ Configuration Flexible :** Personnalisez les items des shops, les prix, les interfaces GUI, et les messages.
*   **🗃️ Stockage de Données :** Utilise SQLite par défaut pour une installation facile.

---

## 📜 **Table des matières**

1. [✨ Introduction](#-introduction)
2. [🏦 Système bancaire](#-système-bancaire)
   - [Types de banques](#types-de-banques)
   - [Gestion des soldes](#gestion-des-soldes)
   - [Contributions des joueurs](#contributions-des-joueurs)
3. [🛒 Shops dynamiques](#-shops-dynamiques)
   - [Shop principal](#shop-principal)
   - [Shop journalier](#shop-journalier)
4. [⚙️ Commandes](#️-commandes)
5. [📊 Placeholders](#-placeholders)
6. [🤖 API pour développeurs](#-api-pour-développeurs)
7. [🛠️ Base de données](#️-base-de-données)
8. [❓ FAQ & dépannage](#-faq--dépannage)
9. [📞 Support](#-support)

---

## ✨ **Introduction**

**Mineconomy** est un plugin spécialement conçu pour gérer une économie basée sur les équipes dans Minecraft. Contrairement aux économies classiques centrées sur les joueurs, Mineconomy met l'accent sur le travail collectif, avec des fonctionnalités uniques telles que :

- Des **banques d'équipe** avec plusieurs devises.
- Un **shop principal** pour des ventes permanentes.
- Un **shop journalier** avec des offres limitées et des rotations automatiques.
- Un système de **prix dynamiques** pour équilibrer l'économie.
- Une **API puissante** pour les développeurs.

---

## 🏦 **Système bancaire**

### **Types de banques**

Chaque serveur peut définir plusieurs banques, chacune avec sa propre devise. Exemple de commande pour en créer une nouvelle :

```plaintext
/tba create Ressources R true
```

Cela crée une banque appelée **Ressources**, avec le symbole **R**, et la définit comme banque par défaut.

### **Gestion des soldes**

Les administrateurs peuvent modifier les soldes des équipes avec ces commandes :

```plaintext
/tba give TeamName BankName 1000   # Ajoute 1000 unités
/tba remove TeamName BankName 500  # Retire 500 unités
/tba set TeamName BankName 2000    # Définit le solde à 2000 unités
```

Les joueurs peuvent consulter les soldes de leur équipe via :

```plaintext
/money         # Affiche tous les soldes de l'équipe
/money Team2   # Affiche les soldes d'une autre équipe (avec permission)
```

### **Contributions des joueurs**

Mineconomy suit automatiquement les contributions des joueurs à leur équipe, permettant de :

- Récompenser les meilleurs contributeurs.
- Afficher des statistiques avec les placeholders.

Réinitialiser les contributions (administrateurs) :

```plaintext
/shopadmin resetcontributions [banque]
```

---

## 🛒 **Shops dynamiques**

### **Shop principal**

Un marché permanent où les joueurs peuvent vendre des ressources. Les items sont configurés dans `shop_default.yml`.

Exemple de configuration :

```yaml
items:
  DIAMOND:
    base_price: 100.0
    use_dynamic_pricing: true
    gui_slot: 10
    gui_item:
      material: DIAMOND
      name: "&b&lDiamant"
      lore:
        - "&7Un diamant brillant"
        - "&7Prix de base: &e100€"
```

#### **Prix dynamiques**

Les prix évoluent automatiquement en fonction des ventes. Exemple de configuration dans `config.yml` :

```yaml
dynamic_pricing:
  DIAMOND:
    minimum_multiplier: 0.5
    tiers:
      1:
        sold_threshold: 1000
        price_multiplier: 0.9
      2:
        sold_threshold: 5000
        price_multiplier: 0.75
```

### **Shop journalier**

Un shop aux offres spéciales, avec des rotations automatiques basées sur des fichiers comme `day_1.yml`.

Exemple de configuration pour un item limité :

```yaml
items:
  DIAMOND:
    price: 200.0
    team_sell_limit: 8
    gui_slot: 14
    gui_item:
      material: DIAMOND
      name: "&b&lDiamant du Jour"
      lore:
        - "&7Un diamant ultra-rare"
```

---

## ⚙️ **Commandes**

### **Commandes pour les joueurs**

- `/money` : Consultez les soldes de votre équipe.
- `/shop` : Accédez au shop principal.
- `/daily` : Accédez au shop journalier.

### **Commandes pour les administrateurs**

- `/tba create <NomBanque> <SymboleDevise> [par_defaut]`
- `/tba give <NomEquipe> <NomBanque> <Montant>`
- `/shopadmin reload` : Rechargez la configuration.
- `/shopadmin setday <1-7>` : Changez manuellement le jour pour le shop journalier.

---

## 📊 **Placeholders**

Mineconomy est compatible avec PlaceholderAPI. Voici quelques exemples utiles :

- `%minefestshop_team_name%` : Nom de l'équipe du joueur.
- `%minefestshop_team_balance_Minefest%` : Solde de la banque Minefest.
- `%minefestshop_daily_limit_DIAMOND%` : Limite de vente journalière pour le diamant.

---

## 🤖 **API pour développeurs**

Utilisez l'API pour intégrer Mineconomy à vos plugins. Exemple d'utilisation en Java :

```java
MinefestShopAPI api = ((Mineconomy) Bukkit.getPluginManager().getPlugin("Mineconomy")).getApi();
double balance = api.getTeamBalance("Equipe1", "Minefest");
api.addTeamBalance("Equipe1", "Minefest", 500.0);
```

---

## 🛠️ **Base de données**

Mineconomy utilise SQLite pour stocker les données. Exemple de structure de table pour les banques :

| **Colonne**       | **Type**   | **Description**                  |
|--------------------|------------|----------------------------------|
| `bank_name`        | TEXT       | Nom unique de la banque          |
| `currency_symbol`  | TEXT       | Symbole de la devise             |
| `default_bank`     | INTEGER    | 1 si banque par défaut, sinon 0  |

---

## ❓ **FAQ & dépannage**

### **Le shop journalier ne change pas automatiquement ?**

Vérifiez que votre serveur fonctionne à minuit. Sinon, forcez le changement avec :

```plaintext
/shopadmin setday <1-7>
```

### **Problèmes avec les placeholders ?**

- Assurez-vous que PlaceholderAPI est installé et à jour.
- Rechargez PlaceholderAPI avec `/papi reload`.

---

## 📞 **Support**

Pour toute assistance, contactez **Wagz_93** sur Discord.

**Merci d'utiliser Mineconomy !** 🎉
