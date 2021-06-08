package com.isadore.isadoremod.main;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.components.InventoryManagement;
import com.isadore.isadoremod.components.Recordings;
import com.isadore.isadoremod.components.SnapCraftUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import net.minecraftforge.event.TickEvent;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class UserData {

    private static final Minecraft mc = Minecraft.getInstance();
    private static final File baseDir = new File(mc.gameDir, "IsadoreMod");
    @Nullable
    private static File playerDir = null;
    @Nullable
    private static File invDir = null;
    @Nullable
    private static File recordDir = null;
    @Nullable
    private static File profilePath = null;

    public static PlayerProfile profile = new PlayerProfile();

    public static class PlayerProfile {
        @Nullable
        public String pwName = null;
        @Nullable
        public String[] nickNames = {};
        @Nullable
        public String webSocketToken = null;
        @Nullable
        public String mine = null;
        public double sliceTimerEnd = 0;
        public double oreSeekTimerEnd = 0;
    }

    public static class Layout {
        public ArrayList<InventoryManagement.StoredSlot> slots;
        public String id;
        public Layout(ArrayList<InventoryManagement.StoredSlot> slots, String id) {
            this.slots = slots; this.id = id;
        }
    }

    public enum RecordingType {
        SLICE, GPICK
    }

    public static class TickRecording {
        public double timeStamp = System.currentTimeMillis();
        public TickEvent.Phase phase;
        public double cameraPitch;
        public double cameraYaw;
        public double posX;
        public double posY;
        public double posZ;
        public boolean forwardsKeybindPressed;
        public boolean backwardsKeybindPressed;
        public boolean leftKeybindPressed;
        public boolean rightKeybindPressed;
        public boolean jumpKeybindPressed;
        public boolean sneakKeybindPressed;
        public boolean attackKeybindPressed;
        public boolean sprinting;
        public boolean sneaking;
        public boolean flying;
        @Nullable
        public byte[][][] emptyMineBlocks;

        public TickRecording(Minecraft mc, ClientPlayerEntity player, TickEvent.Phase phase) {
            this.phase = phase;
            this.cameraPitch = player.rotationPitch;
            this.cameraYaw = player.rotationYaw;
            this.posX = player.getPosX();
            this.posY = player.getPosY();
            this.posZ = player.getPosZ();
            this.forwardsKeybindPressed = mc.gameSettings.keyBindForward.isKeyDown();
            this.backwardsKeybindPressed = mc.gameSettings.keyBindBack.isKeyDown();
            this.leftKeybindPressed = mc.gameSettings.keyBindLeft.isKeyDown();
            this.rightKeybindPressed = mc.gameSettings.keyBindRight.isKeyDown();
            this.jumpKeybindPressed = mc.gameSettings.keyBindJump.isKeyDown();
            this.sneakKeybindPressed = mc.gameSettings.keyBindSneak.isKeyDown();
            this.attackKeybindPressed = mc.gameSettings.keyBindAttack.isKeyDown();
            this.sprinting = player.isSprinting();
            this.sneaking = player.isSneaking();
            this.flying = player.abilities.isFlying;
            this.emptyMineBlocks = SnapCraftUtils.getEmptyMineBlocks();
        }
    }

    public static class Recording {
        public ArrayList<TickRecording> ticks;
        public long timestamp;
        public RecordingType type;
        public Recording(ArrayList<TickRecording> ticks, long timestamp, RecordingType type) {
            this.ticks = ticks;
            this.timestamp = timestamp;
            this.type = type;
        }
    }

    public static void init() {
        try {
            baseDir.mkdir();
            playerDir = new File(baseDir, IsadoreMod.playerID());
            playerDir.mkdir();
            invDir = new File(playerDir, "inventory_layouts");
            invDir.mkdir();
            recordDir = new File(playerDir, "tick_recordings");
            recordDir.mkdir();
            profilePath = new File(playerDir, "profile.json");
            if(profilePath.exists()) {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(profilePath)));
                    profile = new Gson().fromJson(reader, PlayerProfile.class);
                } catch (Exception e) {
                    IsadoreMod.LOGGER.error("Failed reading player profile");
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            IsadoreMod.LOGGER.error("Failed initializing directories: {}", e.getMessage());
        }
    }

    public static void writeProfile() {
        if(profilePath != null) {
            try {
                Files.write(profilePath.toPath(), new Gson().toJson(profile).getBytes());
            } catch (Exception e) {
                IsadoreMod.LOGGER.error("Failed writing player profile");
                e.printStackTrace();
            }
        }
    }

    @Nullable
    public static Layout addInventoryLayout(ArrayList<InventoryManagement.StoredSlot> slots, @Nullable String id) {
        if(invDir != null) {
            if(id == null)
                id = UUID.randomUUID().toString();
            Path layoutPath = new File(invDir, id + ".json").toPath();
            try {
                Files.write(layoutPath, new Gson().toJson(slots).getBytes());
                return new Layout(slots, id);
            } catch (Exception e) {
                IsadoreMod.LOGGER.error("Failed writing inventory layout: {}", e.toString());
            }
        }
        return null;
    }

    @Nullable
    public static ArrayList<Layout> getInventoryLayouts() {
        if(invDir != null) {
            ArrayList<Layout> layouts = new ArrayList<>();
            for (File file : invDir.listFiles()) {
                String layoutID = file.getName().replace(".json", "");
                try {
//                    BufferedReader reader = Files.newBufferedReader(file.toPath());
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
                    ArrayList<InventoryManagement.StoredSlot> slots = new Gson().fromJson(reader, new TypeToken<ArrayList<InventoryManagement.StoredSlot>>(){}.getType());
                    layouts.add(new Layout(slots, layoutID));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return layouts;
        }
        return null;
    }

    public static void deleteInventoryLayout(String id) {
        if(invDir != null) {
            for (File file : invDir.listFiles()) {
                String layoutID = file.getName().replace(".json", "");
                if(layoutID.equals(id)) file.delete();
            }
        }
    }

    public static void saveRecording(RecordingType type, byte[][][] initialState, ArrayList<TickRecording> ticks) {
        if(recordDir != null && type != null && initialState != null && ticks != null) {
            long saveTimestamp = System.currentTimeMillis();
            File layoutDir = new File(recordDir, type + "_" + saveTimestamp);
            layoutDir.mkdir();
            Path statePath = new File(layoutDir, "mine_state.json").toPath();
            Path recordingPath = new File(layoutDir, "ticks.json").toPath();
            try {
                Files.write(statePath, new Gson().toJson(initialState).getBytes());
                Files.write(recordingPath, new Gson().toJson(ticks).getBytes());
                initialStates.add(new MineState(initialState, saveTimestamp, type));
            } catch (Exception e) {
                IsadoreMod.LOGGER.error("Failed writing tick recording");
                e.printStackTrace();
            }
        }
    }

    @Nullable
    public static ArrayList<TickRecording> getRandomRecording() {
        if(recordDir != null) {
            File[] dirs = recordDir.listFiles(File::isDirectory);
            if(dirs.length > 0) {
                try {
                    File dir = dirs[new Random().nextInt(dirs.length)];
                    File ticks = new File(dir, "ticks.json");
                    BufferedReader reader = Files.newBufferedReader(ticks.toPath());
                    return new Gson().fromJson(reader, new TypeToken<ArrayList<TickRecording>>(){}.getType());
                } catch (Exception e) {
                    IsadoreMod.LOGGER.error("Failed getting recording: {}", e.toString());
                }
            }
        }
        return null;
    }


    public static class MineState {
        public byte[][][] state;
        public long timestamp;
        public RecordingType type;
        public MineState(byte[][][] state, long timestamp, RecordingType type) {
            this.state = state;
            this.timestamp = timestamp;
            this.type = type;
        }
    }

    public static ArrayList<MineState> initialStates = null;

    public static void loadInitialStates() {
        if(recordDir != null && initialStates == null) {
            File[] dirs = recordDir.listFiles(File::isDirectory);
            ArrayList<MineState> states = new ArrayList<>();
            for (File d : dirs) {
                String dirName = d.getName();
                RecordingType type = dirName.startsWith("GPICK") ? RecordingType.GPICK : RecordingType.SLICE;
                try {
                    long timestamp = Long.parseLong(dirName.replace(type.name() + "_", ""));
                    File state = new File(d, "mine_state.json");
                    BufferedReader stateReader = Files.newBufferedReader(state.toPath());
                    byte[][][] parsedState = new Gson().fromJson(stateReader, byte[][][].class);
                    if (parsedState == null) {
                        deleteRecording(timestamp);
                        continue;
                    }
                    states.add(new MineState(parsedState, timestamp, type));
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.gc();
            }
            initialStates = states;
        }
    }

    @Nullable
    public static Recording getBestRecording(RecordingType type) {
        if(recordDir != null) {
            byte[][][] currentState = SnapCraftUtils.getEmptyMineBlocks();
            ArrayList<MineState> states = new ArrayList<>();
            if (currentState != null) {
                for (MineState s : initialStates) {
                    if(s.type == type && !Recordings.playedRecordings.contains(s.timestamp)) {
                        double percentMatch = SnapCraftUtils.percentMinesEqual(currentState, s.state);
                        if(percentMatch > .50)
                            states.add(s);
                    }
                }
            }
            if(states.size() > 0) {
                try {
                    MineState randState = states.size() > 1 ? states.get(new Random().nextInt(states.size() - 1)) : states.get(0);
                    File recDir = new File(recordDir, type.toString() + "_" + randState.timestamp);
                    File ticks = new File(recDir, "ticks.json");
                    if(!ticks.exists()) {
                        deleteRecording(randState.timestamp);
                    } else {
                        BufferedReader tickReader = Files.newBufferedReader(ticks.toPath());
                        ArrayList<TickRecording> parsedTicks = new Gson().fromJson(tickReader, new TypeToken<ArrayList<TickRecording>>(){}.getType());
                        if(parsedTicks != null && parsedTicks.size() > 1) {
                            return new Recording(parsedTicks, randState.timestamp, type);
                        } else {
                            deleteRecording(randState.timestamp);
                        }
                    }
                } catch (Exception e) {
                    IsadoreMod.LOGGER.error("Failed getting recording");
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void deleteRecording(long id) {
        if(recordDir != null) {
            File[] dirs = recordDir.listFiles(File::isDirectory);
            for (File d : dirs) {
                try {
                    if(d.getName().endsWith(String.valueOf(id))) {
                        for (int i = 0; i < initialStates.size(); i++) {
                            MineState state = initialStates.get(i);
                            if(state.timestamp == id) {
                                initialStates.remove(i);
                            }
                        }
                        File ticks = new File(d, "ticks.json");
                        File state = new File(d, "mine_state.json");
                        ticks.delete();
                        state.delete();
                        d.delete();
                    }
                } catch (Exception e) {
                    IsadoreMod.LOGGER.error("Failed deleting recording");
                    e.printStackTrace();
                }
            }
        }
    }

}
