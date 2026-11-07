// Renders (a) the design's own markup and (b) the generated Android vectors converted back to SVG, then diffs them.
const { chromium } = require('playwright'); const fs = require('fs');
const { defs, icons } = JSON.parse(fs.readFileSync('icons.json')); const off = JSON.parse(fs.readFileSync('offsets.json'));
const KEYS = fs.readdirSync('/home/claude/monolith/icons/src/colour/res/drawable');
const keyOf = n => n.toLowerCase().replace(/ /g, '_');
let GID = 0;
function vd2svg(xml) {
  let g = 0, defsOut = '';
  const body = xml.replace(/<\?xml[^>]*>|<!--.*?-->/gs, '')
    .replace(/<vector[^>]*>/, '').replace('</vector>', '')
    .replace(/<group android:translateX="([\d.-]+)" android:translateY="([\d.-]+)">/, '<g transform="translate($1 $2)">').replace('</group>', '</g>')
    .replace(/<path ([^>]*?)(\/?)>((?:\s*<aapt:attr[\s\S]*?<\/aapt:attr>)*)\s*(<\/path>)?/g, (m, attrs, selfc, kids) => {
      const A = k => (new RegExp('android:' + k + '="([^"]*)"').exec(attrs) || [])[1];
      let fill = A('fillColor') || 'none', stroke = A('strokeColor') || 'none';
      for (const k of kids.matchAll(/name="android:(\w+)"><gradient [^>]*startX="([\d.-]+)" android:startY="([\d.-]+)" android:endX="([\d.-]+)" android:endY="([\d.-]+)" android:startColor="(#\w+)" android:endColor="(#\w+)"/g)) {
        const id = "vg" + (GID++); defsOut += `<linearGradient id="${id}" gradientUnits="userSpaceOnUse" x1="${k[2]}" y1="${k[3]}" x2="${k[4]}" y2="${k[5]}"><stop offset="0" stop-color="${k[6]}"/><stop offset="1" stop-color="${k[7]}"/></linearGradient>`;
        if (k[1] === 'fillColor') fill = `url(#${id})`; else stroke = `url(#${id})`;
      }
      return `<path d="${A('pathData')}" fill="${fill}" fill-opacity="${A('fillAlpha') || 1}" stroke="${stroke}" stroke-opacity="${A('strokeAlpha') || 1}" stroke-width="${A('strokeWidth') || 0}" stroke-linecap="round" stroke-linejoin="round" fill-rule="evenodd"/>`;
    });
  return `<svg xmlns="http://www.w3.org/2000/svg" width="198" height="198" viewBox="0 0 36 36"><defs>${defsOut}</defs>${body}</svg>`;
}
(async () => {
  const defsXml = Object.entries(defs).map(([k, [a, c]]) => `<linearGradient id="${k}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${c}"/></linearGradient>`).join('');
  let html = `<html><body style="margin:0;background:#888;display:flex;flex-wrap:wrap;gap:0;width:${198 * 2 * 5}px"><svg width="0" height="0" style="position:absolute"><defs>${defsXml}</defs></svg>`;
  for (const ic of icons) {
    const [dx, dy] = off[ic.name] || [0, 0];
    const paths = ic.layers.map(l => `<path transform="translate(${dx} ${dy})" d="${l.d}" fill="${l.f}" stroke="${l.s}" stroke-width="${l.w}" opacity="${l.o}" stroke-linecap="round" stroke-linejoin="round" fill-rule="evenodd"/>`).join('');
    const design = `<div style="width:198px;height:198px;border-radius:99px;background:${ic.tile};display:flex;align-items:center;justify-content:center"><svg width="132" height="132" viewBox="0 0 24 24">${paths}</svg></div>`;
    const gen = vd2svg(fs.readFileSync('/home/claude/monolith/icons/src/colour/res/drawable/' + keyOf(ic.name) + '.xml', 'utf8'));
    html += `<div class="pair" style="display:flex">${design}${gen}</div>`;
  }
  html += '</body></html>';
  fs.writeFileSync('verify.html', html);
  const b = await chromium.launch(); const p = await b.newPage({ viewport: { width: 1980, height: 900 } });
  await p.setContent(html);
  const pairs = await p.$$('.pair'); const sharp = require('fs');
  await p.screenshot({ path: 'verify.png', fullPage: true });
  const diffs = await p.evaluate(async () => {
    const out = [];
    for (const pair of document.querySelectorAll('.pair')) {
      out.push(pair.getBoundingClientRect().toJSON());
    }
    return out;
  });
  // pixel diff via screenshot buffers
  const res = [];
  for (let i = 0; i < pairs.length; i++) {
    const r = diffs[i];
    const A = await p.screenshot({ clip: { x: r.x, y: r.y, width: 198, height: 198 } });
    const B = await p.screenshot({ clip: { x: r.x + 198, y: r.y, width: 198, height: 198 } });
    res.push([A.toString('base64'), B.toString('base64')]);
  }
  const p2 = await b.newPage();
  const scores = await p2.evaluate(async (res) => {
    const load = async s => { const im = new Image(); im.src = 'data:image/png;base64,' + s; await im.decode(); const c = document.createElement('canvas'); c.width = im.width; c.height = im.height; const g = c.getContext('2d'); g.drawImage(im, 0, 0); return g.getImageData(0, 0, c.width, c.height).data; };
    const out = [];
    for (const [a, b] of res) { const A = await load(a), B = await load(b); let bad = 0, n = A.length / 4; for (let i = 0; i < A.length; i += 4) { const d = Math.abs(A[i] - B[i]) + Math.abs(A[i + 1] - B[i + 1]) + Math.abs(A[i + 2] - B[i + 2]); if (d > 30) bad++; } out.push((100 * bad / n).toFixed(2)); }
    return out;
  }, res);
  icons.forEach((ic, i) => console.log(ic.name.padEnd(15), scores[i] + '% pixels differ'));
  await b.close();
})();
