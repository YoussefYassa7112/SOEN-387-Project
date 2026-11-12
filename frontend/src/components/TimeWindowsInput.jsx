// A list of "from / until" rows, used for a spot's availability windows.
// Each window is { start, end } with values from <input type="datetime-local">.
function TimeWindowsInput({ windows, setWindows }) {
    const changeWindow = (index, field, value) => {
        const copy = windows.slice()
        copy[index] = { ...copy[index], [field]: value }
        setWindows(copy)
    }

    const addWindow = () => {
        setWindows(windows.concat({ start: '', end: '' }))
    }

    const removeWindow = (index) => {
        setWindows(windows.filter((window, i) => i !== index))
    }

    return (
        <div>
            {windows.map((window, index) => (
                <div className="row" key={index}>
                    <label>
                        From
                        <input type="datetime-local" value={window.start} onChange={(e) => changeWindow(index, 'start', e.target.value)} />
                    </label>
                    <label>
                        Until
                        <input type="datetime-local" value={window.end} onChange={(e) => changeWindow(index, 'end', e.target.value)} />
                    </label>
                    {windows.length > 1 && (
                        <button type="button" className="button small secondary" onClick={() => removeWindow(index)}>Remove</button>
                    )}
                </div>
            ))}
            <button type="button" className="button small secondary" onClick={addWindow}>+ Add time window</button>
        </div>
    )
}

export default TimeWindowsInput
