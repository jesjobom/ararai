import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import vm from 'node:vm';

const kotlinSource = readFileSync(
  new URL('../app/src/main/java/com/jesjobom/ararai/widget/runtime/WidgetJavaScriptEngine.kt', import.meta.url),
  'utf8',
);
const standardLibrary = kotlinSource.match(/private const val STDLIB = """([\s\S]*?)"""/)?.[1];
assert.ok(standardLibrary, 'widget runtime standard library must be available');
const context = vm.createContext({});
vm.runInContext(standardLibrary, context);
const runtime = vm.runInContext('__araraiCreateRuntime({})', context);
const seededRuntime = vm.runInContext('__araraiCreateRuntime({seed: 1})', context);

const outcomes = {
  events: {
    status: 'success',
    payload: {
      kind: 'success',
      events: [{ text: 'An event', canonicalUrl: 'https://en.wikipedia.org/wiki/Event' }],
      metadata: { tags: ['history'] },
    },
  },
  other: { status: 'success', payload: { kind: 'success', events: [] } },
  rejected: { status: 'failure', reason: 'unavailable' },
  noResults: { status: 'success', payload: { kind: 'failure', reason: 'NoResults' } },
};

test('reads exact payload-relative array paths for the selected alias', () => {
  assert.equal(runtime.isSuccess(outcomes, 'events'), true);
  assert.equal(runtime.isSuccess(outcomes, 'other'), true);
  assert.equal(runtime.getArray(outcomes, 'events', 'events').length, 1);
  assert.equal(runtime.getArray(outcomes, 'other', 'events').length, 0);
  assert.equal(runtime.getArray(outcomes, 'events', 'metadata.tags')[0], 'history');
  assert.equal(runtime.get(outcomes, 'events', 'events.0.text'), 'An event');
});

test('does not confuse tool failures, missing paths, or wrong types with an empty list', () => {
  assert.equal(runtime.isSuccess(outcomes, 'rejected'), false);
  assert.equal(runtime.isSuccess(outcomes, 'noResults'), false);
  assert.equal(runtime.isSuccess(outcomes, 'missing'), false);
  assert.throws(() => runtime.getArray(outcomes, 'rejected', 'events'));
  assert.throws(() => runtime.getArray(outcomes, 'events', 'tags'));
  assert.throws(() => runtime.getArray(outcomes, 'events', 'events.0.text'));
  assert.throws(() => runtime.getArray(outcomes, 'events', '__proto__.events'));
});

test('constructs a link with the exact alias and result provenance path', () => {
  const link = runtime.linkFor(outcomes, 'events', 'events.0.canonicalUrl', 'Read more');
  assert.deepEqual(JSON.parse(JSON.stringify(link)), {
    type: 'https_link',
    label: 'Read more',
    url: 'https://en.wikipedia.org/wiki/Event',
    sourceAlias: 'events',
    sourceField: 'events.0.canonicalUrl',
  });
  assert.throws(() => runtime.linkFor(outcomes, 'events', 'events.0.text', 'Read more'));
});

test('selects a seeded array item without exposing the outcome envelope', () => {
  const data = {
    events: {
      status: 'success',
      payload: {kind: 'success', events: [{text: 'First'}, {text: 'Second'}]},
    },
    empty: outcomes.other,
    failed: outcomes.rejected,
  };
  const picked = seededRuntime.pickArrayItem(data, 'events', 'events');
  assert.deepEqual(JSON.parse(JSON.stringify(picked)), {item: {text: 'Second'}, path: 'events.1'});
  assert.equal(seededRuntime.pickArrayItem(data, 'empty', 'events'), null);
  assert.equal(seededRuntime.pickArrayItem(data, 'failed', 'events'), null);
  assert.throws(() => seededRuntime.pickArrayItem(data, 'events', 'payload.events'));
  assert.throws(() => runtime.pickArrayItem(data, 'events', 'events'));
});

