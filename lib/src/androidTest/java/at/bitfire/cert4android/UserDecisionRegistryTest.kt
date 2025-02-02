package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Semaphore
import kotlin.concurrent.thread

class UserDecisionRegistryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val certStore = CustomCertStore.getInstance(context)
    private val registry = UserDecisionRegistry.getInstance(context)

    private val testCert = TestCertificates.testCert


    @Before
    fun setUp() {
        mockkObject(NotificationUtils)
        mockkObject(registry)
        every { registry.requestDecision(any(), any(), any()) } returns Unit
    }

    @After
    fun cleanUp() {
        unmockkAll()
        certStore.clearUserDecisions()
    }


    @Test
    fun testCheck_FirstDecision_Negative() {
        every { registry.requestDecision(testCert, any(), any()) } answers {
            registry.onUserDecision(testCert, false)
        }
        assertFalse(runBlocking {
            registry.check(testCert, true)
        })
    }

    @Test
    fun testCheck_FirstDecision_Positive() {
        every { registry.requestDecision(testCert, any(), any()) } answers {
            registry.onUserDecision(testCert, true)
        }
        assertTrue(runBlocking {
            registry.check(testCert, true)
        })
    }

    @Test
    fun testCheck_MultipleDecisionsForSameCert_Negative() {
        val canSendFeedback = Semaphore(0)
        every { registry.requestDecision(testCert, any(), any()) } answers {
            thread {
                canSendFeedback.acquire()
                registry.onUserDecision(testCert, false)
            }
        }
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        runBlocking {
            repeat(5) {
                launch(Dispatchers.Default) {
                    results += registry.check(testCert, true)
                }
            }
            canSendFeedback.release()
        }
        synchronized(registry.pendingDecisions) {
            assertFalse(registry.pendingDecisions.containsKey(testCert))
        }
        assertEquals(5, results.size)
        assertTrue(results.all { !it })
        verify(exactly = 1) { registry.requestDecision(any(), any(), any()) }
    }

    @Test
    fun testCheck_MultipleDecisionsForSameCert_Positive() {
        val canSendFeedback = Semaphore(0)
        every { registry.requestDecision(testCert, any(), any()) } answers {
            thread {
                canSendFeedback.acquire()
                registry.onUserDecision(testCert, true)
            }
        }
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        runBlocking {
            repeat(5) {
                launch(Dispatchers.Default) {
                    results += registry.check(testCert, true)
                }
            }
            canSendFeedback.release()
        }
        synchronized(registry.pendingDecisions) {
            assertFalse(registry.pendingDecisions.containsKey(testCert))
        }
        assertEquals(5, results.size)
        assertTrue(results.all { it })
        verify(exactly = 1) { registry.requestDecision(any(), any(), any()) }
    }

    @Test
    fun testCheck_UserDecisionImpossible() {
        every { NotificationUtils.notificationsPermitted(any()) } returns false
        assertFalse(runBlocking {
            // should return instantly
            registry.check(testCert, false)
        })
        verify(inverse = true) {
            registry.requestDecision(any(), any(), any())
        }
    }

}