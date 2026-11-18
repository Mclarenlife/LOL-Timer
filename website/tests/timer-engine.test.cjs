const test = require('node:test');
const assert = require('node:assert/strict');
const E = require('../timer-engine.js');
const now = 1800000000000;
test('all nine baseline cooldowns match the bundled Riot data', () => {
  const fs = require('node:fs');
  const data = JSON.parse(fs.readFileSync(require('node:path').join(__dirname, '../assets/lol/summoner-data.json'), 'utf8').replace(/^\uFEFF/, '')).data;
  E.spells.forEach(spell => assert.equal(spell.seconds, data[spell.id].cooldown[0]));
});
test('ten timers are independent and repeated clicks cannot restart a running timer', () => {
  const state = E.defaults();
  assert.equal(state.slots.length, 10);
  assert.equal(E.start(state.slots[0], now), true);
  E.start(state.slots[5], now + 1000);
  assert.equal(E.start(state.slots[0], now + 20000), false);
  assert.equal(E.remaining(state.slots[0], now + 10000), 290);
  assert.equal(E.remaining(state.slots[5], now + 10000), 171);
  assert.equal(E.remaining(state.slots[2], now + 10000), 0);
});
test('elapsed time is based on deadline, including background throttling and refresh', () => {
  const state = E.defaults(); E.start(state.slots[0], now);
  const restored = E.restore(JSON.parse(JSON.stringify(state)), now + 185400);
  assert.equal(E.remaining(restored.slots[0], now + 185400), 115);
  assert.equal(E.remaining(restored.slots[0], now + 500000), 0);
});
test('end reminder occurs exactly once, even after reload', () => {
  const state = E.defaults(); E.start(state.slots[0], now);
  assert.deepEqual(E.advance(state, now + 299999), []);
  assert.equal(E.advance(state, now + 300000)[0].remaining, 0);
  assert.deepEqual(E.advance(state, now + 301000), []);
  const restored = E.restore(JSON.parse(JSON.stringify(state)), now + 302000);
  assert.deepEqual(E.advance(restored, now + 302000), []);
});
test('one-minute advance reminder fires at threshold; no second end reminder', () => {
  const state = E.defaults(); state.settings.lead = 60; E.start(state.slots[0], now);
  assert.deepEqual(E.advance(state, now + 239999), []);
  assert.equal(E.advance(state, now + 240000)[0].remaining, 60);
  assert.deepEqual(E.advance(state, now + 300000), []);
});
test('short spells and delayed background callbacks have meaningful reminders', () => {
  const state = E.defaults(); state.settings.lead = 60;
  E.start(state.slots[3], now);
  assert.equal(E.advance(state, now)[0].remaining, 15);
  E.start(state.slots[0], now);
  assert.equal(E.advance(state, now + 310000)[0].remaining, 0);
});
test('changing a spell cancels its timer, rejects duplicates, preserves the other slot', () => {
  const state = E.defaults(); E.start(state.slots[0], now); E.start(state.slots[1], now);
  assert.equal(E.select(state, 0, 'SummonerTeleport'), false);
  assert.equal(E.select(state, 0, 'invalid'), false);
  assert.equal(E.select(state, 0, 'SummonerHeal'), true);
  assert.equal(state.slots[0].endAt, null);
  assert.equal(state.slots[1].endAt, now + 300000);
});
test('restart and per-slot reset create a fresh reminder cycle', () => {
  const state = E.defaults(); const slot = state.slots[0]; E.start(slot, now); E.advance(state, now + 300000);
  assert.equal(E.start(slot, now + 301000), true);
  assert.equal(slot.alerted, false);
  E.reset(slot); assert.equal(slot.endAt, null);
  assert.deepEqual(E.advance(state, now + 999000), []);
});
test('settings changes apply to pending timers without repeating delivered reminders', () => {
  const state = E.defaults(); E.start(state.slots[0], now);
  state.settings.lead = 90;
  assert.equal(E.advance(state, now + 215000)[0].remaining, 85);
  state.settings.lead = 120;
  assert.deepEqual(E.advance(state, now + 216000), []);
});
test('multiple simultaneous timers each emit one reminder', () => {
  const state = E.defaults(); [0, 2, 4, 6, 8].forEach(i => E.start(state.slots[i], now));
  assert.equal(E.advance(state, now + 300000).length, 5);
  assert.equal(E.advance(state, now + 300001).length, 0);
});
test('invalid stored state cannot inject spells, negative settings, or nonfinite deadlines', () => {
  assert.deepEqual(E.restore(null), E.defaults());
  const state = E.defaults(); state.settings = { lead: -1, sound: 'no', voice: true };
  state.slots[0] = { spellId: '<img onerror=alert(1)>', endAt: Infinity };
  state.slots[1].endAt = '1800000300000';
  const restored = E.restore(state, now);
  assert.equal(restored.settings.lead, 0); assert.equal(restored.settings.sound, true);
  assert.equal(restored.settings.voice, true); assert.equal(restored.slots[0].spellId, 'SummonerFlash');
  assert.equal(restored.slots[1].endAt, null);
});
test('time format handles zero, seconds, minutes', () => {
  assert.equal(E.format(0), '00:00'); assert.equal(E.format(9), '00:09'); assert.equal(E.format(300), '05:00');
});

test('haste, elapsed time and recording deduction',()=>{
  const engine=require('../timer-engine.js'),m=engine.defaults(),now=1000000;
  m.settings.autoDeduct=5;engine.configure(m,0,5,now);engine.record(m,0,now);
  require('node:assert/strict').equal(m.slots[0].endAt,now+229375);
  engine.configure(m,0,6,now+5000);require('node:assert/strict').equal(m.slots[0].endAt,now+212391);
  require('node:assert/strict').equal(engine.durationMs('SummonerSmite',14),15000);
  require('node:assert/strict').equal(engine.durationMs('SummonerFlash',8),300000);
});

test('renamed spells match the shipped voice grammar for every position', () => {
  const fs = require('node:fs');
  const path = require('node:path');
  const vm = require('node:vm');
  const read = name => fs.readFileSync(path.join(__dirname, '..', name), 'utf8');
  const parse = vm.runInNewContext(read('voice.js') + '\nRiftVoice.parse');
  const commands = JSON.parse(read('assets/speech/commands.json')).map(words => words.replace(/\s/g, ''));
  const lanes = ['上单', '打野', '中单', '下路', '辅助'];
  for (const [name, id] of [['治疗', 'SummonerHeal'], ['疾跑', 'SummonerHaste']]) {
    assert.equal(E.spells.find(spell => spell.id === id).name, name);
    lanes.forEach((lane, role) => {
      const phrase = lane + name;
      assert.ok(commands.includes(phrase), phrase);
      assert.equal(parse(phrase).role, role);
      assert.equal(parse(phrase).id, id);
    });
  }
});
