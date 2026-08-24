package dev.axziom.features.modules.world;

import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.integration.XaeroIntegration;
import dev.axziom.util.network.WebhookUtil;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class StashFinder extends Module {
    public enum NotificationMode { CHAT, TOAST, BOTH }

    public final Setting<Integer> minimumStorageCount = num("MinimumStorage", 4, 1, 256);
    public final Setting<Boolean> shulkerInstantHit = bool("ShulkerInstantHit", true);
    public final Setting<Boolean> crafterInstantHit = bool("CrafterInstantHit", true);
    public final Setting<Boolean> disableOnTeleport = bool("DisableOnTeleportOrDeath", false);
    public final Setting<Boolean> disconnectOnStashFound = bool("DisconnectOnStashFound", false);
    public final Setting<Boolean> ignoreTrialChambers = bool("IgnoreTrialChambers", true);
    public final Setting<Double> minimumDistance = num("MinimumDistance", 0.0, 0.0, 30_000_000.0);
    public final Setting<Boolean> onlyOldChunks = bool("OnlyOldChunks", false);
    public final Setting<Boolean> saveToWaypoints = bool("SaveToWaypoints", true);
    public final Setting<Boolean> sendNotifications = bool("SendNotifications", true);
    public final Setting<NotificationMode> notificationMode = mode("NotificationMode", NotificationMode.BOTH);
    public final Setting<Boolean> sendWebhook = bool("SendWebhook", false).setPage("Webhook");
    public final Setting<String> webhookLink = str("WebhookLink", "").setPage("Webhook");
    public final Setting<Boolean> advancedLogging = bool("AdvancedLogging", true).setPage("Webhook");
    public final Setting<Boolean> ping = bool("Ping", false).setPage("Webhook");
    public final Setting<String> discordId = str("DiscordID", "").setPage("Webhook");

    private final Map<String, StashRecord> stashes = new LinkedHashMap<>();
    private final ArrayDeque<ChunkPos> pending = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();
    private final Map<Long, Integer> lastScan = new HashMap<>();
    private Vec3 lastPosition;
    private ClientLevel trackedLevel;
    private boolean warnedXaero;
    private int tick;

    public StashFinder() {
        super("StashFinder", "Records storage-heavy chunks and optionally creates Xaero waypoints.", Category.WORLD);
    }

    @Override
    public void onEnable() {
        resetScanner();
        warnedXaero = false;
        load();
    }

    @Override
    public void onDisable() {
        save();
        resetScanner();
    }

    @Override
    public void onTick() {
        if (nullCheck()) return;
        if (trackedLevel != mc.level) resetScanner();
        trackedLevel = mc.level;

        if (disableOnTeleport.getValue()) {
            if (mc.player.isDeadOrDying()) {
                Command.sendMessage("{red} StashFinder disabled after death.");
                disable();
                return;
            }
            if (lastPosition != null && lastPosition.distanceToSqr(mc.player.position()) > 1024.0 * 1024.0) {
                Command.sendMessage("{red} StashFinder disabled after a large position change.");
                disable();
                return;
            }
        }
        lastPosition = mc.player.position();

        tick++;
        if (tick == 1 || tick % 20 == 0) queueAroundPlayer();
        int budget = 2;
        while (budget-- > 0) {
            ChunkPos pos = pending.poll();
            if (pos == null) break;
            queued.remove(pos.toLong());
            if (!mc.level.hasChunk(pos.x, pos.z)) continue;
            processChunk(mc.level, mc.level.getChunk(pos.x, pos.z));
            lastScan.put(pos.toLong(), tick);
        }
    }

    private void queueAroundPlayer() {
        ChunkPos center = mc.player.chunkPosition();
        int radius = Math.min(mc.options.renderDistance().get() + 1, 16);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int x = center.x + dx;
                int z = center.z + dz;
                if (!mc.level.hasChunk(x, z)) continue;
                ChunkPos pos = new ChunkPos(x, z);
                long key = pos.toLong();
                if (tick - lastScan.getOrDefault(key, Integer.MIN_VALUE) >= 200 && queued.add(key)) pending.add(pos);
            }
        }
    }

    private void processChunk(ClientLevel world, LevelChunk chunk) {
        ChunkPos pos = chunk.getPos();
        if (Math.hypot(Math.abs(pos.x * 16.0), Math.abs(pos.z * 16.0)) < minimumDistance.getValue()) return;

        boolean newChunk = false;
        boolean oldChunk = false;
        if (XaeroIntegration.xaeroPlusAvailable()) {
            newChunk = XaeroIntegration.isNewChunk(pos.x, pos.z, world.dimension());
            oldChunk = XaeroIntegration.isOldChunk(pos.x, pos.z, world.dimension());
            if (onlyOldChunks.getValue() && newChunk && !oldChunk) return;
        } else if (onlyOldChunks.getValue()) {
            if (!warnedXaero) {
                Command.sendMessage("{red} XaeroPlus was not found; OnlyOldChunks cannot be enforced.");
                warnedXaero = true;
            }
            return;
        }

        StashRecord record = new StashRecord();
        record.chunkX = pos.x;
        record.chunkZ = pos.z;
        record.x = pos.getMiddleBlockX();
        record.z = pos.getMiddleBlockZ();
        record.dimension = world.dimension().identifier().toString();
        record.newChunk = newChunk;
        record.oldChunk = oldChunk;

        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (ignoreTrialChambers.getValue()) {
                var under = world.getBlockState(entity.getBlockPos().below()).getBlock();
                if (under == Blocks.WAXED_OXIDIZED_CUT_COPPER || under == Blocks.TUFF_BRICKS
                        || under == Blocks.WAXED_COPPER_BLOCK || under == Blocks.WAXED_OXIDIZED_COPPER) continue;
            }
            if (entity instanceof ChestBlockEntity) record.chests++;
            else if (entity instanceof BarrelBlockEntity) record.barrels++;
            else if (entity instanceof ShulkerBoxBlockEntity) record.shulkers++;
            else if (entity instanceof EnderChestBlockEntity) record.enderChests++;
            else if (entity instanceof AbstractFurnaceBlockEntity) record.furnaces++;
            else if (entity instanceof DispenserBlockEntity) record.dispensersDroppers++;
            else if (entity instanceof HopperBlockEntity) record.hoppers++;
            else if (entity instanceof CrafterBlockEntity) record.crafters++;
        }

        if (record.total() < minimumStorageCount.getValue()
                && !(shulkerInstantHit.getValue() && record.shulkers > 0)
                && !(crafterInstantHit.getValue() && record.crafters > 0)) return;

        String key = record.dimension + ":" + record.chunkX + ":" + record.chunkZ;
        StashRecord previous = stashes.put(key, record);
        save();
        if (previous != null && record.sameCounts(previous)) return;

        notifyStash(record);
        if (saveToWaypoints.getValue()) {
            XaeroIntegration.addWaypoint(record.x, 70, record.z, record.waypointName(), "S",
                    record.total() < 15 ? 10 : record.total() < 50 ? 14 : record.total() < 100 ? 12 : 4);
        }
        if (disconnectOnStashFound.getValue()) {
            disconnectOnStashFound.setValue(false);
            mc.disconnectFromWorld(Component.literal("[StashFinder] Found stash at " + record.x + ", " + record.z));
        }
    }

    private void notifyStash(StashRecord record) {
        String message = "Found stash at " + record.x + ", " + record.z + " (" + record.total() + " storage blocks).";
        if (sendNotifications.getValue()) {
            if (notificationMode.getValue() != NotificationMode.TOAST) Command.sendMessage(message);
            if (notificationMode.getValue() != NotificationMode.CHAT) {
                SystemToast.addOrUpdate(mc.getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                        Component.literal("Stash Found"), Component.literal(record.x + ", " + record.z));
            }
        }
        if (sendWebhook.getValue() && !webhookLink.getValue().isBlank()) {
            String mention = ping.getValue() && !discordId.getValue().isBlank() ? "<@" + discordId.getValue() + "> " : "";
            String details = advancedLogging.getValue() ? message + " Chests=" + record.chests + ", Barrels=" + record.barrels
                    + ", Shulkers=" + record.shulkers + ", EnderChests=" + record.enderChests + ", Hoppers="
                    + record.hoppers + ", Dispensers/Droppers=" + record.dispensersDroppers + ", Furnaces="
                    + record.furnaces + ", Crafters=" + record.crafters + ", new=" + record.newChunk + ", old=" + record.oldChunk
                    : message;
            WebhookUtil.send(webhookLink.getValue(), "Stash Found", mention + details,
                    mc.player == null ? "unknown" : mc.player.getGameProfile().name());
        }
    }

    private Path folder() {
        return Path.of(System.getProperty("user.dir"), "config", "jewdust", "stash-finder");
    }

    private void load() {
        stashes.clear();
        Path file = folder().resolve("stashes.csv");
        if (!Files.exists(file)) return;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("x,")) continue;
                String[] values = line.split(",", -1);
                if (values.length < 13) continue;
                try {
                    StashRecord record = StashRecord.from(values);
                    stashes.put(record.dimension + ":" + record.chunkX + ":" + record.chunkZ, record);
                } catch (RuntimeException ignored) {
                }
            }
        } catch (Exception exception) {
            Command.sendMessage("{red} Could not load stash file: %s", exception.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(folder());
            try (BufferedWriter writer = Files.newBufferedWriter(folder().resolve("stashes.csv"))) {
                writer.write("x,z,chests,barrels,shulkers,ender_chests,furnaces,dispensers_droppers,hoppers,crafters,dimension,new_chunk,old_chunk\n");
                for (StashRecord record : stashes.values()) {
                    writer.write(record.csv());
                    writer.newLine();
                }
            }
        } catch (Exception exception) {
            Command.sendMessage("{red} Could not save stash file: %s", exception.getMessage());
        }
    }

    @Override
    public String getDisplayInfo() {
        return Integer.toString(stashes.size());
    }

    private void resetScanner() {
        pending.clear();
        queued.clear();
        lastScan.clear();
        lastPosition = null;
        trackedLevel = null;
        tick = 0;
    }

    public static final class StashRecord {
        public int chunkX, chunkZ, x, z, chests, barrels, shulkers, enderChests, furnaces,
                dispensersDroppers, hoppers, crafters;
        public String dimension;
        public boolean newChunk, oldChunk;

        public int total() {
            return chests + barrels + shulkers + enderChests + furnaces + dispensersDroppers + hoppers + crafters;
        }

        public boolean sameCounts(StashRecord other) {
            return other != null && chests == other.chests && barrels == other.barrels && shulkers == other.shulkers
                    && enderChests == other.enderChests && furnaces == other.furnaces
                    && dispensersDroppers == other.dispensersDroppers && hoppers == other.hoppers && crafters == other.crafters;
        }

        public String waypointName() {
            StringBuilder name = new StringBuilder();
            if (chests > 0) name.append("C").append(chests);
            if (barrels > 0) name.append("B").append(barrels);
            if (shulkers > 0) name.append("S").append(shulkers);
            if (enderChests > 0) name.append("E").append(enderChests);
            if (furnaces > 0) name.append("F").append(furnaces);
            if (dispensersDroppers > 0) name.append("D").append(dispensersDroppers);
            if (hoppers > 0) name.append("H").append(hoppers);
            if (crafters > 0) name.append("R").append(crafters);
            return name.toString();
        }

        public String csv() {
            return x + "," + z + "," + chests + "," + barrels + "," + shulkers + "," + enderChests + ","
                    + furnaces + "," + dispensersDroppers + "," + hoppers + "," + crafters + "," + dimension
                    + "," + newChunk + "," + oldChunk;
        }

        public static StashRecord from(String[] values) {
            StashRecord record = new StashRecord();
            record.x = Integer.parseInt(values[0]);
            record.z = Integer.parseInt(values[1]);
            record.chunkX = (record.x - 8) >> 4;
            record.chunkZ = (record.z - 8) >> 4;
            record.chests = Integer.parseInt(values[2]);
            record.barrels = Integer.parseInt(values[3]);
            record.shulkers = Integer.parseInt(values[4]);
            record.enderChests = Integer.parseInt(values[5]);
            record.furnaces = Integer.parseInt(values[6]);
            record.dispensersDroppers = Integer.parseInt(values[7]);
            record.hoppers = Integer.parseInt(values[8]);
            record.crafters = Integer.parseInt(values[9]);
            record.dimension = values[10];
            record.newChunk = Boolean.parseBoolean(values[11]);
            record.oldChunk = Boolean.parseBoolean(values[12]);
            return record;
        }
    }
}
