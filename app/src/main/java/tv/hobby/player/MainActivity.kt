package tv.hobby.player

import android.os.Bundle
import android.webkit.CookieManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import tv.hobby.player.ui.HobbyTheme
import tv.hobby.player.ui.HobbyTvApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CookieManager.getInstance().setAcceptCookie(true)
        setContent {
            HobbyTheme {
                HobbyTvApp()
            }
        }
    }
}
