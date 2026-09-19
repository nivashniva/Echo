package com.nivukx.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.TransferListener
import java.io.IOException

class ChunkingDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long,
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var bytesToRead: Long = C.LENGTH_UNSET.toLong()
    private var bytesReadTotal: Long = 0
    private var isOpened = false
    private var chunkOpened = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.bytesReadTotal = 0
        this.bytesToRead = dataSpec.length
        this.isOpened = true
        this.chunkOpened = openNextChunk()
        return bytesToRead
    }

    private fun openNextChunk(): Boolean {
        val currentDataSpec = dataSpec ?: throw IOException("DataSpec is null")
        if (bytesToRead != C.LENGTH_UNSET.toLong() && bytesReadTotal >= bytesToRead) {
            return false
        }

        val position = currentDataSpec.position + bytesReadTotal
        val length = if (bytesToRead == C.LENGTH_UNSET.toLong()) {
            chunkSize
        } else {
            minOf(chunkSize, bytesToRead - bytesReadTotal)
        }

        if (length <= 0L) return false

        val chunkDataSpec = currentDataSpec.buildUpon()
            .setPosition(position)
            .setLength(length)
            .build()

        upstream.open(chunkDataSpec)
        return true
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (!isOpened || !chunkOpened) return C.RESULT_END_OF_INPUT

        val bytes = try {
            upstream.read(buffer, offset, readLength)
        } catch (e: InvalidResponseCodeException) {
            if (e.responseCode == 416) {
                upstream.close()
                chunkOpened = false
                return C.RESULT_END_OF_INPUT
            }
            throw e
        }

        if (bytes == C.RESULT_END_OF_INPUT) {
            upstream.close()
            chunkOpened = false
            chunkOpened = openNextChunk()
            return if (chunkOpened) {
                read(buffer, offset, readLength)
            } else {
                C.RESULT_END_OF_INPUT
            }
        }

        if (bytes > 0) {
            bytesReadTotal += bytes
        }
        return bytes
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        isOpened = false
        if (chunkOpened) {
            upstream.close()
            chunkOpened = false
        }
    }
}

class ChunkingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSize: Long = 5L * 1024 * 1024,
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        ChunkingDataSource(upstreamFactory.createDataSource(), chunkSize)
}
