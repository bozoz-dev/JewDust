package dev.axziom.features.modules.movement;

import dev.axziom.features.commands.Command;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import dev.axziom.util.inventory.InventoryUtil;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class ElytraSwap extends Module {
    private static final int CHEST_MENU_SLOT = 6;

    public final Setting<Integer> durabilityThreshold = num("DurabilityThreshold", 10, 1, 100);
    public final Setting<Boolean> onlyWhileFlying = bool("OnlyWhileFlying", false);
    public final Setting<Boolean> pauseInInventory = bool("PauseInInventory", true);
    public final Setting<Integer> swapCooldown = num("SwapCooldown", 100, 20, 200);
    public final Setting<Boolean> notifySwap = bool("NotifySwap", true);
    public final Setting<Boolean> swapOnHit = bool("SwapOnHit", false).setPage("CombatProtection");
    public final Setting<Integer> hitProtectionDuration = num("ProtectionDuration", 60, 20, 200).setPage("CombatProtection");
    public final Setting<Boolean> autoSwapBack = bool("AutoSwapBack", true).setPage("CombatProtection");
    public final Setting<Boolean> prioritizeNetherite = bool("PrioritizeNetherite", true).setPage("CombatProtection");

    private int cooldown;
    private int protectionTicks;
    private int lastHurtTime;
    private boolean protectionActive;

    public ElytraSwap() {
        super("ElytraSwap", "Swaps damaged elytras and can temporarily equip a chestplate after a hit.", Category.MOVEMENT);
    }

    @Override
    public void onEnable() {
        resetState();
    }

    @Override
    public void onDisable() {
        resetState();
    }

    @Override
    public void onTick() {
        if (nullCheck() || mc.gameMode == null || mc.player.isDeadOrDying()) return;
        if (pauseInInventory.getValue() && mc.player.containerMenu != mc.player.inventoryMenu) return;

        if (cooldown > 0) cooldown--;
        if (swapOnHit.getValue()) handleProtection();
        if (protectionActive || cooldown > 0) return;

        ItemStack chest = mc.player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.is(Items.ELYTRA) || onlyWhileFlying.getValue() && !mc.player.isFallFlying()) return;
        int maximum = chest.getMaxDamage();
        if (maximum <= 0) return;
        int durability = (maximum - chest.getDamageValue()) * 100 / maximum;
        if (durability > durabilityThreshold.getValue()) return;

        int slot = findBestElytra(durabilityThreshold.getValue() + 1);
        if (slot < 0) {
            notify("{red} No healthier elytra was found.");
            cooldown = 20;
            return;
        }
        if (swapInventoryWithChest(slot)) {
            cooldown = swapCooldown.getValue();
            notify("Swapped to a healthier elytra.");
        }
    }

    private void handleProtection() {
        int hurtTime = mc.player.hurtTime;
        if (hurtTime > 0 && hurtTime != lastHurtTime) {
            lastHurtTime = hurtTime;
            if (!protectionActive && mc.player.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
                int slot = findBestChestplate();
                if (slot >= 0 && swapInventoryWithChest(slot)) {
                    protectionActive = true;
                    protectionTicks = hitProtectionDuration.getValue();
                    notify("Equipped a chestplate for protection.");
                }
            } else if (protectionActive) {
                protectionTicks = hitProtectionDuration.getValue();
            }
        }

        if (!protectionActive) return;
        if (protectionTicks > 0) protectionTicks--;
        if (protectionTicks <= 0 && autoSwapBack.getValue()) {
            int slot = findBestElytra(1);
            if (slot >= 0 && swapInventoryWithChest(slot)) {
                protectionActive = false;
                cooldown = swapCooldown.getValue();
                notify("Swapped back to the elytra.");
            }
        }
    }

    private int findBestElytra(int minimumPercent) {
        int bestSlot = -1;
        int bestDurability = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = mc.player.getInventory().getItem(slot);
            if (!stack.is(Items.ELYTRA) || stack.getMaxDamage() <= 0) continue;
            int percent = (stack.getMaxDamage() - stack.getDamageValue()) * 100 / stack.getMaxDamage();
            if (percent >= minimumPercent && percent > bestDurability) {
                bestDurability = percent;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int findBestChestplate() {
        int bestSlot = -1;
        int bestValue = 0;
        for (int slot = 0; slot < 36; slot++) {
            int value = chestplateValue(mc.player.getInventory().getItem(slot));
            if (value > bestValue) {
                bestValue = value;
                bestSlot = slot;
            }
        }
        return bestSlot;
    }

    private int chestplateValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;
        int durability = stack.getMaxDamage() - stack.getDamageValue();
        if (stack.is(Items.NETHERITE_CHESTPLATE)) return (prioritizeNetherite.getValue() ? 10_000 : 4_000) + durability;
        if (stack.is(Items.DIAMOND_CHESTPLATE)) return 3_000 + durability;
        if (stack.is(Items.IRON_CHESTPLATE)) return 2_000 + durability;
        if (stack.is(Items.CHAINMAIL_CHESTPLATE)) return 1_500 + durability;
        if (stack.is(Items.GOLDEN_CHESTPLATE)) return 1_000 + durability;
        if (stack.is(Items.LEATHER_CHESTPLATE)) return 500 + durability;
        return 0;
    }

    private boolean swapInventoryWithChest(int inventorySlot) {
        if (mc.gameMode == null || mc.player.containerMenu != mc.player.inventoryMenu) return false;
        int containerSlot = inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
        InventoryUtil.click(containerSlot, 0, ClickType.PICKUP);
        InventoryUtil.click(CHEST_MENU_SLOT, 0, ClickType.PICKUP);
        InventoryUtil.click(containerSlot, 0, ClickType.PICKUP);
        return true;
    }

    private void notify(String message) {
        if (notifySwap.getValue()) Command.sendMessage(message);
    }

    private void resetState() {
        cooldown = 0;
        protectionTicks = 0;
        lastHurtTime = 0;
        protectionActive = false;
    }
}
