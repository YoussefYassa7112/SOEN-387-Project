import { useState } from 'react'
import { toApiTime, toInputTime } from '../api.js'
import TimeWindowsInput from './TimeWindowsInput.jsx'

// Replaces the whole availability schedule of a spot.
function AvailabilityForm({ spot, onSave, onCancel }) {
    const [windows, setWindows] = useState(
        spot.availabilityWindows.map((window) => ({
            start: toInputTime(window.startTime),
            end: toInputTime(window.endTime),
        }))
    )

    const handleSubmit = (event) => {
        event.preventDefault()
        const periods = windows.map((window) => ({
            startTime: toApiTime(window.start),
            endTime: toApiTime(window.end),
        }))
        onSave({ periods: periods })
    }

    return (
        <form className="card form" onSubmit={handleSubmit}>
            <h2>Availability of {spot.title}</h2>
            <p className="muted">This replaces the whole schedule.</p>
            <TimeWindowsInput windows={windows} setWindows={setWindows} />
            <div className="row">
                <button className="button">Save availability</button>
                <button type="button" className="button secondary" onClick={onCancel}>Cancel</button>
            </div>
        </form>
    )
}

export default AvailabilityForm
