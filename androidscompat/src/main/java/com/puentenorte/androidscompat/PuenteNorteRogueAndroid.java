package com.puentenorte.androidscompat;

import com.thunderbear06.entity.android.RogueDroidEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** Faction-only hostile rogue Android; deliberately has no player recipe or spawn item. */
public final class PuenteNorteRogueAndroid extends RogueDroidEntity {
    public PuenteNorteRogueAndroid(EntityType<? extends RogueDroidEntity> type, Level level) {
        super(type, level);
        getPersistentData().putBoolean(PuenteNorteRaider.FACTION_TAG, true);
        getPersistentData().putBoolean("androidscompat_pn_enemy_android", true);
    }
}
