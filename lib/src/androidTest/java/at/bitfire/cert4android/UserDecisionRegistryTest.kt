package at.bitfire.cert4android

import androidx.test.platform.app.InstrumentationRegistry
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.concurrent.Semaphore

class UserDecisionRegistryTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val certStore = CustomCertStore.getInstance(context)
    private val registry = UserDecisionRegistry.getInstance(context)

    private val testCert = TestCertificates.testCert


    @Before
    fun setUp() {
        mockkObject(NotificationUtils)
        mockkObject(registry)
    }

    @After
    fun cleanUp() {
        unmockkAll()
        certStore.clearUserDecisions()
    }


    @Test
    fun testCheck_FirstDecision_Negative() {
        assertFalse(runBlocking {
            registry.check(testCert, this) { false }
        })
    }

    @Test
    fun testCheck_FirstDecision_Positive() {
        assertTrue(runBlocking {
            registry.check(testCert, this) { true }
        })
    }

    @Test
    fun testCheck_MultipleDecisionsForSameCert_Negative() {
        val canSendFeedback = Semaphore(0)
        val getUserDecision: suspend (X509Certificate) -> Boolean = mockk {
            coEvery { this@mockk(testCert) } coAnswers {
                canSendFeedback.acquire()
                false
            }
        }
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        runBlocking {
            repeat(5) {
                launch(Dispatchers.Default) {
                    results += registry.check(testCert, this, getUserDecision)
                }
            }
            canSendFeedback.release()
        }
        synchronized(registry.pendingDecisions) {
            assertFalse(registry.pendingDecisions.containsKey(testCert))
        }
        assertEquals(5, results.size)
        assertTrue(results.all { !it })
        coVerify(exactly = 1) { getUserDecision(testCert) }
    }

    @Test
    fun testCheck_MultipleDecisionsForSameCert_Positive() {
        val canSendFeedback = Semaphore(0)
        val getUserDecision: suspend (X509Certificate) -> Boolean = mockk {
            coEvery { this@mockk(testCert) } coAnswers {
                canSendFeedback.acquire()
                true
            }
        }
        val results = Collections.synchronizedList(mutableListOf<Boolean>())
        runBlocking {
            repeat(5) {
                launch(Dispatchers.Default) {
                    results += registry.check(testCert, this, getUserDecision)
                }
            }
            canSendFeedback.release()
        }
        synchronized(registry.pendingDecisions) {
            assertFalse(registry.pendingDecisions.containsKey(testCert))
        }
        assertEquals(5, results.size)
        assertTrue(results.all { it })
        coVerify(exactly = 1) { getUserDecision(testCert) }
    }

}