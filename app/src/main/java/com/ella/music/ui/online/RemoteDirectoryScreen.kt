package com.ella.music.ui.online

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.remote.EmbyService
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.data.remote.RemoteOnlineSong
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun RemoteDirectoryScreen(
    provider: RemoteMusicProvider,
    title: String,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val navidromeService = remember(context) { NavidromeService(context) }
    val embyService = remember(context) { EmbyService(context) }
    val scope = rememberCoroutineScope()
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val navidromeConfig by settingsManager.navidromeConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Navidrome, "")
    )
    val embyConfig by settingsManager.embyConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Emby, "")
    )
    val config = if (provider == RemoteMusicProvider.Navidrome) navidromeConfig else embyConfig
    var items by remember(provider) { mutableStateOf<List<RemoteOnlineSong>>(emptyList()) }
    var loading by remember(provider) { mutableStateOf(false) }
    var message by remember(provider) { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    fun resolveSong(item: RemoteOnlineSong): Song = when (provider) {
        RemoteMusicProvider.Navidrome -> navidromeService.resolvePlayableSong(item)
        RemoteMusicProvider.Emby -> embyService.resolvePlayableSong(item)
        RemoteMusicProvider.Lx -> item.song
    }

    fun load() {
        if (!config.isConfigured) {
            message = context.getString(R.string.remote_source_configure_first)
            return
        }
        scope.launch {
            loading = true
            runCatching {
                when (provider) {
                    RemoteMusicProvider.Navidrome -> navidromeService.listSongs(config)
                    RemoteMusicProvider.Emby -> embyService.listSongs(config)
                    RemoteMusicProvider.Lx -> emptyList()
                }
            }.onSuccess { result ->
                items = result
                message = if (result.isEmpty()) context.getString(R.string.webdav_empty_directory)
                else context.getString(R.string.lx_online_songs_found, result.size)
            }.onFailure { error ->
                message = error.localizedMessage ?: context.getString(R.string.webdav_load_failed)
                Toast.makeText(context, message.orEmpty(), Toast.LENGTH_SHORT).show()
            }
            loading = false
        }
    }

    LaunchedEffect(provider, config) {
        if (config.isConfigured) load()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = title,
            color = ellaPageBackground(),
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(MiuixIcons.Regular.Back, contentDescription = stringResource(R.string.common_back))
                }
            },
            actions = {
                IconButton(onClick = { load() }) {
                    Icon(MiuixIcons.Regular.Refresh, contentDescription = stringResource(R.string.library_refresh), modifier = Modifier.size(24.dp))
                }
                IconButton(onClick = { showSettings = true }) {
                    Icon(MiuixIcons.Regular.Settings, contentDescription = stringResource(R.string.webdav_settings), modifier = Modifier.size(24.dp))
                }
            }
        )

        if (!config.isConfigured && items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.remote_source_configure_first), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            Text(
                text = if (loading) stringResource(R.string.lx_online_processing) else message.orEmpty(),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 160.dp)
            ) {
                items(items, key = { it.remoteId }) { item ->
                    SongItem(
                        song = item.song,
                        albumArtUri = item.coverUrl.takeIf { it.isNotBlank() }?.let(Uri::parse),
                        showPlayNextInLists = showPlayNextInLists,
                        onClick = {
                            val visible = items.ifEmpty { listOf(item) }
                            val startIndex = visible.indexOfFirst { it.remoteId == item.remoteId }.coerceAtLeast(0)
                            playerViewModel.setLazyOnlinePlaylist(
                                songs = visible.map { it.song },
                                startIndex = startIndex,
                                resolvedStartSong = resolveSong(item)
                            ) { song ->
                                val target = visible.firstOrNull { it.song.id == song.id } ?: item
                                resolveSong(target)
                            }
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        },
                        onPlayNext = { playerViewModel.playNext(resolveSong(item)) }
                    )
                }
            }
        }
    }

    if (showSettings) {
        RemoteDirectorySettingsSheet(
            provider = provider,
            savedConfig = config,
            onDismiss = { showSettings = false },
            onSaved = {
                showSettings = false
                load()
            }
        )
    }
}

@Composable
private fun RemoteDirectorySettingsSheet(
    provider: RemoteMusicProvider,
    savedConfig: RemoteMusicSourceConfig,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val navidromeService = remember(context) { NavidromeService(context) }
    val embyService = remember(context) { EmbyService(context) }
    var url by remember(savedConfig) { mutableStateOf(savedConfig.baseUrl) }
    var user by remember(savedConfig) { mutableStateOf(savedConfig.username) }
    var password by remember(savedConfig) { mutableStateOf(savedConfig.password) }
    var status by remember { mutableStateOf<String?>(null) }

    EllaMiuixBottomSheet(
        show = true,
        title = if (provider == RemoteMusicProvider.Navidrome) stringResource(R.string.remote_source_navidrome) else stringResource(R.string.remote_source_emby),
        onDismissRequest = onDismiss
    ) {
        Column(modifier = Modifier.padding(bottom = 18.dp)) {
            EllaMiuixTextField(url, { url = it }, label = stringResource(R.string.webdav_url), modifier = Modifier.fillMaxWidth())
            EllaMiuixTextField(user, { user = it }, label = stringResource(R.string.webdav_username), modifier = Modifier.fillMaxWidth())
            EllaMiuixTextField(
                password,
                { password = it },
                label = stringResource(R.string.webdav_password),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            status?.let {
                Text(text = it, color = MiuixTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
            }
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss),
                    EllaMiuixAction(
                        text = stringResource(R.string.common_save),
                        primary = true,
                        onClick = {
                            scope.launch {
                                runCatching {
                                    if (provider == RemoteMusicProvider.Navidrome) {
                                        val config = RemoteMusicSourceConfig(provider, url, user, password)
                                        navidromeService.test(config)
                                        settingsManager.setNavidromeConfig(url, user, password)
                                    } else {
                                        val login = embyService.login(url, user, password)
                                        settingsManager.setEmbyConfig(url, user, login.token, login.userId, login.serverName)
                                    }
                                }.onSuccess {
                                    Toast.makeText(context, R.string.webdav_config_saved, Toast.LENGTH_SHORT).show()
                                    onSaved()
                                }.onFailure { error ->
                                    status = error.localizedMessage ?: context.getString(R.string.remote_source_request_failed)
                                }
                            }
                        }
                    )
                )
            )
        }
    }
}
