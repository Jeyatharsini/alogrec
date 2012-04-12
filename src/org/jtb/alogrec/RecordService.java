package org.jtb.alogrec;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;

public class RecordService extends Service {
	static final String EXTRA_START_RECORD = "START_WRITE";
	static final String EXTRA_STOP_RECORD = "STOP_WRITE";
	static final String EXTRA_LOG_FILE = "LOG_FILE";

	private static final Executor EX = Executors.newSingleThreadExecutor();
	private static final ScheduledExecutorService SEXS = Executors
			.newScheduledThreadPool(1);
	private static final int NOTIFY_ID = 100;
	private static final int MAX_RETRIES = 10;

	private LogcatRecorder recorder;
	private File logFile;
	private int tries;

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		stop();
		PartialLock.release();
	}

	private void stop() {
		if (recorder != null) {
			recorder.setRunning(false);
			recorder = null;
		}
	}

	private void start() {
		stop();
		if (!Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			if (tries > MAX_RETRIES) {
				stopSelf();
				Log.e("alogrec",
						String.format(
								"external storage not mounted after %d tries, cannot start",
								tries - 1));
				new LogFilePref(this).clearFile();
				return;
			}
			tries++;
			SEXS.schedule(new Runnable() {

				@Override
				public void run() {
					start();
				}
			}, 4, TimeUnit.SECONDS);
		} else {
			recorder = new LogcatRecorder(this, logFile);
			EX.execute(recorder);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent == null) {
			// nothing
		} else if (intent.hasExtra(EXTRA_START_RECORD)) {
			logFile = new File(intent.getStringExtra(EXTRA_LOG_FILE));
			startForeground(NOTIFY_ID, getNotification());
			tries = 0;
			start();
		} else if (intent.hasExtra(EXTRA_STOP_RECORD)) {
			stopForeground(true);
			stopSelf();
		}

		return Service.START_STICKY;
	}

	private Notification getNotification() {
		int icon = android.R.drawable.stat_sys_download;
		CharSequence tickerText = "aLogrec - Recording logs ...";

		Notification notification = new Notification(icon, tickerText,
				System.currentTimeMillis());
		notification.flags |= Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_NO_CLEAR;

		CharSequence contentTitle = tickerText;
		CharSequence contentText = "Select to control recording.";

		Intent notificationIntent = new Intent(this, RouterActivity.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				notificationIntent, 0);

		notification.setLatestEventInfo(this, contentTitle, contentText,
				contentIntent);
		return notification;
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return null;
	}
}
