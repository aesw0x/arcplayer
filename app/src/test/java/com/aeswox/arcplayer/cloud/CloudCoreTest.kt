package com.aeswox.arcplayer.cloud

import com.aeswox.arcplayer.stream.DeliveredRun
import com.aeswox.arcplayer.stream.StreamWindow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudIdsTest {

    @Test
    fun cloudKeysAreAlwaysNegative() {
        val samples = listOf(
            CloudIds.forVideo(1L, 1L, "1"),
            CloudIds.forVideo(Long.MIN_VALUE, Long.MAX_VALUE, "f".repeat(64)),
            CloudIds.forVideo(0L, 0L, ""),
            CloudIds.forChat(Long.MAX_VALUE)
        )
        samples.forEach { assertTrue("expected negative, got $it", it < 0L) }
    }

    @Test
    fun mediaStoreIdsNeverCollideWithCloudKeys() {
        // MediaStore _ID is a positive counter; a negative key can never alias one.
        assertTrue(CloudIds.isCloud(CloudIds.forVideo(42L, 7L, "9")))
        assertTrue(!CloudIds.isCloud(1L))
        assertTrue(!CloudIds.isCloud(9_876_543_210L))
    }

    @Test
    fun keyIsDeterministicPerVideo() {
        val a = CloudIds.forVideo(1234L, 5678L, "18000000090")
        val b = CloudIds.forVideo(1234L, 5678L, "18000000090")
        assertEquals(a, b)
    }

    @Test
    fun differentVideosInDifferentChatsGetDifferentKeys() {
        val a = CloudIds.forVideo(1234L, 5678L, "90")
        val b = CloudIds.forVideo(1235L, 5678L, "90")
        val c = CloudIds.forVideo(1234L, 5679L, "90")
        val d = CloudIds.forVideo(1234L, 5678L, "91")
        assertNotEquals(a, b)
        assertNotEquals(a, c)
        assertNotEquals(a, d)
    }

    @Test
    fun chatKeysRoundTripIdentity() {
        assertEquals(CloudIds.forChat(77L), CloudIds.forChat(77L))
        assertNotEquals(CloudIds.forChat(77L), CloudIds.forChat(78L))
    }
}

class CloudAuthMachineTest {

    private fun start(hasCredentials: Boolean = true) = CloudAuthMachine.initial(hasCredentials)

    @Test
    fun missingCredentialsBlockStart() {
        val t = CloudAuthMachine.transition(start(hasCredentials = false), CloudAuthInput.StartRequested)
        assertEquals(CloudAuthStage.MISSING_CREDENTIALS, t.state.stage)
        assertTrue(t.effect is CloudAuthEffect.None)
    }

    @Test
    fun startSendsParameters() {
        val t = CloudAuthMachine.transition(start(), CloudAuthInput.StartRequested)
        assertEquals(CloudAuthStage.WAITING_FOR_PARAMETERS, t.state.stage)
        assertTrue(t.effect is CloudAuthEffect.SendParameters)
    }

