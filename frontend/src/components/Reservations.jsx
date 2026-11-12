import { useState } from 'react'
import { callApi, toApiTime } from '../api.js'
import ResponseBox from './ResponseBox.jsx'

// POST /parking-spots/{spotId}/reservations and GET /reservations/{reservationId} (need a token).
function Reservations({ token, spotId }) {
    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    // A new random key for each new reservation attempt.
    // Keep the same key and press Reserve again to see the stored answer replayed.
    const [idempotencyKey, setIdempotencyKey] = useState(crypto.randomUUID())
    const [reservationId, setReservationId] = useState('')
    const [result, setResult] = useState(null)

    const handleReserve = async () => {
        const body = { startTime: toApiTime(startTime), endTime: toApiTime(endTime) }
        const answer = await callApi('POST', '/parking-spots/' + spotId + '/reservations', token, body, {
            'Idempotency-Key': idempotencyKey,
        })
        setResult(answer)

        if (answer.status === 202) {
            setReservationId(answer.body.reservationId)
        }
    }

    const handleGet = async () => {
        setResult(await callApi('GET', '/reservations/' + reservationId, token))
    }

    return (
        <section>
            <h2>5. Reservations (driver, needs sign-in)</h2>
            <p>Reserves the spot ID at the top of the page.</p>

            <label>Start time <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></label>
            <label>End time <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></label>
            <label>Idempotency-Key <input value={idempotencyKey} onChange={(e) => setIdempotencyKey(e.target.value)} /></label>
            <button onClick={() => setIdempotencyKey(crypto.randomUUID())}>New key</button>
            <button onClick={handleReserve}>Reserve</button>

            <label>Reservation ID <input value={reservationId} onChange={(e) => setReservationId(e.target.value)} /></label>
            <button onClick={handleGet}>Get reservation</button>

            <ResponseBox result={result} />
        </section>
    )
}

export default Reservations
