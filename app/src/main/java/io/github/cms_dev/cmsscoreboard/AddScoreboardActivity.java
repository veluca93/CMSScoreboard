package io.github.cms_dev.cmsscoreboard;

import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Patterns;
import android.view.View;
import android.widget.EditText;

public class AddScoreboardActivity extends AppCompatActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_scoreboard);
        Toolbar toolbar = (Toolbar) findViewById(R.id.custom_action_bar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null)
            actionBar.setLogo(R.mipmap.cms);
    }

    public void doAdd(View view) {
        EditText mName = (EditText)findViewById(R.id.insert_name);
        EditText mURL = (EditText)findViewById(R.id.insert_url);
        String name = mName.getText().toString();
        String URL = mURL.getText().toString();
        boolean valid = true;
        if (!Patterns.WEB_URL.matcher(URL).matches()) {
            mURL.setError(getString(R.string.invalid_url));
            valid = false;
        }
        if (name.length() < 3) {
            mName.setError(getString(R.string.name_short));
            valid = false;
        }
        Scoreboard newScoreboard = new Scoreboard(URL, name);
        ScoreboardManager scoreboardManager = new ScoreboardManager(this);
        if (valid && scoreboardManager.scoreboardExists(newScoreboard)) {
            mURL.setError(getString(R.string.scoreboard_exists));
            valid = false;
        }
        if (!valid) return;
        scoreboardManager.addAvailableScoreboard(newScoreboard);
        scoreboardManager.setCurrentScoreboard(newScoreboard);
        scoreboardManager.apply();
        finish();
    }
}
