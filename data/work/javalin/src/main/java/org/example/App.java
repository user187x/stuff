package org.example;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class App {
    public static void main(String[] args) {

        // Boot up the database and seed it before starting the web server
        AuthDatabase.init();

        Javalin app = Javalin.start(config -> {
            config.jetty.port = 8080;
            config.staticFiles.add("/public", Location.CLASSPATH);
            config.spaRoot.addFile("/", "/public/index.html");

            config.routes.post("/api/login", ctx -> {
                String user = ctx.formParam("username");
                String pass = ctx.formParam("password");

                // Route authentication through H2
                if (AuthDatabase.authenticate(user, pass)) {
                    ctx.status(200).result("Authenticated");
                } else {
                    ctx.status(401).result("Unauthorized");
                }
            });
        });
    }
}