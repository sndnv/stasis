package stasis.client_android.utils

import android.Manifest
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import stasis.client_android.BuildConfig
import stasis.client_android.lib.ops.Operation

object Permissions {
    fun Activity?.needsExtraPermissions(): Boolean =
        (this?.needsPermissions() ?: false) || needsToBeExternalStorageManager()

    fun Activity.requestMissingPermissions() {
        if (needsPermissions()) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions,
                PermissionsRequestCode
            )
        }

        if (needsToBeExternalStorageManager()) {
            val uri = "package:${BuildConfig.APPLICATION_ID}".toUri()
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
            startActivity(intent)
        }
    }

    fun Application.getOperationRestrictions(ignoreRestrictions: Boolean): List<Operation.Restriction> =
        if (ignoreRestrictions) {
            emptyList()
        } else {
            getSystemService(ConnectivityManager::class.java).getOperationRestrictions()
        }

    fun ConnectivityManager.getOperationRestrictions(): List<Operation.Restriction> {
        val networkRestriction = when (this.activeNetwork) {
            null -> Operation.Restriction.NoConnection
            else ->
                if (this.isActiveNetworkMetered) {
                    Operation.Restriction.LimitedNetwork
                } else {
                    null
                }
        }

        return listOfNotNull(networkRestriction)
    }

    val requiredPermissions: Array<String>
        get() =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                    Manifest.permission.POST_NOTIFICATIONS
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE
                )
            } else {
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }

    fun Activity.getRequiredPermissionsStatus(): List<Pair<String, Boolean>> {
        val permissions = requiredPermissions.map { permission ->
            val result = ContextCompat.checkSelfPermission(this, permission)
            permission to (result == PackageManager.PERMISSION_GRANTED)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            permissions + (Manifest.permission.MANAGE_EXTERNAL_STORAGE to Environment.isExternalStorageManager())
        } else {
            permissions
        }
    }

    private fun Activity.needsPermissions(): Boolean {
        val granted = requiredPermissions.fold(true) { granted, permission ->
            val result = ContextCompat.checkSelfPermission(this, permission)
            granted && result == PackageManager.PERMISSION_GRANTED
        }

        return !granted
    }

    private fun needsToBeExternalStorageManager(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()

    private const val PermissionsRequestCode: Int = 1
}
