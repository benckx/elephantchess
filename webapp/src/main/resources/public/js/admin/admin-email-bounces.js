class AdminEmailBounces extends BasePage {

    #offset = 0;
    #pageSize = 100;
    #previous = document.getElementById('email-bounces-previous');
    #next = document.getElementById('email-bounces-next');
    #summary = document.getElementById('email-bounces-summary');
    #tbody = document.querySelector('#email-bounces-table tbody');

    constructor() {
        super();
        this.#previous.onclick = () => this.#load(this.#offset - this.#pageSize);
        this.#next.onclick = () => this.#load(this.#offset + this.#pageSize);
        this.#load(0);
    }

    async #load(offset) {
        this.#previous.disabled = true;
        this.#next.disabled = true;
        this.#summary.innerText = 'Loading...';
        try {
            const response = await fetch(`${ADMIN_URL_PREFIX}/email-bounces?offset=${offset}`, {
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
            this.#offset = offset;
            this.#summary.innerText = json.entries.length === 0
                ? 'No bounce records.'
                : `Showing ${offset + 1}-${offset + json.entries.length}`;
            this.#next.disabled = !json.hasMore;
        } catch (error) {
            console.error('Error loading email bounces', error);
            this.#summary.innerText = 'Unable to load email bounces. Reload the page to retry.';
        } finally {
            this.#previous.disabled = this.#offset === 0;
        }
    }
}

window.onload = () => new AdminEmailBounces();
