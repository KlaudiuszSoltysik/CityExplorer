package com.example.cityexplorer.util

import com.example.cityexplorer.data.dtos.CustomLocationDoubleTimestamp
import com.example.cityexplorer.data.repositories.HexagonRepository
import com.example.cityexplorer.data.util.CacheService
import com.example.cityexplorer.data.util.LocationTrackingService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import kotlin.reflect.full.callSuspend
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.jvm.isAccessible

@ExperimentalCoroutinesApi
class LocationTrackingServiceTest {
    private val cacheService = mockk<CacheService>(relaxed = true)
    private val hexagonRepository = mockk<HexagonRepository>(relaxed = true)

    private lateinit var service: LocationTrackingService

    @BeforeEach
    fun setup() {
        service = LocationTrackingService()

        service.cacheService = cacheService
        service.hexagonRepository = hexagonRepository

        setPrivateField(service, "isServiceRunning", true)
    }

    @Test
    fun `sendBatchData does nothing when buffer is empty`() = runTest {
        val buffer = getBuffer(service)
        Assertions.assertTrue(buffer.isEmpty())

        callSendBatchData(service)

        coVerify(exactly = 0) { hexagonRepository.postLocationBatch(any()) }
    }

    @Test
    fun `sendBatchData calls repository, clears buffer and resets counter on success`() = runTest {
        val location = CustomLocationDoubleTimestamp(52.0, 16.0, System.currentTimeMillis())
        val buffer = getBuffer(service)
        buffer.add(location)

        coEvery { hexagonRepository.postLocationBatch(any()) } returns true

        setPrivateField(service, "consecutiveFailedSendBatchData", 5)

        callSendBatchData(service)

        coVerify(exactly = 1) {
            hexagonRepository.postLocationBatch(match { dto ->
                dto.locations.size == 1 &&
                        dto.locations[0].latitude == 52.0
            })
        }

        Assertions.assertTrue(buffer.isEmpty())

        val errorCounter = getPrivateField<Int>(service, "consecutiveFailedSendBatchData")
        Assertions.assertEquals(0, errorCounter)
    }

    @Test
    fun `sendBatchData keeps data in buffer and increments counter on API failure (false)`() =
        runTest {
            val location = CustomLocationDoubleTimestamp(52.0, 16.0, System.currentTimeMillis())
            val buffer = getBuffer(service)
            buffer.add(location)

            coEvery { hexagonRepository.postLocationBatch(any()) } returns false

            setPrivateField(service, "consecutiveFailedSendBatchData", 0)

            callSendBatchData(service)

            coVerify(exactly = 1) { hexagonRepository.postLocationBatch(any()) }

            Assertions.assertEquals(1, buffer.size)

            val errorCounter = getPrivateField<Int>(service, "consecutiveFailedSendBatchData")
            Assertions.assertEquals(1, errorCounter)
        }

    @Test
    fun `sendBatchData increments counter when API throws Exception`() = runTest {
        val location = CustomLocationDoubleTimestamp(52.0, 16.0, System.currentTimeMillis())
        getBuffer(service).add(location)

        coEvery { hexagonRepository.postLocationBatch(any()) } throws RuntimeException("Network Error")

        setPrivateField(service, "consecutiveFailedSendBatchData", 10)

        callSendBatchData(service)

        Assertions.assertEquals(1, getBuffer(service).size)

        val errorCounter = getPrivateField<Int>(service, "consecutiveFailedSendBatchData")
        Assertions.assertEquals(11, errorCounter)
    }

    private suspend fun callSendBatchData(serviceInstance: LocationTrackingService) {
        val method = LocationTrackingService::class.declaredMemberFunctions
            .find { it.name == "sendBatchData" }
            ?: throw IllegalArgumentException("Method sendBatchData not found")

        method.isAccessible = true
        method.callSuspend(serviceInstance)
    }

    private fun getBuffer(serviceInstance: LocationTrackingService): MutableList<CustomLocationDoubleTimestamp> {
        val field = LocationTrackingService::class.java.getDeclaredField("locationBuffer")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(serviceInstance) as MutableList<CustomLocationDoubleTimestamp>
    }

    private fun setPrivateField(instance: Any, fieldName: String, value: Any?) {
        val field = instance::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(instance, value)
    }

    private fun <T> getPrivateField(instance: Any, fieldName: String): T {
        val field = instance::class.java.getDeclaredField(fieldName)
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return field.get(instance) as T
    }
}