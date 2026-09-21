document.addEventListener("DOMContentLoaded", () => {

    let metricHistory = [];
    let lastSuccess = -1;
    let lastFail = -1;

    async function updateMetrics() {
        try {
            const res = await fetch('/api/metrics');
            if (!res.ok) return;

            const data = await res.json();

            // 1. Only prepend a new row if the global totals have changed
            if (data.successCount !== lastSuccess || data.failCount !== lastFail) {
                const timeString = new Date().toLocaleTimeString();

                metricHistory.unshift({
                    time: timeString,
                    success: data.successCount,
                    fail: data.failCount
                });

                if (metricHistory.length > 100) {
                    metricHistory.pop();
                }

                const metricsTbody = document.querySelector('#metricsTable tbody');
                metricsTbody.innerHTML = metricHistory.map(m =>
                    `<tr>
                        <td style="color: #6b7280; font-family: monospace;">${m.time}</td>
                        <td style="color: #10b981; font-weight: bold;">${m.success}</td>
                        <td style="color: #ef4444; font-weight: bold;">${m.fail}</td>
                    </tr>`
                ).join('');

                lastSuccess = data.successCount;
                lastFail = data.failCount;
            }

            // 2. Render Certificate DN counts
            const reqTbody = document.querySelector('#requesterTable tbody');
            if (data.requesters.length === 0) {
                reqTbody.innerHTML = '<tr><td colspan="2" style="color: #9ca3af; font-style: italic;">No recent requests</td></tr>';
            } else {
                reqTbody.innerHTML = data.requesters.map(req =>
                    `<tr>
                        <td style="word-break: break-all; font-size: 0.9em;">
                            <code style="background: #f1f5f9; padding: 2px 6px; border-radius: 4px;">${req.dn}</code>
                        </td>
                        <td style="font-weight: bold; text-align: center;">${req.count}</td>
                    </tr>`
                ).join('');
            }

        } catch (error) {
            console.error("Failed to fetch live metrics: ", error);
        }
    }

    setInterval(updateMetrics, 2000);
    updateMetrics();
});
