package de.danoeh.antennapod.activity;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import de.danoeh.antennapod.AppConfig;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.feed.Feed;
import de.danoeh.antennapod.preferences.UserPreferences;
import de.danoeh.antennapod.service.download.DownloadRequest;
import de.danoeh.antennapod.service.download.DownloadStatus;
import de.danoeh.antennapod.service.download.Downloader;
import de.danoeh.antennapod.service.download.HttpDownloader;
import de.danoeh.antennapod.syndication.handler.FeedHandler;
import de.danoeh.antennapod.syndication.handler.UnsupportedFeedtypeException;
import de.danoeh.antennapod.util.DownloadError;
import de.danoeh.antennapod.util.FileNameGenerator;
import de.danoeh.antennapod.util.StorageUtils;
import de.danoeh.antennapod.util.URLChecker;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.Date;

/**
 * Downloads a feed from a feed URL and parses it. Subclasses can display the
 * feed object that was parsed. This activity MUST be started with a given URL
 * or an Exception will be thrown.
 * <p/>
 * If the feed cannot be downloaded or parsed, an error dialog will be displayed
 * and the activity will finish as soon as the error dialog is closed.
 */
public abstract class OnlineFeedViewActivity extends ActionBarActivity {
    private static final String TAG = "OnlineFeedViewActivity";
    public static final String ARG_FEEDURL = "arg.feedurl";

    /** Optional argument: specify a title for the actionbar. */
    public static final String ARG_TITLE = "title";

    public static final int RESULT_ERROR = 2;

    private Feed feed;
    private Downloader downloader;

    @Override
    protected void onCreate(Bundle arg0) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(arg0);

        if (getIntent() != null && getIntent().hasExtra(ARG_TITLE)) {
            getSupportActionBar().setTitle(getIntent().getStringExtra(ARG_TITLE));
        }

        StorageUtils.checkStorageAvailability(this);
        final String feedUrl = getIntent().getStringExtra(ARG_FEEDURL);
        if (feedUrl == null) {
            throw new IllegalArgumentException(
                    "Activity must be started with feedurl argument!");
        }
        if (AppConfig.DEBUG)
            Log.d(TAG, "Activity was started with url " + feedUrl);
        setLoadingLayout();
        startFeedDownload(feedUrl);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (downloader != null && !downloader.isFinished()) {
            downloader.cancel();
        }
    }


    private void onDownloadCompleted(final Downloader downloader) {
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                if (AppConfig.DEBUG) Log.d(TAG, "Download was completed");
                DownloadStatus status = downloader.getResult();
                if (status != null) {
                    if (!status.isCancelled()) {
                        if (status.isSuccessful()) {
                            parseFeed();
                        } else {
                            String errorMsg = status.getReason().getErrorString(
                                    OnlineFeedViewActivity.this);
                            if (errorMsg != null
                                    && status.getReasonDetailed() != null) {
                                errorMsg += " ("
                                        + status.getReasonDetailed() + ")";
                            }
                            showErrorDialog(errorMsg);
                        }
                    }
                } else {
                    Log.wtf(TAG,
                            "DownloadStatus returned by Downloader was null");
                    finish();
                }
            }
        });

    }

    private void startFeedDownload(String url) {
        if (AppConfig.DEBUG)
            Log.d(TAG, "Starting feed download");
        url = URLChecker.prepareURL(url);
        feed = new Feed(url, new Date());
        String fileUrl = new File(getExternalCacheDir(),
                FileNameGenerator.generateFileName(feed.getDownload_url()))
                .toString();
        feed.setFile_url(fileUrl);
        final DownloadRequest request = new DownloadRequest(feed.getFile_url(),
                feed.getDownload_url(), "OnlineFeed", 0, Feed.FEEDFILETYPE_FEED);
        downloader = new HttpDownloader(
                request);
        new Thread() {
            @Override
            public void run() {
                loadData();
                downloader.call();
                onDownloadCompleted(downloader);
            }
        }.start();


    }

    /**
     * Displays a progress indicator.
     */
    private void setLoadingLayout() {
        RelativeLayout rl = new RelativeLayout(this);
        RelativeLayout.LayoutParams rlLayoutParams = new RelativeLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        RelativeLayout.LayoutParams pbLayoutParams = new RelativeLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        pbLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        rl.addView(pb, pbLayoutParams);
        addContentView(rl, rlLayoutParams);
    }

    private void parseFeed() {
        if (feed == null || feed.getFile_url() == null) {
            throw new IllegalStateException(
                    "feed must be non-null and downloaded when parseFeed is called");
        }

        if (AppConfig.DEBUG)
            Log.d(TAG, "Parsing feed");

        Thread thread = new Thread() {

            @Override
            public void run() {
                String reasonDetailed = "";
                boolean successful = false;
                FeedHandler handler = new FeedHandler();
                try {
                    handler.parseFeed(feed);
                    successful = true;
                } catch (SAXException e) {
                    e.printStackTrace();
                    reasonDetailed = e.getMessage();
                } catch (IOException e) {
                    e.printStackTrace();
                    reasonDetailed = e.getMessage();
                } catch (ParserConfigurationException e) {
                    e.printStackTrace();
                    reasonDetailed = e.getMessage();
                } catch (UnsupportedFeedtypeException e) {
                    e.printStackTrace();
                    reasonDetailed = e.getMessage();
                } finally {
                    boolean rc = new File(feed.getFile_url()).delete();
                    if (AppConfig.DEBUG)
                        Log.d(TAG, "Deleted feed source file. Result: " + rc);
                }

                if (successful) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showFeedInformation(feed);
                        }
                    });
                } else {
                    final String errorMsg =
                            DownloadError.ERROR_PARSER_EXCEPTION.getErrorString(
                                    OnlineFeedViewActivity.this)
                                    + " (" + reasonDetailed + ")";
                    runOnUiThread(new Runnable() {

                        @Override
                        public void run() {
                            showErrorDialog(errorMsg);
                        }
                    });
                }
            }
        };
        thread.start();
    }

    /**
     * Can be used to load data asynchronously.
     * */
    protected void loadData() {

    }

    /**
     * Called when feed parsed successfully
     */
    protected void showFeedInformation(Feed feed) {

    }

    private void showErrorDialog(String errorMsg) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.error_label);
        if (errorMsg != null) {
            builder.setMessage(getString(R.string.error_msg_prefix) + errorMsg);
        } else {
            builder.setMessage(R.string.error_msg_prefix);
        }
        builder.setNeutralButton(android.R.string.ok,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        builder.setOnCancelListener(new OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                setResult(RESULT_ERROR);
                finish();
            }
        });
        builder.show();
    }

}
