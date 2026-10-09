package com.verax.veraxcore.storage;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * High-performance Database Migration Engine.
 * 
 * Safely transfers all Player Vaults, Staff Playtime records, Player Settings, and Verified Sessions
 * between SQLite and MySQL in either direction with zero data loss.
 * Runs completely asynchronously to avoid server lag.
 */
public class DatabaseMigrationManager {

    private final VeraxCore plugin;
    private final AtomicBoolean isMigrating = new AtomicBoolean(false);

    public DatabaseMigrationManager(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public boolean isMigrating() {
        return isMigrating.get();
    }

    /**
     * Executes database migration asynchronously.
     *
     * @param sender the command sender requesting the migration
     * @param sqliteToMysql true for SQLite -> MySQL, false for MySQL -> SQLite
     */
    public void startMigration(CommandSender sender, boolean sqliteToMysql) {
        if (!isMigrating.compareAndSet(false, true)) {
            sender.sendMessage(plugin.translateHexColorCodes("&c[✘] A database migration is already in progress! Please wait."));
            return;
        }

        String direction = sqliteToMysql ? "SQLite ➔ MySQL" : "MySQL ➔ SQLite";
        sender.sendMessage(plugin.translateHexColorCodes("&e[VeraxCore] Starting database migration (&b" + direction + "&e)..."));
        sender.sendMessage(plugin.translateHexColorCodes("&7Please do not stop or reload the server during migration."));

        String host     = plugin.getConfig().getString("mysql.host", "localhost");
        int    port     = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "veraxcore");
        String username = plugin.getConfig().getString("mysql.username", "root");
        String password = plugin.getConfig().getString("mysql.password", "");
        boolean isTr    = "tr".equalsIgnoreCase(plugin.getConfig().getString("lang", "tr"));

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection sourceConn = null;
            Connection targetConn = null;

            try {
                // 1. Establish SQLite Connection
                File dbFile = new File(plugin.getDataFolder(), "database.db");
                if (sqliteToMysql && (!dbFile.exists() || dbFile.length() == 0)) {
                    sender.sendMessage(plugin.translateHexColorCodes(isTr
                            ? "&c[✘] SQLite veritabanı dosyası (database.db) bulunamadı veya boş!"
                            : "&c[✘] SQLite database file (database.db) not found or empty!"));
                    isMigrating.set(false);
                    return;
                }

                Class.forName("org.sqlite.JDBC");
                Connection sqliteConn = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
                try (Statement st = sqliteConn.createStatement()) {
                    st.execute("PRAGMA journal_mode = DELETE;");
                    st.execute("PRAGMA synchronous = NORMAL;");
                    st.execute("PRAGMA busy_timeout = 10000;");
                } catch (SQLException ignored) {}

                // 2. Establish MySQL Connection
                String mysqlUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&autoReconnect=true&characterEncoding=utf8&useUnicode=true&serverTimezone=UTC";

                Class.forName("com.mysql.cj.jdbc.Driver");
                Connection mysqlConn = DriverManager.getConnection(mysqlUrl, username, password);

                if (sqliteToMysql) {
                    sourceConn = sqliteConn;
                    targetConn = mysqlConn;
                } else {
                    sourceConn = mysqlConn;
                    targetConn = sqliteConn;
                }

                // Ensure target tables exist
                ensureTargetTables(targetConn, sqliteToMysql);

                // Transfer data
                int vaultsCount = migrateVaults(sourceConn, targetConn, sqliteToMysql);
                int staffCount = migrateStaffPlaytime(sourceConn, targetConn, sqliteToMysql);
                int settingsCount = migratePlayerSettings(sourceConn, targetConn, sqliteToMysql);
                int sessionsCount = migrateVerifiedSessions(sourceConn, targetConn, sqliteToMysql);

                if (!sqliteToMysql) {
                    try (Statement st = sqliteConn.createStatement()) {
                        st.execute("PRAGMA journal_mode = DELETE;");
                    } catch (Throwable ignored) {}
                }

                // Success notification
                sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
                sender.sendMessage(plugin.translateHexColorCodes(isTr
                        ? "  &a[✔] VeraxCore Veritabanı Aktarımı Başarıyla Tamamlandı!"
                        : "  &a[✔] VeraxCore Database Migration Completed Successfully!"));
                sender.sendMessage(plugin.translateHexColorCodes(isTr
                        ? "  &7Yön: &b" + direction
                        : "  &7Direction: &b" + direction));
                sender.sendMessage(plugin.translateHexColorCodes("  &7• " + (isTr ? "Kasa Kayıtları (PV):" : "Player Vaults (PV):") + " &f" + vaultsCount));
                sender.sendMessage(plugin.translateHexColorCodes("  &7• " + (isTr ? "Yetkili Aktiflik Verileri:" : "Staff Playtime Records:") + " &f" + staffCount));
                sender.sendMessage(plugin.translateHexColorCodes("  &7• " + (isTr ? "Oyuncu Ayarları:" : "Player Settings:") + " &f" + settingsCount));
                sender.sendMessage(plugin.translateHexColorCodes("  &7• " + (isTr ? "Doğrulanmış Oturumlar:" : "Verified Sessions:") + " &f" + sessionsCount));
                sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));