test('compact render produces a valid card and a result-proven link', () => {
  const render = (selectedRuntime, selectedOutcomes) => {
    const picked = selectedRuntime.pickArrayItem(selectedOutcomes, 'events', 'events');
    if (!picked) return selectedRuntime.card('No events today');
    return selectedRuntime.card([
      picked.item.text,
      selectedRuntime.linkFor(selectedOutcomes, 'events', picked.path + '.canonicalUrl', 'Read more'),
    ]);
  };
  const success = JSON.parse(JSON.stringify(render(seededRuntime, outcomes)));
  assert.deepEqual(success, {
    type: 'card',
    child: {type: 'column', children: [
      {type: 'text', text: 'An event', tone: 'neutral'},
      {
        type: 'https_link', label: 'Read more',
        url: 'https://en.wikipedia.org/wiki/Event',
        sourceAlias: 'events', sourceField: 'events.0.canonicalUrl',
      },
    ]},
  });
  assert.deepEqual(JSON.parse(JSON.stringify(render(seededRuntime, {events: outcomes.rejected}))), {
    type: 'card', child: {type: 'column', children: [
      {type: 'text', text: 'No events today', tone: 'neutral'},
    ]},
  });
  assert.throws(() => seededRuntime.card([]));
  assert.throws(() => seededRuntime.card([null]));
});

test('card is a complete node, not a mutable builder or an outcomes method', () => {
  const data = {events: {status: 'success', payload: {kind: 'success', events: [{
    title: 'Event title', text: 'Event description',
    canonicalUrl: 'https://en.wikipedia.org/wiki/Event',
  }]}}};
  assert.throws(() => seededRuntime.card());
  assert.equal(typeof data.getArray, 'undefined');

  const picked = seededRuntime.pickArrayItem(data, 'events', 'events', 'seeded_random');
  const card = seededRuntime.card([
    picked.item.title,
    picked.item.text,
    seededRuntime.linkFor(data, 'events', picked.path + '.canonicalUrl', picked.item.title),
  ]);
  assert.deepEqual(card.child.children.map((child) => child.type), ['text', 'text', 'https_link']);
  assert.equal(card.child.children[1].text, 'Event description');
  assert.equal(card.child.children[2].sourceField, 'events.0.canonicalUrl');
  assert.equal(typeof card.addText, 'undefined');
  assert.equal(typeof card.addLinkFor, 'undefined');
});

test('custom render chooses the oldest event and links that same result', () => {
  const data = {events: {status: 'success', payload: {kind: 'success', events: [
    {year: 2001, text: 'Newer', canonicalUrl: 'https://en.wikipedia.org/wiki/Newer'},
    {year: 1500, text: 'Oldest', canonicalUrl: 'https://en.wikipedia.org/wiki/Oldest'},
    {year: 1900, text: 'Older', canonicalUrl: 'https://en.wikipedia.org/wiki/Older'},
  ]}}};
  function render(selectedRuntime, selectedOutcomes) {
    if (!selectedRuntime.isSuccess(selectedOutcomes, 'events')) return selectedRuntime.card('No events');
    const events = selectedRuntime.getArray(selectedOutcomes, 'events', 'events');
    if (events.length === 0) return selectedRuntime.card('No events');
    let chosen = 0;
    for (let i = 1; i < events.length; i++) {
      if (events[i].year < events[chosen].year) chosen = i;
    }
    return selectedRuntime.card([
      events[chosen].text,
      selectedRuntime.linkFor(selectedOutcomes, 'events', 'events.' + chosen + '.canonicalUrl', 'Read'),
    ]);
  }
  const card = JSON.parse(JSON.stringify(render(runtime, data)));
  assert.equal(card.child.children[0].text, 'Oldest');
  assert.equal(card.child.children[1].url, 'https://en.wikipedia.org/wiki/Oldest');
  assert.equal(card.child.children[1].sourceField, 'events.1.canonicalUrl');
  assert.equal(render(runtime, {events: outcomes.rejected}).child.children[0].text, 'No events');
});

test('one collection-card call handles success, empty, failure, and link provenance', () => {
  const config = {
    items: 'events', title: 'text', url: 'canonicalUrl', fallback: 'No events',
  };
  const card = JSON.parse(JSON.stringify(seededRuntime.collectionCard(outcomes, 'events', config)));
  assert.deepEqual(card, {
    type: 'card', child: {type: 'column', children: [
      {type: 'text', text: 'An event', tone: 'neutral'},
      {
        type: 'https_link', label: 'An event',
        url: 'https://en.wikipedia.org/wiki/Event',
        sourceAlias: 'events', sourceField: 'events.0.canonicalUrl',
      },
    ]},
  });
  const fallback = JSON.parse(JSON.stringify(seededRuntime.card('No events')));
  assert.deepEqual(JSON.parse(JSON.stringify(seededRuntime.collectionCard(outcomes, 'other', config))), fallback);
  assert.deepEqual(JSON.parse(JSON.stringify(seededRuntime.collectionCard(outcomes, 'rejected', config))), fallback);
  assert.throws(() => runtime.collectionCard(outcomes, 'events', config));
  assert.throws(() => seededRuntime.collectionCard(outcomes, 'events', {...config, url: 'text'}));
});

