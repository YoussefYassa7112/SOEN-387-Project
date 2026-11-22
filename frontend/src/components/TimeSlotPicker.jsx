import { useState } from 'react'
import { toInputTime } from '../api.js'

const SLOT_MINUTES = 60
const SLOT_MS = SLOT_MINUTES * 60000
const WEEKDAYS = ['Su', 'Mo', 'Tu', 'We', 'Th', 'Fr', 'Sa']

// "2026-11-26" for a date, in the user's time zone. Used to group slots by day.
function dayKey(date) {
    const pad = (number) => String(number).padStart(2, '0')
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
}

// Splits the owner's availability windows into 1-hour slots that start on the hour.
function makeSlots(windows) {
    const slots = []
    for (const window of windows) {
        const windowEnd = new Date(window.endTime).getTime()
        // Round the window's start up to the next full hour.
        let start = Math.ceil(new Date(window.startTime).getTime() / SLOT_MS) * SLOT_MS
        while (start + SLOT_MS <= windowEnd) {
            slots.push({ start: new Date(start), end: new Date(start + SLOT_MS) })
            start += SLOT_MS
        }
    }
    return slots
}

// True when a booked time overlaps this slot.
function isTaken(slot, bookedTimes) {
    return bookedTimes.some((booked) =>
        new Date(booked.startTime) < slot.end && new Date(booked.endTime) > slot.start)
}

// Booking picker: a month calendar (only days with free times can be picked), then that day's
// 1-hour slots. Taken and past slots are greyed out. Click a slot to start, then a later
// slot the same day to extend the booking to it.
// startTime / endTime are the values of the "From" / "Until" fields; onSelect(start, end) sets them.
function TimeSlotPicker({ windows, bookedTimes, startTime, endTime, onSelect }) {
    // "Now" is read once when the picker appears, to decide which slots are already in the past.
    const [now] = useState(() => new Date())

    const slots = makeSlots(windows).map((slot) => {
        const free = !isTaken(slot, bookedTimes) && slot.start >= now
        return { ...slot, free: free, taken: isTaken(slot, bookedTimes), day: dayKey(slot.start) }
    })

    // Days that have at least one free slot can be picked on the calendar.
    const freeDays = new Set(slots.filter((slot) => slot.free).map((slot) => slot.day))
    const firstFreeSlot = slots.find((slot) => slot.free)

    const [selectedDay, setSelectedDay] = useState(firstFreeSlot ? firstFreeSlot.day : null)
    const [month, setMonth] = useState(() => {
        const base = firstFreeSlot ? firstFreeSlot.start : now
        return new Date(base.getFullYear(), base.getMonth(), 1)
    })

    if (!firstFreeSlot) {
        return <p className="message error">No free times left for this spot.</p>
    }

    // ---- Calendar ----
    const daysInMonth = new Date(month.getFullYear(), month.getMonth() + 1, 0).getDate()
    const cells = []
    for (let i = 0; i < month.getDay(); i++) {
        cells.push(null)   // empty boxes before the 1st
    }
    for (let day = 1; day <= daysInMonth; day++) {
        cells.push(new Date(month.getFullYear(), month.getMonth(), day))
    }
    const changeMonth = (step) => setMonth(new Date(month.getFullYear(), month.getMonth() + step, 1))

    // ---- Slots of the picked day ----
    const daySlots = slots.filter((slot) => slot.day === selectedDay)

    const selectedStart = startTime ? new Date(startTime) : null
    const selectedEnd = endTime ? new Date(endTime) : null
    const isSelected = (slot) =>
        selectedStart !== null && selectedEnd !== null && slot.start >= selectedStart && slot.end <= selectedEnd

    const handleClick = (index) => {
        const clicked = daySlots[index]
        const firstIndex = selectedStart ? daySlots.findIndex((slot) => slot.start.getTime() === selectedStart.getTime()) : -1

        // Extend the current booking to the clicked slot, if every slot in between is free and back to back.
        if (firstIndex !== -1 && index > firstIndex) {
            let allFree = true
            for (let i = firstIndex + 1; i <= index; i++) {
                const backToBack = daySlots[i].start.getTime() === daySlots[i - 1].end.getTime()
                if (!daySlots[i].free || !backToBack) {
                    allFree = false
                }
            }
            if (allFree) {
                onSelect(toInputTime(daySlots[firstIndex].start), toInputTime(clicked.end))
                return
            }
        }

        // Otherwise start a new booking of one slot.
        onSelect(toInputTime(clicked.start), toInputTime(clicked.end))
    }

    return (
        <div className="slot-picker">
            <div className="calendar">
                <div className="calendar-header">
                    <button type="button" className="calendar-nav" onClick={() => changeMonth(-1)}>&lsaquo;</button>
                    <strong>{month.toLocaleDateString([], { month: 'long', year: 'numeric' })}</strong>
                    <button type="button" className="calendar-nav" onClick={() => changeMonth(1)}>&rsaquo;</button>
                </div>
                <div className="calendar-grid">
                    {WEEKDAYS.map((name) => <span key={name} className="calendar-weekday">{name}</span>)}
                    {cells.map((date, index) =>
                        date === null ? (
                            <span key={'empty' + index} />
                        ) : (
                            <button
                                key={dayKey(date)}
                                type="button"
                                className={'calendar-day' + (dayKey(date) === selectedDay ? ' selected' : '')}
                                disabled={!freeDays.has(dayKey(date))}
                                onClick={() => setSelectedDay(dayKey(date))}
                            >
                                {date.getDate()}
                            </button>
                        )
                    )}
                </div>
            </div>

            {selectedDay && (
                <>
                    <p className="small muted">
                        Click a start time, then a later one to book until it. Grey times are taken or past.
                    </p>
                    <div className="slot-grid">
                        {daySlots.map((slot, index) => (
                            <button
                                key={slot.start.getTime()}
                                type="button"
                                className={'slot' + (isSelected(slot) ? ' selected' : '')}
                                disabled={!slot.free}
                                title={slot.taken ? 'Already booked' : slot.free ? 'Free' : 'In the past'}
                                onClick={() => handleClick(index)}
                            >
                                {slot.start.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })}
                            </button>
                        ))}
                    </div>
                </>
            )}
        </div>
    )
}

export default TimeSlotPicker
