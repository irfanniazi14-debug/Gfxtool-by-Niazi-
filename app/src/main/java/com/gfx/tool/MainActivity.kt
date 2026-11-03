package com.gfx.tool

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gfx.tool.databinding.ActivityMainBinding
import java.io.File

/**
 * GFX Tool - Resolution & FPS changer
 *
 * This writes a UserCustom.ini config file into the selected game's
 * Android/data/<package>/files/ directory. This is the same mechanism
 * used by common "GFX Tool" apps on the Play Store - it only edits a
 * config file the game reads at startup. It does NOT touch game memory,
 * does NOT inject code, and does NOT bypass anti-cheat.
 *
 * NOTE: Exact .ini key names can change between game versions. If the
 * game ignores the file, check the game's own in-game graphics settings
 * to confirm which keys/values it currently expects, and adjust
 * buildIniContent() below.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Known package names for each game variant
    private val gamePackages = linkedMapOf(
        "PUBG Mobile (Global)" to "com.tencent.ig",
        "PUBG Mobile (Korea)" to "com.pubg.krmobile",
        "PUBG Mobile (Vietnam)" to "com.vng.pubgmobile",
        "PUBG Mobile (Taiwan)" to "com.rekoo.pubgm",
        "BGMI" to "com.pubg.imobile"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        updatePermissionStatus()

        binding.btnGrantPermission.setOnClickListener { requestStoragePermission() }
        binding.btnApply.setOnClickListener { applySettings() }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun setupSpinners() {
        val gameNames = gamePackages.keys.toList()
        binding.spinnerGame.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, gameNames
        )

        val graphicsLevels = listOf("Smooth", "Balanced", "HD", "HDR", "Ultra HDR")
        binding.spinnerGraphics.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, graphicsLevels
        )
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // handled by runtime permission on older versions if needed
        }
    }

    private fun updatePermissionStatus() {
        val granted = hasStoragePermission()
        binding.tvPermissionStatus.text =
            if (granted) "Storage permission: Granted"
            else "Storage permission: Not granted"
        binding.btnGrantPermission.isEnabled = !granted
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun applySettings() {
        if (!hasStoragePermission()) {
            Toast.makeText(this, "Please grant storage permission first", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedGameName = binding.spinnerGame.selectedItem as String
        val pkg = gamePackages[selectedGameName] ?: return

        val fps = when {
            binding.rb40.isChecked -> 40
            binding.rb60.isChecked -> 60
            binding.rb90.isChecked -> 90
            binding.rb120.isChecked -> 120
            else -> 60
        }

        val resolution = when {
            binding.rbHD.isChecked -> "HD"
            binding.rbFHD.isChecked -> "HDR"
            binding.rbUHD.isChecked -> "UHD"
            else -> "HDR"
        }

        val graphics = binding.spinnerGraphics.selectedItem as String

        val targetDir = File(
            Environment.getExternalStorageDirectory(),
            "Android/data/$pkg/files"
        )

        if (!targetDir.exists()) {
            val made = targetDir.mkdirs()
            if (!made) {
                binding.tvResult.text =
                    "Could not find/create folder for $selectedGameName.\n" +
                    "Make sure the game is installed and has been opened at least once."
                return
            }
        }

        val iniFile = File(targetDir, "UserCustom.ini")
        val content = buildIniContent(resolution, fps, graphics)

        try {
            iniFile.writeText(content)
            binding.tvResult.text =
                "Applied: $resolution / ${fps}FPS / $graphics\n" +
                "Saved to: ${iniFile.absolutePath}\n\n" +
                "Fully close the game (remove from recents) and reopen it for changes to take effect."
        } catch (e: Exception) {
            binding.tvResult.text = "Failed to write file: ${e.message}"
        }
    }

    /**
     * Builds the UserCustom.ini content.
     * These keys follow the commonly documented format used by GFX tools.
     * If your game version does not pick these up, compare against the
     * values the game itself writes after you change settings in-game,
     * and update the keys below to match.
     */
    private fun buildIniContent(resolution: String, fps: Int, graphics: String): String {
        return """
            [SystemSettings]
            Resolution=$resolution
            MaxFPS=$fps
            GraphicsQuality=$graphics
            AntiAliasing=Off
            ShadowQuality=Off
            StyleQuality=Smooth
        """.trimIndent()
    }
}
