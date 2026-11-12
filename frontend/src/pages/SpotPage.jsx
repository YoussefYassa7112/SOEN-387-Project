import { useEffect, useState } from 'react'
import { callApi, errorMessage, isSuccess, showPrice, showTime, toApiTime } from '../api.js'
import DemoDetails from '../components/DemoDetails.jsx'

const REASONS = {
    outsideOwnerAvailability: 'The owner has not made the spot available for this whole time.',
    alreadyReserved: 'Someone already reserved this spot during that time.',
    inactive: 'This spot is no longer listed.',
}

// Details of one spot, with an availability check and a reservation form.
function SpotPage({ spotId, token, goBack, saveReservation, demoMode }) {
    const [spotResult, setSpotResult] = useState(null)

    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    const [checkResult, setCheckResult] = useState(null)

    // Each new reservation attempt gets a new random Idempotency-Key.
    // If the same request is sent again with the same key, the server returns
    // the first answer instead of booking twice (Chapter 3).
    const [idempotencyKey, setIdempotencyKey] = useState(crypto.randomUUID())
    const [reserveResult, setReserveResult] = useState(null)

    useEffect(() => {
        const loadSpot = async () => {
            setSpotResult(await callApi('GET', '/parking-spots/' + spotId))
        }
        loadSpot()
    }, [spotId])

    const timesAreFilled = startTime !== '' && endTime !== ''

    const checkAvailability = async () => {
        const params = new URLSearchParams()
        params.append('startTime', toApiTime(startTime))
        params.append('endTime', toApiTime(endTime))
        setCheckResult(await callApi('GET', '/parking-spots/' + spotId + '/availability?' + params.toString()))
    }

    const reserve = async (key) => {
        const body = { startTime: toApiTime(startTime), endTime: toApiTime(endTime) }
        const answer = await callApi('POST', '/parking-spots/' + spotId + '/reservations', token, body, {
            'Idempotency-Key': key,
        })
        setReserveResult(answer)
        setCheckResult(null)

        if (answer.status === 202) {
            saveReservation(answer.body.reservationId)
        }
    }

    // A new time means a new reservation attempt, so it needs a new key.
    const changeTime = (setter, value) => {
        setter(value)
        setIdempotencyKey(crypto.randomUUID())
        setCheckResult(null)
        setReserveResult(null)
    }

    if (!spotResult) {
        return <p>Loading...</p>
    }

    if (!isSuccess(spotResult)) {
        return (
            <div>
                <button className="button secondary" onClick={goBack}>&larr; Back</button>
                <p className="message error">{errorMessage(spotResult)}</p>
                <DemoDetails demoMode={demoMode} result={spotResult} />
            </div>
        )
    }

    const spot = spotResult.body

    return (
        <div>
            <button className="button secondary" onClick={goBack}>&larr; Back</button>

            <div className="spot-layout">
                <div className="card">
                    {spot.photoUrl && <img className="spot-photo large" src={spot.photoUrl} alt={spot.title} />}
                    <h1>{spot.title}</h1>
                    <p className="muted">{spot.address}</p>
                    <p className="price">{showPrice(spot.pricePerHour)} <span className="muted">/ hour</span></p>
                    {spot.parkingType && <span className="badge">{spot.parkingType}</span>}
                    {spot.description && <p>{spot.description}</p>}
                    {spot.restrictions && <p><strong>Restrictions:</strong> {spot.restrictions}</p>}

                    <h3>Available</h3>
                    <ul>
                        {spot.availabilityWindows.map((window, index) => (
                            <li key={index}>{showTime(window.startTime)} &rarr; {showTime(window.endTime)}</li>
                        ))}
                    </ul>
                    <DemoDetails demoMode={demoMode} result={spotResult} />
                </div>

                <div className="card">
                    <h2>Book this spot</h2>
                    <label>
                        From
                        <input type="datetime-local" value={startTime} onChange={(e) => changeTime(setStartTime, e.target.value)} />
                    </label>
                    <label>
                        Until
                        <input type="datetime-local" value={endTime} onChange={(e) => changeTime(setEndTime, e.target.value)} />
                    </label>

                    <div className="row">
                        <button className="button secondary" disabled={!timesAreFilled} onClick={checkAvailability}>
                            Check availability
                        </button>
                        <button className="button" disabled={!timesAreFilled} onClick={() => reserve(idempotencyKey)}>
                            Reserve
                        </button>
                    </div>

                    {checkResult && isSuccess(checkResult) && (
                        <p className={checkResult.body.available ? 'message success' : 'message error'}>
                            {checkResult.body.available ? 'Available for this time.' : REASONS[checkResult.body.reason]}
                        </p>
                    )}
                    {checkResult && !isSuccess(checkResult) && <p className="message error">{errorMessage(checkResult)}</p>}
                    <DemoDetails demoMode={demoMode} result={checkResult} />

                    {reserveResult && reserveResult.status === 202 && (
                        <div className="message success">
                            <p><strong>Reservation received!</strong></p>
                            <p>Total: {showPrice(reserveResult.body.totalPrice)} {reserveResult.body.currency}</p>
                            <p>Status: {reserveResult.body.status}</p>
                            <p className="small">Reservation ID: {reserveResult.body.reservationId}</p>
                        </div>
                    )}
                    {reserveResult && !isSuccess(reserveResult) && <p className="message error">{errorMessage(reserveResult)}</p>}

                    {demoMode && reserveResult && (
                        <div className="demo-actions">
                            <p className="small">Idempotency-Key sent: <code>{reserveResult.request.headers['Idempotency-Key']}</code></p>
                            <button className="button small demo" onClick={() => reserve(idempotencyKey)}>
                                Send again with the same key
                            </button>
                            <button className="button small demo" onClick={() => reserve(crypto.randomUUID())}>
                                Send again with a new key
                            </button>
                        </div>
                    )}
                    <DemoDetails demoMode={demoMode} result={reserveResult} />
                </div>
            </div>
        </div>
    )
}

export default SpotPage
