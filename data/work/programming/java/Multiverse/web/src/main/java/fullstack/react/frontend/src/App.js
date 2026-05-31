import React, { useState, useEffect } from 'react';
import './App.css';

function App() {
    // State variables
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [loggedInUser, setLoggedInUser] = useState(null);
    const [userData, setUserData] = useState(null);
    const [error, setError] = useState('');
    const [isLoading, setIsLoading] = useState(false);

    // Effect to fetch user data when a user logs in
    useEffect(() => {
        if (loggedInUser) {
            setIsLoading(true);
            fetch(`/api/user/${loggedInUser}`)
                .then(res => {
                    if (!res.ok) {
                        throw new Error('Failed to fetch user data');
                    }
                    return res.json();
                })
                .then(data => {
                    setUserData(data);
                    setError('');
                })
                .catch(err => {
                    setError(err.message);
                    setUserData(null);
                })
                .finally(() => setIsLoading(false));
        }
    }, [loggedInUser]);

    // Handle form submission for login
    const handleLogin = async (e) => {
        e.preventDefault();
        setError('');
        setIsLoading(true);

        try {
            const response = await fetch('/api/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ username, password }),
            });

            const data = await response.json();

            if (response.ok) {
                setLoggedInUser(data.username);
                // Clear form fields
                setUsername('');
                setPassword('');
            } else {
                throw new Error(data.message || 'Login failed');
            }
        } catch (err) {
            setError(err.message);
            setLoggedInUser(null);
            setUserData(null);
        } finally {
            setIsLoading(false);
        }
    };

    // Handle logout
    const handleLogout = () => {
        setLoggedInUser(null);
        setUserData(null);
        setError('');
    };

    // Render the main application component
    return (
        <div className="container">
            <header>
                <h1>Javalin & React SPA</h1>
            </header>
            <main>
                {!loggedInUser ? (
                    // Show login form if no user is logged in
                    <form onSubmit={handleLogin} className="login-form">
                        <h2>Login</h2>
                        {error && <p className="error-message">{error}</p>}
                        <div className="form-group">
                            <label htmlFor="username">Username</label>
                            <input
                                type="text"
                                id="username"
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                required
                                placeholder="testuser"
                            />
                        </div>
                        <div className="form-group">
                            <label htmlFor="password">Password</label>
                            <input
                                type="password"
                                id="password"
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                required
                                placeholder="password123"
                            />
                        </div>
                        <button type="submit" disabled={isLoading}>
                            {isLoading ? 'Logging in...' : 'Login'}
                        </button>
                    </form>
                ) : (
                    // Show user profile if a user is logged in
                    <div className="profile-view">
                        <h2>Welcome, {userData?.username}!</h2>
                        {isLoading && <p>Loading profile...</p>}
                        {error && <p className="error-message">{error}</p>}
                        {userData?.imageUrl && (
                            <div className="profile-picture-container">
                                <img
                                    src={userData.imageUrl}
                                    alt={`${userData.username}'s profile`}
                                    className="profile-picture"
                                    onError={(e) => { e.target.onerror = null; e.target.src='[https://placehold.co/400x400/CCCCCC/FFFFFF?text=Image+Error](https://placehold.co/400x400/CCCCCC/FFFFFF?text=Image+Error)'; }}
                                />
                            </div>
                        )}
                        <button onClick={handleLogout}>Logout</button>
                    </div>
                )}
            </main>
            <footer>
                <p>A simple SPA example.</p>
            </footer>
        </div>
    );
}

export default App;
