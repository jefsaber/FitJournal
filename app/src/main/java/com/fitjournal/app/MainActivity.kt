package com.fitjournal.app

import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.fitjournal.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val host = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navigation = host.navController
        val mainDestinations = setOf(
            R.id.exercisesFragment, R.id.workoutsFragment, R.id.progressFragment, R.id.settingsFragment
        )
        binding.bottomNav.setupWithNavController(navigation)
        binding.mainToolbar.setNavigationOnClickListener { navigation.navigateUp() }

        // Consume insets once in the shared shell, including the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            binding.bottomNav.isVisible = navigation.currentDestination?.id in mainDestinations &&
                !insets.isVisible(WindowInsetsCompat.Type.ime())
            WindowInsetsCompat.CONSUMED
        }
        navigation.addOnDestinationChangedListener { _, destination, _ ->
            val mainScreen = destination.id in mainDestinations
            binding.mainToolbar.navigationIcon = if (mainScreen) null else
                AppCompatResources.getDrawable(this, R.drawable.ic_back)
            binding.mainToolbar.navigationContentDescription = getString(R.string.navigate_back)
            binding.mainToolbar.logo = if (mainScreen)
                AppCompatResources.getDrawable(this, R.drawable.ic_brand) else null
            binding.bottomNav.isVisible = mainScreen
            ViewCompat.requestApplyInsets(binding.root)
        }
        ViewCompat.requestApplyInsets(binding.root)

        lifecycleScope.launch {
            try {
                (application as FitJournalApplication).container.seedImporter.importOnce()
            } catch (error: Exception) {
                Log.e("FitJournal.Seed", "Unable to initialize exercise catalogue", error)
            }
        }
    }
}
