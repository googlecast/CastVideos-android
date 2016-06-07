/*
 * Copyright (C) 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.sample.cast.refplayer.queue.ui;

import com.google.android.gms.cast.MediaQueueItem;
import com.google.android.gms.cast.MediaStatus;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.sample.cast.refplayer.R;
import com.google.sample.cast.refplayer.expandedcontrols.ExpandedControlsActivity;
import com.google.sample.cast.refplayer.queue.QueueDataProvider;
import com.google.sample.cast.refplayer.utils.Utils;

import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/**
 * A fragment to show the list of queue items.
 */
public class QueueListViewFragment extends Fragment
        implements QueueListAdapter.OnStartDragListener {

    private static final String TAG = "QueueListViewFragment";
    private QueueDataProvider mProvider;
    private ItemTouchHelper mItemTouchHelper;

    public QueueListViewFragment() {
        super();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_recycler_list_view, container, false);
    }

    @Override
    public void onStartDrag(RecyclerView.ViewHolder viewHolder) {
        mItemTouchHelper.startDrag(viewHolder);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = (RecyclerView) getView().findViewById(R.id.recycler_view);
        mProvider = QueueDataProvider.getInstance(getContext());

        QueueListAdapter adapter = new QueueListAdapter(getActivity(), this);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));

        ItemTouchHelper.Callback callback = new QueueItemTouchHelperCallback(adapter);
        mItemTouchHelper = new ItemTouchHelper(callback);
        mItemTouchHelper.attachToRecyclerView(recyclerView);

        adapter.setEventListener(new QueueListAdapter.EventListener() {
            @Override
            public void onItemViewClicked(View view) {
                switch (view.getId()) {
                    case R.id.container:
                        Log.d(TAG, "onItemViewClicked() container "
                                + view.getTag(R.string.queue_tag_item));
                        onContainerClicked(view);
                        break;
                    case R.id.play_pause:
                        Log.d(TAG, "onItemViewClicked() play-pause "
                                + view.getTag(R.string.queue_tag_item));
                        onPlayPauseClicked(view);
                        break;
                    case R.id.play_upcoming:
                        mProvider.onUpcomingPlayClicked(view,
                                (MediaQueueItem) view.getTag(R.string.queue_tag_item));
                        break;
                    case R.id.stop_upcoming:
                        mProvider.onUpcomingStopClicked(view,
                                (MediaQueueItem) view.getTag(R.string.queue_tag_item));
                        break;
                }
            }
        });
    }

    private void onPlayPauseClicked(View view) {
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient != null) {
            remoteMediaClient.togglePlayback();
        }
    }

    private void onContainerClicked(View view) {
        RemoteMediaClient remoteMediaClient = getRemoteMediaClient();
        if (remoteMediaClient == null) {
            return;
        }
        MediaQueueItem item = (MediaQueueItem) view.getTag(R.string.queue_tag_item);
        if (mProvider.isQueueDetached()) {
            Log.d(TAG, "Is detached: itemId = " + item.getItemId());

            int currentPosition = mProvider.getPositionByItemId(item.getItemId());
            MediaQueueItem[] items = Utils.rebuildQueue(mProvider.getItems());
            remoteMediaClient.queueLoad(items, currentPosition,
                    MediaStatus.REPEAT_MODE_REPEAT_OFF, null);
        } else {
            int currentItemId = mProvider.getCurrentItemId();
            if (currentItemId == item.getItemId()) {
                // We selected the one that is currently playing so we take the user to the
                // full screen controller
                CastSession castSession = CastContext.getSharedInstance(
                        getContext().getApplicationContext())
                        .getSessionManager().getCurrentCastSession();
                if (castSession != null) {
                    Intent intent = new Intent(getActivity(), ExpandedControlsActivity.class);
                    startActivity(intent);
                }
            } else {
                // a different item in the queue was selected so we jump there
                remoteMediaClient.queueJumpToItem(item.getItemId(), null);
            }
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
    }

    private RemoteMediaClient getRemoteMediaClient() {
        CastSession castSession =
                CastContext.getSharedInstance(getContext()).getSessionManager()
                        .getCurrentCastSession();
        if (castSession != null && castSession.isConnected()) {
            return castSession.getRemoteMediaClient();
        }
        return null;
    }
}
