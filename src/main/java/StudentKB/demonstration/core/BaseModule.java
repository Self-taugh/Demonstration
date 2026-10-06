package StudentKB.demonstration.core;

import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public abstract class BaseModule {

    protected final JavaPlugin plugin;
    protected final ConfigManager configManager;
    protected final String moduleName;
    protected final Map<String, Object> config;
    protected final Random random = new Random();
    protected boolean enabled = false;
    protected boolean debug = false;
    protected Map<World, Boolean> worldStates = new HashMap<>();

    public BaseModule(JavaPlugin plugin, ConfigManager configManager, String moduleName) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.moduleName = moduleName;
        this.config = configManager.getModuleConfig(moduleName);
    }

    /**
     * Загрузка конфигурации модуля
     */
    protected abstract void loadConfig();

    /**
     * Запуск модуля
     */
    public abstract void enable();

    /**
     * Остановка модуля
     */
    public abstract void disable();

    /**
     * Перезагрузка модуля
     */
    public void reload() {
        disable();


        loadConfig();
        if (enabled) {
            enable();
        }
    }

    /**
     * Метод для принудительной активации события
     */
    public abstract void triggerEventNow(CommandSender sender, String[] args);

    /**
     * Метод для обработки команды edit
     * @param sender отправитель команды
     * @param args аргументы команды
     * @return true если команда обработана, false если нет
     */
    public boolean handleEditCommand(CommandSender sender, String[] args) {
        return false; // По умолчанию не обрабатываем
    }

    /**
     * Проверяет, активен ли модуль в указанном мире
     */
    public boolean isEnabledInWorld(World world) {
        return worldStates.getOrDefault(world, false);
    }

    /**
     * Устанавливает состояние модуля в мире
     */
    protected void setWorldState(World world, boolean state) {
        worldStates.put(world, state);
    }

    /**
     * Получает все миры, где модуль активен
     */
    public List<World> getActiveWorlds() {
        List<World> active = new ArrayList<>();
        for (Map.Entry<World, Boolean> entry : worldStates.entrySet()) {
            if (entry.getValue()) {
                active.add(entry.getKey());
            }
        }
        return active;
    }

    /**
     * Проверяет, есть ли игроки в мире
     */
    protected boolean hasPlayersInWorld(World world) {
        return world != null && !world.getPlayers().isEmpty();
    }

    /**
     * Получает настройку из конфига модуля
     */
    protected <T> T getConfigValue(String key, T defaultValue) {
        if (config.containsKey(key)) {
            return (T) config.get(key);
        }
        return defaultValue;
    }

    /**
     * Получает строковую настройку из конфига модуля
     */
    protected String getConfigString(String key, String defaultValue) {
        return getConfigValue(key, defaultValue);
    }

    /**
     * Получает числовую настройку из конфига модуля
     */
    protected int getConfigInt(String key, int defaultValue) {
        return getConfigValue(key, defaultValue);
    }

    /**
     * Получает булеву настройку из конфига модуля
     */
    protected boolean getConfigBoolean(String key, boolean defaultValue) {
        return getConfigValue(key, defaultValue);
    }

    /**
     * Получает число с плавающей точкой из конфига модуля
     */
    protected float getConfigFloat(String key, float defaultValue) {
        return getConfigValue(key, defaultValue);
    }

    /**
     * Получает число с плавающей точкой двойной точности из конфига модуля
     */
    protected double getConfigDouble(String key, double defaultValue) {
        return getConfigValue(key, defaultValue);
    }

    /**
     * Получает список строк из конфига модуля
     */
    protected List<String> getConfigStringList(String key) {
        return getConfigValue(key, new ArrayList<>());
    }

    protected <T> T getRandomFromList(List<T> list) {
        if (list == null || list.isEmpty()) return null;
        return list.get(random.nextInt(list.size()));
    }

    /**
     * Получает время из конфига модуля в тиках
     */
    protected int getConfigTimeTicks(String key, String defaultValue) {
        String value = getConfigString(key, defaultValue);
        return configManager.parseTimeToTicks(value);
    }

    /**
     * Выводит сообщение в консоль только если включен режим отладки
     */
    protected void debugLog(String message) {
        if (debug) {
            plugin.getLogger().info("[DEBUG] [" + moduleName + "] " + message);
        }
    }

    /**
     * Выводит предупреждение в консоль
     */
    protected void warningLog(String message) {
        plugin.getLogger().warning("[" + moduleName + "] " + message);
    }

    /**
     * Выводит информационное сообщение в консоль
     */
    protected void infoLog(String message) {
        plugin.getLogger().info("[" + moduleName + "] " + message);
    }

    public String getModuleName() {
        return moduleName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDebug() {
        return debug;
    }
}