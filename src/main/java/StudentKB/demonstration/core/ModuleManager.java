package StudentKB.demonstration.core;

import StudentKB.demonstration.modules.end.EndSculkGrowModule;
import StudentKB.demonstration.modules.nether.NetherSoundsModule;
import StudentKB.demonstration.modules.overworld.OverworldNetherPortalSpawnerModule;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ModuleManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, BaseModule> modules = new HashMap<>();

    public ModuleManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        loadModules();
    }

    private void loadModules() {
        new BukkitRunnable(){
            @Override
            public void run(){
                // Регистрируем все модули
                registerModule(new OverworldNetherPortalSpawnerModule(plugin, configManager));
                registerModule(new EndSculkGrowModule(plugin, configManager));
                registerModule(new NetherSoundsModule(plugin, configManager));

                loadAllModulesConfigs();
                enableAllModules();
            }
        }.runTaskLater(plugin, 100);
    }

    private void registerModule(BaseModule module) {
        modules.put(module.getModuleName(), module);
        plugin.getLogger().info("Зарегистрирован модуль: " + module.getModuleName());
    }

    private void loadAllModulesConfigs() {
        for (BaseModule module : modules.values()) {
            module.loadConfig();
        }
    }

    private void enableAllModules() {
        for (BaseModule module : modules.values()) {
            module.enable();
        }
    }

    public void disableAllModules() {
        for (BaseModule module : modules.values()) {
            module.disable();
        }
    }

    public void reloadAllModules() {
        plugin.getLogger().info("Перезагрузка всех модулей...");

        // Отключаем все модули
        disableAllModules();

        // Очищаем список модулей
        modules.clear();

        // Перезагружаем конфиг в ConfigManager
        configManager.reloadConfig();

        // Загружаем модули заново
        loadModules();

        plugin.getLogger().info("Все модули перезагружены");
    }

    public BaseModule getModule(String name) {
        return modules.get(name);
    }

    public Map<String, BaseModule> getModules() {
        return modules;
    }

    public int getModulesCount() {
        return modules.size();
    }
}