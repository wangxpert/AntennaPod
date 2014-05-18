package de.danoeh.antennapod.adapter;

import android.content.Context;
import android.content.res.TypedArray;
import android.view.View;
import android.widget.ImageButton;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.feed.FeedItem;
import de.danoeh.antennapod.feed.FeedMedia;
import de.danoeh.antennapod.storage.DownloadRequester;

/**
 * Utility methods for the action button that is displayed on the right hand side
 * of a listitem.
 */
public class ActionButtonUtils {

    private final int[] labels;
    private final TypedArray drawables;
    private final Context context;

    public ActionButtonUtils(Context context) {
        if (context == null) throw new IllegalArgumentException("context = null");
        this.context = context;
        drawables = context.obtainStyledAttributes(new int[]{
                R.attr.av_play, R.attr.navigation_cancel, R.attr.av_download, R.attr.navigation_chapters});
        labels = new int[]{R.string.play_label, R.string.cancel_download_label, R.string.download_label};
    }

    /**
     * Sets the displayed bitmap and content description of the given
     * action button so that it matches the state of the FeedItem.
     */
    public void configureActionButton(ImageButton butSecondary, FeedItem item) {
        if (butSecondary == null || item == null) throw new IllegalArgumentException("butSecondary or item was null");
        final FeedMedia media = item.getMedia();
        if (media != null) {
            final boolean isDownloadingMedia = DownloadRequester.getInstance().isDownloadingFile(media);
            if (!media.isDownloaded()) {
                if (isDownloadingMedia) {
                    // item is being downloaded
                    butSecondary.setVisibility(View.VISIBLE);
                    butSecondary.setImageDrawable(drawables
                            .getDrawable(1));
                    butSecondary.setContentDescription(context.getString(labels[1]));
                } else {
                    // item is not downloaded and not being downloaded
                    butSecondary.setVisibility(View.VISIBLE);
                    butSecondary.setImageDrawable(drawables.getDrawable(2));
                    butSecondary.setContentDescription(context.getString(labels[2]));
                }
            } else {
                // item is not being downloaded
                butSecondary.setVisibility(View.VISIBLE);
                if (media.isPlaying()) {
                    butSecondary.setImageDrawable(drawables.getDrawable(3));
                } else {
                    butSecondary
                            .setImageDrawable(drawables.getDrawable(0));
                }
                butSecondary.setContentDescription(context.getString(labels[0]));
            }
        } else {
            butSecondary.setVisibility(View.INVISIBLE);
        }
    }
}
