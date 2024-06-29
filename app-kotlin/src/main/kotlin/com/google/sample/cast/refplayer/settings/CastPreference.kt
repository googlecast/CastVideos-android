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
package com.google.sample.cast.refplayer.settings

import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.utils.Utils


/**
 * An Preference Activity.
 */
class CastPreference constructor() : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportFragmentManager.beginTransaction().replace(android.R.id.content,  CastPreference.MyPreferenceFragment()).commit();
    }

     class MyPreferenceFragment : PreferenceFragmentCompat(), OnSharedPreferenceChangeListener  {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
        }

         override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
             setPreferencesFromResource(R.xml.application_preference, rootKey)

             val versionPref: EditTextPreference? = findPreference("app_version") as EditTextPreference?
             versionPref?.setTitle(getString(R.string.version, Utils.getAppVersionName(requireActivity().applicationContext)))
             preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)

         }

         override fun onSharedPreferenceChanged(
             sharedPreferences: SharedPreferences,
             key: String?
         ) {
             // Handle volume and caption preferences.
         }
     }
}