# VeraxCore

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.20.4+-brightgreen?style=for-the-badge&logo=minecraft" alt="Minecraft Version" />
  <img src="https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk" alt="Java Version" />
  <img src="https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur-blue?style=for-the-badge" alt="Platform" />
  <img src="https://img.shields.io/badge/Version-v3.5.1-informational?style=for-the-badge" alt="Version" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License" />
  <a href="https://www.spigotmc.org/resources/138126/"><img src="https://img.shields.io/badge/SpigotMC-Resource%20138126-FF8C00?style=for-the-badge&logo=spigotmc" alt="SpigotMC" /></a>
</p>

```
 __      __ ______ _____         __   __
 \ \    / /|  ____|  __ \ /\     \ \ / /
  \ \  / / | |__  | |__) /  \     \ V / 
   \ \/ /  |  __| |  _  / /\ \     > <  
    \  /   | |____| | \ \ ____ \  / . \ 
     \/    |______|_|  \_/_/  \_//_/ \_\
```

**VeraxCore** is a modern, high-performance, modular, and all-in-one core management plugin developed from scratch for Minecraft servers running on **Paper / Purpur** (Java 17+, 1.20.4+).

It consolidates dozens of essential server features—ranging from staff activity tracking and two-factor security verification (2FA) to player vaults (PV), interactive trade menus, chat moderation, maintenance mode, and scheduled restarts—into a single, highly optimized, and lightweight package.

---

