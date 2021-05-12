package de.canitzp.rockbottommanagement;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.javalin.http.Context;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.bson.BsonDocument;
import org.bson.Document;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class KeyManager {

    public static boolean checkAccountFromHeader(Context context, Consumer<Account> consumer) {
        String header = context.header("X-API-Key");
        if (header == null) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_API_KEY_MISSING).send(context);
            return false;
        }

        Account account = KeyManager.getAccountForKey(header);
        if (account == null) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_API_KEY_INVALID).send(context);
            return false;
        }

        consumer.accept(account);
        return true;
    }

    public static Account getAccountForKey(String key) {
        BsonDocument filter = BsonDocument.parse("{'keys.key': '" + key + "'}");
        Account account = Account.fromDatabase(Filters.eq("keys.key", key));
        if (account == null) {
            return null;
        }

        // mark account as used, so the api key isn't getting obsolete
        int idx = -1;
        boolean keyInvalid = false;
        List<Document> list = account.getDocument().getList("keys", Document.class);
        for (int i = 0; i < list.size(); i++) {
            Document keys = list.get(i);
            if (keys.get("key").equals(key)) {

                long lastUsed = keys.getLong("lastUsed");
                if (lastUsed + TimeUnit.DAYS.toMillis(7) < System.currentTimeMillis()) {
                    // key to old => invalidate
                    keyInvalid = true;
                }

                idx = i;
                break;
            }
        }
        if (idx >= 0) {
            if (keyInvalid) {
                System.out.println("Invalid key");
                Database.update(account.createDatabaseFilter(), Updates.set("keys." + idx, null));
                Database.update(account.createDatabaseFilter(), Updates.pull("keys", null));
                return null;
            }
            Database.update(filter, Updates.set("keys." + idx + ".lastUsed", System.currentTimeMillis()));
        } else {
            System.out.println("Can't update 'lastUsed' parameter, array index not found.");
        }
        return account;
    }

    public static Pair<Account, UUID> getAccountOnLogin(String email, byte[] passwordHash) {
        Account account = Account.fromDatabase(Filters.eq("e-mail", email));
        if (account == null) {
            return null;
        }

        if (!account.isPasswordCorrect(passwordHash)) {
            return null;
        }

        // generate valid api key
        UUID uuid;
        do {
            uuid = UUID.randomUUID();
        } while (Database.find(BsonDocument.parse("{'keys.key': '" + uuid + "'}")).first() != null);

        Database.update(account.createDatabaseFilter(), BsonDocument.parse("{$push: {'keys': {'key': '" + uuid + "', 'lastUsed': " + System.currentTimeMillis() + "}}}"));

        return Pair.of(account, uuid);
    }

    public static byte[] createPassword(byte[] password) {
        return BCrypt.withDefaults().hash(10, password);
    }

}
