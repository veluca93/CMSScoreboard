package io.github.cms_dev.cmsscoreboard;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import javax.json.Json;
import javax.json.JsonException;
import javax.json.JsonObject;

public class ScoreboardUpdater implements Runnable {
    private Context ctx;
    private String src;
    private HashMap<String, TaskInformation> tasks = new HashMap<>();
    private HashMap<String, ContestantInformation> contestants = new HashMap<>();
    private boolean terminating;
    private ScoreboardStatus status;

    private class EventReceiver {
        private HttpURLConnection connection;
        private String eventName = "message";
        private String eventId = null;
        private String eventData = null;
        public EventReceiver(String url) {
            try {
                URL destURL = new URL(url);
                connection = (HttpURLConnection) destURL.openConnection();
                connection.setReadTimeout(100000);
                connection.setConnectTimeout(10000);
                connection.setRequestMethod("GET");
                connection.setDoInput(true);
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.connect();
            } catch (IOException e) {
                connection = null;
                ScoreboardUpdater.this.status = ScoreboardStatus.INVALID_URL;
            }

        }

        private boolean onEvent() {
            switch (eventName) {
                case "score":
                    try {
                        String[] data = eventData.split(" ");
                        if (!tasks.containsKey(data[1]) || !contestants.containsKey(data[0])) {
                            status = ScoreboardStatus.INVALID_DATA;
                            Log.v("onEvent", "Invalid event - no such user/task!");
                        } else {
                            updateScore(data[0], data[1], Double.parseDouble(data[2]));
                        }
                    } catch (IndexOutOfBoundsException|NumberFormatException|NullPointerException e) {
                        status = ScoreboardStatus.INVALID_DATA;
                        Log.v("onEvent", "Invalid event!");
                    }
                    break;
                default:
                    break;
            }
            return ScoreboardUpdater.this.status != ScoreboardStatus.CONNECTED;
        }

        private boolean dispatchEvent() {
            boolean ret = false;
            if (eventData != null) {
                eventData = eventData.substring(0, eventData.length()-1);
                ret = onEvent();
            }
            eventData = null;
            eventName = "message";
            return ret;
        }

        public void receiveEvents() {
            if (connection == null) return;
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), "UTF-8"));
                while (!ScoreboardUpdater.this.terminating) {
                    String line = reader.readLine();
                    if (line == null) {
                        dispatchEvent();
                        break;
                    };
                    if (line.isEmpty()) {
                        if (dispatchEvent()) break;
                        else continue;
                    }
                    if (line.charAt(0) == ':') continue;
                    String[] data = line.split(":", 2);
                    String field;
                    String value;
                    if (data.length == 1) {
                        field = data[0];
                        value = "";
                    } else {
                        field = data[0];
                        value = data[1];
                        if (value.startsWith(" ")) {
                            value = value.substring(1);
                        }
                    }
                    switch (field) {
                        case "event":
                            eventName = value;
                            break;
                        case "data":
                            if (eventData == null) {
                                eventData = value + '\n';
                            } else {
                                eventData += value + '\n';
                            }
                            break;
                        case "id":
                            eventId = value;
                            break;
                        case "retry":
                            // TODO: implement this
                            break;
                        default:
                            break;
                    }

                }
            } catch (IOException e) {
                ScoreboardUpdater.this.status = ScoreboardStatus.INVALID_DATA;
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                Log.v("exception in thread", sw.toString());
            }
        }
    }

    public ScoreboardUpdater(Context context, Scoreboard source) {
        ctx = context;
        if (source.URL.endsWith("Ranking.html"))
            src = source.URL.substring(0, source.URL.length() - 12);
        else
            src = source.URL;
        if (!src.endsWith("/"))
            src += "/";
        terminating = false;
        status = ScoreboardStatus.LOADING;
    }

    public synchronized List<ContestantInformation> getScores() throws ScoreboardNotReadyException {
        if (status != ScoreboardStatus.CONNECTED) {
            throw new ScoreboardNotReadyException("The scoreboard is not ready!", status);
        }
        ArrayList<ContestantInformation> scoreboard = new ArrayList<>();
        scoreboard.addAll(contestants.values());
        Collections.sort(scoreboard);
        return scoreboard;
    }

    public synchronized Double getMaxScore() {
        double sum = 0;
        for (TaskInformation task: tasks.values()) {
            sum += task.max_score;
        }
        return sum;
    }

    public synchronized void updateScore(String user, String task, double score) {
        contestants.get(user).setScore(task, score);
    }

    public JsonObject downloadJson(String url) throws IOException {
        URL taskURL = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) taskURL.openConnection();
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(10000);
        conn.setRequestMethod("GET");
        conn.setDoInput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.connect();
        javax.json.JsonReader reader = null;
        InputStream is = null;
        try {
            is = conn.getInputStream();
            reader = Json.createReader(is);
            return reader.readObject();
        }
        finally {
            if (reader != null)
                reader.close();
            else if (is != null) {
                is.close();
            }
        }
    }

    public void maintainBoard() {
        ConnectivityManager connMgr = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
            status = ScoreboardStatus.NO_NETWORK;
            return;
        }
        JsonObject tasksData;
        JsonObject usersData;
        JsonObject scoresData;
        try {
            tasksData = downloadJson(src + "tasks/");
            usersData = downloadJson(src + "users/");
            scoresData = downloadJson(src + "scores");
        } catch (JsonException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.v("exception in thread", sw.toString());
            status = ScoreboardStatus.INVALID_DATA;
            return;
        } catch (IOException e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.v("exception in thread", sw.toString());
            status = ScoreboardStatus.INVALID_URL;
            return;
        }
        try {
            for (String task: tasksData.keySet()) {
                tasks.put(task, new TaskInformation(tasksData.getJsonObject(task)));
            }
            for (String user: usersData.keySet()) {
                this.contestants.put(user, new ContestantInformation(user, usersData.getJsonObject(user)));
            }
            for (String user: scoresData.keySet()) {
                JsonObject user_scores = scoresData.getJsonObject(user);
                for (String task: user_scores.keySet()) {
                    if (tasks.containsKey(task)) {
                        updateScore(user, task, user_scores.getJsonNumber(task).doubleValue());
                    } else {
                        status = ScoreboardStatus.INVALID_DATA;
                        Log.v("SseEvent", "Invalid score data!");
                        return;
                    }
                }
            }
        } catch (NullPointerException|ClassCastException e) {
            status = ScoreboardStatus.INVALID_DATA;
            Log.v("SseEvent", "Invalid data!");
            return;
        }
        status = ScoreboardStatus.CONNECTED;
        try {
            EventReceiver eventSource = new EventReceiver(src + "events");
            eventSource.receiveEvents();
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.v("exception in thread", sw.toString());
            Log.v("EventReceiver", "Could not start EventReceiver!");
        }
    }

    @Override
    public void run() {
        try {
            while (!terminating) {
                maintainBoard();
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            Log.v("exception in thread", sw.toString());
        }
    }

    public void terminate() {
        terminating = true;
    }
}
