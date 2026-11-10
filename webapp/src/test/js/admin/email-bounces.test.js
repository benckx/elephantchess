'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

async function loadPage(fetch) {
    const elements = Object.fromEntries(
        ['email-bounces-previous', 'email-bounces-next', 'email-bounces-summary'].map(id => [id, {}])
    );
    const tbody = {
        rows: [],
        replaceChildren() { this.rows = []; },
        insertRow() {
            const row = {cells: [], insertCell() { const cell = {}; this.cells.push(cell); return cell; }};
            this.rows.push(row);
            return row;
        },
    };
    const context = vm.createContext({
        BasePage: class {},
        ADMIN_URL_PREFIX: '/api/admin/analytics',
        headers: () => ({Authorization: 'Bearer test'}),
        formatTimestampDefaultDateFormat: time => String(time),
        document: {getElementById: id => elements[id], querySelector: () => tbody},
        window: {},
        console: {error() {}},
        fetch,
    });
    const file = path.resolve(__dirname, '../../../main/resources/public/js/admin/admin-email-bounces.js');
    vm.runInContext(fs.readFileSync(file, 'utf8'), context);
    context.window.onload();
    await new Promise(resolve => setImmediate(resolve));
    return {elements, tbody};
}

test('renders diagnostic as text, handles legacy metadata and paginates with auth headers', async () => {
    const requests = [];
    const {elements, tbody} = await loadPage(async (url, options) => {
        requests.push({url, options});
        return {
            ok: true,
            json: async () => ({
                entries: [{
                    email: 'recipient@example.org', bouncedTime: 123, action: null, status: null,
                    diagnostic: '<img src=x onerror=alert(1)>', suppressed: true,
                }],
                hasMore: requests.length === 1,
            }),
        };
    });
    assert.equal(tbody.rows[0].cells[5].innerText, '<img src=x onerror=alert(1)>');
    assert.equal(tbody.rows[0].cells[5].innerHTML, undefined);
    assert.equal(tbody.rows[0].cells[3].innerText, 'unknown (legacy)');
    assert.equal(tbody.rows[0].cells[4].innerText, 'yes');
    assert.equal(elements['email-bounces-previous'].disabled, true);
    assert.equal(elements['email-bounces-next'].disabled, false);
    await elements['email-bounces-next'].onclick();
    assert.equal(requests[1].url, '/api/admin/analytics/email-bounces?offset=100');
    assert.equal(requests[1].options.headers.Authorization, 'Bearer test');
    assert.equal(elements['email-bounces-previous'].disabled, false);
    assert.equal(elements['email-bounces-next'].disabled, true);
});

test('failed fetch is displayed explicitly rather than as an empty result', async () => {
    const {elements} = await loadPage(async () => ({ok: false, status: 403}));
    assert.match(elements['email-bounces-summary'].innerText, /Unable to load/);
    assert.equal(elements['email-bounces-next'].disabled, true);
});
