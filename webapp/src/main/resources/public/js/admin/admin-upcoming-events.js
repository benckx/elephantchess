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

const DATE_FORMAT = 'yyyy-mm-dd';

class AdminUpcomingEventsPage extends BasePage {

    #eventsTable = document.getElementById('upcoming-events-table');
    #createForm = document.getElementById('create-event-form');
    #submitButton = document.getElementById('submit-event-btn');
    #startDateInput = document.getElementById('event-start');
    #endDateInput = document.getElementById('event-end');
    #descriptionInput = document.getElementById('event-description');
    #linkInput = document.getElementById('event-link');

    #startDatePicker;
    #endDatePicker;

    constructor() {
        super();
        this.#initDatePickers();
        this.#fetchUpcomingEvents();
        this.#setupFormHandler();
    }

    #initDatePickers() {
        this.#startDatePicker = new Datepicker(this.#startDateInput, {format: DATE_FORMAT});
        this.#endDatePicker = new Datepicker(this.#endDateInput, {format: DATE_FORMAT});
    }

    #fetchUpcomingEvents() {
        getAndHandle(`${ADMIN_URL_PREFIX}/upcoming-events`, json => {
            this.#renderEvents(json.events || []);
        });
    }

    #renderEvents(events) {
        const tbody = this.#eventsTable.getElementsByTagName('tbody')[0];
        emptyTable(this.#eventsTable);

        events.forEach(event => {
            const row = tbody.insertRow();
            if (!event.isEnabled) {
                row.classList.add('disabled-row');
            }

            // ID
            const idCell = row.insertCell();
            idCell.className = 'value-cell';
            idCell.innerText = event.id;

            // Enabled
            const enabledCell = row.insertCell();
            enabledCell.className = 'label-cell';
            enabledCell.innerText = event.isEnabled ? 'yes' : 'no';

            // Start date
            const startCell = row.insertCell();
            startCell.className = 'label-cell';
            startCell.innerText = event.startDate;

            // End date
            const endCell = row.insertCell();
            endCell.className = 'label-cell';
            endCell.innerText = event.endDate;

            // Description
            const descCell = row.insertCell();
            descCell.className = 'long-label-cell';
            descCell.innerText = event.description;
            descCell.title = event.description;

            // Link
            const linkCell = row.insertCell();
            linkCell.className = 'long-label-cell';
            linkCell.appendChild(buildLink(event.link, event.link, '_blank'));
            linkCell.title = event.link;

            // Created At
            const createdAtCell = row.insertCell();
            createdAtCell.className = 'label-cell';
            if (event.createdAt != null) {
                createdAtCell.innerText = formatTimestampDefaultDateFormat(event.createdAt);
            } else {
                createdAtCell.innerText = '--';
            }

            // Created By
            const createdByCell = row.insertCell();
            createdByCell.className = 'label-cell';
            if (event.createdByUsername != null) {
                createdByCell.append(buildUserLinkAnchor(event.createdByUsername))
            } else {
                createdByCell.innerText = '--';
            }

            // Actions
            const actionsCell = row.insertCell();
            actionsCell.className = 'label-cell';

            const toggleLink = document.createElement('a');
            toggleLink.href = '#';
            toggleLink.innerText = event.isEnabled ? 'disable' : 'enable';
            toggleLink.addEventListener('click', (e) => {
                e.preventDefault();
                this.#toggleEvent(event.id, !event.isEnabled);
            });

            const editLink = document.createElement('a');
            editLink.href = '#';
            editLink.innerText = 'edit';
            editLink.addEventListener('click', (e) => {
                e.preventDefault();
                this.#editEvent(event);
            });

            actionsCell.appendChild(toggleLink);
            actionsCell.appendChild(document.createTextNode(' | '));
            actionsCell.appendChild(editLink);
        });
    }

    #toggleEvent(id, enabled) {
        postAndHandle(`${ADMIN_URL_PREFIX}/upcoming-events/toggle`, {id, enabled}, () => {
            UI.pushInfoNotification(enabled ? 'Event enabled' : 'Event disabled');
            this.#fetchUpcomingEvents();
        });
    }

    #editEvent(event) {
        // Populate form with event data
        this.#startDateInput.value = event.startDate;
        this.#endDateInput.value = event.endDate;
        this.#descriptionInput.value = event.description;
        this.#linkInput.value = event.link;

        // Update datepickers
        this.#startDatePicker.setDate(event.startDate);
        this.#endDatePicker.setDate(event.endDate);

        // Store the event id being edited
        this.#editingEventId = event.id;

        // Change button text
        this.#submitButton.value = 'Update Event';

        // Scroll to form
        this.#createForm.scrollIntoView({behavior: 'smooth'});
    }

    #editingEventId = null;

    #setupFormHandler() {
        this.#submitButton.addEventListener('click', (e) => {
            e.preventDefault();

            const startDate = this.#startDatePicker.getDate(DATE_FORMAT);
            const endDate = this.#endDatePicker.getDate(DATE_FORMAT);
            const description = this.#descriptionInput.value.trim();
            const link = this.#linkInput.value.trim();

            // Validate dates are not empty
            if (!startDate || !endDate) {
                UI.pushErrorNotification('Start date and end date are required');
                return;
            }

            // Validate dates
            if (startDate > endDate) {
                UI.pushErrorNotification('Start date must be before or equal to end date');
                return;
            }

            // Validate URL
            if (!this.#isValidUrl(link)) {
                UI.pushErrorNotification('Link must be a valid URL');
                return;
            }

            // Validate description is not empty
            if (!description) {
                UI.pushErrorNotification('Description cannot be empty');
                return;
            }

            const isEditing = this.#editingEventId !== null;

            const onSuccess = () => {
                UI.pushInfoNotification(isEditing ? 'Event updated successfully' : 'Event created successfully');
                this.#resetForm();
                this.#fetchUpcomingEvents();
            };

            if (isEditing) {
                const payload = {id: this.#editingEventId, startDate, endDate, description, link};
                putAndHandle(`${ADMIN_URL_PREFIX}/upcoming-events`, payload, onSuccess);
            } else {
                const payload = {startDate, endDate, description, link};
                postAndHandle(`${ADMIN_URL_PREFIX}/upcoming-events`, payload, onSuccess);
            }
        });
    }

    #resetForm() {
        this.#createForm.reset();
        this.#startDatePicker.setDate({clear: true});
        this.#endDatePicker.setDate({clear: true});
        this.#editingEventId = null;
        this.#submitButton.value = 'Create Event';
    }

    #isValidUrl(string) {
        try {
            const url = new URL(string);
            return url.protocol === 'http:' || url.protocol === 'https:';
        } catch (_) {
            return false;
        }
    }
}

window.onload = () => new AdminUpcomingEventsPage();
