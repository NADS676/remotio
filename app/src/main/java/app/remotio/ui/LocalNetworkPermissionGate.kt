package app.remotio.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.remotio.R

/** From Android 17, an app needs the user's OK before it can reach devices on the home network. */
@Composable
fun LocalNetworkPermissionGate(content: @Composable () -> Unit) {
    if (Build.VERSION.SDK_INT < 37) {
        content()
        return
    }
    val permission = Manifest.permission.ACCESS_LOCAL_NETWORK
    val context = LocalContext.current
    val activity = LocalActivity.current
    fun isGranted() = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    var granted by remember { mutableStateOf(isGranted()) }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { result ->
        granted = result
        asked = true
    }
    // Catches the permission being granted from system settings while we were in the background.
    LifecycleResumeEffect(Unit) {
        granted = isGranted()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        if (!granted && !asked) launcher.launch(permission)
    }

    if (granted) {
        content()
        return
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(painterResource(R.drawable.ic_tv), contentDescription = null, Modifier.size(48.dp))
            Text(stringResource(R.string.permission_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.permission_body), textAlign = TextAlign.Center)
            Button(onClick = {
                // After a second "Don't allow" Android stops showing the prompt, so send the user to settings.
                if (asked && activity?.shouldShowRequestPermissionRationale(permission) == false) {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                } else {
                    launcher.launch(permission)
                }
            }) {
                Text(stringResource(R.string.permission_allow))
            }
        }
    }
}
