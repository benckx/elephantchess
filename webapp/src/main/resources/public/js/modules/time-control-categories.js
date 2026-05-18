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

const TimeControlMode = Object.freeze({
    GAME_TIME: 'GAME_TIME',
    MOVE_TIME: 'MOVE_TIME'
});

const TimeControlEnum = Object.freeze({
    BULLET: 'BULLET',
    BLITZ: 'BLITZ',
    RAPID: 'RAPID',
    CLASSICAL: 'CLASSICAL',
    SEVERAL_DAYS: 'SEVERAL_DAYS',
    CORRESPONDENCE: 'CORRESPONDENCE'
});

/**
 * @type {Map<string, string>}
 */
const timeControlCategoryIconMap = new Map();
timeControlCategoryIconMap.set(TimeControlEnum.BULLET, 'shuttle.png');
timeControlCategoryIconMap.set(TimeControlEnum.BLITZ, 'flash-squared.png');
timeControlCategoryIconMap.set(TimeControlEnum.RAPID, 'run.png');
timeControlCategoryIconMap.set(TimeControlEnum.CLASSICAL, 'museum.png');
timeControlCategoryIconMap.set(TimeControlEnum.SEVERAL_DAYS, 'calendar_page.png');
timeControlCategoryIconMap.set(TimeControlEnum.CORRESPONDENCE, 'email.png');

class TimeControlDuration {

    /**
     * @type {number}
     */
    #days;

    /**
     * @type {number}
     */
    #hours;

    /**
     * @type {number}
     */
    #minutes;

    /**
     * @type {number}
     */
    #seconds;

    constructor(days, hours, minutes, seconds) {
        if (days < 0 || hours < 0 || minutes < 0 || seconds < 0) {
            throw new Error("Invalid duration");
        }

        this.#days = days;
        this.#hours = hours;
        this.#minutes = minutes;
        this.#seconds = seconds;
    }

    get days() {
        return this.#days;
    }

    get hours() {
        return this.#hours;
    }

    get minutes() {
        return this.#minutes;
    }

    get seconds() {
        return this.#seconds;
    }

    /**
     * @return {number}
     */
    toMillis() {
        return this.toSeconds() * 1_000;
    }

    /**
     * @return {number}
     */
    toSeconds() {
        return Number(this.#seconds) + Number(this.#minutes * 60) + Number(this.#hours * 3_600) + Number(this.#days * 86_400);
    }

    static fromMillis(millis) {
        return TimeControlDuration.fromSeconds(Math.floor(millis / 1_000));
    }

    static fromSeconds(secondsParam) {
        let seconds = secondsParam;
        if (seconds < 0) {
            seconds = 0;
        }
        let days = Math.floor(seconds / 86_400);
        seconds -= days * 86_400;
        let hours = Math.floor(seconds / 3_600);
        seconds -= hours * 3_600;
        let minutes = Math.floor(seconds / 60);
        seconds -= minutes * 60;
        return new TimeControlDuration(
            days.toFixed(0),
            hours.toFixed(0),
            minutes.toFixed(0),
            seconds.toFixed(0)
        );
    }

    toLiteral() {
        return {
            days: this.#days,
            hours: this.#hours,
            minutes: this.#minutes,
            seconds: this.#seconds
        }
    }

    printShort() {
        let bits = [];
        if (this.#days > 0) {
            bits.push(`${this.#days}d`);
        }
        if (this.#hours > 0) {
            bits.push(`${this.#hours}h`);
        }
        if (this.#minutes > 0) {
            bits.push(`${this.#minutes}m`);
        }
        if (this.#seconds > 0) {
            bits.push(`${this.#seconds}s`);
        }

        return bits.join(' ');
    }

    /**
     * @param category {string}
     * @return {string}
     */
    printCounter(category) {
        let days = this.days.toString().padStart(2, '0');
        let hours = this.hours.toString().padStart(2, '0');
        let minutes = this.minutes.toString().padStart(2, '0');
        let seconds = this.seconds.toString().padStart(2, '0');

        switch (category) {
            case TimeControlEnum.BULLET:
            case TimeControlEnum.BLITZ:
            case TimeControlEnum.RAPID:
                return minutes + ':' + seconds;
            case TimeControlEnum.CLASSICAL:
                return hours + ':' + minutes + ':' + seconds;
            case TimeControlEnum.SEVERAL_DAYS:
            case TimeControlEnum.CORRESPONDENCE:
                return days + ':' + hours + ':' + minutes + ':' + seconds;
            default:
                throw new Error(`Unknown category: ${category}`);
        }
    }

    toString() {
        return this.printShort();
    }

    static ofSeconds(seconds) {
        return new TimeControlDuration(0, 0, 0, seconds);
    }

    static ofMinutes(minutes) {
        return new TimeControlDuration(0, 0, minutes, 0);
    }

    static ofHours(hours) {
        return new TimeControlDuration(0, hours, 0, 0);
    }

    static ofDays(days) {
        return new TimeControlDuration(days, 0, 0, 0);
    }

}

class TimeControl {

