import { useEffect, useState } from "react";
import { apiRequest } from "../../api.js";

export default function AuditPage({ credentials }) {
  const [events, setEvents] = useState([]);
  const [error, setError] = useState("");

  useEffect(() => {
    apiRequest("/api/audit?limit=200", credentials)
      .then(setEvents)
      .catch((requestError) => setError(requestError.message));
  }, [credentials]);

  return (
    <>
      {error && <div className="module-error" role="alert">{error}</div>}
      <section className="directory">
        <div className="directory-heading"><div><h2>Recent activity</h2><p>Security and change history, newest first</p></div><span className="table-count">{events.length} events</span></div>
        <div className="table-wrap"><table className="data-table"><thead><tr><th>WHEN</th><th>USER</th><th>ACTION</th><th>RECORD</th><th>DETAILS</th></tr></thead><tbody>
          {events.length === 0 ? <tr><td className="empty-state" colSpan="5">No activity has been recorded yet.</td></tr> : events.map((event) => <tr key={event.id}><td className="audit-date">{event.createdAt}</td><td>{event.username}</td><td><span className={`role-badge action-${event.action.toLowerCase()}`}>{event.action}</span></td><td>{event.entityType}{event.entityId ? ` #${event.entityId}` : ""}</td><td>{event.details || "—"}</td></tr>)}
        </tbody></table></div>
        <div className="table-footer">Audit history is visible to administrators only.</div>
      </section>
    </>
  );
}
