package xxx.com.sandbox;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;

public class Sandbox {

//  Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(40));
//
//  HttpServer server = vertx.createHttpServer();
//  Router router = Router.router(vertx);
//
//  Route route = router.route("/some/path/");
//  route.handler(ctx -> {
//
//    HttpServerResponse response = ctx.response();
//    // enable chunked responses because we will be adding data as
//    // we execute over other handlers. This is only required once and
//    // only if several handlers do output.
//    response.setChunked(true);
//
//    response.write("route1\n");
//
//    // Call the next matching route after a 5 second delay
//    ctx.vertx().setTimer(5000, tid -> ctx.next());
//  });
}
