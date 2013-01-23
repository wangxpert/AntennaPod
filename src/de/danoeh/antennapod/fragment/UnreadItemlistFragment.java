package de.danoeh.antennapod.fragment;

import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;

import de.danoeh.antennapod.R;
import de.danoeh.antennapod.adapter.AbstractFeedItemlistAdapter;
import de.danoeh.antennapod.adapter.ExternalFeedItemlistAdapter;
import de.danoeh.antennapod.feed.FeedManager;

/** Contains all unread items. */
public class UnreadItemlistFragment extends ItemlistFragment {

	public UnreadItemlistFragment() {
		super(FeedManager.getInstance().getUnreadItems(), true);

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	protected AbstractFeedItemlistAdapter createListAdapter() {
		return new ExternalFeedItemlistAdapter(getActivity(), 0, items,
				adapterCallback);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		super.onCreateOptionsMenu(menu, inflater);
		menu.add(Menu.NONE, R.id.mark_all_read_item, Menu.NONE, getActivity()
				.getString(R.string.mark_all_read_label));
		menu.add(Menu.NONE, R.id.enqueue_all_item, Menu.NONE, getActivity()
				.getString(R.string.enqueue_all_new));
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.mark_all_read_item:
			manager.markAllItemsRead(getActivity());
			break;
		case R.id.enqueue_all_item:
			manager.enqueueAllNewItems(getActivity());
			break;
		default:
			return false;
		}
		return true;
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		View headerView = getLayoutInflater(savedInstanceState).inflate(
				R.layout.feeditemlist_header, null);
		TextView headerTitle = (TextView) headerView
				.findViewById(R.id.txtvHeaderTitle);
		headerTitle.setText(R.string.new_label);
		headerView.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {

			}
		});
		getListView().addHeaderView(headerView);
		super.onViewCreated(view, savedInstanceState);
	}

}
