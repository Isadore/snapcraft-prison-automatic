package com.isadore.isadoremod.components;

import com.isadore.isadoremod.main.EventHandler;
import com.isadore.isadoremod.main.UserData;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class LayoutHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    @Nullable
    public static Integer currentLayoutIndex = 0;
    public static List<UserData.Layout> allLayouts = new ArrayList<>();

    public static void refreshLayouts() {
        List<UserData.Layout> layouts = UserData.getInventoryLayouts();
        if(layouts != null) {
            allLayouts = layouts;
            if(allLayouts.size() < 1) currentLayoutIndex = null;
        }
    }

    public static void addLayout(boolean overwrite) {
        if(InventoryManagement.isScreenOpen()) {
            ArrayList<InventoryManagement.StoredSlot> slots = InventoryManagement.getCurrentSlots();
            if(slots != null) {
                String currentLayoutID = null;
                if(currentLayoutIndex != null && overwrite)
                    currentLayoutID = allLayouts.get(currentLayoutIndex).id;
                UserData.Layout layout = UserData.addInventoryLayout(slots, overwrite ? currentLayoutID : null);
                allLayouts.add(layout);
                currentLayoutIndex = allLayouts.size() - 1;
            }
        }
    }

    @Nullable
    public static UserData.Layout getCurrentLayout() {
        if(currentLayoutIndex != null && currentLayoutIndex >= 0 && currentLayoutIndex < allLayouts.size()) {
            return allLayouts.get(currentLayoutIndex);
        }
        return null;
    }

    public static void deleteCurrentLayout() {
        if(currentLayoutIndex != null) {
            allLayouts.remove((int) currentLayoutIndex);
            if(allLayouts.size() == 0) currentLayoutIndex = null;
            else currentLayoutIndex--;
        }
    }

    public static void restoreLayout(boolean ignoreQueue) {
        if(currentLayoutIndex != null  && InventoryManagement.isScreenOpen() && (EventHandler.tickQueue.size() < 1 || ignoreQueue)) {
            UserData.Layout savedSlots = allLayouts.get(currentLayoutIndex);
            List<Slot> currentSlots = InventoryManagement.getMainInventorySlots();
            if(currentSlots != null && savedSlots != null)
                InventoryManagement.checkSlotIndex(0, savedSlots, currentSlots);
        }
    }

    public static void restoreLayout() {
        restoreLayout(false);
    }

    public static boolean validateLayout() {
        if(currentLayoutIndex != null) {
            List<ItemStack> currentStacks = InventoryManagement.MainInventoryItemsMappedToSlots();
            if(currentStacks != null) {
                UserData.Layout savedSlots = allLayouts.get(currentLayoutIndex);
                for (int i = 0; i < savedSlots.slots.size(); i++) {
                    ItemStack parsedStack = savedSlots.slots.get(i).parseStackData();
                    if(parsedStack == null) return false;
                    ItemStack currentStack = currentStacks.get(i);
                    if(!InventoryManagement.itemsEqual(currentStack, parsedStack)) return false;
                }
                return true;
            }
        }
        return false;
    }

}
