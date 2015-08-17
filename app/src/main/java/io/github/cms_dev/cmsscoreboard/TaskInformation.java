package io.github.cms_dev.cmsscoreboard;

import android.support.annotation.NonNull;

import javax.json.JsonObject;


public class TaskInformation implements Comparable<TaskInformation> {
    public String name;
    public String short_name;
    public String contest;
    public int score_precision;
    public double max_score;
    private int order;

    public TaskInformation(JsonObject task_info) {
        short_name = task_info.getString("short_name");
        name = task_info.getString("name");
        contest = task_info.getString("contest");
        score_precision = task_info.getInt("score_precision");
        max_score = task_info.getJsonNumber("max_score").doubleValue();
        order = task_info.getInt("order");
    }

    @Override
    public int compareTo(@NonNull TaskInformation another) {
        return order - another.order;
    }
}
