/** Shown instead of a screen the signed-in user's role may not open. */
export function NotEnoughPermissions() {
  return (
    <main className="forbidden">
      <h1>Not enough permissions</h1>
      <p className="error" role="alert">Your role does not allow opening this screen.</p>
    </main>
  )
}
