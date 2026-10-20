package app.remotio

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import app.remotio.tv.RemoteKey
import app.remotio.ui.LocalNetworkPermissionGate
import app.remotio.ui.RemoteViewModel
import app.remotio.ui.RemotioApp
import app.remotio.ui.theme.RemotioTheme

class MainActivity : ComponentActivity() {
    // Same instance RemotioApp gets from viewModel(): both are scoped to this activity.
    private val viewModel: RemoteViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RemotioTheme {
                LocalNetworkPermissionGate {
                    RemotioApp(viewModel)
                }
            }
        }
    }

    /** The phone's own volume buttons turn the TV up and down while Remotio is open. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VolumeUp
            KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VolumeDown
            else -> null
        }
        if (key != null && viewModel.pressFromHardwareKey(key)) return true
        return super.onKeyDown(keyCode, event)
    }
}
