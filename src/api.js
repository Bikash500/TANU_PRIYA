export function basicAuthHeader(credentials) {
  return `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`;
}

export async function apiRequest(path, credentials, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(credentials ? { Authorization: basicAuthHeader(credentials) } : {}),
      ...(options.body ? { "Content-Type": "application/json" } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try {
      const result = await response.json();
      if (typeof result.error === "string") message = result.error;
    } catch (error) {
      if (response.status !== 401 && response.status !== 403) {
        console.error("Could not read the API error response.", error);
      }
      if (response.status === 401) message = "Incorrect email or password.";
      if (response.status === 403) message = "Your account does not have permission for this action.";
    }
    throw new Error(message);
  }

  if (response.status === 204) return null;
  if (response.headers.get("content-type")?.includes("text/csv")) return response.blob();
  return response.json();
}

export function jsonBody(value) {
  return JSON.stringify(value);
}
