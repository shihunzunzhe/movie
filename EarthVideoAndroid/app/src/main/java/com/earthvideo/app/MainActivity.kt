package com.earthvideo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import coil.ImageLoader
import coil.compose.LocalImageLoader
import coil.request.ImageRequest
import coil.util.DebugLogger
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.earthvideo.app.ui.components.BottomNavBar
import com.earthvideo.app.ui.navigation.AppNavGraph
import com.earthvideo.app.ui.navigation.Routes
import com.earthvideo.app.ui.theme.EarthVideoTheme
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Allow content to extend behind the status bar; the theme paints the status bar tint.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // Configure OkHttp with image-specific headers (Referer for yutu images)
            val imageInterceptor = Interceptor { chain ->
                val request = chain.request()
                val newRequest = request.newBuilder()
                    .header("Referer", "https://yutuzy10.com/")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36")
                    .header("accept-language", "zh-CN,zh;q=0.9")
                    .build()
                chain.proceed(newRequest)
            }
            val imageOkHttp = OkHttpClient.Builder()
                .addInterceptor(imageInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val imageLoader = ImageLoader.Builder(this)
                .okHttpClient(imageOkHttp)
                .logger(DebugLogger())
                .crossfade(true)
                .build()
            CompositionLocalProvider(LocalImageLoader provides imageLoader) {
                EarthVideoTheme {
                    EarthVideoApp()
                }
            }
        }
    }
}

@Composable
fun EarthVideoApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Only show bottom bar on main tabs
    val showBottomBar = currentRoute in listOf(Routes.HOME, Routes.RANK, Routes.DISCOVER, Routes.PROFILE)

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    currentRoute = currentRoute ?: Routes.HOME,
                    onTabSelected = { route ->
                        navController.navigate(route) {
                            popUpTo(Routes.HOME) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        AppNavGraph(
            navController = navController,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(WindowInsets.systemBars)
        )
    }
}
