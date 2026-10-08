package dev.autofilld
import dev.autofilld.R
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import dev.autofilld.data.Profile
import dev.autofilld.databinding.ActivityMainBinding
import kotlinx.coroutines.runBlocking

class MainActivity : Activity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenSettings.setOnClickListener {
            openAutofillSettings()
        }

        binding.btnSaveProfile.setOnClickListener {
            saveProfile()
        }
    }

    private fun openAutofillSettings() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                data = android.net.Uri.parse("package:$packageName")
            }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(Intent("android.settings.AUTOFILL_SETTINGS"))
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open autofill settings", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
        loadProfile()
    }

    private fun updateServiceStatus() {
        val afm = getSystemService(AutofillManager::class.java)
        val isEnabled = afm != null && afm.hasEnabledAutofillServices()

        if (isEnabled) {
            binding.statusText.text = getString(R.string.status_enabled)
        } else {
            binding.statusText.text = getString(R.string.status_disabled)
        }
    }

    private fun loadProfile() {
        val app = application as? App ?: return
        app.ioExecutor.execute {
            val profile = runBlocking { app.profileRepository.getProfile() }
            runOnUiThread {
                binding.editFullName.setText(profile.fullName)
                binding.editEmail.setText(profile.email)
                binding.editPhone.setText(profile.phone)
                binding.editAddrLine1.setText(profile.addrLine1)
                binding.editAddrLine2.setText(profile.addrLine2)
                binding.editCity.setText(profile.city)
                binding.editState.setText(profile.state)
                binding.editPostalCode.setText(profile.postalCode)
                binding.editCountry.setText(profile.country)
            }
        }
    }

    private fun saveProfile() {
        val app = application as? App ?: return
        val updated = Profile(
            fullName = binding.editFullName.text.toString().trim(),
            email = binding.editEmail.text.toString().trim(),
            phone = binding.editPhone.text.toString().trim(),
            addrLine1 = binding.editAddrLine1.text.toString().trim(),
            addrLine2 = binding.editAddrLine2.text.toString().trim(),
            city = binding.editCity.text.toString().trim(),
            state = binding.editState.text.toString().trim(),
            postalCode = binding.editPostalCode.text.toString().trim(),
            country = binding.editCountry.text.toString().trim()
        )

        app.ioExecutor.execute {
            runBlocking { app.profileRepository.saveProfile(updated) }
            runOnUiThread {
                Toast.makeText(this, R.string.msg_profile_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }
}
