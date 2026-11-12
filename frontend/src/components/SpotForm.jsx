import { useState } from 'react'
import { toApiTime } from '../api.js'
import TimeWindowsInput from './TimeWindowsInput.jsx'

// Form to create a new spot, or to edit the details of an existing one.
// When "spot" is given we are editing, and availability is changed elsewhere.
function SpotForm({ spot, onSave, onCancel }) {
    const editing = spot !== undefined

    const [title, setTitle] = useState(editing ? spot.title : '')
    const [address, setAddress] = useState(editing ? spot.address : '')
    const [description, setDescription] = useState(editing && spot.description ? spot.description : '')
    const [photoUrl, setPhotoUrl] = useState(editing && spot.photoUrl ? spot.photoUrl : '')
    const [pricePerHour, setPricePerHour] = useState(editing ? spot.pricePerHour : '')
    const [parkingType, setParkingType] = useState(editing && spot.parkingType ? spot.parkingType : '')
    const [restrictions, setRestrictions] = useState(editing && spot.restrictions ? spot.restrictions : '')
    const [windows, setWindows] = useState([{ start: '', end: '' }])

    const handleSubmit = (event) => {
        event.preventDefault()

        // Only send the fields that are filled in.
        const body = {}
        if (title !== '') body.title = title
        if (address !== '') body.address = address
        if (description !== '') body.description = description
        if (photoUrl !== '') body.photoUrl = photoUrl
        if (pricePerHour !== '') body.pricePerHour = Number(pricePerHour)
        if (parkingType !== '') body.parkingType = parkingType
        if (restrictions !== '') body.restrictions = restrictions

        if (!editing) {
            body.availabilityWindows = windows.map((window) => ({
                startTime: toApiTime(window.start),
                endTime: toApiTime(window.end),
            }))
        }

        onSave(body)
    }

    return (
        <form className="card form" onSubmit={handleSubmit}>
            <h2>{editing ? 'Edit spot' : 'List a new spot'}</h2>

            <label>Title <input value={title} onChange={(e) => setTitle(e.target.value)} required /></label>
            <label>Address <input value={address} onChange={(e) => setAddress(e.target.value)} required /></label>
            <label>Price per hour ($) <input type="number" min="0.01" step="0.01" value={pricePerHour} onChange={(e) => setPricePerHour(e.target.value)} required /></label>
            <label>
                Type
                <select value={parkingType} onChange={(e) => setParkingType(e.target.value)}>
                    <option value="">Not specified</option>
                    <option value="driveway">Driveway</option>
                    <option value="privateLot">Private lot</option>
                    <option value="garage">Garage</option>
                    <option value="other">Other</option>
                </select>
            </label>
            <label>Description <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} /></label>
            <label>Photo URL <input type="url" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)} /></label>
            <label>Restrictions <input value={restrictions} onChange={(e) => setRestrictions(e.target.value)} /></label>

            {!editing && (
                <div>
                    <h3>When is it available?</h3>
                    <TimeWindowsInput windows={windows} setWindows={setWindows} />
                </div>
            )}

            <div className="row">
                <button className="button">{editing ? 'Save changes' : 'Create listing'}</button>
                <button type="button" className="button secondary" onClick={onCancel}>Cancel</button>
            </div>
        </form>
    )
}

export default SpotForm
