import { GoogleLogin } from '@react-oauth/google'

// Top bar: logo, navigation, sign-in and the demo mode switch.
function Header({ page, goTo, token, onGoogleSuccess, onLogout, demoMode, setDemoMode }) {
    const navClass = (name) => (page === name ? 'nav-link active' : 'nav-link')

    return (
        <header className="header">
            <div className="header-inner">
                <button className="logo" onClick={() => goTo('search')}>Locus</button>

                <nav>
                    <button className={navClass('search')} onClick={() => goTo('search')}>Find parking</button>
                    <button className={navClass('mySpots')} onClick={() => goTo('mySpots')}>My spots</button>
                    <button className={navClass('reservations')} onClick={() => goTo('reservations')}>My reservations</button>
                </nav>

                <div className="header-right">
                    <label className="demo-switch">
                        <input type="checkbox" checked={demoMode} onChange={(e) => setDemoMode(e.target.checked)} />
                        Demo mode
                    </label>

                    {token ? (
                        <button className="button secondary" onClick={onLogout}>Log out</button>
                    ) : (
                        <GoogleLogin onSuccess={onGoogleSuccess} onError={() => alert('Google sign-in failed')} />
                    )}
                </div>
            </div>
        </header>
    )
}

export default Header
