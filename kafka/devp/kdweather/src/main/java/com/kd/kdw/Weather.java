package com.kd.kdw;

/* 
    This is a simple POJO that maps to a subset of the Weather data returned by the 
    Open Weather API. We use the Unirest API to map the JSON returned by the API to
    materialise it into this Java object. This can be nested, so that this class 
    references other POJO like Coord which are mapped to corresponding nested fields 
    within the JSON.

    A POJO just contains the data fields, and getter/setter methods for each field.
*/

public class Weather {

    private Long id;
    private String name;
    private Coord coord;
    private Long visibility;

    public Weather() {}

    public Weather(Long id, String name, Coord coord, Long visibility) {
        this.id = id;
        this.name = name;
        this.coord = coord;
        this.visibility = visibility;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Coord getCoord() {
        return coord;
    }

    public void setCoord(Coord coord) {
        this.coord = coord;
    }

    public Long getVisibility() {
        return visibility;
    }

    public void setVisibility(Long visibility) {
        this.visibility = visibility;
    }

    @Override
    public String toString() {
        return "Weather{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", coord=" + coord +
                ", visibility=" + visibility +
                '}';
    }
}