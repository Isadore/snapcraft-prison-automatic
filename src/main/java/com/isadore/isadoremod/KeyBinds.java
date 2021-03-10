package com.isadore.isadoremod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.client.util.InputMappings;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

public class KeyBinds {
    public static KeyBinding organizeInventory =  new KeyBinding("Organize inventory", GLFW.GLFW_KEY_Z, "isadoremod");
    public static KeyBinding saveInventoryLayout = new KeyBinding("Save Inventory Layout", GLFW.GLFW_KEY_Z, "isadoremod");
    public static KeyBinding transferAllOfStack = new KeyBinding("Move all stacks", GLFW.GLFW_KEY_Z, "isadoremod");
    public static KeyBinding openPlayerVault = new KeyBinding("Open player vault", GLFW.GLFW_KEY_Z, "isadoremod");
    public static KeyBinding resetSliceTimer = new KeyBinding("Reset slice timer", GLFW.GLFW_KEY_Z, "isadoremod");

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Minecraft mc = Minecraft.getInstance();

    public static void register() {
        ClientRegistry.registerKeyBinding(openPlayerVault);
        ClientRegistry.registerKeyBinding(resetSliceTimer);
//        ClientRegistry.registerKeyBinding(organizeInventory);
//        ClientRegistry.registerKeyBinding(saveInventoryLayout);
//        ClientRegistry.registerKeyBinding(transferAllOfStack);
    }

    public static void openPV() {
        if(InventoryManagement.isScreenOpen()) return;
        int hotBarIndex = mc.player.inventory.currentItem;
        int pvNum = hotBarIndex + 1;
        Integer pvCount = SnapCraftUtils.getRankPVCount();
        if(pvCount == null) pvCount = 7;
        if(pvNum > pvCount) pvNum = SnapCraftUtils.lastPVAccessed;
        mc.player.sendChatMessage("/pv " + pvNum);
        SnapCraftUtils.lastPVAccessed = pvNum;
    }

    public static void resetTimer() {
        EventHandler.sliceTimerEnd = System.currentTimeMillis() + EventHandler.sliceTimeMS;
    }

    public static  void endTimer() {
        EventHandler.sliceTimerEnd = 0;
    }

    public static boolean isKeyDown(KeyBinding keyBind)
    {
        if (keyBind.isInvalid())
            return false;

        if(mc.currentScreen != null)
            return false;

        boolean isDown = false;
        switch (keyBind.getKey().getType())
        {
            case KEYSYM:
                isDown = InputMappings.isKeyDown(mc.getMainWindow().getHandle(), keyBind.getKey().getKeyCode());
                break;
            case MOUSE:
                isDown = GLFW.glfwGetMouseButton(mc.getMainWindow().getHandle(), keyBind.getKey().getKeyCode()) == GLFW.GLFW_PRESS;
                break;
        }
        return isDown && keyBind.getKeyConflictContext().isActive() && keyBind.getKeyModifier().isActive(keyBind.getKeyConflictContext());
    }

}
