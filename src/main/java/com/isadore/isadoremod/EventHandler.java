package com.isadore.isadoremod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.ChestContainer;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.management.PlayerProfileCache;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.*;
import net.minecraftforge.common.ForgeConfig;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class EventHandler {

    private static final Logger LOGGER = LogManager.getLogger();
    private final Minecraft mc = Minecraft.getInstance();

    private static final ResourceLocation empty_bar_texture = new ResourceLocation(IsadoreMod.MODID, "textures/empty_bar.png");
    private static final ResourceLocation emerald_bar_texture = new ResourceLocation(IsadoreMod.MODID, "textures/emerald_bar.png");
    private static final ResourceLocation diamond_bar_texture = new ResourceLocation(IsadoreMod.MODID, "textures/diamond_bar.png");
    private static final ResourceLocation timer_bar_texture = new ResourceLocation(IsadoreMod.MODID, "textures/timer_bar.png");

    private MessageCounter eTokenMessageCounter = new MessageCounter();
    public static final int sliceTimeMS = 7 * 60 * 1000;
    public static long sliceTimerEnd = 0; //System.currentTimeMillis() + sliceTimeMS;

    @SubscribeEvent
    public void onMessage(ClientChatReceivedEvent event) {
        String messageContent = event.getMessage().getString();

        Pattern eTokenPattern = Pattern.compile("^\\+5 tokens for mining 250 blocks! \\(Use tokens to /enchant\\)$");
        Matcher eTokenMatcher = eTokenPattern.matcher(messageContent);

        if(eTokenMatcher.find()) {
            if(this.eTokenMessageCounter.secondsElapsed() > 5) this.eTokenMessageCounter.reset();
            this.eTokenMessageCounter.increment();
            if(this.eTokenMessageCounter.secondsElapsed() < 5 && this.eTokenMessageCounter.messageCount >= 6)
                sliceTimerEnd = System.currentTimeMillis() + sliceTimeMS;
        }

        Pattern cfResultPattern = Pattern.compile("(?<=^You have )(won|lost)(?= your bet against \\S+ with the value \\$\\$[,.\\d]+$)");
        Pattern cfPlayerPattern = Pattern.compile("(?<=^You have (won|lost) your bet against )\\S+(?= with the value \\$\\$[,.\\d]+$)");
        Pattern cfValuePattern = Pattern.compile("(?<=with the value \\$\\$)[,.\\d]+$");
        Pattern[] cfPatterns = { cfResultPattern, cfPlayerPattern, cfValuePattern };

        List<String> cfMatches = new ArrayList<String>();
        for(Pattern p : cfPatterns) {
            Matcher cfMatcher = p.matcher(messageContent);
            if (cfMatcher.find())
                cfMatches.add(cfMatcher.group(0));
        }

        if(cfMatches.size() == 3) {
            boolean win = cfMatches.get(0).equalsIgnoreCase("won");
            float amount = Float.parseFloat(cfMatches.get(2).replaceAll(",", ""));
            IsadoreMod.DB.addCoinFlip(win, cfMatches.get(1), amount);
        }

    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {

        if(InventoryManagement.mouseQueue.size() >= 1 && InventoryManagement.ticksSinceExecute >= 3) {
            InventoryManagement.MouseAction nextAction = InventoryManagement.mouseQueue.get(0);
            nextAction.execute();
            InventoryManagement.mouseQueue.remove(0);
            InventoryManagement.ticksSinceExecute = 0;
        } else {
            InventoryManagement.ticksSinceExecute++;
        }
    }

    @SubscribeEvent
    public void onPress(InputEvent event) {

        if(KeyBinds.isKeyDown(KeyBinds.openPlayerVault))
            KeyBinds.openPV();

        if(KeyBinds.isKeyDown(KeyBinds.resetSliceTimer))
            KeyBinds.resetTimer();

    }

    @SubscribeEvent
    public void onScroll(GuiScreenEvent.MouseScrollEvent event) {

        if(event.isCanceled())
            return;

        if(InventoryManagement.isScreenOpen() && mc.currentScreen instanceof ChestScreen) {

            String chestTitle = mc.currentScreen.getTitle().getString();
            Pattern vaultPattern = Pattern.compile("((?!^Vault #)\\d$)");
            Matcher vaultMatcher = vaultPattern.matcher(chestTitle);

            Integer vaultNum = null;

            Integer pvCount = SnapCraftUtils.getRankPVCount();
            if(pvCount == null) pvCount = 7;

            if(vaultMatcher.find()) {
                try {
                    vaultNum = Integer.parseInt(vaultMatcher.group(0));
                } catch (Exception e) {}
            }

            if(vaultNum != null) {
                Integer mouseDirection = (int) event.getScrollDelta();
                Integer newVaultNum = vaultNum + mouseDirection;
                if(newVaultNum > 0 && newVaultNum <= pvCount) {
                    mc.player.closeScreen();
                    event.setCanceled(true);
                    mc.player.sendChatMessage("/pv " + newVaultNum);
                    SnapCraftUtils.lastPVAccessed = newVaultNum;
                }
            }
        }
    }

    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelTokenActivate(event);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelTokenActivate(event);
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelTokenActivate(event);
    }

    public void cancelTokenActivate(PlayerInteractEvent event) {

        String itemName = event.getItemStack().getDisplayName().getString();

        event.setCanceled(true);

        Pattern crypticPattern = Pattern.compile("^Cryptic Token Prestige I{1,3}$");
        Matcher crypticMatcher = crypticPattern.matcher(itemName);

        if(crypticMatcher.find()) {
            event.setCanceled(true);
        }

    }

    @SubscribeEvent
    public void onClick(InputEvent.RawMouseEvent event) {

        if(event.getButton() == 0) {

            if(mc.currentScreen != null && mc.currentScreen instanceof ContainerScreen) {

                ItemStack stack = mc.player.inventory.getItemStack();

                if(stack != null) {

                    LOGGER.info(stack.getDisplayName().getString());

                    CompoundNBT tags = stack.getTag();

                    if(tags != null) {
                        JsonElement nbtJSON = Dynamic.convert(NBTDynamicOps.INSTANCE, JsonOps.INSTANCE, tags);
                        LOGGER.info(nbtJSON.toString());

                        SnapCraftItemTags cfData = null;

                        try {
                            cfData = new Gson().fromJson(nbtJSON, SnapCraftItemTags.class);
                        } catch (Exception e) {};

                        if(cfData != null) {



                        }




                    }

                }

            }

        }

    }

    @SubscribeEvent
    public void onMessageSent(ClientChatEvent event) {
        String message = event.getMessage();

        if(message.startsWith("/cf") || message.startsWith("/coinflip")) {
            event.setMessage("/");
        }

        Pattern cfMainPattern = Pattern.compile("^\\/cf\\s+([\\d.]+)\\s+(heads|tails)\\s*$", Pattern.CASE_INSENSITIVE);
        Matcher cfMatcher = cfMainPattern.matcher(message);

        if(cfMatcher.find()) {

            Integer cfValue = null;
            try {
                cfValue = Integer.parseInt(cfMatcher.group(0));
            } catch (Exception e) {}

            String cfSide = cfMatcher.group(1);

            if(cfValue != null) {




            }

        }

    }

//    private final

    @SubscribeEvent
    public void renderOverlay(RenderGameOverlayEvent.Post event) {

        if(event.getType() == RenderGameOverlayEvent.ElementType.ALL) {

            int screenWidth = mc.getMainWindow().getScaledWidth();
            int screenHeight = mc.getMainWindow().getScaledHeight();
            MatrixStack matrix = event.getMatrixStack();

            Iterable<ItemStack> inventory = mc.player.inventory.mainInventory;
            ArrayList<ItemStack> diamondStacks = new ArrayList<ItemStack>();
            ArrayList<ItemStack> emeraldStacks = new ArrayList<ItemStack>();

            for(ItemStack i : inventory) {
                if(i.getDisplayName().getString().equals("Block of Diamond"))
                    diamondStacks.add(i);
                if(i.getDisplayName().getString().equals("Block of Emerald"))
                    emeraldStacks.add(i);
            }

            float totalDiamondBlocks = diamondStacks.stream().mapToInt(ItemStack::getCount).sum();
            float totalEmeraldBlocks = emeraldStacks.stream().mapToInt(ItemStack::getCount).sum();

            float diamondPercentFull = totalDiamondBlocks / (diamondStacks.size() * 64);
            float emeraldPercentFull = totalEmeraldBlocks / (emeraldStacks.size() * 64);

            mc.getTextureManager().bindTexture(empty_bar_texture);
            mc.ingameGUI.blit(matrix, screenWidth - 75, 0, 0, 0, 75, 10);

            mc.getTextureManager().bindTexture(diamond_bar_texture);
            mc.ingameGUI.blit(matrix,screenWidth - 49, 1, 0, 0, (int) (48 * diamondPercentFull), 10);
            mc.fontRenderer.drawString(matrix, Integer.toString((int) (diamondPercentFull * 100)) + "%", screenWidth - 71, 1, 0);

            mc.getTextureManager().bindTexture(empty_bar_texture);
            mc.ingameGUI.blit(matrix, screenWidth - 75, 11, 0, 0, 75, 10);

            mc.getTextureManager().bindTexture(emerald_bar_texture);
            mc.ingameGUI.blit(matrix,screenWidth - 49, 12, 0, 0, (int) (48 * emeraldPercentFull), 10);
            mc.fontRenderer.drawString(matrix, Integer.toString((int) (emeraldPercentFull * 100)) + "%", screenWidth - 71, 12, 0);

            long sliceTimeRemaining = this.sliceTimerEnd - System.currentTimeMillis();
            int sliceMinutesRemaining = (int) (sliceTimeRemaining / 60000);
            int sliceSecondsRemaining = (int) (sliceTimeRemaining - (sliceMinutesRemaining * 60000)) / 1000;

            mc.getTextureManager().bindTexture(empty_bar_texture);
            mc.ingameGUI.blit(matrix, screenWidth - 75, 23, 0, 0, 75, 10);

            if(sliceTimeRemaining >= 0) {
                mc.getTextureManager().bindTexture(timer_bar_texture);
                mc.ingameGUI.blit(matrix,screenWidth - 49, 24, 0, 0, (int) (48 * sliceTimeRemaining/sliceTimeMS), 10);
                mc.fontRenderer.drawString(matrix, String.format("%d:%02d", sliceMinutesRemaining, sliceSecondsRemaining), screenWidth - 71, 24, 0);
                matrix.push();
                matrix.scale(3f, 3f, 0);
                if(sliceTimeRemaining/1000 <= 5)
                    mc.fontRenderer.drawString(matrix, String.format("Slice Cooldown Ending In %d Seconds", sliceTimeRemaining/1000), screenWidth/25, screenHeight/10, 0x9e1a26);
                matrix.pop();
            } else {
                mc.fontRenderer.drawString(matrix, "0:00", screenWidth - 71, 24, 0);
            }

        }

    }

}

class MessageCounter {
    public long startTimeStamp = System.currentTimeMillis();
    public int messageCount = 0;

    public long secondsElapsed() {
        return (System.currentTimeMillis() - startTimeStamp) / 1000;
    }

    public void reset() {
        this.startTimeStamp = System.currentTimeMillis();
        this.messageCount = 0;
    }

    public void increment() {
        this.messageCount++;
    }

}

class SnapCraftItemTags {
    ItemDisplay display;
}

class ItemDisplay {
    String[] Lore;
}