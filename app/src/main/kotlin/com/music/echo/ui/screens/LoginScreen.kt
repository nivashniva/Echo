package echo.music.iad1tya.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.music.innertube.YouTube
import echo.music.iad1tya.LocalPlayerAwareWindowInsets
import echo.music.iad1tya.R
import echo.music.iad1tya.constants.AccountChannelHandleKey
import echo.music.iad1tya.constants.AccountEmailKey
import echo.music.iad1tya.constants.AccountNameKey
import echo.music.iad1tya.constants.DataSyncIdKey
import echo.music.iad1tya.constants.InnerTubeCookieKey
import echo.music.iad1tya.constants.SavedAccountsKey
import echo.music.iad1tya.constants.VisitorDataKey
import echo.music.iad1tya.models.AccountData
import echo.music.iad1tya.ui.component.IconButton as EchoIconButton
import echo.music.iad1tya.ui.utils.backToMain
import echo.music.iad1tya.utils.rememberPreference
import echo.music.iad1tya.utils.reportException
import echo.music.iad1tya.viewmodels.AccountSettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    navController: NavController,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val accountSettingsViewModel: AccountSettingsViewModel = hiltViewModel()

    var visitorData by rememberPreference(VisitorDataKey, "")
    var dataSyncId by rememberPreference(DataSyncIdKey, "")
    var innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    var accountName by rememberPreference(AccountNameKey, "")
    var accountEmail by rememberPreference(AccountEmailKey, "")
    var accountChannelHandle by rememberPreference(AccountChannelHandleKey, "")
    var savedAccountsJson by rememberPreference(SavedAccountsKey, "[]")

    var showWebLogin by remember { mutableStateOf(false) }
    var isSelectingGoogleAccount by remember { mutableStateOf(false) }
    var selectedGoogleAccount by remember { mutableStateOf<GoogleAccountSelection?>(null) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var hasCompletedLogin by remember { mutableStateOf(false) }
    val webViewState = remember { mutableStateOf<WebView?>(null) }

    fun savedAccounts(): List<AccountData> = try {
        Json.decodeFromString(savedAccountsJson)
    } catch (_: Exception) {
        emptyList()
    }

    fun continueWithGoogleAccount() {
        isSelectingGoogleAccount = true
        loginError = null
        coroutineScope.launch {
            val result = runCatching { GoogleAccountSignIn(context).chooseAccount().getOrThrow() }
            isSelectingGoogleAccount = false
            result.onSuccess { selection ->
                selectedGoogleAccount = selection
                val saved = savedAccounts().firstOrNull {
                    it.email.equals(selection.email, ignoreCase = true)
                }

                if (saved != null && saved.cookie.isNotBlank()) {
                    accountSettingsViewModel.saveTokenAndRestart(
                        context = context,
                        cookie = saved.cookie,
                        visitorData = saved.visitorData,
                        dataSyncId = saved.dataSyncId,
                        accountName = saved.name,
                        accountEmail = saved.email,
                        accountChannelHandle = saved.channelHandle
                    )
                } else {
                    showWebLogin = true
                }
            }.onFailure { error ->
                Timber.e(error, "Google account selection failed")
                loginError = error.message ?: "Google account selection failed"
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.login)) },
            navigationIcon = {
                EchoIconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
                }
            }
        )

        AnimatedContent(
            targetState = showWebLogin,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
            label = "login-mode"
        ) { webMode ->
            if (!webMode) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.96f),
                        exit = fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Card(
                                modifier = Modifier.size(84.dp),
                                shape = CircleShape,
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.login),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(22.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(Modifier.height(20.dp))
                            Text(
                                text = "Sign in to Echo",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Choose a Google account already on this device, or use the advanced YouTube login.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(24.dp))

                            Button(
                                onClick = ::continueWithGoogleAccount,
                                enabled = !isSelectingGoogleAccount,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                if (isSelectingGoogleAccount) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(R.drawable.login),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(Modifier.size(10.dp))
                                Text("Continue with Google")
                            }

                            selectedGoogleAccount?.let { account ->
                                Spacer(Modifier.height(16.dp))
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(18.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (account.photoUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = account.photoUrl,
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(CircleShape)
                                            )
                                        } else {
                                            Icon(
                                                painter = painterResource(R.drawable.login),
                                                contentDescription = null,
                                                modifier = Modifier.size(44.dp)
                                            )
                                        }
                                        Spacer(Modifier.size(12.dp))
                                        Column {
                                            Text(
                                                account.displayName.ifBlank { account.email },
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                account.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            loginError?.let {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { showWebLogin = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp)
                            ) {
                                Text("Advanced YouTube login")
                            }
                        }
                    }
                }
            } else {
                YouTubeWebLogin(
                    context = context,
                    coroutineScope = coroutineScope,
                    visitorData = visitorData,
                    onVisitorData = { visitorData = it },
                    dataSyncId = dataSyncId,
                    onDataSyncId = { dataSyncId = it },
                    innerTubeCookie = innerTubeCookie,
                    onCookie = { innerTubeCookie = it },
                    onAccountName = { accountName = it },
                    onAccountEmail = { accountEmail = it },
                    onAccountChannelHandle = { accountChannelHandle = it },
                    savedAccountsJson = savedAccountsJson,
                    onSavedAccountsJson = { savedAccountsJson = it },
                    hasCompletedLogin = hasCompletedLogin,
                    onCompleted = { hasCompletedLogin = it },
                    webViewState = webViewState,
                    onError = { reportException(it) }
                )
                BackHandler {
                    if (webViewState.value?.canGoBack() == true) webViewState.value?.goBack()
                    else showWebLogin = false
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YouTubeWebLogin(
    context: android.content.Context,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    visitorData: String,
    onVisitorData: (String) -> Unit,
    dataSyncId: String,
    onDataSyncId: (String) -> Unit,
    innerTubeCookie: String,
    onCookie: (String) -> Unit,
    onAccountName: (String) -> Unit,
    onAccountEmail: (String) -> Unit,
    onAccountChannelHandle: (String) -> Unit,
    savedAccountsJson: String,
    onSavedAccountsJson: (String) -> Unit,
    hasCompletedLogin: Boolean,
    onCompleted: (Boolean) -> Unit,
    webViewState: androidx.compose.runtime.MutableState<WebView?>,
    onError: (Throwable) -> Unit,
) {
    val currentVisitorData by rememberUpdatedState(visitorData)
    val currentDataSyncId by rememberUpdatedState(dataSyncId)
    val currentSavedAccountsJson by rememberUpdatedState(savedAccountsJson)
    val currentHasCompletedLogin by rememberUpdatedState(hasCompletedLogin)
    val currentOnCookie by rememberUpdatedState(onCookie)
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentOnSavedAccounts by rememberUpdatedState(onSavedAccountsJson)
    val currentOnAccountName by rememberUpdatedState(onAccountName)
    val currentOnAccountEmail by rememberUpdatedState(onAccountEmail)
    val currentOnAccountChannelHandle by rememberUpdatedState(onAccountChannelHandle)
    val currentOnError by rememberUpdatedState(onError)

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { webViewContext ->
            WebView(webViewContext).apply {
                webViewState.value = this
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        loadUrl("javascript:Android.onRetrieveVisitorData(window.yt?.config_?.VISITOR_DATA)")
                        loadUrl("javascript:Android.onRetrieveDataSyncId(window.yt?.config_?.DATASYNC_ID)")

                        if (url?.startsWith("https://music.youtube.com") == true && !currentHasCompletedLogin) {
                            val cookie = CookieManager.getInstance().getCookie(url).orEmpty()
                            if (cookie.isBlank()) return
                            currentOnCookie(cookie)
                            currentOnCompleted(true)

                            coroutineScope.launch {
                                YouTube.cookie = cookie
                                YouTube.dataSyncId = currentDataSyncId
                                YouTube.visitorData = currentVisitorData

                                YouTube.accountInfo().onSuccess { info ->
                                    currentOnAccountName(info.name)
                                    currentOnAccountEmail(info.email.orEmpty())
                                    currentOnAccountChannelHandle(info.channelHandle.orEmpty())

                                    val newAccount = AccountData(
                                        name = info.name,
                                        email = info.email.orEmpty(),
                                        channelHandle = info.channelHandle.orEmpty(),
                                        cookie = cookie,
                                        visitorData = currentVisitorData,
                                        dataSyncId = currentDataSyncId,
                                        avatarUrl = info.thumbnailUrl.orEmpty()
                                    )
                                    val accounts = try {
                                        Json.decodeFromString<List<AccountData>>(currentSavedAccountsJson)
                                    } catch (_: Exception) {
                                        emptyList()
                                    }.toMutableList()
                                    accounts.removeAll { it.email.equals(newAccount.email, true) || it.cookie == newAccount.cookie }
                                    accounts.add(newAccount)
                                    currentOnSavedAccounts(Json.encodeToString(accounts))

                                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                                    intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                                    context.startActivity(intent)
                                }.onFailure { error ->
                                    currentOnCompleted(false)
                                    currentOnError(error)
                                }
                            }
                        }
                    }
                }
                settings.apply {
                    javaScriptEnabled = true
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    domStorageEnabled = true
                }
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onRetrieveVisitorData(newVisitorData: String?) {
                        newVisitorData?.takeIf(String::isNotBlank)?.let(onVisitorData)
                    }

                    @JavascriptInterface
                    fun onRetrieveDataSyncId(newDataSyncId: String?) {
                        newDataSyncId?.takeIf(String::isNotBlank)?.substringBefore("||")?.let(onDataSyncId)
                    }
                }, "Android")
                CookieManager.getInstance().setAcceptCookie(true)
                // Start at YouTube Music directly so an existing Google/WebView session can be reused
                // instead of forcing the generic Google ServiceLogin page on every account handoff.
                loadUrl("https://music.youtube.com/")
            }
        },
        onRelease = { webView ->
            webView.stopLoading()
            webView.removeAllViews()
            webView.destroy()
            webViewState.value = null
        }
    )
}
