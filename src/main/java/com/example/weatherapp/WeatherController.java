
package com.example.weatherapp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;

@RestController
public class WeatherController {

    private final RestClient client = RestClient.create();

    @GetMapping("/api/weather")
    public Map<String, Object> getWeather(
            @RequestParam(defaultValue = "Mysuru") String city) {

        if (city == null || city.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Please enter a city name.");
        }

        try {
            // Step 1: Find the city's coordinates.
            JsonNode geo = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("geocoding-api.open-meteo.com")
                            .path("/v1/search")
                            .queryParam("name", city.trim())
                            .queryParam("count", 1)
                            .queryParam("language", "en")
                            .queryParam("format", "json")
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            JsonNode results = geo == null ? null : geo.path("results");

            if (results == null || !results.isArray()
                    || results.isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "City not found. Try another city name.");
            }

            JsonNode place = results.get(0);
            double latitude = place.path("latitude").asDouble();
            double longitude = place.path("longitude").asDouble();

            // Step 2: Request current conditions and 7-day forecast.
            JsonNode weather = client.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host("api.open-meteo.com")
                            .path("/v1/forecast")
                            .queryParam("latitude", latitude)
                            .queryParam("longitude", longitude)
                            .queryParam("current",
                                    "temperature_2m,relative_humidity_2m,"
                                    + "apparent_temperature,is_day,"
                                    + "precipitation,weather_code,"
                                    + "wind_speed_10m")
                            .queryParam("daily",
                                    "weather_code,temperature_2m_max,"
                                    + "temperature_2m_min,sunrise,sunset,"
                                    + "uv_index_max")
                            .queryParam("timezone", "auto")
                            .queryParam("forecast_days", 7)
                            .build())
                    .retrieve()
                    .body(JsonNode.class);

            if (weather == null || weather.path("current").isMissingNode()
                    || weather.path("daily").isMissingNode()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Weather provider returned incomplete data.");
            }

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("city", place.path("name").asText(city));
            response.put("country", place.path("country").asText(""));
            response.put("latitude", latitude);
            response.put("longitude", longitude);
            response.put("timezone",
                    weather.path("timezone").asText(""));
            response.put("current", weather.path("current"));
            response.put("current_units", weather.path("current_units"));
            response.put("daily", weather.path("daily"));
            response.put("daily_units", weather.path("daily_units"));

            return response;

        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Could not fetch weather. Check your internet connection "
                    + "and try again.");
        }
    }
}
