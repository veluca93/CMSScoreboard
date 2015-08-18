package io.github.cms_dev.cmsscoreboard;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public class ScoreboardManager implements SharedPreferences.OnSharedPreferenceChangeListener {
    private SharedPreferences prefs;
    private Context ctx;
    private HashSet<Scoreboard> scoreboards = new HashSet<>();
    private Scoreboard currentScoreboard;

    public ScoreboardManager(Context c) {
        ctx = c;
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx.getApplicationContext());
        prefs.registerOnSharedPreferenceChangeListener(this);
        onSharedPreferenceChanged(prefs, ctx.getString(R.string.available_scoreboards_key));
        onSharedPreferenceChanged(prefs, ctx.getString(R.string.current_scoreboard_key));
    }

    public void apply() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ctx.getString(R.string.current_scoreboard_key), currentScoreboard.saveAsString());
        HashSet<String> newSet = new HashSet<>();
        for (Scoreboard s: scoreboards) {
            newSet.add(s.saveAsString());
        }
        editor.putStringSet(ctx.getString(R.string.available_scoreboards_key), newSet);
        editor.apply();

    }

    public SharedPreferences getSharedPreferences() {
        return prefs;
    }

    public Scoreboard getCurrentScoreboard() {
        return currentScoreboard;
    }

    public void setCurrentScoreboard(Scoreboard scoreboard) {
        currentScoreboard = scoreboard;
    }

    public int getSavedScoreboardsNum() {
        return prefs.getStringSet(ctx.getString(R.string.available_scoreboards_key), new HashSet<String>()).size();
    }

    public Set<Scoreboard> getAvailableScoreboards() {
        return scoreboards;
    }

    public void addAvailableScoreboard(Scoreboard scoreboard) {
        scoreboards.add(scoreboard);
    }

    public void delAvailableScoreboard(Scoreboard scoreboard) {
        scoreboards.remove(scoreboard);
        if (scoreboard.equals(currentScoreboard) && !scoreboards.isEmpty()) {
            currentScoreboard = scoreboards.iterator().next();
        }
    }

    public boolean scoreboardExists(Scoreboard scoreboard) {
        return scoreboards.contains(scoreboard);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(ctx.getString(R.string.available_scoreboards_key))) {
            scoreboards.clear();
            for (String s: prefs.getStringSet(ctx.getString(R.string.available_scoreboards_key), new HashSet<String>())) {
                scoreboards.add(new Scoreboard(s));
            }
        } else if (key.equals(ctx.getString(R.string.current_scoreboard_key))) {
            currentScoreboard = new Scoreboard(prefs.getString(ctx.getString(R.string.current_scoreboard_key), ""));
        }
    }
}
