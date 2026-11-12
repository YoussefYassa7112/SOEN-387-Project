import { useState } from 'react'

// Demo mode only: a button that opens the raw request and response
// (method, path, headers, status code and JSON) of the last call.
function DemoDetails({ demoMode, result }) {
    const [open, setOpen] = useState(false)

    if (!demoMode || !result) {
        return null
    }

    const statusClass = result.status >= 200 && result.status < 300 ? 'status ok' : 'status error'

    return (
        <div className="demo-details">
            <button className="button small demo" onClick={() => setOpen(!open)}>
                {open ? 'Hide details' : 'Show details'}
            </button>

            {open && (
                <div className="demo-box">
                    <p>
                        <code>{result.request.method} {result.request.path}</code>{' '}
                        <span className={statusClass}>{result.status}</span>
                    </p>
                    <h4>Request headers</h4>
                    <pre>{JSON.stringify(result.request.headers, null, 2)}</pre>
                    {result.request.body !== undefined && (
                        <div>
                            <h4>Request body</h4>
                            <pre>{JSON.stringify(result.request.body, null, 2)}</pre>
                        </div>
                    )}
                    <h4>Response body</h4>
                    <pre>{result.body === null ? '(no body)' : JSON.stringify(result.body, null, 2)}</pre>
                </div>
            )}
        </div>
    )
}

export default DemoDetails
