package de.mm20.launcher2.ui.settings.homescreen.wallpaper

import de.mm20.launcher2.ui.component.TelosCyberHeading
import de.mm20.launcher2.i18n.R as I18nR
import de.mm20.launcher2.base.R as BaseR

import de.mm20.launcher2.preferences.SystemBarColors
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import de.mm20.launcher2.ktx.tryStartActivity
import de.mm20.launcher2.ui.R
import de.mm20.launcher2.ui.component.preferences.ListPreference
import de.mm20.launcher2.ui.component.preferences.Preference
import de.mm20.launcher2.ui.component.preferences.PreferenceCategory
import de.mm20.launcher2.ui.component.preferences.PreferenceScreen
import de.mm20.launcher2.ui.component.preferences.SliderPreference
import de.mm20.launcher2.ui.component.preferences.SwitchPreference
import de.mm20.launcher2.ui.locals.LocalBackStack
import de.mm20.launcher2.ui.media.photos.PhotosScreen
import de.mm20.launcher2.wallpapers.StaticWallpaperTarget
import kotlinx.serialization.Serializable

@Serializable
data object WallpaperSettingsRoute : NavKey

@Serializable
data class WallpaperLibraryRoute(val appendVideo: Boolean = false) : NavKey

@Composable
fun WallpaperLibraryScreen(appendVideo: Boolean = false) {
    val viewModel: WallpaperSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val setResultToast: (Boolean) -> Unit = { ok ->
        Toast.makeText(
            context,
            if (ok) I18nR.string.wallpaper_set_success else I18nR.string.wallpaper_set_failure,
            Toast.LENGTH_SHORT,
        ).show()
    }
    fun activateVideoIfNeeded(ok: Boolean) {
        if (ok && !viewModel.isVideoWallpaperActive) {
            context.tryStartActivity(viewModel.getActivationIntent())
        } else {
            setResultToast(ok)
        }
        if (ok) backStack.removeLastOrNull()
    }
    var pendingImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    pendingImageUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.wallpaper_target_dialog_title))  } },
            text = {
                Column {
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.setStaticWallpaper(uri, StaticWallpaperTarget.Home, setResultToast)
                        pendingImageUri = null
                        backStack.removeLastOrNull()
                    }) { Text(stringResource(I18nR.string.preference_wallpaper_set_home)) }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.setStaticWallpaper(uri, StaticWallpaperTarget.Lock, setResultToast)
                        pendingImageUri = null
                        backStack.removeLastOrNull()
                    }) { Text(stringResource(I18nR.string.preference_wallpaper_set_lock)) }
                    TextButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        viewModel.setStaticWallpaper(uri, StaticWallpaperTarget.Both, setResultToast)
                        pendingImageUri = null
                        backStack.removeLastOrNull()
                    }) { Text(stringResource(I18nR.string.preference_wallpaper_set_both)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingImageUri = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        PhotosScreen(embedded = true, wallpaperPick = true) { item ->
            if (item.isVideo || item.isGif) {
                viewModel.setVideoWallpaper(item.uri, appendVideo, ::activateVideoIfNeeded)
            } else {
                pendingImageUri = item.uri
            }
        }
    }
}

