package com.verax.veraxcore.managers;

import com.verax.veraxcore.VeraxCore;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.verax.veraxcore.storage.DatabaseDiagnostic;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {

    private static final byte MAGIC_PAPER_NBT = (byte) 0xFE;

    private final VeraxCore plugin;
    private final Object sqliteLock = new Object();
    private Connection connection;
    private HikariDataSource hikariDataSource;
    private boolean isMysql = false;

    public DatabaseManager(VeraxCore plugin) {
        this.plugin = plugin;
    }

    public void setupDatabase() {
        String type = plugin.getConfig().getString("storage-type", "SQLITE").toUpperCase(Locale.ROOT);
        isMysql = type.equals("MYSQL");

        if (isMysql) {
            connectMySQL();
        } else {
            connectSQLite();
        }

        if (connection != null) {
            createTables();
        }
    }

    public synchronized Connection getConnection() {
        try {
            if (isMysql) {
                if (hikariDataSource == null || hikariDataSource.isClosed()) {
                    connectMySQL();
                }
                if (connection == null || connection.isClosed() || !connection.isValid(1)) {
                    if (hikariDataSource != null && !hikariDataSource.isClosed()) {
                        connection = hikariDataSource.getConnection();
                    }
                }
                return connection;
            } else {
                if (connection == null || connection.isClosed() || !connection.isValid(1)) {
                    connectSQLite();
                }
                return connection;
            }
        } catch (Exception e) {
            if (isMysql) {
                connectMySQL();
            } else {
                connectSQLite();
            }
            return connection;
        }
    }

    // ─── SQLite Connection ───────────────────────────────────────────────

    private void connectSQLite() {
        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }

            // Clear org.sqlite.tmpdir if it was previously set
            if (System.getProperty("org.sqlite.tmpdir") != null && System.getProperty("org.sqlite.tmpdir").contains(".sqlite")) {
                System.clearProperty("org.sqlite.tmpdir");
            }

            // Clean up old temporary directory if it exists
            File oldTempDir = new File(plugin.getDataFolder(), ".sqlite");
            if (oldTempDir.exists()) {
                deleteDirectoryRecursively(oldTempDir);
            }

            File dbFile = new File(plugin.getDataFolder(), "database.db");

            plugin.getLogger().info("Connecting to local SQLite database (database.db)...");

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            if (connection == null) {
                plugin.getLogger().severe("DATABASE CONNECTION FAILED! database.db file could not be created.");
                return;
            }

            try (java.sql.Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode = DELETE;");
                st.execute("PRAGMA synchronous = NORMAL;");
                st.execute("PRAGMA busy_timeout = 10000;");
            } catch (SQLException ignored) {}

            cleanWalShmFiles();

            plugin.getLogger().info("SQLite database connected successfully (journal mode: DELETE, WAL/SHM disabled) and tables checked.");

        } catch (UnsatisfiedLinkError e) {
            plugin.getLogger().severe("==================================================================");
            plugin.getLogger().severe("[VeraxCore] SQLite Native Library (JNI) Load Error!");
            plugin.getLogger().severe("ERROR: SQLite native library (DLL/SO) could not be loaded.");
            plugin.getLogger().severe("CAUSES AND SOLUTIONS:");
            plugin.getLogger().severe("1. If '/reload' or PlugMan was used: Java cannot reload native DLL libraries. Please fully stop and restart the server.");
            plugin.getLogger().severe("2. Temp directory locked or conflicting: Clean old sqlite*.dll files in your %TEMP% folder or reboot the machine.");
            plugin.getLogger().severe("3. You can also configure storage-type: MYSQL in config.yml.");
            plugin.getLogger().severe("==================================================================");
            e.printStackTrace();
        } catch (Exception e) {
            plugin.getLogger().severe("ERROR connecting to SQLite database:");
            e.printStackTrace();
        }
    }

    // ─── MySQL Connection (HikariCP Connection Pool) ─────────────────────

    private void connectMySQL() {
        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            try {
                hikariDataSource.close();
            } catch (Exception ignored) {}
        }

        String host     = plugin.getConfig().getString("mysql.host", "localhost");
        int    port     = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "veraxcore");
        String username = plugin.getConfig().getString("mysql.username", "root");
        String password = plugin.getConfig().getString("mysql.password", "");

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false"
                + "&autoReconnect=true"
                + "&characterEncoding=utf8"
                + "&useUnicode=true"
                + "&serverTimezone=UTC";

        plugin.getLogger().info("Connecting to MySQL server via HikariCP pool → " + host + ":" + port + "/" + database);

        try {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(url);
            hikariConfig.setUsername(username);
            hikariConfig.setPassword(password);
            hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
            hikariConfig.setPoolName("VeraxCore-HikariPool");
            hikariConfig.setMaximumPoolSize(10);
            hikariConfig.setMinimumIdle(2);
            hikariConfig.setIdleTimeout(60000);
            hikariConfig.setConnectionTimeout(10000);
            hikariConfig.setMaxLifetime(1800000);

            // Extra properties for MySQL performance
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            hikariConfig.addDataSourceProperty("useServerPrepStmts", "true");

            this.hikariDataSource = new HikariDataSource(hikariConfig);
            this.connection = this.hikariDataSource.getConnection();

            if (this.connection != null && !this.connection.isClosed()) {
                plugin.getLogger().info("[✔] MySQL connected successfully using HikariCP connection pool!");
            }
        } catch (Throwable e) {
            DatabaseDiagnostic.DiagnosticResult diag = DatabaseDiagnostic.diagnose(
                    e, host, port, database, username);

            plugin.getLogger().severe("[✘] Failed to connect to MySQL database!");
            plugin.getLogger().severe("  Reason: " + diag.getShortReason());
            plugin.getLogger().severe("  Solution: " + diag.getSolution());
            if (diag.getTechnicalDetails() != null && !diag.getTechnicalDetails().isEmpty()) {
                plugin.getLogger().severe("  Details: " + diag.getTechnicalDetails());
            }

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[Database] Debug mode is ENABLED. Printing full stack trace:");
                e.printStackTrace();
            } else {
                plugin.getLogger().info("[Database] Hint: Enable 'debug: true' in config.yml for full Java stack trace.");
            }
            connection = null;
        }
    }

    // ─── Table Creation ──────────────────────────────────────────────────

    private void createTables() {
        try {
            // Check and auto-migrate legacy Turkish 'yetkili_aktiflik' table to English 'staff_playtime'
            try {
                java.sql.DatabaseMetaData meta = connection.getMetaData();
                boolean legacyExists = false;
                try (ResultSet rs = meta.getTables(null, null, "yetkili_aktiflik", null)) {
                    if (rs.next()) legacyExists = true;
                }
                if (!legacyExists) {
                    try (ResultSet rs = meta.getTables(null, null, "YETKILI_AKTIFLIK", null)) {
                        if (rs.next()) legacyExists = true;
                    }
                }

                if (legacyExists) {
                    plugin.getLogger().info("[Database] Found legacy 'yetkili_aktiflik' table. Migrating to English 'staff_playtime'...");
                    try (java.sql.Statement st = connection.createStatement()) {
                        st.executeUpdate("CREATE TABLE IF NOT EXISTS staff_playtime (" +
                                "uuid VARCHAR(36) PRIMARY KEY, " +
                                "username VARCHAR(16), " +
                                "daily INT DEFAULT 0, " +
                                "weekly INT DEFAULT 0, " +
                                "monthly INT DEFAULT 0, " +
                                "total INT DEFAULT 0" +
                                (isMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"));

                        st.executeUpdate("INSERT " + (isMysql ? "IGNORE " : "OR IGNORE ") + "INTO staff_playtime (uuid, username, daily, weekly, monthly, total) " +
                                "SELECT uuid, username, gunluk, haftalik, aylik, toplam FROM yetkili_aktiflik;");

                        st.executeUpdate("DROP TABLE yetkili_aktiflik;");
                        plugin.getLogger().info("[Database] ✔ Successfully migrated legacy table to English 'staff_playtime'!");
                    }
                }
            } catch (Exception ex) {
                plugin.getLogger().warning("[Database] Notice checking legacy table: " + ex.getMessage());
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS staff_playtime (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "username VARCHAR(16), " +
                            "daily INT DEFAULT 0, " +
                            "weekly INT DEFAULT 0, " +
                            "monthly INT DEFAULT 0, " +
                            "total INT DEFAULT 0" +
                            (isMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"))) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS player_vaults (" +
                            "uuid VARCHAR(36), " +
                            "vault_id INT, " +
                            "items " + (isMysql ? "MEDIUMTEXT" : "TEXT") + ", " +
                            "PRIMARY KEY (uuid, vault_id)" +
                            (isMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"))) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS player_settings (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "trade_enabled INT DEFAULT 1, " +
                            "announcement_enabled INT DEFAULT 1" +
                            (isMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"))) {
                ps.executeUpdate();
            }

            try (PreparedStatement ps = connection.prepareStatement(
                    "CREATE TABLE IF NOT EXISTS verified_sessions (" +
                            "uuid VARCHAR(36) PRIMARY KEY, " +
                            "ip VARCHAR(45), " +
                            "verified_at BIGINT, " +
                            "expires_at BIGINT" +
                            (isMysql ? ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;" : ");"))) {
                ps.executeUpdate();
            }

            plugin.getLogger().info("Tables checked/created (100% English schema).");

        } catch (SQLException e) {
            plugin.getLogger().severe("ERROR creating table: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ─── SQL Syntax Helper ───────────────────────────────────────────────

    /**
     * SQLite: ON CONFLICT(col) DO UPDATE SET col = val
     * MySQL:  ON DUPLICATE KEY UPDATE col = val
     */
    private String upsertSuffix(String conflictCol, String setClause) {
        if (isMysql) {
            return " ON DUPLICATE KEY UPDATE " + setClause + ";";
        } else {
            return " ON CONFLICT(" + conflictCol + ") DO UPDATE SET " + setClause + ";";
        }
    }

    private String upsertSuffix2(String conflictCols, String setClause) {
        if (isMysql) {
            return " ON DUPLICATE KEY UPDATE " + setClause + ";";
        } else {
            return " ON CONFLICT(" + conflictCols + ") DO UPDATE SET " + setClause + ";";
        }
    }

    // ─── Close Database ──────────────────────────────────────────────────

    public void closeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                if (!isMysql) {
                    try (java.sql.Statement st = connection.createStatement()) {
                        st.execute("PRAGMA journal_mode = DELETE;");
                    } catch (Throwable ignored) {}
                }
                connection.close();
            }
        } catch (SQLException ignored) {}

        if (!isMysql) {
            cleanWalShmFiles();
        }

        if (hikariDataSource != null && !hikariDataSource.isClosed()) {
            try {
                hikariDataSource.close();
                plugin.getLogger().info("HikariCP connection pool closed.");
            } catch (Exception ignored) {}
        }

        plugin.getLogger().info((isMysql ? "MySQL" : "SQLite") + " database connection closed.");
    }

    /**
     * Deletes residual SQLite WAL/SHM files to prevent them from persisting on disk.
     */
    public void cleanWalShmFiles() {
        try {
            File walFile = new File(plugin.getDataFolder(), "database.db-wal");
            if (walFile.exists()) {
                walFile.delete();
            }
            File shmFile = new File(plugin.getDataFolder(), "database.db-shm");
            if (shmFile.exists()) {
                shmFile.delete();
            }
        } catch (Exception ignored) {}
    }

    public boolean isHikariActive() {
        return isMysql && hikariDataSource != null && !hikariDataSource.isClosed();
    }

    public String getStorageType() {
        if (isMysql) {
            return isHikariActive() ? "MySQL (HikariCP)" : "MySQL";
        }
        return "SQLite";
    }

    @FunctionalInterface
    public interface SqlConsumer {
        void accept(Connection conn) throws SQLException;
    }

    @FunctionalInterface
    public interface SqlFunction<R> {
        R apply(Connection conn) throws SQLException;
    }

    public void execute(SqlConsumer action) {
        if (isMysql) {
            try (Connection conn = (hikariDataSource != null && !hikariDataSource.isClosed()) 
                    ? hikariDataSource.getConnection() : getConnection()) {
                if (conn != null) {
                    action.accept(conn);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Database error (MySQL): " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            synchronized (sqliteLock) {
                Connection conn = getConnection();
                if (conn != null) {
                    try {
                        action.accept(conn);
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Database error (SQLite): " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public <R> R query(SqlFunction<R> queryAction, R defaultValue) {
        if (isMysql) {
            try (Connection conn = (hikariDataSource != null && !hikariDataSource.isClosed()) 
                    ? hikariDataSource.getConnection() : getConnection()) {
                if (conn != null) {
                    return queryAction.apply(conn);
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Database query error (MySQL): " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            synchronized (sqliteLock) {
                Connection conn = getConnection();
                if (conn != null) {
                    try {
                        return queryAction.apply(conn);
                    } catch (SQLException e) {
                        plugin.getLogger().warning("Database query error (SQLite): " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }
        }
        return defaultValue;
    }

    // ─── Username ────────────────────────────────────────────────────────

    public void updateUsername(UUID uuid, String username) {
        if (uuid == null || username == null) return;
        String sql = "INSERT INTO staff_playtime (uuid, username, daily, weekly, monthly, total) VALUES (?, ?, 0, 0, 0, 0)"
                + upsertSuffix("uuid", "username = ?");
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, username);
                ps.setString(3, username);
                ps.executeUpdate();
            }
        });
    }

    public String getUsername(UUID uuid) {
        if (uuid == null) return null;
        String sql = "SELECT username FROM staff_playtime WHERE uuid = ?;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("username");
                    }
                }
            }
            return null;
        }, null);
    }

    // ─── Playtime Tracking ───────────────────────────────────────────────

    public void addSeconds(UUID uuid, String type, int seconds) {
        if (uuid == null || seconds <= 0 || type == null) return;
        String column;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "daily":    case "gunluk":   column = "daily";   break;
            case "weekly":   case "haftalik": column = "weekly";  break;
            case "monthly":  case "aylik":    column = "monthly"; break;
            case "total":    case "toplam":   column = "total";   break;
            default: return;
        }

        String sql = "INSERT INTO staff_playtime (uuid, username, " + column + ") VALUES (?, 'Unknown', ?)"
                + upsertSuffix("uuid", column + " = " + column + " + ?");
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, seconds);
                ps.setInt(3, seconds);
                ps.executeUpdate();
            }
        });
    }

    public static class StaffRecord {
        private final UUID uuid;
        private final String username;
        private final int seconds;

        public StaffRecord(UUID uuid, String username, int seconds) {
            this.uuid = uuid;
            this.username = username;
            this.seconds = seconds;
        }

        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public int getSeconds() { return seconds; }
    }

    public Map<UUID, StaffRecord> getAllStaffRecords(String type) {
        Map<UUID, StaffRecord> map = new LinkedHashMap<>();
        if (type == null) return map;
        String column;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "daily":    case "gunluk":   column = "daily";   break;
            case "weekly":   case "haftalik": column = "weekly";  break;
            case "monthly":  case "aylik":    column = "monthly"; break;
            case "total":    case "toplam":   column = "total";   break;
            default: return map;
        }

        String sql = "SELECT uuid, username, " + column + " FROM staff_playtime ORDER BY " + column + " DESC;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        UUID uuid = UUID.fromString(rs.getString("uuid"));
                        String username = rs.getString("username");
                        int secs = rs.getInt(column);
                        map.put(uuid, new StaffRecord(uuid, username, secs));
                    } catch (Exception ignored) {}
                }
            }
            return map;
        }, map);
    }

    public Map<UUID, Integer> getAllSeconds(String type) {
        Map<UUID, Integer> map = new LinkedHashMap<>();
        if (type == null) return map;
        String column;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "daily":    case "gunluk":   column = "daily";   break;
            case "weekly":   case "haftalik": column = "weekly";  break;
            case "monthly":  case "aylik":    column = "monthly"; break;
            case "total":    case "toplam":   column = "total";   break;
            default: return map;
        }

        String sql = "SELECT uuid, " + column + " FROM staff_playtime ORDER BY " + column + " DESC;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        map.put(UUID.fromString(rs.getString("uuid")), rs.getInt(column));
                    } catch (Exception ignored) {}
                }
            }
            return map;
        }, map);
    }

    public void clearSecondsByType(String type) {
        if (type == null) return;
        String column;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "daily":    case "gunluk":   column = "daily";   break;
            case "weekly":   case "haftalik": column = "weekly";  break;
            case "monthly":  case "aylik":    column = "monthly"; break;
            case "total":    case "toplam":   column = "total";   break;
            default: return;
        }

        String sql = "UPDATE staff_playtime SET " + column + " = 0;";
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
            }
        });
    }

    public void deletePlayer(UUID uuid) {
        if (uuid == null) return;
        String sql = "DELETE FROM staff_playtime WHERE uuid = ?;";
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        });
    }

    // --- PLAYER VAULT (PV) METHODS ---

    public void saveVault(UUID uuid, int vaultId, ItemStack[] items) {
        if (uuid == null) return;
        String serialized = serializeItemStackArray(items);
        String sql = "INSERT INTO player_vaults (uuid, vault_id, items) VALUES (?, ?, ?)"
                + upsertSuffix2("uuid, vault_id", "items = ?");
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, vaultId);
                ps.setString(3, serialized);
                ps.setString(4, serialized);
                ps.executeUpdate();
            }
        });
    }

    public ItemStack[] loadVault(UUID uuid, int vaultId, int size) {
        if (uuid == null) return new ItemStack[size];
        String sql = "SELECT items FROM player_vaults WHERE uuid = ? AND vault_id = ?;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, vaultId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String serialized = rs.getString("items");
                        if (serialized != null && !serialized.isEmpty()) {
                            return deserializeItemStackArray(serialized, size);
                        }
                    }
                }
            }
            return new ItemStack[size];
        }, new ItemStack[size]);
    }

    public boolean isVaultEmpty(UUID uuid, int vaultId) {
        ItemStack[] items = loadVault(uuid, vaultId, 54);
        for (ItemStack item : items) {
            if (item != null && item.getType() != org.bukkit.Material.AIR) {
                return false;
            }
        }
        return true;
    }

    /**
     * Serializes an array of ItemStacks using Paper's native NBT byte serialization.
     * Guarantees 100% loss-free preservation of ItemsAdder, Nexo, Oraxen, custom model data, PDC, and all metadata.
     */
    private String serializeItemStackArray(ItemStack[] items) {
        if (items == null || items.length == 0) return "";
        try {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            DataOutputStream dataOut = new DataOutputStream(byteOut);

            // Magic byte indicating Paper NBT binary format
            dataOut.writeByte(MAGIC_PAPER_NBT);
            dataOut.writeInt(items.length);

            for (ItemStack item : items) {
                if (item == null || item.getType() == org.bukkit.Material.AIR) {
                    dataOut.writeInt(0);
                } else {
                    byte[] itemBytes = item.serializeAsBytes();
                    dataOut.writeInt(itemBytes.length);
                    dataOut.write(itemBytes);
                }
            }

            dataOut.flush();
            return Base64.getEncoder().encodeToString(byteOut.toByteArray());
        } catch (Exception e) {
            plugin.getLogger().severe("Error serializing vault items: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }

    /**
     * Deserializes an array of ItemStacks with backward compatibility.
     * Supports both modern Paper NBT binary format and legacy BukkitObjectInputStream format.
     */
    private ItemStack[] deserializeItemStackArray(String data, int targetSize) {
        if (data == null || data.trim().isEmpty()) {
            return new ItemStack[targetSize];
        }

        try {
            byte[] rawBytes = Base64.getDecoder().decode(data);
            if (rawBytes.length == 0) {
                return new ItemStack[targetSize];
            }

            // Check if data is in modern Paper NBT binary format
            if (rawBytes[0] == MAGIC_PAPER_NBT && rawBytes.length >= 5) {
                ByteArrayInputStream byteIn = new ByteArrayInputStream(rawBytes, 1, rawBytes.length - 1);
                DataInputStream dataIn = new DataInputStream(byteIn);

                int size = dataIn.readInt();
                ItemStack[] items = new ItemStack[targetSize];

                for (int i = 0; i < size; i++) {
                    int len = dataIn.readInt();
                    if (len > 0) {
                        byte[] itemBytes = new byte[len];
                        dataIn.readFully(itemBytes);
                        if (i < targetSize) {
                            try {
                                items[i] = ItemStack.deserializeBytes(itemBytes);
                            } catch (Exception ex) {
                                plugin.getLogger().warning("Failed to deserialize item at slot " + i + ": " + ex.getMessage());
                                items[i] = null;
                            }
                        }
                    } else {
                        if (i < targetSize) {
                            items[i] = null;
                        }
                    }
                }
                return items;
            }

            // Legacy BukkitObjectInputStream fallback (for existing vaults)
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(rawBytes);
                 BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

                int size = dataInput.readInt();
                ItemStack[] items = new ItemStack[targetSize];

                for (int i = 0; i < size; i++) {
                    Object obj = dataInput.readObject();
                    if (i < targetSize) {
                        items[i] = (obj instanceof ItemStack) ? (ItemStack) obj : null;
                    }
                }
                return items;
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Error deserializing vault items: " + e.getMessage());
            return new ItemStack[targetSize];
        }
    }

    // --- BACKWARDS COMPATIBILITY ALIAS METHODS ---

    /** Compatibility helper */
    public void closeConnection() {
        closeDatabase();
    }

    /** Compatibility helper */
    public void clearDailySeconds() {
        clearSecondsByType("daily");
    }

    /** Compatibility helper */
    public int getSeconds(UUID uuid, String type) {
        if (uuid == null || type == null) return 0;
        String column;
        switch (type.toLowerCase(Locale.ROOT)) {
            case "daily":    case "gunluk":   column = "daily";   break;
            case "weekly":   case "haftalik": column = "weekly";  break;
            case "monthly":  case "aylik":    column = "monthly"; break;
            case "total":    case "toplam":   column = "total";   break;
            default: return 0;
        }
        String sql = "SELECT " + column + " FROM staff_playtime WHERE uuid = ?;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) return rs.getInt(column);
                }
            }
            return 0;
        }, 0);
    }

    // --- PLAYER SETTINGS METHODS ---

    public void savePlayerSettings(UUID uuid, boolean tradeEnabled, boolean announcementEnabled) {
        if (uuid == null) return;
        int tradeVal = tradeEnabled ? 1 : 0;
        int annVal = announcementEnabled ? 1 : 0;
        String sql = "INSERT INTO player_settings (uuid, trade_enabled, announcement_enabled) VALUES (?, ?, ?)"
                + upsertSuffix("uuid", "trade_enabled = ?, announcement_enabled = ?");
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, tradeVal);
                ps.setInt(3, annVal);
                ps.setInt(4, tradeVal);
                ps.setInt(5, annVal);
                ps.executeUpdate();
            }
        });
    }

    public boolean[] loadPlayerSettings(UUID uuid) {
        boolean[] settings = new boolean[]{true, true};
        if (uuid == null) return settings;
        String sql = "SELECT trade_enabled, announcement_enabled FROM player_settings WHERE uuid = ?;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        settings[0] = rs.getInt("trade_enabled") != 0;
                        settings[1] = rs.getInt("announcement_enabled") != 0;
                    }
                }
            }
            return settings;
        }, settings);
    }

    /** Compatibility helper */
    public ItemStack[] getVault(UUID uuid, int vaultId) {
        return loadVault(uuid, vaultId, 54);
    }

    // ─── Verified Sessions (IP-based bypass within timeout window) ───────

    public static class VerifiedSessionData {
        private final String ip;
        private final long verifiedAt;
        private final long expiresAt;

        public VerifiedSessionData(String ip, long verifiedAt, long expiresAt) {
            this.ip = ip;
            this.verifiedAt = verifiedAt;
            this.expiresAt = expiresAt;
        }

        public String getIp() { return ip; }
        public long getVerifiedAt() { return verifiedAt; }
        public long getExpiresAt() { return expiresAt; }
    }

    public void saveVerifiedSession(UUID uuid, String ip, long verifiedAt, long expiresAt) {
        if (uuid == null || ip == null) return;
        String sql = "INSERT INTO verified_sessions (uuid, ip, verified_at, expires_at) VALUES (?, ?, ?, ?)"
                + upsertSuffix("uuid", "ip = ?, verified_at = ?, expires_at = ?");
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, ip);
                ps.setLong(3, verifiedAt);
                ps.setLong(4, expiresAt);
                ps.setString(5, ip);
                ps.setLong(6, verifiedAt);
                ps.setLong(7, expiresAt);
                ps.executeUpdate();
            }
        });
    }

    public VerifiedSessionData getVerifiedSession(UUID uuid) {
        if (uuid == null) return null;
        String sql = "SELECT ip, verified_at, expires_at FROM verified_sessions WHERE uuid = ?;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new VerifiedSessionData(rs.getString("ip"), rs.getLong("verified_at"), rs.getLong("expires_at"));
                    }
                }
            }
            return null;
        }, null);
    }

    public Map<UUID, VerifiedSessionData> loadAllValidVerifiedSessions(long currentTime) {
        Map<UUID, VerifiedSessionData> map = new LinkedHashMap<>();
        String sql = "SELECT uuid, ip, verified_at, expires_at FROM verified_sessions WHERE expires_at > ?;";
        return query(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, currentTime);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            UUID uuid = UUID.fromString(rs.getString("uuid"));
                            map.put(uuid, new VerifiedSessionData(rs.getString("ip"), rs.getLong("verified_at"), rs.getLong("expires_at")));
                        } catch (Exception ignored) {}
                    }
                }
            }
            return map;
        }, map);
    }

    public void cleanExpiredVerifiedSessions(long currentTime) {
        String sql = "DELETE FROM verified_sessions WHERE expires_at <= ?;";
        execute(conn -> {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, currentTime);
                ps.executeUpdate();
            }
        });
    }

    private void deleteDirectoryRecursively(File file) {
        if (file == null || !file.exists()) return;
        try {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteDirectoryRecursively(child);
                    }
                }
            }
            file.delete();
        } catch (Exception ignored) {}
    }
}
