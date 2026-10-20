package com.aeswox.arcplayer.cloud

/**
 * The login flow as a pure state machine.
 *
 * TDLib drives authorization by pushing `authorizationState` updates rather than by answering a
 * request, so "what does the UI ask for next" is a function of observed state, not of call order.
 * Keeping that function free of TDLib (and of Android) is what makes the flow testable: the bridge
 * only translates `TdApi` objects into [CloudAuthInput] values and executes [CloudAuthEffect]s.
 */
enum class CloudAuthStage {
    MISSING_CREDENTIALS,
    READY_TO_START,
    WAITING_FOR_PARAMETERS,
    WAITING_FOR_PHONE_NUMBER,
    REQUESTING_CODE,
    WAITING_FOR_CODE,
    CHECKING_CODE,
    WAITING_FOR_PASSWORD,
    WAITING_FOR_REGISTRATION,
    AUTHORIZED,
    CLOSED,
    FAILED
}

/** What the code Telegram just sent us actually is, so the prompt can be honest about it. */
data class CodeHint(
    val codeLength: Int = 0,
    val digitsOnly: Boolean = true,
    val allowFlashCall: Boolean = false,
    val allowMissedCall: Boolean = false,
)

sealed class CloudAuthInput {
    /** The user tapped "Connect", or the tab opened with credentials already stored. */
    data object StartRequested : CloudAuthInput()

    data class PhoneSubmitted(val phoneNumber: String) : CloudAuthInput()
    data class CodeSubmitted(val code: String) : CloudAuthInput()
    data class PasswordSubmitted(val password: String) : CloudAuthInput()
    data class RegistrationSubmitted(val firstName: String, val lastName: String) : CloudAuthInput()

    /** Observed translations of `TdApi.AuthorizationState`. */
    data object PhoneNumberNeeded : CloudAuthInput()
    data class CodeNeeded(val hint: CodeHint) : CloudAuthInput()
    data object PasswordNeeded : CloudAuthInput()
    data object RegistrationNeeded : CloudAuthInput()
    data object Authorized : CloudAuthInput()
    data object Closed : CloudAuthInput()
    data class Rejected(val message: String) : CloudAuthInput()
}

sealed class CloudAuthEffect {
    data object None : CloudAuthEffect()
    data object SendParameters : CloudAuthEffect()
    data class RequestCode(val phoneNumber: String) : CloudAuthEffect()
    data class SubmitCode(val code: String) : CloudAuthEffect()
    data class SubmitPassword(val password: String) : CloudAuthEffect()
    data class SubmitRegistration(val firstName: String, val lastName: String) : CloudAuthEffect()
}

data class CloudAuthState(
    val stage: CloudAuthStage,
    val codeHint: CodeHint? = null,
    val lastPhoneNumber: String = "",
    val error: String? = null,
    /**
     * The form to go back to if the request currently in flight is rejected.
     *
     * `CHECKING_CODE` covers a code, a password and a registration, so the stage alone cannot say
     * which one the user should be asked for again after `PHONE_CODE_INVALID`.
     */
    val retryStage: CloudAuthStage? = null,
    /**
     * Whether TDLib has taken `setTdlibParameters`.
     *
     * The client accepts that call exactly once for its whole life, and TDLib announces it wants
     * parameters at the same moment the tab asks on its own, so the machine has to be able to tell
     * "never sent" from "already in".
     */
    val parametersAccepted: Boolean = false,
)

data class CloudAuthTransition(val state: CloudAuthState, val effect: CloudAuthEffect)

object CloudAuthMachine {

    fun initial(hasCredentials: Boolean): CloudAuthState = CloudAuthState(
        stage = if (hasCredentials) CloudAuthStage.READY_TO_START else CloudAuthStage.MISSING_CREDENTIALS
    )

