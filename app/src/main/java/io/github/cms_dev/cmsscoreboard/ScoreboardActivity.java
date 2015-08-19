package io.github.cms_dev.cmsscoreboard;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
                        (int) (200 - (200 * roundedScore / maxScore)),
                        (int) (200 * roundedScore / maxScore),
                        0));
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

    private void showAddScoreboardDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_add_scoreboard);
        LayoutInflater inflater = getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.add_scoreboard_dialog, null);
        builder.setPositiveButton(R.string.add, null);
        builder.setNeutralButton(R.string.scan_qr, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                try {
                    Intent intent = new Intent("com.google.zxing.client.android.SCAN");
                    intent.putExtra("SCAN_MODE", "QR_CODE_MODE");

                    startActivityForResult(intent, 0);
                } catch (Exception e) {
                    Uri marketUri = Uri.parse("market://details?id=com.google.zxing.client.android");
                    Intent marketIntent = new Intent(Intent.ACTION_VIEW, marketUri);
                    startActivity(marketIntent);
                }
            }
        });
        if (scoreboardManager.getSavedScoreboardsNum() > 0)
            builder.setNegativeButton(R.string.cancel, null);
        else {
            builder.setNegativeButton(R.string.exit, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    finish();
                }
            });
            builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            });
        }
        builder.setView(dialogView);
        final AlertDialog dialog = builder.create();
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                EditText mName = (EditText) dialogView.findViewById(R.id.insert_name);
                EditText mURL = (EditText) dialogView.findViewById(R.id.insert_url);
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
                if (valid && scoreboardManager.scoreboardExists(newScoreboard)) {
                    mURL.setError(getString(R.string.scoreboard_exists));
                    valid = false;
                }
                if (!valid) return;
                scoreboardManager.addAvailableScoreboard(newScoreboard);
                scoreboardManager.setCurrentScoreboard(newScoreboard);
                scoreboardManager.apply();
                dialog.dismiss();
            }
        });
    }

    private void populateScoreboardList() {
        if (scoreboardManager.getSavedScoreboardsNum() == 0) {
            showAddScoreboardDialog();
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
                    showAddScoreboardDialog();
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String[] scanResult = data.getStringExtra("SCAN_RESULT").split("#");
                Log.v("qr", scanResult[0]);
                if (scanResult.length != 2 || !Patterns.WEB_URL.matcher(scanResult[0]).matches() || scanResult[1].length() < 3) {
                    Toast.makeText(this, R.string.invalid_scan, Toast.LENGTH_LONG).show();
                    return;
                }
                Scoreboard newScoreboard = new Scoreboard(scanResult[0], scanResult[1]);
                if (scoreboardManager.scoreboardExists(newScoreboard)) {
                    Toast.makeText(this, R.string.scoreboard_exists, Toast.LENGTH_LONG).show();
                    return;
                }
                scoreboardManager.addAvailableScoreboard(newScoreboard);
                scoreboardManager.setCurrentScoreboard(newScoreboard);
                scoreboardManager.apply();
            }
        }
    }
}
