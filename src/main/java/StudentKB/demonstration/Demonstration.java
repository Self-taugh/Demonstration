package StudentKB.demonstration;

import StudentKB.demonstration.commands.ZabvenieCommand;
import StudentKB.demonstration.core.ConfigManager;
import StudentKB.demonstration.core.ModuleManager;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class Demonstration extends JavaPlugin {

    private ConfigManager configManager;
    private ModuleManager moduleManager;

    @Override
    public void onEnable() {
        // Инициализация менеджеров
        this.configManager = new ConfigManager(this);
        this.moduleManager = new ModuleManager(this, configManager);

        // Регистрация команд
        registerCommands();

        getLogger().info("§aZabvenieEventsVersion2 успешно включен!");
        getLogger().info("§aЗагружено модулей: " + moduleManager.getModulesCount());
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAllModules();
        }
        getLogger().info("ZabvenieEventsVersion2 отключен!");
    }

    private void registerCommands() {
        var lifecycleManager = this.getLifecycleManager();
        lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            final Commands commands = event.registrar();
            commands.register(
                    "zabvenie",
                    "Управление плагином ZabvenieEvents",
                    new ZabvenieCommand(this, moduleManager)
            );
        });
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public void reloadPlugin() {
        configManager.reloadConfig();
        moduleManager.reloadAllModules();
        getLogger().info("§aПлагин перезагружен!");
    }
}
