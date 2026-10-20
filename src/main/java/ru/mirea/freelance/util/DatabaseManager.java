package ru.mirea.freelance.util;

import ru.mirea.freelance.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Настройки читаются один раз, соединения открываются только в фоновых операциях. */
public class DatabaseManager {
    private final String url;
    private final Properties connectionProperties = new Properties();

    public DatabaseManager() {
        this(defaultConfig());
    }

    /** Явный файл для проверки отдельной тестовой базы. */
    public DatabaseManager(Path config) {
        this(open(config));
    }

    private DatabaseManager(InputStream input) {
        if (input == null) {
            throw new DatabaseException("Файл db.properties не найден. Скопируйте "
                    + "src/main/resources/db.properties.example в db.properties рядом с ним и заполните настройки.");
        }
        Properties config = new Properties();
        try (input) {
            config.load(input);
        } catch (IOException | IllegalArgumentException e) {
            throw new DatabaseException("Не удалось прочитать db.properties. Проверьте формат и доступ к файлу.", e);
        }
        url = required(config, "db.url").trim();
        if (!url.startsWith("jdbc:postgresql:")) {
            throw new DatabaseException("Параметр db.url должен быть JDBC-адресом PostgreSQL.");
        }
        connectionProperties.setProperty("user", required(config, "db.user").trim());
        connectionProperties.setProperty("password", required(config, "db.password"));
        connectionProperties.setProperty("connectTimeout", "5");
        connectionProperties.setProperty("socketTimeout", "15");
    }

    private static InputStream open(Path config) {
        try {
            return Files.newInputStream(config);
        } catch (IOException e) {
            throw new DatabaseException("Не удалось открыть файл настроек БД. Проверьте путь и права доступа.", e);
        }
    }

    private static InputStream defaultConfig() {
        String external = System.getProperty("youdo.db.config");
        if (external == null || external.isBlank()) {
            return DatabaseManager.class.getResourceAsStream("/db.properties");
        }
        try {
            return open(Path.of(external));
        } catch (java.nio.file.InvalidPathException error) {
            throw new DatabaseException("Некорректный путь в параметре youdo.db.config.", error);
        }
    }

    private static String required(Properties config, String key) {
        String value = config.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new DatabaseException("В db.properties не задан параметр " + key + ".");
        }
        return value;
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, connectionProperties);
        } catch (SQLException e) {
            // Сообщение драйвера может содержать реквизиты из URL, поэтому в UI его не передаём.
            throw new DatabaseException("Не удалось подключиться к базе данных. "
                    + "Проверьте, запущен ли PostgreSQL, адрес, порт и реквизиты в db.properties.", e);
        }
    }
}
