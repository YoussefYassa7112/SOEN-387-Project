import { useState } from 'react'
import { callApi, toApiTime } from '../api.js'
import ResponseBox from './ResponseBox.jsx'

// Owner endpoints (need a token): create, list mine, update, set availability, deactivate.
function OwnerSpots({ token, spotId, setSpotId }) {
    const [title, setTitle] = useState('')
    const [description, setDescription] = useState('')
    const [address, setAddress] = useState('')
    const [photoUrl, setPhotoUrl] = useState('')
    const [pricePerHour, setPricePerHour] = useState('')
    const [parkingType, setParkingType] = useState('')
    const [restrictions, setRestrictions] = useState('')
    const [windowStart, setWindowStart] = useState('')
    const [windowEnd, setWindowEnd] = useState('')
    const [result, setResult] = useState(null)

    // Puts only the filled fields in the body, so the same form works for create and update.
    const buildBody = () => {
        const body = {}
        if (title !== '') body.title = title
        if (description !== '') body.description = description
        if (address !== '') body.address = address
        if (photoUrl !== '') body.photoUrl = photoUrl
        if (pricePerHour !== '') body.pricePerHour = Number(pricePerHour)
        if (parkingType !== '') body.parkingType = parkingType
        if (restrictions !== '') body.restrictions = restrictions
        return body
    }

    const buildWindows = () => {
        return [{ startTime: toApiTime(windowStart), endTime: toApiTime(windowEnd) }]
    }

    const handleCreate = async () => {
        const body = buildBody()
        body.availabilityWindows = buildWindows()

        const answer = await callApi('POST', '/parking-spots', token, body)
        setResult(answer)

        // Remember the new spot so the other sections can use it right away.
        if (answer.status === 201) {
            setSpotId(answer.body.spotId)
        }
    }

    const handleListMine = async () => {
        setResult(await callApi('GET', '/me/parking-spots', token))
    }

    const handleUpdate = async () => {
        setResult(await callApi('PATCH', '/parking-spots/' + spotId, token, buildBody()))
    }

    const handleSetAvailability = async () => {
        const body = { periods: buildWindows() }
        setResult(await callApi('PUT', '/parking-spots/' + spotId + '/availability', token, body))
    }

    const handleDeactivate = async () => {
        setResult(await callApi('DELETE', '/parking-spots/' + spotId, token))
    }

    return (
        <section>
            <h2>4. My parking spots (owner, needs sign-in)</h2>
            <p>Create sends the whole form. Update sends only the fields you filled in.</p>

            <label>Title <input value={title} onChange={(e) => setTitle(e.target.value)} /></label>
            <label>Description <input value={description} onChange={(e) => setDescription(e.target.value)} /></label>
            <label>Address <input value={address} onChange={(e) => setAddress(e.target.value)} /></label>
            <label>Photo URL <input value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} /></label>
            <label>Price per hour <input type="number" step="0.01" value={pricePerHour} onChange={(e) => setPricePerHour(e.target.value)} /></label>
            <label>Parking type
                <select value={parkingType} onChange={(e) => setParkingType(e.target.value)}>
                    <option value="">(none)</option>
                    <option value="driveway">driveway</option>
                    <option value="privateLot">privateLot</option>
                    <option value="garage">garage</option>
                    <option value="other">other</option>
                </select>
            </label>
            <label>Restrictions <input value={restrictions} onChange={(e) => setRestrictions(e.target.value)} /></label>
            <label>Available from <input type="datetime-local" value={windowStart} onChange={(e) => setWindowStart(e.target.value)} /></label>
            <label>Available until <input type="datetime-local" value={windowEnd} onChange={(e) => setWindowEnd(e.target.value)} /></label>

            <button onClick={handleCreate}>Create spot</button>
            <button onClick={handleListMine}>List my spots</button>
            <button onClick={handleUpdate}>Update spot</button>
            <button onClick={handleSetAvailability}>Replace availability</button>
            <button onClick={handleDeactivate}>Deactivate spot</button>

            <ResponseBox result={result} />
        </section>
    )
}

export default OwnerSpots
