const appDiv = document.getElementById('app');
let isAuthenticated = false; // Simple session state

function renderLogin() {
    appDiv.innerHTML = `
        <h2>System Login</h2>
        <form id="loginForm">
            <input type="text" id="username" placeholder="Username" required />
            <input type="password" id="password" placeholder="Password" required />
            <button type="submit">Login</button>
        </form>
        <p id="error-msg" style="color: red;"></p>
    `;

    document.getElementById('loginForm').addEventListener('submit', async (e) => {
        e.preventDefault();
        const formData = new URLSearchParams();
        formData.append('username', document.getElementById('username').value);
        formData.append('password', document.getElementById('password').value);

        const response = await fetch('/api/login', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            isAuthenticated = true; // Set auth flag
            history.pushState(null, '', '/dashboard');
            renderDashboard();
        } else {
            document.getElementById('error-msg').innerText = 'Authentication Failed';
        }
    });
}


function renderDashboard() {
    // Dynamically resolve the hostname for the CloudBeaver container port
    const cloudBeaverUrl = `${window.location.protocol}//${window.location.hostname}:8978`;

    appDiv.innerHTML = `
        <h2>Dashboard</h2>
        <p>Authentication successful. CloudBeaver Database Interface:</p>
        
        <div style="border: 1px solid #ccc; border-radius: 4px; height: 75vh; margin-top: 10px;">
            <iframe src="${cloudBeaverUrl}" width="100%" height="100%" style="border: none;"></iframe>
        </div>

        <div style="margin-top: 15px; padding: 10px; background: #f4f4f4; font-family: monospace; border-radius: 4px;">
            <strong>CloudBeaver Connection Settings:</strong><br>
            Database Driver: <strong>H2</strong><br>
            JDBC URL: <strong>jdbc:h2:tcp://javalin-spa:9092/mem:authdb</strong><br>
            Username: <strong>sa</strong> (No Password)
        </div>
    `;
}

// function renderDashboard() {
//     // Dynamically resolve the hostname to support local network access
//     const h2ConsoleUrl = `${window.location.protocol}//${window.location.hostname}:8082`;
//
//     appDiv.innerHTML = `
//         <h2>Dashboard</h2>
//         <p>Authentication successful. Live database access granted:</p>
//
//         <div style="border: 1px solid #ccc; border-radius: 4px; height: 70vh; margin-top: 10px;">
//             <iframe src="${h2ConsoleUrl}" width="100%" height="100%" style="border: none;"></iframe>
//         </div>
//
//         <div style="margin-top: 15px; padding: 10px; background: #f4f4f4; font-family: monospace; border-radius: 4px;">
//             <strong>H2 Login Credentials:</strong><br>
//             JDBC URL: jdbc:h2:mem:authdb<br>
//             User Name: sa<br>
//             Password: <em>[Leave Blank]</em>
//         </div>
//     `;
// }

// Client-side router with route protection
window.addEventListener('popstate', route);
function route() {
    if (window.location.pathname === '/dashboard') {
        if (isAuthenticated) {
            renderDashboard();
        } else {
            // Kick unauthenticated users back to login
            history.pushState(null, '', '/');
            renderLogin();
        }
    } else {
        renderLogin();
    }
}

// Initialize
route();