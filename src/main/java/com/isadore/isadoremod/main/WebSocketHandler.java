package com.isadore.isadoremod.main;

import com.google.gson.Gson;
import com.isadore.isadoremod.IsadoreMod;
import com.isadore.isadoremod.components.Recordings;
import com.isadore.isadoremod.components.SnapCraftUtils;
import net.minecraft.client.Minecraft;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.java_websocket.protocols.Protocol;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.Collections;

public class WebSocketHandler extends WebSocketClient {

    public static boolean connected = false;
    public static boolean localClose = false;
    @Nullable
    public static WebSocketClient client = null;
    public static Minecraft mc = Minecraft.getInstance();

    public static String discordStatus = "offline";
    public static double discordOnlineTimestamp = 0;
    public static double lastMessageReceived = 0;

    public enum MessageType {
        ChatMessage, DirectMessage, DiscordTyping, Mention, DiscordMessage, PING, AutoMinerStatus, ScreenShot, DiscordStatusUpdate
    }

    public static class Message {
        public String type;
        public String content;

        public Message(MessageType type, String content) {
            this.type = type.toString();
            this.content = content;
        }

        public MessageType getType() {
            return MessageType.valueOf(type);
        }

    }

    public WebSocketHandler(URI serverURI, String authToken) {
        super(serverURI, new Draft_6455(Collections.emptyList(), Collections.singletonList(new Protocol(authToken))));
    }

    public static void init() {
        try {
            if(UserData.profile.webSocketToken != null && (client == null || !client.isOpen())) {
                String authStr = IsadoreMod.playerID() + '/' + UserData.profile.webSocketToken;
                URI uri = new URI("wss://isadore.co/minecraft-mod/websocket");
                client = new WebSocketHandler(uri, authStr);
                client.connect();
            }
        } catch (Exception e) {
            IsadoreMod.LOGGER.error("Failed initializing websocket connection");
            e.printStackTrace();
        }
    }

    public static void disconnect() {
        if(client != null && client.isOpen()) {
            localClose = true;
            client.close();
            client = null;
        }
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        connected = true;
        IsadoreMod.LOGGER.info("Socket connection opened");
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        connected = false;
        IsadoreMod.LOGGER.info("Websocket closed with exit code " + code + " additional info: " + reason);
        Recordings.resetPlayerActions(false);
        if(!localClose) init();
        localClose = false;
    }

    @Override
    public void onMessage(String message) {
        Message parsedMessage = new Gson().fromJson(message, Message.class);
        IsadoreMod.LOGGER.info("received message: " + parsedMessage.type + " - " + parsedMessage.content);
        if(parsedMessage.getType() == MessageType.DiscordMessage) {
            if(mc.currentScreen instanceof SnapCraftUtils.CustomChatScreen) {
                ((SnapCraftUtils.CustomChatScreen) mc.currentScreen).defaultText = parsedMessage.content;
                EventHandler.tickQueue.add(new EventHandler.QueueDelay(500, 1000));
                EventHandler.tickQueue.add(() -> {
                    if(mc.player != null)
                        mc.player.sendChatMessage(parsedMessage.content);
                    if(mc.currentScreen != null)
                        mc.currentScreen.closeScreen();
                });
            } else {
                EventHandler.tickQueue.add(() -> Recordings.resetPlayerActions(false));
                EventHandler.tickQueue.add(new EventHandler.QueueDelay(500, 1000 + (50 * parsedMessage.content.length())));
                EventHandler.tickQueue.add(() -> SnapCraftUtils.handleCommand(parsedMessage.content));
            }
            lastMessageReceived = System.currentTimeMillis();
        } else if(parsedMessage.getType() == MessageType.DiscordTyping) {
            if(Boolean.parseBoolean(parsedMessage.content) && mc.currentScreen == null) {
                mc.displayGuiScreen(new SnapCraftUtils.CustomChatScreen(""));
            } else if(mc.currentScreen instanceof SnapCraftUtils.CustomChatScreen) {
                mc.currentScreen.closeScreen();
            }
        } else if(parsedMessage.getType() == MessageType.DiscordStatusUpdate) {
            if(parsedMessage.content.equals("online") || discordStatus.equals("online")) {
                discordOnlineTimestamp = System.currentTimeMillis();
            }
            discordStatus = parsedMessage.content;
        }
    }

    @Override
    public void onError(Exception ex) {
       IsadoreMod.LOGGER.error("Websocket Error:" + ex);
    }

    public static void sendMessage(MessageType type, String content) {
        if(connected && client != null) {
            byte[] message = new Gson().toJson(new Message(type, content)).getBytes();
            client.send(message);
        }
    }

}