@Composable
fun WallpaperSettingsScreen() {
    val viewModel: WallpaperSettingsScreenVM = viewModel()
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refresh()
        }
    }

    val pauseOnBatterySaver by viewModel.pauseOnBatterySaver.collectAsStateWithLifecycle()
    val pauseOnThermal by viewModel.pauseOnThermal.collectAsStateWithLifecycle()
    val pauseOnDesktopMode by viewModel.pauseOnDesktopMode.collectAsStateWithLifecycle()
    val videoTransforms by viewModel.videoTransforms.collectAsStateWithLifecycle()
    val videoSpeed by viewModel.videoSpeed.collectAsStateWithLifecycle()
    val videoStartBehavior by viewModel.videoStartBehavior.collectAsStateWithLifecycle()

    val textColors by viewModel.textColors.collectAsStateWithLifecycle()
    val dimWallpaper by viewModel.dimWallpaper.collectAsStateWithLifecycle()
    val blurWallpaper by viewModel.blurWallpaper.collectAsStateWithLifecycle()
    val blurWallpaperRadius by viewModel.blurWallpaperRadius.collectAsStateWithLifecycle()

    val setResultToast: (Boolean) -> Unit = { ok ->
        Toast.makeText(
            context,
            if (ok) I18nR.string.wallpaper_set_success else I18nR.string.wallpaper_set_failure,
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun activateVideoIfNeeded(ok: Boolean) {
        if (ok && !viewModel.isVideoWallpaperActive) {
            context.tryStartActivity(viewModel.getActivationIntent())
        } else {
            setResultToast(ok)
        }
    }

    var pendingImageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var appendToPlaylist by rememberSaveable { mutableStateOf(false) }
    val videoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setVideoWallpaper(uri, appendToPlaylist, ::activateVideoIfNeeded)
        }
    }

    pendingImageUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImageUri = null },
            title = { TelosCyberHeading {  Text(stringResource(I18nR.string.wallpaper_target_dialog_title))  } },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.setStaticWallpaper(uri, StaticWallpaperTarget.Home, setResultToast)
                            pendingImageUri = null
                        },
                    ) {
                        Text(stringResource(I18nR.string.preference_wallpaper_set_home))
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.setStaticWallpaper(uri, StaticWallpaperTarget.Lock, setResultToast)
                            pendingImageUri = null
                        },
                    ) {
                        Text(stringResource(I18nR.string.preference_wallpaper_set_lock))
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            viewModel.setStaticWallpaper(uri, StaticWallpaperTarget.Both, setResultToast)
                            pendingImageUri = null
                        },
                    ) {
                        Text(stringResource(I18nR.string.preference_wallpaper_set_both))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingImageUri = null }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        )
    }

    PreferenceScreen(title = stringResource(I18nR.string.preference_screen_wallpaper)) {
        item {
            PreferenceCategory(title = stringResource(I18nR.string.preference_category_wallpaper)) {
                ListPreference(
                    title = stringResource(I18nR.string.wallpaper_content_color),
                    value = textColors,
                    items = listOf(
                        stringResource(I18nR.string.wallpaper_content_auto) to SystemBarColors.Auto,
                        stringResource(I18nR.string.wallpaper_content_white) to SystemBarColors.Light,
                        stringResource(I18nR.string.wallpaper_content_black) to SystemBarColors.Dark,
                    ),
                    onValueChanged = viewModel::setTextColors,
                )
                Preference(
                    icon = BaseR.drawable.wallpaper_24px,
                    title = stringResource(I18nR.string.preference_wallpaper_change),
                    summary = stringResource(I18nR.string.preference_wallpaper_browse_telos),
                    onClick = { backStack.add(WallpaperLibraryRoute()) },
                )
                Preference(
                    title = stringResource(I18nR.string.preference_wallpaper_choose_video),
                    summary = stringResource(I18nR.string.preference_wallpaper_choose_video_summary),
                    onClick = {
                        appendToPlaylist = false
                        videoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                    },
                )
                Preference(
                    title = stringResource(I18nR.string.preference_wallpaper_system_picker),
                    summary = stringResource(I18nR.string.preference_wallpaper_system_picker_summary),
                    onClick = {
                        context.tryStartActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), null))
                    }
                )
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_dim_wallpaper),
                    summary = stringResource(I18nR.string.preference_dim_wallpaper_summary),
                    value = dimWallpaper,
                    onValueChanged = { viewModel.setDimWallpaper(it) }
                )
                val isBlurSupported = remember { viewModel.isBlurAvailable(context) }
                SwitchPreference(
                    title = stringResource(I18nR.string.preference_blur_wallpaper),
                    summary = stringResource(
                        if (isBlurSupported) I18nR.string.preference_blur_wallpaper_summary
                        else I18nR.string.preference_blur_wallpaper_unsupported
                    ),
                    value = blurWallpaper && isBlurSupported,
                    onValueChanged = { viewModel.setBlurWallpaper(it) },
                    enabled = isBlurSupported
                )
                AnimatedVisibility(blurWallpaper && isBlurSupported) {
                    SliderPreference(
                        title = stringResource(I18nR.string.preference_blur_wallpaper_radius),
                        value = blurWallpaperRadius,
                        onValueChanged = { viewModel.setBlurWallpaperRadius(it) },
                        min = 4,
                        max = 64,
                        step = 4,
                    )
                }
            }
        }
        if (viewModel.hasVideoWallpaper) {
            item {
                PreferenceCategory(title = stringResource(I18nR.string.preference_wallpaper_category_video)) {
                    Preference(
                        title = stringResource(I18nR.string.preference_wallpaper_add_video),
                        onClick = {
                            appendToPlaylist = true
                            backStack.add(WallpaperLibraryRoute(appendVideo = true))
                        }
                    )
                    Preference(
                        title = stringResource(I18nR.string.preference_wallpaper_clear_playlist),
                        onClick = { viewModel.clearVideoPlaylist() }
                    )
                    Preference(
                        title = stringResource(
                            if (viewModel.isVideoWallpaperActive) I18nR.string.wallpaper_video_active
                            else I18nR.string.wallpaper_video_inactive
                        ),
                        enabled = !viewModel.isVideoWallpaperActive,
                        onClick = { context.tryStartActivity(viewModel.getActivationIntent()) }
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_pause_battery_saver),
                        summary = stringResource(I18nR.string.preference_wallpaper_pause_battery_saver_summary),
                        value = pauseOnBatterySaver == true,
                        onValueChanged = { viewModel.setPauseOnBatterySaver(it) }
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_pause_thermal),
                        summary = stringResource(I18nR.string.preference_wallpaper_pause_thermal_summary),
                        value = pauseOnThermal == true,
                        onValueChanged = { viewModel.setPauseOnThermal(it) }
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_pause_desktop_mode),
                        summary = stringResource(I18nR.string.preference_wallpaper_pause_desktop_mode_summary),
                        value = pauseOnDesktopMode == true,
                        onValueChanged = { viewModel.setPauseOnDesktopMode(it) }
                    )
                }
            }
        }
        if (viewModel.hasVideoWallpaper && videoTransforms != null) {
            val transforms = videoTransforms!!
            item {
                PreferenceCategory(title = stringResource(I18nR.string.websearch_dialog_advanced)) {
                    ListPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_scaling_mode),
                        items = listOf(
                            stringResource(I18nR.string.preference_wallpaper_scaling_mode_fit) to de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fit,
                            stringResource(I18nR.string.preference_wallpaper_scaling_mode_fill) to de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Fill,
                            stringResource(I18nR.string.preference_wallpaper_scaling_mode_stretch) to de.mm20.launcher2.preferences.VideoWallpaperScalingMode.Stretch,
                        ),
                        value = transforms.scalingMode,
                        onValueChanged = { if (it != null) viewModel.setVideoScalingMode(it) }
                    )
                    SliderPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_brightness),
                        value = transforms.brightness,
                        min = 0f,
                        max = 2f,
                        onValueChanged = { viewModel.setVideoBrightness(it) },
                    )
                    SliderPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_zoom),
                        value = transforms.zoom,
                        min = 0.5f,
                        max = 5f,
                        onValueChanged = { viewModel.setVideoZoom(it) },
                    )
                    SliderPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_position_x),
                        value = transforms.positionX,
                        min = -1f,
                        max = 1f,
                        onValueChanged = { viewModel.setVideoPosition(it, transforms.positionY) },
                    )
                    SliderPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_position_y),
                        value = transforms.positionY,
                        min = -1f,
                        max = 1f,
                        onValueChanged = { viewModel.setVideoPosition(transforms.positionX, it) },
                    )
                    SliderPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_speed),
                        value = videoSpeed ?: 1f,
                        min = 0.25f,
                        max = 3f,
                        onValueChanged = { viewModel.setVideoSpeed(it) },
                    )
                    ListPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_start_behavior),
                        items = listOf(
                            stringResource(I18nR.string.preference_wallpaper_start_behavior_resume) to de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Resume,
                            stringResource(I18nR.string.preference_wallpaper_start_behavior_restart) to de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Restart,
                            stringResource(I18nR.string.preference_wallpaper_start_behavior_random) to de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Random,
                        ),
                        value = videoStartBehavior ?: de.mm20.launcher2.preferences.VideoWallpaperStartBehavior.Resume,
                        onValueChanged = { if (it != null) viewModel.setVideoStartBehavior(it) }
                    )
                    SwitchPreference(
                        title = stringResource(I18nR.string.preference_wallpaper_parallax),
                        value = transforms.parallax,
                        onValueChanged = { viewModel.setVideoParallax(it) }
                    )
                    if (transforms.parallax) {
                        SliderPreference(
                            title = stringResource(I18nR.string.preference_wallpaper_parallax_strength),
                            value = transforms.parallaxStrength,
                            min = 0.1f,
                            max = 1f,
                            onValueChanged = { viewModel.setVideoParallaxStrength(it) },
                        )
                    }
                }
            }
        }
    }
}
