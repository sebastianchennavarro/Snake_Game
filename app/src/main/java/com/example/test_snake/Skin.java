package com.example.test_snake;

public class Skin {
    private final String id;
    private final String name;
    private final int price;
    private final int imageResId;
    private boolean purchased;

    public Skin(String id, String name, int price, int imageResId, boolean purchased) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.imageResId = imageResId;
        this.purchased = purchased;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getPrice() {
        return price;
    }

    public int getImageResId() {
        return imageResId;
    }

    public boolean isPurchased() {
        return purchased;
    }

    public void setPurchased(boolean purchased) {
        this.purchased = purchased;
    }
}
