// PHỤ TRÁCH: Task 6.
// qrEncoder.js — Bộ sinh mã QR (byte mode) thuần JS, KHÔNG thêm dependency npm.
// Lý do tồn tại: frontend/package.json do Task 8 quản lý nên Task 6 không thêm
// thư viện; QrPage.jsx cần vẽ QR động từ token JWS theo FR-UQ-03 và
// DECISIONS.md mục 5 (QR chỉ hiển thị trên app/web sau đăng nhập).
//
// Cách dùng:
//   import { encodeQr } from '../lib/qrEncoder.js';
//   const qr = encodeQr(token, { ecLevel: 'M' });
//   // qr.modules: Uint8Array length size*size; 1 = module tối, 0 = module sáng.
//
// Thuật toán theo ISO/IEC 18004: byte mode, tự chọn version 1–40, Reed–Solomon
// trên GF(256), 8 mask + 4 luật penalty, format/version info BCH. Bảng
// RS-block/EC/alignment sinh từ dữ liệu chuẩn (python-qrcode) và ma trận đầu ra
// đã được đối chiếu từng ô với bộ sinh tham chiếu cùng giải mã độc lập
// (scripts/test-qr-encoder.mjs giao kèm Task 6).
//
// Bảo mật: hàm chỉ biến chuỗi thành ma trận; không log/cache token. Caller
// (QrPage.jsx) chịu trách nhiệm không đưa token vào URL, log hay ảnh phân phát.

