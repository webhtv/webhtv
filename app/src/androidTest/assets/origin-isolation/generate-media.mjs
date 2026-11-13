import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const source = process.argv[2] || 'app/src/debug/assets/exo-ass/sample-embedded.mkv';
const output = path.resolve(process.argv[3] || 'app/build/origin-isolation/media-fixture');
const allowed = path.resolve('app/build/origin-isolation') + path.sep;
if (!output.startsWith(allowed)) throw new Error('output must be inside ignored task build directory');
const bytes = fs.readFileSync(source);
function vint(p, id = false) {
    let n = 1, bit = 128;
    while (!(bytes[p] & bit)) { n++; bit >>= 1; }
    let value = id ? bytes[p] : bytes[p] & (bit - 1);
    for (let i = 1; i < n; i++) value = value * 256 + bytes[p + i];
    return [value, n];
}
function elements(start, end) {
    const out = [];
    for (let p = start; p < end;) {
        const [id, n] = vint(p, true), [size, m] = vint(p + n);
        const q = p + n + m, stop = Math.min(end, q + size);
        out.push([id, q, stop]); p = stop;
    }
    return out;
}
const segment = elements(0, bytes.length).find(([id]) => id === 0x18538067);
const top = elements(segment[1], segment[2]);
const tracks = top.find(([id]) => id === 0x1654ae6b);
let avcc, width, height, number, defaultNs;
for (const [id, a, b] of elements(tracks[1], tracks[2])) {
    if (id !== 0xae) continue;
    const entry = elements(a, b), type = entry.find(([id]) => id === 0x83);
    if (!type || bytes[type[1]] !== 1) continue;
    const find = key => entry.find(([id]) => id === key);
    const cp = find(0x63a2), no = find(0xd7), du = find(0x23e383), video = find(0xe0);
    avcc = bytes.subarray(cp[1], cp[2]); number = bytes[no[1]];
    defaultNs = du ? bytes.readUIntBE(du[1], du[2] - du[1]) : 33333333;
    const values = new Map(elements(video[1], video[2]).filter(([id]) => [0xb0, 0xba].includes(id)).map(([id, p, e]) => [id, bytes.readUIntBE(p, e - p)]));
    width = values.get(0xb0); height = values.get(0xba);
}
if (!avcc || !width || !height) throw new Error('no AVC video track in existing MKV');
const samples = [];
for (const [id, a, b] of top) {
    if (id !== 0x1f43b675) continue;
    const items = elements(a, b), tc = items.find(([id]) => id === 0xe7);
    const time = tc ? bytes.readUIntBE(tc[1], tc[2] - tc[1]) : 0;
    for (const [id, p, e] of items) {
        if (id !== 0xa3) continue;
        const [track, n] = vint(p);
        if (track !== number) continue;
        const flags = bytes[p + n + 2];
        if (flags & 6) throw new Error('video lacing is unsupported by fixture converter');
        samples.push({time: time + bytes.readInt16BE(p + n), key: !!(flags & 128), data: bytes.subarray(p + n + 3, e)});
    }
}
const u32 = n => { const b = Buffer.alloc(4); b.writeUInt32BE(n >>> 0); return b; };
const i32 = n => { const b = Buffer.alloc(4); b.writeInt32BE(n); return b; };
const u64 = n => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; };
const u16 = n => { const b = Buffer.alloc(2); b.writeUInt16BE(n); return b; };
const zero = n => Buffer.alloc(n);
const box = (type, ...parts) => { const data = Buffer.concat(parts); return Buffer.concat([u32(data.length + 8), Buffer.from(type), data]); };
const full = (type, flags, ...parts) => box(type, u32(flags), ...parts);
const matrix = Buffer.concat([u32(0x10000), u32(0), u32(0), u32(0), u32(0x10000), u32(0), u32(0), u32(0), u32(0x40000000)]);
const avc1 = box('avc1', zero(6), u16(1), zero(16), u16(width), u16(height), u32(0x480000), u32(0x480000), u32(0), u16(1), zero(32), u16(24), u16(65535), box('avcC', avcc));
const stbl = box('stbl', full('stsd', 0, u32(1), avc1), full('stts', 0, u32(0)), full('stsc', 0, u32(0)), full('stsz', 0, u32(0), u32(0)), full('stco', 0, u32(0)));
const mdia = box('mdia', full('mdhd', 0, u32(0), u32(0), u32(90000), u32(0), u16(0x55c4), u16(0)), full('hdlr', 0, u32(0), Buffer.from('vide'), zero(12), Buffer.from('Origin AVC\0')), box('minf', full('vmhd', 1, zero(8)), box('dinf', full('dref', 0, u32(1), full('url ', 1))), stbl));
const trak = box('trak', full('tkhd', 7, u32(0), u32(0), u32(1), zero(8), zero(8), zero(8), matrix, u32(width * 65536), u32(height * 65536)), mdia);
const init = Buffer.concat([box('ftyp', Buffer.from('iso6'), u32(0), Buffer.from('iso6isomavc1mp41')), box('moov', full('mvhd', 0, u32(0), u32(0), u32(1000), u32(0), u32(65536), u16(256), zero(10), matrix, zero(24), u32(2)), trak, box('mvex', full('trex', 0, u32(1), u32(1), u32(3000), u32(0), u32(0))))]);
const step = Math.round(defaultNs * 90000 / 1e9), groups = [];
const originalSamples = samples.slice();
const clipMs = originalSamples.length * step / 90;
for (let cycle = 1; cycle < 4; cycle++) {
    for (const sample of originalSamples) samples.push({...sample, time: sample.time + cycle * clipMs});
}
let start = 0;
for (let i = 1; i < samples.length; i++) {
    if (samples[i].key && (i - start) * step >= 180000) { groups.push([start, i]); start = i; }
}
groups.push([start, samples.length]);
fs.mkdirSync(output, {recursive: true});
fs.writeFileSync(path.join(output, 'init.mp4'), init);
const key = Buffer.from(Array.from({length: 16}, (_, i) => i));
fs.writeFileSync(path.join(output, 'key.bin'), key);
let playlist = '#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-TARGETDURATION:10\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-MAP:URI="init.mp4"\n';
const clear = [init];
for (let index = 0; index < groups.length; index++) {
    const [from, to] = groups[index];
    const records = samples.slice(from, to).map((s, i) => Buffer.concat([u32(step), u32(s.data.length), u32(s.key ? 0x02000000 : 0x01010000), i32(Math.round(s.time * 90) - (from + i) * step)]));
    const traf = offset => box('traf', full('tfhd', 0x020000, u32(1)), full('tfdt', 0x01000000, u64(from * step)), full('trun', 0x01000f01, u32(to - from), i32(offset), ...records));
    let moof = box('moof', full('mfhd', 0, u32(index + 1)), traf(0));
    moof = box('moof', full('mfhd', 0, u32(index + 1)), traf(moof.length + 8));
    const fragment = Buffer.concat([moof, box('mdat', ...samples.slice(from, to).map(s => s.data))]);
    clear.push(fragment);
    const iv = zero(16); iv.writeUInt32BE(index + 1, 12);
    const aes = crypto.createCipheriv('aes-128-cbc', key, iv);
    fs.writeFileSync(path.join(output, `seg-${index}.m4s`), Buffer.concat([aes.update(fragment), aes.final()]));
    playlist += `#EXT-X-KEY:METHOD=AES-128,URI="key.bin",IV=0x${iv.toString('hex')}\n#EXTINF:${((to - from) * step / 90000).toFixed(3)},\nseg-${index}.m4s?sig=keep%2Bexact\n`;
}
playlist += '#EXT-X-ENDLIST\n';
fs.writeFileSync(path.join(output, 'media.m3u8'), playlist);
fs.writeFileSync(path.join(output, 'master.m3u8'), '#EXTM3U\n#EXT-X-VERSION:7\n#EXT-X-STREAM-INF:BANDWIDTH=600000,CODECS="avc1.64001f"\nmedia.m3u8\n');
fs.writeFileSync(path.join(output, 'preview.mp4'), Buffer.concat(clear));
console.log(JSON.stringify({source, width, height, sampleCount: samples.length, durationSeconds: samples.length * step / 90000, segments: groups.length, output}));
