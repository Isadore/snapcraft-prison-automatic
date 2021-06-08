package com.isadore.isadoremod.components;

import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.main.EventHandler;
import com.isadore.isadoremod.main.GuiOverlay;
import com.isadore.isadoremod.main.UserData;
import com.isadore.isadoremod.main.WebSocketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class Recordings {

    public static Minecraft mc = Minecraft.getInstance();

    public static boolean recordActions = false;
    public static boolean playRecordings = false;

    public static class Recording {
        public UUID id = UUID.randomUUID();
        public boolean saving = false;
        public ArrayList<UserData.TickRecording> ticks = new ArrayList<>();
        public byte[][][] initialMineState = SnapCraftUtils.getEmptyMineBlocks();
    }

    public static List<Recording> recordingQueue = Collections.synchronizedList(new ArrayList<>());

    public static void recordTicks(TickEvent event) {
        if(mc.player == null) return;
        if(playerIsAtSpawn() && !wadIsPressed()) {
            if(recordingQueue.size() > 0) {
                Recording lastRec = recordingQueue.get(recordingQueue.size() - 1);
                if(!lastRec.saving) {
                    ItemStack pick = InventoryManagement.getItemStackInInventoryForId("minecraft:diamond_pickaxe");
                    if(pick != null) {
                        UserData.RecordingType type = getCurrentPickType();
                        int emptyStack = mc.player.inventory.getFirstEmptyStack();
                        if(lastRec.ticks.size() > 150 && ((type == UserData.RecordingType.GPICK && emptyStack == -1) || type == UserData.RecordingType.SLICE)) {
                            new Thread(() -> {
                                lastRec.saving = true;
                                UserData.saveRecording(type, lastRec.initialMineState, lastRec.ticks);
                                for (int i = 0; i < recordingQueue.size(); i++) {
                                    if(recordingQueue.get(i).id.compareTo(lastRec.id) == 0)
                                        recordingQueue.remove(i);
                                }
                            }).start();
                        } else {
                            lastRec.ticks.clear();
                        }
                    }
                }
            }
        } else if(recordActions && !playRecordings && mc.currentScreen == null) {
            if((recordingQueue.size() > 0 && recordingQueue.get(recordingQueue.size() - 1).saving) || recordingQueue.size() == 0)
                recordingQueue.add(new Recording());
            recordingQueue.get(recordingQueue.size() - 1).ticks.add(new UserData.TickRecording(mc, mc.player, event.phase));
        }
    }

    @Nullable
    public static UserData.Recording playingRecording = null;
    @Nullable
    public static Thread gettingRecording = null;
    public static long recordingSearchStart = 0;
    public static int playedTicks = 0;
    public static double continuePlayingTimeStamp = 0;
    public static int gpickRepDurability = SnapCraftUtils.exponentialRandomInt(2, 375, 575);
    public static boolean storeCurrentSlice = false;
    public static ArrayList<Long> playedRecordings = new ArrayList<>();

    public static void playTicks(TickEvent event) {
        if(mc.player == null) return;
        try {
            if((playingRecording == null || playedTicks >= playingRecording.ticks.size() - 1) && playRecordings && event.phase == TickEvent.Phase.END) {
                resetRecording();
                playedTicks = 0;
            } else if(!EventHandler.playerInHub && WebSocketHandler.connected && playingRecording != null && continuePlayingTimeStamp <= System.currentTimeMillis() && playRecordings && !recordActions && playedTicks < playingRecording.ticks.size() && mc.currentScreen == null && EventHandler.tickQueue.size() == 0) {
                UserData.RecordingType currentType = getCurrentPickType();

                int rand1 = new Random().nextInt(40 * 60 * 60);
                int rand2 = new Random().nextInt(40 * 60 * 30);
                if(rand1 == 0 || rand2 == 0) {
                    double sleepTime;
                    if(rand1 == 0)
                        sleepTime = SnapCraftUtils.exponentialRandomTime(1, 5d, 27d);
                    else
                        sleepTime = SnapCraftUtils.exponentialRandomTime(1, 1d, 5d);
                    String time = ZonedDateTime.ofInstant(new Timestamp((long) sleepTime).toInstant(), ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
                    WebSocketHandler.sendMessage(WebSocketHandler.MessageType.AutoMinerStatus, String.format("Sleeping until %s", time));
                    continuePlayingTimeStamp = sleepTime;
                    resetPlayerActions();
                    return;
                }

                if(playerIsAtSpawn()) {
                    if(playingRecording.type != getNeededType()) {
                       resetRecording();
                    } else if(playedTicks > 10) {
                        resetRecording();
                        resetPlayerActions();
                        sellBlocks();
                        return;
                    }
                }

                if(playedTicks >= 150) {
                    if(playerIsOutsideMine()) {
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(500, 2123));
                        EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/warp " + playerMineNotNull()));
                        resetRecording();
                        resetPlayerActions();
                        sellBlocks();
                        return;
                    }
                    Double playerDistance = closestPlayerDistance();
                    if(playerDistance != null && playerDistance <= 4) {
                        resetPlayerActions();
                        continuePlayingTimeStamp = SnapCraftUtils.squaredRandomTime(10000, 30000);
                        return;
                    }
                }

                ItemStack pick = InventoryManagement.getItemStackInInventoryForId("minecraft:diamond_pickaxe");
                int pickDura = InventoryManagement.getDurability(pick);
                if(currentType == UserData.RecordingType.GPICK && pickDura <= gpickRepDurability) {
                    resetPlayerActions();
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(560, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 2"));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                    EventHandler.tickQueue.add(() -> {
                        if(mc.currentScreen instanceof ChestScreen) {
                            List<Slot> chestSlots = InventoryManagement.getChestSlots();
                            Slot bestStack = null;
                            for (Slot s : chestSlots) {
                                if(s.getStack().getDisplayName().getString().contains("(Tier V)")) {
                                    if((bestStack == null || s.getStack().getCount() < bestStack.getStack().getCount()))
                                        bestStack = s;
                                }
                            }
                            if(bestStack == null) {
                                playRecordings = false;
                                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.AutoMinerStatus, "No more tier Vs.");
                                return;
                            }
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(bestStack.slotNumber, 0, ClickType.PICKUP));
                            double tokensNeeded = Math.ceil((double) pick.getStack().getDamage() / 500);
                            for (double i = 0; i < tokensNeeded; i++) {
                                EventHandler.tickQueue.add(new EventHandler.QueueDelay(25, 57));
                                EventHandler.tickQueue.add(new InventoryManagement.MouseAction(82, 0, ClickType.PICKUP));
                            }
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(bestStack.slotNumber, 0, ClickType.PICKUP));
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                            EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(1323, 2200));
                        }
                    });
                    gpickRepDurability = SnapCraftUtils.exponentialRandomInt(2, 375, 575);
                    return;
                } else if (currentType == UserData.RecordingType.SLICE && pickDura < 100) {
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(560, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 3"));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                    EventHandler.tickQueue.add(() -> {
                        if(mc.currentScreen instanceof ChestScreen) {
                            Slot freshPick = null;
                            for (Slot s : InventoryManagement.getChestSlots()) {
                                Map<String, Integer> enchants = InventoryManagement.getItemEnchants(s.getStack());
                                if(enchants != null && enchants.get("Slicing") != null && s.getStack().getDamage() == 0) {
                                    freshPick = s;
                                    break;
                                }
                            }
                            if(freshPick == null) {
                                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.AutoMinerStatus, "No more usable slicing picks.");
                                playRecordings = false;
                                return;
                            }
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(freshPick.slotNumber, 0, ClickType.SWAP));
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(300, 750));
                            EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(300, 750));
                        }
                    });
                    return;
                }

                if(playingRecording.type != currentType) {
                    sellBlocks(true);
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(560, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 1"));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(200, 350));
                    if(currentType == UserData.RecordingType.GPICK) {
                        EventHandler.tickQueue.add(() -> {
                            if(mc.currentScreen instanceof ChestScreen) {
                                Map<String, Integer> storedBlocks = new HashMap<>();
                                storedBlocks.put("minecraft:diamond_block", 0);
                                storedBlocks.put("minecraft:emerald_block", 0);
                                storedBlocks.put(SnapCraftUtils.getPrismarineType(), 0);
                                for (Slot s : InventoryManagement.getChestSlots()) {
                                    for (Map.Entry<String, Integer> e : storedBlocks.entrySet()) {
                                        if(InventoryManagement.itemIDsEqual(s.getStack(), e.getKey()))
                                            e.setValue(e.getValue() + s.getStack().getCount());
                                    }
                                }
                                Map<String, Integer> requiredBlocks = new HashMap<>();
                                requiredBlocks.put("minecraft:diamond_block", 0);
                                requiredBlocks.put("minecraft:emerald_block", 0);
                                requiredBlocks.put(SnapCraftUtils.getPrismarineType(), 0);
                                for (InventoryManagement.StoredSlot s : LayoutHandler.allLayouts.get(0).slots) {
                                    for (Map.Entry<String, Integer> e : requiredBlocks.entrySet()) {
                                        if(InventoryManagement.itemIDsEqual(s.parseStackData(), e.getKey()))
                                            e.setValue(e.getValue() + 1);
                                    }
                                }
                                boolean hadExtraBlocks = false;
                                for (Map.Entry<String, Integer> e : requiredBlocks.entrySet()) {
                                    int storedCount = storedBlocks.get(e.getKey());
                                    if(storedCount < 2 * e.getValue())
                                        storeCurrentSlice = true;
                                    if(storedCount > e.getValue() * 64) {
                                        int extraStacks = (storedCount - (e.getValue() * 64)) / 64;
                                        int movedStacks = 0;
                                        for (Slot s : InventoryManagement.getChestSlots()) {
                                            if(InventoryManagement.itemIDsEqual(s.getStack(), e.getKey()) && movedStacks < extraStacks && s.getStack().getCount() == 64) {
                                                EventHandler.tickQueue.add(new EventHandler.QueueDelay(150, 300));
                                                EventHandler.tickQueue.add(new InventoryManagement.MouseAction(s.slotNumber, 0, ClickType.QUICK_MOVE));
                                                hadExtraBlocks = true;
                                                movedStacks++;
                                            }
                                        }
                                    }
                                }
                                if(hadExtraBlocks) {
                                    EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                                    sellBlocks(true);
                                } else {
                                    EventHandler.tickQueue.add(new InventoryManagement.MouseAction(45, 0, ClickType.SWAP));
                                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                                    EventHandler.tickQueue.add(new InventoryManagement.MouseAction(46, 1, ClickType.SWAP));
                                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                                    EventHandler.tickQueue.add(new InventoryManagement.MouseAction(54, 0, ClickType.PICKUP));
                                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                                    EventHandler.tickQueue.add(new InventoryManagement.MouseAction(47, 0, ClickType.PICKUP));
                                    LayoutHandler.currentLayoutIndex = 0;
                                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                                    EventHandler.tickQueue.add(() -> LayoutHandler.restoreLayout(true));
                                    EventHandler.tickQueue.add(() -> mc.player.inventory.currentItem = 0);
                                }
                            }
                        });
                    } else {
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(45, 0, ClickType.SWAP));
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(46, 1, ClickType.SWAP));
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(47, 0, ClickType.PICKUP));
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(54, 0, ClickType.PICKUP));
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                        EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                        mc.player.inventory.currentItem = 1;
                    }
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(400, 2300));
                    return;
                }

                int itemCount = InventoryManagement.getFilledSlotCount();
                if(playingRecording.type == UserData.RecordingType.SLICE && currentType == UserData.RecordingType.SLICE && itemCount == 1) {
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(560, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 1"));
                    LayoutHandler.currentLayoutIndex = 0;
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                    EventHandler.tickQueue.add(() -> LayoutHandler.restoreLayout(true));
                    EventHandler.tickQueue.add(() -> mc.player.inventory.currentItem = 0);
                }

                UserData.TickRecording tick = playingRecording.ticks.get(playedTicks);
                if(tick.phase == event.phase) {
                    mc.player.rotationPitch = (float) tick.cameraPitch;
                    mc.player.rotationYaw = (float) tick.cameraYaw;
                    mc.gameSettings.keyBindAttack.setPressed(tick.attackKeybindPressed);
                    mc.gameSettings.keyBindForward.setPressed(tick.forwardsKeybindPressed);
                    mc.gameSettings.keyBindRight.setPressed(tick.rightKeybindPressed);
                    mc.gameSettings.keyBindLeft.setPressed(tick.leftKeybindPressed);
                    mc.gameSettings.keyBindBack.setPressed(tick.backwardsKeybindPressed);
                    mc.gameSettings.keyBindJump.setPressed(tick.jumpKeybindPressed);
                    mc.gameSettings.keyBindSneak.setPressed(tick.sneakKeybindPressed);
                    mc.player.setSprinting(tick.sprinting);
                    mc.player.setSneaking(tick.sneaking);
                    mc.player.abilities.isFlying = tick.flying;
                    playedTicks++;
                    if(mc.player.inventory.getFirstEmptyStack() == -1 && (GuiOverlay.diamondPercentFull == 1 || GuiOverlay.emeraldPercentFull == 1 || GuiOverlay.prismarinePercentFull == 1)) {
                        resetRecording();
                        resetPlayerActions();
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(400, 2123));
                        EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/warp " + playerMineNotNull()));
                        sellBlocks();
                    } else if (playedTicks >= playingRecording.ticks.size() - 1) {
                        resetRecording();
                        resetPlayerActions();
                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(400, 2123));
                        EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/warp " + playerMineNotNull()));
                        sellBlocks();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public static void resetRecording() {
        if(gettingRecording == null || !gettingRecording.isAlive()) {
            playingRecording = null;
            UserData.RecordingType type = UserData.profile.sliceTimerEnd < System.currentTimeMillis() ? UserData.RecordingType.SLICE : UserData.RecordingType.GPICK;
            gettingRecording = new Thread(() -> {
                playingRecording = UserData.getBestRecording(type);
                if(playingRecording != null)
                    playedRecordings.add(playingRecording.timestamp);
            });
            recordingSearchStart = System.currentTimeMillis();
            gettingRecording.start();
        }
    }

    public static void resetPlayerActions(boolean disableFly) {
        if(mc.player != null) {
            mc.gameSettings.keyBindAttack.setPressed(false);
            mc.gameSettings.keyBindForward.setPressed(false);
            mc.gameSettings.keyBindRight.setPressed(false);
            mc.gameSettings.keyBindLeft.setPressed(false);
            mc.gameSettings.keyBindBack.setPressed(false);
            mc.gameSettings.keyBindJump.setPressed(false);
            mc.gameSettings.keyBindSneak.setPressed(false);
            mc.player.setSprinting(false);
            mc.player.setSneaking(false);
            if(disableFly) mc.player.abilities.isFlying = false;
        }
    }

    public static void resetPlayerActions() {
        resetPlayerActions(true);
    }

    public static boolean wadIsPressed() {
        return mc.gameSettings.keyBindForward.isKeyDown() || mc.gameSettings.keyBindLeft.isKeyDown() || mc.gameSettings.keyBindRight.isKeyDown();
    }

    @Nullable
    public static UserData.RecordingType getCurrentPickType() {
        if(mc.player != null) {
            ItemStack pick = InventoryManagement.getItemStackInInventoryForId("minecraft:diamond_pickaxe");
            if(pick != null) {
                Map<String, Integer> enchants = InventoryManagement.getItemEnchants(pick);
                UserData.RecordingType type = UserData.RecordingType.SLICE;
                if(enchants != null && enchants.get("Efficiency") != null && enchants.get("Efficiency") == 50)
                    type = UserData.RecordingType.GPICK;
                return type;
            }
        }
        return null;
    }

    public static UserData.RecordingType getNeededType() {
        if(UserData.profile.sliceTimerEnd - System.currentTimeMillis() > 0)
            return UserData.RecordingType.GPICK;
        return UserData.RecordingType.SLICE;
    }

    public static void sellBlocks(boolean force) {
        int fullSlots1 = InventoryManagement.getFilledSlotCount();
        UserData.RecordingType type = getCurrentPickType();
        if(InventoryManagement.inventoryPercentFull() >= .3 || (force && (type == UserData.RecordingType.GPICK && fullSlots1 > 3) || (type == UserData.RecordingType.SLICE && fullSlots1 > 1))) {
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(600, 2300));
            if(storeCurrentSlice) {
                EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 1"));
                EventHandler.tickQueue.add(new EventHandler.QueueDelay(300, 500));
                Map<String, List<Integer>> blockSlots = new HashMap<>();
                blockSlots.put("minecraft:diamond_block", new ArrayList<>());
                blockSlots.put("minecraft:emerald_block", new ArrayList<>());
                blockSlots.put(SnapCraftUtils.getPrismarineType(), new ArrayList<>());
                List<ItemStack> stacks = InventoryManagement.MainInventoryItemsMappedToSlots();
                for (int i = 0; i < stacks.size(); i++)  {
                    ItemStack stack = stacks.get(i);
                    for(Map.Entry<String, List<Integer>> e : blockSlots.entrySet()) {
                        if(InventoryManagement.itemIDsEqual(stack, e.getKey()))
                            e.getValue().add(i);
                    }
                }
                for(Map.Entry<String, List<Integer>> e : blockSlots.entrySet()) {
                    if(e.getValue().size() == 1) {
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(e.getValue().get(0) + 54, 0, ClickType.QUICK_MOVE));
                    } else if(e.getValue().size() > 1) {
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(e.getValue().get(0) + 54, 0, ClickType.PICKUP));
                        EventHandler.tickQueue.add(() -> {
                            for (int i = 1; i < e.getValue().size(); i++) {
                                new InventoryManagement.MouseAction(e.getValue().get(i) + 54, 0, ClickType.QUICK_MOVE).run();
                            }
                        });
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(e.getValue().get(0) + 54, 0, ClickType.PICKUP));
                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(e.getValue().get(0) + 54, 0, ClickType.QUICK_MOVE));
                    }
                }
                EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                storeCurrentSlice = false;
            } else {
                EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/sell"));
            }
            // Get rid of garbage picked up @ warp "gold"
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(1200, 2300));
            EventHandler.tickQueue.add(() -> {
                int fullSlots2 = InventoryManagement.getFilledSlotCount();
                if((type == UserData.RecordingType.GPICK && fullSlots2 > 3) || (type == UserData.RecordingType.SLICE && fullSlots2 > 1)) {
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(500, 2344));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/home gold"));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(500, 1200));
                    EventHandler.tickQueue.add(() -> mc.gameSettings.keyBindUseItem.setPressed(true));
                    EventHandler.tickQueue.add(() -> mc.gameSettings.keyBindUseItem.setPressed(false));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(200, 400));
                    List<ItemStack> stacks = InventoryManagement.MainInventoryItemsMappedToSlots();
                    for (int i = 0; i < stacks.size(); i++) {
                        if(!InventoryManagement.itemIDsEqual(stacks.get(i), new String[]{"minecraft:diamond_pickaxe", "minecraft:paper", "minecraft:diamond_axe", "minecraft:air" })) {
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(150, 350));
                            // Player inventory starts at 54 in double chest
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(54 + i, 0, ClickType.QUICK_MOVE));
                        }
                    }
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(150, 350));
                    EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(600, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/warp " + playerMineNotNull()));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(600, 1500));
                    EventHandler.tickQueue.add(Recordings::resetPlayerActions);
                }
            });
        }
    }

    public static void sellBlocks() {
        sellBlocks(false);
    }

    public static boolean playerIsAtSpawn() {
        SnapCraftUtils.MineCoordinates coords = SnapCraftUtils.getMineInfo();
        if(coords == null || mc.player == null) return false;
        return mc.player.getPosX() == coords.spawn.getX() && coords.spawn.getY() == mc.player.getPosY() && mc.player.getPosZ() == coords.spawn.getZ();
    }

    public static boolean playerIsOutsideMine() {
        SnapCraftUtils.MineCoordinates coords = SnapCraftUtils.getMineInfo();
        if(coords == null || mc.player == null) return false;
        return mc.player.getPosX() < coords.layersStart.getX() - 1 || mc.player.getPosX() > coords.layersStart.getX() + coords.width || mc.player.getPosZ() < coords.layersStart.getZ() - 1 || mc.player.getPosZ() > coords.layersStart.getZ() + coords.width;
    }

    public static String playerMineNotNull() {
        String mine = SnapCraftUtils.getPlayerMine();
        return mine != null ? mine.replace("Prestige", "p") : "";
    }

    @Nullable
    public static Double closestPlayerDistance() {
        if(mc.world == null || mc.player == null) return null;
        Double closest = null;
        for (AbstractClientPlayerEntity e : mc.world.getPlayers()) {
            if (!e.getUniqueID().toString().equals(IsadoreMod.playerID())) {
                String name = e.getDisplayName().getString();
                String[] staffTags = {"Helper", "Admin", "Owner", "Mod", "Manager"};
                for (String t : staffTags) {
                    if(name.startsWith("[" + t + "]"))
                        return 0.0;
                }
                double distance = Math.abs(Math.sqrt(Math.pow(e.getPosX() - mc.player.getPosX(), 2) + Math.pow(e.getPosY() - mc.player.getPosY(), 2) + Math.pow(e.getPosZ() - mc.player.getPosZ(), 2)));
                if (closest == null || distance < closest)
                    closest = distance;
            }
        }
        return closest;
    }

    public static boolean percentChance(double chance) {
        return Math.random() <= (chance/100);
    }
    
}