const RS_BLOCKS = {"L":{"1":[[1,19]],"2":[[1,34]],"3":[[1,55]],"4":[[1,80]],"5":[[1,108]],"6":[[2,68]],"7":[[2,78]],"8":[[2,97]],"9":[[2,116]],"10":[[2,68],[2,69]],"11":[[4,81]],"12":[[2,92],[2,93]],"13":[[4,107]],"14":[[3,115],[1,116]],"15":[[5,87],[1,88]],"16":[[5,98],[1,99]],"17":[[1,107],[5,108]],"18":[[5,120],[1,121]],"19":[[3,113],[4,114]],"20":[[3,107],[5,108]],"21":[[4,116],[4,117]],"22":[[2,111],[7,112]],"23":[[4,121],[5,122]],"24":[[6,117],[4,118]],"25":[[8,106],[4,107]],"26":[[10,114],[2,115]],"27":[[8,122],[4,123]],"28":[[3,117],[10,118]],"29":[[7,116],[7,117]],"30":[[5,115],[10,116]],"31":[[13,115],[3,116]],"32":[[17,115]],"33":[[17,115],[1,116]],"34":[[13,115],[6,116]],"35":[[12,121],[7,122]],"36":[[6,121],[14,122]],"37":[[17,122],[4,123]],"38":[[4,122],[18,123]],"39":[[20,117],[4,118]],"40":[[19,118],[6,119]]},"M":{"1":[[1,16]],"2":[[1,28]],"3":[[1,44]],"4":[[2,32]],"5":[[2,43]],"6":[[4,27]],"7":[[4,31]],"8":[[2,38],[2,39]],"9":[[3,36],[2,37]],"10":[[4,43],[1,44]],"11":[[1,50],[4,51]],"12":[[6,36],[2,37]],"13":[[8,37],[1,38]],"14":[[4,40],[5,41]],"15":[[5,41],[5,42]],"16":[[7,45],[3,46]],"17":[[10,46],[1,47]],"18":[[9,43],[4,44]],"19":[[3,44],[11,45]],"20":[[3,41],[13,42]],"21":[[17,42]],"22":[[17,46]],"23":[[4,47],[14,48]],"24":[[6,45],[14,46]],"25":[[8,47],[13,48]],"26":[[19,46],[4,47]],"27":[[22,45],[3,46]],"28":[[3,45],[23,46]],"29":[[21,45],[7,46]],"30":[[19,47],[10,48]],"31":[[2,46],[29,47]],"32":[[10,46],[23,47]],"33":[[14,46],[21,47]],"34":[[14,46],[23,47]],"35":[[12,47],[26,48]],"36":[[6,47],[34,48]],"37":[[29,46],[14,47]],"38":[[13,46],[32,47]],"39":[[40,47],[7,48]],"40":[[18,47],[31,48]]},"Q":{"1":[[1,13]],"2":[[1,22]],"3":[[2,17]],"4":[[2,24]],"5":[[2,15],[2,16]],"6":[[4,19]],"7":[[2,14],[4,15]],"8":[[4,18],[2,19]],"9":[[4,16],[4,17]],"10":[[6,19],[2,20]],"11":[[4,22],[4,23]],"12":[[4,20],[6,21]],"13":[[8,20],[4,21]],"14":[[11,16],[5,17]],"15":[[5,24],[7,25]],"16":[[15,19],[2,20]],"17":[[1,22],[15,23]],"18":[[17,22],[1,23]],"19":[[17,21],[4,22]],"20":[[15,24],[5,25]],"21":[[17,22],[6,23]],"22":[[7,24],[16,25]],"23":[[11,24],[14,25]],"24":[[11,24],[16,25]],"25":[[7,24],[22,25]],"26":[[28,22],[6,23]],"27":[[8,23],[26,24]],"28":[[4,24],[31,25]],"29":[[1,23],[37,24]],"30":[[15,24],[25,25]],"31":[[42,24],[1,25]],"32":[[10,24],[35,25]],"33":[[29,24],[19,25]],"34":[[44,24],[7,25]],"35":[[39,24],[14,25]],"36":[[46,24],[10,25]],"37":[[49,24],[10,25]],"38":[[48,24],[14,25]],"39":[[43,24],[22,25]],"40":[[34,24],[34,25]]},"H":{"1":[[1,9]],"2":[[1,16]],"3":[[2,13]],"4":[[4,9]],"5":[[2,11],[2,12]],"6":[[4,15]],"7":[[4,13],[1,14]],"8":[[4,14],[2,15]],"9":[[4,12],[4,13]],"10":[[6,15],[2,16]],"11":[[3,12],[8,13]],"12":[[7,14],[4,15]],"13":[[12,11],[4,12]],"14":[[11,12],[5,13]],"15":[[11,12],[7,13]],"16":[[3,15],[13,16]],"17":[[2,14],[17,15]],"18":[[2,14],[19,15]],"19":[[9,13],[16,14]],"20":[[15,15],[10,16]],"21":[[19,16],[6,17]],"22":[[34,13]],"23":[[16,15],[14,16]],"24":[[30,16],[2,17]],"25":[[22,15],[13,16]],"26":[[33,16],[4,17]],"27":[[12,15],[28,16]],"28":[[11,15],[31,16]],"29":[[19,15],[26,16]],"30":[[23,15],[25,16]],"31":[[23,15],[28,16]],"32":[[19,15],[35,16]],"33":[[11,15],[46,16]],"34":[[59,16],[1,17]],"35":[[22,15],[41,16]],"36":[[2,15],[64,16]],"37":[[24,15],[46,16]],"38":[[42,15],[32,16]],"39":[[10,15],[67,16]],"40":[[20,15],[61,16]]}};
const EC_PER_BLOCK = {"L":{"1":7,"2":10,"3":15,"4":20,"5":26,"6":18,"7":20,"8":24,"9":30,"10":18,"11":20,"12":24,"13":26,"14":30,"15":22,"16":24,"17":28,"18":30,"19":28,"20":28,"21":28,"22":28,"23":30,"24":30,"25":26,"26":28,"27":30,"28":30,"29":30,"30":30,"31":30,"32":30,"33":30,"34":30,"35":30,"36":30,"37":30,"38":30,"39":30,"40":30},"M":{"1":10,"2":16,"3":26,"4":18,"5":24,"6":16,"7":18,"8":22,"9":22,"10":26,"11":30,"12":22,"13":22,"14":24,"15":24,"16":28,"17":28,"18":26,"19":26,"20":26,"21":26,"22":28,"23":28,"24":28,"25":28,"26":28,"27":28,"28":28,"29":28,"30":28,"31":28,"32":28,"33":28,"34":28,"35":28,"36":28,"37":28,"38":28,"39":28,"40":28},"Q":{"1":13,"2":22,"3":18,"4":26,"5":18,"6":24,"7":18,"8":22,"9":20,"10":24,"11":28,"12":26,"13":24,"14":20,"15":30,"16":24,"17":28,"18":28,"19":26,"20":30,"21":28,"22":30,"23":30,"24":30,"25":30,"26":28,"27":30,"28":30,"29":30,"30":30,"31":30,"32":30,"33":30,"34":30,"35":30,"36":30,"37":30,"38":30,"39":30,"40":30},"H":{"1":17,"2":28,"3":22,"4":16,"5":22,"6":28,"7":26,"8":26,"9":24,"10":28,"11":24,"12":28,"13":22,"14":24,"15":24,"16":30,"17":28,"18":28,"19":26,"20":28,"21":30,"22":24,"23":30,"24":30,"25":30,"26":30,"27":30,"28":30,"29":30,"30":30,"31":30,"32":30,"33":30,"34":30,"35":30,"36":30,"37":30,"38":30,"39":30,"40":30}};
const DATA_CODEWORDS = {"L":{"1":19,"2":34,"3":55,"4":80,"5":108,"6":136,"7":156,"8":194,"9":232,"10":274,"11":324,"12":370,"13":428,"14":461,"15":523,"16":589,"17":647,"18":721,"19":795,"20":861,"21":932,"22":1006,"23":1094,"24":1174,"25":1276,"26":1370,"27":1468,"28":1531,"29":1631,"30":1735,"31":1843,"32":1955,"33":2071,"34":2191,"35":2306,"36":2434,"37":2566,"38":2702,"39":2812,"40":2956},"M":{"1":16,"2":28,"3":44,"4":64,"5":86,"6":108,"7":124,"8":154,"9":182,"10":216,"11":254,"12":290,"13":334,"14":365,"15":415,"16":453,"17":507,"18":563,"19":627,"20":669,"21":714,"22":782,"23":860,"24":914,"25":1000,"26":1062,"27":1128,"28":1193,"29":1267,"30":1373,"31":1455,"32":1541,"33":1631,"34":1725,"35":1812,"36":1914,"37":1992,"38":2102,"39":2216,"40":2334},"Q":{"1":13,"2":22,"3":34,"4":48,"5":62,"6":76,"7":88,"8":110,"9":132,"10":154,"11":180,"12":206,"13":244,"14":261,"15":295,"16":325,"17":367,"18":397,"19":445,"20":485,"21":512,"22":568,"23":614,"24":664,"25":718,"26":754,"27":808,"28":871,"29":911,"30":985,"31":1033,"32":1115,"33":1171,"34":1231,"35":1286,"36":1354,"37":1426,"38":1502,"39":1582,"40":1666},"H":{"1":9,"2":16,"3":26,"4":36,"5":46,"6":60,"7":66,"8":86,"9":100,"10":122,"11":140,"12":158,"13":180,"14":197,"15":223,"16":253,"17":283,"18":313,"19":341,"20":385,"21":406,"22":442,"23":464,"24":514,"25":538,"26":596,"27":628,"28":661,"29":701,"30":745,"31":793,"32":845,"33":901,"34":961,"35":986,"36":1054,"37":1096,"38":1142,"39":1222,"40":1276}};
const ALIGNMENT = {"1":[],"2":[6,18],"3":[6,22],"4":[6,26],"5":[6,30],"6":[6,34],"7":[6,22,38],"8":[6,24,42],"9":[6,26,46],"10":[6,28,50],"11":[6,30,54],"12":[6,32,58],"13":[6,34,62],"14":[6,26,46,66],"15":[6,26,48,70],"16":[6,26,50,74],"17":[6,30,54,78],"18":[6,30,56,82],"19":[6,30,58,86],"20":[6,34,62,90],"21":[6,28,50,72,94],"22":[6,26,50,74,98],"23":[6,30,54,78,102],"24":[6,28,54,80,106],"25":[6,32,58,84,110],"26":[6,30,58,86,114],"27":[6,34,62,90,118],"28":[6,26,50,74,98,122],"29":[6,30,54,78,102,126],"30":[6,26,52,78,104,130],"31":[6,30,56,82,108,134],"32":[6,34,60,86,112,138],"33":[6,30,58,86,114,142],"34":[6,34,62,90,118,146],"35":[6,30,54,78,102,126,150],"36":[6,24,50,76,102,128,154],"37":[6,28,54,80,106,132,158],"38":[6,32,58,84,110,136,162],"39":[6,26,54,82,110,138,166],"40":[6,30,58,86,114,142,170]};
const REMAINDER_BITS = {"1":0,"2":7,"3":7,"4":7,"5":7,"6":7,"7":0,"8":0,"9":0,"10":0,"11":0,"12":0,"13":0,"14":3,"15":3,"16":3,"17":3,"18":3,"19":3,"20":3,"21":4,"22":4,"23":4,"24":4,"25":4,"26":4,"27":4,"28":3,"29":3,"30":3,"31":3,"32":3,"33":3,"34":3,"35":0,"36":0,"37":0,"38":0,"39":0,"40":0};

