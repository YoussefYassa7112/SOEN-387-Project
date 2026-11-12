import { useState } from 'react'
import { callApi, toApiTime } from '../api.js'
import ResponseBox from './ResponseBox.jsx'

// GET /parking-spots: public search with optional filters.
function SearchSpots() {
    const [location, setLocation] = useState('')
    const [startTime, setStartTime] = useState('')
    const [endTime, setEndTime] = useState('')
    const [maxPrice, setMaxPrice] = useState('')
    const [parkingType, setParkingType] = useState('')
    const [limit, setLimit] = useState('20')
    const [offset, setOffset] = useState('0')
    const [result, setResult] = useState(null)

    const handleSearch = async () => {
        // Only send the filters that were filled in.
        const params = new URLSearchParams()
        if (location !== '') params.append('location', location)
        if (startTime !== '') params.append('startTime', toApiTime(startTime))
        if (endTime !== '') params.append('endTime', toApiTime(endTime))
        if (maxPrice !== '') params.append('maxPricePerHour', maxPrice)
        if (parkingType !== '') params.append('parkingType', parkingType)
        if (limit !== '') params.append('limit', limit)
        if (offset !== '') params.append('offset', offset)

        setResult(await callApi('GET', '/parking-spots?' + params.toString()))
    }

    return (
        <section>
            <h2>2. Search parking spots (public)</h2>

            <label>Location <input value={location} onChange={(e) => setLocation(e.target.value)} /></label>
            <label>Start time <input type="datetime-local" value={startTime} onChange={(e) => setStartTime(e.target.value)} /></label>
            <label>End time <input type="datetime-local" value={endTime} onChange={(e) => setEndTime(e.target.value)} /></label>
            <label>Max price per hour <input type="number" step="0.01" value={maxPrice} onChange={(e) => setMaxPrice(e.target.value)} /></label>
            <label>Parking type
                <select value={parkingType} onChange={(e) => setParkingType(e.target.value)}>
                    <option value="">(any)</option>
                    <option value="driveway">driveway</option>
                    <option value="privateLot">privateLot</option>
                    <option value="garage">garage</option>
                    <option value="other">other</option>
                </select>
            </label>
            <label>Limit <input type="number" value={limit} onChange={(e) => setLimit(e.target.value)} /></label>
            <label>Offset <input type="number" value={offset} onChange={(e) => setOffset(e.target.value)} /></label>

            <button onClick={handleSearch}>Search</button>

            <ResponseBox result={result} />
        </section>
    )
}

export default SearchSpots
