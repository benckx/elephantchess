class AdminEmailBounces extends BasePage {

    #summary = document.getElementById('email-bounces-summary');
    #tbody = document.querySelector('#email-bounces-table tbody');

    constructor() {
        super();
        this.#load();
    }

    async #load() {
        this.#summary.innerText = 'Loading...';
        try {
            const response = await fetch(`${ADMIN_URL_PREFIX}/email-bounces`, {
                method: 'GET',
                headers: headers(),
            });
            if (!response.ok) {
                throw new Error(`Unable to load email bounces (${response.status})`);
            }
            const json = await response.json();
            this.#tbody.replaceChildren();
            json.entries.forEach(entry => {
                const row = this.#tbody.insertRow();
                [
                    entry.email,
                    formatTimestampDefaultDateFormat(entry.bouncedTime),
                    entry.action ?? '--',
                    entry.status ?? 'unknown (legacy)',
                    entry.suppressed ? 'yes' : 'no',
                    entry.diagnostic ?? '--',
                ].forEach(value => {
                    const cell = row.insertCell();
                    cell.className = 'label-cell';
                    cell.innerText = value;
                });
            });
            this.#summary.innerText = json.entries.length === 0
                ? 'No bounce records.'
                : `Showing ${json.entries.length} bounce record${json.entries.length === 1 ? '' : 's'}.`;
        } catch (error) {
            console.error('Error loading email bounces', error);
            this.#summary.innerText = 'Unable to load email bounces. Reload the page to retry.';
        }
    }
}

window.onload = () => new AdminEmailBounces();
