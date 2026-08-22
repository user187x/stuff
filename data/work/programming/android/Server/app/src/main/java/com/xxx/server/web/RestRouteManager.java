package com.xxx.server.web;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class RestRouteManager {

  private static final String PREFS_KEY = "custom_routes";
  private static final String TAG = "RestRouteManager";

  private final Context context;
  private final SharedPreferences preferences;
  private final Gson gson;
  private final List<CustomRoute> customRoutes;

  public RestRouteManager(Context context) {
    this.context = context;
    this.preferences = context.getSharedPreferences("HttpServerPrefs", Context.MODE_PRIVATE);
    this.gson = new Gson();
    this.customRoutes = loadRoutes();
  }

  public void addRoute(CustomRoute route) {
    customRoutes.add(route);
    saveRoutes();
  }

  public void removeRoute(int index) {
    if (index >= 0 && index < customRoutes.size()) {
      customRoutes.remove(index);
      saveRoutes();
    }
  }

  public List<CustomRoute> getRoutes() {
    return new ArrayList<>(customRoutes);
  }

  public void applyRoutesToRouter(Router router) {
    for (CustomRoute route : customRoutes) {
      try {
        HttpMethod httpMethod = HttpMethod.valueOf(route.method.toUpperCase());

        Handler<RoutingContext> handler = ctx -> {
          // Set content type based on returns field
          String contentType = getContentType(route.returns);
          ctx.response()
              .setStatusCode(route.statusCode)
              .putHeader("Content-Type", contentType)
              .end(route.responseBody);
        };

        router.route(httpMethod, route.path).handler(handler);
      } catch (IllegalArgumentException e) {
        Log.w(TAG, "Invalid HTTP method in custom route: " + route.method);
      } catch (Exception e) {
        Log.e(TAG, "Error applying custom route: " + route.path, e);
      }
    }
  }

  private String getContentType(String returns) {
    switch (returns.toLowerCase()) {
      case "json":
        return "application/json";
      case "xml":
        return "application/xml";
      case "html":
        return "text/html";
      case "text":
        return "text/plain";
      default:
        return "application/json";
    }
  }

  private void saveRoutes() {
    String json = gson.toJson(customRoutes);
    preferences.edit().putString(PREFS_KEY, json).apply();
  }

  private List<CustomRoute> loadRoutes() {
    String json = preferences.getString(PREFS_KEY, "[]");
    Type listType = new TypeToken<List<CustomRoute>>() {
    }.getType();
    List<CustomRoute> routes = gson.fromJson(json, listType);
    return routes != null ? routes : new ArrayList<>();
  }

  public static class CustomRoute {

    public String path;
    public String method;
    public String accepts;
    public String returns;
    public String responseBody;
    public int statusCode;

    public CustomRoute() {
    }

    public CustomRoute(String path, String method, String accepts, String returns) {
      this.path = path;
      this.method = method;
      this.accepts = accepts;
      this.returns = returns;
      this.responseBody = "{}";
      this.statusCode = 200;
    }
  }
}