test('collection card links a selected canonical page even when the model omits url', () => {
  const pages = {lookup: {status: 'success', payload: {kind: 'success', pages: [
    {title: 'Brazil', canonicalUrl: 'https://en.wikipedia.org/wiki/Brazil'},
  ]}}};
  const card = JSON.parse(JSON.stringify(runtime.collectionCard(pages, 'lookup', {
    items: 'pages', title: 'title', fallback: 'No pages', selection: 'first',
  })));
  assert.deepEqual(card.child.children[1], {
    type: 'https_link', label: 'Brazil',
    url: 'https://en.wikipedia.org/wiki/Brazil',
    sourceAlias: 'lookup', sourceField: 'pages.0.canonicalUrl',
  });
});

test('page-summary result supplies its complete body to an existing title-only card', () => {
  const pages = {lookup: {status: 'success', payload: {
    kind: 'success', defaultBodyField: 'extract', pages: [
      {title: 'Brazil', extract: 'Complete introduction', canonicalUrl: 'https://en.wikipedia.org/wiki/Brazil'},
    ],
  }}};
  const config = {items: 'pages', title: 'title', fallback: 'Wikipedia', selection: 'first'};
  const card = JSON.parse(JSON.stringify(runtime.collectionCard(pages, 'lookup', config)));
  assert.deepEqual(card.child.children.map(child => child.type), ['text', 'text', 'https_link']);
  assert.equal(card.child.children[1].text, 'Complete introduction');
  const titleOnly = JSON.parse(JSON.stringify(runtime.collectionCard(pages, 'lookup', {...config, body: null})));
  assert.deepEqual(titleOnly.child.children.map(child => child.type), ['text', 'https_link']);
});

test('collection card works with a nested result path and distinct item fields', () => {
  const data = {
    pages: {status: 'success', payload: {kind: 'success', data: {items: [
      {name: 'Page', summary: 'Summary', link: 'https://en.wikipedia.org/wiki/Page'},
    ]}}},
  };
  const card = JSON.parse(JSON.stringify(seededRuntime.collectionCard(data, 'pages', {
    items: 'data.items', title: 'name', body: 'summary', url: 'link',
    linkLabel: 'Open', fallback: 'No pages',
  })));
  assert.deepEqual(card.child.children.map((node) => node.type), ['text', 'text', 'https_link']);
  assert.equal(card.child.children[2].sourceField, 'data.items.0.link');
  assert.equal(card.child.children[2].label, 'Open');
  assert.throws(() => seededRuntime.collectionCard(data, 'pages', {
    items: 'data.items', title: 'missing', fallback: 'No pages',
  }));
});

test('collection selection supports first and seeded-random without changing legacy behavior', () => {
  const data = {events: {status: 'success', payload: {kind: 'success', events: [
    {title: 'First'}, {title: 'Second'},
  ]}}};
  const config = {items: 'events', title: 'title', fallback: 'None'};
  const title = (selectedRuntime, selection) => selectedRuntime.collectionCard(
    data, 'events', {...config, selection},
  ).child.children[0].text;
  assert.equal(title(runtime, 'first'), 'First');
  assert.equal(title(seededRuntime, 'seeded_random'), 'Second');
  assert.equal(title(seededRuntime), 'Second');
  assert.throws(() => title(runtime, 'seeded_random'));
  assert.throws(() => title(seededRuntime, 'last'));
});

test('three-level array paths retain exact provenance for a selected item', () => {
  const data = {archive: {status: 'success', payload: {kind: 'success', data: {history: {
    events: [{title: 'Past event', link: 'https://en.wikipedia.org/wiki/Past_event'}],
  }}}}};
  const card = runtime.collectionCard(data, 'archive', {
    items: 'data.history.events', title: 'title', url: 'link',
    fallback: 'No events', selection: 'first',
  });
  assert.equal(card.child.children[1].sourceField, 'data.history.events.0.link');
});
