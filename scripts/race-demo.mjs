// Race demo (LOCUS-36): many people try to book the same parking spot
// for the same hour at the same moment.
//
// The spot's row is locked while one booking is checked and saved, so the
// others wait their turn and then see the spot is taken. We expect exactly
// one 202 Accepted and every other request to get 409 PARKING_SPOT_UNAVAILABLE.
//
// Needs Node 18 or newer (it uses the built-in fetch). No packages to install.
//
// Usage:
//   node scripts/race-demo.mjs --spot <spotId> --start 2026-12-01T14:00:00-05:00 \
//        --end 2026-12-01T15:00:00-05:00 --token <jwt> [--users 10] [--api http://localhost:8080]
//
// --token can be given several times (one per person). If you give fewer
// tokens than --users, the tokens are reused, and every request still gets
// its own Idempotency-Key, so each one counts as a separate booking attempt.

const args = process.argv.slice(2);

function option(name, fallback) {
  const index = args.indexOf('--' + name);
  return index >= 0 ? args[index + 1] : fallback;
}

function allOptions(name) {
  const values = [];
  args.forEach((arg, index) => {
    if (arg === '--' + name) values.push(args[index + 1]);
  });
  return values;
}

const api = option('api', 'http://localhost:8080').replace(/\/+$/, '');
const spot = option('spot');
const start = option('start');
const end = option('end');
const users = Number(option('users', '10'));
const tokens = allOptions('token');

if (!spot || !start || !end || tokens.length === 0) {
  console.log('Missing options. Example:');
  console.log('  node scripts/race-demo.mjs --spot <spotId> --start 2026-12-01T14:00:00-05:00 ' +
    '--end 2026-12-01T15:00:00-05:00 --token <jwt> --users 10');
  process.exit(1);
}

async function book(person) {
  const response = await fetch(`${api}/api/v1/parking-spots/${spot}/reservations`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + tokens[person % tokens.length],
      'Idempotency-Key': `race-${Date.now()}-${person}`,
    },
    body: JSON.stringify({ startTime: start, endTime: end }),
  });
  const body = await response.json().catch(() => ({}));
  return { person, status: response.status, code: body.code || '' };
}

console.log(`Sending ${users} booking requests at the same time to ${api} ...`);

// Promise.all starts every request before waiting for any of them.
const results = await Promise.all(
  Array.from({ length: users }, (_, person) => book(person).catch((error) => ({
    person, status: 'network error', code: error.message,
  }))),
);

for (const result of results) {
  console.log(`  person ${result.person + 1}: ${result.status} ${result.code}`);
}

const tally = {};
for (const result of results) {
  tally[result.status] = (tally[result.status] || 0) + 1;
}
console.log('\nSummary:', tally);

const accepted = tally[202] || 0;
if (accepted === 1 && (tally[409] || 0) === users - 1) {
  console.log('Exactly one person got the spot; everyone else was told it is taken.');
} else if (accepted === 0) {
  console.log('Nobody got the spot. Check that the spot is free for that time and the token is valid.');
} else {
  console.log('Unexpected result: look at the statuses above.');
}
