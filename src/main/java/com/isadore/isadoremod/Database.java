package com.isadore.isadoremod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.util.JSON;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.player.ClientPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bson.Document;
import org.bson.types.ObjectId;

import javax.annotation.Nullable;
import java.util.ArrayList;

public class Database {
    private MongoCollection<Document> coinFlipCollection;
    private MongoCollection<Document> inventoryLayoutCollection;

    public static class InventoryDoc {
        String userID;
        String userName;
        ArrayList<InventoryManagement.StoredSlot> slots;

        @Nullable
        public InventoryManagement.StoredSlot getSlot(int index) {
            for(InventoryManagement.StoredSlot s : slots) {
                if(s.index == index)
                    return s;
            }
            return null;
        }

    }

    private static final Logger LOGGER = LogManager.getLogger();

    public Database() {

        MongoClient client = new MongoClient(new MongoClientURI("mongodb://mod:LdFpTAJm4L3hBdrp@71.127.202.151:27017/isadore-mod"));
        MongoDatabase database = client.getDatabase("isadore-mod");
        coinFlipCollection = database.getCollection("coinFlips");
        inventoryLayoutCollection = database.getCollection("inventoryLayouts");

    }

    public void addCoinFlip(boolean didWin, String opponent, Float amount) {

        ClientPlayerEntity player = Minecraft.getInstance().player;
//        ServerPlayerEntity opponentEntity = player.getServer().getPlayerList().getPlayerByUsername(opponent.trim());
//        LOGGER.info(opponentEntity.getUniqueID().toString());

        Document doc = new Document("didWin", didWin)
                .append("userID", player.getUniqueID().toString())
                .append("userName", player.getName().getString())
                .append("opponentName", opponent)
//                .append("opponentID", opponentEntity.getUniqueID().toString())
                .append("amount", amount)
                .append("timestamp", System.currentTimeMillis());

        coinFlipCollection.insertOne(doc);

    }

    public void saveInventoryLayout(ArrayList<InventoryManagement.StoredSlot> slots) {

        ClientPlayerEntity player = Minecraft.getInstance().player;
        String playerID = player.getUniqueID().toString();

        String slotJSON = new Gson().toJson(slots);

        Document doc = new Document("userID", playerID)
                .append("userName", player.getName().getString())
                .append("slots", JSON.parse(slotJSON));

        inventoryLayoutCollection.replaceOne(Filters.eq("userID", playerID), doc, new UpdateOptions().upsert(true));

    }

    @Nullable
    public InventoryDoc getInventoryLayout() {

        ClientPlayerEntity player = Minecraft.getInstance().player;
        String playerID = player.getUniqueID().toString();

        FindIterable<Document> docs = inventoryLayoutCollection.find(Filters.eq("userID", playerID));
        Document firstDoc = docs.first();

        if(firstDoc != null) {
            InventoryDoc parsedDoc = new Gson().fromJson(firstDoc.toJson(), InventoryDoc.class);
            return parsedDoc;
        }

        return null;
    }

}

class CoinFlipDoc {
    ObjectId _id = null;
    Boolean won = null;
    String user = null;
    int amount = 0;
    long timestamp = 0;
}


