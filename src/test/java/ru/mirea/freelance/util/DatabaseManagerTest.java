package ru.mirea.freelance.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ru.mirea.freelance.exception.DatabaseException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerTest {
    @TempDir Path directory;

    private Properties settings() {
        Properties properties = new Properties();
        properties.setProperty("db.url", "jdbc:postgresql://127.0.0.1:1/youdo");
        properties.setProperty("db.user", "youdo");
        properties.setProperty("db.password", "private-test-password");
        return properties;
    }

    private Path write(Properties properties) throws IOException {
        Path file = directory.resolve("db.properties");
        try (OutputStream out = Files.newOutputStream(file)) {
            properties.store(out, null);
        }
        return file;
    }

    @ParameterizedTest
    @ValueSource(strings = {"db.url", "db.user", "db.password"})
    void missingKeyNamesTheSettingWithoutPrintingSecrets(String key) throws IOException {
        Properties properties = settings();
        properties.remove(key);
        Path file = write(properties);
        DatabaseException error = assertThrows(DatabaseException.class, () -> new DatabaseManager(file));
        assertTrue(error.getMessage().contains(key));
        assertFalse(error.getMessage().contains("private-test-password"));
    }

    @Test
    void missingFileGivesAnExplanation() {
        DatabaseException error = assertThrows(DatabaseException.class,
                () -> new DatabaseManager(directory.resolve("missing.properties")));
        assertTrue(error.getMessage().contains("настроек БД"));
    }

    @Test
    void blankSettingIsRejected() throws IOException {
        Properties properties = settings();
        properties.setProperty("db.user", "  ");
        Path file = write(properties);
        assertTrue(assertThrows(DatabaseException.class,
                () -> new DatabaseManager(file)).getMessage().contains("db.user"));
    }

    @Test
    void nonPostgresUrlIsRejected() throws IOException {
        Properties properties = settings();
        properties.setProperty("db.url", "jdbc:mysql://localhost/youdo");
        Path file = write(properties);
        assertTrue(assertThrows(DatabaseException.class,
                () -> new DatabaseManager(file)).getMessage().contains("PostgreSQL"));
    }

    @Test
    void constructorDoesNotConnectAndConnectionErrorDoesNotExposeCredentials() throws IOException {
        DatabaseManager database = new DatabaseManager(write(settings()));
        DatabaseException error = assertThrows(DatabaseException.class, database::getConnection);
        assertTrue(error.getMessage().contains("db.properties"));
        assertFalse(error.getMessage().contains("private-test-password"));
        assertNotNull(error.getCause());
    }
}
