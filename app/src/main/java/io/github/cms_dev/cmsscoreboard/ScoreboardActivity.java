package io.github.cms_dev.cmsscoreboard;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreboardActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, ServiceConnection {
    private ActionBarDrawerToggle mDrawerToggle;
    private ListView mScoreboardList;
    private ListView mContestantsList;
    private ProgressBar mContestantsLoading;
    private TextView mErrorText;
    private ScoreboardManager scoreboardManager;
    private ScoreboardService.ScoreboardBinder binder = null;
    private List<ContestantInformation> scoreboardData = new ArrayList<>();
    private Double maxScore = null;
    private ContestantAdapter contestantAdapter;
    private List<Scoreboard> scoreboards = new ArrayList<>();
    private ScoreboardAdapter scoreboardAdapter;

    private class ContestantAdapter extends ArrayAdapter<ContestantInformation> {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.scoreboard_item, parent, false);
            }
            TextView name = (TextView) convertView.findViewById(R.id.contestant_name);
            TextView score = (TextView) convertView.findViewById(R.id.contestant_score);
            name.setText(scoreboardData.get(position).getFullName());
            long roundedScore = Math.round(scoreboardData.get(position).getTotalScore());
            score.setText(Long.toString(roundedScore));
            if (maxScore != null) {
                score.setTextColor(Color.argb(
                        255,
                        (int) (255 - (255 * roundedScore / maxScore)),
                        (int) (255 * roundedScore / maxScore),
                        128));
            }
            return convertView;
        }
        public ContestantAdapter(Context context) {
            super(context, R.layout.scoreboard_item, scoreboardData);
        }
    }

    private class ScoreboardAdapter extends ArrayAdapter<Scoreboard> {
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            if (scoreboards.get(position).equals(scoreboardManager.getCurrentScoreboard()))
                view.setTypeface(view.getTypeface(), Typeface.BOLD);
            else
                view.setTypeface(Typeface.create(view.getTypeface(), Typeface.NORMAL));
            return view;
        }

        public ScoreboardAdapter(Context context) {
            super(context, R.layout.scoreboard_chooser, scoreboards);
        }
    }

    private class ScoreboardUpdateTask extends AsyncTask<Scoreboard, Void, List<ContestantInformation>> {
        private ScoreboardStatus scoreboardStatus = null;
        private Double maxScore = null;
        @Override
        protected List<ContestantInformation> doInBackground(Scoreboard... params) {
            if (binder == null)
                return null;
            try {
                maxScore = binder.getMaxScore(params[0]);
                return binder.getScores(params[0]);
            } catch (ScoreboardNotReadyException e) {
                scoreboardStatus = e.status;
                return null;
            }
        }
        @Override
        public void onPostExecute(List<ContestantInformation> result) {
            if (result == null && scoreboardStatus != ScoreboardStatus.CONNECTED) {
                if (scoreboardStatus == ScoreboardStatus.LOADING) {
                    mContestantsLoading.setVisibility(View.VISIBLE);
                    mErrorText.setVisibility(View.GONE);
                    mContestantsList.setVisibility(View.GONE);
                    return;
                }
                int messageId = R.string.scoreboard_message_non_existent;
                switch (scoreboardStatus) {
                    case NO_NETWORK:
                        messageId = R.string.scoreboard_message_no_network;
                        break;
                    case INVALID_URL:
                        messageId = R.string.scoreboard_message_invalid_url;
                        break;
                    case INVALID_DATA:
                        messageId = R.string.scoreboard_message_invalid_data;
                        break;
                    case NON_EXISTENT:
                        messageId = R.string.scoreboard_message_non_existent;
                        break;
                }
                mContestantsList.setVisibility(View.GONE);
                mContestantsLoading.setVisibility(View.GONE);
                mErrorText.setVisibility(View.VISIBLE);
                mErrorText.setText(getString(messageId));
            }
            if (result != null) {
                mContestantsList.setVisibility(View.VISIBLE);
                mContestantsLoading.setVisibility(View.GONE);
                mErrorText.setVisibility(View.GONE);
                scoreboardData.clear();
                scoreboardData.addAll(result);
                ScoreboardActivity.this.maxScore = maxScore;
                contestantAdapter.notifyDataSetChanged();
            }
        }
    }

    private class ScoreboardUpdateLoop implements Runnable {
        @Override
        public void run() {
            new ScoreboardUpdateTask().execute(scoreboardManager.getCurrentScoreboard());
            if (binder != null) {
                mContestantsList.postDelayed(new ScoreboardUpdateLoop(), 1000);
            }
        }
    }

    private void populateScoreboardList() {
        if (scoreboardManager.getSavedScoreboardsNum() == 0) {
            Intent intent = new Intent(this, AddScoreboardActivity.class);
            startActivity(intent);
        } else {
            scoreboards.clear();
            scoreboards.addAll(scoreboardManager.getAvailableScoreboards());
            Collections.sort(scoreboards);
            scoreboardAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        binder = (ScoreboardService.ScoreboardBinder) service;
        mContestantsList.postDelayed(new ScoreboardUpdateLoop(), 1000);
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        binder = null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scoreboard);

        contestantAdapter = new ContestantAdapter(this);
        scoreboardAdapter = new ScoreboardAdapter(this);

        Toolbar toolbar = (Toolbar) findViewById(R.id.custom_action_bar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setLogo(R.mipmap.cms);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_drawer);
        }

        DrawerLayout mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        ListView mExtraList = (ListView) findViewById(R.id.drawer_extra);
        mScoreboardList = (ListView) findViewById(R.id.scoreboards);
        mContestantsList = (ListView) findViewById(R.id.contestants);
        mContestantsLoading = (ProgressBar) findViewById(R.id.scoreboard_loading_spinner);
        mErrorText = (TextView) findViewById(R.id.scoreboard_error);

        mScoreboardList.setAdapter(scoreboardAdapter);
        mContestantsList.setAdapter(contestantAdapter);

        scoreboardManager = new ScoreboardManager(this);
        populateScoreboardList();
        scoreboardManager.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        mExtraList.setAdapter(new ArrayAdapter<>(this,
                R.layout.scoreboard_chooser, getResources().getStringArray(R.array.drawer_extra)));
        mExtraList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    Intent intent = new Intent(ScoreboardActivity.this, AddScoreboardActivity.class);
                    startActivity(intent);
                }
            }
        });

        mScoreboardList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                scoreboardManager.setCurrentScoreboard((Scoreboard) mScoreboardList.getItemAtPosition(position));
                scoreboardManager.apply();
            }
        });

        mScoreboardList.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(final AdapterView<?> parent, View view, final int position, long id) {
                final Scoreboard clicked_item = ((Scoreboard) parent.getItemAtPosition(position));
                AlertDialog.Builder builder = new AlertDialog.Builder(ScoreboardActivity.this);
                builder.setTitle(R.string.scoreboard_source_options_title)
                        .setItems(R.array.scoreboard_source_options, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                ScoreboardManager prefs = new ScoreboardManager(ScoreboardActivity.this);
                                if (which == 0) {
                                    prefs.delAvailableScoreboard(clicked_item);
                                } else if (which == 1) {
                                    prefs.setCurrentScoreboard(clicked_item);
                                }
                                prefs.apply();
                            }
                        });
                builder.create().show();
                return true;
            }
        });

        mDrawerLayout.setStatusBarBackgroundColor(getResources().getColor(R.color.cms_color));

        mDrawerToggle = new ActionBarDrawerToggle(
                this,
                mDrawerLayout,
                toolbar,
                R.string.drawer_open,
                R.string.drawer_close);
        mDrawerLayout.setDrawerListener(mDrawerToggle);

        Intent intent = new Intent(this, ScoreboardService.class);
        bindService(intent, this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindService(this);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        mDrawerToggle.onConfigurationChanged(newConfig);
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        return mDrawerToggle.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        populateScoreboardList();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        scoreboardManager.onSharedPreferenceChanged(sharedPreferences, key);
        if (key.equals(getString(R.string.current_scoreboard_key))) {
            scoreboardAdapter.notifyDataSetChanged();
            mErrorText.setVisibility(View.GONE);
            mContestantsList.setVisibility(View.GONE);
            mContestantsLoading.setVisibility(View.VISIBLE);
            new ScoreboardUpdateTask().execute(scoreboardManager.getCurrentScoreboard());
        } else if (key.equals(getString(R.string.available_scoreboards_key))) {
            populateScoreboardList();
        }
    }
}
