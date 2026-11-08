'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

test('variant symbols distinguish Janggi, Manchu, and Xiangqi', () => {
    const context = vm.createContext({
        window: {},
        navigator: {userAgent: ''},
        document: {getElementById: () => null},
    });
    for (const name of ['enums', 'ui']) {
        const file = path.resolve(__dirname, `../../../main/resources/public/js/modules/${name}.js`);
        vm.runInContext(fs.readFileSync(file, 'utf8'), context);
    }
    assert.equal(vm.runInContext('formatVariantSymbol(Variant.JANGGI)', context), '장');
    assert.equal(vm.runInContext('formatVariantSymbol(Variant.MANCHU)', context), '统');
    assert.equal(vm.runInContext('formatVariantSymbol(Variant.XIANGQI)', context), '象');
});
