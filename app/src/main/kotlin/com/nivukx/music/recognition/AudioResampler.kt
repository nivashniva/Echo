package com.nivukx.music.recognition

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.yield
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder


data class DecodedAudio(
    val data: ByteArray,
    val channelCount: Int,
    val sampleRate: Int,
    val pcmEncoding: Int,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DecodedAudio
        return data.contentEquals(other.data) &&
                channelCount == other.channelCount &&
                sampleRate == other.sampleRate &&
                pcmEncoding == other.pcmEncoding
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + channelCount
        result = 31 * result + sampleRate
        result = 31 * result + pcmEncoding
        return result
    }
}


@OptIn(UnstableApi::class)
object AudioResampler {

    suspend fun resample(
        decodedAudio: DecodedAudio,
        outputSampleRate: Int
    ): Result<DecodedAudio> = withContext(Dispatchers.Default) {
        if (decodedAudio.sampleRate == outputSampleRate) {
            return@withContext Result.success(decodedAudio)
        }
        
        var sonicRef: AudioProcessor? = null
        try {
            val sonic: AudioProcessor = SonicAudioProcessor().apply {
                setOutputSampleRateHz(outputSampleRate)
            }
            sonicRef = sonic
            
            val inputFormat = AudioProcessor.AudioFormat(
                decodedAudio.sampleRate,
                decodedAudio.channelCount,
                decodedAudio.pcmEncoding
            )
            val outputFormat = sonic.configure(inputFormat)
            sonic.flush()

            val inputBuf = ByteBuffer.wrap(decodedAudio.data).order(ByteOrder.nativeOrder())
            sonic.queueInput(inputBuf)
            sonic.queueEndOfStream()

            val outputStream = java.io.ByteArrayOutputStream(decodedAudio.data.size)
            var outputChunkCount = 0

            while (!sonic.isEnded) {
                ensureActive()
                val outputBuffer = sonic.output
                if (!outputBuffer.hasRemaining()) {
                    yield()
                    continue
                }
                val chunk = ByteArray(outputBuffer.remaining())
                outputBuffer.get(chunk)
                outputStream.write(chunk)
                outputChunkCount++
            }
            sonic.reset()

            val resampledData = outputStream.toByteArray()
            
            Result.success(DecodedAudio(
                data = resampledData,
                channelCount = outputFormat.channelCount,
                sampleRate = outputFormat.sampleRate,
                pcmEncoding = outputFormat.encoding,
            ))
        } catch (e: Exception) {
            ensureActive()
            Result.failure(e)
        } finally {
            sonicRef?.reset()
        }
    }
}
