import { useEffect, useState } from 'react'
import { callApi, errorMessage, isSuccess, showPrice, showTime } from '../api.js'
import DemoDetails from '../components/DemoDetails.jsx'

const STATUS_TEXT = {
    pendingPayment: 'Waiting for payment',
    confirmed: 'Confirmed',
    paymentFailed: 'Payment failed',
    cancelled: 'Cancelled',
}

// Shows the reservations made in this browser.
// The API has no "list my reservations" endpoint yet, so we remember the IDs
// we booked and load each one with GET /reservations/{reservationId}.
function ReservationsPage({ token, reservationIds, saveReservation, openSpot, demoMode }) {
    const [results, setResults] = useState([])
    const [searchId, setSearchId] = useState('')

    useEffect(() => {
        const loadAll = async () => {
            const answers = []
            for (const id of reservationIds) {
                answers.push(await callApi('GET', '/reservations/' + id, token))
            }
            setResults(answers)
        }
        if (token) {
            loadAll()
        }
    }, [token, reservationIds])

    const handleFind = (event) => {
        event.preventDefault()
        if (searchId.trim() !== '') {
            saveReservation(searchId.trim())
            setSearchId('')
        }
    }

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

            {reservationIds.length === 0 && <p className="muted">No reservations yet. Find a spot and book it!</p>}

            <div className="grid">
                {results.map((answer, index) => (
                    <div className="card" key={index}>
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
                ))}
            </div>
        </div>
    )
}

export default ReservationsPage
