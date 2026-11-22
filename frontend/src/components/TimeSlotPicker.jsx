import { useState } from 'react'
import { toInputTime } from '../api.js'

const SLOT_MINUTES = 30

// Splits the owner's availability windows into 30-minute slots.
function makeSlots(windows) {
    const slots = []
    for (const window of windows) {
        let start = new Date(window.startTime)
        const windowEnd = new Date(window.endTime)
        while (start.getTime() + SLOT_MINUTES * 60000 <= windowEnd.getTime()) {
            const end = new Date(start.getTime() + SLOT_MINUTES * 60000)
            slots.push({ start: start, end: end })
            start = end
        }
    }
    return slots
}

// True when a booked time overlaps this slot.
function isTaken(slot, bookedTimes) {
    return bookedTimes.some((booked) =>
        new Date(booked.startTime) < slot.end && new Date(booked.endTime) > slot.start)
}

// Booking grid: every 30-minute slot of the owner's availability. Taken and past slots are greyed
// out and cannot be clicked. Click a slot to start, then a later slot to extend the booking to it.
// startTime / endTime are the values of the "From" / "Until" fields; onSelect(start, end) sets them.
function TimeSlotPicker({ windows, bookedTimes, startTime, endTime, onSelect }) {
    // "Now" is read once when the grid appears, to decide which slots are already in the past.
    const [now] = useState(() => new Date())
    const slots = makeSlots(windows).map((slot) => ({
        ...slot,
        taken: isTaken(slot, bookedTimes),
        past: slot.start < now,
    }))

    const selectedStart = startTime ? new Date(startTime) : null
    const selectedEnd = endTime ? new Date(endTime) : null
    const isSelected = (slot) =>
        selectedStart !== null && selectedEnd !== null && slot.start >= selectedStart && slot.end <= selectedEnd

    const handleClick = (index) => {
        const clicked = slots[index]
        const firstIndex = selectedStart ? slots.findIndex((slot) => slot.start.getTime() === selectedStart.getTime()) : -1

        // Extend the current booking to the clicked slot, if every slot in between is free and back to back.
        if (firstIndex !== -1 && index > firstIndex) {
            let allFree = true
            for (let i = firstIndex + 1; i <= index; i++) {
                const backToBack = slots[i].start.getTime() === slots[i - 1].end.getTime()
                if (slots[i].taken || slots[i].past || !backToBack) {
                    allFree = false
                }
            }
            if (allFree) {
                onSelect(toInputTime(slots[firstIndex].start), toInputTime(clicked.end))
                return
            }
        }

        // Otherwise start a new booking of one slot.
        onSelect(toInputTime(clicked.start), toInputTime(clicked.end))
    }

    if (slots.length === 0) {
        return <p className="muted small">The owner has no available times for this spot.</p>
    }

    // One row of slots per day.
    const days = []
    slots.forEach((slot, index) => {
        const day = slot.start.toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' })
        if (days.length === 0 || days[days.length - 1].day !== day) {
            days.push({ day: day, items: [] })
        }
        days[days.length - 1].items.push({ slot: slot, index: index })
    })

    return (
        <div className="slot-picker">
            <p className="small muted">Click a start time, then a later one to book until it. Grey times are taken or past.</p>
            {days.map((group) => (
                <div key={group.day}>
                    <p className="small"><strong>{group.day}</strong></p>
                    <div className="slot-grid">
                        {group.items.map(({ slot, index }) => (
                            <button
                                key={index}
                                type="button"
                                className={'slot' + (isSelected(slot) ? ' selected' : '')}
                                disabled={slot.taken || slot.past}
                                title={slot.taken ? 'Already booked' : slot.past ? 'In the past' : 'Free'}
                                onClick={() => handleClick(index)}
                            >
                                {slot.start.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                            </button>
                        ))}
                    </div>
                </div>
            ))}
        </div>
    )
}

export default TimeSlotPicker
