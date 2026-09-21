document.addEventListener("DOMContentLoaded", () => {

    // Maintain a local history of table rows
    let metricHistory = [];

    async function updateMetrics() {
        try {
            const res = await fetch('/api/metrics');
            if (!res.ok) return;

            const data = await res.json();
            const timeString = new Date().toLocaleTimeString();

            // 1. Update Metrics History (Prepend newest to top)
            metricHistory.unshift({
                time: timeString,
                success: data.successCount,
                fail: data.failCount
            });

            // Keep array capped to prevent infinite browser memory usage
            if (metricHistory.length > 100) {
                metricHistory.pop();
            }

            // Render Metrics Table
            const metricsTbody = document.querySelector('#metricsTable tbody');
            metricsTbody.innerHTML = metricHistory.map(m =>
                `<tr>
                    <td style="color: #6b7280; font-family: monospace;">${m.time}</td>
                    <td style="color: #10b981; font-weight: bold;">${m.success}</td>
                    <td style="color: #ef4444; font-weight: bold;">${m.fail}</td>
                </tr>`
            ).join('');

            // 2. Update Requester Table
            const reqTbody = document.querySelector('#requesterTable tbody');
            if (data.requesters.length === 0) {
                reqTbody.innerHTML = '<tr><td style="color: #9ca3af; font-style: italic;">No recent requests</td></tr>';
            } else {
                reqTbody.innerHTML = data.requesters.map(req =>
                    `<tr><td><code style="background: #f1f5f9; padding: 2px 6px; border-radius: 4px;">${req}</code></td></tr>`
                ).join('');
            }

        } catch (error) {
            console.error("Failed to fetch live metrics: ", error);
        }
    }

    // Begin polling loop every 2 seconds
    setInterval(updateMetrics, 2000);
    updateMetrics();
});
