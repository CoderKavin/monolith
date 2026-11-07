// Evaluates the design's own icon code and dumps raw layers + tiles + gradient defs.
const fs = require('fs');
const src = fs.readFileSync(process.argv[2], 'utf8');
const script = src.slice(src.indexOf('class Component extends DCLogic'), src.lastIndexOf('</script>'));
const patched = script.replace("return { name: name, c: c, t: th };", "return { name: name, c: c, t: th, raw: layers, tile: tile };");
const Comp = new Function('DCLogic', patched + '; return Component;')(class { constructor(p) { this.props = p; } });
const out = new Comp({ theme: 'Dark', hue: 265 }).renderVals();
const defs = {};
for (const m of src.matchAll(/<linearGradient id="(\w+)"[^>]*><stop offset="0" stop-color="(#\w+)"><\/stop><stop offset="1" stop-color="(#\w+)"><\/stop>/g)) defs[m[1]] = [m[2], m[3]];
const icons = out.all.map(i => ({ name: i.name, tile: i.tile, layers: i.raw }));
fs.writeFileSync('icons.json', JSON.stringify({ defs, icons }, null, 1));
console.log(icons.length, Object.keys(defs).length, icons.map(i => i.name + ':' + i.layers.length).join(' '));
