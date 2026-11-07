// Turns the design's icon layers into Android vector drawables (colour + themed) and an appfilter.
const fs = require('fs'), path = require('path');
const { defs, icons } = JSON.parse(fs.readFileSync('icons.json'));
const meas = Object.fromEntries(JSON.parse(fs.readFileSync('measure.json')).map(m => [m.name, m]));
const off = JSON.parse(fs.readFileSync('offsets.json'));
const OUT = process.argv[2];
const KEY = { 'Phone': 'phone', 'Contacts': 'contacts', 'Messages': 'messages', 'Email': 'email', 'Internet': 'internet', 'Galaxy Store': 'galaxy_store',
  'Camera': 'camera', 'Gallery': 'gallery', 'Music': 'music', 'Voice Recorder': 'voice_recorder', 'Notes': 'notes', 'My Files': 'my_files', 'Clock': 'clock',
  'Calendar': 'calendar', 'Calculator': 'calculator', 'Weather': 'weather', 'Settings': 'settings', 'Reminder': 'reminder', 'Health': 'health', 'Monolith': 'monolith' };
const COMPONENTS = {
  phone: ['com.samsung.android.dialer/com.samsung.android.dialer.DialtactsActivity', 'com.samsung.android.dialer/.DialtactsActivity', 'com.samsung.android.contacts/com.android.dialer.DialtactsActivity'],
  contacts: ['com.samsung.android.app.contacts/com.samsung.android.contacts.contactslist.PeopleActivity', 'com.samsung.android.contacts/com.samsung.android.contacts.contactslist.PeopleActivity', 'com.samsung.android.contacts/com.android.contacts.activities.PeopleActivity'],
  messages: ['com.samsung.android.messaging/com.android.mms.ui.ConversationComposer', 'com.samsung.android.messaging/com.samsung.android.messaging.ui.ConversationListActivity', 'com.samsung.android.messaging/com.samsung.android.messaging.ui.ConversationList'],
  email: ['com.samsung.android.email.provider/com.samsung.android.email.ui.activity.MessageListXL', 'com.samsung.android.email.provider/com.samsung.android.email.ui.messagelist.EmailMessageListActivity'],
  internet: ['com.sec.android.app.sbrowser/com.sec.android.app.sbrowser.SBrowserMainActivity', 'com.sec.android.app.sbrowser/.SBrowserMainActivity', 'com.sec.android.app.sbrowser/com.sec.android.app.sbrowser.SBrowserMainActivityManager'],
  galaxy_store: ['com.sec.android.app.samsungapps/com.sec.android.app.samsungapps.SamsungAppsMainActivity', 'com.sec.android.app.samsungapps/com.sec.android.app.samsungapps.Main'],
  camera: ['com.sec.android.app.camera/com.sec.android.app.camera.Camera', 'com.sec.android.app.camera/.Camera'],
  gallery: ['com.sec.android.gallery3d/com.samsung.android.gallery.app.activity.GalleryActivity', 'com.sec.android.gallery3d/com.sec.android.gallery3d.app.GalleryActivity', 'com.sec.android.gallery3d/com.sec.android.gallery3d.app.GalleryOpaqueActivity', 'com.sec.android.gallery3d/.app.GalleryOpaqueActivity'],
  music: ['com.sec.android.app.music/com.sec.android.app.music.common.activity.MusicMainActivity', 'com.sec.android.app.music/com.samsung.android.app.music.common.activity.MusicMainActivity'],
  voice_recorder: ['com.sec.android.app.voicenote/com.sec.android.app.voicenote.main.VNMainActivity'],
  notes: ['com.samsung.android.app.notes/com.samsung.android.app.notes.memolist.MemoListActivity', 'com.samsung.android.app.notes/.memolist.MemoListActivity'],
  my_files: ['com.sec.android.app.myfiles/com.sec.android.app.myfiles.external.ui.MainActivity', 'com.sec.android.app.myfiles/.external.ui.MainActivity', 'com.sec.android.app.myfiles/com.sec.android.app.myfiles.ui.MainActivity', 'com.sec.android.app.myfiles/com.sec.android.app.myfiles.common.MainActivity', 'com.sec.android.app.myfiles/com.sec.android.app.myfiles.external.ui.LaunchActivity'],
  clock: ['com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.ClockPackage', 'com.sec.android.app.clockpackage/com.sec.android.app.clockpackage.common.activity.ClockPackage'],
  calendar: ['com.samsung.android.calendar/com.android.calendar.AllInOneActivity', 'com.samsung.android.calendar/com.samsung.android.app.calendar.activity.MainActivity'],
  calculator: ['com.sec.android.app.popupcalculator/com.sec.android.app.popupcalculator.Calculator', 'com.sec.android.app.popupcalculator/.Calculator'],
  weather: ['com.sec.android.daemonapp/com.sec.android.daemonapp.app.MainActivity', 'com.sec.android.daemonapp/com.samsung.android.weather.app.AppLauncherActivity'],
  settings: ['com.android.settings/com.android.settings.Settings', 'com.android.settings/.Settings'],
  reminder: ['com.samsung.android.app.reminder/com.samsung.android.app.reminder.ui.LaunchMainActivity'],
  health: ['com.sec.android.app.shealth/com.samsung.android.app.shealth.home.HomeDashboardActivity', 'com.sec.android.app.shealth/com.samsung.android.app.shealth.home.HomeMainActivity', 'com.sec.android.app.shealth/com.sec.android.app.shealth.SplashScreenActivity'],
  monolith: ['com.monolith.widgets/com.monolith.widgets.MainActivity']
};
const N = v => Math.round(v * 1000) / 1000;
// Tile 36 units across; the 24-unit glyph grid sits in its middle 2/3, exactly like the design (44px glyph in a 66px tile).
function gradient(stops, x, y0, y1) {
  return `<aapt:attr name="%ATTR%"><gradient android:type="linear" android:startX="${N(x)}" android:startY="${N(y0)}" android:endX="${N(x)}" android:endY="${N(y1)}" android:startColor="${stops[0]}" android:endColor="${stops[1]}"/></aapt:attr>`;
}
function paint(value, box) {
  const m = /^url\(#(\w+)\)$/.exec(value);
  if (m) return { grad: gradient(defs[m[1]], box[0] + box[2] / 2, box[1], box[1] + box[3]) };
  return { color: value };
}
function tileFill(tile) {
  const m = /linear-gradient\(180deg, (#\w+), (#\w+)\)/.exec(tile);
  if (m) return { grad: gradient([m[1], m[2]], 18, 0, 36) };
  return { color: tile };
}
const CIRCLE = 'M18,0A18,18 0 1,1 18,36A18,18 0 1,1 18,0Z';
function pathXml(d, fill, stroke, w, alpha) {
  let attrs = [`android:pathData="${d}"`, 'android:fillType="evenOdd"'], kids = '';
  if (fill) { if (fill.color) attrs.push(`android:fillColor="${fill.color}"`); else kids += fill.grad.replace('%ATTR%', 'android:fillColor'); if (alpha < 1) attrs.push(`android:fillAlpha="${alpha}"`); }
  if (stroke) { if (stroke.color) attrs.push(`android:strokeColor="${stroke.color}"`); else kids += stroke.grad.replace('%ATTR%', 'android:strokeColor');
    attrs.push(`android:strokeWidth="${w}"`, 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"'); if (alpha < 1) attrs.push(`android:strokeAlpha="${alpha}"`); }
  return kids ? `    <path ${attrs.join(' ')}>\n      ${kids}\n    </path>` : `    <path ${attrs.join(' ')}/>`;
}
function vector(ic, themed) {
  const [dx, dy] = off[ic.name] || [0, 0], boxes = meas[ic.name].boxes;
  const bg = themed ? { color: '@color/th_bg' } : tileFill(ic.tile);
  const body = ic.layers.map((l, i) => {
    if (themed) {
      const col = { color: l.role === 'c' ? '@color/th_bg' : '@color/th_fg' };
      const a = l.role === 'a' ? 0.55 : (l.role === 'c' ? 1 : l.o);
      return pathXml(l.d, l.f === 'none' ? null : col, l.s === 'none' ? null : col, l.w, a);
    }
    return pathXml(l.d, l.f === 'none' ? null : paint(l.f, boxes[i]), l.s === 'none' ? null : paint(l.s, boxes[i]), l.w, l.o);
  }).join('\n');
  return `<?xml version="1.0" encoding="utf-8"?>
<!-- Generated from the Monolith icon design (${ic.name}). Do not edit by hand. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android" xmlns:aapt="http://schemas.android.com/aapt"
    android:width="192dp" android:height="192dp" android:viewportWidth="36" android:viewportHeight="36">
${pathXml(CIRCLE, bg, null, 0, 1)}
  <group android:translateX="${N(6 + dx)}" android:translateY="${N(6 + dy)}">
${body.replace(/^/gm, '  ')}
  </group>
</vector>
`;
}
const appfilter = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>', '  <scale factor="1.0"/>'];
const drawable = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>', '  <version>1</version>', '  <category title="Monolith"/>'];
for (const ic of icons) {
  const k = KEY[ic.name];
  for (const [flavor, themed] of [['colour', false], ['themed', true]]) {
    const dir = path.join(OUT, flavor, 'res', 'drawable'); fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(path.join(dir, k + '.xml'), vector(ic, themed));
  }
  for (const c of COMPONENTS[k]) appfilter.push(`  <item component="ComponentInfo{${c}}" drawable="${k}"/>`);
  drawable.push(`  <item drawable="${k}"/>`);
}
appfilter.push('</resources>', ''); drawable.push('</resources>', '');
for (const d of ['main/res/xml', 'main/assets']) fs.mkdirSync(path.join(OUT, d), { recursive: true });
for (const f of ['main/res/xml/appfilter.xml', 'main/assets/appfilter.xml']) fs.writeFileSync(path.join(OUT, f), appfilter.join('\n'));
for (const f of ['main/res/xml/drawable.xml', 'main/assets/drawable.xml']) fs.writeFileSync(path.join(OUT, f), drawable.join('\n'));
console.log('wrote', icons.length * 2, 'vectors,', appfilter.length - 5, 'appfilter entries');
