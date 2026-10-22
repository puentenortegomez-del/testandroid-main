package com.puentenorte.androidscompat;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatApi;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.audiochannel.AudioPlayer;
import de.maxhenkel.voicechat.api.audiochannel.EntityAudioChannel;
import de.maxhenkel.voicechat.api.opus.OpusEncoder;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/** Optional Simple Voice Chat bridge for server generated Android speech. */
@ForgeVoicechatPlugin
public final class AndroidsCompatVoiceChat implements VoicechatPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("androidscompat");
    private static volatile VoicechatServerApi serverApi;

    @Override
    public String getPluginId() {
        return AndroidsCompatMod.MOD_ID;
    }

    @Override
    public void initialize(VoicechatApi api) {
        if (api instanceof VoicechatServerApi voicechatServerApi) {
            serverApi = voicechatServerApi;
            LOGGER.info("Simple Voice Chat TTS bridge ready");
        }
    }

    static boolean isReady() {
        return serverApi != null;
    }

    static boolean speakFrom(Mob android, short[] pcm, double distance) {
        VoicechatServerApi api = serverApi;
        if (api == null || android == null || !android.isAlive() || pcm.length == 0) {
            return false;
        }

        EntityAudioChannel channel = api.createEntityAudioChannel(
                UUID.randomUUID(), api.fromEntity(android));
        if (channel == null) {
            return false;
        }
        channel.setDistance((float) distance);

        OpusEncoder encoder = api.createEncoder();
        AudioPlayer player = api.createAudioPlayer(channel, encoder, pcm);
        player.setOnStopped(() -> {
            channel.flush();
            encoder.close();
        });
        player.startPlaying();
        return true;
    }
}
