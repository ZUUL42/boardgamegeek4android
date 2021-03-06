package com.boardgamegeek.ui;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.util.ArrayMap;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.entities.CollectionItemEntity;
import com.boardgamegeek.events.CollectionStatusChangedEvent;
import com.boardgamegeek.io.Adapter;
import com.boardgamegeek.io.BggService;
import com.boardgamegeek.io.model.CollectionItem;
import com.boardgamegeek.io.model.CollectionResponse;
import com.boardgamegeek.mappers.CollectionItemMapper;
import com.boardgamegeek.ui.loader.BggLoader;
import com.boardgamegeek.ui.loader.SafeResponse;
import com.boardgamegeek.util.RandomUtils;
import com.boardgamegeek.util.UIUtils;
import com.crashlytics.android.answers.Answers;
import com.crashlytics.android.answers.CustomEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

import icepick.Icepick;
import icepick.State;
import se.emilsjolander.stickylistheaders.StickyListHeadersAdapter;
import timber.log.Timber;

public class BuddyCollectionFragment extends StickyHeaderListFragment implements LoaderManager.LoaderCallbacks<SafeResponse<CollectionResponse>> {
	private static final String KEY_BUDDY_NAME = "BUDDY_NAME";
	private static final int BUDDY_GAMES_LOADER_ID = 1;

	private BuddyCollectionAdapter adapter;
	private SubMenu subMenu;
	private String buddyName;
	@State String statusValue;
	@State String statusLabel;
	private String[] statusValues;
	private String[] statusEntries;

	public static BuddyCollectionFragment newInstance(String username) {
		Bundle args = new Bundle();
		args.putString(KEY_BUDDY_NAME, username);
		BuddyCollectionFragment fragment = new BuddyCollectionFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		buddyName = getArguments().getString(KEY_BUDDY_NAME);

		if (TextUtils.isEmpty(buddyName)) {
			Timber.w("Missing buddy name.");
			return;
		}

		statusEntries = getResources().getStringArray(R.array.pref_sync_status_entries);
		statusValues = getResources().getStringArray(R.array.pref_sync_status_values);

		setHasOptionsMenu(true);
		Icepick.restoreInstanceState(this, savedInstanceState);
		if (TextUtils.isEmpty(statusValue)) {
			statusValue = statusValues[0];
		}
		if (TextUtils.isEmpty(statusLabel)) {
			statusLabel = statusEntries[0];
		}
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setEmptyText(getString(R.string.empty_buddy_collection));
		reload();
	}

	@Override
	public void onListItemClick(View convertView, int position, long id) {
		super.onListItemClick(convertView, position, id);
		int gameId = (int) convertView.getTag(R.id.id);
		String gameName = (String) convertView.getTag(R.id.game_name);
		GameActivity.start(getContext(), gameId, gameName);
	}

