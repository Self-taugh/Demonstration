package StudentKB.demonstration.modules.nether;

import StudentKB.demonstration.core.BaseModule;
import StudentKB.demonstration.core.ConfigManager;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class NetherSoundsModule extends BaseModule {
    private int minInterval;
    private int maxInterval;
    private boolean useCustomSounds;
    private List<String> soundList;
    private List<String> customSoundList;
    private float volume;
    private float pitch;

    private BukkitTask soundTask;
    private World netherWorld;

    private static final String NETHER_WORLD = "world_nether";

    public NetherSoundsModule(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, configManager, "nether-sounds");
    }

    @Override
    protected void loadConfig() {
        this.enabled = getConfigBoolean("enabled", true);
        this.debug = getConfigBoolean("debug", false);
        this.minInterval = getConfigTimeTicks("min-interval", "30s");
        this.maxInterval = getConfigTimeTicks("max-interval", "2m");
        this.useCustomSounds = getConfigBoolean("use-custom-sounds", false);
        this.soundList = getConfigStringList("sounds");
        this.customSoundList = getConfigStringList("custom-sounds");
        this.volume = ((Double) getConfigDouble("volume", 1.0)).floatValue();
        this.pitch = ((Double) getConfigDouble("pitch", 1.0)).floatValue();

        // Если список звуков пуст, заполняем дефолтными
        if (soundList.isEmpty()) {
            soundList = Arrays.asList(
                    "AMBIENT_NETHER_WASTES_MOOD",
                    "AMBIENT_SOUL_SAND_VALLEY_MOOD",
                    "AMBIENT_WARPED_FOREST_MOOD",
                    "ENTITY_GHAST_SCREAM",
                    "ENTITY_GHAST_HURT",
                    "ENTITY_GHAST_SHOOT",
                    "ENTITY_WITHER_SKELETON_DEATH",
                    "ENTITY_WITHER_SKELETON_HURT"
            );
        }

        this.netherWorld = Bukkit.getWorld(NETHER_WORLD);
        if (this.netherWorld == null) {
            warningLog("Мир 'world_nether' не найден!");
        }

        debugLog("Загружена конфигурация: интервал=" + (minInterval/20) + "с-" + (maxInterval/20) +
                "с, звуков=" + soundList.size() + (useCustomSounds ? ", кастомных=" + customSoundList.size() : ""));
    }

    @Override
    public void enable() {
        if (!enabled) {
            infoLog("Модуль отключен в конфиге");
            return;
        }

        if (netherWorld == null) {
            warningLog("Мир 'world_nether' не найден! Модуль не будет работать.");
            return;
        }

        startSoundTask();

        infoLog("Модуль запущен для мира: " + netherWorld.getName());
    }

    @Override
    public void disable() {
        if (soundTask != null && !soundTask.isCancelled()) {
            soundTask.cancel();
            soundTask = null;
        }

        infoLog("Модуль остановлен");
    }

    @Override
    public void triggerEventNow(CommandSender sender, String[] args) {
        if (!enabled) {
            sender.sendMessage("§cМодуль отключен!");
            return;
        }

        if (netherWorld == null) {
            sender.sendMessage("§cМир Незера не найден!");
            return;
        }

        debugLog("Принудительная активация события");
        playSoundForAllPlayers();
        sender.sendMessage("§aЗвук воспроизведен!");
    }

    /**
     * Запускает задачу воспроизведения звуков
     */
    private void startSoundTask() {
        if (soundTask != null && !soundTask.isCancelled()) {
            soundTask.cancel();
        }

        soundTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled) {
                    debugLog("Модуль отключен, задача остановлена");
                    this.cancel();
                    return;
                }

                if (!hasPlayersInWorld(netherWorld)) {
                    debugLog("Нет игроков в Незере, пропускаем звук");
                    return;
                }

                playSoundForAllPlayers();
            }
        }.runTaskTimer(plugin, getRandomInterval(), getRandomInterval());

        debugLog("Задача запущена с интервалом " + (minInterval/20) + "с - " + (maxInterval/20) + "с");
    }

    /**
     * Воспроизводит звук для всех игроков в Незере
     */
    private void playSoundForAllPlayers() {
        String soundName = getRandomSound();
        if (soundName == null) {
            warningLog("Нет доступных звуков для воспроизведения");
            return;
        }

        boolean isCustom = customSoundList != null && customSoundList.contains(soundName);
        List<Player> players = netherWorld.getPlayers();

        debugLog("Воспроизведение звука '" + soundName + "' (" + (isCustom ? "кастомный" : "стандартный") +
                ") для " + players.size() + " игроков");

        for (Player player : players) {
            if (isCustom) {
                playCustomSound(player, soundName);
            } else {
                playVanillaSound(player, soundName);
            }
        }
    }

    /**
     * Воспроизводит стандартный звук
     */
    private void playVanillaSound(Player player, String soundName) {
        try {
            Sound sound = getSoundByName(soundName);
            if (sound == null) {
                warningLog("Звук '" + soundName + "' не найден!");
                return;
            }

            float randomPitch = pitch + (float) ((random.nextDouble() - 0.5) * 0.3);
            player.playSound(player.getLocation(), sound, volume, randomPitch);

            debugLog("Воспроизведен стандартный звук '" + soundName + "' для " + player.getName());

        } catch (Exception e) {
            warningLog("Ошибка при воспроизведении звука '" + soundName + "': " + e.getMessage());
        }
    }

    /**
     * Воспроизводит кастомный звук через команду
     */
    private void playCustomSound(Player player, String soundName) {
        try {
            // Отключаем обратную связь команд чтобы не засорять чат
            boolean rule = player.getWorld().getGameRuleValue(GameRules.SEND_COMMAND_FEEDBACK);
            player.getWorld().setGameRule(GameRules.SEND_COMMAND_FEEDBACK, false);

            float randomPitch = pitch + (float) ((random.nextDouble() - 0.5) * 0.3);
            String command = "playsound " + soundName + " ambient " +
                    player.getName() + " ~ ~ ~ " + volume + " " + randomPitch + " 1";
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);

            player.getWorld().setGameRule(GameRules.SEND_COMMAND_FEEDBACK, rule);

            debugLog("Воспроизведен кастомный звук '" + soundName + "' для " + player.getName());

        } catch (Exception e) {
            warningLog("Ошибка при воспроизведении кастомного звука '" + soundName + "': " + e.getMessage());
        }
    }

    /**
     * Устаревшиц способ получения звуков
     */
    private Sound getSoundByName(String soundName) {
        try {
            NamespacedKey key = NamespacedKey.minecraft(soundName.toLowerCase());
            Sound sound = Registry.SOUNDS.get(key);

            if (sound != null) {
                return sound;
            }

            try {
                return (Sound) Sound.class.getDeclaredField(soundName).get(null);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Выбирает случайный звук из списка
     */
    private String getRandomSound() {
        List<String> availableSounds = new ArrayList<>(soundList);

        if (useCustomSounds && customSoundList != null && !customSoundList.isEmpty()) {
            if (random.nextBoolean()) {
                availableSounds.addAll(customSoundList);
                debugLog("Добавлены кастомные звуки в выборку");
            }
        }

        if (availableSounds.isEmpty()) {
            return null;
        }

        String selected = availableSounds.get(random.nextInt(availableSounds.size()));
        debugLog("Выбран звук: " + selected);
        return selected;
    }

    /**
     * Возвращает случайный интервал в тиках
     */
    private int getRandomInterval() {
        if (maxInterval <= minInterval) {
            return minInterval;
        }
        int interval = minInterval + random.nextInt(maxInterval - minInterval);
        debugLog("Выбран интервал: " + (interval/20) + "с");
        return interval;
    }

    /**
     * Метод для получения статуса модуля
     */
    public String getStatus() {
        StringBuilder status = new StringBuilder();
        status.append("§6=== Модуль ").append(moduleName).append(" ===\n");
        status.append("§7Статус: ").append(enabled ? "§aВключен" : "§cОтключен").append("\n");
        status.append("§7Отладка: ").append(debug ? "§aВключена" : "§cОтключена").append("\n");
        status.append("§7Активен в мире: ");
        if (isEnabledInWorld(netherWorld)) {
            status.append("§a").append(netherWorld != null ? netherWorld.getName() : "неизвестно");
        } else {
            status.append("§cНет");
        }
        status.append("\n");

        long minIntervalSec = minInterval / 20;
        long maxIntervalSec = maxInterval / 20;
        status.append("§7Интервал: §f").append(minIntervalSec).append("с - ")
                .append(maxIntervalSec).append("с\n");
        status.append("§7Звуков: §f").append(soundList.size());
        if (useCustomSounds && customSoundList != null && !customSoundList.isEmpty()) {
            status.append(" (+ ").append(customSoundList.size()).append(" кастомных)");
        }
        status.append("\n");
        status.append("§7Громкость: §f").append(volume);
        status.append("§7, Тон: §f").append(pitch);

        return status.toString();
    }
}