package de.danoeh.antennapod.adapter;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.miroguide.model.MiroChannel;

public class MiroChannellistAdapter extends ArrayAdapter<MiroChannel> {

	public MiroChannellistAdapter(Context context, int textViewResourceId,
			List<MiroChannel> objects) {
		super(context, textViewResourceId, objects);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		Holder holder;
		MiroChannel channel = getItem(position);

		// Inflate Layout
		if (convertView == null) {
			holder = new Holder();
			LayoutInflater inflater = (LayoutInflater) getContext()
					.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

			convertView = inflater.inflate(R.layout.miro_channellist_item, null);
			holder.title = (TextView) convertView.findViewById(R.id.txtvTitle);
			holder.cover = (ImageView) convertView
					.findViewById(R.id.imgvChannelimage);
			

			convertView.setTag(holder);
		} else {
			holder = (Holder) convertView.getTag();
		}
		
		holder.cover.setVisibility(View.GONE);
		holder.title.setText(channel.getName());
		
		return convertView;
	}

	static class Holder {
		ImageView cover;
		TextView title;
	}

	

}
