package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.Teaching
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TeachingRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun createTeaching_validPayload_createsDocumentAndReturnsId(): Unit = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = TeachingRepository(firestore)

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createTeaching(uid, "preference", "I prefer concise answers", "high")
    }
    assertTrue(result.isSuccess)
    val teachingId = result.getOrThrow()
    assertTrue(teachingId.isNotEmpty())
  }

  @Test
  fun getTeachingsOnce_authenticatedOwner_returnsMatchingTeachings(): Unit = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = TeachingRepository(firestore)

    val teachingId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createTeaching(uid, "rule", "Always respond in bullet points", "normal").getOrThrow()
    }

    val listResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.getTeachingsOnce(uid)
    }
    assertTrue(listResult.isSuccess)
    val list = listResult.getOrThrow()
    assertTrue(list.any { it.id == teachingId })
  }

  @Test
  fun observeTeachings_authenticatedOwner_emitsRealtimeUpdates(): Unit = runBlocking {
    val uid = signInTestUser(ALICE_EMAIL)
    val repository = TeachingRepository(firestore)

    val teachingId = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createTeaching(uid, "personal", "My dog is named Rusty", "normal").getOrThrow()
    }

    val emitted = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeTeachings(uid).first { list -> list.any { it.id == teachingId } }
    }
    assertTrue(emitted.any { it.id == teachingId })
  }

  @Test
  fun getTeachings_crossUserAccess_failsWithPermissionDenied(): Unit = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = TeachingRepository(firestore)
    val teachingId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.createTeaching(aliceUid, "work", "Working on AI Studio app", "core").getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = TeachingRepository(firestore)

    val exception = assertThrows(Exception::class.java) {
      runBlocking {
        withTimeout(DEFAULT_TIMEOUT_MS) {
          firestore.collection("users").document(aliceUid).collection("teachings").document(teachingId).get().await()
        }
      }
    }
    val firestoreException = exception.unwrapFirestoreException()
    assertNotNull(firestoreException)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
  }

  @Test
  fun observeTeachings_unauthenticatedUser_failsWithPermissionDenied(): Unit = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    auth.signOut()

    val repository = TeachingRepository(firestore)
    val exception = assertThrows(Exception::class.java) {
      runBlocking {
        withTimeout(FLOW_TIMEOUT_MS) {
          repository.observeTeachings(aliceUid).first()
        }
      }
    }
    val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
      "Expected nested FirebaseFirestoreException, got $exception"
    }
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
  }

  private companion object {
    const val ALICE_EMAIL = "alice@test.com"
    const val BOB_EMAIL = "bob@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 4000L
  }
}
