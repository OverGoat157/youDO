package ru.mirea.freelance.util;

import ru.mirea.freelance.exception.DatabaseException;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Подключение к PostgreSQL. Настройки читаются из db.properties в classpath.
 */
public class DatabaseManager {

    private static final String CONFIG_FILE = "db.properties";

    private final String url;
    private final String user;
    private final String password;

    public DatabaseManager() {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(CONFIG_FILE)) {
            if (in == null) {
                throw new DatabaseException("Файл " + CONFIG_FILE + " не найден. "
                        + "Скопируйте db.properties.example в db.properties и укажите настройки.");
            }
            props.load(in);
        } catch (IOException e) {
            throw new DatabaseException("Не удалось прочитать " + CONFIG_FILE, e);
        }
        url = props.getProperty("db.url");
        user = props.getProperty("db.user");
        password = props.getProperty("db.password");
    }

    /** Открывает новое соединение. Закрывать его должен вызывающий (try-with-resources). */
    public Connection getConnection() {
        try {
            return DriverManager.getConnection(url, user, password);
        } catch (SQLException e) {
            throw new DatabaseException("Не удалось подключиться к базе данных: " + e.getMessage(), e);
        }
    }
}