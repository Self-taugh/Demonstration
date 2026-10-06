package StudentKB.demonstration.modules.end;

import StudentKB.demonstration.core.BaseModule;
import StudentKB.demonstration.core.ConfigManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.MultipleFacing;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class EndSculkGrowModule extends BaseModule {
    private int minInterval;
    private int maxInterval;
    private int maxDistance;
    private int minGrowth;
    private int maxGrowth;
    private boolean playEffects;
    private String growthMode;

    private BukkitTask sculkTask;
    private World targetWorld;

    // Список возможных направлений блоков для спавна
    private static final Set<BlockFace> FACES = new HashSet<>(Arrays.asList(
            BlockFace.UP,
            BlockFace.DOWN,
            BlockFace.NORTH,
            BlockFace.SOUTH,
            BlockFace.EAST,
            BlockFace.WEST
    ));

    public EndSculkGrowModule(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, configManager, "end-sculk-grow");
    }

    @Override
    protected void loadConfig() {
        this.enabled = getConfigBoolean("enabled", true);
        this.debug = getConfigBoolean("debug", false);
        this.minInterval = getConfigTimeTicks("min-interval", "30m");
        this.maxInterval = getConfigTimeTicks("max-interval", "2h");
        this.maxDistance = getConfigInt("max-distance", 20);
        this.minGrowth = getConfigInt("min-growth", 1);
        this.maxGrowth = getConfigInt("max-growth", 5);
        this.playEffects = getConfigBoolean("play-effects", true);
        this.growthMode = getConfigString("growth-mode", "scattered");
        this.targetWorld = Bukkit.getWorld("world_the_end");
        if (this.targetWorld == null) {
            warningLog("Мир 'world_the_end' не найден!");
        }

        debugLog("Загружена конфигурация: интервал=" + (minInterval/20) + "с-" + (maxInterval/20) +
                "с, макс. расстояние=" + maxDistance + ", рост=" + minGrowth + "-" + maxGrowth +
                ", режим роста=" + growthMode);
    }

    @Override
    public void enable() {
        if (!enabled) {
            infoLog("Модуль отключен в конфиге");
            return;
        }

        if (targetWorld == null) {
            warningLog("Мир 'world_the_end' не найден! Модуль не будет работать.");
            return;
        }

        startSculkTask();
        setWorldState(targetWorld, true);

        infoLog("Модуль запущен для мира: " + targetWorld.getName());
    }

    @Override
    public void disable() {
        if (sculkTask != null && !sculkTask.isCancelled()) {
            sculkTask.cancel();
            sculkTask = null;
        }
        worldStates.clear();

        infoLog("Модуль остановлен");
    }

    @Override
    public void triggerEventNow(CommandSender sender, String[] args) {
        if (!enabled) {
            infoLog("Модуль отключен, невозможно активировать событие");
            return;
        }

        if (targetWorld == null) {
            warningLog("Мир 'world_the_end' не найден!");
            return;
        }

        debugLog("Принудительная активация события");
        tryGrowSculk();
    }

    /**
     * Запускает задачу роста скалка
     */
    private void startSculkTask() {
        if (sculkTask != null && !sculkTask.isCancelled()) {
            sculkTask.cancel();
        }

        sculkTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!enabled || !isEnabledInWorld(targetWorld)) {
                    debugLog("Модуль отключен или не активен в мире, задача остановлена");
                    this.cancel();
                    return;
                }

                if (!hasPlayersInWorld(targetWorld)) {
                    debugLog("Нет игроков в мире " + targetWorld.getName() + ", пропускаем событие");
                    return;
                }

                tryGrowSculk();
            }
        }.runTaskTimer(plugin, getRandomInterval(), getRandomInterval());

        debugLog("Задача запущена с интервалом " + (minInterval/20) + "с - " + (maxInterval/20) + "с");
    }

    /**
     * Пытается вырастить скалк возле игрока
     */
    private void tryGrowSculk() {
        List<Player> players = targetWorld.getPlayers();

        // Перемешиваем список игроков для случайного выбора
        Collections.shuffle(players, random);

        for (Player player : players) {
            debugLog("Проверка игрока: " + player.getName());

            int count = minGrowth + random.nextInt(maxGrowth - minGrowth + 1);
            debugLog("Будет заспавнено " + count + " жил");

            String currentMode = getCurrentMode();
            debugLog("Выбран режим роста: " + currentMode);

            List<Location> spawnedLocations = new ArrayList<>();

            if (currentMode.equalsIgnoreCase("cluster")) {
                // Режим кластера
                spawnCluster(player, count, spawnedLocations);
            } else {
                // Режим разброса
                for (int i = 0; i < count; i++) {
                    Location sculkLocation = findSculkLocation(player);
                    if (sculkLocation != null) {
                        spawnSculkVein(sculkLocation);
                        spawnedLocations.add(sculkLocation);
                        debugLog("Заспавнена жила #" + (i + 1) + " на " + sculkLocation);
                    } else {
                        debugLog("Не удалось найти место для жилы #" + (i + 1));
                    }
                }
            }

            // Если удалось заспавнить хотя бы одну жилу - выходим
            if (!spawnedLocations.isEmpty()) {
                debugLog("Успешно заспавнено " + spawnedLocations.size() + " жил для игрока " + player.getName());
                return;
            }
        }

        debugLog("Не удалось заспавнить скалк ни для одного игрока");
    }

    /**
     * Определяет режим роста для текущего события
     */
    private String getCurrentMode() {
        if (growthMode.equalsIgnoreCase("random")) {
            // Случайный режим - выбираем между scattered и cluster
            return random.nextBoolean() ? "scattered" : "cluster";
        }
        return growthMode;
    }

    /**
     * Спавнит кластер скалковых жил
     */
    private void spawnCluster(Player player, int count, List<Location> spawnedLocations) {
        // Находим центр кластера
        Location centerLocation = findSculkLocation(player);
        if (centerLocation == null) {
            debugLog("Не удалось найти центр кластера");
            return;
        }

        // Спавним центр кластера
        spawnSculkVein(centerLocation);
        spawnedLocations.add(centerLocation);
        count--;
        debugLog("Заспавнен центр кластера на " + centerLocation);

        // Собираем все подходящие блоки вокруг центра
        Set<Location> availableLocations = new HashSet<>();
        collectAvailableLocations(centerLocation, availableLocations);
        debugLog("Найдено " + availableLocations.size() + " доступных мест вокруг центра");

        // Перемешиваем доступные локации
        List<Location> shuffledLocations = new ArrayList<>(availableLocations);
        Collections.shuffle(shuffledLocations, random);

        // Спавним жилы
        for (Location location : shuffledLocations) {
            if (count <= 0) {
                break;
            }
            spawnSculkVein(location);
            spawnedLocations.add(location);
            count--;
            debugLog("Заспавнена жила кластера на " + location);
        }

        if (count > 0) {
            debugLog("Не удалось найти достаточно мест для кластера, осталось " + count + " жил");
        }
    }

    /**
     * Собирает все доступные локации вокруг указанной позиции
     */
    private void collectAvailableLocations(Location center, Set<Location> availableLocations) {
        World world = center.getWorld();
        Block centerBlock = center.getBlock();

        // Проверяем блоки в радиусе 3 блоков от центра
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                for (int dz = -3; dz <= 3; dz++) {
                    // Пропускаем центр
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }

                    Block block = world.getBlockAt(centerBlock.getX() + dx,
                            centerBlock.getY() + dy,
                            centerBlock.getZ() + dz);

                    Location location = block.getLocation();

                    // Проверяем, можно ли здесь заспавнить жилу
                    if (canSpawnAtLocation(location)) {
                        availableLocations.add(location);
                    }
                }
            }
        }
    }

    /**
     * Проверяет, можно ли заспавнить жилу в указанной позиции
     */
    private boolean canSpawnAtLocation(Location location) {
        World world = location.getWorld();
        Block checkBlock = world.getBlockAt(location);

        // Блок должен быть воздухом или заменяемым
        if (!checkBlock.isEmpty() && checkBlock.getType() != Material.AIR) {
            return false;
        }

        // Проверяем все 6 сторон блока
        for (BlockFace face : FACES) {
            Block relativeBlock = checkBlock.getRelative(face);
            // Блок, на который крепится жила, должен быть твердым
            if (relativeBlock.getType().isSolid()) {
                return true;
            }
        }

        return false;
    }

    /**
     * Ищет место для спавна скалковой жилы
     */
    private Location findSculkLocation(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        for (int attempt = 0; attempt < 100; attempt++) {
            double angle1 = random.nextDouble() * 2 * Math.PI;
            double angle2 = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * maxDistance;

            double x = playerLoc.getX() + Math.sin(angle1) * Math.cos(angle2) * distance;
            double y = playerLoc.getY() + Math.sin(angle2) * distance;
            double z = playerLoc.getZ() + Math.cos(angle1) * Math.cos(angle2) * distance;

            if (y < 0) y = 10;
            if (y > world.getMaxHeight() - 5) y = world.getMaxHeight() - 10;

            Location location = new Location(world, x, y, z);

            if (canSpawnAtLocation(location)) {
                debugLog("Найдено место для жилы на " + location);
                return location;
            }
        }

        return null;
    }

    /**
     * Спавнит скалковую жилу в указанной позиции
     */
    private void spawnSculkVein(Location location) {
        World world = location.getWorld();
        Block block = world.getBlockAt(location);

        // Получаем блок данных для жилы
        MultipleFacing sculkData = (MultipleFacing) Material.SCULK_VEIN.createBlockData();

        // Проверяем все стороны и добавляем те, которые прилегают к твердому блоку
        boolean hasFace = false;
        for (BlockFace face : FACES) {
            Block relativeBlock = block.getRelative(face);
            if (relativeBlock.getType().isSolid() && !relativeBlock.isEmpty()) {
                sculkData.setFace(face, true);
                hasFace = true;
                debugLog("Жила прикреплена к стороне: " + face);
            }
        }

        // Если нет подходящей стороны, ставим жилу-блок
        if (!hasFace) {
            debugLog("Не найдено подходящих сторон для жилы, ставим жилу-блок");
            for (BlockFace face : FACES) {
                sculkData.setFace(face, true);
            }
        }

        block.setBlockData(sculkData);

        if (playEffects) {
            spawnSculkEffects(location);
        }

        debugLog("Скалковая жила заспавнена на " + location);
    }

    /**
     * Создает эффекты при спавне скалка
     */
    private void spawnSculkEffects(Location location) {
        World world = location.getWorld();

        // Частицы скалка
        world.spawnParticle(
                Particle.SCULK_SOUL,
                location.clone().add(0.5, 0.5, 0.5),
                20,
                0.3, 0.3, 0.3,
                0.05
        );

        // Дополнительные частицы для эффекта роста
        world.spawnParticle(
                Particle.SCULK_CHARGE,
                location.clone().add(0.5, 0.5, 0.5),
                10,
                0.5, 0.5, 0.5,
                0.1,1f
        );

        // Звук роста скалка
        world.playSound(
                location,
                Sound.BLOCK_SCULK_CATALYST_BLOOM,
                1.0f,
                0.8f + random.nextFloat() * 0.4f
        );

        // Другой звук скалка
        world.playSound(
                location,
                Sound.BLOCK_SCULK_SENSOR_CLICKING,
                0.5f,
                0.5f + random.nextFloat() * 0.3f
        );

        // Долгоживущие частицы
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 10) {
                    this.cancel();
                    return;
                }

                world.spawnParticle(
                        Particle.SCULK_SOUL,
                        location.clone().add(0.5, 0.5, 0.5),
                        5,
                        0.4, 0.4, 0.4,
                        0.03
                );
                ticks++;
            }
        }.runTaskTimer(plugin, 2, 2);
    }

    /**
     * Возвращает случайный интервал в тиках
     */
    private int getRandomInterval() {
        if (maxInterval <= minInterval) {
            return minInterval;
        }
        int interval = minInterval + random.nextInt(maxInterval - minInterval);
        debugLog("Выбран интервал: " + (interval/20/60) + "м " + (interval/20%60) + "с");
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

        if (isEnabledInWorld(targetWorld)) {
            status.append("§a").append(targetWorld != null ? targetWorld.getName() : "неизвестно");
        } else {
            status.append("§cНет");
        }
        status.append("\n");

        long minIntervalSec = minInterval / 20;
        long maxIntervalSec = maxInterval / 20;
        status.append("§7Интервал: §f");
        if (minIntervalSec >= 3600) {
            status.append(minIntervalSec / 3600).append("ч ");
            status.append((minIntervalSec % 3600) / 60).append("м");
        } else if (minIntervalSec >= 60) {
            status.append(minIntervalSec / 60).append("м");
        } else {
            status.append(minIntervalSec).append("с");
        }
        status.append(" - ");
        if (maxIntervalSec >= 3600) {
            status.append(maxIntervalSec / 3600).append("ч ");
            status.append((maxIntervalSec % 3600) / 60).append("м");
        } else if (maxIntervalSec >= 60) {
            status.append(maxIntervalSec / 60).append("м");
        } else {
            status.append(maxIntervalSec).append("с");
        }
        status.append("\n");

        status.append("§7Макс. расстояние: §f").append(maxDistance).append(" блоков\n");
        status.append("§7Количество жил: §f").append(minGrowth).append("-").append(maxGrowth).append("\n");
        status.append("§7Режим роста: §f").append(growthMode).append("\n");
        status.append("§7Эффекты: ").append(playEffects ? "§aВключены" : "§cОтключены");

        return status.toString();
    }
}