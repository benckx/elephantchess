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

class PlayedPuzzlesPage extends InfiniteScrollPage {

    /**
     * @type {HTMLTableElement}
     */
    #itemsDiv = document.getElementById('my-game-items');

    /**
     * @type {HTMLDivElement}
     */
    #noPuzzlePlayedMessage = document.getElementById('no-puzzle-played-message');

    constructor() {
        super();
        this.fetchItems();
    }

    baseUrl() {
        return '/api/puzzle/list-played-puzzles';
    }

    deserializeJsonEntry(jsonEntry) {
        return new PlayedPuzzleDto(jsonEntry);
    }

    /**
     * @param entry {PlayedPuzzleDto}
     */
    extractToken(entry) {
        return entry.date.toString();
    }

    showNoItem(value) {
        this.#noPuzzlePlayedMessage.style.display = value ? 'block' : 'none';
    }

    /**
     * @param entries {PlayedPuzzleDto[]}
     */
    addEntries(entries) {
        function buildPuzzleIcon() {
            const puzzleImg = document.createElement('img');
            puzzleImg.className = 'icon';
            puzzleImg.src = '/images/icons/puzzle-piece.png';
            puzzleImg.alt = 'Puzzle';
            return wrapInDiv(puzzleImg);
        }

        /**
         * @param entry {PlayedPuzzleDto}
         * @returns {HTMLDivElement}
         */
        function buildRatingDiv(entry) {
            // rating
            const ratingSpan = document.createElement('span');
            ratingSpan.className = 'rating-from';
            ratingSpan.innerText = entry.ratingFrom.toString();

            const deltaSpan = document.createElement('span');
            deltaSpan.classList.add('user-rating-delta-value-box', 'user-rating-delta-value-smaller');
            if (entry.ratingDelta > 0) {
                deltaSpan.innerHTML = HTML_WHITE_SPACE + '+' + entry.ratingDelta;
                deltaSpan.classList.add('user-rating-delta-value-box-positive');
            } else if (entry.ratingDelta < 0) {
                deltaSpan.innerHTML = HTML_WHITE_SPACE + entry.ratingDelta.toString();
                deltaSpan.classList.add('user-rating-delta-value-box-negative');
            } else {
                deltaSpan.innerHTML = HTML_WHITE_SPACE + 'n/a';
            }

            const div = document.createElement('div');
            div.append(ratingSpan, deltaSpan);
            return div;
        }

        /**
         * @param entry {PlayedPuzzleDto}
         * @returns {HTMLDivElement}
         */
        function buildOutcomeDiv(entry) {
            const outcomeLabelHolder = buildDivWithClass('outcome-label-holder');

            let text;
            switch (entry.outcome) {
                case PuzzleOutcome.SOLVED:
                    text = 'solved';
                    outcomeLabelHolder.classList.add('outcome-label-win');
                    break;
                case PuzzleOutcome.FAILED:
                    text = 'failed';
                    outcomeLabelHolder.classList.add('outcome-label-loss');
                    break;
                case PuzzleOutcome.SKIPPED:
                    text = 'skipped';
                    break;
                default:
                    text = '??';
                    break;
            }

            outcomeLabelHolder.append(buildSimpleTextDiv(text));
            return outcomeLabelHolder;
        }

        entries.forEach(entry => {
            // structure
            const leftPane = buildDivWithClass('left-pane');
            const middlePane = buildDivWithClass('middle-pane');
            const outcomeIndicatorPane = buildDivWithClass('indicator-pane');
            const ratingDeltaIndicatorPane = buildDivWithClass('indicator-pane');
            const rightPane = buildDivWithClass('right-pane');

            const item = document.createElement('a');
            item.className = 'my-game-item';
            item.setAttribute('href', entry.idUrl);

            item.append(
                leftPane,
                middlePane,
                ratingDeltaIndicatorPane,
                outcomeIndicatorPane,
                rightPane
            );

            this.#itemsDiv.append(item);
            addMiniboardDiv(item, `${entry.puzzleId}-${entry.date}`, entry.startFen, entry.playerColor);

            // left pane
            leftPane.append(buildPuzzleIcon());

            // middle pane
            const puzzleMetadata = document.createElement('a');
            puzzleMetadata.classList.add('puzzle-metadata', `puzzle-metadata-${entry.puzzleId}`);
            puzzleMetadata.innerText = 'Loading...';
            puzzleMetadata.setAttribute('href', '#');

            const categoryDiv = document.createElement('div');
            categoryDiv.className = 'puzzle-categories';
            categoryDiv.append(buildLink(entry.categoriesUrl, entry.formattedCategories.join(', ')));

            middlePane.append(
                puzzleMetadata,
                wrapInDiv(buildColorSpan(entry.playerColor)),
                categoryDiv,
            );

            // indicator panes
            ratingDeltaIndicatorPane.append(buildRatingDiv(entry));
            outcomeIndicatorPane.append(buildOutcomeDiv(entry));

            // right pane
            const dateDiv = document.createElement('div');
            dateDiv.className = 'last-modified';
            dateDiv.id = `last-modified-${entry.puzzleId}-${entry.date}`;
            setRelativeTimeAndToolTip(dateDiv, entry.date);

            rightPane.append(dateDiv);
        });

        this.#updateOriginalGameMetadata(entries);
    }

    /**
     * Fetches original game metadata for the given puzzle ids and updates the table accordingly.
     *
     * @param entries {PlayedPuzzleDto[]}
     */
    #updateOriginalGameMetadata(entries) {
        const puzzleIds = entries.map(entry => entry.puzzleId).filter(onlyUnique);
        this.#fetchOriginalGamesMetadata(puzzleIds, puzzleIdsToMetadata => {
            for (const puzzleId of puzzleIds) {
                getElementsByClassNameArray(`puzzle-metadata-${puzzleId}`).forEach(metadataDiv => {
                    const metadata = puzzleIdsToMetadata.get(puzzleId);
                    if (metadata != null) {
                        const gameMetadata = metadata.gameMetadata;
                        let color = null;
                        switch (gameMetadata.outcome) {
                            case Outcome.RED_WINS:
                                color = Color.RED;
                                break;
                            case Outcome.BLACK_WINS:
                                color = Color.BLACK;
                                break;
                            case Outcome.DRAW:
                                color = Color.RED;
                                break;
                        }

                        let link = `${gameIdToPageLink(gameMetadata.gameId)}`;
                        if (color != null) {
                            link += `&orientation=${color.toUpperCase()}`;
                        }

                        let text = gameMetadata.toStringPlayerNames();
                        if (metadata.puzzleRating != null) {
                            text += ` (${metadata.puzzleRating})`;
                        }
                        metadataDiv.innerText = text;
                        metadataDiv.setAttribute('href', link);
                    } else {
                        metadataDiv.innerText = 'No original game found';
                    }
                });
            }
        });
    }

    /**
     * @param puzzleIds {string[]}
     * @param cb {function(Map<string, {gameMetadata: GameMetadataDto, puzzleRating: number|null}>)}
     */
    #fetchOriginalGamesMetadata(puzzleIds, cb) {
        postAndHandle('/api/puzzle/original-games-metadata', {puzzleIds: puzzleIds}, json => {
            const puzzleIdsToMetadata = new Map();
            json.entries.forEach(entry => {
                puzzleIdsToMetadata.set(
                    entry.puzzleId,
                    {
                        gameMetadata: new GameMetadataDto(entry.gameMetadata),
                        puzzleRating: entry.puzzleRating,
                    }
                );
            });

            cb(puzzleIdsToMetadata);
        });
    }

}

window.onload = () => new PlayedPuzzlesPage();
