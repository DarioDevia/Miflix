// Executes the HTML emitted by tvTrailerHtml against a stub of the official IFrame API.
// It checks our JavaScript contract, not a live WebView or YouTube video.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
const html = fs.readFileSync(process.argv[2], 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
let options, state = -1, plays = 0, pauses = 0, size, events = {}, logs = [];
const context = {
  console: {log: value => logs.push(value)},
  window: {innerWidth: 1280, innerHeight: 720, addEventListener: (name, cb) => events[name] = cb},
  YT: {Player: function(id, config) {
    options = config;
    return {getPlayerState: () => state, setSize: (w,h) => size = [w,h],
      playVideo: () => plays++, pauseVideo: () => pauses++};
  }}
};
vm.createContext(context); vm.runInContext(script, context);
context.onYouTubeIframeAPIReady();
assert.equal(options.videoId, 'abcdefghijk');
assert.equal(options.playerVars.start, 12);
assert.equal(options.playerVars.autoplay, 0);
assert.equal(options.playerVars.origin, 'https://ar.com.miflix.client.tv');
assert.equal(plays, 0);
options.events.onReady(); assert.ok(logs.includes('MIFLIX_TV_READY'));
context.tvToggle(); assert.equal(plays, 1); assert.ok(logs.includes('MIFLIX_TV_PLAY_REQUEST'));
state = 1; context.tvToggle(); assert.equal(pauses, 1);
context.tvPause(); assert.equal(pauses, 2);
context.window.innerWidth = 120; context.window.innerHeight = 80; events.resize();
assert.deepEqual(size, [200,200]);
options.events.onError({data:153}); assert.ok(logs.includes('MIFLIX_TV_ERROR:153'));
options.events.onAutoplayBlocked(); assert.ok(logs.includes('MIFLIX_TV_BLOCKED'));
options.events.onStateChange({data:1}); assert.ok(logs.includes('MIFLIX_TV_STATE:1'));
assert.ok(html.includes('MIFLIX_TV_SCRIPT_ERROR'));
console.log('PASS: IFrame identity, cue, explicit play/pause, resize, state/error/blocked callbacks');
