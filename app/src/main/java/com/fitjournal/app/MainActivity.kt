package com.fitjournal.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.util.Log
import kotlinx.coroutines.launch
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.fitjournal.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        binding.bottomNav.setupWithNavController(host.navController)
        // Non-blocking, idempotent JSON seed. Run once on first launch and safely retry on failure.
        lifecycleScope.launch {
            try {
                (application as FitJournalApplication).container.seedImporter.importOnce()
            } catch (error: Exception) {
                Log.e("FitJournal.Seed", "Unable to initialize exercise catalogue", error)
            }
        }
    }
}
