package de.canitzp.rockbottommanagement;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Database {

    private static MongoDatabase DB_ROCKBOTTOM;
    private static MongoCollection<Document> C_ACCOUNT;

    public static boolean connect() {
        String login = readDatabaseLogin();
        if (login == null) {
            return false;
        }

        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(login))
                .retryWrites(true)
                .build();
        MongoClient mongoClient = MongoClients.create(settings);

        DB_ROCKBOTTOM = mongoClient.getDatabase("rockbottom");
        C_ACCOUNT = DB_ROCKBOTTOM.getCollection("account");

        return true;
    }

    private static String readDatabaseLogin() {
        try {
            return Files.readString(Path.of("./databaseLogin"));
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static FindIterable<Document> find(Bson filter) {
        return C_ACCOUNT.find(filter);
    }

    public static long count(Bson filter) {
        return C_ACCOUNT.countDocuments(filter);
    }

    public static UpdateResult update(Bson filter, Bson update) {
        return C_ACCOUNT.updateOne(filter, update);
    }

    public static InsertOneResult insert(Document document) {
        return C_ACCOUNT.insertOne(document);
    }

}
