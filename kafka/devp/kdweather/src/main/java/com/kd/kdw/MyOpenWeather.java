package com.kd.kdw;

// Use Kong Unirest library for invoking HTTP Rest APIs
// http://kong.github.io/unirest-java/
import kong.unirest.HttpResponse;
import kong.unirest.JsonNode;
import kong.unirest.json.JSONObject;
import kong.unirest.Unirest;

import java.util.List;
import java.util.stream.Collectors;

// TEMPORARY 
import java.util.Map;
import java.util.HashMap;

/* 
    This class handles all the communication with the OpenWeather REST API, which is our
    source data system.

    This is normally invoked from the Task class, but we also provide a main() method for
    debugging purposes so that we can rapidly test the Open Weather functionality in a standalone
    manner without the overhead of starting Kafka Connect.
*/

public class MyOpenWeather {

    private static final String BASE_URL = "https://api.openweathermap.org/data/2.5/weather";

    private MySourceConnectorConfig config;

    // TEMPORARY
    public static void main(String[] args) {
        System.out.println("Hello World!"); // Display the string.
        Map<String, String> map = new HashMap<String, String>();
        map.put("topic", "badt");
        map.put("sleep.seconds", "5");
        map.put("cities", "Germany, Sweden");
        
        MySourceConnectorConfig owc = new MySourceConnectorConfig(map);
        MyOpenWeather ow = new MyOpenWeather(owc);
        ow.experimentWeather();
    }

    /* 
        Save the configuration properties
    */
    public MyOpenWeather(MySourceConnectorConfig weatherConfig) {
        config = weatherConfig;
    }

    /* 
        Return a list of weather data from the Open Weather API for the given 
        cities/countries.
    */
    public List<Weather> getCurrentWeather() {
        // Get the API Key and list of cities from the configuration
        String appKey = config.getApiKey();
        List<String> cities = config.getCities();

        // Call the Open Weather API for one city at a time and then collect
        // all the results into a list. Open Weather returns the data as JSON.
        // Unirest can return this to us as JSON using .asJson() or materialised
        // as a Java object using .asObject(objClass). This object must be a
        // POJO that contains all the fields in the JSON.
        // .asObject returns a HttpResponse<Weather> and
        // .getBody uses that to return a Weather object.
        // .asJson returns a HttpResponse<JsonNode>
        List<Weather> resps = cities.stream().map(city -> Unirest.get(BASE_URL)
                            .queryString("q", city)
                            .queryString("APPID", appKey)
                            .asObject(Weather.class)
                            .getBody())
                       .collect(Collectors.toList());
        resps.forEach(System.out::println);

        return resps;
    }

    /* 
        Experiment with different calls to the Open Weather API
    */
    public void experimentWeather() {
        // Get the API Key and list of cities from the configuration
        String appKey = config.getApiKey();
        List<String> cities = config.getCities();

        HttpResponse<JsonNode> jsonResp = Unirest.get(BASE_URL)
            .queryString("q", cities.get(0))
            .queryString("APPID", appKey)
            .asJson();
        System.out.println(jsonResp.getBody());

        JSONObject response = Unirest.get(BASE_URL)
                    .queryString("q", cities.get(0))
                    .queryString("APPID", appKey)
                    .asJson()
                    .getBody()
                    .getObject()
                    .getJSONObject("coord");
        System.out.println(response);

        HttpResponse<Weather> objResp = Unirest.get(BASE_URL)
                    .queryString("q", cities.get(1))
                    .queryString("APPID", appKey)
                    .asObject(Weather.class);
        Weather owind = objResp.getBody();
        System.out.println(owind.getId() + "lat" + owind.getCoord().getLat());
    }
}