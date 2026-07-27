// mp3encoder.js – Convert WAV Blob to MP3 Blob using lamejs
// Exposes global function wavFileToMp3Blob(file): Promise<Blob>

async function wavFileToMp3Blob(wavFile) {
  // Read file as ArrayBuffer
  const arrayBuffer = await wavFile.arrayBuffer();
  // Decode audio data
  const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);

  const sampleRate = audioBuffer.sampleRate;
  const channelData = [];
  for (let i = 0; i < audioBuffer.numberOfChannels; i++) {
    channelData.push(audioBuffer.getChannelData(i));
  }
  // Interleave channels (assuming mono or stereo)
  const interleaved = interleave(channelData);

  const mp3Encoder = new lamejs.Mp3Encoder(audioBuffer.numberOfChannels, sampleRate, 256);
  const sampleBlockSize = 1152;
  const mp3Data = [];
  for (let i = 0; i < interleaved.length; i += sampleBlockSize) {
    const sampleChunk = interleaved.subarray(i, i + sampleBlockSize);
    const mp3buf = mp3Encoder.encodeBuffer(sampleChunk);
    if (mp3buf.length > 0) {
      mp3Data.push(new Uint8Array(mp3buf));
    }
  }
  const endBuf = mp3Encoder.flush();
  if (endBuf.length > 0) {
    mp3Data.push(new Uint8Array(endBuf));
  }
  return new Blob(mp3Data, { type: 'audio/mpeg' });
}

function interleave(channels) {
  if (channels.length === 1) return channels[0];
  const length = channels[0].length;
  const result = new Float32Array(length * channels.length);
  let offset = 0;
  for (let i = 0; i < length; i++) {
    for (let ch = 0; ch < channels.length; ch++) {
      result[offset++] = channels[ch][i];
    }
  }
  return result;
}
