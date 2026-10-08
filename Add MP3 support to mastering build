name: Build APK

on:
  workflow_dispatch:
  push:
    branches:
      - main

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up Java 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle 9.1.0
        uses: gradle/actions/setup-gradle@v4
        with:
          gradle-version: '9.1.0'

      - name: Extract project
        run: |
          rm -rf project
          mkdir project
          unzip -q SdjRoumko_Mastering_v0.3.0.zip -d project

      - name: Add MP3 decoder
        run: |
          mkdir -p project/app/src/main/java/com/sdjroumko/mastering
          cat > project/app/src/main/java/com/sdjroumko/mastering/AudioDecoder.kt <<'KOTLIN'
          package com.sdjroumko.mastering

          import android.content.ContentResolver
          import android.media.MediaCodec
          import android.media.MediaExtractor
          import android.media.MediaFormat
          import android.net.Uri
          import java.nio.ByteBuffer
          import java.nio.ByteOrder

          object AudioDecoder {
              fun decodeMp3(resolver: ContentResolver, uri: Uri): WavData {
                  val extractor = MediaExtractor()
                  try {
                      extractor.setDataSource(
                          resolver.openAssetFileDescriptor(uri, "r")!!
                      )

                      var trackIndex = -1

                      for (i in 0 until extractor.trackCount) {
                          val format = extractor.getTrackFormat(i)
                          val mime =
                              format.getString(MediaFormat.KEY_MIME) ?: ""

                          if (mime.startsWith("audio/")) {
                              trackIndex = i
                              break
                          }
                      }

                      require(trackIndex >= 0) {
                          "Δεν βρέθηκε audio track στο MP3."
                      }

                      extractor.selectTrack(trackIndex)

                      val inputFormat =
                          extractor.getTrackFormat(trackIndex)

                      val mime =
                          inputFormat.getString(MediaFormat.KEY_MIME)
                              ?: throw IllegalArgumentException(
                                  "Άγνωστο audio format."
                              )

                      val codec =
                          MediaCodec.createDecoderByType(mime)

                      codec.configure(
                          inputFormat,
                          null,
                          null,
                          0
                      )

                      codec.start()

                      val output = ArrayList<Float>()

                      var sampleRate =
                          inputFormat.getInteger(
                              MediaFormat.KEY_SAMPLE_RATE
                          )

                      var channels =
                          inputFormat.getInteger(
                              MediaFormat.KEY_CHANNEL_COUNT
                          )

                      val bufferInfo =
                          MediaCodec.BufferInfo()

                      var inputFinished = false
                      var outputFinished = false

                      while (!outputFinished) {

                          if (!inputFinished) {
                              val inputIndex =
                                  codec.dequeueInputBuffer(10000)

                              if (inputIndex >= 0) {
                                  val inputBuffer =
                                      codec.getInputBuffer(inputIndex)
                                          ?: continue

                                  inputBuffer.clear()

                                  val sampleSize =
                                      extractor.readSampleData(
                                          inputBuffer,
                                          0
                                      )

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
                                  10000
                              )

                          when {

                              outputIndex >= 0 -> {

                                  val buffer =
                                      codec.getOutputBuffer(
                                          outputIndex
                                      )

                                  if (
                                      buffer != null &&
                                      bufferInfo.size > 0
                                  ) {

                                      buffer.position(
                                          bufferInfo.offset
                                      )

                                      buffer.limit(
                                          bufferInfo.offset +
                                              bufferInfo.size
                                      )

                                      val bytes =
                                          ByteArray(
                                              buffer.remaining()
                                          )

                                      buffer.get(bytes)

                                      val shortBuffer =
                                          ByteBuffer
                                              .wrap(bytes)
                                              .order(
                                                  ByteOrder.LITTLE_ENDIAN
                                              )
                                              .asShortBuffer()

                                      while (
                                          shortBuffer.hasRemaining()
                                      ) {
                                          output.add(
                                              (
                                                  shortBuffer.get()
                                                      .toFloat() /
                                                      32768f
                                                  ).coerceIn(
                                                      -1f,
                                                      1f
                                                  )
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

                              outputIndex ==
                                  MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {

                                  val format =
                                      codec.outputFormat

                                  if (
                                      format.containsKey(
                                          MediaFormat.KEY_SAMPLE_RATE
                                      )
                                  ) {
                                      sampleRate =
                                          format.getInteger(
                                              MediaFormat.KEY_SAMPLE_RATE
                                          )
                                  }

                                  if (
                                      format.containsKey(
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
                          sampleRate,
                          channels,
                          output.toFloatArray()
                      )

                  } finally {
                      extractor.release()
                  }
              }
          }
          KOTLIN

      - name: Fix startup and connect MP3
        working-directory: project
        run: |
          python3 - <<'PY'
          from pathlib import Path

          f = Path(
              "app/src/main/java/com/sdjroumko/mastering/MainActivity.kt"
          )

          s = f.read_text()

          s = s.replace(
              "private val eqBands = MasterEngine.defaultBands().toMutableList()",
              "private val eqBands by lazy { MasterEngine.defaultBands().toMutableList() }"
          )

          s = s.replace(
              'val data = WavCodec.read(contentResolver, uri)',
              '''val mimeType = contentResolver.getType(uri) ?: ""
                      val fileName = uri.toString().lowercase()

                      val data = if (
                          mimeType.contains("mpeg") ||
                          mimeType.contains("mp3") ||
                          fileName.endsWith(".mp3")
                      ) {
                          AudioDecoder.decodeMp3(
                              contentResolver,
                              uri
                          )
                      } else {
                          WavCodec.read(
                              contentResolver,
                              uri
                          )
                      }'''
          )

          s = s.replace(
              'picker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/*"))',
              'picker.launch(arrayOf("audio/wav", "audio/x-wav", "audio/mpeg", "audio/mp3", "audio/*"))'
          )

          f.write_text(s)
          PY

      - name: Build Debug APK
        working-directory: project
        run: gradle --no-daemon --stacktrace assembleDebug

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: SdjRoumko-Mastering-MP3
          path: project/app/build/outputs/apk/debug/app-debug.apk
