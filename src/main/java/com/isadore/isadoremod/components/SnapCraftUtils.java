package com.isadore.isadoremod.components;

import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.main.EventHandler;
import com.isadore.isadoremod.main.UserData;
import javafx.geometry.Point3D;
import net.minecraft.block.BlockState;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.inventory.ChestScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.scoreboard.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.client.event.ClientChatEvent;
import net.minecraftforge.common.MinecraftForge;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapCraftUtils {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final Minecraft mc = Minecraft.getInstance();
    private static final int commandRateLimitMS = 250;
    private static long lastCommandSentTimestamp = 0;
    private static String tokenTeamName = null;
    private static String mineTeamName = null;
    public static int lastPVAccessed = 1;
    public static boolean lastChestWasPV = false;

    public static boolean serverIsSnapcraft() {
        ServerData server = mc.getCurrentServerData();
        if(server != null) return server.serverIP.equals("snapcraft.net");
        return false;
    }

    @Nullable
    public static String getRankName() {
        String parsedRank = null;
        if(mc.player != null) {
            ITextComponent displayName = mc.player.getDisplayName();
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
        if(rank != null) {
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

    @Nullable
    public static Integer getCurrentPVNum() {
        Screen screen = mc.currentScreen;
        return getPVNum(screen);
    }

    @Nullable
    public static Integer getPVNum(@Nullable Screen screen) {
        Integer vaultNum = null;
        if(screen instanceof ChestScreen) {
            String chestTitle = screen.getTitle().getString();
            if(chestTitle.contains("Vault #")) {
                try {
                    vaultNum = Integer.parseInt(chestTitle.substring(9));
                } catch (Exception ignored) {
                }
            }
        }
        return vaultNum;
    }

    public static void updateLastAccessedPV(Screen screen) {
        Integer currentPV = getPVNum(screen);
        if(currentPV != null) SnapCraftUtils.lastPVAccessed = currentPV;
        SnapCraftUtils.lastChestWasPV = (currentPV != null);
    }

    public static void openPV(int PVNum) {
        Integer maxPV = getRankPVCount();
        Integer currentPV = getCurrentPVNum();
        if(mc.player != null && maxPV != null && PVNum > 0 && PVNum <= maxPV) {
            if(currentPV != null && currentPV == PVNum) return;
            if(currentPV != null) mc.player.closeScreen();
            handleCommand("/pv " + PVNum, IsadoreMod.playerIsIsadore());
        }
    }

    @Nullable
    public static String getPlayerMine() {
        ClientPlayerEntity player = mc.player;
        if(mc.player != null) {
            Scoreboard scoreboard = player.getWorldScoreboard();
            Collection<ScorePlayerTeam> teams = scoreboard.getTeams();
            ScorePlayerTeam mineTeam = null;
            if(mineTeamName != null) {
                mineTeam = scoreboard.getTeam(mineTeamName);
            } else {
                for(ScorePlayerTeam t : teams) {
                    if(fullTeamStr(t).startsWith("  Mine "))
                        mineTeam = t;
                }
            }
            if(mineTeam != null) {
                String mine = fullTeamStr(mineTeam).replaceFirst("  Mine ", "");
                UserData.profile.mine = mine;
                return mine;
            }
        }
        return UserData.profile.mine;
    }

    public static class MineCoordinates {
        public Point3D layersStart;
        public int width;
        public int depth;
        public Point3D spawn;

        public MineCoordinates(Point3D layersStart, int w, int d, Point3D spawn) {
            this.layersStart = layersStart;
            this.width = w;
            this.depth = d;
            this.spawn = spawn;
        }

    }

    @Nullable
    public static MineCoordinates getMineInfo() {
        String mine = getPlayerMine();
        switch (mine != null ? mine : "") {
            case "Prestige3":
                return new MineCoordinates(new Point3D(3009, 71, 1476), 49, 66, new Point3D(3000.500, 72.0, 1500.500));
            case "Prestige2":
                return new MineCoordinates(new Point3D(2525, 70, 1476), 49, 66, new Point3D(2500.500, 72.0, 1500.500));
            case "Prestige1":
                return new MineCoordinates(new Point3D(2007, 71, 1476), 49, 66, new Point3D(2000.500, 72.0, 1500.500));
            default:
                return null;
        }

    }

    public static String getPrismarineType() {
        String mine = getPlayerMine();
        switch (mine != null ? mine : "") {
            case "Prestige3":
                return "minecraft:dark_prismarine";
            case "Prestige2":
                return "minecraft:prismarine_bricks";
            default:
                return "minecraft:prismarine";
        }
    }

    @Nullable
    public static byte[][][] getEmptyMineBlocks() {
        MineCoordinates coords = getMineInfo();
        if(mc.world == null || coords == null) return null;
        int startX = (int) coords.layersStart.getX(); // max 3057
        int startY = (int) coords.layersStart.getY(); // min 6
        int startZ = (int) coords.layersStart.getZ(); // max 1524
        byte[][][] blocks = new byte[coords.depth][coords.width][coords.width];
        for (int d = 0; d < coords.depth; d++) {
            int layerCount = 0;
            for (int h = 0; h < coords.width; h++) {
                int sliceCount = 0;
                for (int w = 0; w < coords.width; w++) {
                    int x = startX + h;
                    int y = startY - d;
                    int z = startZ + w;
                    BlockState blockState = mc.world.getBlockState(new BlockPos(x, y, z));
                    boolean isEmpty = blockState.getMaterial() == Material.AIR;
                    if(isEmpty) {
                        layerCount++;
                        sliceCount++;
                        blocks[d][h][w] = 1;
                    }
                    if(w == coords.width - 1) {
                        if(sliceCount == 0) blocks[d][h] = null;
                        if(sliceCount == coords.width) blocks[d][h] = new byte[0];
                    }
                    if(h == (coords.width - 1)  && w == (coords.width - 1)) {
                        if(layerCount == 0) blocks[d] = null;
                        if(layerCount == Math.pow(coords.width, 2)) blocks[d] = new byte[0][];
                    }
                }
            }
        }
        return blocks;
    }

    @Nullable
    public static Double percentMinesEqual(byte[][][] current, byte[][][] stored) {
        MineCoordinates coords = getMineInfo();
        Integer sharedBlockCount = sharedBlockCount(current, stored);
        if(coords == null || sharedBlockCount == null) return null;
        int totalBlocks = (coords.depth) * (coords.width) * (coords.width);
        return (double) sharedBlockCount / totalBlocks;
    }

    @Nullable
    public static Integer sharedBlockCount(byte[][][] current, byte[][][] stored) {
        MineCoordinates coords = getMineInfo();
        if(coords == null) return null;
        int sharedBlocks = 0;
        for (int i = 0; i < current.length; i++) {
            if((current[i] == null && stored[i] == null) || ((current[i] != null && current[i].length == 0) && (stored[i] != null && stored[i].length == 0))) {
                sharedBlocks += Math.pow(coords.width, 2);
            } else if(current[i] != null && current[i].length != 0 && stored[i] != null && stored[i].length > 0) {
                for (int j = 0; j < coords.width; j++) {
                    if((current[i][j] == null && stored[i][j] == null) || ((current[i][j] != null && current[i][j].length == 0) && (stored[i][j] != null && stored[i][j].length == 0))) {
                        sharedBlocks += coords.width;
                    } else if(current[i][j] != null && current[i][j].length != 0 && stored[i][j] != null && stored[i][j].length > 0) {
                        for (int k = 0; k < current[i][j].length; k++) {
                            if(current[i][j][k] == stored[i][j][k]) sharedBlocks++;
                        }
                    }
                }
            }
        }
        return sharedBlocks;
    }

    public static String fullTeamStr(ScorePlayerTeam team) {
        return team.getPrefix().getString() + team.getSuffix().getString();
    }

    @Nullable
    public static Integer getTokenCount() {
        ClientPlayerEntity player = mc.player;
        if(mc.player != null) {
            Scoreboard scoreboard = player.getWorldScoreboard();
            Collection<ScorePlayerTeam> teams = scoreboard.getTeams();
            for(ScorePlayerTeam t : teams) {
                if(t.getPrefix().getString().contains("Tokens"))
                    return (int) Float.parseFloat(t.getSuffix().getString());
            }
        }
        return null;
    }

    private static boolean shouldSendCommand() { return lastCommandSentTimestamp + commandRateLimitMS < System.currentTimeMillis(); };
    public static void handleCommand(String command, @Nullable Runnable preTask, @Nullable Runnable postTask, boolean human) {
        if(shouldSendCommand() && mc.player != null) {
            if(preTask != null) preTask.run();
            if(human) {
                EventHandler.cancelInputUntil = System.currentTimeMillis() + 1000 - (Math.pow(new Random().nextDouble(), 2) * 750);
                mc.displayGuiScreen(new CustomChatScreen(command));
                EventHandler.tickQueue.add(() -> {
                    if(mc.currentScreen != null) {
//                        mc.currentScreen.sendMessage(command);
                        if(!MinecraftForge.EVENT_BUS.post(new ClientChatEvent(command)))
                            mc.player.sendChatMessage(command);
                        try {
                            mc.currentScreen.closeScreen();
                        } catch (Exception ex) {
                            LOGGER.info(ex.getMessage());
                        }
                    }
                });
            } else {
                if(!MinecraftForge.EVENT_BUS.post(new ClientChatEvent(command)))
                    mc.player.sendChatMessage(command);
            }
//            mc.ingameGUI.getChatGUI().addToSentMessages(command);
            lastCommandSentTimestamp = System.currentTimeMillis();
            if(postTask != null) postTask.run();
        }
    }

    public static class CustomChatScreen extends ChatScreen {

        public String defaultText;

        public CustomChatScreen(String defaultText) {
            super(defaultText);
            this.defaultText = defaultText;
        }

        public void tick() {
            this.inputField.setText(this.defaultText);
        }

    }

    public static void handleCommand(String command) {
        handleCommand(command, null, null, false);
    }

    public static void handleCommand(String command, boolean human) {
        handleCommand(command, null, null, human);
    }

    public static double exponentialRandomTime(int power, int msMin, int msMax) {
        return System.currentTimeMillis() + exponentialRandomInt(power, msMin, msMax);
    }

    public static double exponentialRandomTime(int power, double sMin, double sMax) {
        return exponentialRandomTime(power, (int) (sMin * 60 * 1000), (int) (sMax * 60 * 1000));
    }

    public static double squaredRandomTime(int msMin, int msMax) {
        return exponentialRandomTime(2, msMin, msMax);
    }

    public static double squaredRandomTime(double sMin, double sMax) {
        return squaredRandomTime((int) (sMin * 60 * 1000), (int) (sMax * 60 * 1000));
    }

    public static int exponentialRandomInt(int power, int min, int max) {
        return max - (int) (Math.pow(new Random().nextDouble(), power) * (max - min));
    }

    public static boolean messageHasNicknameMatch(String msg) {
        String[] splitMsg = msg.split("\\u00BB");
        String lowercased = msg.toLowerCase(Locale.ROOT);
        if(splitMsg.length == 2) {
            lowercased = splitMsg[1].toLowerCase(Locale.ROOT);
        }
        if(lowercased.contains(mc.session.getUsername().toLowerCase(Locale.ROOT)))
            return true;
        if(UserData.profile.nickNames != null) {
            for (String s : UserData.profile.nickNames) {
                if(lowercased.contains(s.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
        }
        return false;
    }

    public static String[] blacklist = {
            "[Warning] Ground items will be removed in",
            "+5 tokens for mining 250 blocks! (Use tokens to /enchant)",
            "Your inventory is full!",
            "You have received 5 tokens.",
            "A ghost with tokens! Kill it to receive 10 Enchant Tokens!",
            "-------------------------------------------",
            "A ghost with a repair token! Kill it to receive a Repair Token",
            "The stone is alive! Kill it to receive",
            "--------------- Announcement ---------------",
            "================================================",
            "http://snapcraft.net/vote",
            "Click the following link to start voting:",
            "Top 5 voters receive $20 Webshop vouchers",
            "You have not voted today! Vote for daily rewards in every server!",
            "Received:",
            "Amount Sold:",
            "Multiplier:",
            "**STRUCK WITH GREAT FORCE**",
            "Knowing",
            "You don't have permission to break this sign.",
            "Have an admin set the flag: use/break"
    };

    public static boolean messageIsBlackListed(String msg) {
        String lowercased = msg.toLowerCase(Locale.ROOT);
        // Heart unicode
        if(msg.contains(Character.toString((char) 0x2764))) return true;
        for (String s : blacklist) {
            if(lowercased.startsWith(s.toLowerCase(Locale.ROOT)))
                return true;
        }
        return false;
    }

}
