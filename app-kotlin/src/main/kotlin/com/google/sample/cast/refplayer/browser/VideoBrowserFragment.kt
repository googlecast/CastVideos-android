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

import com.google.android.gms.cast.MediaInfo
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.View
import android.view.LayoutInflater
import com.google.sample.cast.refplayer.R
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.SessionManagerListener
import android.os.Bundle
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.ImageButton
import androidx.core.app.ActivityOptionsCompat
import android.content.Intent
import androidx.core.app.ActivityCompat
import androidx.core.util.Pair
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import com.google.sample.cast.refplayer.mediaplayer.LocalPlayerActivity
import com.google.sample.cast.refplayer.utils.Utils
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/**
 * A fragment to host a list view of the video catalog.
 */
class VideoBrowserFragment : Fragment(), VideoListAdapter.ItemClickListener,
    LoaderManager.LoaderCallbacks<List<MediaInfo>?> {
    private var mRecyclerView: RecyclerView? = null
    private var mAdapter: VideoListAdapter? = null
    private var mEmptyView: View? = null
    private var mLoadingView: View? = null
    private val mSessionManagerListener: SessionManagerListener<CastSession> = MySessionManagerListener()
    private val  vidBrowserExecutor: Executor = Executors.newCachedThreadPool();

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle? ): View? {
        return inflater.inflate(R.layout.video_browser_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        mRecyclerView = requireView().findViewById<View>(R.id.list) as RecyclerView
        mEmptyView = requireView().findViewById(R.id.empty_view)
        mLoadingView = requireView().findViewById(R.id.progress_indicator)
        val layoutManager = LinearLayoutManager(activity)
        layoutManager.orientation = LinearLayoutManager.VERTICAL
        mRecyclerView!!.layoutManager = layoutManager
        mAdapter = VideoListAdapter(this, context)
        mRecyclerView!!.adapter = mAdapter
        LoaderManager.getInstance(this).initLoader(0, null, this)
    }

    override fun itemClicked(view: View?, item: MediaInfo?, position: Int) {
        if (view is ImageButton) {
            Utils.showQueuePopup(activity, view, item)
        } else {
            val transitionName = getString(R.string.transition_image)
            val viewHolder = mRecyclerView!!.findViewHolderForLayoutPosition(position) as VideoListAdapter.ViewHolder?
            val imagePair = Pair
                .create(viewHolder!!.imageView as View, transitionName)
            val options = ActivityOptionsCompat
                .makeSceneTransitionAnimation(requireActivity(), imagePair)
            val intent = Intent(activity, LocalPlayerActivity::class.java)
            intent.putExtra("media", item)
            intent.putExtra("shouldStart", false)
            ActivityCompat.startActivity(requireActivity(), intent, options.toBundle())
        }
    }

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<List<MediaInfo>?> {
        return VideoItemLoader(activity, CATALOG_URL)
    }

    override fun onLoadFinished(loader: Loader<List<MediaInfo>?>, data: List<MediaInfo>?) {
        mAdapter!!.setData(data)
        mLoadingView!!.visibility = View.GONE
        mEmptyView!!.visibility = if (null == data || data.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onLoaderReset(loader: Loader<List<MediaInfo>?>) {
        mAdapter!!.setData(null)
    }

    override fun onStart() {
        CastContext.getSharedInstance(requireContext(),vidBrowserExecutor).result.sessionManager
            .addSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        super.onStart()
    }

    override fun onStop() {
        CastContext.getSharedInstance(requireContext(),vidBrowserExecutor).result.sessionManager
            .removeSessionManagerListener(mSessionManagerListener, CastSession::class.java)
        super.onStop()
    }

    private inner class MySessionManagerListener : SessionManagerListener<CastSession> {
        override fun onSessionEnded(session: CastSession, error: Int) {
            mAdapter!!.notifyDataSetChanged()
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            mAdapter!!.notifyDataSetChanged()
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            mAdapter!!.notifyDataSetChanged()
        }

        override fun onSessionStarting(session: CastSession) {}
        override fun onSessionStartFailed(session: CastSession, error: Int) {}
        override fun onSessionEnding(session: CastSession) {}
        override fun onSessionResuming(session: CastSession, sessionId: String) {}
        override fun onSessionResumeFailed(session: CastSession, error: Int) {}
        override fun onSessionSuspended(session: CastSession, reason: Int) {}
    }

    companion object {
        private const val TAG = "VideoBrowserFragment"
        private const val CATALOG_URL =
            "https://commondatastorage.googleapis.com/gtv-videos-bucket/CastVideos/f.json"
    }
}