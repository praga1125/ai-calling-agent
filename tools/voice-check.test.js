/*
 * Runs the page's own voice detection over real WAV files, so a change to the thresholds can be
 * checked without a microphone. Extracts the functions straight out of index.html: a copy here
 * would drift from what the browser runs, which is exactly the bug this is meant to catch.
 *
 *   node tools/voice-check.test.js
 */
const fs = require('fs');
const path = require('path');

const page = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'index.html'), 'utf8');

function lift(name, kind) {
  const start = page.indexOf(`${kind} ${name}`);
  if (start < 0) throw new Error(`${name} not found in index.html`);
  if (kind === 'const') {
    return page.slice(start, page.indexOf(';', start) + 1);
  }
  let depth = 0;
  for (let i = page.indexOf('{', start); i < page.length; i++) {
    if (page[i] === '{') depth++;
    if (page[i] === '}' && --depth === 0) return page.slice(start, i + 1);
  }
  throw new Error(`${name} is unbalanced`);
}

const source = [
  'FRAME_MS', 'TARGET_RMS', 'VOICE_RATIO', 'MIN_VOICE_RMS', 'MIN_SPEECH_MS', 'LOUD_ROOM_RMS', 'QUIET_RMS',
  'TRIM_PAD_MS'
].map(name => lift(name, 'const')).concat(
  ['frameLevels', 'percentile', 'normalizeGain', 'downsample', 'measureSpeech', 'trimSilence', 'analyseTake']
    .map(name => lift(name, 'function'))
).join('\n');

const analyse = new Function(`${source}
  return (samples, rate) => {
    const take = analyseTake(samples, rate);
    return {
      speech: take.speech,
      rawMedian: take.rawMedian,
      accepted: take.hasVoice,
      trimmedSeconds: take.pcm.length / take.targetRate
    };
  };`)();

function readWav(file) {
  const buffer = fs.readFileSync(file);
  const rate = buffer.readUInt32LE(24);
  let offset = 12;
  while (offset < buffer.length - 8) {
    const id = buffer.toString('ascii', offset, offset + 4);
    const size = buffer.readUInt32LE(offset + 4);
    if (id === 'data') {
      const end = Math.min(buffer.length, offset + 8 + (size === 0xffffffff ? buffer.length : size));
      const samples = new Float32Array((end - offset - 8) / 2);
      for (let i = 0; i < samples.length; i++) {
        samples[i] = buffer.readInt16LE(offset + 8 + i * 2) / 32768;
      }
      return { samples, rate };
    }
    offset += 8 + size;
  }
  throw new Error('no data chunk in ' + file);
}

/** Amplitude-modulated tone at speech-like rates: rises above the floor and stays there. */
function syntheticSpeech(seconds, rate, amplitude) {
  const samples = new Float32Array(Math.round(seconds * rate));
  for (let i = 0; i < samples.length; i++) {
    const t = i / rate;
    const syllable = Math.max(0, Math.sin(2 * Math.PI * 4 * t));
    samples[i] = amplitude * syllable * Math.sin(2 * Math.PI * 180 * t) + (Math.random() - 0.5) * 0.0005;
  }
  return samples;
}

function silenceWithClick(seconds, rate) {
  const samples = new Float32Array(Math.round(seconds * rate));
  for (let i = 0; i < samples.length; i++) samples[i] = (Math.random() - 0.5) * 0.004;
  const at = Math.round(rate * 1.5);
  for (let i = at; i < at + Math.round(rate * 0.03); i++) samples[i] = (Math.random() - 0.5) * 1.6;
  return samples;
}

/** A one-word answer late in a long take: the case that must never be turned away. */
function shortAnswerInSilence(rate) {
  const samples = new Float32Array(rate * 5);
  for (let i = 0; i < samples.length; i++) samples[i] = (Math.random() - 0.5) * 0.004;
  const word = syntheticSpeech(0.4, rate, 0.08);
  samples.set(word, rate * 3);
  return samples;
}

/** Someone who talks without pausing gives the floor no silence to sit in. */
function continuousSpeech(rate) {
  const samples = new Float32Array(rate * 4);
  for (let i = 0; i < samples.length; i++) {
    const t = i / rate;
    samples[i] = 0.2 * Math.sin(2 * Math.PI * 200 * t) * (0.7 + 0.3 * Math.sin(2 * Math.PI * 7 * t));
  }
  return samples;
}

const cases = [
  ['loud speech', true, syntheticSpeech(3, 48000, 0.3), 48000],
  ['very quiet speech (weak mic)', true, syntheticSpeech(3, 48000, 0.004), 48000],
  ['short "yes" in five seconds of silence', true, shortAnswerInSilence(48000), 48000],
  ['unbroken speech, no pauses', true, continuousSpeech(48000), 48000],
  ['silence with one click', false, silenceWithClick(5, 48000), 48000],
  ['pure room noise', false, Float32Array.from({ length: 48000 * 5 },
    () => (Math.random() - 0.5) * 0.006), 48000],
  ['digital silence', false, new Float32Array(48000 * 3), 48000]
];

const audioDir = path.join(__dirname, '..', 'storage', 'audio');
for (const file of fs.readdirSync(audioDir).filter(f => f.startsWith('speech-test')).slice(0, 1)) {
  const wav = readWav(path.join(audioDir, file));
  cases.push([`app generated speech (${file.slice(0, 16)})`, true, wav.samples, wav.rate]);
}

let failures = 0;
for (const [name, expected, samples, rate] of cases) {
  const { speech, accepted, trimmedSeconds } = analyse(samples, rate);
  const ok = accepted === expected;
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name.padEnd(40)} `
    + `voiced ${String(speech.speechMs).padStart(5)}ms  level ${speech.level.toFixed(4)}  `
    + `sent ${trimmedSeconds.toFixed(1)}s  -> ${accepted ? 'accepted' : 'rejected'}`);
}

// Real uploads are reported, not asserted: nobody here knows what was said into that microphone.
console.log('\nrecordings this browser has already sent:');
const uploads = fs.readdirSync(audioDir).filter(f => f.includes('customer')).slice(0, 6);
for (const file of uploads) {
  const wav = readWav(path.join(audioDir, file));
  const { speech, accepted, trimmedSeconds } = analyse(wav.samples, wav.rate);
  console.log(`  ${file.slice(0, 22).padEnd(24)} ${speech.seconds.toFixed(1)}s recorded, `
    + `${trimmedSeconds.toFixed(1)}s would be sent  voiced ${String(speech.speechMs).padStart(5)}ms  `
    + `-> ${accepted ? 'accepted' : 'rejected'}`);
}
if (!uploads.length) console.log('  (none yet)');

console.log(failures ? `\n${failures} case(s) failed` : '\nall cases passed');
process.exit(failures ? 1 : 0);
