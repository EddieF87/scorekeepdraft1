package com.example.android.scorekeepdraft1.objects;

/**
 * Created by Eddie on 11/9/2017.
 */

public class MainPageSelection {

    public static final String KEY_SELECTION_ID = "selectionID";
    public static final String KEY_SELECTION_TYPE = "selectionType";
    public static final String KEY_SELECTION_NAME = "selectionName";

    public static final int TYPE_LEAGUE = 0;
    public static final int TYPE_TEAM = 1;
    public static final int TYPE_PLAYER = 2;

    private String id;
    private String name;
    private int type;

    public MainPageSelection() {
    }

    public MainPageSelection(String id, String name, int type) {
        this.name = name;
        this.id = id;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public int getType() {
        return type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setType(int type) {
        this.type = type;
    }
}