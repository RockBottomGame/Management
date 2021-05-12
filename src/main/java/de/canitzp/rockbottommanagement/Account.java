package de.canitzp.rockbottommanagement;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class Account {

    private final Document dbDocument;
    private final ObjectId databaseId;
    private final String username;
    private final String email;
    private final UUID accountId;
    private final boolean verified;
    private final ObjectNode playerDesign;
    private final byte[] passwordHash;

    public Account(Document dbDocument, ObjectId id, String username, String email, UUID accountId, boolean verified, ObjectNode playerDesign, byte[] passwordHash) {
        this.dbDocument = dbDocument;
        this.databaseId = id;
        this.username = username;
        this.email = email;
        this.accountId = accountId;
        this.verified = verified;
        this.playerDesign = playerDesign;
        this.passwordHash = passwordHash;
    }

    public static Account fromDatabase(Bson filter) {
        FindIterable<Document> documents = Database.find(filter);
        Document first = documents.first();
        if (first == null) {
            return null;
        }

        ObjectId id = first.getObjectId("_id");
        String username = first.get("username", String.class);
        String email = first.get("e-mail", String.class);
        String accountIdRaw = first.get("account_id", "");
        UUID accountId = null;
        if (!accountIdRaw.isEmpty()) {
            accountId = UUID.fromString(accountIdRaw);
        }
        Boolean verifiedRaw = first.getBoolean("verified");
        boolean verified = verifiedRaw != null ? verifiedRaw : false;
        Document playerDesignRaw = first.get("player_design", new Document());
        ObjectNode playerDesign = Main.stringToJson(playerDesignRaw.toJson());
        byte[] dbPasswordHash = first.get("password", String.class).getBytes(StandardCharsets.UTF_8);

        return new Account(first, id, username, email, accountId, verified, playerDesign, dbPasswordHash);
    }

    public Document getDocument() {
        return this.dbDocument;
    }

    public ObjectId getDatabaseId() {
        return databaseId;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public boolean isVerified() {
        return verified;
    }

    public ObjectNode getPlayerDesign() {
        return playerDesign;
    }

    public boolean isPasswordCorrect(byte[] toVerify) {
        return BCrypt.verifyer().verify(toVerify, this.passwordHash).verified;
    }

    public ObjectNode toJson(ObjectNode json) {
        json.put("username", this.username);
        json.put("e-mail", this.email);
        json.put("account_id", this.accountId.toString());
        json.put("verified", this.verified);
        json.set("player_design", this.playerDesign);
        return json;
    }

    public Bson createDatabaseFilter() {
        return Filters.eq("_id", this.getDatabaseId());
    }

    public String getVerificationCode() {
        Document dbEntry = Database.find(Filters.eq("_id", this.databaseId)).first();
        if (dbEntry != null) {
            Object verificationCodeRaw = dbEntry.get("verification_code");
            return verificationCodeRaw instanceof String ? (String) verificationCodeRaw : null;
        }
        return null;
    }

    public void invalidateAPIKey(String key) {
        int idx = -1;
        List<Document> list = this.getDocument().getList("keys", Document.class);
        for (int i = 0; i < list.size(); i++) {
            Document keys = list.get(i);
            if (keys.get("key").equals(key)) {
                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            Database.update(this.createDatabaseFilter(),
                    Updates.combine(
                            Updates.set("keys." + idx, null),
                            Updates.pull("keys", null)
                    )
            );
        }
    }

    @Override
    public String toString() {
        return "Account{" +
                "databaseId=" + databaseId +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                ", accountId=" + accountId +
                ", verified=" + verified +
                ", playerDesign=" + playerDesign +
                ", passwordHash=" + Arrays.toString(passwordHash) +
                '}';
    }
}
