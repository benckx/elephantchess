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

class TestBoardPage extends BasePage {

    constructor() {
        super();
        const boardGui = createWebappBoardGui({variant: Variant.JANGGI});
        boardGui.loadFen(JANGGI_START_FEN);

        const moveTreeWidget = new MoveTreeWidget({
            containerId: 'move-tree-container',
            variant: Variant.JANGGI,
            ...moveTreeResizeCookiePersistence('simple-board', 'move-tree-container')
        });
        moveTreeWidget.addNavigationPanel({
            containerId: 'mobile-navigation-panel',
            isDownloadButtonEnabled: true
        });
        moveTreeWidget.addNavigationPanel({
            containerId: 'move-history-navigation-panel',
            isDownloadButtonEnabled: true
        });
        moveTreeWidget.startFen = JANGGI_START_FEN;
        moveTreeWidget.boardWidget = boardGui;

        boardGui.addAfterMoveListener((move) => {
            moveTreeWidget.addMoveAtTheEnd(move);
        });

        new SettingsGui(boardGui, moveTreeWidget);

        // TODO: just to test
        const moves = "h0g2,h9g7,i3h3,e8e9,i0i4,b9c7,e1e0,b7e7,b0c2,e7a7,b2e2,d9e8,e3d3,g6g5,a0b0,a6b6,c3c4,i6i5,i4i2,h7e7,c2e3,a7d7,e3d5,e7e5,d5c7,d7i7,c7e8,f9e8,h3i3,g5f5,g3h3,i5h5,g2h0,i7i2,h0i2,i9i6,d0e1,a9a5,e0d0,i6g6,h2h5,g6g0,h5h1,a5b5,b0b5,b6b5,i2g3,g0g2,h1c1,g9i6,g3e4,g2g4,d3d4,b5b4,e4f2,g4g5,d4e4,e5e7,c4d4,f5f4,c1f1,f4e4,d4e4,g5c5,e2e0,c5c3,f1d1,e7i7,e1d2,c9a6,e4d4,i6f4,d1d3,a6d4,d2d1,i7d7,f0e1,d4a2";
        moves.split(",").forEach(move => boardGui.registerMoveIfLegal(HalfMove.parseUci(move)));
    }

}

window.onload = () => new TestBoardPage();
