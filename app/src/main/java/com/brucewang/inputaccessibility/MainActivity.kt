package com.brucewang.inputaccessibility

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.brucewang.inputaccessibility.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenSettings.setOnClickListener {
            openAccessibilitySettings()
        }

        updateServiceStatus()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    private fun updateServiceStatus() {
        val accessibilityEnabled = isAccessibilityServiceEnabled()

        // 检查无障碍服务
        if (accessibilityEnabled) {
            binding.tvServiceStatus.text = "✓ 无障碍服务已启用"
            binding.tvStatus.text = "✓ 服务已就绪"
        } else {
            binding.tvServiceStatus.text = "✗ 无障碍服务未启用"
            binding.tvStatus.text = "需要启用无障碍服务"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${InputAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return enabledServices?.contains(service) == true
    }
}

