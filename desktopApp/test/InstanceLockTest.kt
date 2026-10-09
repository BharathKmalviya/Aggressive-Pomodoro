package com.pomodoro.platform

import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstanceLockTest {
    @Test fun transientMailboxWriteFailureIsRetriedWithinTheActivationDeadline() = runBlocking {
        profile { directory ->
            assertNotNull(InstanceLock.acquire(directory)).use { owner ->
                val conflict = Files.createDirectory(directory.resolve("activation.request"))
                val blocker = Files.writeString(conflict.resolve("temporary-conflict"), "disposable fixture")
                val launcher = async(Dispatchers.IO) { InstanceLock.activateExisting(directory, 2_000) }
                try {
                    delay(100)
                    assertFalse(launcher.isCompleted, "A temporary mailbox write failure must remain retryable")
                } finally {
                    Files.delete(blocker)
                    Files.delete(conflict)
                }
                val token = awaitRequest(owner)
                owner.acknowledgeActivation(token)
                assertTrue(launcher.await())
                assertNull(InstanceLock.acquire(directory))
            }
        }
    }

    @Test fun oneOwnerAcceptsLauncherActivationWithoutTouchingTheSnapshot() = runBlocking {
        profile { directory ->
            val snapshot = directory.resolve("session.properties")
            Files.writeString(snapshot, "existing timer, settings, tasks and credit")
            val original = Files.readAllBytes(snapshot)
            assertNotNull(InstanceLock.acquire(directory)).use { owner ->
                assertNull(InstanceLock.acquire(directory))
                val launcher = async(Dispatchers.IO) { InstanceLock.activateExisting(directory, 2_000) }
                val token = awaitRequest(owner)
                owner.acknowledgeActivation(token)
                assertTrue(launcher.await())
                assertNull(owner.activationRequest())
                assertTrue(original.contentEquals(Files.readAllBytes(snapshot)))
            }
            assertFalse(Files.exists(directory.resolve("activation.request")))
            assertFalse(Files.exists(directory.resolve("activation.response")))
            assertNotNull(InstanceLock.acquire(directory)).close()
        }
    }

    @Test fun startupRequestIsRetainedUntilTheUiIsReadyAndOldRequestsDoNotReplay() = runBlocking {
        profile { directory ->
            val stale = UUID.randomUUID().toString()
            Files.writeString(directory.resolve("activation.request"), stale)
            Files.writeString(directory.resolve("activation.response"), stale)
            assertNotNull(InstanceLock.acquire(directory)).use { owner ->
                assertNull(owner.activationRequest())
                val launcher = async(Dispatchers.IO) { InstanceLock.activateExisting(directory, 2_000) }
                withTimeout(1_000) {
                    while (!Files.exists(directory.resolve("activation.request"))) delay(5)
                }
                // The owner is loading its UI; no activation polling has happened yet.
                assertFalse(launcher.isCompleted)
                val token = assertNotNull(owner.activationRequest())
                owner.acknowledgeActivation(token)
                assertTrue(launcher.await())
                assertNull(owner.activationRequest())
            }
            assertNotNull(InstanceLock.acquire(directory)).use { owner -> assertNull(owner.activationRequest()) }
        }
    }

    @Test fun repeatedAndCompetingLaunchesAreAcknowledgedByOneOwner() = runBlocking {
        profile { directory ->
            assertNotNull(InstanceLock.acquire(directory)).use { owner ->
                val launchers = List(2) { async(Dispatchers.IO) { InstanceLock.activateExisting(directory, 3_000) } }
                val tokens = mutableSetOf<String>()
                withTimeout(4_000) {
                    while (launchers.any { !it.isCompleted }) {
                        owner.activationRequest()?.let { token ->
                            tokens += token
                            owner.acknowledgeActivation(token)
                        }
                        delay(5)
                    }
                }
                assertTrue(launchers.all { it.await() })
                assertEquals(2, tokens.size)
                assertNull(InstanceLock.acquire(directory))
            }
        }
    }

    @Test fun malformedOversizedAndUndeliverableRequestsNeverAuthorizeAnotherOwner() = runBlocking {
        profile { directory ->
            assertNotNull(InstanceLock.acquire(directory)).use { owner ->
                for (text in listOf("open", "x".repeat(36), "x".repeat(100_000))) {
                    Files.writeString(directory.resolve("activation.request"), text)
                    assertNull(owner.activationRequest())
                }
                assertFalse(InstanceLock.activateExisting(directory, 75)) // Owner has not accepted it.
                assertNull(InstanceLock.acquire(directory))
            }
            val absent = directory.resolve("absent-profile")
            assertFalse(InstanceLock.activateExisting(absent, 75))
            assertFalse(Files.exists(absent))
        }
    }

    @Test fun failedAcknowledgementDoesNotRepeatedlyStealFocusAndCloseStillReleasesOwnership() = runBlocking {
        profile { directory ->
            val owner = assertNotNull(InstanceLock.acquire(directory))
            val token = UUID.randomUUID().toString()
            Files.writeString(directory.resolve("activation.request"), token)
            assertEquals(token, owner.activationRequest())
            val response = Files.createDirectory(directory.resolve("activation.response"))
            Files.writeString(response.resolve("block-cleanup"), "disposable test fixture")
            assertFailsWith<IOException> { owner.acknowledgeActivation(token) }
            // A response write failure must not repeatedly deliver the same foreground request.
            assertNull(owner.activationRequest())
            owner.close()
            owner.close()
            assertNull(owner.activationRequest())
            assertNotNull(InstanceLock.acquire(directory)).use { replacement ->
                assertNull(replacement.activationRequest())
            }
            Files.delete(response.resolve("block-cleanup"))
            Files.delete(response)
            assertNotNull(InstanceLock.acquire(directory)).close()
        }
    }

    private suspend fun awaitRequest(owner: InstanceLock): String = withTimeout(1_500) {
        var request = owner.activationRequest()
        while (request == null) { delay(5); request = owner.activationRequest() }
        request
    }

    private suspend fun profile(block: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("pomodoro-activation-test")
        try { block(directory) } finally {
            check(directory.toAbsolutePath().normalize().startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize()))
            directory.toFile().deleteRecursively()
        }
    }
}
