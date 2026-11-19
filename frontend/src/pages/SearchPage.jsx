import { useEffect, useState } from 'react'
import { callApi, errorMessage, isSuccess, toApiTime, toInputTime } from '../api.js'
import SpotCard from '../components/SpotCard.jsx'
import DemoDetails from '../components/DemoDetails.jsx'

const PAGE_SIZE = 6
const EVENT_BUFFER_MS = 15 * 60 * 1000

// Home page: search active parking spots (public, no sign-in needed).
function SearchPage({ openSpot, demoMode, token }) {
    const [location, setLocation] = useState('')
    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    const [maxPrice, setMaxPrice] = useState('')
    const [parkingType, setParkingType] = useState('')
    const [sort, setSort] = useState('')
    const [eventQuery, setEventQuery] = useState('')
    const [lookingUpEvent, setLookingUpEvent] = useState(false)
    const [eventParking, setEventParking] = useState(null)
    const [rankingEvent, setRankingEvent] = useState(false)
    const [foundEvent, setFoundEvent] = useState(null)
    const [eventMessage, setEventMessage] = useState('')
    const [destinationLatitude, setDestinationLatitude] = useState('')
    const [destinationLongitude, setDestinationLongitude] = useState('')
    const [query, setQuery] = useState('')
    const [interpreting, setInterpreting] = useState(false)
    const [aiMessage, setAiMessage] = useState('')
    const [appliedFilters, setAppliedFilters] = useState('')
    const [offset, setOffset] = useState(0)

    const [result, setResult] = useState(null)
    const [error, setError] = useState('')

    const search = async (newOffset, filters) => {
        const params = new URLSearchParams(filters)
        params.set('limit', PAGE_SIZE)
        params.set('offset', newOffset)

        const answer = await callApi('GET', '/parking-spots?' + params.toString())
        setEventParking(null)
        setResult(answer)
        setOffset(newOffset)
        setError(isSuccess(answer) ? '' : errorMessage(answer))
    }

    useEffect(() => {
        search(0, '')
    }, [])

    const handleSubmit = (event) => {
        event.preventDefault()
        if (sort === 'event' && foundEvent) {
            rankEventParking(foundEvent.venue, location, toApiTime(startTime), toApiTime(endTime))
            return
        }

        // The displayed venue address is a destination, not a listing address filter.
        const params = new URLSearchParams()
        if (location !== '' && location !== foundEvent?.address) params.append('location', location)
        if (startTime !== '') params.append('startTime', toApiTime(startTime))
        if (endTime !== '') params.append('endTime', toApiTime(endTime))
        if (maxPrice !== '') params.append('maxPricePerHour', maxPrice)
        if (parkingType !== '') params.append('parkingType', parkingType)
        if (sort !== '') params.append('sort', sort)
        if (sort === 'closest') {
            params.set('destinationLatitude', destinationLatitude)
            params.set('destinationLongitude', destinationLongitude)
        }
        const filters = params.toString()
        setAppliedFilters(filters)
        search(0, filters)
    }

    const applyAiFilters = async () => {
        setInterpreting(true)
        setAiMessage('')
        const answer = await callApi('POST', '/parking-spots/interpret-search', token, { query: query.trim() })
        setInterpreting(false)
        if (!isSuccess(answer)) {
            setAiMessage(errorMessage(answer))
            return
        }

        const filters = answer.body
        if (Object.values(filters).every((value) => value == null)) {
            setAiMessage('Try including an area, time range, parking type, or hourly budget.')
            return
        }
        setFoundEvent(null)
        if (sort === 'event') setSort('')
        setLocation(filters.location ?? '')
        setStartTime(filters.startTime ? toInputTime(filters.startTime) : '')
        setEndTime(filters.endTime ? toInputTime(filters.endTime) : '')
        setMaxPrice(filters.maxPricePerHour ?? '')
        setParkingType(filters.parkingType ?? '')
        setAiMessage('Review the filters, then select Search.')
    }

    const rankEventParking = async (venue, address, parkingStart, parkingEnd) => {
        setRankingEvent(true)
        setError('')
        const answer = await callApi('POST', '/parking-spots/event-parking', token, {
            venue,
            address,
            startTime: parkingStart,
            endTime: parkingEnd,
            maxPricePerHour: maxPrice === '' ? null : Number(maxPrice),
            parkingType: parkingType || null,
        })
        setRankingEvent(false)
        setResult(answer)
        setOffset(0)
        setEventParking(isSuccess(answer) ? answer.body : null)
        setError(isSuccess(answer) ? '' : errorMessage(answer))
    }

    const lookupEvent = async (event) => {
        event.preventDefault()
        if (!token || interpreting || lookingUpEvent || rankingEvent || !eventQuery.trim()) return
        setLookingUpEvent(true)
        setEventMessage('')
        const answer = await callApi('POST', '/parking-spots/lookup-event', token, { query: eventQuery.trim() })
        setLookingUpEvent(false)
        if (!isSuccess(answer)) {
            setEventMessage(errorMessage(answer))
            return
        }

        const details = answer.body
        const parkingStart = new Date(Date.parse(details.startTime) - EVENT_BUFFER_MS).toISOString()
        const parkingEnd = details.endTime
            ? new Date(Date.parse(details.endTime) + EVENT_BUFFER_MS).toISOString()
            : ''
        const address = details.address || details.venue
        setFoundEvent({ ...details, address })
        setLocation(address)
        setStartTime(toInputTime(parkingStart))
        setEndTime(parkingEnd ? toInputTime(parkingEnd) : '')
        setDestinationLatitude(details.coordinates?.latitude ?? '')
        setDestinationLongitude(details.coordinates?.longitude ?? '')
        setSort('event')
        setResult(null)
        setError('')
        setEventParking(null)
        if (parkingEnd) {
            setEventMessage('Parking times include 15 minutes before and after the event.')
            await rankEventParking(details.venue, address, parkingStart, parkingEnd)
        } else {
            setEventMessage('The start includes 15 extra minutes. Enter a parking end time with 15 minutes to return to your car.')
        }
    }

    const page = eventParking
        ? { items: eventParking.items.slice(offset, offset + PAGE_SIZE), total: eventParking.items.length }
        : result && isSuccess(result) && result.body.items ? result.body : null

    const changePage = (nextOffset) => {
        if (eventParking) {
            setOffset(nextOffset)
        } else {
            search(nextOffset, appliedFilters)
        }
    }

    return (
        <div>
            <section className="hero">
                <h1>Find a parking spot</h1>
                <p>Book a driveway, garage or private lot from people near you.</p>

                <form className="card search-form" onSubmit={(event) => {
                    event.preventDefault()
                    if (token && !interpreting && !lookingUpEvent && !rankingEvent && query.trim()) applyAiFilters()
                }}>
                    <label className="wide">
                        Describe the parking you need
                        <input
                            placeholder="Parking downtown tomorrow from 2 to 4 pm under $8 an hour"
                            value={query}
                            maxLength={300}
                            onChange={(event) => setQuery(event.target.value)}
                            disabled={!token || interpreting}
                        />
                    </label>
                    <button className="button secondary" disabled={!token || interpreting || lookingUpEvent || rankingEvent || !query.trim()}>
                        {interpreting ? 'Reading query…' : 'Apply AI filters'}
                    </button>
                    <p className="wide muted" role="status">
                        {token ? aiMessage : 'Sign in to use AI search.'}
                    </p>
                </form>
                <form className="card search-form" onSubmit={lookupEvent}>
                    <label className="wide">
                        Find a concert or event
                        <input
                            placeholder="Voices of Hope in Montréal"
                            value={eventQuery}
                            maxLength={300}
                            onChange={(event) => setEventQuery(event.target.value)}
                            disabled={!token || lookingUpEvent || rankingEvent}
                        />
                    </label>
                    <button className="button secondary" disabled={!token || interpreting || lookingUpEvent || rankingEvent || !eventQuery.trim()}>
                        {lookingUpEvent ? 'Looking up event…' : rankingEvent ? 'Finding parking…' : 'Look up event'}
                    </button>
                    {foundEvent && (
                        <p className="event-search-detail">
                            <strong>{foundEvent.name}</strong> · {foundEvent.venue}
                            {foundEvent.address && ' · ' + foundEvent.address}
                            {' · '}<a href={foundEvent.sourceUrl} target="_blank" rel="noreferrer">Event source</a>
                        </p>
                    )}
                    {(eventMessage || !token) && (
                        <p className="event-search-detail muted" role="status">
                            {token ? eventMessage : 'Sign in to look up events.'}
                        </p>
                    )}
                </form>
                <form className="card search-form" onSubmit={handleSubmit}>
                    <label className="wide">
                        {sort === 'event' ? 'Venue address' : 'Where?'}
                        <input placeholder="Address or area" value={location} maxLength={sort === 'event' ? 300 : 200}
                            required={sort === 'event'} readOnly={sort === 'event'} onChange={(e) => setLocation(e.target.value)} />
                    </label>
                    <label>
                        From
                        <input type="datetime-local" value={startTime} required={sort === 'event' || endTime !== ''} onChange={(e) => setStartTime(e.target.value)} />
                    </label>
                    <label>
                        Until
                        <input type="datetime-local" value={endTime} required={sort === 'event' || startTime !== ''} onChange={(e) => setEndTime(e.target.value)} />
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
                    <label>
                        Sort by
                        <select value={sort} onChange={(event) => setSort(event.target.value)}>
                            <option value="">Default</option>
                            <option value="cheapest">Cheapest</option>
                            {foundEvent && <option value="event">Nearest to event</option>}
                            <option value="closest">Closest to venue coordinates</option>
                        </select>
                    </label>
                    {sort === 'closest' && (
                        <>
                            <label>
                                Venue latitude
                                <input type="number" min="-90" max="90" step="any" required
                                    value={destinationLatitude} onChange={(event) => setDestinationLatitude(event.target.value)} />
                            </label>
                            <label>
                                Venue longitude
                                <input type="number" min="-180" max="180" step="any" required
                                    value={destinationLongitude} onChange={(event) => setDestinationLongitude(event.target.value)} />
                            </label>
                        </>
                    )}
                    <button className="button" disabled={lookingUpEvent || rankingEvent}>Search</button>
                </form>
            </section>

            {error && <p className="message error">{error}</p>}
            <DemoDetails demoMode={demoMode} result={result} />

            {page && (
                <div>
                    <p className="muted">
                        {page.total} spot(s) found
                        {eventParking?.ranking === 'distance' && ' · Ordered by straight-line distance'}
                        {eventParking?.ranking === 'estimated' && ' · Estimated proximity from addresses'}
                        {eventParking?.ranking === 'price' && ' · Proximity unavailable, ordered by price'}
                        {eventParking?.total > page.total && ` · Showing ${page.total} of ${eventParking.total} available spots`}
                    </p>

                    <div className="grid">
                        {page.items.map((spot) => (
                            <SpotCard key={spot.spotId} spot={spot} onClick={() => openSpot(spot.spotId)} />
                        ))}
                    </div>

                    {page.total > PAGE_SIZE && (
                        <div className="pager">
                            <button className="button secondary" disabled={offset === 0} onClick={() => changePage(offset - PAGE_SIZE)}>
                                Previous
                            </button>
                            <span>
                                Page {offset / PAGE_SIZE + 1} of {Math.ceil(page.total / PAGE_SIZE)}
                            </span>
                            <button className="button secondary" disabled={offset + PAGE_SIZE >= page.total} onClick={() => changePage(offset + PAGE_SIZE)}>
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