// Bit mức ECL trong format info theo chuẩn: L=01, M=00, Q=11, H=10.
const LEVEL_FORMAT_BITS = { L: 0b01, M: 0b00, Q: 0b11, H: 0b10 };
const G15 = 0x537;
const G15_MASK = 0x5412;
const G18 = 0x1f25;

function bch15(data5) {
  let d = data5 << 10;
  for (let bit = 14; bit >= 10; bit--) {
    if (((d >>> bit) & 1) === 1) d ^= G15 << (bit - 10);
  }
  return ((data5 << 10) | d) ^ G15_MASK;
}

function bch18(data6) {
  let d = data6 << 12;
  for (let bit = 17; bit >= 12; bit--) {
    if (((d >>> bit) & 1) === 1) d ^= G18 << (bit - 12);
  }
  return (data6 << 12) | d;
}

// ---------- Reed–Solomon trên GF(256), đa thức gốc 0x11d ----------
const EXP = new Uint8Array(512);
const LOG = new Uint8Array(256);
(function initTables() {
  let x = 1;
  for (let i = 0; i < 255; i++) {
    EXP[i] = x;
    LOG[x] = i;
    x <<= 1;
    if (x & 0x100) x ^= 0x11d;
  }
  for (let i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
})();

function gfMul(a, b) {
  if (a === 0 || b === 0) return 0;
  return EXP[LOG[a] + LOG[b]];
}

function rsGenerator(degree) {
  let poly = [1];
  for (let i = 0; i < degree; i++) {
    // Nhân đa thức hiện tại với (x + α^i):
    //  - poly[j] là hệ số của x^(n-j): nhân x → hệ số của x^(n+1-j) tại next[j]
    //  - nhân α^i → hệ số của x^(n-j) tại next[j+1]
    const next = new Array(poly.length + 1).fill(0);
    for (let j = 0; j < poly.length; j++) {
      next[j] ^= poly[j];
      next[j + 1] ^= gfMul(poly[j], EXP[i]);
    }
    poly = next; // hệ số từ bậc cao xuống bậc thấp
  }
  return poly;
}

function rsEncode(data, degree) {
  const gen = rsGenerator(degree);
  const rem = new Array(degree).fill(0);
  for (const byte of data) {
    const factor = byte ^ rem[0];
    rem.copyWithin(0, 1);
    rem[degree - 1] = 0;
    for (let j = 0; j < degree; j++) {
      rem[j] ^= gfMul(gen[j + 1], factor);
    }
  }
  return Uint8Array.from(rem);
}

// ---------- Bit buffer ----------
class BitBuffer {
  constructor() { this.bytes = []; this.bitLen = 0; }
  push(value, length) {
    for (let i = length - 1; i >= 0; i--) {
      const bit = (value >>> i) & 1;
      if (this.bitLen % 8 === 0) this.bytes.push(0);
      if (bit) this.bytes[this.bytes.length - 1] |= 0x80 >>> (this.bitLen % 8);
      this.bitLen++;
    }
  }
}

function cciBits(version) { return version < 10 ? 8 : 16; }

function pickVersion(byteLen, levelName) {
  for (let v = 1; v <= 40; v++) {
    const capacity = DATA_CODEWORDS[levelName][String(v)] * 8;
    const needed = 4 + cciBits(v) + byteLen * 8;
    if (capacity >= needed) return v;
  }
  throw new Error('QR: nội dung vượt sức chứa version 40 (mức ' + levelName + ')');
}

// ---------- Ma trận nền: finder/timing/alignment/dark module ----------
function buildFunctionMap(size, version) {
  const map = new Uint8Array(size * size); // 1 = ô chức năng, không đặt dữ liệu
  const set = (r, c) => { if (r >= 0 && r < size && c >= 0 && c < size) map[r * size + c] = 1; };

  // Finder 7x7 + separator 1 module
  for (const [r0, c0] of [[0, 0], [0, size - 7], [size - 7, 0]]) {
    for (let dr = -1; dr <= 7; dr++) for (let dc = -1; dc <= 7; dc++) set(r0 + dr, c0 + dc);
  }
  // Timing
  for (let i = 8; i < size - 8; i++) { set(6, i); set(i, 6); }
  // Alignment
  const pos = ALIGNMENT[String(version)] || [];
  for (const r of pos) {
    for (const c of pos) {
      if ((r === 6 && c === 6) || (r === 6 && c === size - 7) || (r === size - 7 && c === 6)) continue;
      for (let dr = -2; dr <= 2; dr++) for (let dc = -2; dc <= 2; dc++) set(r + dr, c + dc);
    }
  }
  // Vùng format info (hai bản sao; ô timing (8,6),(6,8) đã chặn ở trên)
  for (let i = 0; i <= 8; i++) {
    if (i !== 6) { set(8, i); set(i, 8); }
  }
  // Bản sao 2 của format info: hàng 8 cột size-8..size-1 và cột 8 dòng size-7..size-1
  for (let i = 0; i <= 7; i++) set(8, size - 1 - i);
  for (let i = size - 7; i < size; i++) set(i, 8);
  // Dark module
  set(size - 8, 8);
  // Vùng version info (version >= 7)
  if (version >= 7) {
    for (let i = 0; i < 6; i++) {
      set(size - 11, i); set(size - 10, i); set(size - 9, i);
      set(i, size - 11); set(i, size - 10); set(i, size - 9);
    }
  }
  return map;
}

function buildBaseMatrix(size, version) {
  const dark = new Uint8Array(size * size);
  const draw = (r, c, on) => { dark[r * size + c] = on ? 1 : 0; };

  // Finder pattern: viền tối + lõi 3x3 tối, separator sáng
  for (const [r0, c0] of [[0, 0], [0, size - 7], [size - 7, 0]]) {
    for (let dr = -1; dr <= 7; dr++) {
      for (let dc = -1; dc <= 7; dc++) {
        const r = r0 + dr, c = c0 + dc;
        if (r < 0 || r >= size || c < 0 || c >= size) continue;
        const inside = dr >= 0 && dr <= 6 && dc >= 0 && dc <= 6;
        if (!inside) { draw(r, c, 0); continue; }
        const edge = dr === 0 || dr === 6 || dc === 0 || dc === 6;
        const core = dr >= 2 && dr <= 4 && dc >= 2 && dc <= 4;
        draw(r, c, edge || core);
      }
    }
  }
  // Timing: tối tại chỉ số chẵn
  for (let i = 8; i < size - 8; i++) {
    draw(6, i, i % 2 === 0);
    draw(i, 6, i % 2 === 0);
  }
  // Alignment 5x5: vòng ngoài tối, vòng trong sáng, tâm tối
  const pos = ALIGNMENT[String(version)] || [];
  for (const r of pos) {
    for (const c of pos) {
      if ((r === 6 && c === 6) || (r === 6 && c === size - 7) || (r === size - 7 && c === 6)) continue;
      for (let dr = -2; dr <= 2; dr++) {
        for (let dc = -2; dc <= 2; dc++) {
          draw(r + dr, c + dc, Math.max(Math.abs(dr), Math.abs(dc)) !== 1);
        }
      }
    }
  }
  // Dark module
  draw(size - 8, 8, 1);

  return { dark, fnMap: buildFunctionMap(size, version) };
}

// Đặt bit dữ liệu theo đường zig-zag chuẩn (bỏ cột timing dọc).
// Khi debugSeq là mảng, ghi lại từng (row,col,bit) đã đặt để kiểm thử chéo.
function placeData(size, bitCount, getBit, fnMap, dark, isDataCell, debugSeq) {
  let col = size - 1;
  let row = size - 1;
  let upward = true; // tương ứng inc = -1 (đi lên) rồi đảo chiều
  let idx = 0;
  while (col > 0) {
    if (col === 6) col--;
    for (;;) {
      // row luôn hợp lệ tại đây
      for (const c of [col, col - 1]) {
        const p = row * size + c;
        if (!fnMap[p]) {
          const bit = idx < bitCount ? getBit(idx) : 0;
          dark[p] = bit;
          isDataCell[p] = 1;
          if (debugSeq) debugSeq.push([row, c, bit]);
          idx++;
        }
      }
      if (upward) row--; else row++;
      if (row < 0 || row >= size) {
        // bước lùi về ô hợp lệ cuối rồi đảo chiều như mô tả chuẩn
        if (upward) row++; else row--;
        upward = !upward;
        break;
      }
    }
    col -= 2;
  }
  return idx;
}

const MASKS = [
  (r, c) => (r + c) % 2 === 0,
  (r) => r % 2 === 0,
  (r, c) => c % 3 === 0,
  (r, c) => (r + c) % 3 === 0,
  (r, c) => (Math.floor(r / 2) + Math.floor(c / 3)) % 2 === 0,
  (r, c) => ((r * c) % 2) + ((r * c) % 3) === 0,
  (r, c) => (((r * c) % 2) + ((r * c) % 3)) % 2 === 0,
  (r, c) => (((r + c) % 2) + ((r * c) % 3)) % 2 === 0,
];

function penaltyScore(m, size) {
  let score = 0;
  // N1: chuỗi >=5 cùng màu theo dòng/cột, cộng 3 + (độ dài - 5) cho mỗi chuỗi
  const lineScore = (get) => {
    let s = 0;
    for (let i = 0; i < size; i++) {
      let run = 1;
      let prev = get(i, 0);
      for (let j = 1; j < size; j++) {
        const v = get(i, j);
        if (v === prev) {
          run++;
        } else {
          if (run >= 5) s += 3 + (run - 5);
          prev = v;
          run = 1;
        }
      }
      if (run >= 5) s += 3 + (run - 5);
    }
    return s;
  };
  score += lineScore((i, j) => m[i * size + j]);
  score += lineScore((i, j) => m[j * size + i]);
  // N2: khối 2x2 cùng màu
  for (let r = 0; r < size - 1; r++) {
    for (let c = 0; c < size - 1; c++) {
      const v = m[r * size + c];
      if (m[r * size + c + 1] === v && m[(r + 1) * size + c] === v && m[(r + 1) * size + c + 1] === v) score += 3;
    }
  }
  // N3: mẫu finder 1011101 kèm 4 module sáng một bên
  const pat1 = [1, 0, 1, 1, 1, 0, 1, 0, 0, 0, 0];
  const pat2 = [0, 0, 0, 0, 1, 0, 1, 1, 1, 0, 1];
  const matchAt = (get) => {
    let count = 0;
    for (let i = 0; i < size; i++) {
      for (let j = 0; j <= size - 11; j++) {
        let ok1 = true, ok2 = true;
        for (let k = 0; k < 11; k++) {
          const v = get(i, j + k);
          if (v !== pat1[k]) ok1 = false;
          if (v !== pat2[k]) ok2 = false;
          if (!ok1 && !ok2) break;
        }
        if (ok1 || ok2) count++;
      }
    }
    return count;
  };
  score += 40 * matchAt((i, j) => m[i * size + j]);
  score += 40 * matchAt((i, j) => m[j * size + i]);
  // N4: lệch tỉ lệ module tối so với 50%
  let darkCount = 0;
  for (let i = 0; i < m.length; i++) darkCount += m[i];
  const percent = (darkCount * 100) / (size * size);
  score += 10 * Math.floor(Math.abs(percent - 50) / 5);
  return score;
}

// Vẽ format info (2 bản sao) theo sơ đồ chuẩn; bit 0 là LSB của từ 15 bit.
function drawFormat(m, size, maskId, levelName) {
  const bits = bch15((LEVEL_FORMAT_BITS[levelName] << 3) | maskId);
  const bit = (i) => (bits >>> i) & 1;
  for (let i = 0; i < 15; i++) {
    const on = bit(i) === 1;
    // Bản sao dọc: cột 8
    if (i < 6) m[i * size + 8] = on;
    else if (i < 8) m[(i + 1) * size + 8] = on;
    else m[(size - 15 + i) * size + 8] = on;
    // Bản sao ngang: dòng 8
    if (i < 8) m[8 * size + (size - 1 - i)] = on;
    else if (i === 8) m[8 * size + 7] = on;
    else m[8 * size + (14 - i)] = on;
  }
}

// Vẽ version info cho version >= 7 (2 bản sao đối xứng).
function drawVersion(m, size, version) {
  if (version < 7) return;
  const bits = bch18(version);
  for (let i = 0; i < 18; i++) {
    const on = ((bits >>> i) & 1) === 1;
    const r = Math.floor(i / 3);
    const c = size - 11 + (i % 3);
    m[r * size + c] = on;
    m[c * size + r] = on;
  }
}

function applyMask(m, size, isDataCell, maskFn) {
  for (let r = 0; r < size; r++) {
    for (let c = 0; c < size; c++) {
      const p = r * size + c;
      if (!isDataCell[p]) continue;
      if (maskFn(r, c)) m[p] ^= 1;
    }
  }
}

/**
 * Sinh ma trận QR cho chuỗi (byte mode UTF-8; token JWS là ASCII nên an toàn).
 * @param {string} text nội dung cần mã hóa
 * @param {{ecLevel?: 'L'|'M'|'Q'|'H', mask?: number}} opts mask để kiểm thử chéo
 * @returns {{size:number, version:number, maskId:number, modules:Uint8Array}}
 */
export function encodeQr(text, opts = {}) {
  const levelName = opts.ecLevel || 'M';
  if (!(levelName in RS_BLOCKS)) throw new Error('QR: ecLevel phải là L/M/Q/H');
  const bytes = new TextEncoder().encode(text);
  if (bytes.length === 0) throw new Error('QR: nội dung rỗng');
  let version = pickVersion(bytes.length, levelName);
  if (opts.minVersion != null && opts.minVersion >= version) {
    const capacity = DATA_CODEWORDS[levelName][String(opts.minVersion)] * 8;
    if (capacity >= 4 + cciBits(opts.minVersion) + bytes.length * 8) version = opts.minVersion;
  }

  // 1. Bit stream: mode byte + số lượng ký tự + dữ liệu + terminator + pad
  const buf = new BitBuffer();
  buf.push(0b0100, 4);
  buf.push(bytes.length, cciBits(version));
  for (const b of bytes) buf.push(b, 8);
  const capacityBits = DATA_CODEWORDS[levelName][String(version)] * 8;
  const terminator = Math.min(4, capacityBits - buf.bitLen);
  if (terminator > 0) buf.push(0, terminator);
  while (buf.bitLen % 8 !== 0) buf.push(0, 1);
  const padBytes = [0xec, 0x11];
  let padIdx = 0;
  while (buf.bytes.length < capacityBits / 8) buf.bytes.push(padBytes[padIdx++ % 2]);

  // 2. Chia khối Reed–Solomon, sinh ECC và interleave
  const groups = RS_BLOCKS[levelName][String(version)];
  const ecCount = EC_PER_BLOCK[levelName][String(version)];
  const blocks = [];
  let offset = 0;
  for (const [count, dataPer] of groups) {
    for (let b = 0; b < count; b++) {
      const data = Uint8Array.from(buf.bytes.slice(offset, offset + dataPer));
      blocks.push({ data, ec: rsEncode(data, ecCount) });
      offset += dataPer;
    }
  }
  const maxData = Math.max(...blocks.map((b) => b.data.length));
  const finalBytes = [];
  for (let i = 0; i < maxData; i++) {
    for (const b of blocks) if (i < b.data.length) finalBytes.push(b.data[i]);
  }
  for (let i = 0; i < ecCount; i++) {
    for (const b of blocks) finalBytes.push(b.ec[i]);
  }

  // 3. Ma trận nền và đặt dữ liệu zig-zag
  const size = 17 + 4 * version;
  const { dark, fnMap } = buildBaseMatrix(size, version);
  const codewordBits = [];
  for (const byte of finalBytes) {
    for (let i = 7; i >= 0; i--) codewordBits.push((byte >>> i) & 1);
  }
  const isDataCell = new Uint8Array(size * size);
  const debugSeq = opts.debugSequence ? [] : null;
  placeData(size, codewordBits.length, (i) => codewordBits[i], fnMap, dark, isDataCell, debugSeq);

  // 4. Chọn mask theo penalty (hoặc dùng mask ép cho kiểm thử chéo)
  let bestMask = 0;
  if (opts.mask != null) {
    bestMask = opts.mask;
  } else {
    let bestScore = Infinity;
    for (let maskId = 0; maskId < 8; maskId++) {
      const candidate = Uint8Array.from(dark);
      applyMask(candidate, size, isDataCell, MASKS[maskId]);
      drawFormat(candidate, size, maskId, levelName);
      drawVersion(candidate, size, version);
      const score = penaltyScore(candidate, size);
      if (score < bestScore) { bestScore = score; bestMask = maskId; }
    }
  }

  // 5. Áp mask và vẽ format/version info lần cuối
  applyMask(dark, size, isDataCell, MASKS[bestMask]);
  drawFormat(dark, size, bestMask, levelName);
  drawVersion(dark, size, version);

  return { size, version, maskId: bestMask, modules: dark, codewords: Uint8Array.from(finalBytes), placedSequence: debugSeq };
}

export default encodeQr;
