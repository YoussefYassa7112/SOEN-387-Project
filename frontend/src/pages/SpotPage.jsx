import { useEffect, useState } from 'react'
import { CardElement, useElements, useStripe } from '@stripe/react-stripe-js'
import { callApi, errorMessage, isSuccess, showPrice, showTime, toApiTime } from '../api.js'
import DemoDetails from '../components/DemoDetails.jsx'
import DirectionsButton from '../components/DirectionsButton.jsx'
import SpotMap from '../components/SpotMap.jsx'
import { STRIPE_ENABLED } from '../stripe.js'

// Fallback when there is no Stripe publishable key: Stripe's ready-made test cards.
const TEST_CARDS = [
    { value: 'pm_card_visa', label: 'Visa - payment succeeds' },
    { value: 'pm_card_chargeDeclined', label: 'Declined card - payment fails' },
]

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
    const [testCard, setTestCard] = useState(TEST_CARDS[0].value)
    const [checkResult, setCheckResult] = useState(null)

    // Stripe's card form. The card number goes straight to Stripe, never to our server;
    // Stripe gives back an id (pm_...) that we send with the booking.
    const stripe = useStripe()
    const elements = useElements()
    const [cardComplete, setCardComplete] = useState(false)
    const [cardError, setCardError] = useState(null)
    // The pm_... id for the current attempt, reused if the same request is sent again,
    // so a retry with the same Idempotency-Key is exactly the same request.
    const [paymentMethodId, setPaymentMethodId] = useState(null)

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

    // Which card to charge: the card typed in Stripe's form, or the chosen test card.
    const choosePaymentMethod = async () => {
        if (!STRIPE_ENABLED) {
            return testCard
        }
        if (paymentMethodId) {
            return paymentMethodId
        }
        const { paymentMethod, error } = await stripe.createPaymentMethod({
            type: 'card',
            card: elements.getElement(CardElement),
        })
        if (error) {
            setCardError(error.message)
            return null
        }
        setPaymentMethodId(paymentMethod.id)
        return paymentMethod.id
    }

    const reserve = async (key) => {
        const paymentMethod = await choosePaymentMethod()
        if (!paymentMethod) {
            return
        }
        const body = { startTime: toApiTime(startTime), endTime: toApiTime(endTime), paymentMethod: paymentMethod }
        const answer = await callApi('POST', '/parking-spots/' + spotId + '/reservations', token, body, {
            'Idempotency-Key': key,
        })
        setReserveResult(answer)
        setCheckResult(null)

        if (answer.status === 202) {
            saveReservation(answer.body.reservationId)
        }
    }

    // A new time or card means a new reservation attempt, so it needs a new key.
    const changeTime = (setter, value) => {
        setter(value)
        setIdempotencyKey(crypto.randomUUID())
        setPaymentMethodId(null)
        setCheckResult(null)
        setReserveResult(null)
    }

    // Editing the card is also a new attempt.
    const changeCard = (event) => {
        setCardComplete(event.complete)
        setCardError(event.error ? event.error.message : null)
        setIdempotencyKey(crypto.randomUUID())
        setPaymentMethodId(null)
        setReserveResult(null)
    }

    const canReserve = timesAreFilled && (!STRIPE_ENABLED || (stripe !== null && cardComplete))

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

                    <h3>Location</h3>
                    <SpotMap spots={[spot]} />
                    <p><DirectionsButton address={spot.address} /></p>

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
                    {STRIPE_ENABLED ? (
                        <label>
                            Card
                            <div className="card-input">
                                <CardElement options={{ hidePostalCode: true }} onChange={changeCard} />
                            </div>
                            <span className="small">
                                Test mode, no real cards: 4242 4242 4242 4242 succeeds,
                                4000 0000 0000 0002 is declined. Any future date and any CVC.
                            </span>
                            {cardError && <span className="message error">{cardError}</span>}
                        </label>
                    ) : (
                        <label>
                            Test card
                            <select value={testCard} onChange={(e) => changeTime(setTestCard, e.target.value)}>
                                {TEST_CARDS.map((card) => (
                                    <option key={card.value} value={card.value}>{card.label}</option>
                                ))}
                            </select>
                        </label>
                    )}

                    <div className="row">
                        <button className="button secondary" disabled={!timesAreFilled} onClick={checkAvailability}>
                            Check availability
                        </button>
                        <button className="button" disabled={!canReserve} onClick={() => reserve(idempotencyKey)}>
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
