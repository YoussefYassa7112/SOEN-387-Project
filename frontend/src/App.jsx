import { useState } from 'react'
import Header from './components/Header.jsx'
import DemoDetails from './components/DemoDetails.jsx'
import SearchPage from './pages/SearchPage.jsx'
import SpotPage from './pages/SpotPage.jsx'
import MySpotsPage from './pages/MySpotsPage.jsx'
import ReservationsPage from './pages/ReservationsPage.jsx'
import { callApi, isSuccess } from './api.js'

// Reads a saved value from the browser, or returns the default.
function load(key, defaultValue) {
    const saved = localStorage.getItem(key)
    return saved === null ? defaultValue : JSON.parse(saved)
}

function save(key, value) {
    localStorage.setItem(key, JSON.stringify(value))
}

function App() {
    // Which page is shown: 'search', 'spot', 'mySpots' or 'reservations'.
    const [page, setPage] = useState('search')
    const [previousPage, setPreviousPage] = useState('search')
    const [spotId, setSpotId] = useState('')

    // Saved in the browser so a refresh keeps them.
    const [token, setToken] = useState(load('locusToken', ''))
    const [demoMode, setDemoMode] = useState(load('locusDemoMode', false))
    const [reservationIds, setReservationIds] = useState(load('locusReservations', []))

    // Demo mode: the last sign-in call, and a box to paste a token for a second user.
    const [signInResult, setSignInResult] = useState(null)
    const [pastedToken, setPastedToken] = useState('')

    const changeToken = (newToken) => {
        save('locusToken', newToken)
        setToken(newToken)
    }

    const changeDemoMode = (on) => {
        save('locusDemoMode', on)
        setDemoMode(on)
    }

    const saveReservation = (id) => {
        if (!reservationIds.includes(id)) {
            const newIds = [id].concat(reservationIds)
            save('locusReservations', newIds)
            setReservationIds(newIds)
        }
    }

    const goTo = (newPage) => {
        setPage(newPage)
        window.scrollTo(0, 0)
    }

    const openSpot = (id) => {
        setSpotId(id)
        setPreviousPage(page)
        goTo('spot')
    }

    // POST /auth/google: trade the Google ID token for our own Locus JWT.
    const handleGoogleSuccess = async (credentialResponse) => {
        const answer = await callApi('POST', '/auth/google', null, { idToken: credentialResponse.credential })
        setSignInResult(answer)
        if (isSuccess(answer)) {
            changeToken(answer.body.accessToken)
        } else {
            alert('Sign-in failed (status ' + answer.status + ')')
        }
    }

    return (
        <div>
            <Header
                page={page}
                goTo={goTo}
                token={token}
                onGoogleSuccess={handleGoogleSuccess}
                onLogout={() => changeToken('')}
                demoMode={demoMode}
                setDemoMode={changeDemoMode}
            />

            {demoMode && (
                <div className="demo-bar">
                    <div className="container">
                        <strong>Demo mode:</strong> buttons marked in purple show the raw requests and responses.
                        <div className="row">
                            <input placeholder="Paste a Locus JWT to act as another user" value={pastedToken} onChange={(e) => setPastedToken(e.target.value)} />
                            <button className="button small demo" disabled={pastedToken.trim() === ''} onClick={() => { changeToken(pastedToken.trim()); setPastedToken('') }}>
                                Use this token
                            </button>
                            {token && (
                                <button className="button small demo" onClick={() => navigator.clipboard.writeText(token)}>
                                    Copy my token
                                </button>
                            )}
                        </div>
                        <DemoDetails demoMode={demoMode} result={signInResult} />
                    </div>
                </div>
            )}

            <main className="container">
                {page === 'search' && <SearchPage openSpot={openSpot} demoMode={demoMode} />}
                {page === 'spot' && (
                    <SpotPage
                        spotId={spotId}
                        token={token}
                        goBack={() => goTo(previousPage)}
                        saveReservation={saveReservation}
                        demoMode={demoMode}
                    />
                )}
                {page === 'mySpots' && <MySpotsPage token={token} openSpot={openSpot} demoMode={demoMode} />}
                {page === 'reservations' && (
                    <ReservationsPage
                        token={token}
                        reservationIds={reservationIds}
                        saveReservation={saveReservation}
                        openSpot={openSpot}
                        demoMode={demoMode}
                    />
                )}
            </main>
        </div>
    )
}

export default App
