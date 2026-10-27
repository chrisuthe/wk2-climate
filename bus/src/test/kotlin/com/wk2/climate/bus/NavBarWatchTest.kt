package com.wk2.climate.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavBarWatchTest {

    private val periodMs = 250L

    @Test
    fun `the current value is emitted at once`() = runTest {
        val seen = mutableListOf<Pair<Long, Boolean>>()
        val job = launch { NavBarWatch.follow(periodMs) { true }.collect { seen += currentTime to it } }
        runCurrent()
        assertEquals(listOf(0L to true), seen)
        job.cancel()
    }

    @Test
    fun `a change is seen within one period`() = runTest {
        var shown = true
        val seen = mutableListOf<Pair<Long, Boolean>>()
        val job = launch { NavBarWatch.follow(periodMs) { shown }.collect { seen += currentTime to it } }
        runCurrent()
        advanceTimeBy(100)
        shown = false
        advanceTimeBy(periodMs)
        runCurrent()
        assertEquals(listOf(0L to true, periodMs to false), seen)
        job.cancel()
    }

    @Test
    fun `an unchanged value is not emitted again`() = runTest {
        val seen = mutableListOf<Boolean>()
        val job = launch { NavBarWatch.follow(periodMs) { true }.collect { seen += it } }
        advanceTimeBy(periodMs * 20)
        runCurrent()
        assertEquals(listOf(true), seen)
        job.cancel()
    }

    @Test
    fun `hidden then shown again comes back`() = runTest {
        val script = ArrayDeque(listOf(true, false, false, true))
        val seen = mutableListOf<Boolean>()
        val job = launch {
            NavBarWatch.follow(periodMs) { script.removeFirstOrNull() ?: true }.collect { seen += it }
        }
        advanceTimeBy(periodMs * 4)
        runCurrent()
        assertEquals(listOf(true, false, true), seen)
        job.cancel()
    }
}
