/*
 * Copyright 2022 Google LLC. All Rights Reserved.
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
package com.google.sample.cast.refplayer.browser

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.View
import android.view.LayoutInflater
import com.google.sample.cast.refplayer.R
import com.google.android.gms.cast.framework.CastContext
import com.android.volley.toolbox.NetworkImageView
import android.widget.TextView
import com.android.volley.toolbox.ImageLoader
import android.widget.ImageView
import com.google.android.gms.cast.*
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * An [ArrayAdapter] to populate the list of videos.
 */
class VideoListAdapter(private val mClickListener: ItemClickListener, context: Context?) :
    RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {
    private val mAppContext: Context
    private var videos: List<MediaInfo>? = null
    private val  castExecutor: Executor = Executors.newSingleThreadExecutor();

    init {
        mAppContext = context!!.applicationContext
    }

    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val context = viewGroup.context
        val parent = LayoutInflater.from(context).inflate(R.layout.browse_row, viewGroup, false)
        return ViewHolder.newInstance(parent)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = videos!![position]
        val mm = item.metadata
        viewHolder.setTitle(mm!!.getString(MediaMetadata.KEY_TITLE))
        viewHolder.setDescription(mm.getString(MediaMetadata.KEY_SUBTITLE))
        viewHolder.setImage(mm.images[0].url.toString(), mAppContext)
        viewHolder.mMenu.setOnClickListener { view ->
            mClickListener.itemClicked(
                view,
                item,
                position
            )
        }
        viewHolder.mImgView.setOnClickListener { view ->
            mClickListener.itemClicked(
                view,
                item,
                position
            )
        }
        viewHolder.mTextContainer.setOnClickListener { view ->
            mClickListener.itemClicked(
                view,
                item,
                position
            )
        }
        val castSession = CastContext.getSharedInstance(mAppContext,castExecutor)
            .result
            .sessionManager
            .currentCastSession
        viewHolder.mMenu.visibility =
            if (castSession != null && castSession.isConnected) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int {
        return if (videos == null) 0 else videos!!.size
    }

    /**
     * A [RecyclerView.ViewHolder] that displays a single video in
     * the video list.
     */
    class ViewHolder private constructor(
        private val mParent: View,
        val mImgView: NetworkImageView,
        val mTextContainer: View,
        private val mTitleView: TextView,
        private val mDescriptionView: TextView,
        val mMenu: View
    ) : RecyclerView.ViewHolder(mParent) {
        private var mImageLoader: ImageLoader? = null
        fun setTitle(title: String?) {
            mTitleView.text = title
        }

        fun setDescription(description: String?) {
            mDescriptionView.text = description
        }

        fun setImage(imgUrl: String?, context: Context) {
            mImageLoader = CustomVolleyRequest.Companion.getInstance(context!!)?.imageLoader
            mImageLoader!![imgUrl, ImageLoader.getImageListener(mImgView, 0, 0)]
            mImgView.setImageUrl(imgUrl, mImageLoader)
        }

        fun setOnClickListener(listener: View.OnClickListener?) {
            mParent.setOnClickListener(listener)
        }

        val imageView: ImageView
            get() = mImgView

        companion object {
            fun newInstance(parent: View): ViewHolder {
                val imgView = parent.findViewById<View>(R.id.imageView1) as NetworkImageView
                val titleView = parent.findViewById<View>(R.id.textView1) as TextView
                val descriptionView = parent.findViewById<View>(R.id.textView2) as TextView
                val menu = parent.findViewById<View>(R.id.menu)
                val textContainer = parent.findViewById<View>(R.id.text_container)
                return ViewHolder(parent, imgView, textContainer, titleView, descriptionView, menu)
            }
        }
    }

    fun setData(data: List<MediaInfo>?) {
        videos = data
        notifyDataSetChanged()
    }

    /**
     * A listener called when an item is clicked in the video list.
     */
    interface ItemClickListener {
        fun itemClicked(view: View?, item: MediaInfo?, position: Int)
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(position)
    }

    companion object {
        private const val ASPECT_RATIO = 9f / 16f
    }
}