package de.canitzp.rockbottommanagement;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.javalin.Javalin;
import io.javalin.plugin.openapi.OpenApiOptions;
import io.javalin.plugin.openapi.OpenApiPlugin;
import io.javalin.plugin.openapi.ui.ReDocOptions;
import io.javalin.plugin.openapi.ui.SwaggerOptions;
import io.swagger.v3.oas.models.info.Info;

public class Main {

    private static final Info OPENAPI_INFO = new Info().version("1.0.0").title("RockBottom Management");
    private static final OpenApiOptions OPENAPI_OPTIONS = new OpenApiOptions(OPENAPI_INFO)
            .activateAnnotationScanningFor("de.canitzp.rockbottommanagement")
            .path("/swagger-docs")
            .swagger(new SwaggerOptions("/swagger"))
            .reDoc(new ReDocOptions("/redoc"));

    public static void main(String[] args) {
        Database.connect();

        Javalin javalin = Javalin.create(config -> config.registerPlugin(new OpenApiPlugin(OPENAPI_OPTIONS)));

        javalin.post("/user/create", Endpoints::create);
        javalin.post("/user/login", Endpoints::login);
        javalin.post("/user/logout", Endpoints::logout);
        javalin.post("/user/username", Endpoints::setUsername);
        javalin.post("/user/password", Endpoints::setPassword);
        javalin.post("/user/player_design", Endpoints::setPlayerDesign);
        javalin.post("/user/verify", Endpoints::verifyAccount);
        javalin.post("/user/password_reset", Endpoints::resetPassword);
        javalin.post("/user/password_reset_set", Endpoints::resetPasswordWithVerificationCodeAndPassword);
        javalin.post("/user/resend_verification", Endpoints::resendVerificationCode);
        javalin.post("/user/check_verification", Endpoints::checkVerification);

        javalin.get("/user", Endpoints::getUser);

        javalin.start(8080);
    }

    public static ObjectNode stringToJson(String body) {
        String jsonString = "{}";
        if (body != null) {
            jsonString = body;
        }
        JsonNode jsonNode;
        try {
            jsonNode = new ObjectMapper().readTree(jsonString);
            if (jsonNode.isObject()) {
                return ((ObjectNode) jsonNode);
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return JsonNodeFactory.instance.objectNode();
    }

}
