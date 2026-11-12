// Shows the last request of a section: status code and raw JSON.
function ResponseBox({ result }) {
    if (!result) {
        return null
    }

    const isOk = typeof result.status === 'number' && result.status < 400

    return (
        <div className="response">
            <p>
                <code>{result.request}</code>{' '}
                <strong className={isOk ? 'ok' : 'error'}>{result.status}</strong>
            </p>
            <pre>{JSON.stringify(result.body, null, 2)}</pre>
        </div>
    )
}

export default ResponseBox
