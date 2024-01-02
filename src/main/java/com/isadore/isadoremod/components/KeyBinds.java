package com.isadore.isadoremod.components;

import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.main.GuiOverlay;
import com.isadore.isadoremod.main.UserData;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.StringTextComponent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    public static KeyBinding organizeInventory =  new KeyBinding("Organize inventory", -1, IsadoreMod.MODID);
    public static KeyBinding transferAllOfStack = new KeyBinding("Move all stacks", -1, IsadoreMod.MODID);
    public static KeyBinding openPlayerVault = new KeyBinding("Open player vault", -1, IsadoreMod.MODID);
    public static KeyBinding resetSliceTimer = new KeyBinding("Reset slice timer", -1, IsadoreMod.MODID);
    public static KeyBinding warpToMine = new KeyBinding("Warp to mine", -1, IsadoreMod.MODID);
    public static KeyBinding sellAll = new KeyBinding("Sell all", -1, IsadoreMod.MODID);
    public static KeyBinding recordingPlayToggle = new KeyBinding("Play/Pause recording", -1, IsadoreMod.MODID);
    public static KeyBinding[] allKeyBinds = { recordingPlayToggle, sellAll, organizeInventory, transferAllOfStack, openPlayerVault, resetSliceTimer, warpToMine };

    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        for(KeyBinding k : allKeyBinds) ClientRegistry.registerKeyBinding(k);
    }

    public static void openPV() {
        if(InventoryManagement.isScreenOpen()) return;
        SnapCraftUtils.openPV(SnapCraftUtils.lastPVAccessed);
    }

    public static void resetTimer() {
        UserData.profile.sliceTimerEnd = System.currentTimeMillis() + GuiOverlay.sliceTimeMS;
    }

    public static void warpToMine() {
        String mine = SnapCraftUtils.getPlayerMine();
        if(mine != null) SnapCraftUtils.handleCommand("/warp " + mine.replaceAll("Prestige", "p"), true);
    }

    public static void sellAll(boolean human) {
        SnapCraftUtils.handleCommand("/sell", human);
    }

    public static void endTimer() {
        UserData.profile.sliceTimerEnd = 0;
    }

    public static void toggleRecordingPlay() {
        Recordings.playRecordings = !Recordings.playRecordings;
        Recordings.stopAfterCurrentRec = false;
        if(mc.player != null) {
            String msg = (Recordings.playRecordings ? "Playing" : "Paused") + " recording.";
            SnapCraftUtils.sendClientMessage(msg);
        }
        Recordings.resetPlayerActions();
    }

}
