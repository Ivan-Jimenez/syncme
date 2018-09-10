package mx.ivancastro.syncme;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {
    private boolean mIsBound = false;
    private MusicService mServ;

    private ProgressBar progressBar;
    private TextView songName;

    Intent music;
    Thread progressThread;

    ArrayList<String> arrayTest;
    private static final int MY_PERMISSION_REQUEST = 1;

    private ServiceConnection Scon = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mServ = ((MusicService.ServiceBinder)service).getServiceInstance();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServ = null;
        }
    };

    void doBindService () {
        bindService(new Intent(this, MusicService.class), Scon, Context.BIND_AUTO_CREATE);
        mIsBound = true;
    }

    void doUnbindService () {
        if (mIsBound) {
            unbindService(Scon);
            mIsBound = false;
        }
    }

    @Override
    public void onDestroy () {
        doUnbindService();
        super.onDestroy();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        arrayTest = new ArrayList<>();

        // Check permissions
        if (ContextCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_REQUEST);
            } else {
                ActivityCompat.requestPermissions(MainActivity.this,
                        new String[] {Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSION_REQUEST);
            }
        } else {
            getSongs();
        }


        songName = findViewById(R.id.txtSongName);

        //getSongs();

        // Create playlist
        ArrayList<HashMap<String, String>> songList = getPlayList(Environment.getExternalStorageDirectory().getAbsolutePath());
        if (songList != null) {
            for (int i = 0; i < songList.size(); i++) {
                String fileName = songList.get(i).get("file_name");
                String filePath = songList.get(i).get("file_path");
                Log.e("file details ", " name: " + fileName + " path: " + filePath);
            }
            Toast.makeText(this, songList.get(0).get("file_name"), Toast.LENGTH_LONG).show();
            songName.setText(arrayTest.get(5));
        }

        // Bind the service
        doBindService();

        //Start the service
        music = new Intent();
        music.setClass(this, MusicService.class);
        //startService(music);

        progressBar = findViewById(R.id.progressBar);

        // Set buttons handlers
        Button btnPlay = findViewById(R.id.btnPlay);
        btnPlay.setOnClickListener(btnClick(this));
        btnPlay.setOnLongClickListener(longClick);

    }

    private View.OnClickListener btnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            if (!mServ.isPlaying()) {
                startService(music);
                mServ.resumeMusic();
            } else {
                mServ.pauseMusic();
            }
        }
    };

    // TEST for progressbar
    private View.OnClickListener btnClick (final Activity activity) {
        View.OnClickListener click = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (progressThread == null) createAndRunProgressThread(activity);
                if (!mServ.isPlaying()) {
                    startService(music);
                    mServ.resumeMusic();
                } else {
                    mServ.pauseMusic();
                    try {
                        progressThread.stop();
                    } catch (Exception e) { e.printStackTrace(); }
                }
            }
        };
        return click;
    }

    private View.OnLongClickListener longClick = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick (View v) {
            mServ.forwardMusic();
            Toast.makeText(MainActivity.this, "Ademantado 10s", Toast.LENGTH_LONG).show();
            return true;
        }
    };

    private void createAndRunProgressThread (final Activity activity) {
        progressThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    try {
                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateProgressBar();
                            }
                        });
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        messageBox(activity, "¡Error! -> progress thread: " + e.toString() + " - " +
                        e.getMessage(), "createAndRunProgressThread");
                    }
                }
            }
        });
        progressThread.start();
    }

    private void updateProgressBar () {
        progressBar.setMax(mServ.getSongDuration());
        progressBar.setProgress(mServ.getSongCurentPosition());
    }

    public void messageBox (final Context context, final String message, final String title) {
        this.runOnUiThread(
                new Runnable() {
                    @Override
                    public void run() {
                        final AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                        alertDialog.setTitle(title);
                        alertDialog.setIcon(android.R.drawable.stat_sys_warning);
                        alertDialog.setMessage(message);
                        alertDialog.setCancelable(false);
                        alertDialog.setButton("Aceptar", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                alertDialog.cancel();
                            }
                        });
                        alertDialog.show();
                    }
                }
        );
    }

    /**
     * Function to read all mp3 files from sdcard
     * and store the details in ArrayList
     * */
    ArrayList<HashMap<String,String>> getPlayList(String rootPath) {
        ArrayList<HashMap<String,String>> fileList = new ArrayList<>();


        try {
            File rootFolder = new File(rootPath);
            File[] files = rootFolder.listFiles(); //here you will get NPE if directory doesn't contains  any file,handle it like this.
            for (File file : files) {
                if (file.isDirectory()) {
                    if (getPlayList(file.getAbsolutePath()) != null) {
                        fileList.addAll(getPlayList(file.getAbsolutePath()));
                    } else {
                        break;
                    }
                } else if (file.getName().endsWith(".mp3")) {
                    HashMap<String, String> song = new HashMap<>();
                    song.put("file_path", file.getAbsolutePath());
                    song.put("file_name", file.getName());
                    fileList.add(song);
                }
            }
            return fileList;
        } catch (Exception e) {
            return null;
        }
    }

    private void getSongs () {
        ContentResolver contentResolver = getContentResolver();
        Uri songUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor songCursor = contentResolver.query(songUri, null, null, null, null);

        if (songCursor != null && songCursor.moveToFirst()) {
            int songTitle = songCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int songArtist = songCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);

            do {
                String currentTitle = songCursor.getString(songTitle);
                String currentArtist = songCursor.getString(songArtist);
                arrayTest.add(currentTitle.concat(" ").concat(currentArtist));
            } while (songCursor.moveToNext());
        }
    }

    @Override
    public void onRequestPermissionsResult (int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSION_REQUEST: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (ContextCompat.checkSelfPermission(MainActivity.this,
                            Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                        Toast.makeText(MainActivity.this, "Persmiso", Toast.LENGTH_LONG).show();
                        // Do stuff
                        getSongs();
                    }
                } else {
                    // Permission no granted
                    finish();
                }
                return;
            }
        }
    }
}
