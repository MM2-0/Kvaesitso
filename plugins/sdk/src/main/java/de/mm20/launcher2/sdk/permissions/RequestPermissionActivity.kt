package de.mm20.launcher2.sdk.permissions

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import de.mm20.launcher2.sdk.R
import de.mm20.launcher2.sdk.databinding.ActivityRequestPermissionBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

internal class RequestPermissionActivity: Activity() {

    private lateinit var binding: ActivityRequestPermissionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val callingPackage = callingPackage ?: throw IllegalArgumentException("No calling package")

        val callingPackageInfo = try {
            packageManager.getApplicationInfo(callingPackage, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalArgumentException("Invalid calling package")
        }

        val myPackageInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            throw IllegalStateException("Invalid package")
        }


        val permissionManager = PluginPermissionManager(this)

        val hasPermission = runBlocking {
            permissionManager.hasPermission(callingPackage).first()
        }

        if (hasPermission) {
            finish()
            return
        }

        binding = ActivityRequestPermissionBinding.inflate(LayoutInflater.from(this))
        val text = getString(
            R.string.request_permission_message,
            callingPackageInfo.loadLabel(packageManager),
            myPackageInfo.loadLabel(packageManager)
        )
        binding.textView.text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY)
        setContentView(binding.root)
        binding.grantButton.setOnClickListener {
            permissionManager.grantPermission(callingPackage)
            setResult(RESULT_OK)
            finish()
        }
        binding.denyButton.setOnClickListener {
            permissionManager.revokePermission(callingPackage)
            setResult(RESULT_CANCELED)
            finish()
        }
    }
}