    fun transition(state: CloudAuthState, input: CloudAuthInput): CloudAuthTransition = when (input) {
        CloudAuthInput.StartRequested ->
            if (state.parametersAccepted ||
                state.stage == CloudAuthStage.MISSING_CREDENTIALS ||
                state.stage == CloudAuthStage.WAITING_FOR_PARAMETERS
            ) {
                // Either nothing to send with, or the parameters are already in flight or already
                // in. TDLib rejects a second setTdlibParameters, so re-entrant starts are silent.
                unchanged(state)
            } else {
                CloudAuthTransition(
                    state.copy(
                        stage = CloudAuthStage.WAITING_FOR_PARAMETERS,
                        error = null,
                        retryStage = CloudAuthStage.READY_TO_START
                    ),
                    CloudAuthEffect.SendParameters
                )
            }

        is CloudAuthInput.PhoneSubmitted -> {
            val cleaned = normalizePhoneNumber(input.phoneNumber)
            when {
                cleaned == null -> stayWithError(state, "That doesn't look like a phone number.")
                else -> CloudAuthTransition(
                    state.copy(
                        stage = CloudAuthStage.REQUESTING_CODE,
                        lastPhoneNumber = cleaned,
                        error = null,
                        retryStage = CloudAuthStage.WAITING_FOR_PHONE_NUMBER
                    ),
                    CloudAuthEffect.RequestCode(cleaned)
                )
            }
        }

        is CloudAuthInput.CodeSubmitted -> {
            val cleaned = input.code.trim()
            when {
                cleaned.length !in CODE_LENGTH_RANGE ->
                    stayWithError(state, "Codes are ${CODE_LENGTH_RANGE.first}-${CODE_LENGTH_RANGE.last} characters.")
                else -> CloudAuthTransition(
                    state.copy(
                        stage = CloudAuthStage.CHECKING_CODE,
                        error = null,
                        retryStage = CloudAuthStage.WAITING_FOR_CODE
                    ),
                    CloudAuthEffect.SubmitCode(cleaned)
                )
            }
        }

        is CloudAuthInput.PasswordSubmitted ->
            if (input.password.isEmpty()) {
                stayWithError(state, "Enter your two-step verification password.")
            } else {
                CloudAuthTransition(
                    state.copy(
                        stage = CloudAuthStage.CHECKING_CODE,
                        error = null,
                        retryStage = CloudAuthStage.WAITING_FOR_PASSWORD
                    ),
                    CloudAuthEffect.SubmitPassword(input.password)
                )
            }

        is CloudAuthInput.RegistrationSubmitted -> {
            val first = input.firstName.trim()
            if (first.isEmpty()) {
                stayWithError(state, "A first name is required.")
            } else {
                CloudAuthTransition(
                    state.copy(
                        stage = CloudAuthStage.CHECKING_CODE,
                        error = null,
                        retryStage = CloudAuthStage.WAITING_FOR_REGISTRATION
                    ),
                    CloudAuthEffect.SubmitRegistration(first, input.lastName.trim())
                )
            }
        }

        CloudAuthInput.PhoneNumberNeeded ->
            settle(state, CloudAuthStage.WAITING_FOR_PHONE_NUMBER)

        is CloudAuthInput.CodeNeeded ->
            settle(state.copy(codeHint = input.hint), CloudAuthStage.WAITING_FOR_CODE)

        CloudAuthInput.PasswordNeeded -> settle(state, CloudAuthStage.WAITING_FOR_PASSWORD)
        CloudAuthInput.RegistrationNeeded -> settle(state, CloudAuthStage.WAITING_FOR_REGISTRATION)
        CloudAuthInput.Authorized -> settle(state, CloudAuthStage.AUTHORIZED)
        CloudAuthInput.Closed -> settle(state, CloudAuthStage.CLOSED)

        is CloudAuthInput.Rejected -> {
            // Go back to whichever form the user just filled in, so the fix is one keystroke away.
            val back = state.retryStage ?: CloudAuthStage.FAILED
            CloudAuthTransition(
                state.copy(stage = back, error = input.message, retryStage = null),
                CloudAuthEffect.None
            )
        }
    }

    /**
     * Accepts `+1 (555) 010-9922`, `5550109922`, or a bare username-style `@handle` for bots.
     *
     * Returns the E.164-ish form TDLib wants, or null when there are too few digits to be real.
     */
    fun normalizePhoneNumber(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val digits = StringBuilder()
        var plusAllowed = true
        for (ch in trimmed) {
            when {
                ch.isDigit() -> digits.append(ch)
                ch == '+' && plusAllowed && digits.isEmpty() -> digits.append(ch)
                ch == '+' -> plusAllowed = false
                ch == '-' || ch == ' ' || ch == '(' || ch == ')' -> Unit
                else -> return null
            }
        }
        if (digits.length !in PHONE_DIGITS_RANGE) return null
        return digits.toString()
    }

    private val CODE_LENGTH_RANGE = 5..64
    private val PHONE_DIGITS_RANGE = 4..20

    private fun settle(state: CloudAuthState, stage: CloudAuthStage): CloudAuthTransition {
        // Any state past waitTdlibParameters is proof that the parameters landed.
        val seen = state.copy(parametersAccepted = true)
        if (seen.stage == stage) return CloudAuthTransition(seen, CloudAuthEffect.None)
        return CloudAuthTransition(
            seen.copy(stage = stage, error = null, retryStage = null),
            CloudAuthEffect.None
        )
    }

    private fun unchanged(state: CloudAuthState) = CloudAuthTransition(state, CloudAuthEffect.None)

    private fun stayWithError(state: CloudAuthState, message: String) = CloudAuthTransition(
        state.copy(error = message),
        CloudAuthEffect.None
    )
}
