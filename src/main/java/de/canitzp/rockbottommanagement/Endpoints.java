package de.canitzp.rockbottommanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import de.canitzp.rockbottommanagement.model.*;
import io.javalin.http.Context;
import io.javalin.plugin.openapi.annotations.*;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpStatus;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.json.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class Endpoints {

    private static final String REGEX_USERNAME = "[ \\-0-9A-Z_a-z]+";
    private static final String REGEX_PASSWORD = "(.)*";
    private static final String REGEX_EMAIL = "(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)\\])";

    @OpenApi(
            path = "/user",
            summary = "Get user information with api-key",
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            }
    )
    public static void getUser(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            context.json(account.toJson(JsonNodeFactory.instance.objectNode()));
        });
    }

    @OpenApi(
            path = "/user/create",
            method = HttpMethod.POST,
            summary = "Creates a account",
            description = "Create a account. Needs the desired e-mail, username and password within a json. Checks if username and e-mail aren't already taken and for the regex to match.",
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = CreateAccountModel.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = CreateAccountModel.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "Account was created fine."),
                    @OpenApiResponse(status = "409", description = "Username or E-Mail are already taken. Check the 'RBM-Error'-header for more information."),
                    @OpenApiResponse(status = "422", description = "Input error. Check the 'RBM-Error'-header for more information.")
            }
    )
    public static void create(Context context) {
        ObjectNode json = Main.stringToJson(context.body());
        if (json.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
            return;
        }
        String email = json.path("e-mail").asText();
        String password = json.path("password").asText();
        String username = json.path("username").asText();
        if (email.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_EMAIL_MISSING).send(context);
            return;
        }
        if (password.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PASSWORD_MISSING).send(context);
            return;
        }
        if (username.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_USERNAME_MISSING).send(context);
            return;
        }

        if (!email.matches(REGEX_EMAIL)) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_EMAIL_REGEX).send(context);
            return;
        }
        if (!password.matches(REGEX_PASSWORD)) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PASSWORD_REGEX).send(context);
            return;
        }
        if (!username.matches(REGEX_USERNAME)) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_USERNAME_REGEX).send(context);
            return;
        }

        if (Database.count(Filters.eq("e-mail", email)) > 0) {
            Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_EMAIL_TAKEN).send(context);
            return;
        }
        if (Database.count(Filters.eq("username", username)) > 0) {
            Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_USERNAME_TAKEN).send(context);
            return;
        }

        UUID accountId;
        do {
            accountId = UUID.randomUUID();
        } while (Database.count(Filters.eq("account_id", accountId.toString())) > 0);

        String verificationCode = RandomStringUtils.randomNumeric(6);

        Document document = new Document();
        document.put("e-mail", email);
        document.put("username", username);
        document.put("account_id", accountId.toString());
        document.put("password", new String(KeyManager.createPassword(password.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8));
        document.put("verified", false);
        document.put("verification_code", verificationCode);
        Database.insert(document);

        MailHelper.sendVerifyMail(email, username, accountId, verificationCode);
    }

    @OpenApi(
            path = "/user/login",
            summary = "Login to a account.",
            description = "Login to an account. Needs the e-mail and password field within a json object.",
            method = HttpMethod.POST,
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = LoginModel.class, type = "application/json"),
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = LoginModel.class, type = "application/json"),
            })
    )
    public static void login(Context context) {
        ObjectNode json = Main.stringToJson(context.body());
        if (json.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
            return;
        }
        String email = json.path("e-mail").asText();
        String password = json.path("password").asText();
        if (email.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_EMAIL_MISSING).send(context);
            return;
        }
        if (password.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PASSWORD_MISSING).send(context);
            return;
        }

        Pair<Account, UUID> accountKeyPair = KeyManager.getAccountOnLogin(email, password.getBytes(StandardCharsets.UTF_8));
        if (accountKeyPair == null) {
            Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_CREDENTIALS_INVALID).send(context);
            return;
        }

        ObjectNode returnJson = JsonNodeFactory.instance.objectNode();
        returnJson.set("account", accountKeyPair.getLeft().toJson(JsonNodeFactory.instance.objectNode()));
        returnJson.put("api-key", accountKeyPair.getRight().toString());

        context.json(returnJson);
    }

    @OpenApi(
            path = "/user/logout",
            method = HttpMethod.POST,
            summary = "Logout a account",
            description = "Invalidates and removes the given API-Key from the corresponding account.",
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", description = "The request was processed fine."),
                    @OpenApiResponse(status = "422", description = "X-API-Key is absent or invalid.")
            }
    )
    public static void logout(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            String key = context.header("X-API-Key");
            account.invalidateAPIKey(key);
        });
    }

    @OpenApi(
            path = "/user/username",
            method = HttpMethod.POST,
            summary = "Sets the username of an account.",
            description = "Sets the username of an account by the X-API-Key and the new username within a json object.",
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            },
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = SetUsernameModel.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = SetUsernameModel.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "The request was fine and the change was made."),
                    @OpenApiResponse(status = "422", description = "The request body does not contain the username or it doesn't match the regex.")
            }
    )
    public static void setUsername(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            ObjectNode json = Main.stringToJson(context.body());
            if (json.isEmpty()) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
                return;
            }
            if (!json.has("username")) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_USERNAME_MISSING).send(context);
                return;
            }
            String username = json.get("username").asText();

            if (!username.matches(REGEX_USERNAME)) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_USERNAME_REGEX).send(context);
                return;
            }

            Database.update(Filters.eq("_id", account.getDatabaseId()), BsonDocument.parse("{$set: {'username': '" + username + "'}}"));
        });
    }

    @OpenApi(
            path = "/user/password",
            method = HttpMethod.POST,
            summary = "Sets the password of an account.",
            description = "Sets the password of an account by the X-API-Key and the old password + the new password within a json object.",
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            },
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = SetPasswordModel.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = SetPasswordModel.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "The request was fine and the change was made."),
                    @OpenApiResponse(status = "401", description = "The requesters old password wasn't correct."),
                    @OpenApiResponse(status = "422", description = "The request body does not contain the passwords or it doesn't match the regex.")
            }
    )
    public static void setPassword(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            ObjectNode json = Main.stringToJson(context.body());
            if (json.isEmpty()) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
                return;
            }
            if (!json.has("password")) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PASSWORD_MISSING).send(context);
                return;
            }
            if (!json.has("old_password")) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_OLD_PASSWORD_MISSING).send(context);
                return;
            }

            String oldPasswordText = json.get("old_password").asText();

            if (!account.isPasswordCorrect(oldPasswordText.getBytes(StandardCharsets.UTF_8))) {
                Error.create().codeHTTP(HttpStatus.SC_UNAUTHORIZED).codeInternal(Error.E_PASSWORD_INVALID).send(context);
                return;
            }

            String passwordText = json.get("password").asText();

            if (!passwordText.matches(REGEX_PASSWORD)) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PASSWORD_REGEX).send(context);
                return;
            }

            byte[] password = KeyManager.createPassword(passwordText.getBytes(StandardCharsets.UTF_8));

            Database.update(Filters.eq("_id", account.getDatabaseId()), Updates.set("password", new String(password, StandardCharsets.UTF_8)));
        });
    }

    @OpenApi(
            path = "/user/player_design",
            summary = "Set the player  design.",
            description = "Needs the X-API-Key header. The player design is a json node named 'player_design'",
            method = HttpMethod.POST,
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            },
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = SetPlayerDesign.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = SetPlayerDesign.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "The request was fine and the change was made."),
                    @OpenApiResponse(status = "422", description = "The request body does not contain the player design.")
            }
    )
    public static void setPlayerDesign(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            ObjectNode json = Main.stringToJson(context.body());
            if (json.isEmpty()) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
                return;
            }
            if (!json.has("player_design")) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PLAYER_DESIGN_MISSING).send(context);
                context.status(HttpStatus.SC_UNPROCESSABLE_ENTITY);
                return;
            }
            JsonNode playerDesignNode = json.get("player_design");
            if (!playerDesignNode.isObject()) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PLAYER_DESIGN_INVALID).send(context);
                return;
            }

            Database.update(Filters.eq("_id", account.getDatabaseId()), Updates.set("player_design", new JsonObject(playerDesignNode.toPrettyString())));
        });
    }

    @OpenApi(
            path = "/user/verify",
            method = HttpMethod.POST,
            summary = "Verify a account",
            description = "Use the given verification code to check if it is correct and the account is verified.",
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            },
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = VerifyAccountModel.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = VerifyAccountModel.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "The account verification is fine."),
                    @OpenApiResponse(status = "422", description = "The request body does not contain the verification_code."),
                    @OpenApiResponse(status = "500", description = "The verification code couldn't be loaded from the database.")
            }
    )
    public static void verifyAccount(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            ObjectNode json = Main.stringToJson(context.body());
            if (json.isEmpty()) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
                return;
            }
            if (!json.has("verification_code")) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_VERIFICATION_CODE_MISSING).send(context);
                context.status(HttpStatus.SC_UNPROCESSABLE_ENTITY);
                return;
            }

            String verificationCode = json.path("verification_code").asText();

            String accountVerificationCode = account.getVerificationCode();
            if (accountVerificationCode != null && accountVerificationCode.equals(verificationCode)) {
                Database.update(account.createDatabaseFilter(), Updates.set("verified", true));
                context.status(200);
            } else {
                context.status(500);
            }
        });
    }

    @OpenApi(
            path = "/user/password_reset",
            method = HttpMethod.POST,
            summary = "Reset the password for an account",
            description = "Resets the password and verification code of an account.",
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = ResetPasswordModel.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = ResetPasswordModel.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "The account verification is fine or the account couldn't be found."),
                    @OpenApiResponse(status = "422", description = "The request body does not contain the account e-mail.")
            }
    )
    public static void resetPassword(Context context) {
        ObjectNode json = Main.stringToJson(context.body());
        if (json.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
            return;
        }
        if (!json.has("e-mail")) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_EMAIL_MISSING).send(context);
            return;
        }

        String email = json.path("e-mail").asText();

        Account account = Account.fromDatabase(Filters.eq("e-mail", email));
        if (account == null) {
            // commented away, so a attacker can't exploit the exact error messages, to find out if an email is used!
            //Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_ACCOUNT_NOT_FOUND).send(context);
            context.status(200);
            return;
        }

        String verificationCode = RandomStringUtils.randomNumeric(6);

        Database.update(account.createDatabaseFilter(), Updates.combine(
                Updates.set("verification_code", verificationCode),
                Updates.set("verified", false)
        ));

        MailHelper.sendForgotPasswordMail(account.getEmail(), account.getUsername(), verificationCode);
    }

    @OpenApi(
            path = "/user/password_reset_set",
            method = HttpMethod.POST,
            summary = "Reset the password for an account - Step 2 with verification code and new password",
            description = "Resets the password an verification code of an account. This is step 2 for the reset. The verification code and the new password has to be send inside a json object.",
            requestBody = @OpenApiRequestBody(required = true, content = {
                    @OpenApiContent(from = ResetPasswordVerificationCodeModel.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(required = true, contentType = "application/json", oneOf = {
                    @OpenApiContent(from = ResetPasswordVerificationCodeModel.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "The account verification is fine."),
                    @OpenApiResponse(status = "409", description = "No account found with the requested e-mail or the verification code is invalid."),
                    @OpenApiResponse(status = "422", description = "The request body does not contain the account e-mail, verification code or password.")
            }
    )
    public static void resetPasswordWithVerificationCodeAndPassword(Context context) {
        ObjectNode json = Main.stringToJson(context.body());
        if (json.isEmpty()) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
            return;
        }
        if (!json.has("e-mail")) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_EMAIL_MISSING).send(context);
            return;
        }
        if (!json.has("verification_code")) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_VERIFICATION_CODE_MISSING).send(context);
            return;
        }
        if (!json.has("password")) {
            Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_PASSWORD_MISSING).send(context);
            return;
        }

        String email = json.path("e-mail").asText();
        String verificationCode = json.path("verification_code").asText();
        String passwordString = json.path("password").asText();
        byte[] password = KeyManager.createPassword(passwordString.getBytes(StandardCharsets.UTF_8));

        Account account = Account.fromDatabase(Filters.eq("e-mail", email));
        if (account == null) {
            Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_ACCOUNT_NOT_FOUND).send(context);
            return;
        }

        if (!verificationCode.equals(account.getVerificationCode())) {
            Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_VERIFICATION_CODE_INVALID).send(context);
            return;
        }

        Database.update(account.createDatabaseFilter(),
                Updates.combine(
                        Updates.set("verified", true),
                        Updates.set("password", new String(password, StandardCharsets.UTF_8))
                )
        );
    }

    @OpenApi(
            path = "/user/resend_verification",
            method = HttpMethod.POST,
            summary = "Resend the verification email for an account",
            description = "Request the resend for the account creation e-mail with verification code.",
            headers = {
                    @OpenApiParam(name = "X-API-Key", required = true)
            },
            responses = {
                    @OpenApiResponse(status = "200", description = "The account verification is fine."),
                    @OpenApiResponse(status = "500", description = "The verification code couldn't be loaded from the database.")
            }
    )
    public static void resendVerificationCode(Context context) {
        KeyManager.checkAccountFromHeader(context, account -> {
            String verificationCode = account.getVerificationCode();
            if (verificationCode == null) {
                context.status(500);
                return;
            }

            MailHelper.sendVerifyMail(account.getEmail(), account.getUsername(), account.getAccountId(), verificationCode);
        });
    }

    @OpenApi(
            path = "/user/check_verification",
            method = HttpMethod.POST,
            summary = "Check if the account is verified.",
            description = "To check you either can send the X-API-Key header or a json body, with the 'account_id' field populated.",
            headers = {
                    @OpenApiParam(name = "X-API-Key")
            },
            requestBody = @OpenApiRequestBody(content = {
                    @OpenApiContent(from = CheckAccountVerification.class, type = "application/json")
            }),
            composedRequestBody = @OpenApiComposedRequestBody(contentType = "application/json", oneOf = {
                    @OpenApiContent(from = CheckAccountVerification.class, type = "application/json")
            }),
            responses = {
                    @OpenApiResponse(status = "200", description = "Check the RMB_Error header if 491 is present, otherwise the account is verified."),
                    @OpenApiResponse(status = "422", description = "With RBM_Error header for further explanation."),
                    @OpenApiResponse(status = "409", description = "Account could not be found with the provided 'account_id'.")
            }
    )
    public static void checkVerification(Context context){
        boolean foundAccountByHeader = KeyManager.checkAccountFromHeader(context, account -> {
            if (!account.isVerified()) {
                Error.create().codeHTTP(200).codeInternal(Error.E_NOT_VERIFIED).send(context);
                return;
            }
            context.status(200);
        });

        if(!foundAccountByHeader){
            ObjectNode json = Main.stringToJson(context.body());
            if (json.isEmpty()) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_JSON_MISSING).send(context);
                return;
            }
            if (!json.has("account_id")) {
                Error.create().codeHTTP(HttpStatus.SC_UNPROCESSABLE_ENTITY).codeInternal(Error.E_ACCOUNT_UUID_MISSING).send(context);
                return;
            }

            Account account = Account.fromDatabase(Filters.eq("account_id", json.path("account_id").asText()));
            if (account == null) {
                Error.create().codeHTTP(HttpStatus.SC_CONFLICT).codeInternal(Error.E_ACCOUNT_NOT_FOUND).send(context);
                return;
            }

            if (!account.isVerified()) {
                Error.create().codeHTTP(200).codeInternal(Error.E_NOT_VERIFIED).send(context);
                return;
            }
            context.status(200);
        }
    }

}