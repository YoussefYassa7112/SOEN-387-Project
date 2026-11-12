import { showPrice } from '../api.js'

// One parking spot in a list.
function SpotCard({ spot, onClick, children }) {
    return (
        <div className="card spot-card" onClick={onClick}>
            {spot.photoUrl ? (
                <img className="spot-photo" src={spot.photoUrl} alt={spot.title} />
            ) : (
                <div className="spot-photo placeholder">P</div>
            )}
            <div className="spot-info">
                <h3>{spot.title}</h3>
                <p className="muted">{spot.address}</p>
                <p>
                    <strong>{showPrice(spot.pricePerHour)}</strong> / hour
                    {spot.parkingType && <span className="badge">{spot.parkingType}</span>}
                    {!spot.active && <span className="badge inactive">inactive</span>}
                </p>
                {children}
            </div>
        </div>
    )
}

export default SpotCard
