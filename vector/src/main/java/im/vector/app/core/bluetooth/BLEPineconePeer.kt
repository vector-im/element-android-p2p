/*
 * Copyright (c) 2023 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package im.vector.app.core.bluetooth

import android.bluetooth.BluetoothSocket
import gobind.Conduit
import timber.log.Timber
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

class BLEPineconePeer(
        private val deviceAddress: DeviceAddress,
        private val conduit: Conduit,
        private val socket: BluetoothSocket,
        private val pineconeDisconenct: (Conduit) -> Unit,
        private val stopCallback: () -> Unit,
) {
    private val isConnected: Boolean
        get() = socket.isConnected
    private var stopped = AtomicBoolean(false)

    private val bleInput: InputStream = socket.inputStream
    private val bleOutput: OutputStream = socket.outputStream
    private val readerThread = thread {
        reader()
    }
    private val writerThread = thread {
        writer()
    }

    private val TAG = "BLEPineconePeer: $deviceAddress"

    fun close() {
        if (stopped.getAndSet(true)) {
            return
        }

        Timber.i("$TAG: Closing")
        try {
            conduit.close()
        } catch (_: Exception) {
        }
        pineconeDisconenct(conduit)

        try {
            bleInput.close()
        } catch (_: Exception) {
        }
        try {
            bleOutput.close()
        } catch (_: Exception) {
        }
        try {
            socket.close()
        } catch (_: Exception) {
        }

        readerThread.interrupt()
        writerThread.interrupt()
        Timber.i("$TAG: Closed")
    }

    private fun reader() {
        val b = ByteArray(socket.maxReceivePacketSize)
        while (isConnected) {
            try {
                val rn = bleInput.read(b)
                if (rn < 0) {
                    continue
                }
                val r = b.sliceArray(0 until rn).clone()
                conduit.write(r)
            } catch (e: Exception) {
                Timber.e("$TAG: reader exception: $e")
                try {
                    stopCallback()
                } catch (_: Exception) {}
                break
            }
        }
    }

    private fun writer() {
        val b = ByteArray(socket.maxTransmitPacketSize)
        while (isConnected) {
            try {
                val rn = conduit.read(b).toInt()
                if (rn < 0) {
                    continue
                }
                val w = b.sliceArray(0 until rn).clone()
                bleOutput.write(w)
            } catch (e: Exception) {
                Timber.e("$TAG: writer exception: $e")
                try {
                    stopCallback()
                } catch (_: Exception) {}
                break
            }
        }
    }
}
