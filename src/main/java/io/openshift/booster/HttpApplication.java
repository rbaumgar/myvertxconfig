package io.openshift.booster;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;

import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

/**
 *
 */
public class HttpApplication extends AbstractVerticle {

    private ConfigRetriever conf;
    private String message;

    private static final Logger LOGGER = LogManager.getLogger(HttpApplication.class);
    private JsonObject config;

    @Override
    public void start() {

        //setLogLevel("DEBUG");

        Router router = Router.router(vertx);
        router.get("/api/greeting").handler(this::greeting);
        router.get("/health").handler(rc -> rc.response().end("OK"));
        router.get("/").handler(StaticHandler.create());
           
        ConfigStoreOptions appStore = new ConfigStoreOptions()
            .setType("configmap")
            .setFormat("yaml")
            .setConfig(new JsonObject()
                .put("name", "app-config")
                .put("key", "app-config.yml"));
        
        // Check configmap every 60 seconds, default is 5
        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
            .setScanPeriod(60000)
            .addStore(appStore);
                
        conf = ConfigRetriever.create(vertx, options);
        conf.getConfig(json -> {
            config = json.result();
            setLogLevel(config.getString("level", "INFO")); 
            LOGGER.info("Configuration retrieved: {}", config);
            message = config.getString("message", "Hello, %s");
            Integer port = config.getInteger("http.port", 8080);
            vertx.createHttpServer()
                .requestHandler(router)
                .listen(port);
            LOGGER.info("Server start at: {}", port);
        });                   
        
        // The Configuration Retriever periodically retrieves the configuration, 
        // and if the outcome is different from the current one, your application can be reconfigured.
        // By default, the configuration is reloaded every 5 seconds
        conf.listen(change -> {
            // Previous configuration
            //JsonObject previous = change.getPreviousConfiguration();
            // New configuration
            config = change.getNewConfiguration();
            //config = conf;
            LOGGER.info("New configuration: {}", config.getString("message"));
            setLogLevel(config.getString("level", "INFO"));
          });

    }

    private void setLogLevel(String level) {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        Configuration config = ctx.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(Level.getLevel(level));
        ctx.updateLoggers();
    }

    private void greeting(RoutingContext rc) {
        if (message == null) {
            rc.response().setStatusCode(500)
                .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
                .end(new JsonObject().put("content", "no config map").encode());
            return;
        }
        String name = rc.request().getParam("name");
        if (name == null) {
            name = "World";
        }

        LOGGER.debug("Replying to request, parameter={}", name);
        JsonObject response = new JsonObject()
            .put("content", String.format(message, name));

        rc.response()
            .putHeader(CONTENT_TYPE, "application/json; charset=utf-8")
            .end(response.encodePrettily());
    }

}