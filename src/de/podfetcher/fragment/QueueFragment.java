package de.podfetcher.fragment;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.actionbarsherlock.view.ActionMode;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

import de.podfetcher.R;
import de.podfetcher.feed.FeedManager;

public class QueueFragment extends ItemlistFragment {

	public QueueFragment() {
		super(FeedManager.getInstance().getQueue(), true);
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		super.onCreateActionMode(mode, menu);
		menu.add(Menu.NONE, R.id.move_up_item, Menu.NONE,
				R.string.move_up_label);
		menu.add(Menu.NONE, R.id.move_down_item, Menu.NONE,
				R.string.move_down_label);
		return true;
	}

	@Override
	public void onPause() {
		super.onPause();
		try {
			getActivity().unregisterReceiver(queueUpdate);
		} catch (IllegalArgumentException e) {

		}
	}

	@Override
	public void onResume() {
		super.onResume();
		getActivity().registerReceiver(queueUpdate,
				new IntentFilter(FeedManager.ACTION_QUEUE_UPDATE));
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		boolean handled = false;
		switch (item.getItemId()) {
		case R.id.move_up_item:
			manager.moveQueueItem(getActivity(), selectedItem, -1);
			handled = true;
			break;
		case R.id.move_down_item:
			manager.moveQueueItem(getActivity(), selectedItem, 1);
			handled = true;
			break;
		default:
			handled = super.onActionItemClicked(mode, item);
		}
		fila.notifyDataSetChanged();
		mode.finish();
		return handled;
	}

	private BroadcastReceiver queueUpdate = new BroadcastReceiver() {

		@Override
		public void onReceive(Context context, Intent intent) {
			fila.notifyDataSetChanged();
		}

	};

}
