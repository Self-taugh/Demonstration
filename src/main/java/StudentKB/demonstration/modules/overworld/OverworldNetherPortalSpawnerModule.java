package StudentKB.demonstration.modules.overworld;

import StudentKB.demonstration.core.BaseModule;
import StudentKB.demonstration.core.ConfigManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class OverworldNetherPortalSpawnerModule extends BaseModule {
    private int minInterval;
    private int maxInterval;
    private int searchRadius;
    private int maxAttempts;
    private int minDistanceToOtherPortals;
    private boolean playEffects;

    private BukkitTask portalTask;
    private World targetWorld;

    // Константы для портала
    private static final int PORTAL_WIDTH = 4;
    private static final int PORTAL_HEIGHT = 5;
    private static final int MIN_Y = 60;

    // Мини база порталов
    private static final String PORTALS_FILE = "portals.json";
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // Список блоков на которых нельзя ставить
    private static final Set<Material> UNSUITABLE_BLOCKS = new HashSet<>(Arrays.asList(
            Material.WATER,
            Material.LAVA,
            Material.BUBBLE_COLUMN,

            // Все что связано с растениями
            Material.OAK_LEAVES,
            Material.SPRUCE_LEAVES,
            Material.BIRCH_LEAVES,
            Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES,
            Material.DARK_OAK_LEAVES,
            Material.MANGROVE_LEAVES,
            Material.AZALEA_LEAVES,
            Material.FLOWERING_AZALEA_LEAVES,
            Material.OAK_SAPLING,
            Material.SPRUCE_SAPLING,
            Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.MANGROVE_PROPAGULE,
            Material.TALL_GRASS,
            Material.FERN,
            Material.LARGE_FERN,
            Material.DEAD_BUSH,
            Material.VINE,
            Material.CAVE_VINES,
            Material.CAVE_VINES_PLANT,
            Material.TWISTING_VINES,
            Material.TWISTING_VINES_PLANT,
            Material.WEEPING_VINES,
            Material.WEEPING_VINES_PLANT,
            Material.BIG_DRIPLEAF,
            Material.BIG_DRIPLEAF_STEM,
            Material.SMALL_DRIPLEAF,
            Material.SEAGRASS,
            Material.TALL_SEAGRASS,
            Material.KELP,
            Material.KELP_PLANT,

            // Цветы (а то заплачут)
            Material.DANDELION,
            Material.POPPY,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.WITHER_ROSE,
            Material.SUNFLOWER,
            Material.LILAC,
            Material.ROSE_BUSH,
            Material.PEONY,
            Material.SWEET_BERRY_BUSH,
            Material.CACTUS,
            Material.SUGAR_CANE,


            // ОВозможно важные блоки игроков
            Material.CACTUS,
            Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE,
            Material.LANTERN,
            Material.SOUL_LANTERN,
            Material.SLIME_BLOCK,
            Material.HONEY_BLOCK

    ));

    public OverworldNetherPortalSpawnerModule(JavaPlugin plugin, ConfigManager configManager) {
        super(plugin, configManager, "overworld-nether-portal-spawner");
    }

    @Override
    protected void loadConfig() {
        this.enabled = getConfigBoolean("enabled", true);
        this.debug = getConfigBoolean("debug", false);
        this.minInterval = getConfigTimeTicks("min-interval", "20h");
        this.maxInterval = getConfigTimeTicks("max-interval", "26h");
        this.searchRadius = getConfigInt("search-radius", 50);
        this.maxAttempts = getConfigInt("max-attempts", 20);
        this.minDistanceToOtherPortals = getConfigInt("min-distance-to-other-portals", 10);
        this.playEffects = getConfigBoolean("play-effects", true);

        this.targetWorld = Bukkit.getWorld("world");
        if (this.targetWorld == null) {
            warningLog("Мир 'world' не найден!");
        }

        debugLog("Загружена конфигурация: интервал=" + (minInterval/20) + "с-" + (maxInterval/20) +
                "с, радиус=" + searchRadius + ", попыток=" + maxAttempts);
    }

    @Override
    public void enable() {
        if (!enabled) {
            infoLog("Модуль отключен в конфиге");
            return;
        }

        if (targetWorld == null) {
            warningLog("Мир 'world' не найден! Модуль не будет работать.");
            return;
        }

        startPortalTask();
        setWorldState(targetWorld, true);

        infoLog("Модуль запущен для мира: " + targetWorld.getName());
    }

    @Override
    public void disable() {
        if (portalTask != null && !portalTask.isCancelled()) {
            portalTask.cancel();
            portalTask = null;
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
            warningLog("Мир 'world' не найден!");
            return;
        }

        debugLog("Принудительная активация события");
        trySpawnPortal();
    }

    /**
     * Запускает задачу спавна порталов
     */
    private void startPortalTask() {
        if (portalTask != null && !portalTask.isCancelled()) {
            portalTask.cancel();
        }

        portalTask = new BukkitRunnable() {
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

                trySpawnPortal();
            }
        }.runTaskTimer(plugin, getRandomInterval(), getRandomInterval());

        debugLog("Задача запущена с интервалом " + (minInterval/20) + "с - " + (maxInterval/20) + "с");
    }

    /**
     * Пытается заспавнить портал возле игрока
     */
    private void trySpawnPortal() {
        List<Player> players = targetWorld.getPlayers();
        Collections.shuffle(players, random);

        for (Player player : players) {
            debugLog("Проверка игрока: " + player.getName());

            Location portalLocation = findPortalLocation(player);
            if (portalLocation != null) {
                spawnPortal(portalLocation);
                return;
            }
        }

        debugLog("Не удалось найти подходящее место для портала ни для одного игрока");
    }

    /**
     * Ищет место для спавна портала возле игрока
     */
    private Location findPortalLocation(Player player) {
        Location playerLoc = player.getLocation();
        World world = player.getWorld();

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = searchRadius * (0.3 + random.nextDouble() * 0.7);

            double x = playerLoc.getX() + Math.cos(angle) * distance;
            double z = playerLoc.getZ() + Math.sin(angle) * distance;

            Block surfaceBlock = world.getBlockAt((int) x, world.getMaxHeight() - 1, (int) z);
            Material surfaceType = surfaceBlock.getType();

            // Проверяем не попали ли в список запрещенок
            if (UNSUITABLE_BLOCKS.contains(surfaceType)) {
                debugLog("Точка находится в нежелательном блоке: " + surfaceType + ", пропускаем");
                continue;
            }

            // Находим поверхность
            int y = findSurfaceY(world, (int) x, (int) z);
            if (y < MIN_Y) {
                debugLog("Точка ниже Y=60. пропускаем (x=" + (int)x + ", z=" + (int)z + ", y=" + y + ")");
                continue;
            }

            Location location = new Location(world, x, y, z);

            // Проверяем можно ли здесь построить портал
            if (canBuildPortal(location)) {
                debugLog("Найдено место для портала! x=" + (int)x + ", z=" + (int)z + ", y=" + y);
                return location;
            }
        }

        return null;
    }

    /**
     * Находит высоту поверхности (самый высокий не-воздушный блок)
     * Исключает воду, лаву и другие нежелательные блоки
     */
    private int findSurfaceY(World world, int x, int z) {
        // Ищем сверху вниз, начиная с максимальной высоты
        for (int y = world.getMaxHeight() - 1; y > 0; y--) {
            Block block = world.getBlockAt(x, y, z);
            Material type = block.getType();

            // Пропускаем воздух
            if (block.isEmpty()) {
                continue;
            }

            // Проверяем не запрещен ли блок
            if (UNSUITABLE_BLOCKS.contains(type)) {
                debugLog("Найден нежелательный блок " + type + " на y=" + y + ", пропускаем");
                return 0;
            }

            return y + 1;
        }
        return 0;
    }

    /**
     * Проверяет, можно ли построить портал в указанной позиции
     */
    private boolean canBuildPortal(Location location) {
        World world = location.getWorld();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        for (int x = 0; x < PORTAL_WIDTH; x++) {
            Block bottomBlock = world.getBlockAt(baseX + x, baseY - 1, baseZ);
            Material bottomType = bottomBlock.getType();

            // Проверяем что под блоком есть твердый блок
            if (bottomBlock.isEmpty()) {
                debugLog("Под порталом нет блока на x=" + (baseX + x));
                return false;
            }

            if (UNSUITABLE_BLOCKS.contains(bottomType)) {
                debugLog("Под порталом нежелательный блок " + bottomType + " на x=" + (baseX + x));
                return false;
            }
        }

        // Проверяем область портала на наличие запретных блоков
        for (int y = 0; y < PORTAL_HEIGHT; y++) {
            for (int x = 0; x < PORTAL_WIDTH; x++) {
                Block block = world.getBlockAt(baseX + x, baseY + y, baseZ);
                Material type = block.getType();

                if (UNSUITABLE_BLOCKS.contains(type)) {
                    debugLog("В области портала найден нежелательный блок " + type +
                            " на x=" + (baseX + x) + ", y=" + (baseY + y));
                    return false;
                }

                if (x == 0 || x == PORTAL_WIDTH - 1 || y == 0 || y == PORTAL_HEIGHT - 1) {
                    if (!block.isEmpty() && !UNSUITABLE_BLOCKS.contains(type)) {
                        if (!block.isLiquid() && !block.isPassable()) {
                            debugLog("Блок занят: " + type + " at " + block.getLocation());
                            return false;
                        }
                    }
                }
            }
        }

        // Проверяем, что внутренняя область пуста
        for (int y = 1; y < PORTAL_HEIGHT - 1; y++) {
            for (int x = 1; x < PORTAL_WIDTH - 1; x++) {
                Block block = world.getBlockAt(baseX + x, baseY + y, baseZ);
                if (!block.isEmpty() && !UNSUITABLE_BLOCKS.contains(block.getType())) {
                    return false;
                }
            }
        }

        // Проверяем расстояние до других порталов
        if (isNearOtherPortal(location)) {
            debugLog("Слишком близко к другому порталу");
            return false;
        }

        return true;
    }

    /**
     * Проверяет, есть ли активный портал рядом
     */
    private boolean isNearOtherPortal(Location location) {
        World world = location.getWorld();
        int centerX = location.getBlockX() + PORTAL_WIDTH / 2;
        int centerY = location.getBlockY() + PORTAL_HEIGHT / 2;
        int centerZ = location.getBlockZ();

        // Проверяем куб вокруг портала
        int radius = minDistanceToOtherPortals;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block block = world.getBlockAt(centerX + x, centerY + y, centerZ + z);
                    if (block.getType() == Material.NETHER_PORTAL) {
                        debugLog("Найден другой портал на расстоянии " +
                                Math.sqrt(x*x + y*y + z*z) + " блоков");
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Спавнит портал в указанной позиции
     */
    private void spawnPortal(Location location) {
        World world = location.getWorld();
        int baseX = location.getBlockX();
        int baseY = location.getBlockY();
        int baseZ = location.getBlockZ();

        for (int y = 0; y < PORTAL_HEIGHT; y++) {
            for (int x = 0; x < PORTAL_WIDTH; x++) {
                // Строим рамку
                if (x == 0 || x == PORTAL_WIDTH - 1 || y == 0 || y == PORTAL_HEIGHT - 1) {
                    Block block = world.getBlockAt(baseX + x, baseY + y, baseZ);
                    block.setType(Material.OBSIDIAN);
                    debugLog("Поставлен обсидиан на " + block.getLocation());
                }
            }
        }

        // Ставим блоки порталов
        for (int y = 1; y < PORTAL_HEIGHT - 1; y++) {
            for (int x = 1; x < PORTAL_WIDTH - 1; x++) {
                Block block = world.getBlockAt(baseX + x, baseY + y, baseZ);
                block.setType(Material.NETHER_PORTAL);
                debugLog("Поставлен портал на " + block.getLocation());
            }
        }

        // Сохраняем информацию о портале в файл
        savePortalLocation(location);

        if (playEffects) {
            spawnPortalEffects(location);
        }

        infoLog("Портал заспавнен на координатах: " +
                location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
    }

    /**
     * Сохраняет координаты портала в файл
     */
    private void savePortalLocation(Location location) {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            File portalFile = new File(dataFolder, PORTALS_FILE);
            JsonObject root;

            // Если файл существует, загружаем его
            if (portalFile.exists()) {
                try (Reader reader = new InputStreamReader(new FileInputStream(portalFile), StandardCharsets.UTF_8)) {
                    root = gson.fromJson(reader, JsonObject.class);
                }
            } else {
                root = new JsonObject();
            }

            String worldName = location.getWorld().getName();

            // Получаем или создаем массив для мира
            JsonArray worldPortals;
            if (root.has(worldName)) {
                worldPortals = root.getAsJsonArray(worldName);
            } else {
                worldPortals = new JsonArray();
                root.add(worldName, worldPortals);
            }

            // Создаем объект портала
            JsonObject portalData = new JsonObject();
            portalData.addProperty("x", location.getBlockX() + PORTAL_WIDTH / 2);
            portalData.addProperty("y", location.getBlockY() + PORTAL_HEIGHT / 2);
            portalData.addProperty("z", location.getBlockZ());

            // Добавляем в массив
            worldPortals.add(portalData);

            // Сохраняем файл
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(portalFile), StandardCharsets.UTF_8)) {
                gson.toJson(root, writer);
            }

            debugLog("Информация о портале сохранена в файл: " +
                    "x=" + portalData.get("x").getAsInt() +
                    ", y=" + portalData.get("y").getAsInt() +
                    ", z=" + portalData.get("z").getAsInt() +
                    " в мире " + worldName);

        } catch (Exception e) {
            warningLog("Не удалось сохранить информацию о портале: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Создает эффекты при спавне портала
     */
    private void spawnPortalEffects(Location location) {
        World world = location.getWorld();
        int centerX = location.getBlockX() + PORTAL_WIDTH / 2;
        int centerY = location.getBlockY() + PORTAL_HEIGHT / 2;
        int centerZ = location.getBlockZ();

        Location center = new Location(world, centerX + 0.5, centerY + 0.5, centerZ + 0.5);

        // Создаем вспышку частиц
        for (int i = 0; i < 30; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double radius = 2 + random.nextDouble() * 3;
            double height = random.nextDouble() * PORTAL_HEIGHT;

            double x = centerX + 0.5 + Math.cos(angle) * radius;
            double y = centerY + height;
            double z = centerZ + 0.5 + Math.sin(angle) * radius;

            Location particleLoc = new Location(world, x, y, z);
            world.spawnParticle(
                    Particle.FLAME,
                    particleLoc,
                    5,
                    0.3, 0.3, 0.3,
                    0.1
            );
        }

        // Пепел
        world.spawnParticle(
                Particle.ASH,
                center,
                1,
                0, 0, 0,
                0
        );

        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        world.playSound(center, Sound.BLOCK_PORTAL_TRAVEL, 0.5f, 0.5f);

        // Долгоживущие частицы
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 20) {
                    this.cancel();
                    return;
                }

                for (int i = 0; i < 10; i++) {
                    double x = centerX + 0.5 + (random.nextDouble() - 0.5) * PORTAL_WIDTH;
                    double y = centerY + random.nextDouble() * PORTAL_HEIGHT;
                    double z = centerZ + 0.5 + (random.nextDouble() - 0.5) * 0.5;

                    Location particleLoc = new Location(world, x, y, z);
                    world.spawnParticle(
                            Particle.PORTAL,
                            particleLoc,
                            2,
                            0.2, 0.2, 0.2,
                            0.1
                    );
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0, 2);
    }

    /**
     * Возвращает случайный интервал в тиках
     */
    private int getRandomInterval() {
        if (maxInterval <= minInterval) {
            return minInterval;
        }
        int interval = minInterval + random.nextInt(maxInterval - minInterval);
        debugLog("Выбран интервал: " + (interval/20/60/60) + "ч " + ((interval/20/60)%60) + "м");
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

        status.append("§7Радиус поиска: §f").append(searchRadius).append(" блоков\n");
        status.append("§7Максимум попыток: §f").append(maxAttempts).append("\n");
        status.append("§7Расстояние до других порталов: §f").append(minDistanceToOtherPortals).append(" блоков\n");
        status.append("§7Эффекты: ").append(playEffects ? "§aВключены" : "§cОтключены");

        return status.toString();
    }
}