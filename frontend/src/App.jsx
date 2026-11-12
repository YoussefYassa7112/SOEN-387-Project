import { useState } from 'react'
import SignIn from './components/SignIn.jsx'
import SearchSpots from './components/SearchSpots.jsx'
import SpotDetails from './components/SpotDetails.jsx'
import OwnerSpots from './components/OwnerSpots.jsx'
import Reservations from './components/Reservations.jsx'

// Test page for the Locus API.
// Each section calls the endpoints we have so far and shows the status code
// and the raw JSON of the answer, including errors.
function App() {
    // The JWT is kept in localStorage so a page refresh does not log us out.
    const [token, setToken] = useState(localStorage.getItem('locusToken') || '')
    const [spotId, setSpotId] = useState('')

    const saveToken = (newToken) => {
        localStorage.setItem('locusToken', newToken)
        setToken(newToken)
    }

    return (
        <main>
            <h1>Locus API test page</h1>

            <div className="current">
                <label>
                    Spot ID used by sections 3, 4 and 5
                    <input value={spotId} onChange={(e) => setSpotId(e.target.value)} placeholder="paste a spotId, or create a spot" />
                </label>
            </div>

            <SignIn token={token} setToken={saveToken} />
            <SearchSpots />
            <SpotDetails spotId={spotId} />
            <OwnerSpots token={token} spotId={spotId} setSpotId={setSpotId} />
            <Reservations token={token} spotId={spotId} />
        </main>
    )
}

export default App
