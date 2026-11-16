/*
 * Copyright (c) 2025 Vitor Pamplona
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the
 * Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package com.vitorpamplona.amethyst.service.logging

import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Printer
import com.vitorpamplona.quartz.utils.Log
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicBoolean

class LogMonitor : Printer {
    private val mStackSampler: StackSampler
    private var mPrintingStarted = false
    private var mStartTimestamp: Long = 0

    //   threshold
    private val mBlockThresholdMillis: Long = 16

    // Sampling frequency
    private val mSampleInterval: Long = 1000

    private val mLogHandler: Handler

    init {
        mStackSampler = StackSampler(mSampleInterval)
        val handlerThread = HandlerThread("block-canary-io")
        handlerThread.start()
        mLogHandler = Handler(handlerThread.getLooper())
    }

    override fun println(x: String) {
        // From if to Else, execute DispatchMessage. If the execution takes more than the threshold, the output stuck information
        if (!mPrintingStarted) {
            // Record start time
            mStartTimestamp = System.currentTimeMillis()
            mPrintingStarted = true
            mStackSampler.startDump()
        } else {
            val endTime = System.currentTimeMillis()

            if (x.indexOf("com.vitorpamplona.amethyst") > 0) {
                Log.d("block-canary") { "Looper ${endTime - mStartTimestamp}ms for $x" }
            }

            mPrintingStarted = false
            //
            if (isBlock(endTime)) {
                notifyBlockEvent(endTime)
            }
            mStackSampler.stopDump()
        }
    }

    private fun notifyBlockEvent(endTime: Long) {
        mLogHandler.post {
            // Obtain the stack of the main thread stack
            val stacks: List<String> = mStackSampler.getStacks(mStartTimestamp, endTime)
            for (stack in stacks) {
                Log.e("block-canary", stack)
            }
        }
    }

    private fun isBlock(endTime: Long): Boolean = endTime - mStartTimestamp > mBlockThresholdMillis
}

class StackSampler(
    private val mSampleInterval: Long,
) {
    private val mHandler: Handler
    private val mStackMap: MutableMap<Long, String> = LinkedHashMap()
    private val mMaxCount = 100

    // Whether to sample
    var mShouldSample: AtomicBoolean = AtomicBoolean(false)

    /**
     * Start sampling and execute stack
     */
    fun startDump() {
        // Avoid repeating start
        if (mShouldSample.get()) {
            return
        }
        mShouldSample.set(true)
        mHandler.removeCallbacks(mRunnable)
        mHandler.postDelayed(mRunnable, mSampleInterval)
    }

    fun stopDump() {
        if (!mShouldSample.get()) {
            return
        }
        mShouldSample.set(false)
        mHandler.removeCallbacks(mRunnable)
    }

    fun getStacks(
        startTime: Long,
        endTime: Long,
    ): List<String> {
        val result = mutableListOf<String>()
        synchronized(mStackMap) {
            for (entryTime in mStackMap.keys) {
                if (startTime < entryTime && entryTime < endTime) {
                    result.add(
                        TIME_FORMATTER.format(entryTime) +
                            SEPARATOR +
                            SEPARATOR +
                            mStackMap[entryTime],
                    )
                }
            }
        }
        return result
    }

    init {
        val handlerThread = HandlerThread("block-canary-sampler")
        handlerThread.start()
        mHandler = Handler(handlerThread.looper)
    }

    private val mRunnable: Runnable =
        object : Runnable {
            override fun run() {
                val sb = StringBuilder()
                val stackTrace = Looper.getMainLooper().thread.stackTrace
                for (s in stackTrace) {
                    sb.append(s.toString()).append("\n")
                }
                synchronized(mStackMap) {
                    // Save up to 100 stack information
                    if (mStackMap.size == mMaxCount) {
                        mStackMap.remove(mStackMap.keys.iterator().next())
                    }
                    mStackMap.put(System.currentTimeMillis(), sb.toString())
                }
                if (mShouldSample.get()) {
                    mHandler.postDelayed(this, mSampleInterval)
                }
            }
        }

    companion object {
        const val SEPARATOR: String = "\r\n"

        @Suppress("SimpleDateFormat")
        val TIME_FORMATTER: SimpleDateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS")
    }
}
