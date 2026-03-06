package com.yourapp.rentbot.domain;

public class Listing {

    private String title;
    private String price;
    private String link;

    public Listing(String title, String price, String link) {
        this.title = title;
        this.price = price;
        this.link = link;
    }

    public String getTitle() { return title; }
    public String getPrice() { return price; }
    public String getLink() { return link; }
}