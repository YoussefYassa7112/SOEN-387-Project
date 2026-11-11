
import { useState } from 'react'
import { GoogleLogin } from '@react-oauth/google'

function App() {
    const [status, setStatus] = useState('')
    const [accessToken, setAccessToken] = useState(null)

    const handleGoogleSuccess = async (credentialResponse) => {
        setStatus('Signing in with Locus...')

        try {
            const response = await fetch(
                'http://localhost:8080/api/v1/auth/google',
                {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                    },
                    body: JSON.stringify({
                        idToken: credentialResponse.credential,
                    }),
                }
            )

            if (!response.ok) {
                throw new Error(`Backend returned ${response.status}`)
            }

            const data = await response.json()

            if (!data.accessToken || data.tokenType !== 'Bearer') {
                throw new Error('Invalid authentication response')
            }

            setAccessToken(data.accessToken)
            setStatus('Successfully authenticated with Locus!')

        } catch (error) {
            console.error('Authentication error:', error.message)
            setAccessToken(null)
            setStatus(`Authentication failed: ${error.message}`)
        }
    }

    const handleCopyToken = async () => {
        try {
            await navigator.clipboard.writeText(accessToken)
            setStatus('JWT token copied to clipboard!')
        } catch (error) {
            console.error('Clipboard error:', error)
            setStatus('Could not copy token. Check browser permissions.')
        }
    }

    const handleLogout = () => {
        setAccessToken(null)
        setStatus('Logged out of Locus')
    }

    return (
        <main style={{ padding: '40px' }}>
            <h1>Locus</h1>
            <p>Parking Rental Platform</p>

            {!accessToken ? (
                <GoogleLogin
                    onSuccess={handleGoogleSuccess}
                    onError={() => setStatus('Google Sign-In failed')}
                />
            ) : (
                <div>
                    <h2>Authentication successful!</h2>
                    <p>You received a Locus JWT bearer token.</p>

                    <button onClick={handleCopyToken}>
                        Copy JWT Token
                    </button>

                    <button
                        onClick={handleLogout}
                        style={{ marginLeft: '10px' }}
                    >
                        Log out
                    </button>
                </div>
            )}

            <p role="status">{status}</p>
        </main>
    )
}

export default App
