package com.isadore.isadoremod.main;

import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.components.InventoryManagement;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.network.IPacket;
import net.minecraft.network.play.client.CClickWindowPacket;
import net.minecraft.network.play.client.CPlayerTryUseItemPacket;
import net.minecraft.network.play.server.*;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

public class PacketHandler extends ChannelDuplexHandler {

    private static final Minecraft mc = Minecraft.getInstance();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object obj) throws Exception {
        super.channelRead(ctx, obj);
        if(obj instanceof IPacket) {
            this.onPacket((IPacket<?>) obj, true);
        }
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object obj, ChannelPromise promise) throws Exception {
        super.write(ctx, obj, promise);
        if(obj instanceof IPacket) {
            this.onPacket((IPacket<?>) obj, false);
        }
    }

    private void onPacket(IPacket<?> packet, boolean incoming) {
//        if(incoming)
//            IsadoreMod.LOGGER.info(packet.getClass().getSimpleName());
//        if(packet instanceof CClickWindowPacket) {
//            CClickWindowPacket ClickWindowPacket = (CClickWindowPacket) packet;
//            IsadoreMod.LOGGER.info("Stack: {}, Slot: {}, Button: {}, Type: {}", ClickWindowPacket.getClickedItem(), ClickWindowPacket.getSlotId(), ClickWindowPacket.getUsedButton(), ClickWindowPacket.getClickType());
//        }
//        if(packet instanceof SSetSlotPacket) {
//            SSetSlotPacket slotPacket = (SSetSlotPacket) packet;
//            IsadoreMod.LOGGER.info("Slot: {}, Stack: {}", slotPacket.getSlot(), slotPacket.getStack());
//        }
        if(packet instanceof SPlayerPositionLookPacket) {
            SPlayerPositionLookPacket sPlayerPositionLookPacket = (SPlayerPositionLookPacket) packet;
            IsadoreMod.LOGGER.debug("id: {}, x: {}, y: {}, z: {}", sPlayerPositionLookPacket.getTeleportId(), sPlayerPositionLookPacket.getX(), sPlayerPositionLookPacket.getY(), sPlayerPositionLookPacket.getZ());
        }
        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method m : methods) {
            Type[] params = m.getGenericParameterTypes();
            if(params.length > 0 && packet.getClass().getTypeName().equals(params[0].getTypeName())) {
                try {
                    if(params.length > 1 && params[1].getTypeName().equals("boolean")) {
                        m.invoke(this, packet, incoming);
                    } else {
                        m.invoke(this, packet);
                    }
                } catch (Exception e) {
                    IsadoreMod.LOGGER.error("Failed calling packet method");
                    e.printStackTrace();
                }
            }
        }
    }

    public int lastItemCount = 0;
    Map<String, Integer> lastItemCounts = new HashMap<>();

    public void onItemsUpdate(SWindowItemsPacket packet, boolean incoming) {
        int totalItems = packet.getItemStacks().stream().mapToInt(ItemStack::getCount).sum();
        int change = totalItems - lastItemCount;
        Map<String, Integer> itemCounts = new HashMap<>();
        for(ItemStack i : packet.getItemStacks()) {
            String itemID = InventoryManagement.getItemID(i);
            if(itemID == null) continue;
            itemCounts.computeIfAbsent(itemID, k -> i.getCount());
            itemCounts.computeIfPresent(itemID, (k, v) -> v + i.getCount());
        }
        StringBuilder changeStr = new StringBuilder();
        int maxChange = 0;
        int increasedItemTypes = 0;
        int decreasedItemTypes = 0;
        for (Map.Entry<String, Integer> i : lastItemCounts.entrySet()) {
            Integer currentVal = itemCounts.get(i.getKey());
            Integer lastVal = i.getValue();
            if(currentVal == null) {
                decreasedItemTypes++;
                changeStr.append(String.format("\n(%s, %s) ", i.getKey(), -lastVal));
            } else if(!currentVal.equals(lastVal)) {
                if(currentVal > lastVal)
                    increasedItemTypes++;
                else
                    decreasedItemTypes++;
                int currentChange = currentVal - lastVal;
                changeStr.append(String.format("\n(%s, %s) ", i.getKey(), change));
                if(currentChange > maxChange)
                    maxChange = currentChange;
            }
        }
        if(maxChange > 25 && packet.getWindowId() == 0 && mc.player != null) {
            Map<String, Integer> itemEnchants = InventoryManagement.getItemEnchants(mc.player.getHeldItemMainhand());
            if(itemEnchants != null) {
                if(itemEnchants.get("OreSeeker") != null && increasedItemTypes == 1 && decreasedItemTypes == 0)
                    UserData.profile.oreSeekTimerEnd = System.currentTimeMillis() + GuiOverlay.oreSeekTimeMS;
                if(itemEnchants.get("Slicing") != null)
                    UserData.profile.sliceTimerEnd = System.currentTimeMillis() + GuiOverlay.sliceTimeMS;
            }
            IsadoreMod.LOGGER.info(InventoryManagement.getItemEnchants(mc.player.getHeldItemMainhand()));
            IsadoreMod.LOGGER.info("ItemsPacket count: {}, Change count: {}, WindowID: {}, Incoming: {}, Changed items: {}", totalItems, change, packet.getWindowId(), incoming, changeStr.toString());
        }
        lastItemCount = totalItems;
        lastItemCounts = itemCounts;
    }

}


