package mx.ivancastro.syncme;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity {
    private boolean mIsBound = false;
    private MusicService mServ;

    private ProgressBar progressBar;

    Intent music;
    Thread progressThread;

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
}
