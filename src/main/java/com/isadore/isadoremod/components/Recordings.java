package com.isadore.isadoremod.components;

import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.main.EventHandler;
import com.isadore.isadoremod.main.GuiOverlay;
import com.isadore.isadoremod.main.UserData;
import com.isadore.isadoremod.main.WebSocketHandler;
import javafx.util.Pair;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.AbstractClientPlayerEntity;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.inventory.container.ClickType;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockRayTraceResult;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.sql.Timestamp;
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
                    ItemStack pick = InventoryManagement.getItemStackInInventory(Items.DIAMOND_PICKAXE);
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
    public static double stopPlayingTimestamp = 0;
    public static int gpickRepDurability = SnapCraftUtils.exponentialRandomInt(2, 375, 575);
    public static boolean storeCurrentSlice = false;
    public static boolean stopAfterCurrentRec = false;
    public static ArrayList<Long> playedRecordings = new ArrayList<>();

    public static int ticksMiningNothing = 0;
    public static int ticksNearPlayer = 0;
    public static int ticksMiningNothingThreshold = SnapCraftUtils.exponentialRandomInt(1, 25, 30);

    public static void playTicks(TickEvent event) {
        if(mc.player == null) return;
        try {
            if(playRecordings && mc.player.getHeldItemMainhand().getItem() == Items.PAPER) {
                KeyBinds.toggleRecordingPlay();
                EventHandler.tickQueue.clear();
            } else if((playingRecording == null || playedTicks >= playingRecording.ticks.size() - 1) && playRecordings && event.phase == TickEvent.Phase.END) {
                resetRecording();
                playedTicks = 0;
            } else if(!EventHandler.playerInHub && WebSocketHandler.connected && playingRecording != null && continuePlayingTimeStamp <= System.currentTimeMillis() && playRecordings && !recordActions && playedTicks < playingRecording.ticks.size() && mc.currentScreen == null && EventHandler.tickQueue.size() == 0 && mc.world != null) {
                UserData.RecordingType currentType = getCurrentPickType();

                if(playedTicks == 0 && stopAfterCurrentRec) {
                    KeyBinds.toggleRecordingPlay();
                    return;
                }

                Pair<AbstractClientPlayerEntity, Double> staff = closestStaffMember();
                if(!stopAfterCurrentRec && staff != null) {
                    IsadoreMod.LOGGER.debug("staff");
                    stopAfterCurrentRec = true;
                    WebSocketHandler.sendMessage(WebSocketHandler.MessageType.AutoMinerStatus, String.format("%s %.1f blocks away, stopping", staff.getKey().getDisplayName().getString(), staff.getValue()));
                }

                if(EventHandler.lastPingTimestamp <= (System.currentTimeMillis() - 30000) && EventHandler.lastPingTimestamp > WebSocketHandler.lastMessageReceived) {
                    KeyBinds.toggleRecordingPlay();
                    EventHandler.lastPingTimestamp = 0;
                    return;
                }

                int rand1 = new Random().nextInt(40 * 60 * 60);
                int rand2 = new Random().nextInt(40 * 60 * 30);
                if(rand1 == 0 || rand2 == 0) {
                    double sleepTime;
                    if(rand1 == 0)
                        sleepTime = SnapCraftUtils.exponentialRandomTime(1, 5d, 27d);
                    else
                        sleepTime = SnapCraftUtils.exponentialRandomTime(1, 1d, 5d);
                    String time = ZonedDateTime.ofInstant(new Timestamp((long) sleepTime).toInstant(), ZoneId.of("America/New_York")).format(DateTimeFormatter.ofPattern("hh:mm:ss a"));
                    WebSocketHandler.sendMessage(WebSocketHandler.MessageType.AutoMinerStatus, "Sleeping until " + time);
                    continuePlayingTimeStamp = sleepTime;
                    warpToMine();
                    resetPlayerActions();
                    sellBlocks();
                    return;
                }

                if(!EventHandler.flyIsEnabled) {
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(400, 700));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/fly"));
                    continuePlayingTimeStamp = SnapCraftUtils.squaredRandomTime(1000, 2000);
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
                        warpToMine();
                        resetRecording();
                        resetPlayerActions();
                        sellBlocks();
                        return;
                    }
                    Double playerDistance = closestPlayerDistance();
                    if(playerDistance != null && playerDistance <= 4) {
                        ticksNearPlayer++;
                    } else {
                        ticksNearPlayer = 0;
                    }
                    if(ticksNearPlayer >= 100) {
                        resetPlayerActions();
                        continuePlayingTimeStamp = SnapCraftUtils.squaredRandomTime(600, 2300);
                        return;
                    }
                }

                ItemStack pick = InventoryManagement.getItemStackInInventory(Items.DIAMOND_PICKAXE);
                if(pick == null) return;
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
                                    if(bestStack == null || s.getStack().getCount() < bestStack.getStack().getCount() || s.getStack().getCount() == bestStack.getStack().getCount())
                                        bestStack = s;
                                }
                            }
                            if(bestStack == null) {
                                KeyBinds.toggleRecordingPlay();
                                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.AutoMinerStatus, "No more tier Vs.");
                                return;
                            }
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(bestStack.slotNumber, 0, ClickType.PICKUP));
                            double tokensNeeded = Math.ceil((double) pick.getStack().getDamage() / 500);
                            boolean stackTooSmall = bestStack.getStack().getCount() < tokensNeeded;
                            if(stackTooSmall)
                                tokensNeeded = bestStack.getStack().getCount();
                            for (double i = 0; i < tokensNeeded; i++) {
                                EventHandler.tickQueue.add(new EventHandler.QueueDelay(25, 57));
                                EventHandler.tickQueue.add(new InventoryManagement.MouseAction(81, 0, ClickType.PICKUP));
                            }
                            if(!stackTooSmall) {
                                EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                                EventHandler.tickQueue.add(new InventoryManagement.MouseAction(bestStack.slotNumber, 0, ClickType.PICKUP));
                            }
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(250, 350));
                            EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(1323, 2200));
                        }
                    });
                    gpickRepDurability = SnapCraftUtils.exponentialRandomInt(2, 375, 575);
                    return;
                } else if (currentType == UserData.RecordingType.SLICE && pickDura < 100) {
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(560, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 4"));
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
                                KeyBinds.toggleRecordingPlay();
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
                    sellBlocks();
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(560, 2300));
                    EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 1"));
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(200, 350));
                    if(currentType == UserData.RecordingType.GPICK) {
                        EventHandler.tickQueue.add(() -> {
                            if(mc.currentScreen instanceof ChestScreen) {
                                Map<Item, Integer> storedBlocks = new HashMap<>();
                                storedBlocks.put(Items.DIAMOND_BLOCK, 0);
                                storedBlocks.put(Items.EMERALD_BLOCK, 0);
                                storedBlocks.put(SnapCraftUtils.getPrismarineType(), 0);
                                for (Slot s : InventoryManagement.getChestSlots()) {
                                    for (Map.Entry<Item, Integer> e : storedBlocks.entrySet()) {
                                        if(s.getStack().getItem() == e.getKey())
                                            e.setValue(e.getValue() + s.getStack().getCount());
                                    }
                                }
                                Map<Item, Integer> requiredBlocks = new HashMap<>();
                                requiredBlocks.put(Items.DIAMOND_BLOCK, 0);
                                requiredBlocks.put(Items.EMERALD_BLOCK, 0);
                                requiredBlocks.put(SnapCraftUtils.getPrismarineType(), 0);
                                for (InventoryManagement.StoredSlot s : LayoutHandler.allLayouts.get(0).slots) {
                                    for (Map.Entry<Item, Integer> e : requiredBlocks.entrySet()) {
                                        if(s.parseStackData().getItem() == e.getKey())
                                            e.setValue(e.getValue() + 1);
                                    }
                                }
                                boolean hadExtraBlocks = false;
                                for (Map.Entry<Item, Integer> e : requiredBlocks.entrySet()) {
                                    int storedCount = storedBlocks.get(e.getKey());
                                    if(storedCount < 2 * e.getValue())
                                        storeCurrentSlice = true;
                                    if(storedCount > e.getValue() * 64) {
                                        int extraStacks = (storedCount - (e.getValue() * 64)) / 64;
                                        int movedStacks = 0;
                                        for (Slot s : InventoryManagement.getChestSlots()) {
                                            if(s.getStack().getItem() == e.getKey() && movedStacks < extraStacks && s.getStack().getCount() == 64) {
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
                                    sellBlocks();
                                } else {
                                    EventHandler.tickQueue.add(new InventoryManagement.MouseAction(45, 0, ClickType.SWAP));
                                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                                    if(IsadoreMod.playerIsIsadore()) {
                                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(54, 0, ClickType.PICKUP));
                                        EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                                        EventHandler.tickQueue.add(new InventoryManagement.MouseAction(46, 0, ClickType.PICKUP));
                                    }
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
                        if(IsadoreMod.playerIsIsadore()) {
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(46, 0, ClickType.PICKUP));
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                            EventHandler.tickQueue.add(new InventoryManagement.MouseAction(54, 0, ClickType.PICKUP));
                            EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                        }
                        EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
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
                    EventHandler.tickQueue.add(new EventHandler.QueueDelay(100, 350));
                    EventHandler.tickQueue.add(() -> mc.player.inventory.currentItem = 0);
                }

                if(playedTicks == 0 && !playerIsAtSpawn()) {
                    warpToMine();
                    return;
                }

                UserData.TickRecording tick = playingRecording.ticks.get(playedTicks);
                SnapCraftUtils.MineCoordinates coords = SnapCraftUtils.getMineInfo();
                if(tick.phase == event.phase && coords != null) {
                    mc.player.rotationPitch = (float) tick.cameraPitch;
                    mc.player.rotationYaw = (float) tick.cameraYaw;
                    if(tick.attackKeybindPressed && tick.phase == TickEvent.Phase.END) {
                        mc.sendClickBlockToController(true);
                        if(mc.objectMouseOver == null || mc.objectMouseOver.getType() != RayTraceResult.Type.BLOCK) {
                            ticksMiningNothing++;
                        } else {
                            BlockPos pos = ((BlockRayTraceResult) mc.objectMouseOver).getPos();
                            if(pos.getX() < coords.layersStart.getX() || pos.getX() > coords.layersStart.getX() + coords.width - 1 || pos.getY() < coords.layersStart.getY() - coords.depth + 1 || pos.getY() > coords.layersStart.getY() || pos.getZ() < coords.layersStart.getZ() || pos.getZ() > coords.layersStart.getZ() + coords.width - 1) {
                                ticksMiningNothing++;
                            } else {
                                ticksMiningNothing = 0;
                            }
                        }
                    }
//                    mc.gameSettings.keyBindAttack.setPressed(tick.attackKeybindPressed);
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
                    if(ticksMiningNothing > ticksMiningNothingThreshold || (UserData.profile.sliceTimerEnd > System.currentTimeMillis() && playingRecording.type == UserData.RecordingType.SLICE) || (playedTicks >= playingRecording.ticks.size() - 1) || (mc.player.inventory.getFirstEmptyStack() == -1 && (GuiOverlay.diamondPercentFull == 1 || GuiOverlay.emeraldPercentFull == 1 || GuiOverlay.prismarinePercentFull == 1))) {
                        if(ticksMiningNothing > ticksMiningNothingThreshold)
                            ticksMiningNothingThreshold = SnapCraftUtils.exponentialRandomInt(1, 25, 30);
                        resetRecording();
                        resetPlayerActions();
                        warpToMine();
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
            ticksMiningNothing = 0;
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
            if(mc.playerController != null) mc.playerController.blockHitDelay = 0;
            if(disableFly) mc.player.abilities.isFlying = false;
            if(mc.player.isOnLadder()) mc.player.setSneaking(true);
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
            ItemStack pick = InventoryManagement.getItemStackInInventory(Items.DIAMOND_PICKAXE);
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

    public static void sellBlocks() {
        if(mc.player == null) return;
        boolean hasBlocksToSell = false;
        List<ItemStack> stacks = InventoryManagement.MainInventoryItemsMappedToSlots();
        List<Integer> badItemSlots = new ArrayList<>();
        for (int i = 0; i < stacks.size(); i++) {
            ItemStack stack = stacks.get(i);
            boolean mineBlock = InventoryManagement.itemsEqual(stack, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.COBBLESTONE, Items.QUARTZ_BLOCK, Items.REDSTONE_BLOCK, SnapCraftUtils.getPrismarineType());
            boolean inventoryItem = InventoryManagement.itemsEqual(stack, IsadoreMod.playerIsIsadore() ? Items.PAPER : null, Items.DIAMOND_PICKAXE, Items.DIAMOND_AXE, Items.DIAMOND_SWORD, Items.AIR);
            if(mineBlock) hasBlocksToSell = true;
            if(!inventoryItem && !mineBlock) badItemSlots.add(i);
        }
        if(hasBlocksToSell) {
            for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
                IsadoreMod.LOGGER.debug(ste);
            }
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(600, 2300));
            if(storeCurrentSlice) {
                EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 1"));
                EventHandler.tickQueue.add(new EventHandler.QueueDelay(300, 500));
                Map<Item, List<Integer>> blockSlots = new HashMap<>();
                blockSlots.put(Items.DIAMOND_BLOCK, new ArrayList<>());
                blockSlots.put(Items.EMERALD_BLOCK, new ArrayList<>());
                blockSlots.put(SnapCraftUtils.getPrismarineType(), new ArrayList<>());
                for (int i = 0; i < stacks.size(); i++)  {
                    ItemStack stack = stacks.get(i);
                    for(Map.Entry<Item, List<Integer>> e : blockSlots.entrySet()) {
                        if(stack.getItem() == e.getKey())
                            e.getValue().add(i);
                    }
                }
                for(Map.Entry<Item, List<Integer>> e : blockSlots.entrySet()) {
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
        }
        if(badItemSlots.size() > 0) {
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(700, 1500));
            EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/pv 3"));
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(700, 1500));
            for (int i = 0; i < badItemSlots.size(); i++) {
                EventHandler.tickQueue.add(new EventHandler.QueueDelay(150, 350));
                // Player inventory starts at 54 in double chest
                EventHandler.tickQueue.add(new InventoryManagement.MouseAction(54 + badItemSlots.get(i), 0, ClickType.QUICK_MOVE));
            }
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(150, 350));
            EventHandler.tickQueue.add(() -> { if(mc.currentScreen != null)  mc.currentScreen.closeScreen(); });
            EventHandler.tickQueue.add(new EventHandler.QueueDelay(600, 1500));
            EventHandler.tickQueue.add(Recordings::resetPlayerActions);
        }
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

    @Nullable
    public static Double closestPlayerDistance() {
        if(mc.world == null || mc.player == null) return null;
        Double closest = null;
        for (AbstractClientPlayerEntity e : mc.world.getPlayers()) {
            if (!e.getUniqueID().toString().equals(IsadoreMod.playerID())) {
                double distance = Math.abs(Math.sqrt(Math.pow(e.getPosX() - mc.player.getPosX(), 2) + Math.pow(e.getPosY() - mc.player.getPosY(), 2) + Math.pow(e.getPosZ() - mc.player.getPosZ(), 2)));
                if (closest == null || distance < closest)
                    closest = distance;
            }
        }
        return closest;
    }

    @Nullable
    public static Pair<AbstractClientPlayerEntity, Double> closestStaffMember() {
        if(mc.world == null || mc.player == null) return null;
        String[] staffWhiteList = {
                "1d6173c1-0786-416c-aa2f-d3cdaf40268f" // LazyLauraa
        };
        playerLoop:
        for (AbstractClientPlayerEntity e : mc.world.getPlayers()) {
            if (!e.getUniqueID().toString().equals(IsadoreMod.playerID())) {
                String name = e.getDisplayName().getString();
                String[] staffTags = {"Builder", "Helper", "Admin", "Owner", "Mod", "Manager"};
                for (String t : staffTags) {
                    if (name.contains(String.format("[%s]", t))) {
                        for (String i : staffWhiteList) {
                            if (e.getUniqueID().toString().equals(i)) continue playerLoop;
                        }
                        return new Pair<>(e, Math.abs(Math.sqrt(Math.pow(e.getPosX() - mc.player.getPosX(), 2) + Math.pow(e.getPosY() - mc.player.getPosY(), 2) + Math.pow(e.getPosZ() - mc.player.getPosZ(), 2))));
                    }
                }
            }
        }
        return null;
    }

    public static void warpToMine(boolean ignorePos) {
        if(ignorePos || !playerIsAtSpawn()) {
            String mine = SnapCraftUtils.getPlayerMine();
            if(mine != null) {
                EventHandler.tickQueue.add(new EventHandler.QueueDelay(600, 2300));
                EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/warp " + mine.replace("Prestige", "p")));
            }
        }
    }

    public static void warpToMine() {
        warpToMine(false);
    }

    public static boolean mineBlocksValid() {
        SnapCraftUtils.MineCoordinates coords = SnapCraftUtils.getMineInfo();
        if(coords == null || mc.world == null) return false;
        int startX = (int) coords.layersStart.getX(); // max 3057
        int startY = (int) coords.layersStart.getY(); // min 6
        int startZ = (int) coords.layersStart.getZ(); // max 1524
        for (int d = 0; d < coords.depth + 100; d++) {
            for (int h = 0; h < coords.width + 200; h++) {
                for (int w = 0; w < coords.width + 200; w++) {
                    int x = startX + h;
                    int y = startY - d;
                    int z = startZ + w;
                    BlockState blockState = mc.world.getBlockState(new BlockPos(x, y, z));
                    Block block = blockState.getBlock();
                    if(h >= 100 && h <= coords.width + 100 && w >= 100 && w <= coords.width + 100) {
                        // Block is in mine
                        if(d <= coords.depth && block != Blocks.AIR && block != Blocks.EMERALD_BLOCK && block != Blocks.DIAMOND_BLOCK && block != Blocks.REDSTONE_BLOCK && block != Blocks.QUARTZ_BLOCK && block != Blocks.STONE && block != Blocks.PRISMARINE && block != Blocks.PRISMARINE_BRICKS && block != Blocks.DARK_PRISMARINE)
                            return false;
                    }
                }
            }
        }
        return true;
    }
    
}
