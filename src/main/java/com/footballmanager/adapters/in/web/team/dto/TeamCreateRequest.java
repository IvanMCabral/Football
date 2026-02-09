package com.footballmanager.adapters.in.web.team.dto;

import java.math.BigDecimal;

/**
 * DTO para crear un equipo desde el frontend
 */
public class TeamCreateRequest {
    private String name;
    private String country;
    private String city;
    private String stadiumName;
    private BigDecimal budget;

    public TeamCreateRequest() {
    }

    public TeamCreateRequest(String name, String country, String city, String stadiumName, BigDecimal budget) {
        this.name = name;
        this.country = country;
        this.city = city;
        this.stadiumName = stadiumName;
        this.budget = budget;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getStadiumName() {
        return stadiumName;
    }

    public void setStadiumName(String stadiumName) {
        this.stadiumName = stadiumName;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public void setBudget(BigDecimal budget) {
        this.budget = budget;
    }
}