    @Test
    fun phoneSubmittedIsNormalisedAndRequestsACode() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_PHONE_NUMBER),
            CloudAuthInput.PhoneSubmitted("+1 (555) 010-9922")
        )
        assertEquals(CloudAuthStage.REQUESTING_CODE, t.state.stage)
        assertEquals(CloudAuthEffect.RequestCode("+15550109922"), t.effect)
        assertEquals("+15550109922", t.state.lastPhoneNumber)
    }

    @Test
    fun nonsensePhoneNumberKeepsTheStageAndExplainsItself() {
        val before = CloudAuthState(CloudAuthStage.WAITING_FOR_PHONE_NUMBER)
        val t = CloudAuthMachine.transition(before, CloudAuthInput.PhoneSubmitted("not a number"))
        assertEquals(CloudAuthStage.WAITING_FOR_PHONE_NUMBER, t.state.stage)
        assertTrue(t.effect is CloudAuthEffect.None)
        assertTrue(t.state.error != null)
    }

    @Test
    fun normalizeRejectsLettersAndTooFewDigits() {
        assertNull(CloudAuthMachine.normalizePhoneNumber("abc12345"))
        assertNull(CloudAuthMachine.normalizePhoneNumber("12"))
        assertEquals("+919876543210", CloudAuthMachine.normalizePhoneNumber("+91 98765 43210"))
    }

    @Test
    fun shortCodeIsRejectedBeforeItCostsARoundTrip() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_CODE),
            CloudAuthInput.CodeSubmitted("123")
        )
        assertEquals(CloudAuthStage.WAITING_FOR_CODE, t.state.stage)
        assertTrue(t.state.error != null)
    }

    @Test
    fun alphanumericCodesAreAcceptedBecauseTelegramSendsThem() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_CODE),
            CloudAuthInput.CodeSubmitted("A1B2C3")
        )
        assertEquals(CloudAuthStage.CHECKING_CODE, t.state.stage)
        assertEquals(CloudAuthEffect.SubmitCode("A1B2C3"), t.effect)
    }

    @Test
    fun passwordAndRegistrationBothAdvance() {
        val pw = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_PASSWORD),
            CloudAuthInput.PasswordSubmitted("hunter2")
        )
        assertEquals(CloudAuthEffect.SubmitPassword("hunter2"), pw.effect)

        val reg = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_REGISTRATION),
            CloudAuthInput.RegistrationSubmitted(" Alice ", "R")
        )
        assertEquals(CloudAuthEffect.SubmitRegistration("Alice", "R"), reg.effect)
    }

    @Test
    fun emptyRegistrationNameNeverReachesTheNetwork() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_REGISTRATION),
            CloudAuthInput.RegistrationSubmitted("   ", "R")
        )
        assertTrue(t.effect is CloudAuthEffect.None)
        assertTrue(t.state.error != null)
    }

    @Test
    fun observedStatesSettleWithoutEffects() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.CHECKING_CODE),
            CloudAuthInput.Authorized
        )
        assertEquals(CloudAuthStage.AUTHORIZED, t.state.stage)
        assertNull(t.state.error)
        assertTrue(t.effect is CloudAuthEffect.None)
    }

    @Test
    fun duplicateAuthorizedUpdateIsANoOp() {
        val already = CloudAuthState(CloudAuthStage.AUTHORIZED, parametersAccepted = true)
        val t = CloudAuthMachine.transition(already, CloudAuthInput.Authorized)
        assertEquals(already, t.state)
    }

    @Test
    fun reachingAuthorizedRecordsThatParametersLanded() {
        val t = CloudAuthMachine.transition(start(), CloudAuthInput.PhoneNumberNeeded)
        assertTrue(t.state.parametersAccepted)
    }

    @Test
    fun aSecondStartAfterTheParametersLandedSendsNothing() {
        // TDLib announces waitTdlibParameters at the same moment the tab asks on its own. Only the
        // first may send; a second setTdlibParameters is refused and reads to the user as a login failure.
        val sent = CloudAuthMachine.transition(start(), CloudAuthInput.StartRequested)
        assertTrue(sent.effect is CloudAuthEffect.SendParameters)
        val observed = CloudAuthMachine.transition(sent.state, CloudAuthInput.StartRequested)
        assertTrue(observed.effect is CloudAuthEffect.None)
        assertEquals(CloudAuthStage.WAITING_FOR_PARAMETERS, observed.state.stage)
    }

    @Test
    fun aStartAfterAnObservedStateNeverResendsParameters() {
        val waitingForPhone = CloudAuthMachine.transition(start(), CloudAuthInput.PhoneNumberNeeded).state
        val t = CloudAuthMachine.transition(waitingForPhone, CloudAuthInput.StartRequested)
        assertTrue(t.effect is CloudAuthEffect.None)
    }

    @Test
    fun codeHintSurvivesTheTransitionToWaitingForCode() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.REQUESTING_CODE),
            CloudAuthInput.CodeNeeded(CodeHint(codeLength = 5, digitsOnly = true))
        )
        assertEquals(CloudAuthStage.WAITING_FOR_CODE, t.state.stage)
        assertEquals(5, t.state.codeHint?.codeLength)
    }

    @Test
    fun rejectionLandsOnFailedWithTheReason() {
        val t = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.CHECKING_CODE),
            CloudAuthInput.Rejected("PHONE_CODE_INVALID")
        )
        assertEquals(CloudAuthStage.FAILED, t.state.stage)
        assertEquals("PHONE_CODE_INVALID", t.state.error)
    }

    @Test
    fun failureCanBeRetried() {
        val failed = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.CHECKING_CODE),
            CloudAuthInput.Rejected("AUTH_FAILED")
        ).state
        val retry = CloudAuthMachine.transition(failed, CloudAuthInput.StartRequested)
        assertEquals(CloudAuthStage.WAITING_FOR_PARAMETERS, retry.state.stage)
        assertNull(retry.state.error)
    }

    @Test
    fun aRejectedCodeReturnsToTheCodeFormNotToThePhoneForm() {
        val checking = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_CODE),
            CloudAuthInput.CodeSubmitted("ABCDE")
        ).state
        val rejected = CloudAuthMachine.transition(checking, CloudAuthInput.Rejected("PHONE_CODE_INVALID"))
        assertEquals(CloudAuthStage.WAITING_FOR_CODE, rejected.state.stage)
        assertEquals("PHONE_CODE_INVALID", rejected.state.error)
        assertTrue(rejected.effect is CloudAuthEffect.None)
    }

    @Test
    fun aRejectedPasswordReturnsToThePasswordForm() {
        val checking = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_PASSWORD),
            CloudAuthInput.PasswordSubmitted("hunter2")
        ).state
        assertEquals(CloudAuthStage.CHECKING_CODE, checking.stage)
        val rejected = CloudAuthMachine.transition(checking, CloudAuthInput.Rejected("PASSWORD_INCORRECT"))
        assertEquals(CloudAuthStage.WAITING_FOR_PASSWORD, rejected.state.stage)
    }

    @Test
    fun anObservedStateClearsThePendingRetryTarget() {
        val checking = CloudAuthMachine.transition(
            CloudAuthState(CloudAuthStage.WAITING_FOR_CODE),
            CloudAuthInput.CodeSubmitted("ABCDE")
        ).state
        val ready = CloudAuthMachine.transition(checking, CloudAuthInput.Authorized)
        assertNull(ready.state.retryStage)
        assertNull(ready.state.error)
    }
}

