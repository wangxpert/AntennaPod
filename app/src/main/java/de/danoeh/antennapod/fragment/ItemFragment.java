package de.danoeh.antennapod.fragment;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;

import org.apache.commons.lang3.ArrayUtils;

import java.util.List;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.activity.MainActivity;
import de.danoeh.antennapod.adapter.DefaultActionButtonCallback;
import de.danoeh.antennapod.core.event.DownloadEvent;
import de.danoeh.antennapod.core.event.DownloaderUpdate;
import de.danoeh.antennapod.core.event.FeedItemEvent;
import de.danoeh.antennapod.core.event.QueueEvent;
import de.danoeh.antennapod.core.feed.EventDistributor;
import de.danoeh.antennapod.core.feed.FeedItem;
import de.danoeh.antennapod.core.feed.FeedMedia;
import de.danoeh.antennapod.core.glide.ApGlideSettings;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.service.download.Downloader;
import de.danoeh.antennapod.core.storage.DBReader;
import de.danoeh.antennapod.core.storage.DBTasks;
import de.danoeh.antennapod.core.storage.DBWriter;
import de.danoeh.antennapod.core.storage.DownloadRequestException;
import de.danoeh.antennapod.core.storage.DownloadRequester;
import de.danoeh.antennapod.core.util.Converter;
import de.danoeh.antennapod.core.util.IntentUtils;
import de.danoeh.antennapod.core.util.LongList;
import de.danoeh.antennapod.core.util.ShareUtils;
import de.danoeh.antennapod.core.util.playback.Timeline;
import de.danoeh.antennapod.menuhandler.FeedItemMenuHandler;
import de.greenrobot.event.EventBus;
import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;

/**
 * Displays information about a FeedItem and actions.
 */
public class ItemFragment extends Fragment {

    private static final String TAG = "ItemFragment";

    private static final int EVENTS = EventDistributor.UNREAD_ITEMS_UPDATE;

    private static final String ARG_FEEDITEM = "feeditem";

    /**
     * Creates a new instance of an ItemFragment
     *
     * @param feeditem The ID of the FeedItem that should be displayed.
     * @return The ItemFragment instance
     */
    public static ItemFragment newInstance(long feeditem) {
        ItemFragment fragment = new ItemFragment();
        Bundle args = new Bundle();
        args.putLong(ARG_FEEDITEM, feeditem);
        fragment.setArguments(args);
        return fragment;
    }

    private boolean itemsLoaded = false;
    private long itemID;
    private FeedItem item;
    private LongList queue;
    private String webviewData;
    private List<Downloader> downloaderList;

    private ViewGroup root;
    private View header;
    private WebView webvDescription;
    private TextView txtvTitle;
    private TextView txtvDuration;
    private TextView txtvPublished;
    private ImageView imgvCover;
    private ProgressBar progbarDownload;
    private ProgressBar progbarLoading;
    private Button butAction1;
    private Button butAction2;
    private ImageButton butMore;
    private PopupMenu popupMenu;

    private Subscription subscription;

    /**
     * URL that was selected via long-press.
     */
    private String selectedURL;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        setHasOptionsMenu(false);

        itemID = getArguments().getLong(ARG_FEEDITEM, -1);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        ((MainActivity) getActivity()).getSupportActionBar().setTitle("");
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        View layout = inflater.inflate(R.layout.feeditem_fragment, container, false);