	@Override
	public void onSaveInstanceState(@NonNull Bundle outState) {
		super.onSaveInstanceState(outState);
		Icepick.saveInstanceState(this, outState);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.buddy_collection, menu);
		MenuItem mi = menu.findItem(R.id.menu_collection_status);
		if (mi != null) {
			subMenu = mi.getSubMenu();
			if (subMenu != null) {
				for (int i = 0; i < statusEntries.length; i++) {
					subMenu.add(1, Menu.FIRST + i, i, statusEntries[i]);
				}
				subMenu.setGroupCheckable(1, true, true);
			}
		}
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public void onPrepareOptionsMenu(Menu menu) {
		UIUtils.showMenuItem(menu, R.id.menu_collection_random_game, adapter != null && adapter.getCount() > 0);
		// check the proper submenu item
		if (subMenu != null) {
			for (int i = 0; i < subMenu.size(); i++) {
				MenuItem smi = subMenu.getItem(i);
				if (smi.getTitle().equals(statusLabel)) {
					smi.setChecked(true);
					break;
				}
			}
		}
		super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		String status = "";
		int i = id - Menu.FIRST;
		if (i >= 0 && i < statusValues.length) {
			status = statusValues[i];
		} else if (id == R.id.menu_collection_random_game) {
			final int index = RandomUtils.getRandom().nextInt(adapter.getCount());
			if (index < adapter.getCount()) {
				CollectionItemEntity ci = adapter.getItem(index);
				if (ci != null) {
					GameActivity.start(getContext(), ci.getGameId(), ci.getGameName(), ci.getThumbnailUrl());
					return true;
				}
				return false;
			}
		}

		if (!TextUtils.isEmpty(status) && !status.equals(statusValue)) {
			statusValue = status;
			statusLabel = statusEntries[i];
			Answers.getInstance().logCustom(new CustomEvent("Filter")
				.putCustomAttribute("contentType", "BuddyCollection")
				.putCustomAttribute("filterType", status));
			reload();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void reload() {
		EventBus.getDefault().postSticky(new CollectionStatusChangedEvent(statusLabel));
		if (adapter != null) {
			adapter.clear();
		}
		getActivity().invalidateOptionsMenu();
		setListShown(false);
		getLoaderManager().restartLoader(BUDDY_GAMES_LOADER_ID, null, this);
	}

	@Override
	public Loader<SafeResponse<CollectionResponse>> onCreateLoader(int id, Bundle data) {
		return new BuddyGamesLoader(getActivity(), buddyName, statusValue);
	}

	@Override
	public void onLoadFinished(Loader<SafeResponse<CollectionResponse>> loader, SafeResponse<CollectionResponse> data) {
		if (getActivity() == null) return;
		ArrayList<CollectionItemEntity> list = new ArrayList<>();
		if (data != null &&
			data.getBody() != null &&
			data.getBody().items != null) {
			CollectionItemMapper mapper = new CollectionItemMapper();
			for (CollectionItem item : data.getBody().items) {
				list.add(mapper.map(item).getFirst());
			}
		}

		if (adapter == null) {
			adapter = new BuddyCollectionAdapter(getActivity(), list);
			setListAdapter(adapter);
		} else {
			adapter.setCollection(list);
		}
		adapter.notifyDataSetChanged();
		getActivity().invalidateOptionsMenu();

		if (data == null) {
			setEmptyText(getString(R.string.empty_buddy_collection));
		} else if (data.hasError()) {
			setEmptyText(data.getErrorMessage());
		} else if (data.getBody().totalitems == 0) {
			setEmptyText(getString(R.string.empty_buddy_collection));
		} else {
			if (isResumed()) {
				setListShown(true);
			} else {
				setListShownNoAnimation(true);
			}
			restoreScrollState();
		}
	}

	@Override
	public void onLoaderReset(Loader<SafeResponse<CollectionResponse>> loader) {
	}

	private static class BuddyGamesLoader extends BggLoader<SafeResponse<CollectionResponse>> {
		private final BggService bggService;
		private final String username;
		private final ArrayMap<String, String> options;

		public BuddyGamesLoader(Context context, String username, String status) {
			super(context);
			bggService = Adapter.createForXml();
			this.username = username;
			options = new ArrayMap<>();
			options.put(status, "1");
			options.put(BggService.COLLECTION_QUERY_KEY_BRIEF, "1");
		}

		@Override
		public SafeResponse<CollectionResponse> loadInBackground() {
			return new SafeResponse<>(bggService.collection(username, options));
		}
	}

	public static class BuddyCollectionAdapter extends ArrayAdapter<CollectionItemEntity> implements StickyListHeadersAdapter {
		private final LayoutInflater inflater;

		public BuddyCollectionAdapter(Activity activity, List<CollectionItemEntity> collection) {
			super(activity, R.layout.row_text_2, collection);
			inflater = activity.getLayoutInflater();
		}

		public void setCollection(List<CollectionItemEntity> games) {
			clear();
			addAll(games);
			notifyDataSetChanged();
		}

		@NonNull
		@Override
		public View getView(int position, View convertView, @NonNull ViewGroup parent) {
			BuddyGameViewHolder holder;
			if (convertView == null) {
				convertView = inflater.inflate(R.layout.row_text_2, parent, false);
				holder = new BuddyGameViewHolder(convertView);
				convertView.setTag(holder);
			} else {
				holder = (BuddyGameViewHolder) convertView.getTag();
			}

			CollectionItemEntity item;
			try {
				item = getItem(position);
			} catch (ArrayIndexOutOfBoundsException e) {
				return convertView;
			}
			if (item != null) {
				holder.title.setText(item.getGameName());
				holder.text.setText(String.valueOf(item.getGameId()));

				convertView.setTag(R.id.id, item.getGameId());
				convertView.setTag(R.id.game_name, item.getGameName());
			}
			return convertView;
		}

		@Override
		public View getHeaderView(int position, View convertView, ViewGroup parent) {
			HeaderViewHolder holder;
			if (convertView == null) {
				holder = new HeaderViewHolder();
				convertView = inflater.inflate(R.layout.row_header, parent, false);
				holder.text = convertView.findViewById(android.R.id.title);
				convertView.setTag(holder);
			} else {
				holder = (HeaderViewHolder) convertView.getTag();
			}
			holder.text.setText(getHeaderText(position));
			return convertView;
		}

		@Override
		public long getHeaderId(int position) {
			return getHeaderText(position).charAt(0);
		}

		private String getHeaderText(int position) {
			if (position < getCount()) {
				CollectionItemEntity item = getItem(position);
				if (item != null) {
					return item.getSortName().substring(0, 1);
				}
			}
			return "-";
		}

		class BuddyGameViewHolder {
			public final TextView title;
			public final TextView text;

			public BuddyGameViewHolder(View view) {
				title = view.findViewById(android.R.id.title);
				text = view.findViewById(android.R.id.text1);
			}
		}

		class HeaderViewHolder {
			TextView text;
		}
	}
}
