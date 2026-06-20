package org.example;

import java.util.LinkedHashMap;
import java.util.Map;

public class DetailedSector {

    private String link;
    private final Map<String, String> data = new LinkedHashMap<>();

    public String getLink() { return link; }
    public void setLink(String link) { this.link = link; }

    public void put(String column, String value) { data.put(column, value); }
    public String get(String column) { return data.getOrDefault(column, ""); }
    public Map<String, String> getData() { return data; }

    @Override
    public String toString() {
        return "DetailedSector{link='" + link + "', data=" + data + "}";
    }
}