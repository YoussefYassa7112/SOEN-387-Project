import { useEffect, useState } from 'react'
import { callApi, errorMessage, isSuccess, toApiTime } from '../api.js'
import SpotCard from '../components/SpotCard.jsx'
import DemoDetails from '../components/DemoDetails.jsx'

const PAGE_SIZE = 6

// Home page: search active parking spots (public, no sign-in needed).
function SearchPage({ openSpot, demoMode }) {
    const [location, setLocation] = useState('')
    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    const [maxPrice, setMaxPrice] = useState('')
    const [parkingType, setParkingType] = useState('')
    const [offset, setOffset] = useState(0)

    const [result, setResult] = useState(null)
    const [error, setError] = useState('')

    const search = async (newOffset) => {
        // Only send the filters that were filled in.
        const params = new URLSearchParams()
        if (location !== '') params.append('location', location)
        if (startTime !== '') params.append('startTime', toApiTime(startTime))
        if (endTime !== '') params.append('endTime', toApiTime(endTime))
        if (maxPrice !== '') params.append('maxPricePerHour', maxPrice)
        if (parkingType !== '') params.append('parkingType', parkingType)
        params.append('limit', PAGE_SIZE)
        params.append('offset', newOffset)

        const answer = await callApi('GET', '/parking-spots?' + params.toString())
        setResult(answer)
        setOffset(newOffset)
        setError(isSuccess(answer) ? '' : errorMessage(answer))
    }

    // Show all spots when the page opens.
    useEffect(() => {
        search(0)
    }, [])

    const handleSubmit = (event) => {
        event.preventDefault()
        search(0)
    }

    const page = result && isSuccess(result) ? result.body : null

    return (
        <div>
            <section className="hero">
                <h1>Find a parking spot</h1>
                <p>Book a driveway, garage or private lot from people near you.</p>

                <form className="card search-form" onSubmit={handleSubmit}>
                    <label className="wide">
                        Where?
                        <input placeholder="Address or area" value={location} onChange={(e) => setLocation(e.target.value)} />
                    </label>
                    <label>
                        From
                        <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} />
                    </label>
                    <label>
                        Until
                        <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} />
                    </label>
                    <label>
                        Max $/hour
                        <input type="number" min="0" step="0.01" value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)} />
                    </label>
                    <label>
                        Type
                        <select value={parkingType} onChange={(e) => setParkingType(e.target.value)}>
                            <option value="">Any</option>
                            <option value="driveway">Driveway</option>
                            <option value="privateLot">Private lot</option>
                            <option value="garage">Garage</option>
                            <option value="other">Other</option>
                        </select>
                    </label>
                    <button className="button">Search</button>
                </form>
            </section>

            {error && <p className="message error">{error}</p>}
            <DemoDetails demoMode={demoMode} result={result} />

            {page && (
                <div>
                    <p className="muted">{page.total} spot(s) found</p>

                    <div className="grid">
                        {page.items.map((spot) => (
                            <SpotCard key={spot.spotId} spot={spot} onClick={() => openSpot(spot.spotId)} />
                        ))}
                    </div>

                    {page.total > PAGE_SIZE && (
                        <div className="pager">
                            <button className="button secondary" disabled={offset === 0} onClick={() => search(offset - PAGE_SIZE)}>
                                Previous
                            </button>
                            <span>
                                Page {offset / PAGE_SIZE + 1} of {Math.ceil(page.total / PAGE_SIZE)}
                            </span>
                            <button className="button secondary" disabled={offset + PAGE_SIZE >= page.total} onClick={() => search(offset + PAGE_SIZE)}>
                                Next
                            </button>
                        </div>
                    )}
                </div>
            )}
        </div>
    )
}

export default SearchPage