class CloudWindowPlannerTest {

    private fun wholeFile() = WindowFacts(
        fileSizeBytes = 100L * 1024 * 1024,
        downloadOffset = 0L,
        downloadedPrefixSize = 100L * 1024 * 1024,
        isDownloadingActive = false,
        isDownloadingCompleted = true
    )

    @Test
    fun completedFilesNeedNoWork() {
        assertTrue(CloudWindowPlanner.plan(50L * 1024 * 1024, wholeFile()) is WindowCommand.None)
    }

    @Test
    fun readingInsideALiveWindowLeavesTheTransferAlone() {
        val facts = WindowFacts(
            fileSizeBytes = 200L * 1024 * 1024,
            downloadOffset = 10L * 1024 * 1024,
            downloadedPrefixSize = 64L * 1024 * 1024,
            isDownloadingActive = true,
            isDownloadingCompleted = false
        )
        assertTrue(CloudWindowPlanner.plan(12L * 1024 * 1024, facts) is WindowCommand.None)
    }

    @Test
    fun seekingBackwardsRestartsJustBehindTheNewPosition() {
        val facts = WindowFacts(
            fileSizeBytes = 200L * 1024 * 1024,
            downloadOffset = 100L * 1024 * 1024,
            downloadedPrefixSize = 64L * 1024 * 1024,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(20L * 1024 * 1024, facts)
        assertTrue(command is WindowCommand.Fetch)
        command as WindowCommand.Fetch
        assertEquals(18L * 1024 * 1024, command.offset)
        assertEquals(0L, command.offset % StreamWindow.REQUEST_ALIGN_BYTES)
    }

    @Test
    fun exhaustedIdleWindowPushesForward() {
        val facts = WindowFacts(
            fileSizeBytes = 200L * 1024 * 1024,
            downloadOffset = 20L * 1024 * 1024,
            downloadedPrefixSize = 4L * 1024 * 1024,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(22L * 1024 * 1024, facts)
        assertTrue(command is WindowCommand.Fetch)
        assertEquals(24L * 1024 * 1024, (command as WindowCommand.Fetch).offset)
    }

    @Test
    fun aFetchNearTheEndOfTheFileIsClampedNotUnbounded() {
        val size = 100L * 1024 * 1024
        val facts = WindowFacts(
            fileSizeBytes = size,
            downloadOffset = 98L * 1024 * 1024,
            downloadedPrefixSize = 1024L * 1024,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(99L * 1024 * 1024, facts)
        command as WindowCommand.Fetch
        assertEquals(97L * 1024 * 1024, command.offset)
        assertEquals(3L * 1024 * 1024, command.limit)
        assertTrue(command.offset + command.limit <= size)
    }

    @Test
    fun aFetchNeverRunsPastTheEndOfTheFile() {
        val size = 50L * 1024 * 1024
        val facts = WindowFacts(
            fileSizeBytes = size,
            downloadOffset = 50L * 1024 * 1024,
            downloadedPrefixSize = 0L,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(50L * 1024 * 1024, facts)
        command as WindowCommand.Fetch
        assertTrue(command.offset + command.limit <= size)
        assertTrue(command.limit > 0L)
    }

    @Test
    fun unknownFileSizeStillStartsSomething() {
        val facts = WindowFacts(
            fileSizeBytes = 0L,
            downloadOffset = 0L,
            downloadedPrefixSize = 0L,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(0L, facts)
        command as WindowCommand.Fetch
        assertEquals(0L, command.offset)
        assertEquals(CloudWindowPlanner.DEFAULT_WINDOW_BYTES, command.limit)
    }

    @Test
    fun anInFlightWindowIsJudgedByWhatItAskedForNotByWhatHasLanded() {
        val facts = WindowFacts(
            fileSizeBytes = 200L * 1024 * 1024,
            downloadOffset = 20L * 1024 * 1024,
            downloadedPrefixSize = 0L,
            isDownloadingActive = true,
            isDownloadingCompleted = false
        )
        // TDLib names the window it began fetching long before any of it has landed. Judged against
        // the empty prefix, every player tick cancels the transfer that is about to feed it.
        assertTrue(CloudWindowPlanner.plan(24L * 1024 * 1024, facts) is WindowCommand.None)
        assertTrue(CloudWindowPlanner.plan(150L * 1024 * 1024, facts) is WindowCommand.Fetch)
    }

    /** The window Telegram opens the moment the previous one is exhausted, with nothing landed yet. */
    private fun justExtendedForward() = WindowFacts(
        fileSizeBytes = 200L * 1024 * 1024,
        downloadOffset = 100L * 1024 * 1024,
        downloadedPrefixSize = 0L,
        isDownloadingActive = true,
        isDownloadingCompleted = false
    )

    @Test
    fun aForwardWindowIsNotCancelledWhileTheHeadStillReadsCachedBytes() {
        // Measured on the Note at every 64 MB boundary: the extension was asked for at the byte the
        // previous window finished delivering, and one tick later the head — still a few MB behind
        // that seam — was read as a rewind. The cancel re-downloaded about 8 MB of film already on
        // the disk, and the play head overran the landing edge into the hole behind it.
        val delivered = DeliveredRun(80L * 1024 * 1024, 100L * 1024 * 1024)
        assertTrue(CloudWindowPlanner.plan(94L * 1024 * 1024, justExtendedForward(), delivered) is WindowCommand.None)
        assertTrue(CloudWindowPlanner.plan(99L * 1024 * 1024, justExtendedForward(), delivered) is WindowCommand.None)
    }

    @Test
    fun aRewindOntoBytesNoReplyHasEverCoveredStillMovesTheWindowBack() {
        // The rule the fix above narrows has to keep working: a scrub into a part of the film that
        // has not arrived has no bytes anywhere near it, and only a request aimed backwards reaches it.
        val delivered = DeliveredRun(80L * 1024 * 1024, 100L * 1024 * 1024)
        val command = CloudWindowPlanner.plan(40L * 1024 * 1024, justExtendedForward(), delivered)
        command as WindowCommand.Fetch
        assertEquals(38L * 1024 * 1024, command.offset)

        // With nothing remembered about the file, the same position is still judged a rewind.
        assertTrue(CloudWindowPlanner.plan(94L * 1024 * 1024, justExtendedForward()) is WindowCommand.Fetch)
    }

    @Test
    fun anIdleWindowParkedNowhereDoesNotCountAsRoomAhead() {
        // The state a cancelled deep seek leaves behind: a span parked at the offset it was aiming
        // for, no bytes of its own, and the play head back at the start of a 2 GB file. Judged on the
        // span alone the head has the whole rest of the film ahead of it, so nothing was ever asked
        // for and the screen held at 0% cached, 0 KB/s, with no transfer running.
        val facts = WindowFacts(
            fileSizeBytes = 2_400_000_000L,
            downloadOffset = 2_341_470_208L,
            downloadedPrefixSize = 0L,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(
            2_606L,
            facts,
            DeliveredRun(0L, 2L * 1024 * 1024)
        )
        command as WindowCommand.Fetch
        assertEquals(2L * 1024 * 1024, command.offset)
        assertTrue(command.limit > 0L)
    }

    @Test
    fun landedBytesBeatAStaleMemoryOfThem() {
        // Where the prefix says film has arrived, that is the ground to extend from — even if the
        // remembered run ends far behind and would pull the request back over bytes already on disk.
        val facts = WindowFacts(
            fileSizeBytes = 200L * 1024 * 1024,
            downloadOffset = 20L * 1024 * 1024,
            downloadedPrefixSize = 64L * 1024 * 1024,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(
            80L * 1024 * 1024,
            facts,
            DeliveredRun(20L * 1024 * 1024, 40L * 1024 * 1024)
        )
        assertEquals(84L * 1024 * 1024, (command as WindowCommand.Fetch).offset)
    }

    @Test
    fun aRewindOntoLandedBytesWaitsForNothing() {
        // The other side of the same rule: a head sitting inside film that really is on disk, with a
        // parked window far ahead, must not spend a round trip re-fetching it.
        val facts = WindowFacts(
            fileSizeBytes = 2_400_000_000L,
            downloadOffset = 100L * 1024 * 1024,
            downloadedPrefixSize = 0L,
            isDownloadingActive = false,
            isDownloadingCompleted = false
        )
        val command = CloudWindowPlanner.plan(
            1L * 1024 * 1024,
            facts,
            DeliveredRun(0L, 20L * 1024 * 1024)
        )
        assertTrue(command is WindowCommand.None)
    }

    @Test
    fun playHeadMapsToBytesInProportionToTheFile() {
        val size = 100L * 1024 * 1024
        assertEquals(0L, CloudWindowPlanner.readPositionBytes(0L, 60_000L, size))
        assertEquals(size / 2, CloudWindowPlanner.readPositionBytes(30_000L, 60_000L, size))
        assertEquals(size, CloudWindowPlanner.readPositionBytes(60_000L, 60_000L, size))
    }

    @Test
    fun aPlayHeadPastTheEndNeverReadsPastTheFile() {
        val size = 10L * 1024 * 1024
        assertEquals(size, CloudWindowPlanner.readPositionBytes(99_000L, 60_000L, size))
    }

    @Test
    fun anUnknownDurationOrSizeReadsFromTheStart() {
        assertEquals(0L, CloudWindowPlanner.readPositionBytes(30_000L, 0L, 10L * 1024 * 1024))
        assertEquals(0L, CloudWindowPlanner.readPositionBytes(30_000L, 60_000L, 0L))
        assertEquals(0L, CloudWindowPlanner.readPositionBytes(-5L, 60_000L, 10L * 1024 * 1024))
    }
}
