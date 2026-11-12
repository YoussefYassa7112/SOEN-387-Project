import { useEffect, useState } from 'react'
import { callApi, errorMessage, isSuccess } from '../api.js'
import SpotCard from '../components/SpotCard.jsx'
import SpotForm from '../components/SpotForm.jsx'
import AvailabilityForm from '../components/AvailabilityForm.jsx'
import DemoDetails from '../components/DemoDetails.jsx'

// Owner page: my listings, and forms to create, edit, change availability and deactivate.
function MySpotsPage({ token, openSpot, demoMode }) {
    const [listResult, setListResult] = useState(null)
    // What the page shows: 'list', 'create', 'edit' or 'availability'.
    const [mode, setMode] = useState('list')
    const [selectedSpot, setSelectedSpot] = useState(null)
    // The last create / update / deactivate call, shown in demo mode and for errors.
    const [actionResult, setActionResult] = useState(null)
    const [message, setMessage] = useState('')

    const loadMySpots = async () => {
        setListResult(await callApi('GET', '/me/parking-spots', token))
    }

    useEffect(() => {
        if (token) {
            loadMySpots()
        }
    }, [token])

    // Runs one owner action, then goes back to the list if it worked.
    const runAction = async (method, path, body, successMessage) => {
        const answer = await callApi(method, path, token, body)
        setActionResult(answer)

        if (isSuccess(answer)) {
            setMessage(successMessage)
            setMode('list')
            loadMySpots()
        } else {
            setMessage('')
        }
    }

    const createSpot = (body) => runAction('POST', '/parking-spots', body, 'Your spot is listed.')
    const updateSpot = (body) => runAction('PATCH', '/parking-spots/' + selectedSpot.spotId, body, 'Changes saved.')
    const saveAvailability = (body) => runAction('PUT', '/parking-spots/' + selectedSpot.spotId + '/availability', body, 'Availability saved.')

    const deactivateSpot = (spot) => {
        if (window.confirm('Deactivate "' + spot.title + '"? Drivers will no longer find it.')) {
            runAction('DELETE', '/parking-spots/' + spot.spotId, undefined, 'Spot deactivated.')
        }
    }

    const startEdit = (spot, newMode) => {
        setSelectedSpot(spot)
        setMode(newMode)
        setMessage('')
        setActionResult(null)
    }

    if (!token) {
        return <p className="message">Sign in with Google to list and manage your parking spots.</p>
    }

    const failed = actionResult && !isSuccess(actionResult)

    return (
        <div>
            <div className="page-title">
                <h1>My spots</h1>
                {mode === 'list' && (
                    <button className="button" onClick={() => startEdit(null, 'create')}>+ List a new spot</button>
                )}
            </div>

            {message && <p className="message success">{message}</p>}
            {failed && <p className="message error">{errorMessage(actionResult)}</p>}
            <DemoDetails demoMode={demoMode} result={actionResult} />

            {mode === 'create' && <SpotForm onSave={createSpot} onCancel={() => setMode('list')} />}
            {mode === 'edit' && <SpotForm spot={selectedSpot} onSave={updateSpot} onCancel={() => setMode('list')} />}
            {mode === 'availability' && (
                <AvailabilityForm spot={selectedSpot} onSave={saveAvailability} onCancel={() => setMode('list')} />
            )}

            {mode === 'list' && listResult && !isSuccess(listResult) && (
                <p className="message error">{errorMessage(listResult)}</p>
            )}

            {mode === 'list' && listResult && isSuccess(listResult) && (
                <div>
                    {listResult.body.length === 0 && <p className="muted">You have no listings yet.</p>}

                    <div className="grid">
                        {listResult.body.map((spot) => (
                            <SpotCard key={spot.spotId} spot={spot}>
                                {spot.active && (
                                    <div className="row">
                                        <button className="button small secondary" onClick={() => openSpot(spot.spotId)}>View</button>
                                        <button className="button small secondary" onClick={() => startEdit(spot, 'edit')}>Edit</button>
                                        <button className="button small secondary" onClick={() => startEdit(spot, 'availability')}>Availability</button>
                                        <button className="button small danger" onClick={() => deactivateSpot(spot)}>Deactivate</button>
                                    </div>
                                )}
                            </SpotCard>
                        ))}
                    </div>
                    <DemoDetails demoMode={demoMode} result={listResult} />
                </div>
            )}
        </div>
    )
}

export default MySpotsPage
