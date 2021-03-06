package io.github.cms_dev.cmsscoreboard;

import android.support.annotation.NonNull;

import java.util.HashMap;
import java.util.HashSet;

public class Scoreboard implements Comparable<Scoreboard> {
    public String name;
    public String URL;
    public HashSet<TaskInformation> task = new HashSet<>();

    public Scoreboard(String str) {
        String[] parts = str.split("#");
        URL = parts[0];
        if (parts.length > 1) {
            name = parts[1];
        } else {
            name = URL;
        }
    }

    public Scoreboard(String URL, String name) {
        this.name = name;
        this.URL = URL;
    }

    public void addTask( TaskInformation add ){
        task.add(add);
    }

    public String toString() {
        return name;
    }

    public String saveAsString() {
        return URL + "#" + name;
    }

    public int compareTo(@NonNull Scoreboard other) {
        return name.compareTo(other.name);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Scoreboard && URL.equals(((Scoreboard) other).URL);
    }

    @Override
    public int hashCode() {
        return URL.hashCode();
    }
}
