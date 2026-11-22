package com.aeswox.arcplayer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aeswox.arcplayer.cloud.CloudAuthMachine
import com.aeswox.arcplayer.cloud.CloudAuthStage
import com.aeswox.arcplayer.cloud.CloudRepository
import com.aeswox.arcplayer.cloud.telegramAuthError
import com.aeswox.arcplayer.cloud.telegramCodeInstruction
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor

private val LoginBackground = Color(0xFF000000)
private val LoginAccent = Color(0xFFFFFFFF)
private val LoginMuted = Color(0xFFAAAAAA)
private val LoginText = Color(0xFFEEEEEE)
private val LoginError = Color(0xFFFF6B6B)

private enum class TvLoginForm { QR, PHONE, CODE, PASSWORD, REGISTER, CONNECTING, CONFIGURATION, RECOVERY, CONNECTED }

/** Native TV entry point. Only this surface opts in to QR; phone onboarding keeps its usual flow. */
@Composable
internal fun TvTelegramLogin(repository: CloudRepository, onBrowse: () -> Unit) {
    val state by repository.authState.collectAsStateWithLifecycle()
    val repositoryBusy by repository.busy.collectAsStateWithLifecycle()
    var phoneFallback by rememberSaveable(repository) { mutableStateOf(false) }
    // Saving the attempt flag is safe; the token, code and password are deliberately NOT saveable.
    var qrAttempted by rememberSaveable(repository) {
        mutableStateOf(state.error != null || state.qrCodeLink != null || state.qrRequestInFlight)
    }
    val browse by rememberUpdatedState(onBrowse)
    val working = repositoryBusy || state.qrRequestInFlight ||
        state.stage == CloudAuthStage.REQUESTING_CODE || state.stage == CloudAuthStage.CHECKING_CODE
    val stage = if (state.stage == CloudAuthStage.CHECKING_CODE) {
        state.retryStage ?: CloudAuthStage.WAITING_FOR_CODE
    } else state.stage
    val form = when (stage) {
        CloudAuthStage.MISSING_CREDENTIALS -> TvLoginForm.CONFIGURATION
        CloudAuthStage.WAITING_FOR_PHONE_NUMBER, CloudAuthStage.REQUESTING_QR_CODE,
        CloudAuthStage.WAITING_FOR_OTHER_DEVICE_CONFIRMATION -> if (phoneFallback) TvLoginForm.PHONE else TvLoginForm.QR
        CloudAuthStage.REQUESTING_CODE -> TvLoginForm.PHONE
        CloudAuthStage.WAITING_FOR_CODE -> TvLoginForm.CODE
        CloudAuthStage.WAITING_FOR_PASSWORD -> TvLoginForm.PASSWORD
        CloudAuthStage.WAITING_FOR_REGISTRATION -> TvLoginForm.REGISTER
        CloudAuthStage.AUTHORIZED -> TvLoginForm.CONNECTED
        CloudAuthStage.FAILED, CloudAuthStage.CLOSED -> TvLoginForm.RECOVERY
        CloudAuthStage.READY_TO_START -> if (state.error == null) TvLoginForm.CONNECTING else TvLoginForm.RECOVERY
        else -> TvLoginForm.CONNECTING
    }
    var phone by remember(repository) { mutableStateOf(state.lastPhoneNumber) }
    var code by remember(form) { mutableStateOf("") }
    var password by remember(form) { mutableStateOf("") }
    var firstName by remember(form) { mutableStateOf("") }
    var lastName by remember(form) { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val firstFocus = remember { FocusRequester() }
    val focusBrowse = form == TvLoginForm.CONNECTING || form == TvLoginForm.CONFIGURATION ||
        form == TvLoginForm.CONNECTED || state.stage == CloudAuthStage.CLOSED

    LaunchedEffect(repository) {
        if (state.stage != CloudAuthStage.MISSING_CREDENTIALS) repository.begin()
    }
    LaunchedEffect(repository, state.stage, phoneFallback, qrAttempted) {
        if (state.stage == CloudAuthStage.WAITING_FOR_PHONE_NUMBER && !phoneFallback && !qrAttempted) {
            qrAttempted = true
            // A rejection requires an explicit retry, never a recomposition-driven request loop.
            if (state.error == null) repository.requestQrCodeAuthentication()
        }
    }
    LaunchedEffect(state.stage) {
        if (state.stage == CloudAuthStage.AUTHORIZED) browse()
    }
    LaunchedEffect(form, working) {
        keyboard?.hide()
        if (!working || form == TvLoginForm.QR || focusBrowse) firstFocus.requestFocus()
    }

    val changeNumber: () -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        phoneFallback = true
        qrAttempted = true
        repository.resetToPhoneNumber()
    }
    val back: () -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        when {
            working -> browse()
            form == TvLoginForm.PHONE -> { phoneFallback = false; qrAttempted = true }
            form == TvLoginForm.CODE || form == TvLoginForm.REGISTER -> changeNumber()
            form == TvLoginForm.PASSWORD -> {
                qrAttempted = true
                // QR-based 2FA returns to QR selection; phone-based 2FA returns to Change number.
                repository.resetToPhoneNumber()
            }
            else -> browse()
        }
    }
    BackHandler(onBack = back)

    val valid = when (form) {
        TvLoginForm.PHONE -> CloudAuthMachine.normalizePhoneNumber(phone) != null
        TvLoginForm.CODE -> code.trim().length in 5..64
        TvLoginForm.PASSWORD -> password.isNotEmpty()
        TvLoginForm.REGISTER -> firstName.isNotBlank()
        else -> false
    }
    val submit: () -> Unit = {
        if (!working && valid) {
            focus.clearFocus()
            keyboard?.hide()
            when (form) {
                TvLoginForm.PHONE -> repository.submitPhoneNumber(phone)
                TvLoginForm.CODE -> repository.submitCode(code)
                TvLoginForm.PASSWORD -> repository.submitPassword(password)
                TvLoginForm.REGISTER -> repository.submitRegistration(firstName, lastName)
                else -> Unit
            }
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = LoginAccent, onPrimary = LoginBackground, background = LoginBackground,
            surface = Color(0xFF141414), onSurface = LoginText, onBackground = LoginText,
            onSurfaceVariant = LoginMuted, outline = Color(0xFF444444), error = LoginError
        ),
        typography = Typography(
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 20.sp, lineHeight = 28.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 26.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        )
    ) {
        // At the TV baseline (960 x 540 dp), both columns fit with comfortable overscan-safe insets.
        Row(
            Modifier.fillMaxSize().background(LoginBackground).imePadding().padding(horizontal = 48.dp, vertical = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text("ARC TV", color = LoginAccent, fontFamily = FontFamily.SansSerif,
                    fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
                Column(
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(when (form) {
                        TvLoginForm.QR -> "Your Telegram.\nOn the big screen."
                        TvLoginForm.PHONE -> "Sign in with\nyour phone number."
                        TvLoginForm.CODE -> "Check your code."
                        TvLoginForm.PASSWORD -> "One more step."
                        TvLoginForm.REGISTER -> "Create your account."
                        TvLoginForm.CONFIGURATION -> "Telegram isn't\nconfigured."
                        TvLoginForm.RECOVERY -> "Let's reconnect."
                        TvLoginForm.CONNECTED -> "You're connected."
                        TvLoginForm.CONNECTING -> "Connecting\nto Telegram."
                    }, color = LoginText, fontFamily = FontFamily.SansSerif, fontSize = 36.sp,
                        lineHeight = 42.sp, fontWeight = FontWeight.SemiBold)
                    Text(when (form) {
                        TvLoginForm.QR -> "Open Telegram on your phone to sign in."
                        TvLoginForm.PHONE -> "Enter your Telegram number, including + and the country code."
                        TvLoginForm.CODE -> telegramCodeInstruction(state.codeHint)
                        TvLoginForm.PASSWORD -> "Enter your Telegram two-step verification password. This is not your phone's PIN."
                        TvLoginForm.REGISTER -> "This number has no Telegram account. Continuing creates one; use an existing account if you prefer."
                        TvLoginForm.CONFIGURATION -> "This build is missing its Telegram app credentials. Install a configured build to sign in. You can still browse without Telegram."
                        TvLoginForm.RECOVERY -> if (state.stage == CloudAuthStage.CLOSED)
                            "Close and reopen Arc Player to reconnect. You can still browse your on-device library."
                        else "Check your connection, then try again. Your on-device library is still available."
                        TvLoginForm.CONNECTED -> "Opening your library…"
                        TvLoginForm.CONNECTING -> "Restoring your connection. This can take a moment. You can browse while you wait."
                    }, color = LoginMuted)
                    if (form == TvLoginForm.QR) {
                        Text("1   Open Settings → Devices\n2   Choose Link Desktop Device\n3   Scan the code and confirm", color = LoginText,
                            fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 32.sp)
                    }
                    if (state.error != null) {
                        Text(telegramAuthError(state.error.orEmpty()), color = LoginError,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                }
                TvAction("Browse without signing in", onClick = { keyboard?.hide(); browse() },
                    modifier = if (focusBrowse) Modifier.focusRequester(firstFocus) else Modifier)
            }
            Box(Modifier.width(340.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.fillMaxWidth().tvGlass().padding(24.dp).verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (form) {
                        TvLoginForm.QR -> {
                            val link = state.qrCodeLink.takeIf {
                                state.stage == CloudAuthStage.WAITING_FOR_OTHER_DEVICE_CONFIRMATION
                            }
                            Box(
                                Modifier.size(280.dp).clip(RoundedCornerShape(12.dp)).background(Color.White)
                                    .border(1.dp, Color(0xFFCFD7E2), RoundedCornerShape(12.dp)).padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (link != null) {
                                    // Key the whole renderer so a refreshed token can never show the old matrix.
                                    key(link) { TvTelegramQr(link) }
                                } else {
                                    Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                        val awaitingLink = state.stage == CloudAuthStage.REQUESTING_QR_CODE || state.qrRequestInFlight
                                        if (awaitingLink && state.error == null) CircularProgressIndicator(color = Color(0xFF555555))
                                        Text(when {
                                            state.error != null -> "QR unavailable"
                                            awaitingLink -> "Waiting for Telegram…"
                                            else -> "Choose Show QR code to link Telegram"
                                        }, color = LoginBackground, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            Text(if (link != null) "Refreshes automatically" else "Use your phone or the remote",
                                color = LoginMuted, fontFamily = FontFamily.SansSerif, fontSize = 16.sp)
                            TvAction("Use phone number", onClick = { phoneFallback = true; qrAttempted = true },
                                modifier = Modifier.fillMaxWidth().focusRequester(firstFocus))
                            if (state.stage == CloudAuthStage.WAITING_FOR_PHONE_NUMBER && qrAttempted) {
                                TvAction(if (state.error == null) "Show QR code" else "Try QR again",
                                    onClick = { repository.requestQrCodeAuthentication() },
                                    modifier = Modifier.fillMaxWidth(), enabled = !working)
                            }
                        }
                        TvLoginForm.PHONE, TvLoginForm.CODE, TvLoginForm.PASSWORD, TvLoginForm.REGISTER -> {
                            when (form) {
                                TvLoginForm.PHONE -> TvLoginField(phone, { phone = it }, "Phone number",
                                    KeyboardType.Phone, !working, Modifier.focusRequester(firstFocus), onDone = submit)
                                TvLoginForm.CODE -> {
                                    if (state.lastPhoneNumber.isNotBlank()) Text(state.lastPhoneNumber, color = LoginMuted)
                                    val length = state.codeHint?.codeLength ?: 0
                                    TvLoginField(code, { code = it }, if (length > 0) "$length-character code" else "Sign-in code",
                                        if (state.codeHint?.digitsOnly == true) KeyboardType.NumberPassword else KeyboardType.Ascii,
                                        !working, Modifier.focusRequester(firstFocus), onDone = submit)
                                }
                                TvLoginForm.PASSWORD -> TvLoginField(password, { password = it }, "Telegram password",
                                    KeyboardType.Password, !working, Modifier.focusRequester(firstFocus), secret = true, onDone = submit)
                                TvLoginForm.REGISTER -> {
                                    TvLoginField(firstName, { firstName = it }, "First name", KeyboardType.Text,
                                        !working, Modifier.focusRequester(firstFocus), next = true,
                                        onDone = { focus.moveFocus(FocusDirection.Down) })
                                    TvLoginField(lastName, { lastName = it }, "Last name (optional)", KeyboardType.Text,
                                        !working, onDone = submit)
                                }
                                else -> Unit
                            }
                            if (working) TvLoginProgress("Waiting for Telegram…")
                            TvAction(when (form) {
                                TvLoginForm.PHONE -> "Send sign-in code"
                                TvLoginForm.CODE -> "Verify code"
                                TvLoginForm.PASSWORD -> "Unlock Telegram"
                                else -> "Create Telegram account"
                            }, onClick = submit, modifier = Modifier.fillMaxWidth(), enabled = valid && !working)
                            TvAction(if (form == TvLoginForm.PHONE) "Back to QR sign-in" else "Change number",
                                onClick = if (form == TvLoginForm.PHONE) back else changeNumber,
                                modifier = Modifier.fillMaxWidth(), enabled = !working)
                        }
                        TvLoginForm.CONNECTING -> TvLoginProgress("Connecting securely…")
                        TvLoginForm.RECOVERY -> if (state.stage != CloudAuthStage.CLOSED) {
                            TvAction("Try again", onClick = {
                                // Retry explicitly, using phone fallback rather than starting another QR loop.
                                qrAttempted = true
                                phoneFallback = true
                                repository.retryAuthorization()
                            }, modifier = Modifier.fillMaxWidth().focusRequester(firstFocus), enabled = !working)
                        }
                        TvLoginForm.CONFIGURATION, TvLoginForm.CONNECTED -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLoginProgress(label: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), color = LoginAccent, strokeWidth = 2.dp)
        Text(label, color = LoginMuted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

@Composable
private fun TvLoginField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    next: Boolean = false,
    onDone: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = value, onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused }
            .border(if (focused) 3.dp else 1.dp, if (focused) LoginAccent else Color(0xFF444444), RoundedCornerShape(16.dp))
            .onPreviewKeyEvent {
                if (it.type == KeyEventType.KeyDown) {
                    when (it.key) {
                        Key.DirectionDown -> focus.moveFocus(FocusDirection.Down)
                        Key.DirectionUp -> focus.moveFocus(FocusDirection.Up)
                        else -> false
                    }
                } else false
            },
        enabled = enabled, singleLine = true,
        label = { Text(label, fontFamily = FontFamily.SansSerif, fontSize = 16.sp) },
        textStyle = TextStyle(color = LoginText, fontFamily = FontFamily.SansSerif, fontSize = 22.sp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = if (next) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent, cursorColor = LoginAccent,
            focusedLabelColor = LoginAccent, unfocusedLabelColor = LoginMuted
        )
    )
}

@Composable
private fun TvTelegramQr(link: String) {
    // ZXing core runs entirely on-device. No image endpoint, bitmap file, token cache or expiry timer.
    val result by produceState<Result<BitMatrix>?>(initialValue = null, key1 = link) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0, mapOf(
                    EncodeHintType.MARGIN to 4,
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
                ))
            }
        }
    }
    val matrix = result?.getOrNull()
    when {
        matrix != null -> Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Telegram sign-in QR code" }) {
            // Integral device pixels preserve sharp module edges, with ZXing's four-module quiet zone.
            val module = floor(size.minDimension / matrix.width)
            val left = floor((size.width - matrix.width * module) / 2f)
            val top = floor((size.height - matrix.height * module) / 2f)
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(left + x * module, top + y * module), Size(module, module))
            }
        }
        result == null -> CircularProgressIndicator(color = Color(0xFF555555))
        else -> Text("Can't display this code.\nUse your phone number instead.",
            Modifier.padding(20.dp), color = LoginBackground, textAlign = TextAlign.Center)
    }
}
