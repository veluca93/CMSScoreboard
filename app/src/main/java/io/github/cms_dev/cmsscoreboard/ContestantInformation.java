package io.github.cms_dev.cmsscoreboard;

import android.support.annotation.NonNull;

import javax.json.JsonObject;

import java.util.HashMap;

public class ContestantInformation implements Comparable<ContestantInformation> {
    public HashMap<String, Double> scores;
    public String firstName;
    public String lastName;
    public String username;
    public String team;

    public ContestantInformation(String username, JsonObject user_info) {
        this.username = username;
        scores = new HashMap<>();
        firstName = user_info.getString("f_name");
        lastName = user_info.getString("l_name");
        if (!user_info.isNull("team")) {
            team = user_info.getString("team");
        }
    }

    public synchronized Double getScore(String task) {
        if (scores.containsKey(task))
            return scores.get(task);
        else
            return (double) 0;
    }

    public synchronized void setScore(String task, double score) {
        scores.put(task, score);
    }

    public synchronized Double getTotalScore() {
        double total = 0;
        for (Double v: scores.values()) {
            total += v;
        }
        return total;
    }

    public synchronized String getFullName() {
        return firstName + " " + lastName;
    }

    @Override
    public synchronized int compareTo(@NonNull ContestantInformation another) {
        int res = another.getTotalScore().compareTo(getTotalScore());
        return res == 0 ? lastName.compareTo(another.lastName) : res;
    }
}