                plugin.getLogger().info("[Migration] Finished " + direction + " (Vaults: " + vaultsCount + ", Staff: " + staffCount + ", Settings: " + settingsCount + ", Sessions: " + sessionsCount + ")");

            } catch (Exception e) {
                DatabaseDiagnostic.DiagnosticResult diag = DatabaseDiagnostic.diagnose(
                        e, host, port, database, username);

                // User / sender message
                sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));
                sender.sendMessage(plugin.translateHexColorCodes("  &c[✘] Database migration failed!"));
                sender.sendMessage(plugin.translateHexColorCodes("  &eReason: &f" + diag.getShortReason()));
                sender.sendMessage(plugin.translateHexColorCodes("  &7Solution: &f" + diag.getSolution()));
                sender.sendMessage(plugin.translateHexColorCodes("&8&m-----------------------------------------------------"));

                // Clean console output
                plugin.getLogger().severe("[Migration] ✘ Database migration failed!");
                plugin.getLogger().severe("[Migration]   Reason: " + diag.getShortReason());
                plugin.getLogger().severe("[Migration]   Solution: " + diag.getSolution());
                if (diag.getTechnicalDetails() != null && !diag.getTechnicalDetails().isEmpty()) {
                    plugin.getLogger().severe("[Migration]   Details: " + diag.getTechnicalDetails());
                }

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[Migration] Debug mode is ENABLED in config.yml. Printing full stack trace:");
                    e.printStackTrace();
                } else {
                    plugin.getLogger().info("[Migration] Hint: Enable 'debug: true' in config.yml for full Java stack trace.");
                }
            } finally {
                if (sourceConn != null) {
                    try { sourceConn.close(); } catch (SQLException ignored) {}
                }
                if (targetConn != null) {
                    try { targetConn.close(); } catch (SQLException ignored) {}
                }
                try {
                    File wal = new File(plugin.getDataFolder(), "database.db-wal");
                    if (wal.exists()) wal.delete();
                    File shm = new File(plugin.getDataFolder(), "database.db-shm");
                    if (shm.exists()) shm.delete();
                } catch (Exception ignored) {}
                isMigrating.set(false);
            }
        });
    }

    private void ensureTargetTables(Connection conn, boolean isTargetMysql) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_playtime (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "username VARCHAR(16), " +
                    "daily INT DEFAULT 0, " +
                    "weekly INT DEFAULT 0, " +
                    "monthly INT DEFAULT 0, " +
                    "total INT DEFAULT 0" +
                    (isTargetMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"));

            st.executeUpdate("CREATE TABLE IF NOT EXISTS player_vaults (" +
                    "uuid VARCHAR(36), " +
                    "vault_id INT, " +
                    "items " + (isTargetMysql ? "MEDIUMTEXT" : "TEXT") + ", " +
                    "PRIMARY KEY (uuid, vault_id)" +
                    (isTargetMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"));

            st.executeUpdate("CREATE TABLE IF NOT EXISTS player_settings (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "trade_enabled INT DEFAULT 1, " +
                    "announcement_enabled INT DEFAULT 1" +
                    (isTargetMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"));

            st.executeUpdate("CREATE TABLE IF NOT EXISTS verified_sessions (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "ip VARCHAR(45), " +
                    "verified_at BIGINT, " +
                    "expires_at BIGINT" +
                    (isTargetMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"));
        }
    }

    private int migrateVaults(Connection source, Connection target, boolean isTargetMysql) throws SQLException {
        int count = 0;
        String query = "SELECT uuid, vault_id, items FROM player_vaults;";
        String insert = isTargetMysql
                ? "INSERT INTO player_vaults (uuid, vault_id, items) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE items = ?;"
                : "INSERT INTO player_vaults (uuid, vault_id, items) VALUES (?, ?, ?) ON CONFLICT(uuid, vault_id) DO UPDATE SET items = ?;";

        try (PreparedStatement psQuery = source.prepareStatement(query);
             ResultSet rs = psQuery.executeQuery();
             PreparedStatement psInsert = target.prepareStatement(insert)) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                int vaultId = rs.getInt("vault_id");
                String items = rs.getString("items");

                psInsert.setString(1, uuid);
                psInsert.setInt(2, vaultId);
                psInsert.setString(3, items);
                psInsert.setString(4, items);
                psInsert.addBatch();
                count++;

                if (count % 100 == 0) {
                    psInsert.executeBatch();
                }
            }
            psInsert.executeBatch();
        }
        return count;
    }

    private int migrateStaffPlaytime(Connection source, Connection target, boolean isTargetMysql) throws SQLException {
        int count = 0;
        String sourceTable = "staff_playtime";
        boolean legacy = false;
        try (ResultSet rs = source.getMetaData().getTables(null, null, "staff_playtime", null)) {
            if (!rs.next()) {
                legacy = true;
                sourceTable = "yetkili_aktiflik";
            }
        } catch (Exception ignored) {}

        String query = legacy
                ? "SELECT uuid, username, gunluk, haftalik, aylik, toplam FROM " + sourceTable + ";"
                : "SELECT uuid, username, daily, weekly, monthly, total FROM " + sourceTable + ";";

        String insert = isTargetMysql
                ? "INSERT INTO staff_playtime (uuid, username, daily, weekly, monthly, total) VALUES (?, ?, ?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE username = ?, daily = ?, weekly = ?, monthly = ?, total = ?;"
                : "INSERT INTO staff_playtime (uuid, username, daily, weekly, monthly, total) VALUES (?, ?, ?, ?, ?, ?) " +
                  "ON CONFLICT(uuid) DO UPDATE SET username = ?, daily = ?, weekly = ?, monthly = ?, total = ?;";

        try (PreparedStatement psQuery = source.prepareStatement(query);
             ResultSet rs = psQuery.executeQuery();
             PreparedStatement psInsert = target.prepareStatement(insert)) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String username = rs.getString("username");
                int daily = legacy ? rs.getInt("gunluk") : rs.getInt("daily");
                int weekly = legacy ? rs.getInt("haftalik") : rs.getInt("weekly");
                int monthly = legacy ? rs.getInt("aylik") : rs.getInt("monthly");
                int total = legacy ? rs.getInt("toplam") : rs.getInt("total");

                psInsert.setString(1, uuid);
                psInsert.setString(2, username);
                psInsert.setInt(3, daily);
                psInsert.setInt(4, weekly);
                psInsert.setInt(5, monthly);
                psInsert.setInt(6, total);

                psInsert.setString(7, username);
                psInsert.setInt(8, daily);
                psInsert.setInt(9, weekly);
                psInsert.setInt(10, monthly);
                psInsert.setInt(11, total);

                psInsert.addBatch();
                count++;

                if (count % 100 == 0) {
                    psInsert.executeBatch();
                }
            }
            psInsert.executeBatch();
        }
        return count;
    }

    private int migratePlayerSettings(Connection source, Connection target, boolean isTargetMysql) throws SQLException {
        int count = 0;
        String query = "SELECT uuid, trade_enabled, announcement_enabled FROM player_settings;";
        String insert = isTargetMysql
                ? "INSERT INTO player_settings (uuid, trade_enabled, announcement_enabled) VALUES (?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE trade_enabled = ?, announcement_enabled = ?;"
                : "INSERT INTO player_settings (uuid, trade_enabled, announcement_enabled) VALUES (?, ?, ?) " +
                  "ON CONFLICT(uuid) DO UPDATE SET trade_enabled = ?, announcement_enabled = ?;";

        try (PreparedStatement psQuery = source.prepareStatement(query);
             ResultSet rs = psQuery.executeQuery();
             PreparedStatement psInsert = target.prepareStatement(insert)) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                int trade = rs.getInt("trade_enabled");
                int ann = rs.getInt("announcement_enabled");

                psInsert.setString(1, uuid);
                psInsert.setInt(2, trade);
                psInsert.setInt(3, ann);
                psInsert.setInt(4, trade);
                psInsert.setInt(5, ann);

                psInsert.addBatch();
                count++;

                if (count % 100 == 0) {
                    psInsert.executeBatch();
                }
            }
            psInsert.executeBatch();
        }
        return count;
    }

    private int migrateVerifiedSessions(Connection source, Connection target, boolean isTargetMysql) throws SQLException {
        int count = 0;
        String query = "SELECT uuid, ip, verified_at, expires_at FROM verified_sessions;";
        String insert = isTargetMysql
                ? "INSERT INTO verified_sessions (uuid, ip, verified_at, expires_at) VALUES (?, ?, ?, ?) " +
                  "ON DUPLICATE KEY UPDATE ip = ?, verified_at = ?, expires_at = ?;"
                : "INSERT INTO verified_sessions (uuid, ip, verified_at, expires_at) VALUES (?, ?, ?, ?) " +
                  "ON CONFLICT(uuid) DO UPDATE SET ip = ?, verified_at = ?, expires_at = ?;";

        try (PreparedStatement psQuery = source.prepareStatement(query);
             ResultSet rs = psQuery.executeQuery();
             PreparedStatement psInsert = target.prepareStatement(insert)) {

            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String ip = rs.getString("ip");
                long verifiedAt = rs.getLong("verified_at");
                long expiresAt = rs.getLong("expires_at");

                psInsert.setString(1, uuid);
                psInsert.setString(2, ip);
                psInsert.setLong(3, verifiedAt);
                psInsert.setLong(4, expiresAt);

                psInsert.setString(5, ip);
                psInsert.setLong(6, verifiedAt);
                psInsert.setLong(7, expiresAt);

                psInsert.addBatch();
                count++;

                if (count % 100 == 0) {
                    psInsert.executeBatch();
                }
            }
            psInsert.executeBatch();
        }
        return count;
    }
}
