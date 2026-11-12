import { useState } from 'react'
import { callApi, toApiTime } from '../api.js'
import ResponseBox from './ResponseBox.jsx'

// GET /parking-spots/{spotId} and GET /parking-spots/{spotId}/availability (both public).
function SpotDetails({ spotId }) {
    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    const [result, setResult] = useState(null)

    const handleDetails = async () => {
        setResult(await callApi('GET', '/parking-spots/' + spotId))
    }

    const handleAvailability = async () => {
        const params = new URLSearchParams()
        params.append('startTime', toApiTime(startTime))
        params.append('endTime', toApiTime(endTime))
        setResult(await callApi('GET', '/parking-spots/' + spotId + '/availability?' + params.toString()))
    }

    return (
        <section>
            <h2>3. Spot details and availability (public)</h2>
            <p>Uses the spot ID at the top of the page.</p>

            <button onClick={handleDetails}>Get details</button>

            <label>Start time <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></label>
            <label>End time <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></label>
            <button onClick={handleAvailability}>Check availability</button>

            <ResponseBox result={result} />
        </section>
    )
}

export default SpotDetails
