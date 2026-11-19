import { useEffect, useState } from 'react'
import { callApi, errorMessage, isSuccess, showPrice, showTime } from '../api.js'
import DemoDetails from '../components/DemoDetails.jsx'

const STATUS_TEXT = {
    pendingPayment: 'Waiting for payment',
    confirmed: 'Confirmed',
    paymentFailed: 'Payment failed',
    cancelled: 'Cancelled',
}

// One reservation card.
function ReservationCard({ answer, openSpot, demoMode }) {
    return (
        <div className="card">
            {isSuccess(answer) ? (
                <div>
                    <span className={'badge ' + answer.body.status}>{STATUS_TEXT[answer.body.status]}</span>
                    <p><strong>{showTime(answer.body.startTime)}</strong> &rarr; <strong>{showTime(answer.body.endTime)}</strong></p>
                    <p>Total: {showPrice(answer.body.totalPrice)} {answer.body.currency}</p>
                    <p className="small muted">ID: {answer.body.reservationId}</p>
                    <button className="button small secondary" onClick={() => openSpot(answer.body.spotId)}>View spot</button>
                </div>
            ) : (
                <p className="message error">{errorMessage(answer)}</p>
            )}
            <DemoDetails demoMode={demoMode} result={answer} />
        </div>
    )
}

// LOCUS-30 / LOCUS-50: all of my reservations, loaded from GET /me/reservations,
// so they show up on any computer I sign in from.
// reservationIds (the bookings made in this browser) is only used to reload the list after a new booking.
function ReservationsPage({ token, reservationIds, openSpot, demoMode }) {
    const [listResult, setListResult] = useState(null)
    const [searchId, setSearchId] = useState('')
    const [foundResult, setFoundResult] = useState(null)

    useEffect(() => {
        const loadMine = async () => {
            setListResult(await callApi('GET', '/me/reservations', token))
        }
        if (token) {
            loadMine()
        }
    }, [token, reservationIds])

    // Look up one reservation by its ID. Someone else's ID gets a 403 (useful in the demo).
    const handleFind = async (event) => {
        event.preventDefault()
        if (searchId.trim() !== '') {
            setFoundResult(await callApi('GET', '/reservations/' + searchId.trim(), token))
        }
    }

    const reservations = listResult && isSuccess(listResult) ? listResult.body : []

    if (!token) {
        return <p className="message">Sign in with Google to see your reservations.</p>
    }

    return (
        <div>
            <div className="page-title">
                <h1>My reservations</h1>
            </div>

            <form className="row" onSubmit={handleFind}>
                <input placeholder="Reservation ID" value={searchId} onChange={(e) => setSearchId(e.target.value)} />
                <button className="button secondary">Find reservation</button>
            </form>

            {foundResult && (
                <div className="grid">
                    <ReservationCard answer={foundResult} openSpot={openSpot} demoMode={demoMode} />
                </div>
            )}

            {listResult === null && <p>Loading...</p>}
            {listResult && !isSuccess(listResult) && <p className="message error">{errorMessage(listResult)}</p>}
            {listResult && isSuccess(listResult) && reservations.length === 0 && (
                <p className="muted">No reservations yet. Find a spot and book it!</p>
            )}

            <div className="grid">
                {reservations.map((reservation) => (
                    <ReservationCard
                        key={reservation.reservationId}
                        answer={{ status: 200, body: reservation }}
                        openSpot={openSpot}
                        demoMode={false}
                    />
                ))}
            </div>
            <DemoDetails demoMode={demoMode} result={listResult} />
        </div>
    )
}

export default ReservationsPage
