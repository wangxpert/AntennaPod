package de.danoeh.antennapod.adapter;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.Toast;

import org.apache.commons.lang3.Validate;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.dialog.DownloadRequestErrorDialogCreator;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.gpoddernet.model.GpodnetEpisodeAction;
import de.danoeh.antennapod.core.preferences.GpodnetPreferences;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.playback.PlaybackService;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequestException;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.util.NetworkUtils;

/**
 * Default implementation of an ActionButtonCallback
 */
public class DefaultActionButtonCallback implements ActionButtonCallback {

    private static final String TAG = "DefaultActionButtonCallback";

    private final Context context;

    private final int TEN_MINUTES_IN_MILLIS = 60 * 1000 * 10;

    // remember timestamp when user allowed downloading via mobile connection
    private static long allowMobileDownloadsTimestamp;
    private static long onlyAddToQueueTimeStamp;

    public DefaultActionButtonCallback(Context context) {
        Validate.notNull(context);
        this.context = context;
    }

    @Override
    public void onActionButtonPressed(final FeedItem item) {

        if (item.hasMedia()) {
            final FeedMedia media = item.getMedia();
            boolean isDownloading = DownloadRequester.getInstance().isDownloadingFile(media);
            if (!isDownloading && !media.isDownloaded()) {
                if (UserPreferences.isAllowMobileUpdate() || NetworkUtils.connectedToWifi(context) ||
                        (System.currentTimeMillis()-allowMobileDownloadsTimestamp) < TEN_MINUTES_IN_MILLIS) {
                    try {
                        DBTasks.downloadFeedItems(context, item);
                        Toast.makeText(context, R.string.status_downloading_label, Toast.LENGTH_SHORT).show();
                    } catch (DownloadRequestException e) {
                        e.printStackTrace();
                        DownloadRequestErrorDialogCreator.newRequestErrorDialog(context, e.getMessage());
                    }
                } else {
                    if(System.currentTimeMillis() - onlyAddToQueueTimeStamp < TEN_MINUTES_IN_MILLIS) {
                        DBWriter.addQueueItem(context, item.getId());
                        Toast.makeText(context, R.string.added_to_queue_label, Toast.LENGTH_SHORT).show();
                    } else {
                        confirmMobileDownload(context, item);
                    }
                }
            } else if (isDownloading) {
                DownloadRequester.getInstance().cancelDownload(context, media);
                Toast.makeText(context, R.string.download_cancelled_msg, Toast.LENGTH_SHORT).show();
            } else { // media is downloaded
                if (item.hasMedia() && item.getMedia().isCurrentlyPlaying()) {
                    context.sendBroadcast(new Intent(PlaybackService.ACTION_PAUSE_PLAY_CURRENT_EPISODE));
                }
                else if (item.hasMedia() && item.getMedia().isCurrentlyPaused()) {
                    context.sendBroadcast(new Intent(PlaybackService.ACTION_RESUME_PLAY_CURRENT_EPISODE));
                }
                else {
                    DBTasks.playMedia(context, media, false, true, false);
                }
            }
        } else {
            if (!item.isRead()) {
                DBWriter.markItemRead(context, item, true, true);

                if(GpodnetPreferences.loggedIn()) {
                    // gpodder: send played action
                    FeedMedia media = item.getMedia();
                    GpodnetEpisodeAction action = new GpodnetEpisodeAction.Builder(item, GpodnetEpisodeAction.Action.PLAY)
                            .currentDeviceId()
                            .currentTimestamp()
                            .started(media.getDuration() / 1000)
                            .position(media.getDuration() / 1000)
                            .total(media.getDuration() / 1000)
                            .build();
                    GpodnetPreferences.enqueueEpisodeAction(action);
                }
            }
        }
    }

    private void confirmMobileDownload(final Context context, final FeedItem item) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder
                .setTitle(R.string.confirm_mobile_download_dialog_title)
                .setMessage(context.getText(R.string.confirm_mobile_download_dialog_message))
                .setPositiveButton(R.string.confirm_mobile_download_dialog_enable_temporarily,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                allowMobileDownloadsTimestamp = System.currentTimeMillis();
                                try {
                                    DBTasks.downloadFeedItems(context, item);
                                    Toast.makeText(context, R.string.status_downloading_label, Toast.LENGTH_SHORT).show();
                                } catch (DownloadRequestException e) {
                                    e.printStackTrace();
                                    DownloadRequestErrorDialogCreator.newRequestErrorDialog(context, e.getMessage());
                                }
                            }
                        })
                .setNeutralButton(R.string.confirm_mobile_download_dialog_only_add_to_queue,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                onlyAddToQueueTimeStamp = System.currentTimeMillis();
                                DBWriter.addQueueItem(context, item.getId());
                                Toast.makeText(context, R.string.added_to_queue_label, Toast.LENGTH_SHORT).show();
                            }
                        })
                .setNegativeButton(R.string.cancel_label, null)
                .create()
                .show();
    }
}
