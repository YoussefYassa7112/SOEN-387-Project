import { useState } from 'react'
import { GoogleLogin } from '@react-oauth/google'
import { callApi } from '../api.js'
import ResponseBox from './ResponseBox.jsx'

// POST /auth/google: trade the Google ID token for our own Locus JWT.
function SignIn({ token, setToken }) {
    const [result, setResult] = useState(null)
    const [pastedToken, setPastedToken] = useState('')

    const handleGoogleSuccess = async (credentialResponse) => {
        const answer = await callApi('POST', '/auth/google', null, {
            idToken: credentialResponse.credential,
        })
        setResult(answer)

        if (answer.status === 200) {
            setToken(answer.body.accessToken)
        }
    }

    const handleUsePastedToken = () => {
        setToken(pastedToken.trim())
        setPastedToken('')
    }

    return (
        <section>
            <h2>1. Sign in</h2>

            {token ? (
                <div>
                    <p>Signed in. Every protected request below sends this JWT.</p>
                    <textarea readOnly rows={3} value={token} />
                    <button onClick={() => setToken('')}>Log out</button>
                </div>
            ) : (
                <div>
                    <GoogleLogin
                        onSuccess={handleGoogleSuccess}
                        onError={() => setResult({ request: 'Google Sign-In', status: 'failed', body: '' })}
                    />
                    <p>Or paste a Locus JWT (useful to test as a second user):</p>
                    <textarea rows={3} value={pastedToken} onChange={(e) => setPastedToken(e.target.value)} />
                    <button onClick={handleUsePastedToken} disabled={pastedToken.trim() === ''}>
                        Use this token
                    </button>
                </div>
            )}

            <ResponseBox result={result} />
        </section>
    )
}

export default SignIn
