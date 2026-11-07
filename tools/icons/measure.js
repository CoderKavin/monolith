const { chromium } = require('playwright');
const fs = require('fs');
(async () => {
  const data = JSON.parse(fs.readFileSync('icons.json'));
  const b = await chromium.launch(); const p = await b.newPage();
  await p.setContent('<html><body></body></html>');
  const res = await p.evaluate(async ({ defs, icons }) => {
    const NS = 'http://www.w3.org/2000/svg';
    const defsXml = Object.entries(defs).map(([k, [a, c]]) => `<linearGradient id="${k}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="${a}"/><stop offset="1" stop-color="${c}"/></linearGradient>`).join('');
    const out = [];
    for (const ic of icons) {
      // geometry bbox per layer
      const svg = document.createElementNS(NS, 'svg'); svg.setAttribute('viewBox', '0 0 24 24'); svg.setAttribute('width', 240); document.body.appendChild(svg);
      const boxes = ic.layers.map(l => { const e = document.createElementNS(NS, 'path'); e.setAttribute('d', l.d); svg.appendChild(e); const bb = e.getBBox(); return [bb.x, bb.y, bb.width, bb.height]; });
      svg.remove();
      // ink bounds: render all layers in a single colour, opaque, to measure the visible silhouette
      const S = 960, sc = S / 32;
      const paths = ic.layers.map(l => `<path d="${l.d}" fill="${l.f === 'none' ? 'none' : '#000'}" stroke="${l.s === 'none' ? 'none' : '#000'}" stroke-width="${l.w}" stroke-linecap="round" stroke-linejoin="round" fill-rule="evenodd"/>`).join('');
      const xml = `<svg xmlns="${NS}" width="${S}" height="${S}" viewBox="-4 -4 32 32"><defs>${defsXml}</defs>${paths}</svg>`;
      const img = new Image(); img.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(xml); await img.decode();
      const cv = document.createElement('canvas'); cv.width = cv.height = S; const g = cv.getContext('2d'); g.drawImage(img, 0, 0);
      const px = g.getImageData(0, 0, S, S).data;
      let x0 = S, y0 = S, x1 = 0, y1 = 0, m = 0, mx = 0, my = 0;
      for (let y = 0; y < S; y++) for (let x = 0; x < S; x++) { const a = px[(y * S + x) * 4 + 3]; if (a > 8) { if (x < x0) x0 = x; if (x > x1) x1 = x; if (y < y0) y0 = y; if (y > y1) y1 = y; m += a; mx += a * x; my += a * y; } }
      const u = v => v / sc - 4;
      out.push({ name: ic.name, boxes, ink: [u(x0), u(y0), u(x1 + 1), u(y1 + 1)], centroid: [u(mx / m), u(my / m)] });
    }
    return out;
  }, data);
  for (const r of res) { const [a, b2, c, d] = r.ink; r.cx = (a + c) / 2; r.cy = (b2 + d) / 2; r.w = c - a; r.h = d - b2;
    console.log(r.name.padEnd(15), 'bbox ctr', r.cx.toFixed(2), r.cy.toFixed(2), ' size', r.w.toFixed(1), 'x', r.h.toFixed(1), ' mass', r.centroid.map(v => v.toFixed(2)).join(',')); }
  fs.writeFileSync('measure.json', JSON.stringify(res, null, 1));
  await b.close();
})();
