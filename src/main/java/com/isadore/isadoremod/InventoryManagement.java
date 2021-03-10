package com.isadore.isadoremod;

import com.google.gson.JsonElement;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.inventory.ItemStackHelper;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class InventoryManagement {

    public static boolean isOrganizing = false;
    public static ArrayList<MouseAction> mouseQueue = new ArrayList<MouseAction>();
    public static int ticksSinceExecute = 2;

    public static class StoredSlot {
        public int index;
        public JsonElement stackData;

        public StoredSlot(int index, JsonElement stackData) {
            this.index = index;
            this.stackData = stackData;
        }

        @Nullable
        public ItemStack parseStackData() {
            INBT convertedStack = Dynamic.convert(JsonOps.INSTANCE, NBTDynamicOps.INSTANCE, stackData);
            if(convertedStack instanceof CompoundNBT) {
                return ItemStack.read((CompoundNBT) convertedStack);
            }
            return null;
        }

    }

    public static class MouseAction {
        Integer windowID = null;
        Integer slotID = null;
        Integer mouseButton = null;
        ClickType clickType = null;

        public MouseAction(int windowID, int slotID, int mouseButton, ClickType clickType) {
            this.windowID = windowID; this.slotID = slotID; this.mouseButton = mouseButton; this.clickType = clickType;
        }

        public void execute() {
            mc.playerController.windowClick(this.windowID, this.slotID, this.mouseButton, this.clickType, mc.player);
        }

    }

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Minecraft mc = Minecraft.getInstance();

    public static void saveLayout() {

        if(isScreenOpen()) {
            ArrayList<StoredSlot> slots = getCurrentLayout();
            if(slots != null)
                IsadoreMod.DB.saveInventoryLayout(slots);
        }

    }

//    public static void restoreLayout() {
//
//        if(isScreenOpen() && !InventoryManagement.isOrganizing && mouseQueue.size() < 1) {
//
//            Integer windowID = getWindowID();
//
//            InventoryManagement.isOrganizing = true;
//
//            Database.InventoryDoc savedSlots = IsadoreMod.DB.getInventoryLayout();
//            List<Slot> currentSlots = getMainInventorySlots();
//
//            if(windowID != null && savedSlots != null && currentSlots != null) {
//
//                for (int i = 0; i < currentSlots.size(); i++) {
//
//                    Slot currentSlot = currentSlots.get(i);
//                    StoredSlot storedSlot = savedSlots.getSlot(i);
//                    ItemStack currentStack = currentSlot.getStack();
//                    ItemStack storedStack = storedSlot.parseStackData();
//
//                    String currentItemID = ForgeRegistries.ITEMS.getKey(currentStack.getItem()).toString();
//                    String storedItemID = ForgeRegistries.ITEMS.getKey(storedStack.getItem()).toString();
//
//                    if(currentStack.isEnchanted()) {
//                        itemEnchantsEqual(storedStack, currentStack);
//                    }
//
//                    if(!currentItemID.equals(storedItemID)) {
//
//                        if(currentItemID.equals("minecraft:air")) {
//
//
//
//                            Slot usableStack = findSlotWithItemInInventory(storedStack);
//
//                            Slot chestUsableStack = findSlotWithItemInChest(storedStack);
//
//                            if(chestUsableStack != null)
//                                usableStack = chestUsableStack;
//
//                            if(usableStack != null) {
//
//                                int usableCount = usableStack.getStack().getCount();
//                                int storedCount = storedStack.getCount();
//                                mouseQueue.add(new MouseAction(windowID, usableStack.slotNumber, 0, ClickType.PICKUP));
//
//                                if(storedCount < usableCount) {
//
//                                    for (int j = 0; j < storedCount; j++) {
//                                        mouseQueue.add(new MouseAction(windowID, currentSlot.slotNumber, 1, ClickType.PICKUP));
//                                    }
//                                    Slot nextStoredSlot = null;
//                                    if(savedSlots.size() >= i + 1) {
//                                        nextStoredSlot = currentSlots.get(i + 1);
//                                    }
//
//                                    if(nextSlot == null || !itemsEqual(nextSlot.getStack(), storedStack)) {
//                                        mouseQueue.add(new MouseAction(windowID, usableStack.slotNumber, 0, ClickType.PICKUP));
//                                    }
//
//                                } else {
//                                    mouseQueue.add(new MouseAction(windowID, currentSlot.slotNumber, 0, ClickType.PICKUP));
//                                }
//
//                            }
//
//                        } else {
//
//
//
//
//                        }
//
//
//                    }
//
//                }
//
//
//
//            }
//
//        }
//
//        InventoryManagement.isOrganizing = false;
//
//    }

    public static void transferAllOfStack() {
        if(isScreenOpen() && mc.currentScreen instanceof ContainerScreen) {
            Integer windowID = getWindowID();
            Slot currentSlot = ((ContainerScreen<?>) mc.currentScreen).getSlotUnderMouse();
            if(windowID != null && currentSlot != null) {
                mouseQueue.add(new MouseAction(windowID, currentSlot.slotNumber, 0, ClickType.QUICK_MOVE));
            }
        }
    }

    @Nullable
    public static Integer getWindowID() {
        Integer windowID = null;
        if(isScreenOpen()) {
            if(mc.currentScreen instanceof InventoryScreen) {
                windowID = ((InventoryScreen) mc.currentScreen).getContainer().windowId;
            } else if(mc.currentScreen instanceof ChestScreen) {
                windowID = ((ChestScreen) mc.currentScreen).getContainer().windowId;
            }
        }
        return windowID;
    }

    @Nullable
    public static Slot findSlotWithItemInInventory(ItemStack stack) {
        List<Slot> inventorySlots = getMainInventorySlots();
        Slot finalSlot = null;

        if(inventorySlots != null) {
            for(Slot s : inventorySlots) {
                if (itemsEqual(s.getStack(), stack)) {
                    if(finalSlot == null) finalSlot = s;
                    if(finalSlot != null && s.getStack().getCount() > finalSlot.getStack().getCount()) finalSlot = s;
                }
            }
        }

        return finalSlot;
    }

    public static Slot findSlotWithItemInChest(ItemStack stack) {
        List<Slot> inventorySlots = getChestSlots();
        Slot finalSlot = null;

        if(inventorySlots != null) {
            for(Slot s : inventorySlots) {
                if (itemsEqual(s.getStack(), stack)) {
                    if(finalSlot == null) finalSlot = s;
                    if(finalSlot != null && s.getStack().getCount() < finalSlot.getStack().getCount()) finalSlot = s;
                }
            }
        }

        return finalSlot;
    }

    @Nullable
    public static List<Slot> sortedChestSlots(ItemStack stack) {
        List<Slot> chestSlots = getChestSlots();
        List<Slot> sortedList = null;

        if(chestSlots != null)
            sortedList = new ArrayList<Slot>(chestSlots).stream().sorted(Comparator.comparingInt((Slot a) -> a.getStack().getCount())).collect(Collectors.toList());

        return sortedList;
    }

    public static boolean itemsEqual(ItemStack x, ItemStack y) {
        return (itemIDsEqual(x, y) && itemEnchantsEqual(x, y) && itemNamesEqual(x, y));
    }

    public static boolean itemNamesEqual(ItemStack x, ItemStack y) {

        if(x.isDamageable() && y.isDamageable()) {
            Pattern durabilityPattern = Pattern.compile(".+(?=\\[\\d+\\]$)");
            Matcher durabilityMatcherX = durabilityPattern.matcher(x.getDisplayName().getString());
            Matcher durabilityMatcherY = durabilityPattern.matcher(y.getDisplayName().getString());

            String matchX = null;
            String matchY = null;
            if(durabilityMatcherX.find()) matchX = durabilityMatcherX.group(0);
            if(durabilityMatcherY.find()) matchY = durabilityMatcherY.group(0);
            if(matchX != null && matchY != null && matchX == matchY) {
                return true;
            }
        }
        return x.getDisplayName().getString().equals(y.getDisplayName().getString());
    }

    public static boolean itemIDsEqual(ItemStack x, ItemStack y) {
        String ItemIDy = ForgeRegistries.ITEMS.getKey(x.getItem()).toString();
        String ItemIDx = ForgeRegistries.ITEMS.getKey(y.getItem()).toString();
        return ItemIDy.equals(ItemIDx);
    }

    public static boolean itemEnchantsEqual(ItemStack x, ItemStack y) {

        Map<Enchantment, Integer> enchantsY = EnchantmentHelper.getEnchantments(y);
        Map<Enchantment, Integer> enchantsX = EnchantmentHelper.getEnchantments(x);
        Iterator<Enchantment> iteratorX = enchantsX.keySet().iterator();

        if(enchantsX.size() != enchantsY.size()) return false;

        while (iteratorX.hasNext()) {
            Enchantment key = iteratorX.next();
            Integer valueX = enchantsX.get(key);
            if(enchantsY.get(key) != null) {
                Integer valueY = enchantsY.get(key);
                if(valueX != valueY) return false;
            } else {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static ArrayList<StoredSlot> getCurrentLayout() {

        List<Slot> inventorySlots = getMainInventorySlots();

        if(inventorySlots != null) {

            ArrayList<StoredSlot> slots = new ArrayList<StoredSlot>();

            for (int i = 0; i < inventorySlots.size(); i++) {
                ItemStack stack = inventorySlots.get(i).getStack();
                CompoundNBT serializedStack = stack.write(new CompoundNBT());
                JsonElement stackJSON = Dynamic.convert(NBTDynamicOps.INSTANCE, JsonOps.INSTANCE, serializedStack);
                slots.add(new StoredSlot(i, stackJSON));
            }
            return  slots;
        }

        return null;
    }

    @Nullable
    public static List<Slot> getMainInventorySlots() {

        List<Slot> inventorySlots = null;

        if(mc.currentScreen instanceof ChestScreen) {

            ChestContainer chest = ((ChestScreen) mc.currentScreen).getContainer();

            int chestSize = chest.getLowerChestInventory().getSizeInventory();
            int totalSize = chest.inventorySlots.size();

            inventorySlots = chest.inventorySlots.subList(chestSize, totalSize);

        } else if (mc.currentScreen instanceof InventoryScreen) {
            PlayerContainer inventory = ((InventoryScreen) mc.currentScreen).getContainer();
            inventorySlots = inventory.inventorySlots.subList(9, 45);
        }
        if(inventorySlots != null) {
            return new ArrayList<Slot>(inventorySlots);
        } else {
            return null;
        }


    }

    public static List<Slot> getChestSlots() {
        if(isScreenOpen() && mc.currentScreen instanceof ChestScreen) {
            ChestContainer chest = ((ChestScreen) mc.currentScreen).getContainer();
            int chestSize = chest.getLowerChestInventory().getSizeInventory();
            return new ArrayList<Slot>(chest.inventorySlots.subList(0, chestSize - 1));
        }
        return null;
    }

    public static boolean isScreenOpen() {
        return (mc.currentScreen != null && mc.currentScreen instanceof ContainerScreen);
    }


}
