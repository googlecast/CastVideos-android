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

package com.google.sample.cast.refplayer.browser;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.sample.cast.refplayer.R;

import com.androidquery.AQuery;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

/**
 * An {@link ArrayAdapter} to populate the list of videos.
 */
public class VideoListAdapter extends RecyclerView.Adapter<VideoListAdapter.ViewHolder> {

    private static final float ASPECT_RATIO = 9f / 16f;
    private final ItemClickListener mClickListener;
    private final Context mAppContext;
    private List<MediaInfo> videos;

    public VideoListAdapter(ItemClickListener clickListener, Context context) {
        mClickListener = clickListener;
        mAppContext = context.getApplicationContext();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        Context context = viewGroup.getContext();
        View parent = LayoutInflater.from(context).inflate(R.layout.browse_row, viewGroup, false);
        return ViewHolder.newInstance(parent);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, final int position) {
        final MediaInfo item = videos.get(position);
        MediaMetadata mm = item.getMetadata();
        viewHolder.setTitle(mm.getString(MediaMetadata.KEY_TITLE));
        viewHolder.setDescription(mm.getString(MediaMetadata.KEY_SUBTITLE));
        viewHolder.setImage(mm.getImages().get(0).getUrl().toString());

        viewHolder.mMenu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mClickListener.itemClicked(view, item, position);
            }
        });
        viewHolder.mImgView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mClickListener.itemClicked(view, item, position);
            }
        });

        viewHolder.mTextContainer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mClickListener.itemClicked(view, item, position);
            }
        });
        CastSession castSession = CastContext.getSharedInstance(mAppContext).getSessionManager()
                .getCurrentCastSession();
        viewHolder.mMenu.setVisibility(
                (castSession != null && castSession.isConnected()) ? View.VISIBLE : View.GONE);
    }

    @Override
    public int getItemCount() {
        return videos == null ? 0 : videos.size();
    }

    /**
     * A {@link android.support.v7.widget.RecyclerView.ViewHolder} that displays a single video in
     * the video list.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final View mParent;
        private final View mMenu;
        private final View mTextContainer;
        private AQuery mAquery;
        private TextView mTitleView;
        private TextView mDescriptionView;
        private ImageView mImgView;

        public static ViewHolder newInstance(View parent) {
            ImageView imgView = (ImageView) parent.findViewById(R.id.imageView1);
            TextView titleView = (TextView) parent.findViewById(R.id.textView1);
            TextView descriptionView = (TextView) parent.findViewById(R.id.textView2);
            View menu = parent.findViewById(R.id.menu);
            View textContainer = parent.findViewById(R.id.text_container);
            AQuery aQuery = new AQuery(parent);
            return new ViewHolder(parent, imgView, textContainer, titleView, descriptionView, menu,
                    aQuery);
        }

        private ViewHolder(View parent, ImageView imgView, View textContainer, TextView titleView,
                TextView descriptionView, View menu, AQuery aQuery) {
            super(parent);
            mParent = parent;
            mImgView = imgView;
            mTextContainer = textContainer;
            mMenu = menu;
            mTitleView = titleView;
            mDescriptionView = descriptionView;
            mAquery = aQuery;
        }

        public void setTitle(String title) {
            mTitleView.setText(title);
        }

        public void setDescription(String description) {
            mDescriptionView.setText(description);
        }

        public void setImage(String imgUrl) {
            mAquery.id(mImgView).width(114).image(imgUrl,
                    true, true, 0, R.drawable.default_video, null, 0, ASPECT_RATIO);
        }

        public void setOnClickListener(View.OnClickListener listener) {
            mParent.setOnClickListener(listener);
        }

        public ImageView getImageView() {
            return mImgView;
        }
    }

    public void setData(List<MediaInfo> data) {
        videos = data;
        notifyDataSetChanged();
    }

    /**
     * A listener called when an item is clicked in the video list.
     */
    public interface ItemClickListener {

        void itemClicked(View view, MediaInfo item, int position);
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(position);
    }
}
