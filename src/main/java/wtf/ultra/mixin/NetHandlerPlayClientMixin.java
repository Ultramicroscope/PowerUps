package wtf.ultra.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S02PacketChat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wtf.ultra.PowerUpsMod;

@Mixin({NetHandlerPlayClient.class})
public class NetHandlerPlayClientMixin {
    @Inject(method = "handleJoinGame", at = @At("TAIL"))
    public void handleJoin(S01PacketJoinGame packetIn, CallbackInfo ci) {
        if (Minecraft.getMinecraft().getCurrentServerData().serverIP.contains("hypixel.")) {
            PowerUpsMod.sendLocraw();
            PowerUpsMod.locin();
        }
    }

    @Inject(method = "handleChat", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/server/S02PacketChat;getType()B", ordinal = 1), cancellable = true)
    public void handleChat(S02PacketChat packetIn, CallbackInfo ci) {
        if (PowerUpsMod.handleMsg(packetIn.getChatComponent().getUnformattedText())) ci.cancel();
    }
}
