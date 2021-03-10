package com.isadore.isadoremod;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.ITextComponent;

import javax.annotation.Nullable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapCraftUtils {
    private static final Minecraft mc = Minecraft.getInstance();
    public static int lastPVAccessed = 1;

    @Nullable
    public static String getRankName() {
        ITextComponent displayName = mc.player.getDisplayName();
        String parsedRank = null;
        if(displayName != null) {
            String name = displayName.getString();
            Pattern rankPattern = Pattern.compile("^\\[(\\S+)\\]");
            Matcher rankMatcher = rankPattern.matcher(name);
            if(rankMatcher.find()) {
                parsedRank = rankMatcher.group(1);
            }
        }
        return parsedRank;
    }

    @Nullable
    public static Integer getRankPVCount() {
        String rank = getRankName();
        if(getRankName() != null) {
            switch (rank) {
                case "VIP":
                    return 1;
                case "MVP":
                    return 2;
                case "Brute":
                    return 3;
                case "Elite":
                    return 4;
                case "Dealer":
                    return 5;
                case "Legend":
                    return 6;
                case "Boss":
                    return 7;
            }
        }
        return null;
    }

}
