package io.github.cms_dev.cmsscoreboard;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ScoreboardService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    private final HashMap<Scoreboard, ScoreboardUpdater> scoreboards = new HashMap<>();
    private ScoreboardManager scoreboardManager;
    private final ScoreboardBinder binder = new ScoreboardBinder();

    private void startUpdaters() {
        synchronized (scoreboards) {
            for (Scoreboard scoreboard : scoreboardManager.getAvailableScoreboards()) {
                if (!scoreboards.containsKey(scoreboard)) {
                    ScoreboardUpdater updater = new ScoreboardUpdater(this, scoreboard);
                    scoreboards.put(scoreboard, updater);
                    Log.d("ScoreBoardService","START THREAD "+scoreboard.URL);
                    new Thread(updater).start();
                }
            }
            HashSet<Scoreboard> scoreboardsToDelete = new HashSet<>();
            for (Scoreboard scoreboard: scoreboards.keySet()) {
                if (!scoreboardManager.scoreboardExists(scoreboard)) {
                    scoreboardsToDelete.add(scoreboard);
                }
            }
            for (Scoreboard scoreboard: scoreboardsToDelete) {
                scoreboards.get(scoreboard).terminate();
                scoreboards.remove(scoreboard);
            }
        }
    }

    private void stopUpdaters() {
        synchronized (scoreboards) {
            for (ScoreboardUpdater updater : scoreboards.values()) {
                updater.terminate();
            }
        }
    }

    public class ScoreboardBinder extends Binder {
        public List<ContestantInformation> getScores(Scoreboard scoreboard) throws ScoreboardNotReadyException {
            synchronized (scoreboards) {
                if (!scoreboards.containsKey(scoreboard))
                    throw new ScoreboardNotReadyException("Scoreboard does not exist!", ScoreboardStatus.NON_EXISTENT);
                return scoreboards.get(scoreboard).getScores();
            }
        }
        public Double getMaxScore(Scoreboard scoreboard) throws ScoreboardNotReadyException {
            synchronized (scoreboards) {
                if (!scoreboards.containsKey(scoreboard))
                    throw new ScoreboardNotReadyException("Scoreboard does not exist!", ScoreboardStatus.NON_EXISTENT);
                return scoreboards.get(scoreboard).getMaxScore();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onCreate() {
        scoreboardManager = new ScoreboardManager(this);
        Log.d("ONCREATE","SCOREBOARD START");
        startUpdaters();
        scoreboardManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        stopUpdaters();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        scoreboardManager.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals(getString(R.string.available_scoreboards_key)))
            startUpdaters();
    }
}
