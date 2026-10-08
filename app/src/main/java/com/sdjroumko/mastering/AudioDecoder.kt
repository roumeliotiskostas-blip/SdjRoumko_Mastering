package com.sdjroumko.mastering

import android.content.ContentResolver
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioDecoder {

    fun decodeMp3(
        resolver: ContentResolver,
        uri: Uri
    ): WavData {

        val extractor = MediaExtractor()

        try {
            extractor.setDataSource(
                resolver.openAssetFileDescriptor(uri, "r")!!
            )

            var trackIndex = -1

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""

                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }

            require(trackIndex >= 0) {
                "Δεν βρέθηκε audio track στο MP3."
            }

            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalArgumentException("Άγνωστο audio format.")

            val codec = MediaCodec.createDecoderByType(mime)

            codec.configure(inputFormat, null, null, 0)
            codec.start()

            val output = ArrayList<Float>()
            var sampleRate = inputFormat.getInteger(
                MediaFormat.KEY_SAMPLE_RATE
            )
            var channels = inputFormat.getInteger(
                MediaFormat.KEY_CHANNEL_COUNT
            )

            val bufferInfo = MediaCodec.BufferInfo()

            var inputFinished = false
            var outputFinished = false

            while (!outputFinished) {

                if (!inputFinished) {
                    val inputIndex =
                        codec.dequeueInputBuffer(10_000)

                    if (inputIndex >= 0) {
                        val inputBuffer =
                            codec.getInputBuffer(inputIndex)
                                ?: continue

                        inputBuffer.clear()

                        val sampleSize =
                            extractor.readSampleData(inputBuffer, 0)

                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputFinished = true
                        } else {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime,
                                0
                            )
                            extractor.advance()
                        }
                    }
                }

                val outputIndex =
                    codec.dequeueOutputBuffer(
                        bufferInfo,
                        10_000
                    )

                when {
                    outputIndex >= 0 -> {

                        val buffer =
                            codec.getOutputBuffer(outputIndex)

                        if (buffer != null &&
                            bufferInfo.size > 0
                        ) {

                            buffer.position(bufferInfo.offset)
                            buffer.limit(
                                bufferInfo.offset +
                                    bufferInfo.size
                            )

                            val bytes = ByteArray(
                                buffer.remaining()
                            )

                            buffer.get(bytes)

                            val shortBuffer =
                                ByteBuffer
                                    .wrap(bytes)
                                    .order(ByteOrder.LITTLE_ENDIAN)
                                    .asShortBuffer()

                            while (shortBuffer.hasRemaining()) {
                                val value =
                                    shortBuffer.get() / 32768f

                                output.add(
                                    value.coerceIn(-1f, 1f)
                                )
                            }
                        }

                        codec.releaseOutputBuffer(
                            outputIndex,
                            false
                        )

                        if (
                            bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        ) {
                            outputFinished = true
                        }
                    }

                    outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val format =
                            codec.outputFormat

                        if (format.containsKey(
                                MediaFormat.KEY_SAMPLE_RATE
                            )
                        ) {
                            sampleRate =
                                format.getInteger(
                                    MediaFormat.KEY_SAMPLE_RATE
                                )
                        }

                        if (format.containsKey(
                                MediaFormat.KEY_CHANNEL_COUNT
                            )
                        ) {
                            channels =
                                format.getInteger(
                                    MediaFormat.KEY_CHANNEL_COUNT
                                )
                        }
                    }
                }
            }

            codec.stop()
            codec.release()

            require(output.isNotEmpty()) {
                "Το MP3 δεν περιέχει αποκωδικοποιήσιμο audio."
            }

            return WavData(
                sampleRate = sampleRate,
                channels = channels,
                samples = output.toFloatArray()
            )

        } finally {
            extractor.release()
        }
    }
}
