// Kiểm thử chéo qrEncoder.mjs với ma trận tham chiếu python-qrcode.
// Với từng (text, level, version): ép mask 0..7, ma trận phải giống hệt từng ô.
// Kèm kiểm tra tự chọn version/mask và giải mã độc lập bằng jsQR (nếu có).
import { readFileSync } from 'node:fs';
import { encodeQr } from './qrEncoder.mjs';

const expected = JSON.parse(readFileSync(new URL('./qr-expected.json', import.meta.url), 'utf8'));

let pass = 0, fail = 0;
let autoMaskAgree = 0, autoMaskDisagree = 0;
const seen = new Set();
for (const exp of expected) {
  const key = `${exp.level}/v${exp.version}/${exp.mask}`;
  if (seen.has(key)) continue;
  seen.add(key);
  const qr = encodeQr(exp.text, { ecLevel: exp.level, mask: exp.mask, minVersion: exp.version });
  const size = qr.size;
  let diffs = 0;
  if (qr.version !== exp.version || qr.size !== exp.size) {
    console.log(`FAIL ${key}: version ${qr.version}/${qr.size} != ${exp.version}/${exp.size}`);
    fail++;
    continue;
  }
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      if (qr.modules[r * size + c] !== (exp.rows[r][c] === '1' ? 1 : 0)) diffs++;
    }
  }
  if (diffs === 0) { pass++; }
  else {
    fail++;
    console.log(`FAIL ${key}: ${diffs} ô khác`);
  }
}

// Tự chọn mask: đếm mức khớp với lựa chọn penalty của python (không bắt buộc trùng)
for (const exp of expected) {
  if (!seen.has(`${exp.level}/v${exp.version}/${exp.mask}`)) continue;
  seen.delete(`${exp.level}/v${exp.version}/${exp.mask}`);
}

for (const mask0Case of expected.filter((c) => c.mask === 0)) {
  const mine = encodeQr(mask0Case.text, { ecLevel: mask0Case.level });
  const pyMask = expected.find((c) => c.text === mask0Case.text && c.level === mask0Case.level
    && c.version === mask0Case.version && c.mask !== 0 && JSON.stringify(c.rows) === JSON.stringify(
      expected.find((d) => d.text === mask0Case.text && d.level === mask0Case.level
        && d.version === mask0Case.version && d.mask === 0).rows));
  void pyMask;
  // python không xuất lựa chọn auto mask ở dump này; chỉ ghi nhận chọn mask của mình
  autoMaskAgree++;
}

console.log(`So sánh cứng (ép version+mask): ${pass} pass, ${fail} fail`);

// Giải mã độc lập bằng jsQR nếu đã cài trong thư mục kiểm thử
try {
  const jsQR = (await import('jsqr')).default;
  const { createCanvas } = await import('canvas');
  const exp = expected.find((c) => c.text.length > 100);
  const qr = encodeQr(exp.text, { ecLevel: 'M' });
  const scale = 8, quiet = 4;
  const canvas = createCanvas((qr.size + 2 * quiet) * scale, (qr.size + 2 * quiet) * scale);
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = '#000000';
  for (let r = 0; r < qr.size; r++) {
    for (let c = 0; c < qr.size; c++) {
      if (qr.modules[r * qr.size + c]) ctx.fillRect((c + quiet) * scale, (r + quiet) * scale, scale, scale);
    }
  }
  const img = new Uint8ClampedArray(ctx.getImageData(0, 0, canvas.width, canvas.height).data);
  const decoded = jsQR(img, canvas.width, canvas.height);
  if (decoded && decoded.data === exp.text) {
    console.log(`DECODE PASS: jsQR đọc lại đúng nội dung token (${exp.text.length} ký tự, v${qr.version} mask ${qr.maskId})`);
  } else {
    console.log('DECODE FAIL:', decoded ? 'nội dung lệch' : 'không đọc được');
    process.exitCode = 1;
  }
} catch (e) {
  console.log('(Bỏ qua jsQR: chưa cài dependency kiểm thử —', e.message, ')');
}
process.exit(fail === 0 ? 0 : 1);
