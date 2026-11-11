class AdminEmailBounces extends BasePage {

    #summary = document.getElementById('email-bounces-summary');
    #tbody = document.querySelector('#email-bounces-table tbody');

    constructor() {
        super();
        this.#load();
    }

    #load() {
        this.#summary.innerText = 'Loading...';
        getAndHandle(`${ADMIN_URL_PREFIX}/email-bounces`, json => {
            this.#tbody.replaceChildren();
            json.entries.forEach(entry => {
                const row = this.#tbody.insertRow();
                [
                    entry.email,
                    formatTimestampDefaultDateFormat(entry.creationTime),
                    formatTimestampDefaultDateFormat(entry.updateTime),
                    entry.action ?? '--',
                    entry.status ?? '--',
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
        });
    }
}

window.onload = () => new AdminEmailBounces();
