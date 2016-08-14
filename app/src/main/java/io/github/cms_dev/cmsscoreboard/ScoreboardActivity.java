package io.github.cms_dev.cmsscoreboard;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.media.Image;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Layout;
import android.util.Log;
import android.util.Patterns;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

public class ScoreboardActivity extends AppCompatActivity implements SharedPreferences.OnSharedPreferenceChangeListener, ServiceConnection {
    private ActionBarDrawerToggle mDrawerToggle;
    private DrawerLayout mDrawerLayout;
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
    public HashMap<String,Boolean> mContestantOpen = new HashMap<>();
    public HashSet<String> mContestantFavouriteList = new HashSet<String>();
    public static Context mContext = null ;
    public static SharedPreferences mSharedPreferences = null ;
    public static ImageCache imageCache = null ;

    private class ContestantAdapter extends ArrayAdapter<ContestantInformation> {


        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.scoreboard_item, parent, false);
            }
            final String contestantName = scoreboardData.get(position).getFullName() ;
            long roundedScore = Math.round(scoreboardData.get(position).getTotalScore());
            TextView rank = (TextView) convertView.findViewById(R.id.contestant_rank);
            TextView name = (TextView) convertView.findViewById(R.id.contestant_name);
            TextView score = (TextView) convertView.findViewById(R.id.contestant_score);
            LinearLayout taskN = (LinearLayout) convertView.findViewById(R.id.contestant_task_name);
            LinearLayout taskS = (LinearLayout) convertView.findViewById(R.id.contestant_task_score);
            LinearLayout info = (LinearLayout) convertView.findViewById(R.id.contestant_info);
            ImageView star = (ImageView) convertView.findViewById(R.id.contestant_star);
            ImageView flag = (ImageView) convertView.findViewById(R.id.contestant_flag);
            ImageView face = (ImageView) convertView.findViewById(R.id.contestant_photo);

            rank.setText( (1+position)+" " );
            name.setText(contestantName);
            score.setText(Long.toString(roundedScore));
            if (maxScore != null) {
                score.setTextColor(Color.argb(
                        255,
                        (int) (200 - (200 * roundedScore / maxScore)),
                        (int) (200 * roundedScore / maxScore),
                        0));
            }

            taskN.removeAllViews();
            taskS.removeAllViews();
            TextView nameTask = new TextView(this.getContext());
            TextView scoreTask = new TextView(this.getContext());
            nameTask.setText("Task");
            scoreTask.setText("Score");
            nameTask.setBackgroundColor(Color.parseColor("#cccccc"));
            scoreTask.setBackgroundColor(Color.parseColor("#cccccc"));
            taskN.addView(nameTask);
            taskS.addView(scoreTask);

            String baseUrl = scoreboardData.get(position).scoreboard.URL ;
            while( !baseUrl.endsWith("/") ) baseUrl = baseUrl.substring(0,baseUrl.length()-1);
            String commonExt[] = {"png","jpg","jpeg"};
            String url = scoreboardData.get(position).scoreboard.URL ;
            while( !url.endsWith("/") ) url = url.substring(0,url.length()-1);
            //TODO: usare last index of

            // Tentativi per recuperare la foto
            String urlAttempts[] = new String[2] ;
            urlAttempts[0] = "faces/" + scoreboardData.get(position).username ;
            urlAttempts[1] = "faces/" + scoreboardData.get(position).id ;
            BitmapDrawable facePic = null ;

            for( int i = 0 ; i < urlAttempts.length && facePic == null ; i++ )
            {
                for( int j = 0 ; j < commonExt.length && facePic == null ; j++ )
                {
                    url = baseUrl + urlAttempts[i] + "." + commonExt[j] ;
                    imageCache.addImage(url);
                    BitmapDrawable faccia = imageCache.getImage(url);
                    if (faccia != null) {
                        facePic = faccia ;
                    }
                }
            }
            if( facePic != null ) face.setImageDrawable(facePic);
            else face.setImageResource(R.drawable.no_photo);

            // Tentativi per la bandiera
            url = scoreboardData.get(position).scoreboard.URL ;
            while( !url.endsWith("/") ) url = url.substring(0,url.length()-1);
            url = url + "flags/" + scoreboardData.get(position).team + ".png" ;
            if( imageCache != null ) {
                imageCache.addImage(url);
             //   Log.d("Flags",url)
                BitmapDrawable bandiera = imageCache.getImage(url);
                if (bandiera != null) {
                    flag.setImageDrawable(bandiera);
                }
                else flag.setImageResource(R.drawable.noflag);
            }
            else flag.setImageResource(R.drawable.noflag);


            // Aggiungo le righe dei task / punteggi
            if( scoreboardData.get(position).scoreboard != null  ) {
                Vector<String> shortTaskName = new Vector<String>();

                for (TaskInformation taskKey : scoreboardData.get(position).scoreboard.task)
                    shortTaskName.add(taskKey.short_name);
                Collections.sort(shortTaskName);

                for( int i = 0 ; i < shortTaskName.size(); i++ )
                {
                    nameTask = new TextView(this.getContext());
                    scoreTask = new TextView(this.getContext());
                    double scoreD = scoreboardData.get(position).getScore(shortTaskName.get(i));
                    nameTask.setText(shortTaskName.get(i));
                    scoreTask.setText((Integer.valueOf((int) Math.round(scoreD))).toString());
                    taskN.addView(nameTask);
                    taskS.addView(scoreTask);
                }

            }
            mSharedPreferences = getApplicationContext().getSharedPreferences("FAVORITI", mContext.MODE_PRIVATE);

            if( mSharedPreferences.contains(contestantName) && mSharedPreferences.getString(contestantName, null).equals("OK") ){
                star.setImageResource(R.drawable.ic_star_black_48dp);
            } else {
                star.setImageResource(R.drawable.ic_star_border_black_48dp);
            }

            if( mContestantOpen.get(contestantName) == null || !mContestantOpen.get(contestantName) ){
                mContestantOpen.put(contestantName,false);
                info.setVisibility(View.GONE);
            }
            else {
                mContestantOpen.put(contestantName,true);
                info.setVisibility(View.VISIBLE);
            }

            info.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                }
            });
            star.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    ImageView viewImg = (ImageView) view ;
                    TextView name = (TextView) ( (LinearLayout)view.getParent()).findViewById(R.id.contestant_name);

                    String res = "OK" ;
                    if( !mSharedPreferences.contains(name.getText().toString()) || !mSharedPreferences.getString(contestantName, null).equals("OK") ){
                        mContestantFavouriteList.add(name.getText().toString());
                       viewImg.setImageResource(R.drawable.ic_star_black_48dp);
                    }
                    else {
                        res = "NO" ;
                        mContestantFavouriteList.remove( name.getText().toString() );
                        viewImg.setImageResource(R.drawable.ic_star_border_black_48dp);
                    }
                    mSharedPreferences.edit().putString(name.getText().toString(), res).commit();

                }
            });
            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    LinearLayout info = (LinearLayout) view.findViewById(R.id.contestant_info);
                    TextView name = (TextView) view.findViewById(R.id.contestant_name);
                    String contestantName = name.getText().toString();
                    if( mContestantOpen.get(contestantName) == null || !mContestantOpen.get(contestantName) ){
                        mContestantOpen.put(contestantName,true);
                        info.setVisibility(View.VISIBLE);
                    }
                    else {
                        mContestantOpen.put(contestantName,false);
                        info.setVisibility(View.GONE);
                    }
                }
            });

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
                if (scoreboardManager.getSavedScoreboardsNum() == 0) {
                    messageId = R.string.scoreboard_message_no_scoreboards;
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
        builder.setNegativeButton(R.string.cancel, null);
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
        scoreboards.clear();
        scoreboards.addAll(scoreboardManager.getAvailableScoreboards());
        Collections.sort(scoreboards);
        scoreboardAdapter.notifyDataSetChanged();
        if (scoreboardManager.getSavedScoreboardsNum() == 0) {
            mDrawerLayout.openDrawer(Gravity.LEFT);
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
        imageCache = new ImageCache();

        mContext = this.getApplicationContext();
        Toolbar toolbar = (Toolbar) findViewById(R.id.custom_action_bar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setLogo(R.mipmap.cms);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_drawer);
        }

        ListView mExtraList = (ListView) findViewById(R.id.drawer_extra);
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
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

        //mContestantFavouriteList.
        Map<String,?> keys = this.getPreferences(Context.MODE_PRIVATE).getAll();
        for(Map.Entry<String,?> entry : keys.entrySet()){
            Log.d("map values",entry.getKey() + ": " +
                    entry.getValue().toString());
        }

        mScoreboardList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                scoreboardManager.setCurrentScoreboard((Scoreboard) mScoreboardList.getItemAtPosition(position));
                scoreboardManager.apply();
                mDrawerLayout.closeDrawers();
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
