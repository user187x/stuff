package com.xxx.bargirl;

import java.util.Date;
import java.util.UUID;

public class Profile {

    private final Date created = new Date();
    private String id = UUID.randomUUID().toString();
    private String name; // Changed to be non-final
    private String age; // Changed to be non-final
    private final int defaultImageResource;
    private String imageUriString;
    private boolean isFavorite;

    // New fields for stats
    private String location; // Changed to be non-final
    private final boolean status;
    private int bodyCount; // Changed to be non-final
    private int babies; // Changed to be non-final
    private int barFine; // Changed to be non-final

    // New state for the smashed button
    private boolean isSmashed;

    public Profile(String name, String age, int defaultImageResource, String location, boolean status, int bodyCount, int babies, int barFine) {
        this.name = name;
        this.age = age;
        this.defaultImageResource = defaultImageResource;
        this.location = location;
        this.status = status;
        this.bodyCount = bodyCount;
        this.babies = babies;
        this.barFine = barFine;
        this.isFavorite = false;
        this.isSmashed = false; // Default value
    }

    public Profile(String id, String name, String age, int defaultImageResource, String location, boolean status, int bodyCount, int babies, int barFine) {
        this.id = id;
        this.name = name;
        this.age = age;
        this.defaultImageResource = defaultImageResource;
        this.location = location;
        this.status = status;
        this.bodyCount = bodyCount;
        this.babies = babies;
        this.barFine = barFine;
        this.isFavorite = false;
        this.isSmashed = false; // Default value
    }

    // Constructor for creating a new profile from the bottom sheet
    public Profile(String name, String age, int defaultImageResource) {
        this(name, age, defaultImageResource, "Unknown", false, 0, 0, 0);
    }


    // Getters
    public String getId() { return id; }
    public String getName() { return name; }
    public String getAge() { return age; }
    public int getDefaultImageResource() { return defaultImageResource; }
    public String getImageUriString() { return imageUriString; }
    public boolean isFavorite() { return isFavorite; }
    public String getLocation() { return location; }
    public boolean isActive() { return status; }
    public int getBodyCount() { return bodyCount; }
    public int getBabies() { return babies; }
    public int getBarFine() { return barFine; }
    public boolean isSmashed() { return isSmashed; }


    // Setters
    public void setImageUriString(String imageUriString) { this.imageUriString = imageUriString; }
    public void setFavorite(boolean favorite) { isFavorite = favorite; }
    public void setSmashed(boolean smashed) { isSmashed = smashed; }
    public void setBodyCount(int count) { this.bodyCount = count; }
    public void setName(String name) { this.name = name; }
    public void setAge(String age) { this.age = age; }
    public void setLocation(String location) { this.location = location; }
    public void setBabies(int babies) { this.babies = babies; }
    public void setBarFine(int barFine) { this.barFine = barFine; }


    // Business Logic
    public void incrementBodyCount() {
        this.bodyCount++;
    }
    public void decrementBodyCount() {
        if (this.bodyCount > 0) { // Prevent going below zero
            this.bodyCount--;
        }
    }
}