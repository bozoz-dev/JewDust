package dev.axziom.features.modules.render;

import dev.axziom.JewDust;
import dev.axziom.features.modules.Module;
import dev.axziom.features.settings.Setting;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.awt.Color;

public class TablistModule extends Module {

    public Setting<Boolean> friends = bool("Friends", true);
    public Setting<Boolean> enemies = bool("Enemies", true);
    public final Setting<Color> friendColor = color("Friend Color", 145, 79, 220, 255);
    public final Setting<Color> enemyColor  = color("Enemy Color", 145, 79, 220, 255);

    public TablistModule() {
        super("Tablist", "Hides players in the tablist who aren't you, a friend, or an enemy", Category.RENDER);
    }

    public boolean shouldShow(PlayerInfo info) {
        String name = info.getProfile().name();
        if (name == null) return false;
        if (mc.player != null && name.equalsIgnoreCase(mc.player.getGameProfile().name())) return true;
        if (friends.getValue() && JewDust.friendManager.isFriend(name)) return true;
        if (enemies.getValue() && JewDust.enemyManager.isEnemy(name)) return true;
        return false;
    }
}
