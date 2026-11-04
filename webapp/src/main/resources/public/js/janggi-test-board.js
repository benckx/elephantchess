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
        const moves = "h1g3,h10g8,i4h4,e9e10,i1i5,b10c8,e2e1,b8e8,b1c3,e8a8,b3e3,d10e9,e4d4,g7g6,a1b1,a7b7,c4c5,i7i6,i5i3,h8e8,c3e4,a8d8,e4d6,e8e6,d6c8,d8i8,c8e9,f10e9,h4i4,g6f6,g4h4,i6h6,g3h1,i8i3,h1i3,i10i7,d1e2,a10a6,e1d1,i7g7,h3h6,g7g1,h6h2,a6b6,b1b6,b7b6,i3g4,g1g3,h2c2,g10i7,g4e5,g3g5,d4d5,b6b5,e5f3,g5g6,d5e5,e6e8,c5d5,f6f5,c2f2,f5e5,d5e5,g6c6,e3e1,c6c4,f2d2,e8i8,e2d3,c10a7,e5d5,i7f5,d2d4,a7d5,d3d2,i8d8,f1e2,d5a3";
        moves.split(",").forEach(move => boardGui.registerMoveIfLegal(HalfMove.parseUci(move)));
    }

}

window.onload = () => new TestBoardPage();