        header = inflater.inflate(R.layout.feeditem_fragment_header, toolbar, false);
        root = (ViewGroup) layout.findViewById(R.id.content_root);
        txtvTitle = (TextView) header.findViewById(R.id.txtvTitle);
        txtvDuration = (TextView) header.findViewById(R.id.txtvDuration);
        txtvPublished = (TextView) header.findViewById(R.id.txtvPublished);
        if (Build.VERSION.SDK_INT >= 14) { // ellipsize is causing problems on old versions, see #448
            txtvTitle.setEllipsize(TextUtils.TruncateAt.END);
        }
        webvDescription = (WebView) layout.findViewById(R.id.webvDescription);
        if (UserPreferences.getTheme() == R.style.Theme_AntennaPod_Dark) {
            if (Build.VERSION.SDK_INT >= 11
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                webvDescription.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            }
            webvDescription.setBackgroundColor(getResources().getColor(
                R.color.black));
        }
        webvDescription.getSettings().setUseWideViewPort(false);
        webvDescription.getSettings().setLayoutAlgorithm(
            WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
        webvDescription.getSettings().setLoadWithOverviewMode(true);
        webvDescription.setOnLongClickListener(webViewLongClickListener);
        webvDescription.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                if(IntentUtils.isCallable(getActivity(), intent)) {
                    startActivity(intent);
                }
                return true;
            }
        });
        registerForContextMenu(webvDescription);

        imgvCover = (ImageView) header.findViewById(R.id.imgvCover);
        progbarDownload = (ProgressBar) header.findViewById(R.id.progbarDownload);
        progbarLoading = (ProgressBar) layout.findViewById(R.id.progbarLoading);
        butAction1 = (Button) header.findViewById(R.id.butAction1);
        butAction2 = (Button) header.findViewById(R.id.butAction2);
        butMore = (ImageButton) header.findViewById(R.id.butMoreActions);
        popupMenu = new PopupMenu(getActivity(), butMore);

        butAction1.setOnClickListener(new View.OnClickListener() {
                                          DefaultActionButtonCallback actionButtonCallback = new DefaultActionButtonCallback(getActivity());

                                          @Override

                                          public void onClick(View v) {
                                              if (item == null) {
                                                  return;
                                              }
                                              actionButtonCallback.onActionButtonPressed(item);
                                              FeedMedia media = item.getMedia();
                                              if (media != null && media.isDownloaded()) {
                                                  // playback was started, dialog should close itself
                                                  ((MainActivity) getActivity()).dismissChildFragment();
                                              }
                                          }
                                      }
        );

        butAction2.setOnClickListener(v -> {
                if (item == null) {
                    return;
                }

                if (item.hasMedia()) {
                    FeedMedia media = item.getMedia();
                    if (!media.isDownloaded()) {
                        DBTasks.playMedia(getActivity(), media, true, true, true);
                        ((MainActivity) getActivity()).dismissChildFragment();
                    } else {
                        DBWriter.deleteFeedMediaOfItem(getActivity(), media.getId());
                    }
                } else if (item.getLink() != null) {
                    Uri uri = Uri.parse(item.getLink());
                    getActivity().startActivity(new Intent(Intent.ACTION_VIEW, uri));
                }
            }
        );

        butMore.setOnClickListener(v -> {
                if (item == null) {
                    return;
                }
                popupMenu.getMenu().clear();
                popupMenu.inflate(R.menu.feeditem_options);
                if (item.hasMedia()) {
                    FeedItemMenuHandler.onPrepareMenu(getActivity(), popupMenuInterface, item, true, queue);
                } else {
                    // these are already available via button1 and button2
                    FeedItemMenuHandler.onPrepareMenu(getActivity(), popupMenuInterface, item, true, queue,
                        R.id.mark_read_item, R.id.visit_website_item);
                }
                popupMenu.show();
            }
        );

        popupMenu.setOnMenuItemClickListener(menuItem -> {

                try {
                    return FeedItemMenuHandler.onMenuItemClicked(getActivity(), menuItem.getItemId(), item);
                } catch (DownloadRequestException e) {
                    e.printStackTrace();
                    Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_LONG).show();
                    return true;
                }
            }
        );

        return layout;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        toolbar.addView(header);
        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        EventDistributor.getInstance().register(contentUpdate);
        EventBus.getDefault().registerSticky(this);
        if(itemsLoaded) {
            updateAppearance();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        EventDistributor.getInstance().unregister(contentUpdate);
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        resetViewState();
        if(subscription != null) {
            subscription.unsubscribe();
        }
        if (webvDescription != null && root != null) {
            root.removeView(webvDescription);
            webvDescription.destroy();
        }
    }

    private void resetViewState() {
        Toolbar toolbar = ((MainActivity) getActivity()).getToolbar();
        toolbar.removeView(header);
    }

    private final FeedItemMenuHandler.MenuInterface popupMenuInterface = new FeedItemMenuHandler.MenuInterface() {
        @Override
        public void setItemVisibility(int id, boolean visible) {
            MenuItem item = popupMenu.getMenu().findItem(id);
            if (item != null) {
                item.setVisible(visible);
            }
        }
    };


    private void onFragmentLoaded() {
        progbarLoading.setVisibility(View.INVISIBLE);
        if (webviewData != null) {
            webvDescription.loadDataWithBaseURL(null, webviewData, "text/html",
                    "utf-8", "about:blank");
        }
        updateAppearance();
    }

    private void updateAppearance() {
        if (item == null) {
            Log.d(TAG, "updateAppearance item is null");
            return;
        }

        txtvTitle.setText(item.getTitle());

        if (item.getPubDate() != null) {
            txtvPublished.setText(DateUtils.formatDateTime(getActivity(), item.getPubDate().getTime(), DateUtils.FORMAT_ABBREV_ALL));
        }

        Glide.with(getActivity())
                .load(item.getImageUri())
                .placeholder(R.color.light_gray)
                .error(R.color.light_gray)
                .diskCacheStrategy(ApGlideSettings.AP_DISK_CACHE_STRATEGY)
                .fitCenter()
                .dontAnimate()
                .into(imgvCover);

        progbarDownload.setVisibility(View.INVISIBLE);
        if (item.hasMedia() && downloaderList != null) {
            for (Downloader downloader : downloaderList) {
                if (downloader.getDownloadRequest().getFeedfileType() == FeedMedia.FEEDFILETYPE_FEEDMEDIA
                        && downloader.getDownloadRequest().getFeedfileId() == item.getMedia().getId()) {
                    progbarDownload.setVisibility(View.VISIBLE);
                    progbarDownload.setProgress(downloader.getDownloadRequest().getProgressPercent());
                }
            }
        }

        FeedMedia media = item.getMedia();
        if (media == null) {
            TypedArray drawables = getActivity().obtainStyledAttributes(new int[]{R.attr.navigation_accept,
                    R.attr.location_web_site});

            if (!item.isPlayed()) {
                butAction1.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(0), null, null, null);
                butAction1.setText(getActivity().getString(R.string.mark_read_label));
                butAction1.setVisibility(View.VISIBLE);
            } else {
                butAction1.setVisibility(View.INVISIBLE);
            }

            if (item.getLink() != null) {
                butAction2.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(1), null, null, null);
                butAction2.setText(getActivity().getString(R.string.visit_website_label));
            } else {
                butAction2.setEnabled(false);
            }

            drawables.recycle();
        } else {if(media.getDuration() > 0) {
                txtvDuration.setText(Converter.getDurationStringLong(media.getDuration()));
            }

            boolean isDownloading = DownloadRequester.getInstance().isDownloadingFile(media);
            TypedArray drawables = getActivity().obtainStyledAttributes(new int[]{R.attr.av_play,
                    R.attr.av_download, R.attr.action_stream, R.attr.content_discard, R.attr.navigation_cancel});

            if (!media.isDownloaded()) {
                butAction2.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(2), null, null, null);
                butAction2.setText(getActivity().getString(R.string.stream_label));
            } else {
                butAction2.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(3), null, null, null);
                butAction2.setText(getActivity().getString(R.string.remove_episode_lable));
            }

            if (isDownloading) {
                butAction1.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(4), null, null, null);
                butAction1.setText(getActivity().getString(R.string.cancel_download_label));
            } else if (media.isDownloaded()) {
                butAction1.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(0), null, null, null);
                butAction1.setText(getActivity().getString(R.string.play_label));
            } else {
                butAction1.setCompoundDrawablesWithIntrinsicBounds(drawables.getDrawable(1), null, null, null);
                butAction1.setText(getActivity().getString(R.string.download_label));
            }

            drawables.recycle();
        }
    }

    private View.OnLongClickListener webViewLongClickListener = new View.OnLongClickListener() {

        @Override
        public boolean onLongClick(View v) {
            WebView.HitTestResult r = webvDescription.getHitTestResult();
            if (r != null
                    && r.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
                Log.d(TAG, "Link of webview was long-pressed. Extra: " + r.getExtra());
                selectedURL = r.getExtra();
                webvDescription.showContextMenu();
                return true;
            }
            selectedURL = null;
            return false;
        }
    };

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        boolean handled = selectedURL != null;
        if (selectedURL != null) {
            switch (item.getItemId()) {
                case R.id.open_in_browser_item:
                    Uri uri = Uri.parse(selectedURL);
                    final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    if(IntentUtils.isCallable(getActivity(), intent)) {
                        getActivity().startActivity(intent);
                    }
                    break;
                case R.id.share_url_item:
                    ShareUtils.shareLink(getActivity(), selectedURL);
                    break;
                case R.id.copy_url_item:
                    if (android.os.Build.VERSION.SDK_INT >= 11) {
                        ClipData clipData = ClipData.newPlainText(selectedURL,
                                selectedURL);
                        android.content.ClipboardManager cm = (android.content.ClipboardManager) getActivity()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setPrimaryClip(clipData);
                    } else {
                        android.text.ClipboardManager cm = (android.text.ClipboardManager) getActivity()
                                .getSystemService(Context.CLIPBOARD_SERVICE);
                        cm.setText(selectedURL);
                    }
                    Toast t = Toast.makeText(getActivity(),
                            R.string.copied_url_msg, Toast.LENGTH_SHORT);
                    t.show();
                    break;
                default:
                    handled = false;
                    break;

            }
            selectedURL = null;
        }
        return handled;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                                    ContextMenu.ContextMenuInfo menuInfo) {
        if (selectedURL != null) {
            super.onCreateContextMenu(menu, v, menuInfo);
                Uri uri = Uri.parse(selectedURL);
                final Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                if(IntentUtils.isCallable(getActivity(), intent)) {
                    menu.add(Menu.NONE, R.id.open_in_browser_item, Menu.NONE,
                            R.string.open_in_browser_label);
                }
                menu.add(Menu.NONE, R.id.copy_url_item, Menu.NONE,
                        R.string.copy_url_label);
                menu.add(Menu.NONE, R.id.share_url_item, Menu.NONE,
                        R.string.share_url_label);
                menu.setHeaderTitle(selectedURL);
        }
    }

    public void onEventMainThread(QueueEvent event) {
        if(event.contains(itemID)) {
            load();
        }
    }

    public void onEventMainThread(FeedItemEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        for(FeedItem item : event.items) {
            if(itemID == item.getId()) {
                load();
                return;
            }
        }
    }

    public void onEventMainThread(DownloadEvent event) {
        Log.d(TAG, "onEventMainThread() called with: " + "event = [" + event + "]");
        DownloaderUpdate update = event.update;
        downloaderList = update.downloaders;
        if(item == null || item.getMedia() == null) {
            return;
        }
        long mediaId = item.getMedia().getId();
        if(ArrayUtils.contains(update.mediaIds, mediaId)) {
            if (itemsLoaded && getActivity() != null) {
                updateAppearance();
            }
        }
    }


    private EventDistributor.EventListener contentUpdate = new EventDistributor.EventListener() {
        @Override
        public void update(EventDistributor eventDistributor, Integer arg) {
            if ((arg & EVENTS) != 0) {
                load();
            }
        }
    };

    private void load() {
        if(subscription != null) {
            subscription.unsubscribe();
        }
        subscription = Observable.fromCallable(() -> loadInBackground())
            .subscribeOn(Schedulers.newThread())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(result -> {
                item = result.first;
                queue = result.second;
                if (!itemsLoaded) {
                    itemsLoaded = true;
                    onFragmentLoaded();
                } else {
                    updateAppearance();
                }
            }, error -> {
                Log.e(TAG, Log.getStackTraceString(error));
            });
    }

    private Pair<FeedItem,LongList> loadInBackground() {
        FeedItem data1 = DBReader.getFeedItem(itemID);
        if (data1 != null) {
            Timeline t = new Timeline(getActivity(), data1);
            webviewData = t.processShownotes(false);
        }
        LongList data2 = DBReader.getQueueIDList();
        return Pair.create(data1, data2);
    }

}