## 📑 Table of Contents
- [Features & Modules](#-features--modules)
- [Requirements & Compatibility](#-requirements--compatibility)
- [Commands & Permissions](#-commands--permissions)
- [Installation](#-installation)
- [Configuration (config.yml)](#-configuration-configyml)
- [Discord Webhook Integration](#-discord-webhook-integration)
- [Database Support](#-database-support)
- [Building from Source](#-building-from-source)
- [License](#-license)

---

## 🚀 Features & Modules

Every single module can be enabled or disabled individually within `config.yml` under the `modules:` section:

### ⏱️ Staff Playtime & Activity Tracking
* **Real-Time Tracking:** Accurately records active in-game playtime for all authorized staff members.
* **AFK Detection:** Seamlessly integrates with EssentialsX to pause activity counters while staff members are AFK.
* **Target Quota System:** Compares recorded playtime against configurable targets (`target-seconds`) to show whether staff met their activity quota (`✅` or `❌`).
* **Automated Discord Reports:** Automatically dispatches rich Discord Embed reports at midnight (00:00) daily, weekly (every Monday), and monthly (1st of every month), with optional automatic playtime resets.
* **In-Game Leaderboards:** View overall or real-time staff rankings directly within the game.

### 🛡️ 2FA & Security Verification
* Automatically freezes specified admin/critical accounts upon server login.
* Generates a one-time verification code and sends an alert including the player's IP address directly to your private Discord Webhook.
* Restricts player movement, commands, and chat until verification is completed.
* The player safely unlocks their account using `/verify <code>` (or `/doğrula <code>`).

### 📦 Advanced Player Vaults (PV)
* Async storage for virtual player vaults/chests, ensuring zero TPS drops.
* **LuckPerms Integration:**
  * Vault count permission: `veraxcore.pv.amount.<number>`
  * Vault slot size permission: `veraxcore.pv.slot.<number>` (e.g., 9, 18, 27, 36, 45, 54)
* **Administrative Inspection:** Staff can view and modify both online and offline player vaults in real time using `/pvadmin show <player> <vault_number>`.

### 💬 Chat Moderation & Word Actions
* **Chat Management:** Instantly lock, unlock, or clear chat (`/chat open|close|clear` or `/sohbet`).
* **Anti-Spam & Cooldowns:** Built-in configurable cooldowns prevent chat flooding and repetitive spam.
* **Automatic Word Actions:** Filters banned words, slurs, or advertising links with configurable actions (cancel message, warn player, and execute automated console commands like mutes).
* **Discord Moderation Audit:** Logs chat clear, lock, and unlock events to a dedicated Discord webhook.

### 👥 Staff Chat Channel
* An isolated, private communication channel for server staff members (`/sc <message>`, `/ys`, `/staffchat`).
* Features optional sound alerts upon message delivery.
* Configurable and localized console logging for server administrators (`staff-chat.log-to-console`).

### 🚨 Player Reporting System
* Allows players to report rule-breakers via `/report <player> <reason>` (or `/rapor`).
* Immediately alerts online staff members in-game and sends a Discord webhook embed complete with the reporter's and reported player's avatar thumbnails (via Minotar API).
* Includes configurable cooldowns to prevent report spam.

### 🤝 Interactive Trade GUI & Broadcast System
* Players can broadcast trade advertisements server-wide using `/trade <message>` (or `/ticaret`).
* Trade ads are displayed via a custom BossBar, sound alert, and chat broadcast.
* Players can open the interactive GUI and click player heads to initiate trades.
* Players who prefer not to receive trade notifications can toggle them off using `/trade off`.

### 🚩 Spawn & First-Join System
* Separate spawn configurations for returning players (`/spawn`) and first-time players (`/spawn setfirst`).
* Configurable countdown teleportation with movement/damage cancellation and custom sound effects.
* Permission bypass support for staff/VIPs.

### 🔧 Server Maintenance Mode
* Toggle server maintenance with `/maintenance <time|open|close>` (or `/bakım`).
* Automatically kicks unauthorized players and changes the server ping list (MOTD) to reflect maintenance status.
* Displays a live countdown BossBar to inform players.
* Configured staff ranks (`allowed-ranks`) bypass maintenance automatically.

### 🔄 Scheduled Automatic Server Restart
* Triggers automatic server restarts at specified times (e.g., `04:00`, `16:00`) in the background without needing external cron jobs.
* Broadcasts an animated countdown BossBar prior to restart (e.g., 30 minutes in advance).

### 📢 Announcements & Auto-Announcer
* **Title Announcements:** Broadcast custom title & subtitle messages across players' screens with configurable fade-in, stay, and fade-out timings (`/announcement <message>` or `/duyuru`).
* **Periodic Auto-Announcements:** Send recurring messages (Discord links, store info, tips) on custom intervals with sound effects. Players can opt out using `/autoannouncement off` (or `/otoduyuru`).

### 🗑️ Trash Bin
* Opens a virtual trash disposal GUI via `/trash` (or `/çöp`) that permanently removes discarded items when closed.

### 🌐 Multi-Language Support
* Out-of-the-box translations included:
  * 🇹🇷 **Turkish** (`lang/tr.yml`)
  * 🇬🇧 **English** (`lang/en.yml`)
  * 🇩🇪 **German** (`lang/de.yml`)
* Full MiniMessage and Hex color code formatting support across all messages and prefixes.

---

## 🛠️ Requirements & Compatibility

| Requirement | Supported Version | Notes |
| :--- | :--- | :--- |
| **Java** | Java 17 or higher | Required by modern Paper builds |
| **Server Software** | PaperMC / Purpur 1.20.4+ | Fully compatible with Spigot 1.16+ API |
| **LuckPerms** *(Optional)* | v5.4+ | Recommended for rank & player vault limits |
| **EssentialsX** *(Optional)* | v2.20+ | Recommended for accurate staff AFK detection |

---

## 💻 Commands & Permissions

### Commands

| Command | Aliases | Description | Permission |
| :--- | :--- | :--- | :--- |
| `/veraxcore [reload/info/modules/help]` | `/vc` | Main admin management command | `veraxcore.admin` |
| `/yetkilisüre [reset/log]` | `/admintime` | Displays and manages staff activity times | `veraxcore.stafftime.view` |
| `/ys <message>` | `/sc`, `/staffchat` | Sends a message to the staff chat channel | `veraxcore.staffchat` |
| `/sohbet <open/close/clear>` | `/chat` | Opens, locks, or clears chat | `veraxcore.chat.lock/open/clear` |
| `/duyuru <message>` | `/announcement` | Broadcasts a title announcement to all players | `veraxcore.announcement` |
| `/rapor <player> <reason>` | `/report` | Submits a player report to staff and Discord | *(Available to everyone)* |
| `/ticaret [message/on/off]` | `/trade` | Posts a trade announcement or opens GUI | `veraxcore.trade` |
| `/doğrula <code>` | `/verify` | Completes two-factor staff verification | *(Accounts under verification)* |
| `/pv [vault_number]` | `/vault`, `/playervault` | Opens personal player vault | `veraxcore.pv.amount.<n>` |
| `/pvadmin show <player> [vault_number]` | `/pva`, `/vaultadmin` | Views and edits another player's vault | `veraxcore.pv.admin` |
| `/spawn [set/setfirst/remove]` | - | Teleports to spawn or configures locations | `veraxcore.spawn.set` |
| `/çöp` | `/trash` | Opens the disposal trash bin | `veraxcore.trash` *(optional)* |
| `/otoduyuru <on/off>` | `/autoannouncement` | Toggles automatic chat announcements | *(Available to everyone)* |
| `/bakım [duration/on/off]` | `/maintenance` | Controls server maintenance mode | `veraxcore.maintenance` |

### Permissions

```yaml
veraxcore.admin: Grants full access to all VeraxCore commands and features (Default: OP)
veraxcore.chat.talk: Permission to talk when chat is locked
veraxcore.chat.open: Permission to unlock the chat
veraxcore.chat.lock: Permission to lock the chat
veraxcore.chat.clear: Permission to clear the chat
veraxcore.announcement: Permission to broadcast title announcements
veraxcore.stafftime.view: Permission to view staff activity playtimes
veraxcore.stafftime.reset: Permission to reset staff playtime records (Default: OP)
veraxcore.stafftime.sendlog: Permission to manually send Discord activity reports (Default: OP)
veraxcore.staffchat: Permission to read and speak in staff chat
veraxcore.spawn.set: Permission to set the normal spawn point (Default: OP)
veraxcore.spawn.setfirst: Permission to set the first-join spawn point (Default: OP)
veraxcore.spawn.remove: Permission to delete spawn locations (Default: OP)
veraxcore.spawn.bypass: Permission to bypass teleport countdown delays
veraxcore.report: Permission to receive and manage player reports
veraxcore.trade: Permission to access the trade system
veraxcore.trash: Permission to open the trash bin
veraxcore.pv.admin: Permission to inspect and modify player vaults (Default: OP)
veraxcore.pv.amount.<number>: Maximum number of vaults allowed (e.g. veraxcore.pv.amount.5)
veraxcore.pv.slot.<number>: Capacity/slots of vaults (e.g. veraxcore.pv.slot.54)
veraxcore.maintenance: Permission to enable/disable maintenance mode (Default: OP)
veraxcore.maintenance.bypass: Permission to join and stay online during maintenance (Default: OP)

```

---


## 💸 Trade System Photos
<img width="431" height="388" alt="Trade Menu" src="https://github.com/user-attachments/assets/af090707-504d-41fd-ad69-317d90226b46" />
<img width="696" height="136" alt="Trade Message" src="https://github.com/user-attachments/assets/8f9ecdf1-7ac1-41de-a6bb-1c4ca09f5643" />

## 🔰 Staff Chat System Photo
<img width="503" height="93" alt="Staff Chat" src="https://github.com/user-attachments/assets/cfc9bf8f-47b6-4a41-8168-083600103093" />

## 💬 Chat Management System Photos
<img width="500" height="31" alt="Chat Open" src="https://github.com/user-attachments/assets/e7d95ce6-ce35-4587-be7b-b477af67e5e7" />
<img width="442" height="32" alt="Chat Close" src="https://github.com/user-attachments/assets/fa497c06-3bc4-457f-9cd8-180c71ba3dcc" />
<img width="410" height="34" alt="Chat Clear" src="https://github.com/user-attachments/assets/73b17f5c-4221-4abd-9fe8-d6978ee2b799" />

## 🚧 Maintenance System Photos
<img width="1920" height="1009" alt="Maintenance Countdown" src="https://github.com/user-attachments/assets/fddccd44-2f31-4c36-9fba-840d8a88dd0a" />
<img width="1920" height="1009" alt="Maintenance Started" src="https://github.com/user-attachments/assets/2aaccc17-187e-4677-8964-f85e048683b0" />
<img width="1362" height="683" alt="Maintenance Kick Screen" src="https://github.com/user-attachments/assets/785ab888-061f-4ee0-868e-b8254d97edb3" />

## 📦 Installation

1. Download the latest `VeraxCore.jar` release from [SpigotMC](https://www.spigotmc.org/resources/138126/) or build it from source.
2. Place the JAR file into your server's `plugins/` directory.
3. Start or restart your server (a full restart is recommended over `/reload`).
4. Navigate to `plugins/VeraxCore/config.yml` and configure your preferred language, database credentials, and Discord webhooks.
5. Apply any configuration updates in-game using `/veraxcore reload`.

---

## ⚙️ Configuration (`config.yml`)

```yaml
# Language options: tr (Turkish), en (English), de (German)
lang: "en"

# Storage type: SQLITE or MYSQL
storage-type: SQLITE

# Enable or disable individual modules
modules:
  trade: true
  staff-time: true
  staff-chat: true
  report: true
  spawn: true
  chat: true
  announcement: true
  verification: true
  trash: true
  auto-announcement: true
  pv: true
  word-actions: true
  maintenance: true
  auto-restart: true

# Discord Webhook Endpoints
webhook-url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
chat-log-webhook-url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
report-webhook-url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
trade-webhook-url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
verification-webhook-url: "YOUR_DISCORD_WEBHOOK_URL_HERE"
```

---

## 🔗 Discord Webhook Integration

VeraxCore seamlessly connects your in-game server events with your Discord community:
* **Staff Activity Reports:** Automated daily/weekly/monthly embeds detailing active playtime, quotas, and staff status.
* **Chat Audits:** Complete audit trails of when chat was locked, cleared, or unlocked.
* **Player Reports:** Real-time embeds including the reporter, reported player, reason, and player avatar heads.
* **Trade Announcements:** Broadcasts player trade postings to dedicated Discord channels.
* **Security Verification:** Instant alerts with player IP addresses and 2FA authentication codes.

---

## 🗄️ Database Support

VeraxCore offers two robust data storage options:
1. **SQLite (Default):** Zero configuration required. Lightweight, local file-based database (`VeraxCore.db`) ideal for standalone servers.
2. **MySQL:** Built with **HikariCP** high-performance connection pooling. Delivers fast, resilient, and asynchronous database queries suitable for networks and high-traffic servers.

---

## 🔨 Building from Source

To compile VeraxCore yourself, ensure you have **JDK 17** and **Apache Maven** installed:

```bash
# Clone the repository and compile
mvn clean package
```

The compiled shaded artifact will be generated in `target/VeraxCore-3.5.1.jar`.


---

## 📄 License

This project is licensed under the **[MIT License](LICENSE)**. See the [LICENSE](LICENSE) file for full details.

---

<p align="center">
  <b>Developer:</b> <a href="https://www.spigotmc.org/resources/138126/">VeraxDev</a> • <b>Version:</b> 3.5.2
</p>








