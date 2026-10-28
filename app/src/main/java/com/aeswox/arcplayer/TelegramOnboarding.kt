package com.aeswox.arcplayer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aeswox.arcplayer.cloud.CloudAuthStage
import com.aeswox.arcplayer.cloud.CloudAuthState
import com.aeswox.arcplayer.cloud.TelegramStep
import com.aeswox.arcplayer.cloud.telegramAuthError
import com.aeswox.arcplayer.cloud.telegramCodeInstruction
import com.aeswox.arcplayer.cloud.telegramStep
import com.aeswox.arcplayer.ui.animations.StaggeredListItem
import com.aeswox.arcplayer.ui.animations.jellyClick
import com.aeswox.arcplayer.ui.animations.jellyTabCrossSlide
import com.aeswox.arcplayer.ui.animations.physicsBounceOverscroll
import com.aeswox.arcplayer.ui.components.HugeIcons

@Composable
internal fun TelegramOnboarding(
    state: CloudAuthState,
    busy: Boolean,
    started: Boolean,
    hasCollection: Boolean,
    setupBusy: Boolean,
    setupError: String?,
    topPadding: Dp,
    bottomPadding: Dp,
    onStart: () -> Unit,
    onWelcome: () -> Unit,
    onExit: () -> Unit,
    onPhone: (String) -> Unit,
    onCode: (String) -> Unit,
    onPassword: (String) -> Unit,
    onRegistration: (String, String) -> Unit,
    onKeys: (Int, String) -> Unit,
    onChangeNumber: () -> Unit,
    onSetup: () -> Unit
) {
    val step = telegramStep(state, started)
    val working = busy || state.stage == CloudAuthStage.REQUESTING_CODE || state.stage == CloudAuthStage.CHECKING_CODE
    var phone by remember { mutableStateOf(state.lastPhoneNumber) }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val exit: () -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        onExit()
    }
    val back: () -> Unit = {
        focus.clearFocus()
        keyboard?.hide()
        when {
            working || setupBusy -> exit()
            step == TelegramStep.PHONE || step == TelegramStep.KEYS -> onWelcome()
            step == TelegramStep.CODE -> onChangeNumber()
            else -> exit()
        }
    }
    // The welcome page is the front door, so Back there belongs to the screen underneath it.
    BackHandler(enabled = step != TelegramStep.WELCOME, onBack = back)
    LaunchedEffect(step) {
        focus.clearFocus()
        keyboard?.hide()
        if (step != TelegramStep.CODE) code = ""
        if (step != TelegramStep.PASSWORD) password = ""
    }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background).imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = topPadding),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(scheme.surfaceContainer)
                        .jellyClick(role = Role.Button, onClickLabel = "Back", onClick = back),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = scheme.onSurface, modifier = Modifier.size(22.dp))
                }
                Text("TELEGRAM SETUP", Modifier.weight(1f).padding(start = 16.dp),
                    color = scheme.onSurfaceVariant, fontSize = 11.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.SemiBold)
                Box(Modifier.heightIn(min = 48.dp).jellyClick(role = Role.Button, onClick = exit)
                    .padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                    Text("Not now", color = scheme.onSurfaceVariant, fontSize = 14.sp)
                }
            }
            val progress = when (step) {
                TelegramStep.CODE, TelegramStep.PASSWORD, TelegramStep.REGISTER -> 1
                TelegramStep.CONNECTED -> 2
                else -> 0
            }
            Row(
                Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                    .semantics { stateDescription = "Step ${progress + 1} of 3" },
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val width by animateDpAsState(if (progress == index) 24.dp else 8.dp,
                        animationSpec = spring(), label = "onboarding_step_width")
                    Box(Modifier.width(width).height(8.dp).clip(CircleShape)
                        .background(if (index <= progress) scheme.onSurface else scheme.outlineVariant))
                }
                Text(listOf("Connect", "Verify", "Collect")[progress], Modifier.padding(start = 8.dp),
                    color = scheme.onSurfaceVariant, fontSize = 12.sp)
            }
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val (enter, exit) = jellyTabCrossSlide(targetState.ordinal > initialState.ordinal)
                    enter togetherWith exit
                },
                modifier = Modifier.weight(1f),
                label = "telegram_step_glide"
            ) { page ->
                val submit: () -> Unit = {
                    if (!working) {
                        focus.clearFocus()
                        keyboard?.hide()
                        when (page) {
                            TelegramStep.PHONE -> if (phone.isNotBlank()) onPhone(phone)
                            TelegramStep.CODE -> if (code.trim().length in 5..64) onCode(code.trim())
                            TelegramStep.PASSWORD -> if (password.isNotEmpty()) onPassword(password)
                            TelegramStep.REGISTER -> if (firstName.isNotBlank()) onRegistration(firstName, lastName)
                            TelegramStep.KEYS -> apiId.trim().toIntOrNull()?.takeIf { it > 0 }?.let {
                                if (apiHash.isNotBlank()) onKeys(it, apiHash.trim())
                            }
                            else -> Unit
                        }
                    }
                }
                val error = if (page == TelegramStep.CONNECTED) setupError else state.error
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(
                        Modifier.weight(1f).fillMaxWidth().physicsBounceOverscroll().verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        StaggeredListItem(0) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Box(Modifier.size(72.dp).clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainer),
                                    contentAlignment = Alignment.Center) {
                                    Icon(if (page == TelegramStep.CONNECTED) Icons.Default.Check else HugeIcons.Library,
                                        null, tint = scheme.onSurface, modifier = Modifier.size(32.dp))
                                }
                                Spacer(Modifier.height(4.dp))
                                CollectionHeading(when (page) {
                                    TelegramStep.WELCOME -> "Your films.\nOne beautiful home."
                                    TelegramStep.PHONE -> "Let's connect."
                                    TelegramStep.CODE -> "Check your code."
                                    TelegramStep.PASSWORD -> "One more step."
                                    TelegramStep.REGISTER -> "New to Telegram?"
                                    TelegramStep.CONNECTED -> "You're connected."
                                    TelegramStep.KEYS -> "Connect this build."
                                    TelegramStep.RECOVERY -> "Let's try again."
                                    TelegramStep.CONNECTING -> "Connecting securely."
                                })
                                Text(when (page) {
                                    TelegramStep.WELCOME -> "Bring your Telegram movies and series into Arc Player. Your collection, beautifully organised."
                                    TelegramStep.PHONE -> "Use your Telegram phone number, including + and the country code."
                                    TelegramStep.CODE -> telegramCodeInstruction(state.codeHint)
                                    TelegramStep.PASSWORD -> "Your account uses two-step verification. Enter your Telegram password, not your phone's PIN."
                                    TelegramStep.REGISTER -> "This number doesn't have a Telegram account yet. Continuing creates one."
                                    TelegramStep.CONNECTED -> if (hasCollection) "Your private collection is ready to open." else "Now give your movies a home. Set up a private Telegram channel for your collection."
                                    TelegramStep.KEYS -> "This build needs a Telegram API ID and API hash. These are app keys, not your account password."
                                    TelegramStep.RECOVERY -> "Your Telegram connection couldn't be completed. Your on-device library is still available."
                                    TelegramStep.CONNECTING -> "Restoring your Telegram connection. This may take a moment."
                                }, color = scheme.onSurfaceVariant, fontSize = 16.sp, lineHeight = 24.sp)
                            }
                        }
                        StaggeredListItem(1) {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                when (page) {
                                    TelegramStep.WELCOME -> OnboardingCard {
                                        OnboardingDetail("01", "Connect with Telegram", "Use the account you already have.")
                                        OnboardingDetail("02", "Add films. Start watching.", "Forward videos to your private channel. Arc Player does the organising.")
                                    }
                                    TelegramStep.PHONE -> OnboardingField(phone, { phone = it }, "Phone number", KeyboardType.Phone,
                                        enabled = !working, onDone = submit)
                                    TelegramStep.CODE -> {
                                        if (state.lastPhoneNumber.isNotBlank()) Text(state.lastPhoneNumber,
                                            color = scheme.onSurface, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                                        val length = state.codeHint?.codeLength ?: 0
                                        OnboardingField(code, { code = it },
                                            if (length > 0) "$length-character code" else "Sign-in code",
                                            if (state.codeHint?.digitsOnly == true) KeyboardType.NumberPassword else KeyboardType.Ascii,
                                            enabled = !working, onDone = submit)
                                        CollectionPill("Change number", enabled = !working, onClick = onChangeNumber)
                                    }
                                    TelegramStep.PASSWORD -> OnboardingField(password, { password = it }, "Telegram password",
                                        KeyboardType.Password, secret = true, enabled = !working, onDone = submit)
                                    TelegramStep.REGISTER -> {
                                        OnboardingField(firstName, { firstName = it }, "First name", KeyboardType.Text,
                                            enabled = !working, next = true, onDone = { focus.moveFocus(FocusDirection.Down) })
                                        OnboardingField(lastName, { lastName = it }, "Last name (optional)", KeyboardType.Text,
                                            enabled = !working, onDone = submit)
                                    }
                                    TelegramStep.KEYS -> {
                                        OnboardingField(apiId, { apiId = it }, "API ID", KeyboardType.Number,
                                            next = true, onDone = { focus.moveFocus(FocusDirection.Down) })
                                        OnboardingField(apiHash, { apiHash = it }, "API hash", KeyboardType.Ascii,
                                            secret = true, onDone = submit)
                                    }
                                    TelegramStep.CONNECTED -> OnboardingCard {
                                        OnboardingDetail("01", "Your private shelf", "We'll reuse your Arc Player Server channel if it exists, or create it for you.")
                                        OnboardingDetail("02", "Add from Telegram", "Forward your movies into that channel, then come back to Arc Player.")
                                    }
                                    TelegramStep.CONNECTING -> OnboardingCard {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            CircularProgressIndicator(Modifier.size(20.dp), color = scheme.onSurface, strokeWidth = 2.dp)
                                            Text("Waiting for Telegram…", color = scheme.onSurface, fontSize = 14.sp)
                                        }
                                        Text("Taking longer? Check your connection. You can leave and come back without losing your library.",
                                            color = scheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
                                    }
                                    TelegramStep.RECOVERY -> Unit
                                }
                            }
                        }
                    }
                    // Connecting has no button of its own, so it must not reserve the footer's height.
                    if (page != TelegramStep.CONNECTING) {
                        Column(
                            Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = bottomPadding + 24.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            if (error != null && page != TelegramStep.WELCOME) {
                                Text(telegramAuthError(error), color = scheme.error, fontSize = 14.sp, lineHeight = 21.sp,
                                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                            }
                            when (page) {
                                TelegramStep.WELCOME -> {
                                    OnboardingAction("Connect Telegram", onClick = onStart)
                                    // A first launch arrives here without anyone having asked, so the way
                                    // out has to be as solid as the way in. The "Not now" up in the header
                                    // is a label, and a label is not something to lean on. It promises a
                                    // library rather than a device, because that is where leaving the setup
                                    // actually lands — with the On Device pill one tap away.
                                    CollectionPill(
                                        "Go to my library",
                                        Modifier.fillMaxWidth().heightIn(min = 56.dp),
                                        onClick = exit
                                    )
                                }
                                TelegramStep.PHONE -> OnboardingAction("Send code", phone.isNotBlank(), working, submit)
                                TelegramStep.CODE -> OnboardingAction("Verify code", code.trim().length in 5..64, working, submit)
                                TelegramStep.PASSWORD -> OnboardingAction("Unlock Telegram", password.isNotEmpty(), working, submit)
                                TelegramStep.REGISTER -> OnboardingAction("Create Telegram account", firstName.isNotBlank(), working, submit)
                                TelegramStep.KEYS -> OnboardingAction("Save and connect", (apiId.toIntOrNull() ?: 0) > 0 && apiHash.isNotBlank(), onClick = submit)
                                TelegramStep.CONNECTED -> OnboardingAction(if (hasCollection) "Open my collection" else "Set up my collection",
                                    loading = setupBusy, onClick = onSetup)
                                TelegramStep.RECOVERY -> if (state.stage == CloudAuthStage.CLOSED) {
                                    Text("Close and reopen Arc Player to reconnect to Telegram.", color = scheme.onSurfaceVariant,
                                        fontSize = 14.sp, lineHeight = 21.sp)
                                    OnboardingAction("Back to my library", onClick = exit)
                                } else OnboardingAction("Try again", enabled = !working, onClick = onStart)
                                TelegramStep.CONNECTING -> Unit
                            }
                            // The welcome page says the same thing now with a button the viewer can press.
                            if (page == TelegramStep.PHONE) {
                                Text("Optional, always. On-device playback works without Telegram.",
                                    color = scheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(32.dp))
        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)) { content() }
}

@Composable
private fun OnboardingDetail(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(number, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
            fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 3.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 21.sp)
        }
    }
}

@Composable
private fun OnboardingAction(label: String, enabled: Boolean = true, loading: Boolean = false, onClick: () -> Unit) {
    CollectionPill(Modifier.fillMaxWidth().heightIn(min = 56.dp), selected = true, enabled = enabled && !loading, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            Text(if (loading) "Please wait…" else label, color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

@Composable
private fun OnboardingField(
    value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardType,
    secret: Boolean = false, enabled: Boolean = true, next: Boolean = false, onDone: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = enabled,
        shape = RoundedCornerShape(24.dp),
        visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard, autoCorrectEnabled = false,
            imeAction = if (next) ImeAction.Next else ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }, onNext = { onDone() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = scheme.onSurface, unfocusedBorderColor = scheme.outlineVariant,
            focusedTextColor = scheme.onSurface, unfocusedTextColor = scheme.onSurface,
            disabledTextColor = scheme.onSurfaceVariant, cursorColor = scheme.onSurface,
            focusedLabelColor = scheme.onSurface, unfocusedLabelColor = scheme.onSurfaceVariant
        ))
}
