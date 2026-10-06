package StudentKB.demonstration.core;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConfigManager {

    private final JavaPlugin plugin;
    private FileConfiguration config;
    private File configFile;

    // Паттерн для парсинга времени (поддерживает h, m, s)
    private static final Pattern TIME_PATTERN = Pattern.compile(
            "(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?"
    );

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");
        loadConfig();
    }

    public void loadConfig() {
        // Создаем папку плагина, если её нет
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        // Проверяем, существует ли файл конфига
        if (!configFile.exists()) {
            // Если файла нет - создаем из ресурсов
            plugin.saveDefaultConfig();
            plugin.getLogger().info("Создан новый конфигурационный файл из ресурсов");
        }

        // Загружаем файл конфигурации
        this.config = YamlConfiguration.loadConfiguration(configFile);

        // Загружаем дефолтный конфиг из ресурсов, если есть
        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defConfig);
        }

        plugin.getLogger().info("Конфигурация загружена из файла");
    }

    public void reloadConfig() {
        plugin.getLogger().info("Перезагрузка конфигурации...");
        this.config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defConfigStream = plugin.getResource("config.yml");
        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defConfigStream, StandardCharsets.UTF_8)
            );
            config.setDefaults(defConfig);
        }

        plugin.getLogger().info("Конфигурация перезагружена");
    }

    /**
     * Парсит строку времени в тики
     * Поддерживает форматы: 5s, 2m, 1h, 1h30m, 1h20m30s, 2m30s
     */
    public int parseTimeToTicks(String timeString) {
        if (timeString == null || timeString.isEmpty()) return 0;

        // Пробуем распарсить как число в секундах
        try {
            return (int) (Double.parseDouble(timeString) * 20);
        } catch (NumberFormatException ignored) {}

        Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
        if (!matcher.matches()) {
            plugin.getLogger().warning("Неверный формат времени: " + timeString);
            return 0;
        }

        int hours = 0;
        int minutes = 0;
        int seconds = 0;

        if (matcher.group(1) != null) {
            hours = Integer.parseInt(matcher.group(1));
        }
        if (matcher.group(2) != null) {
            minutes = Integer.parseInt(matcher.group(2));
        }
        if (matcher.group(3) != null) {
            seconds = Integer.parseInt(matcher.group(3));
        }

        long totalSeconds = hours * 3600L + minutes * 60L + seconds;
        return (int) (totalSeconds * 20);
    }

    /**
     * Получает настройки модуля из конфига
     */
    public Map<String, Object> getModuleConfig(String moduleName) {
        String path = "modules." + moduleName;
        if (!config.contains(path)) {
            return new HashMap<>();
        }
        return config.getConfigurationSection(path).getValues(false);
    }

    /**
     * Получает значение из конфига с указанным типом
     */
    public <T> T getValue(String path, T defaultValue) {
        if (!config.contains(path)) {
            return defaultValue;
        }
        return (T) config.get(path);
    }

    /**
     * Получает строковое значение из конфига
     */
    public String getString(String path, String defaultValue) {
        return config.getString(path, defaultValue);
    }

    /**
     * Получает целочисленное значение из конфига
     */
    public int getInt(String path, int defaultValue) {
        return config.getInt(path, defaultValue);
    }

    /**
     * Получает булево значение из конфига
     */
    public boolean getBoolean(String path, boolean defaultValue) {
        return config.getBoolean(path, defaultValue);
    }

    /**
     * Получает список строк из конфига
     */
    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }

    /**
     * Получает значение времени из конфига (парсит в тики)
     */
    public int getTimeTicks(String path, String defaultValue) {
        String value = config.getString(path, defaultValue);
        return parseTimeToTicks(value);
    }

    /**
     * Сохраняет текущий конфиг в файл
     */
    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (Exception e) {
            plugin.getLogger().warning("Не удалось сохранить конфиг: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public File getConfigFile() {
        return configFile;
    }
}