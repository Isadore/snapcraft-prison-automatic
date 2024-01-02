package com.isadore.isadoremod.components;

import com.google.gson.JsonElement;
import com.isadore.isadoremod.main.EventHandler;
import com.isadore.isadoremod.main.GuiOverlay;
import com.isadore.isadoremod.main.UserData;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.HopperScreen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.inventory.container.*;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.registries.ForgeRegistries;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

public class InventoryManagement {

    private static final Logger LOGGER = LogManager.getLogger();
    private static final Minecraft mc = Minecraft.getInstance();

    public static boolean recipeBookOpen = false;

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
            if(convertedStack instanceof CompoundNBT)
                return ItemStack.read((CompoundNBT) convertedStack);
            return null;
        }

    }

    public static class MouseAction implements Runnable {
        Integer slotID = null;
        Integer mouseButton = null;
        ClickType clickType = null;

        public MouseAction(int slotID, int mouseButton, ClickType clickType) {
            this.slotID = slotID; this.mouseButton = mouseButton; this.clickType = clickType;
        }

        public void run() {
            Integer windowID = getWindowID();
            if(mc.playerController != null && mc.player != null && windowID != null) {
                mc.playerController.windowClick(windowID, this.slotID, this.mouseButton, this.clickType, mc.player);
//                LOGGER.info(String.format("Slot: %s, Button: %s, Type: %s", this.slotID, this.mouseButton, this.clickType.toString()));
            }
        }

    }

    public static void updateRecipeBookState(InventoryScreen screen) {
        Boolean isBookVisible = screen.getRecipeGui().isVisible();
        if(isBookVisible != null && recipeBookOpen != isBookVisible) {
            recipeBookOpen = isBookVisible;
            int bookDistance = 77;
            for (Widget b : screen.buttons) {
                if(b instanceof GuiOverlay.IdentifiableButton) {
                    if(recipeBookOpen)
                        b.x += bookDistance;
                    else
                        b.x -= bookDistance;
                }
            }
        }
    }

    public static void checkSlotIndex(int i, UserData.Layout savedSlots, List<Slot> currentSlots) {

        Integer windowID = getWindowID();

        if(windowID != null && savedSlots != null && currentSlots != null && mc.playerController != null && mc.player != null) {

                Slot currentSlot = currentSlots.get(i);
                StoredSlot storedSlot = savedSlots.slots.get(i);
                ItemStack currentStack = currentSlot.getStack();
                ItemStack storedStack = storedSlot.parseStackData();

                if(!itemsEqual(currentStack, storedStack) && currentStack.isEmpty() && storedStack != null) {

                    Slot usableChestSlot = findSlotWithItemInChest(storedStack);

                    if(usableChestSlot != null) {

                        ItemStack usableChestStack = usableChestSlot.getStack();
                        int usableStackCount = usableChestStack.getCount();

                        if(usableChestStack.isStackable() && usableChestStack.getCount() > 1) {

                            EventHandler.tickQueue.add(new MouseAction(usableChestSlot.slotNumber, 0, ClickType.PICKUP));

                            ArrayList<Slot> draggableSlots = getDraggableSlots(usableChestStack, currentSlots, savedSlots);

                            if(draggableSlots.size() > 1) {

                                EventHandler.tickQueue.add(new MouseAction(-999, 4, ClickType.QUICK_CRAFT));
                                EventHandler.tickQueue.add(() -> {
                                    for(Slot d : draggableSlots)
                                        new MouseAction(d.slotNumber, 5, ClickType.QUICK_CRAFT).run();
                                });
                                EventHandler.tickQueue.add(new MouseAction(-999, 6, ClickType.QUICK_CRAFT));

                                if(usableStackCount > draggableSlots.size()) {
                                    EventHandler.tickQueue.add(new MouseAction(usableChestSlot.slotNumber, 0, ClickType.PICKUP));
                                }

                            } else if(draggableSlots.size() == 1 && itemsEqual(storedStack, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, SnapCraftUtils.getPrismarineType())) {
                                EventHandler.tickQueue.add(new MouseAction(currentSlot.slotNumber, 1, ClickType.PICKUP));
                                if(usableChestStack.getCount() > 1)
                                    EventHandler.tickQueue.add(new MouseAction(usableChestSlot.slotNumber, 0, ClickType.PICKUP));
                            } else if(draggableSlots.size() == 1) {
                                EventHandler.tickQueue.add(new MouseAction(currentSlot.slotNumber, 0, ClickType.PICKUP));
                            }

                        } else if(storedSlot.index > 26) {
                            EventHandler.tickQueue.add(new MouseAction(usableChestSlot.slotNumber, storedSlot.index - 27, ClickType.SWAP));
                        } else {
                            EventHandler.tickQueue.add(new MouseAction(usableChestSlot.slotNumber, 0, ClickType.PICKUP));
                            EventHandler.tickQueue.add(new MouseAction(currentSlot.slotNumber, 0, ClickType.PICKUP));
                        }

                    }

                }

        }
        if(currentSlots!= null && i < currentSlots.size() - 1)  {
            EventHandler.tickQueue.add(() -> checkSlotIndex(i + 1, savedSlots, currentSlots));
        } else {
            EventHandler.tickQueue.add(() -> {if(mc.currentScreen != null) mc.currentScreen.closeScreen();});
        }

    }

    public static ArrayList<Slot> getDraggableSlots(ItemStack stack, List<Slot> currentSlots, UserData.Layout savedSlots) {
        ArrayList<Slot> finalSlots = new ArrayList<>();
        for (int i = 0; i < savedSlots.slots.size(); i++) {
            ItemStack savedStack = savedSlots.slots.get(i).parseStackData();
            if(savedStack == null) continue;
            ItemStack currentStack = currentSlots.get(i).getStack();
            if(itemsEqual(savedStack, stack) && savedStack.getCount() == 1 && currentStack.isEmpty())
                finalSlots.add(currentSlots.get(i));
            if(finalSlots.size() == stack.getCount()) break;
        }
        return finalSlots;
    }

    public static void shiftDoubleClickSlot() {
        if(isScreenOpen()) {
            Integer windowID = getWindowID();
            ContainerScreen screen = (ContainerScreen) mc.currentScreen;
            Slot slot = screen.getSlotUnderMouse();
            if(windowID != null && slot != null) {
                ItemStack currentStack = slot.getStack().copy();
                List<Slot> allSlots = getALlSlots();
                if(allSlots != null && mc.player != null) {
                    int spacesToMove = 0;
                    for(Slot s : allSlots) {
                        if (s != null && s.canTakeStack(mc.player) && s.getHasStack() && s.isSameInventory(slot) && Container.canAddItemToSlot(s, currentStack, true))
                            spacesToMove++;
                    }
                    if(spacesToMove == 1) {
                        EventHandler.tickQueue.add(new MouseAction(slot.slotNumber, 0, ClickType.QUICK_MOVE));
                    } else if(spacesToMove > 1) {
                        if(mc.player.inventory.getItemStack().getItem() == Items.AIR)
                            EventHandler.tickQueue.add(new MouseAction(slot.slotNumber, 0, ClickType.PICKUP));
                        EventHandler.tickQueue.add(() -> {
                                for(Slot s : allSlots) {
                                    if (s != null && s.canTakeStack(mc.player) && s.getHasStack() && s.isSameInventory(slot) && Container.canAddItemToSlot(s, currentStack, true)) {
                                        new MouseAction(s.slotNumber, 0, ClickType.QUICK_MOVE).run();
    //                                    tickQueue.add(new MouseAction(s.slotNumber, 0, ClickType.QUICK_MOVE));
                                    }
                                }
                        });
                        EventHandler.tickQueue.add(new MouseAction(slot.slotNumber, 0, ClickType.PICKUP));
                        EventHandler.tickQueue.add(new MouseAction(slot.slotNumber, 0, ClickType.QUICK_MOVE));
                    }
                }
            }
        }
    }

    @Nullable
    public static Integer getWindowID() {
        if(isScreenOpen()) {
            return ((ContainerScreen) mc.currentScreen).getContainer().windowId;
        }
        return null;
    }

    @Nullable
    public static Slot findSlotWithItemInInventory(ItemStack stack) {
        List<Slot> inventorySlots = getMainInventorySlots();
        Slot finalSlot = null;
        if(inventorySlots != null) {
            for(Slot s : inventorySlots) {
                if (itemsEqual(s.getStack(), stack)) {
                    if(finalSlot == null) finalSlot = s;
                    if(s.getStack().getCount() > finalSlot.getStack().getCount()) finalSlot = s;
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
                    int finalStackCount = finalSlot.getStack().getCount();
                    int currentStackCount = s.getStack().getCount();
                    if(currentStackCount < finalStackCount || currentStackCount == finalStackCount) finalSlot = s;
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
            sortedList = new ArrayList<>(chestSlots).stream().sorted(Comparator.comparingInt((Slot a) -> a.getStack().getCount())).collect(Collectors.toList());
        return sortedList;
    }

    @Nullable
    public static ItemStack getItemStackInInventory(Item item) {
        if(mc.player != null) {
            for (ItemStack i : mc.player.inventory.mainInventory) {
                if(i.getItem() == item) return i;
            }
        }
        return null;
    }

    public static int getEmptySlotCount() {
        int count = 0;
        if(mc.player != null) {
            for (ItemStack i : mc.player.inventory.mainInventory) {
                if(i.isEmpty()) count++;
            }
        }
        return count;
    }

    public static int getFilledSlotCount() {
        return 36 - getEmptySlotCount();
    }

    public static double inventoryPercentFull() {
        if(mc.player != null) {
            int maxItemCount = mc.player.inventory.mainInventory.stream().mapToInt(ItemStack::getMaxStackSize).sum();
            int currentItemCount = mc.player.inventory.mainInventory.stream().mapToInt(ItemStack::getCount).sum();
            return (double) currentItemCount / maxItemCount;
        }
        return 0;
    }

    public static int getDurability(ItemStack stack) {
        return stack.getMaxDamage() - stack.getDamage();
    }

    public static boolean itemsEqual(ItemStack x, ItemStack y) {
        return (x.getItem() == y.getItem() && (itemEnchantsEqual(x, y) || itemNamesEqual(x, y)));
    }

    public static boolean itemNamesEqual(ItemStack x, ItemStack y) {
        return x.getDisplayName().getString().equals(y.getDisplayName().getString());
    }

    public static boolean itemIDsEqual(ItemStack x, String y) {
        return y.equals(getItemID(x));
    }

    public static boolean itemsEqual(ItemStack x, Item... items) {
        for (Item i : items)
            if(x.getItem() == i) return true;
        return false;
    }

    @Nullable
    public static String getItemID(ItemStack stack) {
        return getItemID(stack.getItem());
    }

    @Nullable
    public static String getItemID(Item item) {
        ResourceLocation registryItem = ForgeRegistries.ITEMS.getKey(item);
        if(registryItem != null)
            return registryItem.toString();
        else return null;
    }

    public static boolean itemEnchantsEqual(ItemStack x, ItemStack y) {
        HashMap<String, Integer> enchantsY = getItemEnchants(y);
        HashMap<String, Integer> enchantsX = getItemEnchants(x);
        if(enchantsX == null || enchantsY == null || enchantsX.size() != enchantsY.size()) return false;
        for (Map.Entry<String, Integer> i : enchantsY.entrySet()) {
            String yEnchant = i.getKey();
            Integer xValue = enchantsX.get(yEnchant);
            if(xValue == null || !xValue.equals(i.getValue())) return false;
        }
        return true;
    }

    //Snapcraft Custom Enchants
    @Nullable
    public static HashMap<String, Integer> getItemEnchants(ItemStack i) {
        CompoundNBT tags = i.getTag();
        if(tags != null && tags.contains("Enchantments")) {
            CompoundNBT display = tags.getCompound("display");
            Set<String> displayKeys = display.keySet();
            for (String s : displayKeys) {
                if(s.startsWith("ViaVersion") && s.endsWith("Lore")) {
                    ListNBT enchantNBTList = display.getList(s, Constants.NBT.TAG_STRING);
                    HashMap<String, Integer> enchantMap = new HashMap<>();
                    for (INBT e : enchantNBTList) {
                        String enchantStr = e.getString().substring(2);
                        if(enchantStr.endsWith(" ")) continue;
                        String[] enchStrArr = enchantStr.split("\\s");
                        if(enchStrArr.length < 2) continue;
                        String enchantName = enchStrArr[0];
                        try {
                            Integer enchantLevel = Integer.parseInt(enchStrArr[1]);
                            enchantMap.put(enchantName, enchantLevel);
                        } catch (Exception ignored) {}
                    }
                    return enchantMap;
                }
            }
        }
        return null;
    }

    @Nullable
    public static ArrayList<StoredSlot> getCurrentSlots() {
        List<Slot> inventorySlots = getMainInventorySlots();
        if(inventorySlots != null) {
            ArrayList<StoredSlot> slots = new ArrayList<>();
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
            inventorySlots = inventory.inventorySlots.subList(9, 46);
        }
        if(inventorySlots != null) {
            return new ArrayList<>(inventorySlots);
        }
        return null;
    }

    @Nullable
    public static List<ItemStack> MainInventoryItemsMappedToSlots() {
        if(mc.player != null) {
            List<ItemStack> inventory = mc.player.inventory.mainInventory;
            List<ItemStack> currentStacksArranged = new ArrayList<>();
            currentStacksArranged.addAll(inventory.subList(9, 36));
            currentStacksArranged.addAll(inventory.subList(0, 9));
            return currentStacksArranged;
        }
        return null;
    }

    public static List<Slot> getChestSlots() {
        if(isScreenOpen() && mc.currentScreen instanceof ChestScreen) {
            ChestContainer chest = ((ChestScreen) mc.currentScreen).getContainer();
            int chestSize = chest.getLowerChestInventory().getSizeInventory();
            return new ArrayList<>(chest.inventorySlots.subList(0, chestSize));
        }
        return null;
    }

    @Nullable
    public static List<Slot> getALlSlots() {
        if(mc.currentScreen instanceof ContainerScreen)
            return ((ContainerScreen) mc.currentScreen).getContainer().inventorySlots;
        return null;
    }

    public static boolean isScreenOpen() {
        return (mc.currentScreen instanceof ContainerScreen);
    }


}