    /**
     * @type {TimeControlDuration}
     */
    #base;

    /**
     * @type {TimeControlDuration|null}
     */
    #increment

    /**
     * @param base {TimeControlDuration}
     * @param increment {TimeControlDuration|null}
     */
    constructor(base, increment = null) {
        if (increment != null) {
            if (base.toSeconds() < increment.toSeconds()) {
                throw new Error("Base time can not be smaller increment");
            }
        }

        this.#base = base;
        this.#increment = increment;
    }

    /**
     * @return {TimeControlDuration}
     */
    get base() {
        return this.#base;
    }

    /**
     * @return {TimeControlDuration|null}
     */
    get increment() {
        return this.#increment;
    }

    /**
     * @return {string}
     */
    get id() {
        let id = this.#base.toSeconds().toString();
        if (this.#increment != null) {
            id += `-${this.#increment.toSeconds()}`;
        }

        return id;
    }

    printShort(separator = '/') {
        if (this.#increment != null) {
            return `${this.#base.printShort()}${separator}${this.#increment.printShort()}`;
        } else {
            return this.#base.printShort();
        }
    }

    printPgnFormat() {
        let result = this.#base.toSeconds().toString();
        if (this.#increment != null) {
            result += `+${this.#increment.toSeconds().toString()}`;
        }
        return result;
    }

    /**
     * @param id {string} element id with format "tc-1800-60" or "tc-1800"
     * @return {TimeControl}
     */
    static fromTcFormatId(id) {
        let split = id.split('-');
        switch (split.length) {
            case 2:
                return new TimeControl(TimeControlDuration.fromSeconds(parseInt(split[1])), null);
            case 3:
                return new TimeControl(TimeControlDuration.fromSeconds(parseInt(split[1])), TimeControlDuration.fromSeconds(parseInt(split[2])));
            default:
                throw new Error(`Invalid time control format: ${id}`);
        }
    }

    static fromJson(json) {
        if (json.timeControlBase != null) {
            let base = TimeControlDuration.fromSeconds(json.timeControlBase);
            let increment = null;
            if (json.timeControlIncrement != null && json.timeControlIncrement > 0) {
                increment = TimeControlDuration.fromSeconds(json.timeControlIncrement);
            }
            return new TimeControl(base, increment);
        } else {
            throw new Error("No time control found in json");
        }
    }

}

class TimeControlCategory {

    /**
     * @type {string}
     */
    #name;

    /**
     * @type {TimeControl[]}
     */
    #timeControls;

    constructor(name, timeControls) {
        this.#name = name;
        this.#timeControls = timeControls;
    }

    /**
     * @return {string}
     */
    get name() {
        return this.#name;
    }

    /**
     * @return {TimeControl[]}
     */
    get timeControls() {
        return this.#timeControls;
    }

}

/***
 * @type {TimeControlCategory[]}
 */
const timeControlCategories = [];

timeControlCategories.push(
    new TimeControlCategory('Bullet', [
        new TimeControl(TimeControlDuration.ofMinutes(1)),
        new TimeControl(TimeControlDuration.ofMinutes(1), TimeControlDuration.ofSeconds(1)),
        new TimeControl(TimeControlDuration.ofMinutes(2), TimeControlDuration.ofSeconds(1))
    ])
);

timeControlCategories.push(
    new TimeControlCategory('Blitz', [
        new TimeControl(TimeControlDuration.ofMinutes(3)),
        new TimeControl(TimeControlDuration.ofMinutes(3), TimeControlDuration.ofSeconds(2)),
        new TimeControl(TimeControlDuration.ofMinutes(5))
    ])
);

timeControlCategories.push(
    new TimeControlCategory('Rapid', [
        new TimeControl(TimeControlDuration.ofMinutes(10)),
        new TimeControl(TimeControlDuration.ofMinutes(15)),
        new TimeControl(TimeControlDuration.ofMinutes(15), TimeControlDuration.ofSeconds(10)),
        new TimeControl(TimeControlDuration.ofMinutes(30))
    ])
);

timeControlCategories.push(
    new TimeControlCategory('Classical', [
        new TimeControl(TimeControlDuration.ofHours(1)),
        new TimeControl(TimeControlDuration.ofHours(1), TimeControlDuration.ofMinutes(1)),
        new TimeControl(TimeControlDuration.ofHours(2)),
    ])
);

timeControlCategories.push(
    new TimeControlCategory('Several days', [
        new TimeControl(TimeControlDuration.ofDays(1)),
        new TimeControl(TimeControlDuration.ofDays(2)),
        new TimeControl(TimeControlDuration.ofDays(3)),
        new TimeControl(TimeControlDuration.ofDays(7)),
    ])
);
