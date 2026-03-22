package com.consistencygridwallpaper.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.consistencygridwallpaper.R
import com.consistencygridwallpaper.storage.UserPrefs
import com.consistencygridwallpaper.workers.WallpaperWorker
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private lateinit var userPrefs: UserPrefs

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val root = inflater.inflate(R.layout.fragment_settings, container, false)
        userPrefs = UserPrefs(requireContext())

        val etToken: EditText = root.findViewById(R.id.et_token)
        val btnSave: Button = root.findViewById(R.id.btn_save_settings)
        val switchAuto: Switch = root.findViewById(R.id.switch_auto_update)

        etToken.setText(userPrefs.getToken())
        switchAuto.isChecked = userPrefs.isAutoUpdateEnabled()

        btnSave.setOnClickListener {
            val token = etToken.text.toString()
            if (token.isNotEmpty()) {
                userPrefs.saveToken(token)
                Toast.makeText(context, "Settings Saved ✅", Toast.LENGTH_SHORT).show()
            }
        }

        switchAuto.setOnCheckedChangeListener { _, isChecked ->
            userPrefs.setAutoUpdate(isChecked)
            if (isChecked) {
                scheduleWork()
            } else {
                cancelWork()
            }
        }

        return root
    }

    private fun scheduleWork() {
        com.consistencygridwallpaper.workers.WorkScheduler.scheduleDailyUpdate(requireContext())
        Toast.makeText(context, "Daily Update Scheduled for 12 AM ✅", Toast.LENGTH_SHORT).show()
    }

    private fun cancelWork() {
        WorkManager.getInstance(requireContext()).cancelUniqueWork(com.consistencygridwallpaper.workers.WorkScheduler.TAG)
        Toast.makeText(context, "Daily Update Cancelled ❌", Toast.LENGTH_SHORT).show()
    }
}
