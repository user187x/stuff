document.addEventListener("DOMContentLoaded", () => {

    // --- Chart Initialization with Time Scale ---
    const ctx = document.getElementById('liveChart').getContext('2d');
    const metricChart = new Chart(ctx, {
        type: 'line',
        data: {
            datasets: [
                {
                    label: 'Successful Auth Validations',
                    data: [], // Points pushed as {x: timestamp, y: value}
                    borderColor: '#10b981',
                    backgroundColor: 'rgba(16, 185, 129, 0.1)',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.3
                },
                {
                    label: 'Failed Validations',
                    data: [],
                    borderColor: '#ef4444',
                    backgroundColor: 'rgba(239, 68, 68, 0.1)',
                    borderWidth: 2,
                    fill: true,
                    tension: 0.3
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            animation: false,
            scales: {
                x: {
                    type: 'time',
                    time: {
                        unit: 'second',
                        tooltipFormat: 'PPpp', // Date-fns format
                        displayFormats: {
                            second: 'HH:mm:ss'
                        }
                    },
                    title: {
                        display: true,
                        text: 'Time'
                    }
                },
                y: {
                    beginAtZero: true,
                    ticks: {
                        precision: 0 // Only show whole numbers for metric counts
                    }
                }
            }
        }
    });

    // --- Metric Polling Logic ---
    async function updateMetrics() {
        try {
            const res = await fetch('/api/metrics');
            if (!res.ok) return;

            const data = await res.json();
            const now = new Date();

            // 1. Push intelligent (x,y) coordinates to the graph
            metricChart.data.datasets[0].data.push({ x: now, y: data.successCount });
            metricChart.data.datasets[1].data.push({ x: now, y: data.failCount });

            // Truncate data older than 60 seconds based on actual timestamps, not array length
            const cutoffTime = now.getTime() - 60000;
            metricChart.data.datasets.forEach(dataset => {
                while(dataset.data.length > 0 && dataset.data[0].x.getTime() < cutoffTime) {
                    dataset.data.shift();
                }
            });
            metricChart.update();

            // 2. Update Requester Table
            const tbody = document.querySelector('#requesterTable tbody');
            if (data.requesters.length === 0) {
                tbody.innerHTML = '<tr><td style="color: #9ca3af; font-style: italic;">No recent requests</td></tr>';
            } else {
                tbody.innerHTML = data.requesters.map(req =>
                    `<tr><td><code style="background: #f1f5f9; padding: 2px 6px; border-radius: 4px;">${req}</code></td></tr>`
                ).join('');
            }

        } catch (error) {
            console.error("Failed to fetch live metrics: ", error);
        }
    }

    // Begin loop
    setInterval(updateMetrics, 2000);
    updateMetrics();
});
