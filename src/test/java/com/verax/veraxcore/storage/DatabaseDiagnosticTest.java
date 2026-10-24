package com.verax.veraxcore.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseDiagnosticTest {

    @Test
    @DisplayName("Diagnose MySQL Access Denied (Error 1045)")
    void testAccessDenied() {
        SQLException sqlEx = new SQLException("Access denied for user 'root'@'localhost'", "28000", 1045);
        DatabaseDiagnostic.DiagnosticResult result = DatabaseDiagnostic.diagnose(sqlEx, "localhost", 3306, "veraxcore", "root");

        assertNotNull(result);
        assertTrue(result.getShortReason().contains("Access Denied"));
        assertTrue(result.getSolution().contains("password"));
    }

    @Test
    @DisplayName("Diagnose Unknown Database (Error 1049)")
    void testUnknownDatabase() {
        SQLException sqlEx = new SQLException("Unknown database 'veraxcore'", "42000", 1049);
        DatabaseDiagnostic.DiagnosticResult result = DatabaseDiagnostic.diagnose(sqlEx, "localhost", 3306, "veraxcore", "root");

        assertNotNull(result);
        assertTrue(result.getShortReason().contains("does not exist"));
        assertTrue(result.getSolution().contains("CREATE DATABASE"));
    }

    @Test
    @DisplayName("Diagnose Connection Refused")
    void testConnectionRefused() {
        ConnectException connEx = new ConnectException("Connection refused: connect");
        DatabaseDiagnostic.DiagnosticResult result = DatabaseDiagnostic.diagnose(connEx, "127.0.0.1", 3306, "veraxcore", "root");

        assertNotNull(result);
        assertTrue(result.getShortReason().contains("Connection Refused"));
        assertTrue(result.getSolution().contains("running"));
    }

    @Test
    @DisplayName("Diagnose Unknown Host")
    void testUnknownHost() {
        UnknownHostException hostEx = new UnknownHostException("invalid.host.domain");
        DatabaseDiagnostic.DiagnosticResult result = DatabaseDiagnostic.diagnose(hostEx, "invalid.host.domain", 3306, "veraxcore", "root");

        assertNotNull(result);
        assertTrue(result.getShortReason().contains("Unknown Host"));
    }

    @Test
    @DisplayName("Diagnose null throwable gracefully")
    void testNullThrowable() {
        DatabaseDiagnostic.DiagnosticResult result = DatabaseDiagnostic.diagnose(null, "localhost", 3306, "veraxcore", "root");
        assertNotNull(result);
        assertEquals("Unknown database error.", result.getShortReason());
    }
}
