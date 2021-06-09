package com.isadore.isadoremod.main;

import com.isadore.isadoremod.*;
import com.isadore.isadoremod.components.*;
import com.mojang.blaze3d.matrix.MatrixStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MobEntity;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.text.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;

public class EventHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft mc = Minecraft.getInstance();

    public static LinkedList<Runnable> tickQueue = new LinkedList<>();
    public static long executedTick = 0;
    public static double cancelInputUntil = 0;

    public static String lastAHSellPrice = null;
    public static ItemStack lastAHSellItem = null;
    public static String lastVillagerSalePrice = null;
    public static String lastVillagerSaleCount = null;

    public static boolean playerInHub = true;

    public static class QueueDelay implements Runnable {
        public int min;
        public int max;
        public int power;

        public QueueDelay(int power, int min, int max) {
            this.power = power;
            this.min = min;
            this.max = max;
        }

        public QueueDelay(int min, int max) {
            this.power = 2;
            this.min = min;
            this.max = max;
        }

        public void run() {
            EventHandler.cancelInputUntil = SnapCraftUtils.exponentialRandomTime(this.power, this.min, this.max);
        }

    }

    @SubscribeEvent
    public void onMessage(ClientChatReceivedEvent event) {
        String messageContent = event.getMessage().getString().trim();

        if(messageContent.startsWith("Received:"))
            lastVillagerSalePrice = messageContent;
        if(messageContent.startsWith("Amount Sold:"))
            lastVillagerSaleCount = messageContent;

        if(messageContent.startsWith("Connected to Hub #"))
            playerInHub = true;

        if(!playerInHub && messageContent.length() > 0) {
            if(messageContent.startsWith("From")) {
                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.DirectMessage, messageContent);
            } else if(SnapCraftUtils.messageHasNicknameMatch(messageContent) || messageContent.startsWith("Your friend")) {
                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.Mention, messageContent);
            } else if(messageContent.startsWith("Multiplier:")) {
                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.ChatMessage, lastVillagerSaleCount + ", " + lastVillagerSalePrice);
            } else if(!SnapCraftUtils.messageIsBlackListed(messageContent)) {
                WebSocketHandler.sendMessage(WebSocketHandler.MessageType.ChatMessage, messageContent);
            }
        }

        if(IsadoreMod.disabled) return;

        if(messageContent.equals("Connected to Prison")) {
            playerInHub = false;
            Integer rankPVCount = SnapCraftUtils.getRankPVCount();
            if(rankPVCount != null && rankPVCount >= 5) {
                cancelInputUntil = SnapCraftUtils.squaredRandomTime(900, 2500);
                EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand("/fly"));
            }
        }

        if(messageContent.equals("Scheduled server restart in 1"))
            UserData.profile.sliceTimerEnd = 0;

        if(messageContent.equals(SnapCraftUtils.getPlayerMine() + " is resetting.")) {
            if(Recordings.recordingQueue.size() > 0 && !Recordings.recordingQueue.get(Recordings.recordingQueue.size() - 1).saving)
                Recordings.recordingQueue.get(Recordings.recordingQueue.size() - 1).initialMineState = SnapCraftUtils.getEmptyMineBlocks();
            Recordings.playedRecordings.clear();
        }

        LOGGER.info("\"" + messageContent + "\"");

        if(messageContent.equals("You have listed an item on the auction house!") && lastAHSellItem != null && lastAHSellPrice != null && mc.player != null) {
            StringTextComponent text = new StringTextComponent(String.format("\u00A7eYou have listed %sx \"\u00A76%s\u00A7e\" for \u00A76$%s\u00A7e on the auction house!", lastAHSellItem.getCount(), lastAHSellItem.getDisplayName().getString(), lastAHSellPrice));
            event.setMessage(text);
        }

        if(messageContent.equals("Your warp with ID 6 has expired.") && Recordings.playRecordings) {
            tickQueue.add(Recordings::resetPlayerActions);
            tickQueue.add(new QueueDelay(800, 2300));
            tickQueue.add(() -> SnapCraftUtils.handleCommand("/home pw"));
            tickQueue.add(new QueueDelay(800, 2300));
            for (int i = 1; i <= 6; i++) {
                tickQueue.add(new QueueDelay(200, 600));
                tickQueue.add(() -> SnapCraftUtils.handleCommand("/pw set"));
            }
            if(UserData.profile.pwName != null && !UserData.profile.pwName.isEmpty()) {
                tickQueue.add(new QueueDelay(500, 1300));
                for (int i = 1; i <= 6; i++) {
                    tickQueue.add(new QueueDelay(500, 1300));
                    String cmd = "/pw name " + i + " " + UserData.profile.pwName;
                    tickQueue.add(() -> SnapCraftUtils.handleCommand(cmd));
                }
            }
            if(UserData.profile.pwItem != null && !UserData.profile.pwItem.isEmpty()) {
                tickQueue.add(new QueueDelay(500, 1300));
                if(UserData.profile.pwName != null) {
                    for (int i = 1; i <= 6; i++) {
                        tickQueue.add(new QueueDelay(500, 1300));
                        String cmd = "/pw item " + i + " " + UserData.profile.pwItem;
                        tickQueue.add(() -> SnapCraftUtils.handleCommand(cmd));
                    }
                }
            }
            tickQueue.add(new QueueDelay(700, 2300));
            tickQueue.add(() -> SnapCraftUtils.handleCommand("/warp " + Recordings.playerMineNotNull()));
            tickQueue.add(new QueueDelay(1000, 2300));
            tickQueue.add(Recordings::resetPlayerActions);
        }

    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        GuiOverlay.computeStackPercents();
        Recordings.recordTicks(event);
        Recordings.playTicks(event);
        if(event.phase == TickEvent.Phase.START || mc.player == null) return;
        if(!IsadoreMod.disabled) {
            ItemStack heldItem = mc.player.getHeldItemMainhand();
            if(heldItem.isDamageable() && mc.gameSettings.keyBindAttack.isKeyDown()) {
                int damage = heldItem.getMaxDamage() - heldItem.getDamage();
                String itemID = InventoryManagement.getItemID(heldItem);
                if(itemID != null) {
                    if(itemID.contains("diamond") && damage < 100)
                        mc.gameSettings.keyBindAttack.setPressed(false);
                    if(itemID.contains("iron") && damage < 10)
                        mc.gameSettings.keyBindAttack.setPressed(false);
                }
            }
        }
        if(mc.world == null || cancelInputUntil > System.currentTimeMillis()) return;
        long lastTick2 = executedTick;
        long currentTick = mc.world.getGameTime();
        if(lastTick2 > currentTick)
            executedTick = 0;
        if(tickQueue.size() >= 1 && currentTick > lastTick2) {
            Runnable nextAction = tickQueue.get(0);
            nextAction.run();
            executedTick = currentTick;
            try {
                tickQueue.removeFirst();
            } catch (Exception e) {
                LOGGER.info(e.getMessage());
            }
        }
    }

    @SubscribeEvent
    public void onPress(InputEvent event) {
        if(IsadoreMod.disabled) return;

        if(event.isCancelable() && cancelInputUntil > System.currentTimeMillis())
            event.setCanceled(true);

        if(KeyBinds.isKeyDown(KeyBinds.openPlayerVault))
            KeyBinds.openPV();

        if(KeyBinds.isKeyDown(KeyBinds.resetSliceTimer))
            KeyBinds.resetTimer();

        if(KeyBinds.isKeyDown(KeyBinds.warpToMine))
            KeyBinds.warpToMine();

        if(KeyBinds.isKeyDown(KeyBinds.organizeInventory, false))
            LayoutHandler.restoreLayout();

        if(KeyBinds.isKeyDown(KeyBinds.transferAllOfStack, false))
            InventoryManagement.shiftDoubleClickSlot();

        if(KeyBinds.isKeyDown(KeyBinds.holdleftClick))
            KeyBinds.holdLeftClick();

        if(KeyBinds.isKeyDown(KeyBinds.sellAll))
            KeyBinds.sellAll(true);

        if(KeyBinds.isKeyDown(KeyBinds.recordingPlayToggle, false))
            KeyBinds.toggleRecordingPlay();

    }

    @SubscribeEvent
    public void onScroll(GuiScreenEvent.MouseScrollEvent event) {

        if(event.isCanceled() || tickQueue.size() > 0)
            return;

        if(InventoryManagement.isScreenOpen() && mc.currentScreen instanceof ChestScreen) {
            Integer vaultNum = SnapCraftUtils.getCurrentPVNum();
            if(vaultNum != null) {
                Integer pvCount = SnapCraftUtils.getRankPVCount();
                if(pvCount == null) pvCount = 7;
                Integer mouseDirection = (int) event.getScrollDelta();
                int newVaultNum = vaultNum - mouseDirection;
                if(newVaultNum > 0 && newVaultNum <= pvCount) {
                    SnapCraftUtils.handleCommand("/pv " + newVaultNum, () -> {
                        mc.player.closeScreen();
                        event.setCanceled(true);
                    }, null, IsadoreMod.playerIsIsadore());
                }
            }
        }
    }

    @SubscribeEvent
    public void onUIPress(GuiScreenEvent.KeyboardKeyPressedEvent event) {

        if(IsadoreMod.disabled) return;

        if(cancelInputUntil > System.currentTimeMillis())
            event.setCanceled(true);

        if(mc.currentScreen != null && SnapCraftUtils.lastChestWasPV && mc.currentScreen instanceof ChestScreen) {
            ChestScreen screen = (ChestScreen) event.getGui();
            Slot hoveredSlot = screen.getSlotUnderMouse();
            if(hoveredSlot == null) {
                KeyBinding[] hotBarKeys = mc.gameSettings.keyBindsHotbar;
                KeyBinding pressedKey = null;
                int pressedCount = 0;
                for (KeyBinding key : hotBarKeys) {
                    if(KeyBinds.isKeyDown(key, false)) {
                        pressedKey = key;
                        pressedCount++;
                    }
                }
                if(pressedKey != null && pressedCount == 1) {
                    int pvNum = pressedKey.getKey().getKeyCode() - 48;
                    event.setCanceled(true);
                    SnapCraftUtils.openPV(pvNum);
                }
            }
        }

    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
//        if(event.getGui() == null) InventoryManagement.tickQueue.clear();
        Recordings.resetPlayerActions(false);
        SnapCraftUtils.updateLastAccessedPV(event.getGui());
    }

    @SubscribeEvent
    public void onClick(InputEvent.RawMouseEvent event) {

        if(IsadoreMod.disabled) return;

        if ((mc.currentScreen instanceof ChestScreen || mc.currentScreen instanceof InventoryScreen) && tickQueue.size() > 0)
            event.setCanceled(true);

        if(mc.currentScreen == null && mc.player != null) {

            KeyBinding rightClick = mc.gameSettings.keyBindUseItem;
            KeyBinding leftClick = mc.gameSettings.keyBindAttack;

            ItemStack heldItem = mc.player.getHeldItemMainhand();
            String itemName = heldItem.getDisplayName().getString();

            if(event.getButton() == rightClick.getKey().getKeyCode()) {
                if(itemName.startsWith("Cryptic Token Prestige I"))
                    event.setCanceled(true);
                Map<String, Integer> heldItemEnchants = InventoryManagement.getItemEnchants(heldItem);
                if(heldItemEnchants != null) {
                    Integer eff = heldItemEnchants.get("Efficiency");
                    if(eff != null && eff == 50)
                        event.setCanceled(true);
                }
            }

            if(heldItem.isDamageable() && event.getButton() == leftClick.getKey().getKeyCode()) {
                String itemID = InventoryManagement.getItemID(heldItem);
                if(itemID != null) {
                    int damage = heldItem.getMaxDamage() - heldItem.getDamage();
                    if(itemID.contains("diamond") && damage < 100)
                        event.setCanceled(true);
                    if(itemID.contains("iron") && damage < 10)
                        event.setCanceled(true);
                }
            }
        }

    }

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {
        if(IsadoreMod.disabled) return;
        if(event.getType() == RenderGameOverlayEvent.ElementType.ALL) {
            MatrixStack matrix = event.getMatrixStack();
//            if (mc.world != null)
//                mc.fontRenderer.drawString(matrix, Long.toString(mc.world.getGameTime()), 0,0,0);
//            mc.fontRenderer.drawString(matrix, Long.toString(InventoryManagement.executedTick), 0, 10, 0);
            if(!mc.gameSettings.showDebugInfo) {
                if(mc.player != null) {
                    mc.fontRenderer.drawString(matrix, String.valueOf(mc.player.rotationPitch), 0, 0, 0);
                    mc.fontRenderer.drawString(matrix, String.valueOf(mc.player.rotationYaw), 0, 10, 0);
                }
                int recQueueSize = Recordings.recordingQueue.size();
                mc.fontRenderer.drawString(matrix, String.format("Recording: %s, Length: %s", Recordings.recordActions, recQueueSize > 0  && !Recordings.recordingQueue.get(recQueueSize - 1).saving ? Recordings.recordingQueue.get(recQueueSize - 1).ticks.size() : null), 0, 20, 0);
                mc.fontRenderer.drawString(matrix, String.format("Playing: %s, Length: %s, Played: %s", Recordings.playRecordings, Recordings.playingRecording != null ? Recordings.playingRecording.ticks.size() : null, Recordings.playedTicks), 0, 30, 0);
                mc.fontRenderer.drawString(matrix, String.format("Screen: %s", mc.currentScreen), 0, 40, 0);
                mc.fontRenderer.drawString(matrix, Recordings.gettingRecording != null && Recordings.gettingRecording.isAlive() ? "Searching... " + (int) (System.currentTimeMillis() - Recordings.recordingSearchStart) / 1000  + "s" : "Done.", 0 , 50, 0);
                mc.fontRenderer.drawString(matrix, "Cancel input until: " + (int)(cancelInputUntil - System.currentTimeMillis()), 0, 60, 0);
                mc.fontRenderer.drawString(matrix, "Full Slots: " + InventoryManagement.getFilledSlotCount(), 0, 70, 0);
                mc.fontRenderer.drawString(matrix, "Closest Player: " + Recordings.closestPlayerDistance(), 0, 80, 0);
                mc.fontRenderer.drawString(matrix, "Socket Connected: " + WebSocketHandler.connected, 0, 90, 0);
                mc.fontRenderer.drawString(matrix, "Mining ticks missed: " + Recordings.ticksMiningNothing, 0, 100, 0);
                //                ArrayList<SnapCraftUtils.Coordinates> emptyBLocks = SnapCraftUtils.getEmptyMineBlocks();
//                mc.fontRenderer.drawString(matrix, String.format("Mine percent empty: %s, percent similar: %s", emptyBLocks != null ? (int) ((double) emptyBLocks.size() / (72 * 49 * 49) * 100) : null, Recordings.playingRecording != null ? SnapCraftUtils.percentMinesEqual(emptyBLocks, Recordings.playingRecording.ticks.get(0).emptyMineBlocks) : null), 0 , 50, 0);
                GuiOverlay.renderSliceTimer(matrix);
                GuiOverlay.renderOreSeekTimer(matrix);
                GuiOverlay.renderInventoryPercentBars(matrix);
            }
            GuiOverlay.renderItemHighlightOverride(matrix);
        }
    }

    @SubscribeEvent
    public void drawForeground(GuiContainerEvent.DrawForeground event) {
        if(IsadoreMod.disabled) return;
        GuiOverlay.renderLayoutItems(event.getMatrixStack());
    }

    @SubscribeEvent
    public void guiScreen(GuiScreenEvent.DrawScreenEvent event) {
        if(IsadoreMod.disabled) return;
        GuiOverlay.renderLayoutID(event);
        Screen screen = event.getGui();
        if(screen instanceof InventoryScreen)
            InventoryManagement.updateRecipeBookState((InventoryScreen) screen);
    }

    @SubscribeEvent
    public void loadScreen(GuiScreenEvent.InitGuiEvent.Post event) {
        if(IsadoreMod.disabled) return;
        GuiOverlay.renderLayoutButtons(event);
    }

    @SubscribeEvent
    public void worldLoad(WorldEvent.Load event) {
        mc.gameSettings.heldItemTooltips = false;
    }

    @SubscribeEvent
    public void onMessageSent(ClientChatEvent event) {
        String msg = event.getMessage();
        String lowercased = event.getMessage().toLowerCase(Locale.ROOT);
        if(mc.player == null) return;
        if(msg.equals("/isadoremod")) {
            IsadoreMod.disabled = !IsadoreMod.disabled;
            mc.gameSettings.heldItemTooltips = IsadoreMod.disabled;
            event.setCanceled(true);
//            mc.player.sendMessage(new StringTextComponent(String.format("%sIsadoreMod %s", IsadoreMod.disabled ? "\u00A7c" : "\u00A7a", IsadoreMod.disabled ? "disabled" : "enabled")), null);
        }
        if(IsadoreMod.disabled) return;
        if(lowercased.startsWith("/pwname")) {
            String name = "";
            if(msg.length() > 8) name = msg.substring(8).trim();
            UserData.profile.pwName = name;
            mc.player.sendMessage(new StringTextComponent(String.format("Pwarp name stored: %s", name.replace("&", "\u00A7"))), null);
            event.setCanceled(true);
        } else if(lowercased.startsWith("/pwitem")) {
            String item = "";
            if(msg.length() > 8) item = msg.substring(8).trim();
            UserData.profile.pwItem = item;
            mc.player.sendMessage(new StringTextComponent(String.format("Pwarp item stored: %s", item)), null);
            event.setCanceled(true);
        } else if(lowercased.startsWith("/rec")) {
            if(lowercased.contains("play")) {
                KeyBinds.toggleRecordingPlay();
            } else if(lowercased.contains("reset")) {
                Recordings.resetRecording();
            } else if(lowercased.contains("delete")) {
                if(Recordings.playingRecording != null)
                    UserData.deleteRecording(Recordings.playingRecording.timestamp);
            } else  {
                Recordings.recordActions = !Recordings.recordActions;
            }
            event.setCanceled(true);
        } else if(lowercased.startsWith("/ah sell ")) {
            try {
                int price = Integer.parseInt(msg.substring(9));
                NumberFormat format = NumberFormat.getInstance();
                format.setGroupingUsed(true);
                lastAHSellPrice = format.format(price);
                lastAHSellItem = mc.player.getHeldItemMainhand().copy();
            } catch (Exception ignored) {
                lastAHSellPrice = null;
                lastAHSellItem = null;
            }
        }
    }

    private boolean loggedIn = false;

    @SubscribeEvent
    public void onLogin(ClientPlayerNetworkEvent.LoggedInEvent event) {
        WebSocketHandler.init();
        NetworkManager manager = event.getNetworkManager();
        if(manager != null && !loggedIn)  {
            manager.channel().pipeline().addBefore("packet_handler", "listener", new PacketHandler());
            loggedIn = true;
        }
    }

    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        UserData.writeProfile();
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggedOutEvent event) {
        WebSocketHandler.disconnect();
        loggedIn = false;
    }

    @SubscribeEvent
    public void renderEntity(RenderLivingEvent.Post<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if(entity instanceof MobEntity && entity.isInvisible() && entity.isAlive())
            event.getRenderer().getRenderManager().renderDebugBoundingBox(event.getMatrixStack(), event.getBuffers().getBuffer(RenderType.getLines()), entity, event.getPartialRenderTick());
    }

}