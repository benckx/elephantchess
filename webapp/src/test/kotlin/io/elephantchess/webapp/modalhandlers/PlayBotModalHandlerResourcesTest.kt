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

package io.elephantchess.webapp.modalhandlers

import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

class PlayBotModalHandlerResourcesTest {

    @Test
    fun `manchu opening mode lock wiring is present in modal and handler resources`() {
        val modalHtml = this::class.java.classLoader.getResource("modals/play-bot.html")!!.readText()
        val modalHandler = this::class.java.classLoader.getResource("public/js/modal-handlers/play-bot-modal-handler.js")!!
            .readText()

        assertTrue(modalHtml.contains("class=\"standard-radio play-bot-opening-radio-option\""))
        assertTrue(modalHandler.contains("#openingRadioOptions = getElementsByClassNameArray('play-bot-opening-radio-option');"))
        assertTrue(modalHandler.contains("this.#openingRadios[i].disabled = isManchu;"))
        assertTrue(modalHandler.contains("this.#openingEngineOnlyRadio.checked = true;"))
    }
}
