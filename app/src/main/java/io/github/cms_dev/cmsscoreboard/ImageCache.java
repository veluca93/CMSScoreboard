package io.github.cms_dev.cmsscoreboard;

import android.app.ProgressDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Queue;

public class ImageCache  {
    static HashSet<String> urlImage ;
    static HashMap<String,BitmapDrawable> image ;
    static Queue<String> urlToDownload ;
    static Thread downloader = null ;
    static boolean stop = true ;

    public ImageCache()
    {
        if( urlImage == null ) urlImage = new HashSet<>();
        if( image == null )         image = new HashMap<>();
        if( urlToDownload == null ) urlToDownload = new LinkedList<>();
    }

  private class BackgroundTask extends AsyncTask<String, Void, String>
  {

      @Override
      protected void onPreExecute() {
          super.onPreExecute();
          //progress.setProgress(0);
          //progress.show();
      }

      @Override
      protected String doInBackground(String... params)
      {
          String src = params[0].toString();

         // Log.d("Download","start " + src);

          try {
              java.net.URL url = new java.net.URL(src);
              HttpURLConnection connection = (HttpURLConnection) url
                      .openConnection();
              connection.setRequestProperty("Accept", "*/*");
              connection.setDoInput(true);
              connection.connect();
              InputStream input = connection.getInputStream();
              Bitmap myBitmap = BitmapFactory.decodeStream(input);
            //  Log.d("Foto",src/*src.substring(src.lastIndexOf("/"),src.length())*/ + " H: " + myBitmap.getHeight() + " W : " + myBitmap.getWidth());
              image.put(src,new BitmapDrawable(ScoreboardActivity.mContext.getResources(),myBitmap) );
          } catch (IOException e) {
              e.printStackTrace();
              return src ;
          }

          return "" ;
      }

  }

    public boolean addImage(String url)
    {
        if( !urlImage.contains(url) )
        {
            urlImage.add(url);
            urlToDownload.add(url);
            new BackgroundTask().execute(url);
            return true ;
        }
        return false ;
    }

    public void stopThread()
    {
        stop = true ;
    }
    public BitmapDrawable getImage( String url )
    {
        if( image.containsKey(url) )
            return image.get(url);
        return null ;
    }







}
