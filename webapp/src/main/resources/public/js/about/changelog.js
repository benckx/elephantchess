/*
 * Copyright (C) 2026  Encelade SRL
 * Copyright (C) 2026  elephantchess.io
 * Copyright (C) 2026  Benoît Vleminckx (benckx)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

class ChangelogPage extends BasePage {

    constructor() {
        super();
    }

}

// Use event delegation on the document so the binding cannot be lost if
// `.anchor-copy-link` nodes are added later or if `BasePage`'s constructor
// throws before the per-element listeners would have been attached.
document.addEventListener('click', (event) => {
    const link = event.target.closest('.anchor-copy-link');
    if (!link) return;

    event.preventDefault();

    const target = link.dataset.anchorTarget;
    if (!target) return;

    const url = `${getFullHost()}${window.location.pathname}#${target}`;
    copyTextToClipboardAndNotify(url, 'Link copied to clipboard!');
});

window.onload = () => new ChangelogPage();
