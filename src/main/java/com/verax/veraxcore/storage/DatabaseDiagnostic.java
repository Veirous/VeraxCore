package com.verax.veraxcore.storage;

import java.sql.SQLException;
import java.util.Locale;

/**
 * Diagnostic utility that analyzes database exceptions (MySQL / SQLite / HikariCP)
 * and translates raw stack traces into clear, human-readable English reasons and actionable solutions.
 */
public class DatabaseDiagnostic {

    public static class DiagnosticResult {
        private final String shortReason;
        private final String solution;
        private final String technicalDetails;

        public DiagnosticResult(String shortReason, String solution, String technicalDetails) {
            this.shortReason = shortReason;
            this.solution = solution;
            this.technicalDetails = technicalDetails;
        }

        public String getShortReason() {
            return shortReason;
        }

        public String getSolution() {
            return solution;
        }

        public String getTechnicalDetails() {
            return technicalDetails;
        }
    }

    /**
     * Diagnoses a database error and produces an understandable reason and solution in English.
     *
     * @param throwable the caught exception
     * @param host configured MySQL host
     * @param port configured MySQL port
     * @param database configured MySQL database name
     * @param username configured MySQL username
     * @return DiagnosticResult containing reason, solution, and technical details in English
     */
    public static DiagnosticResult diagnose(Throwable throwable, String host, int port, String database, String username) {
        if (throwable == null) {
            return new DiagnosticResult(
                    "Unknown database error.",
                    "Check database configuration settings.",
                    "N/A"
            );
        }

        // Traverse the entire cause hierarchy to collect all messages and find the root cause
        StringBuilder chainMessages = new StringBuilder();
        Throwable current = throwable;
        Throwable root = throwable;
        SQLException sqlException = null;

        boolean isConnectRefused = false;
        boolean isUnknownHost = false;
        boolean isTimeout = false;
        boolean isClassNotFound = false;

        while (current != null) {
            root = current;
            if (current instanceof SQLException && sqlException == null) {
                sqlException = (SQLException) current;
            }
            if (current instanceof java.net.ConnectException) {
                isConnectRefused = true;
            }
            if (current instanceof java.net.UnknownHostException) {
                isUnknownHost = true;
            }
            if (current instanceof java.net.SocketTimeoutException) {
                isTimeout = true;
            }
            if (current instanceof ClassNotFoundException) {
                isClassNotFound = true;
            }

            if (current.getMessage() != null) {
                chainMessages.append(" ").append(current.getMessage());
            }

            if (current.getCause() == current) {
                break;
            }
            current = current.getCause();
        }

        String allText = chainMessages.toString().toLowerCase(Locale.ROOT);
        String rootMsg = root.getMessage() != null ? root.getMessage().trim() : root.getClass().getSimpleName();
        String technicalDetails = root.getClass().getSimpleName() + (root.getMessage() != null ? ": " + root.getMessage().trim() : "");

        int errorCode = sqlException != null ? sqlException.getErrorCode() : -1;
        String sqlState = sqlException != null && sqlException.getSQLState() != null ? sqlException.getSQLState() : "";

        // 1. Connection Refused (Port closed, MySQL offline, or wrong host/port)
        if (isConnectRefused || allText.contains("connection refused") || allText.contains("bağlantı reddedildi")) {
            String reason = "Could not connect to MySQL server (Connection Refused).";
            String solution = "Ensure MySQL service (XAMPP / MariaDB / Docker) is running and host/port (" + host + ":" + port + ") in config.yml are correct. Try using '127.0.0.1' instead of 'localhost'.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 2. Access Denied (Wrong username or password)
        if (errorCode == 1045 || "28000".equals(sqlState) || allText.contains("access denied for user")) {
            String reason = "MySQL authentication failed (Access Denied - invalid username or password).";
            String solution = "Verify 'mysql.username' ('" + username + "') and 'mysql.password' in config.yml.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 3. Unknown Database (Database doesn't exist)
        if (errorCode == 1049 || "42000".equals(sqlState) || allText.contains("unknown database")) {
            String reason = "Database '" + database + "' does not exist on MySQL server (Unknown Database).";
            String solution = "Create the database in MySQL (CREATE DATABASE IF NOT EXISTS " + database + ";) or check 'mysql.database' in config.yml.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 4. Unknown Host (Host IP or hostname cannot be resolved)
        if (isUnknownHost || allText.contains("unknownhostexception") || allText.contains("unknown host")) {
            String reason = "MySQL host address ('" + host + "') could not be resolved (Unknown Host).";
            String solution = "Ensure 'mysql.host' in config.yml is a valid IP address (e.g. 127.0.0.1) or domain name.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 5. Connection Timeout / Firewall Block
        if (isTimeout || allText.contains("timed out") || allText.contains("sockettimeout")) {
            String reason = "Connection to MySQL server timed out (Network / Firewall issue).";
            String solution = "Ensure port " + port + " is open in firewall/UFW and MySQL allows remote connections (bind-address = 0.0.0.0).";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 6. Generic Communications Link Failure
        if (allText.contains("communications link failure")) {
            String reason = "Communications link failure with MySQL server.";
            String solution = "Check if MySQL server is running and " + host + ":" + port + " is reachable.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 7. SQLite Busy / Locked
        if (allText.contains("sqlite_busy") || allText.contains("database is locked") || allText.contains("database locked")) {
            String reason = "SQLite database file is locked or in use by another process (SQLITE_BUSY).";
            String solution = "Close any external applications accessing database.db and restart the server.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 8. SQLite Read-Only / Permission
        if (allText.contains("sqlite_readonly") || allText.contains("permission denied") || allText.contains("access is denied")) {
            String reason = "Database file write permission denied (Read-Only).";
            String solution = "Check file write permissions for plugins/VeraxCore and the database file.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 9. ClassNotFound / Driver missing
        if (isClassNotFound || allText.contains("classnotfoundexception")) {
            String reason = "Database JDBC Driver could not be loaded.";
            String solution = "Verify Java version and server libraries.";
            return new DiagnosticResult(reason, solution, technicalDetails);
        }

        // 10. Fallback for other errors
        String reason = "Database error: " + rootMsg;
        String solution = "Check configuration settings. Enable 'debug: true' in config.yml for full Java stack trace.";

        return new DiagnosticResult(reason, solution, technicalDetails);
    }
}
