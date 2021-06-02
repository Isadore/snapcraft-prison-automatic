package com.isadore.isadoremod.main;

import com.isadore.isadoremod.components.InventoryManagement;
import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.components.LayoutHandler;
import com.isadore.isadoremod.components.SnapCraftUtils;
import com.mojang.blaze3d.matrix.MatrixStack;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.AbstractGui;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.IngameGui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.client.gui.screen.inventory.InventoryScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.gui.widget.button.ImageButton;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ColorHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.vector.Matrix4f;
import net.minecraft.util.text.IFormattableTextComponent;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class GuiOverlay {

    private static final Minecraft mc = Minecraft.getInstance();

    private static final ResourceLocation button_texture = new ResourceLocation(IsadoreMod.MODID, "textures/buttons.png");

    public static int screenWidth() {
        return mc.getMainWindow().getScaledWidth();
    }
    public static int screenHeight() {
        return mc.getMainWindow().getScaledHeight();
    }

    public static final double sliceTimeMS = 7 * 60 * 1000;
    public static final double oreSeekTimeMS = 60 * 1000;

    public static double prismarinePercentFull = 0;
    public static double emeraldPercentFull = 0;
    public static double diamondPercentFull = 0;


    private static void renderBackgroundBar(MatrixStack matrix, int y) {
        int lightGrey = ColorHelper.PackedColor.packColor(255, 153,153,153);
        int darkGrey = ColorHelper.PackedColor.packColor(255, 56, 56, 56);
        AbstractGui.fill(matrix, screenWidth() - 75, y + 10, screenWidth(), y, lightGrey);
        AbstractGui.fill(matrix, screenWidth() - 49, y + 9, screenWidth() - 1, y + 1, darkGrey);
    }

    private static void renderPercentBar(MatrixStack matrix, int y, double percent, int color, int outlineColor) {
        if(Double.isNaN(percent) || percent == 0) return;
        int widthToSubtract = (int) (48 * (1 - percent));
        int maxX = (screenWidth() - widthToSubtract);
        AbstractGui.fill(matrix, screenWidth() - 49, y + 8, maxX - 1, y, outlineColor);
        if(widthToSubtract >= 47) return;
        AbstractGui.fill(matrix, screenWidth() - 48, y + 7, maxX - 2, y + 1, color);
    }

    public static void renderOreSeekTimer(MatrixStack matrix) {
        double oreSeekTimeRemaining = UserData.profile.oreSeekTimerEnd - System.currentTimeMillis();
        int secondsRemaining = (int) oreSeekTimeRemaining / 1000;
        GuiOverlay.renderBackgroundBar(matrix, 11);
        if(oreSeekTimeRemaining > 0) {
            int darkPink = ColorHelper.PackedColor.packColor(255, 130, 47, 100);
            int lightPink = ColorHelper.PackedColor.packColor(255, 235, 89, 181);
            GuiOverlay.renderPercentBar(matrix, 12, oreSeekTimeRemaining / oreSeekTimeMS,  darkPink, lightPink);
            mc.fontRenderer.drawString(matrix, String.format("0:%02d", secondsRemaining), screenWidth() - 71, 12, 0);
        } else {
            mc.fontRenderer.drawString(matrix, "0:00", screenWidth() - 71, 12, 0);
        }
    }

    public static void renderSliceTimer(MatrixStack matrix) {

        double sliceTimeRemaining = UserData.profile.sliceTimerEnd - System.currentTimeMillis();
        int sliceMinutesRemaining = (int) (sliceTimeRemaining / 60000);
        int sliceSecondsRemaining = (int) (sliceTimeRemaining - (sliceMinutesRemaining * 60000)) / 1000;

        GuiOverlay.renderBackgroundBar(matrix, 0);

        if(sliceTimeRemaining >= 0) {
            int brightRed = ColorHelper.PackedColor.packColor(255, 158, 26, 38);
            int darkRed = ColorHelper.PackedColor.packColor(255, 215, 0, 39);
            GuiOverlay.renderPercentBar(matrix, 1, sliceTimeRemaining/sliceTimeMS, darkRed, brightRed);
            mc.fontRenderer.drawString(matrix, String.format("%d:%02d", sliceMinutesRemaining, sliceSecondsRemaining), screenWidth() - 71, 1, 0);
            if(sliceTimeRemaining/1000 <= 5 && mc.ingameGUI.displayedTitle == null) {
                long window = mc.getMainWindow().getHandle();
                GLFW.glfwRequestWindowAttention(window);
                ITextComponent announcementText = new StringTextComponent(String.format("Slice Cooldown Ending In %d Seconds", (int) sliceTimeRemaining/1000))
                        .mergeStyle(TextFormatting.DARK_RED);
                mc.ingameGUI.func_238452_a_(announcementText, null, 1, 6, 1);
            }
        } else {
            mc.fontRenderer.drawString(matrix, "0:00", screenWidth() - 71, 1, 0);
        }

    }

    public static void computeStackPercents() {
        if(mc.player == null) return;
        Iterable<ItemStack> inventory = mc.player.inventory.mainInventory;
        ArrayList<ItemStack> diamondStacks = new ArrayList<>();
        ArrayList<ItemStack> emeraldStacks = new ArrayList<>();
        ArrayList<ItemStack> prismarineStacks = new ArrayList<>();
        for(ItemStack i : inventory) {
            String id = InventoryManagement.getItemID(i);
            if(id == null) return;
            if(id.equals("minecraft:diamond_block"))
                diamondStacks.add(i);
            if(id.equals("minecraft:emerald_block"))
                emeraldStacks.add(i);
            if(id.equals("minecraft:prismarine") || id.equals("minecraft:prismarine_bricks") || id.equals("minecraft:dark_prismarine"))
                prismarineStacks.add(i);
        }
//        double totalBlocks = allStacks.stream().mapToInt(ItemStack::getCount).sum();
//        double totalSize = allStacks.stream().mapToInt(ItemStack::getMaxStackSize).sum();
        double totalDiamondBlocks = diamondStacks.stream().mapToInt(ItemStack::getCount).sum();
        double totalEmeraldBlocks = emeraldStacks.stream().mapToInt(ItemStack::getCount).sum();
        double totalPrismarineBlocks = prismarineStacks.stream().mapToInt(ItemStack::getCount).sum();
        diamondPercentFull = totalDiamondBlocks / (diamondStacks.size() * 64);
        emeraldPercentFull = totalEmeraldBlocks / (emeraldStacks.size() * 64);
        prismarinePercentFull = totalPrismarineBlocks / (prismarineStacks.size() * 64);
    }

    public static void renderInventoryPercentBars(MatrixStack matrix) {

        if(mc.player == null) return;

        GuiOverlay.renderBackgroundBar(matrix, 22);
        int darkBlue = ColorHelper.PackedColor.packColor(255, 69, 172, 165);
        int lightBlue = ColorHelper.PackedColor.packColor(255, 0, 208, 205);
        GuiOverlay.renderPercentBar(matrix, 23, diamondPercentFull, darkBlue, lightBlue);
        mc.fontRenderer.drawString(matrix, (int) (diamondPercentFull * 100) + "%", screenWidth() - 71, 23, 0);

        GuiOverlay.renderBackgroundBar(matrix, 33);
        int darkGreen = ColorHelper.PackedColor.packColor(255, 18, 144, 41);
        int lightGreen = ColorHelper.PackedColor.packColor(255, 73, 186, 78);
        GuiOverlay.renderPercentBar(matrix, 34, emeraldPercentFull, darkGreen, lightGreen);
        mc.fontRenderer.drawString(matrix, (int) (emeraldPercentFull * 100) + "%", screenWidth() - 71, 34, 0);

        GuiOverlay.renderBackgroundBar(matrix, 44);
        int prismLight = ColorHelper.PackedColor.packColor(255, 79, 137, 129);
        int prismDark = ColorHelper.PackedColor.packColor(255, 40, 64, 54);
        GuiOverlay.renderPercentBar(matrix, 45, prismarinePercentFull, prismDark, prismLight);
        mc.fontRenderer.drawString(matrix, (int) (prismarinePercentFull * 100) + "%", screenWidth() - 71, 45, 0);

        if(mc.player.inventory.getFirstEmptyStack() == -1) {
            int percentColor = 0xffe100;
            if(diamondPercentFull > 0) {
                mc.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:diamond_block"))), (screenWidth() / 2) + 7, (screenHeight() / 2) - 25);
                mc.fontRenderer.drawString(matrix, (int) (diamondPercentFull * 100) + "%", (screenWidth() / 2) + 24, (screenHeight() / 2) - 21, 0);
                mc.fontRenderer.drawString(matrix, (int) (diamondPercentFull * 100) + "%", (screenWidth() / 2) + 25, (screenHeight() / 2) - 20, percentColor);
            }
            if(emeraldPercentFull > 0) {
                mc.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("minecraft:emerald_block"))), (screenWidth() / 2) + 7, (screenHeight() / 2) - 7);
                mc.fontRenderer.drawString(matrix, (int) (emeraldPercentFull * 100) + "%", (screenWidth() / 2) + 24, (screenHeight() / 2) - 3, 0);
                mc.fontRenderer.drawString(matrix, (int) (emeraldPercentFull * 100) + "%", (screenWidth() / 2) + 25, (screenHeight() / 2) - 2, percentColor);
            }
            if(prismarinePercentFull > 0) {
                mc.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(SnapCraftUtils.getPrismarineType()))), (screenWidth() / 2) + 7, (screenHeight() / 2) + 11);
                mc.fontRenderer.drawString(matrix, (int) (prismarinePercentFull * 100) + "%", (screenWidth() / 2) + 24, (screenHeight() / 2) + 15, 0);
                mc.fontRenderer.drawString(matrix, (int) (prismarinePercentFull * 100) + "%", (screenWidth() / 2) + 25, (screenHeight() / 2) + 16, percentColor);
            }
        }

    }

    public static void renderItemHighlightOverride(MatrixStack matrix) {

        int remainingHighlightTicks = mc.ingameGUI.remainingHighlightTicks;

        if(mc.playerController != null && mc.player != null && remainingHighlightTicks > 0) {

            ItemStack heldItem = mc.player.getHeldItemMainhand();
            if(InventoryManagement.itemIDsEqual(heldItem, "minecraft:air")) return;

            IFormattableTextComponent iformattabletextcomponent = (new StringTextComponent("")).append(heldItem.getDisplayName()).mergeStyle(heldItem.getRarity().color);
            if(heldItem.isDamageable())
                iformattabletextcomponent = iformattabletextcomponent.append(new StringTextComponent(String.format(" %s/%s", heldItem.getMaxDamage() - heldItem.getDamage(), heldItem.getMaxDamage())));
            FontRenderer font = heldItem.getItem().getFontRenderer(heldItem);

            int i = mc.ingameGUI.getFontRenderer().getStringPropertyWidth(iformattabletextcomponent);
            int j = (screenWidth() - i) / 2;
            int k = screenHeight() - 59;

            if (!mc.playerController.shouldDrawHUD()) k += 14;

            int l = (int)((float)remainingHighlightTicks * 256.0F / 10.0F);
            if (l > 255) l = 255;

            if(l > 0) {
                RenderSystem.pushMatrix();
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                IngameGui.fill(matrix, j - 2, k - 2, j + i + 2, k + 9 + 2, mc.gameSettings.getChatBackgroundColor(0));
                if(font == null) {
                    mc.ingameGUI.getFontRenderer().func_243246_a(matrix, iformattabletextcomponent, (float)j, (float)k, 16777215 + (l << 24));
                } else {
                    j = (screenWidth() - font.getStringPropertyWidth(iformattabletextcomponent)) / 2;
                    font.func_243246_a(matrix, iformattabletextcomponent, (float)j, (float)k, 16777215 + (l << 24));
                }
                RenderSystem.disableBlend();
                RenderSystem.popMatrix();
            }
        }

    }

    public static void renderLayoutItems(MatrixStack matrix) {

        if(LayoutHandler.currentLayoutIndex != null && mc.currentScreen instanceof ContainerScreen) {

            UserData.Layout layout = LayoutHandler.getCurrentLayout();
            List<Slot> slots = InventoryManagement.getMainInventorySlots();
            if(slots == null || layout == null) return;

            Matrix4f matrix4f = matrix.getLast().getMatrix();
            ItemRenderer itemRenderer = mc.getItemRenderer();

            for (int i = 0; i < slots.size(); i++) {
                if(i >= layout.slots.size()) continue;
                ItemStack storedStack = layout.slots.get(i).parseStackData();
                if(storedStack == null) continue;
                Slot currentSlot = slots.get(i);
                if(currentSlot.getHasStack()) continue;
                int x = currentSlot.xPos;
                int y = currentSlot.yPos;

                mc.getItemRenderer().renderItemAndEffectIntoGuiWithoutEntity(storedStack, x, y);

                BufferBuilder bufferbuilder = Tessellator.getInstance().getBuffer();
                RenderSystem.enableBlend();
                RenderSystem.disableTexture();
                RenderSystem.defaultBlendFunc();
                RenderSystem.disableDepthTest();
                int maxX = x + 16;
                int maxY = y + 16;
                int r = 111; int g = 111; int b = 111; int a = 125;
                bufferbuilder.begin(7, DefaultVertexFormats.POSITION_COLOR);
                bufferbuilder.pos(matrix4f, x, maxY, itemRenderer.zLevel).color(r, g, b, a).endVertex();
                bufferbuilder.pos(matrix4f, maxX, maxY, itemRenderer.zLevel).color(r, g, b, a).endVertex();
                bufferbuilder.pos(matrix4f, maxX, y, itemRenderer.zLevel).color(r, g, b, a).endVertex();
                bufferbuilder.pos(matrix4f, x, y, itemRenderer.zLevel).color(r, g, b, a).endVertex();
                bufferbuilder.finishDrawing();
                WorldVertexBufferUploader.draw(bufferbuilder);
                RenderSystem.enableTexture();
                RenderSystem.disableBlend();
            }

        }

    }

    public static void fillZ(MatrixStack matrixStack, int x, int y, int max_x, int max_y, int z, int color) {

    }

    public static void renderLayoutID(GuiScreenEvent.DrawScreenEvent event) {

        Screen screen = event.getGui();
        boolean inventoryOpen = screen instanceof InventoryScreen;
        boolean chestOpen = screen instanceof ChestScreen;

        if(inventoryOpen || chestOpen) {
            Integer chestRows = null;
            if(chestOpen)
                chestRows = ((ChestScreen) screen).getContainer().getNumRows();

            MatrixStack matrix = event.getMatrixStack();
            int x = (screenWidth() / 2) + 48;
            int y = (screenHeight() / 2) - 13;

            if(chestOpen) {
                x+= 25;
                y+= 2 + ((chestRows - 3) * 9);
            }

            if(inventoryOpen && InventoryManagement.recipeBookOpen)
                x+= 77;

            String text = LayoutHandler.currentLayoutIndex != null ? Integer.toString(LayoutHandler.currentLayoutIndex + 1) : "";
            mc.fontRenderer.drawString(matrix, text, x, y, 0);
        }

    }

    public static void renderLayoutButtons(GuiScreenEvent.InitGuiEvent.Post event) {

        Screen screen = event.getGui();
        boolean inventoryOpen = screen instanceof InventoryScreen;
        boolean chestOpen = screen instanceof ChestScreen;
        Integer chestRows = chestOpen ? ((ChestScreen) screen).getContainer().getNumRows() : null;

        if(chestOpen || inventoryOpen) {

            Button addLayout = new IdentifiableButton("isadoremod", (screenWidth() / 2) + 70, (screenHeight() / 2) - 26, 10, 10, 10, 0, 10, button_texture,  (button) -> {
                LayoutHandler.addLayout(mc.gameSettings.keyBindUseItem.isKeyDown());
            });
            Button removeLayout = new IdentifiableButton("isadoremod", (screenWidth() / 2) + 58, (screenHeight() / 2) - 26, 10, 10, 20, 0, 10, button_texture, (button) -> {
                UserData.Layout currentLayout = LayoutHandler.getCurrentLayout();
                if(LayoutHandler.currentLayoutIndex != null && currentLayout != null) {
                    UserData.deleteInventoryLayout(currentLayout.id);
                    LayoutHandler.deleteCurrentLayout();
                }
            });
            Button layoutBack = new IdentifiableButton("isadoremod", (screenWidth() / 2) + 58, (screenHeight() / 2) - 14, 10, 10, 30, 0, 10, button_texture, (button) -> {
                if(LayoutHandler.currentLayoutIndex != null) {
                    if(LayoutHandler.currentLayoutIndex > 0)
                        LayoutHandler.currentLayoutIndex--;
                    else
                        LayoutHandler.currentLayoutIndex = LayoutHandler.allLayouts.size() - 1;
                }
            });
            Button layoutForwards = new IdentifiableButton("isadoremod", (screenWidth() / 2) + 70, (screenHeight() / 2) - 14, 10, 10, 40, 0, 10, button_texture, (button) -> {
                if(LayoutHandler.currentLayoutIndex != null) {
                    if(LayoutHandler.allLayouts.size() > LayoutHandler.currentLayoutIndex + 1)
                        LayoutHandler.currentLayoutIndex++;
                    else
                        LayoutHandler.currentLayoutIndex = 0;
                }
            });
            Button applyCurrent = new IdentifiableButton("isadoremod", (screenWidth() / 2) + 46, (screenHeight() / 2) - 14, 10, 10, 0, 0, 10, button_texture, (button) -> {
                LayoutHandler.restoreLayout();
            });

            if(chestOpen) {
                applyCurrent.x += 25;
                applyCurrent.y += 2;
                layoutBack.x -= 11;
                layoutBack.y += 2;
                layoutForwards.x -= 11;
                layoutForwards.y += 2;
                addLayout.x -= 35;
                addLayout.y += 14;
                removeLayout.x -= 35;
                removeLayout.y += 14;
            }

            Button[] buttons = { addLayout, removeLayout, layoutBack, layoutForwards, applyCurrent };

            for(Button b : buttons) {
                if(InventoryManagement.recipeBookOpen && inventoryOpen) b.x += 77;
                if(chestOpen) b.y += (chestRows - 3) * 9;
                event.addWidget(b);
            }
        }

    }

    public static class IdentifiableButton extends ImageButton {
        String ID = "";
        public IdentifiableButton(String ID, int xIn, int yIn, int widthIn, int heightIn, int xTexStartIn, int yTexStartIn, int yDiffTextIn, ResourceLocation resourceLocationIn, Button.IPressable onPressIn) {
            super(xIn, yIn, widthIn, heightIn, xTexStartIn, yTexStartIn, yDiffTextIn, resourceLocationIn, onPressIn);
            this.ID = ID;
        }
    }

    public static void renderMineLayerValues(MatrixStack matrix) {

    }

    public static void renderItemAddNotifications(MatrixStack matrix) {

    }

}
