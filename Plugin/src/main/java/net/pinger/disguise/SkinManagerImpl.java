package net.pinger.disguise;

import com.google.gson.JsonObject;
import net.pinger.disguise.exception.UserNotFoundException;
import net.pinger.disguise.http.HttpRequest;
import net.pinger.disguise.http.HttpResponse;
import net.pinger.disguise.http.request.HttpGetRequest;
import net.pinger.disguise.http.request.HttpPostRequest;
import net.pinger.disguise.response.Response;
import net.pinger.disguise.util.HttpUtil;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class SkinManagerImpl implements SkinManager {

    // Field where all skins from url are saved
    private final Map<String, Skin> skins = new HashMap<>();

    // Reference to the main plugin
    private final DisguisePlugin dp;

    public SkinManagerImpl(DisguisePlugin dp) {
        this.dp = dp;
    }

    @Override
    public void getFromImage(String imageUrl, Consumer<Response<Skin>> response) {
        Skin skin = this.skins.get(imageUrl);

        // Check if the skin is already
        // Cached
        if (skin != null) {
            // Add it to the consumers
            response.accept(new Response<>(skin, Response.ResponseType.SUCCESS));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(this.dp, () -> {
            try {
                // Create a request
                HttpRequest req = new HttpPostRequest(HttpUtil.toMineskin(imageUrl));
                HttpResponse res = req.connect();

                // Read the response
                JsonObject object = DisguiseAPI.GSON.fromJson(res.getResponse(), JsonObject.class);

                // Sync back the data
                // To save in the consumer
                Bukkit.getScheduler().runTask(this.dp, () -> {
                    // Read from the error property
                    // That might be displayed
                    if (object.has("error")) {
                        response.accept(new Response<>(null, Response.ResponseType.FAILURE, new RuntimeException(object.get("error").getAsString())));
                        return;
                    }

                    // Read the data if there is no error
                    JsonObject textured = object.getAsJsonObject("data").getAsJsonObject("texture");

                    // Instantiate the skin and store this url
                    Skin newSkin = new Skin(textured.get("value").getAsString(), textured.get("signature").getAsString());
                    this.skins.put(imageUrl, newSkin);

                    // Update the response consumer
                    response.accept(new Response<>(newSkin, Response.ResponseType.SUCCESS));
                });

            } catch (IOException e) {
                response.accept(new Response<>(null, Response.ResponseType.FAILURE, e));
            }
        });
    }

    @Override
    public Skin getFromMojang(String playerName) {
        return null;
    }

    @Override
    public Skin getFromMojang(UUID playerId) {
        try {
            // Create a request
            HttpRequest request = new HttpGetRequest(HttpUtil.toMojangUrl(playerId));
            HttpResponse response = request.connect();

            // Read as json
            JsonObject object = DisguiseAPI.GSON.fromJson(response.getResponse(), JsonObject.class);

            // Check if the response contains and error
            if (object.has("errorMessage")) {
                throw new UserNotFoundException("Couldn't find this user.");
            }

            // Get the properties object
            JsonObject properties = object.getAsJsonArray("properties")
                    .get(0)
                    .getAsJsonObject();

            return new Skin(properties.get("value").getAsString(), properties.get("signature").getAsString());
        } catch (IOException e) {
            // If an exception is caught
            // We just want to return null
            return null;
        }
    }


}