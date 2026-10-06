package StudentKB.demonstration.commands;


import StudentKB.demonstration.Demonstration;
import StudentKB.demonstration.core.BaseModule;
import StudentKB.demonstration.core.ModuleManager;
import StudentKB.demonstration.modules.end.EndSculkGrowModule;
import StudentKB.demonstration.modules.nether.NetherSoundsModule;
import StudentKB.demonstration.modules.overworld.OverworldNetherPortalSpawnerModule;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class ZabvenieCommand implements BasicCommand {

    private final Demonstration plugin;
    private final ModuleManager moduleManager;

    // Список всех доступных подкоманд
    private static final List<String> SUB_COMMANDS = List.of("reload", "modules", "status", "trigger", "edit");
    private static final List<String> MODULE_NAMES = List.of(
            "overworld-nether-portal-spawner",
            "end-sculk-grow",
            "nether-sounds"
    );

    public ZabvenieCommand(Demonstration plugin, ModuleManager moduleManager) {
        this.plugin = plugin;
        this.moduleManager = moduleManager;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();

        if (!sender.hasPermission("zabvenie.admin")) {
            sender.sendMessage("§cУ вас нет прав для выполнения этой команды!");
            return;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reloadPlugin();
                sender.sendMessage("§aПлагин перезагружен!");
                break;

            case "modules":
                listModules(sender);
                break;

            case "status":
                showStatus(sender, args);
                break;

            case "trigger":
                triggerEvent(sender, args);
                break;

            case "edit":
                handleEditCommand(sender, args);
                break;

            default:
                sendHelp(sender);
                break;
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== ZabvenieEvents Help ===");
        sender.sendMessage("§e/zabvenie reload §7- Перезагрузить плагин");
        sender.sendMessage("§e/zabvenie modules §7- Показать список модулей");
        sender.sendMessage("§e/zabvenie status [module] §7- Показать статус модуля");
        sender.sendMessage("§e/zabvenie trigger [module] §7- Принудительно активировать событие модуля");
        sender.sendMessage("§e/zabvenie edit [module] [args...] §7- Редактировать модуль");
        sender.sendMessage("§7Доступные модули: " + String.join(", ", MODULE_NAMES));
    }

    private void listModules(CommandSender sender) {
        sender.sendMessage("§6=== Список модулей ===");
        for (BaseModule module : moduleManager.getModules().values()) {
            String status = module.isEnabled() ? "§aВключен" : "§cОтключен";
            String debug = module.isDebug() ? "§a" : "§c";
            sender.sendMessage("§7- §f" + module.getModuleName() + " §7: " + status +
                    " §7(отладка: " + debug + (module.isDebug() ? "вкл" : "выкл") + "§7)");
        }
    }

    private void showStatus(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /zabvenie status <module>");
            sender.sendMessage("§cДоступные модули: " + String.join(", ", MODULE_NAMES));
            return;
        }

        BaseModule module = moduleManager.getModule(args[1]);
        if (module == null) {
            sender.sendMessage("§cМодуль '" + args[1] + "' не найден!");
            sender.sendMessage("§cДоступные модули: " + String.join(", ", MODULE_NAMES));
            return;
        }

        if (module instanceof OverworldNetherPortalSpawnerModule) {
            sender.sendMessage(((OverworldNetherPortalSpawnerModule) module).getStatus());
        } else if (module instanceof EndSculkGrowModule) {
            sender.sendMessage(((EndSculkGrowModule) module).getStatus());
        }else if (module instanceof NetherSoundsModule) {
            sender.sendMessage(((NetherSoundsModule) module).getStatus());
        }
        else {
            sender.sendMessage("§7Модуль: §f" + module.getModuleName());
            sender.sendMessage("§7Статус: " + (module.isEnabled() ? "§aВключен" : "§cОтключен"));
            sender.sendMessage("§7Отладка: " + (module.isDebug() ? "§aВключена" : "§cОтключена"));
            sender.sendMessage("§7Активен в мирах: " + module.getActiveWorlds().size());
        }
    }

    private void triggerEvent(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /zabvenie trigger <module>");
            sender.sendMessage("§cДоступные модули: " + String.join(", ", MODULE_NAMES));
            return;
        }

        BaseModule module = moduleManager.getModule(args[1]);
        if (module == null) {
            sender.sendMessage("§cМодуль '" + args[1] + "' не найден!");
            sender.sendMessage("§cДоступные модули: " + String.join(", ", MODULE_NAMES));
            return;
        }

        if (!module.isEnabled()) {
            sender.sendMessage("§cМодуль '" + args[1] + "' отключен!");
            return;
        }


        module.triggerEventNow(sender, Arrays.copyOfRange(args, 2, args.length));
        sender.sendMessage("§aСобытие модуля '" + args[1] + "' принудительно активировано!");
    }

    private void handleEditCommand(CommandSender sender, String[] args) {
        // Проверяем, что указан модуль
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /zabvenie edit <module> [args...]");
            sender.sendMessage("§cДоступные модули: " + String.join(", ", MODULE_NAMES));
            return;
        }

        String moduleName = args[1];
        BaseModule module = moduleManager.getModule(moduleName);
        if (module == null) {
            sender.sendMessage("§cМодуль '" + moduleName + "' не найден!");
            sender.sendMessage("§cДоступные модули: " + String.join(", ", MODULE_NAMES));
            return;
        }

        if (!module.isEnabled()) {
            sender.sendMessage("§cМодуль '" + moduleName + "' отключен!");
            return;
        }

        // Если аргументов только 2 - показываем помощь модуля
        if (args.length == 2) {
            // Показываем общую помощь для модуля
            sender.sendMessage("§6=== Редактирование модуля: " + moduleName + " ===");
            // Передаем пустой массив для показа помощи
            boolean handled = module.handleEditCommand(sender, new String[]{moduleName});
            if (!handled) {
                sender.sendMessage("§7У этого модуля нет доступных команд редактирования.");
                sender.sendMessage("§7Попробуйте: /zabvenie edit " + moduleName + " help");
            }
            return;
        }

        // Передаем команду модулю для обработки (передаем полный массив args)
        boolean handled = module.handleEditCommand(sender, args);
        if (!handled) {
            sender.sendMessage("§cМодуль '" + moduleName + "' не поддерживает эту команду.");
            sender.sendMessage("§7Попробуйте: /zabvenie edit " + moduleName + " help");
        }
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        List<String> suggestions = new ArrayList<>();

        if (args.length == 0) {
            suggestions.addAll(SUB_COMMANDS);
        } else if (args.length == 1) {
            String input = args[0].toLowerCase();
            for (String command : SUB_COMMANDS) {
                if (command.startsWith(input)) {
                    suggestions.add(command);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            String input = args[1].toLowerCase();

            if (subCommand.equals("status") || subCommand.equals("trigger") || subCommand.equals("edit")) {
                for (String module : MODULE_NAMES) {
                    if (subCommand.equals("trigger")) {
                        BaseModule moduleInstance = moduleManager.getModule(module);
                        if (moduleInstance == null || !moduleInstance.isEnabled()) {
                            continue;
                        }
                    }
                    if (module.startsWith(input)) {
                        suggestions.add(module);
                    }
                }
            }
        } else if (args.length >= 3) {
            String subCommand = args[0].toLowerCase();
            String moduleName = args[1].toLowerCase();

            // Для модуля nether-get-out добавляем подкоманды
            if (subCommand.equals("edit") && moduleName.equals("nether-get-out")) {
                String input = args[2].toLowerCase();
                if (args.length == 3) {
                    List<String> netherSubCommands = List.of("add", "remove", "list", "help");
                    for (String cmd : netherSubCommands) {
                        if (cmd.startsWith(input)) {
                            suggestions.add(cmd);
                        }
                    }
                } else if (args.length == 4 && args[2].equalsIgnoreCase("add")) {
                    // Для add предлагаем уровни
                    String levelInput = args[3].toLowerCase();
                    List<String> levels = List.of("positive", "neutral", "negative");
                    for (String level : levels) {
                        if (level.startsWith(levelInput)) {
                            suggestions.add(level);
                        }
                    }
                }
            }
        }

        return suggestions;
    }

    @Override
    public @Nullable String permission() {
        return "zabvenie.admin";
    }
}